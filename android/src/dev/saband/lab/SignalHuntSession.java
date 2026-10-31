package dev.saband.lab;

import android.content.Context;
import android.location.LocationManager;
import android.os.*;
import android.telephony.*;
import java.util.*;

/** Selected-subscription cell updates only; no cached all-SIM fallback or modem writes. */
public final class SignalHuntSession {
    public interface Listener {void sample(SignalHunt.Sample value);void unavailable(String reason);void tick();}
    private final Context context;private final Handler handler=new Handler(Looper.getMainLooper());
    private final Listener listener;private final int slot;
    private TelephonyManager manager;private Object callback;private PhoneStateListener legacy;
    private int subscription,generation,requestSerial;private boolean running,pending;private long requestStarted,lastRequest,lastObservation=-1;
    private final Runnable pulse=new Runnable() {public void run() {
        if(!running)return;
        try {
            if(!validSubscription()) {stop();listener.unavailable("SIM changed or became unavailable. Start a new hunt.");listener.tick();return;}
            LocationManager location=context.getSystemService(LocationManager.class);
            if(location==null||!location.isLocationEnabled()) {stop();listener.unavailable("Location is off. Enable it and resume the hunt.");listener.tick();return;}
            long now=SystemClock.elapsedRealtime();
            if(pending&&now-requestStarted>=8000) {pending=false;requestSerial++;if(!recentObservation(now))listener.unavailable("Cell update timed out · waiting for a fresh modem sample");}
            if(!pending&&now-lastRequest>=5000)request();
            listener.tick();
        } catch(SecurityException e) {stop();listener.unavailable("Phone or Precise location permission was removed. Resume to grant access.");listener.tick();return;}
        catch(RuntimeException e) {stop();listener.unavailable("Cell updates are unavailable. Resume to retry.");listener.tick();return;}
        if(running)handler.postDelayed(this,1000);
    }};
    public SignalHuntSession(Context context,int slot,Listener listener) {this.context=context.getApplicationContext();this.slot=slot;this.listener=listener;}
    public boolean running() {return running;}
    public void start() {
        stop();
        try {
            SubscriptionInfo info=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoForSimSlotIndex(slot);
            if(info==null)throw new IllegalStateException("No active subscription in this SIM slot.");
            subscription=info.getSubscriptionId();manager=context.getSystemService(TelephonyManager.class).createForSubscriptionId(subscription);
            running=true;lastRequest=-5000;lastObservation=-1;final int token=generation;
            if(Build.VERSION.SDK_INT>=31)callback=Api31.register(manager,context.getMainExecutor(),cells -> receive(token,cells));
            else {
                legacy=new PhoneStateListener() {@Override public void onCellInfoChanged(List<CellInfo> cells) {receive(token,cells);}};
                manager.listen(legacy,PhoneStateListener.LISTEN_CELL_INFO);
            }
            handler.post(pulse);
        } catch(RuntimeException e) {stop();listener.unavailable("Cannot start cell updates: "+e.getMessage());listener.tick();}
    }
    public void stop() {
        running=false;pending=false;generation++;requestSerial++;handler.removeCallbacksAndMessages(null);
        try {if(manager!=null&&callback!=null&&Build.VERSION.SDK_INT>=31)Api31.unregister(manager,callback);if(manager!=null&&legacy!=null)manager.listen(legacy,PhoneStateListener.LISTEN_NONE);}catch(RuntimeException ignored) {}
        callback=null;legacy=null;manager=null;
    }
    private boolean validSubscription() {
        SubscriptionInfo info=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoForSimSlotIndex(slot);
        return info!=null&&info.getSubscriptionId()==subscription;
    }
    private boolean recentObservation(long now) {return lastObservation>0&&lastObservation<=now&&now-lastObservation<=SignalHunt.FRESH_MS;}
    private void request() {
        final int token=generation,request=++requestSerial;pending=true;requestStarted=lastRequest=SystemClock.elapsedRealtime();
        manager.requestCellInfoUpdate(context.getMainExecutor(),new TelephonyManager.CellInfoCallback() {
            @Override public void onCellInfo(List<CellInfo> cells) {if(!running||token!=generation||request!=requestSerial)return;pending=false;receive(token,cells);}
            @Override public void onError(int code,Throwable error) {if(!running||token!=generation||request!=requestSerial)return;pending=false;if(!recentObservation(SystemClock.elapsedRealtime()))listener.unavailable("Cell update delayed ("+code+") · waiting for fresh data");listener.tick();}
        });
    }
    private void receive(int token,List<CellInfo> cells) {
        if(!running||token!=generation)return;
        try {
            if(!validSubscription()) {stop();listener.unavailable("SIM changed. Start a new hunt.");listener.tick();return;}
            LocationManager location=context.getSystemService(LocationManager.class);
            if(location==null||!location.isLocationEnabled()) {stop();listener.unavailable("Location is off. Enable it and resume.");listener.tick();return;}
            long now=SystemClock.elapsedRealtime();ArrayList<CellInfoNr> candidates=new ArrayList<>(),primary=new ArrayList<>();
            if(cells!=null)for(CellInfo cell:cells)if(cell instanceof CellInfoNr&&cell.isRegistered()) {
                long stamp=cell.getTimestampMillis();if(stamp<=0||stamp>now||now-stamp>SignalHunt.FRESH_MS)continue;
                candidates.add((CellInfoNr)cell);if(cell.getCellConnectionStatus()==CellInfo.CONNECTION_PRIMARY_SERVING)primary.add((CellInfoNr)cell);
            }
            List<CellInfoNr> chosen=primary.isEmpty()?candidates:primary;
            if(chosen.size()!=1) {lastObservation=-1;listener.unavailable(chosen.isEmpty()?"No fresh serving NR cell reported for this SIM":"Multiple serving NR cells · no single cell selected");listener.tick();return;}
            CellInfoNr cell=chosen.get(0);CellIdentityNr id=(CellIdentityNr)cell.getCellIdentity();CellSignalStrengthNr signal=(CellSignalStrengthNr)cell.getCellSignalStrength();
            String operator=id.getMccString()==null||id.getMncString()==null?"":id.getMccString()+id.getMncString();
            lastObservation=cell.getTimestampMillis();listener.sample(new SignalHunt.Sample(id.getBands(),id.getNrarfcn(),id.getPci(),id.getTac(),id.getNci(),operator,signal.getSsRsrp(),signal.getSsRsrq(),signal.getSsSinr(),cell.getTimestampMillis()));
        } catch(RuntimeException e) {lastObservation=-1;listener.unavailable("Cell reading unavailable. Check Phone and Precise location access.");listener.tick();}
    }
    /** Separate class keeps API 31 callback types off the API 30 execution path. */
    private static final class Api31 extends TelephonyCallback implements TelephonyCallback.CellInfoListener {
        private final java.util.function.Consumer<List<CellInfo>> consumer;
        Api31(java.util.function.Consumer<List<CellInfo>> consumer) {this.consumer=consumer;}
        @Override public void onCellInfoChanged(List<CellInfo> cells) {consumer.accept(cells);}
        static Object register(TelephonyManager manager,java.util.concurrent.Executor executor,java.util.function.Consumer<List<CellInfo>> consumer) {
            Api31 callback=new Api31(consumer);manager.registerTelephonyCallback(executor,callback);return callback;
        }
        static void unregister(TelephonyManager manager,Object callback) {manager.unregisterTelephonyCallback((Api31)callback);}
    }
}
