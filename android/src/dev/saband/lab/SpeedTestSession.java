package dev.saband.lab;

import android.content.Context;
import android.net.*;
import android.os.SystemClock;
import android.telephony.*;
import org.json.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Foreground cellular route and serving-cell sampling around the HTTP phases. */
public final class SpeedTestSession {
    public interface Progress {void update(String text);}
    private final Context context;private final int slot;private final Progress progress;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private volatile SpeedTestEngine engine;private long checkedAt;
    private ConnectivityManager cm;private Network network;private int subscription;private TelephonyManager tm;
    private final JSONArray samples=new JSONArray();private final List<int[]> bandSamples=new ArrayList<>();
    public SpeedTestSession(Context context,int slot,Progress progress) {this.context=context;this.slot=slot;this.progress=progress;}
    public void cancel() {cancelled.set(true);SpeedTestEngine e=engine;if(e!=null)e.cancel();}
    public JSONObject run(String preference) throws Exception {
        JSONObject report=new JSONObject();report.put("schema",1);report.put("slot",slot);report.put("started_ms",System.currentTimeMillis());
        report.put("provider","Cloudflare");report.put("method","4 HTTP streams; 10s/direction or 100 MB down / 20 MB up; end-to-end payload average");
        report.put("last_read_sa_preference",preference);report.put("serving_samples",samples);
        try {
            cm=context.getSystemService(ConnectivityManager.class);
            SubscriptionInfo info=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoForSimSlotIndex(slot);
            if(info==null)throw new IOException("No active subscription for this SIM slot.");
            subscription=info.getSubscriptionId();tm=context.getSystemService(TelephonyManager.class).createForSubscriptionId(subscription);
            network=cm.getActiveNetwork();route();
            sample("Before latency");
            engine=new SpeedTestEngine(url -> {route();return (HttpURLConnection)network.openConnection(url);},() -> {
                if(cancelled.get())throw new InterruptedIOException("Test stopped.");
                synchronized(this) {long now=SystemClock.elapsedRealtime();if(now-checkedAt>=500) {route();checkedAt=now;}}
            },new SpeedTestEngine.Listener() {
                public void phase(String phase) {
                    progress.update(phase+"…");if(!phase.equals("Latency"))sample("Before "+phase.toLowerCase(Locale.ROOT));
                }
                public void progress(String phase,long bytes,long nanos) {
                    double mbps=nanos<=0?0:bytes*8000.0/nanos;
                    progress.update(String.format(Locale.ROOT,"%s · %.1f Mbps · %.1f MB",phase,mbps,bytes/1000000.0));
                }
            });
            if(cancelled.get())engine.cancel();SpeedTestEngine.Result result=engine.run();
            if(!cancelled.get())sample("After transfer");
            report.put("complete",result.complete()&&!cancelled.get());report.put("cancelled",result.cancelled||cancelled.get());report.put("error",cancelled.get()?"Stopped; incomplete results.":result.error);
            report.put("http_latency_ms",result.latencyMs<0?JSONObject.NULL:result.latencyMs);
            report.put("download",rate(result.download));report.put("upload",rate(result.upload));
            report.put("received_payload_bytes",result.receivedBytes);report.put("sent_payload_bytes",result.sentBytes);
            // A SIM/route change at the last boundary must invalidate a previously successful result.
            if(!cancelled.get())route();
        } catch(Exception e) {report.put("complete",false);report.put("cancelled",cancelled.get());report.put("error",cancelled.get()?"Stopped; incomplete results.":e.getMessage());}
        if(cancelled.get()) {report.put("complete",false);report.put("cancelled",true);report.put("error","Stopped; incomplete results.");}
        report.put("finished_ms",System.currentTimeMillis());report.put("serving_bands_sampled",SpeedTestBands.summary(bandSamples));
        report.put("band_sampling_note","Fresh registered NR samples at phase boundaries, not continuous monitoring or a complete carrier-aggregation list. Unknown does not mean no coverage.");
        return report;
    }
    private void route() throws IOException {
        if(cancelled.get())throw new InterruptedIOException("Test stopped.");
        if(SubscriptionManager.getDefaultDataSubscriptionId()!=subscription||SubscriptionManager.getActiveDataSubscriptionId()!=subscription)
            throw new IOException("Choose SIM "+(slot+1)+" as the phone's mobile-data SIM, then retry. Data-SIM switching is not supported during a test.");
        Network active=cm.getActiveNetwork();NetworkCapabilities caps=active==null?null:cm.getNetworkCapabilities(active);
        if(network==null||!network.equals(active)||caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)||caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)||!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN))
            throw new IOException("Use mobile data with Wi-Fi and VPN off. Test stopped because the cellular route is unavailable or changed.");
        if(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)||!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))throw new IOException("Mobile internet is not ready. Wait for service and retry.");
        NetworkSpecifier spec=caps.getNetworkSpecifier();
        if(spec instanceof TelephonyNetworkSpecifier&&((TelephonyNetworkSpecifier)spec).getSubscriptionId()!=subscription)throw new IOException("The active network belongs to another SIM.");
    }
    private void sample(String phase) {
        if(cancelled.get())return;
        JSONObject sample=new JSONObject();TreeSet<Integer> observed=new TreeSet<>();
        try {
            sample.put("phase",phase);sample.put("requested_ms",System.currentTimeMillis());sample.put("captured_ms",System.currentTimeMillis());sample.put("source","subscription_cell_update");
            int rat=tm.getDataNetworkType();sample.put("data_rat",rat);sample.put("rat_label",rat==20?"NR":rat==13?"LTE":"type "+rat);
            AtomicReference<List<CellInfo>> updated=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>("");CountDownLatch done=new CountDownLatch(1);
            tm.requestCellInfoUpdate(context.getMainExecutor(),new TelephonyManager.CellInfoCallback() {
                @Override public void onCellInfo(List<CellInfo> cells) {updated.set(cells);done.countDown();}
                @Override public void onError(int code,Throwable detail) {error.set("Cell update error "+code);done.countDown();}
            });
            long deadline=SystemClock.elapsedRealtime()+2500;
            while(!done.await(100,TimeUnit.MILLISECONDS)) {if(cancelled.get()||SystemClock.elapsedRealtime()>=deadline)break;}
            JSONArray entries=new JSONArray();sample.put("nr_serving_cells",entries);
            List<CellInfo> cells=updated.get();long now=SystemClock.elapsedRealtime(),wall=System.currentTimeMillis();sample.put("captured_ms",wall);
            if(cells==null)sample.put("observation_error",error.get().isEmpty()?"Fresh cell update unavailable; no cached fallback used.":error.get());
            else for(CellInfo cell:cells)if(cell instanceof CellInfoNr&&cell.isRegistered()&&entries.length()<32) {
                CellIdentityNr id=(CellIdentityNr)cell.getCellIdentity();long stamp=cell.getTimestampMillis(),age=stamp>0&&stamp<=now?now-stamp:-1;
                int[] bands=SpeedTestBands.serving(id.getBands(),true,age);for(int band:bands)observed.add(band);
                JSONObject c=new JSONObject();c.put("bands",array(bands));c.put("reported_bands",array(id.getBands()));c.put("age_ms",age<0?JSONObject.NULL:age);
                c.put("fresh",age>=0&&age<=SpeedTestBands.MAX_AGE_MS);c.put("nrarfcn",number(id.getNrarfcn()));c.put("pci",number(id.getPci()));
                c.put("observed_ms",age<0?JSONObject.NULL:wall-age);
                c.put("ss_rsrp_dbm",number(((CellSignalStrengthNr)cell.getCellSignalStrength()).getSsRsrp()));entries.put(c);
            }
            int[] bands=new int[observed.size()];int i=0;for(int band:observed)bands[i++]=band;
            sample.put("serving_bands",array(bands));sample.put("serving_band_label",SpeedTestBands.names(bands));bandSamples.add(bands);
        } catch(Exception e) {
            try {sample.put("observation_error",e.getMessage());sample.put("serving_band_label","unknown");}catch(JSONException ignored) {}
            bandSamples.add(new int[0]);
        }
        samples.put(sample);
    }
    private static Object number(int n) {return n==Integer.MAX_VALUE?JSONObject.NULL:n;}
    private static JSONArray array(int[] values) {JSONArray a=new JSONArray();if(values!=null)for(int n:values)a.put(n);return a;}
    private static Object rate(SpeedTestEngine.Rate rate) throws JSONException {
        if(rate==null)return JSONObject.NULL;JSONObject r=new JSONObject();r.put("mbps",rate.mbps());r.put("payload_bytes",rate.bytes);
        r.put("elapsed_ms",rate.nanos/1000000.0);r.put("data_cap_reached",rate.capped);r.put("short_sample",rate.nanos<2000000000L);return r;
    }
}
