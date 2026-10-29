package dev.saband.lab;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.*;

/** Local-only, bounded history. Reports contain no SIM identity or phone backup. */
public final class SpeedTestStore {
    private static AtomicFile file(Context c) {return new AtomicFile(new File(c.getFilesDir(),"speed-tests.json"));}
    public static synchronized JSONArray read(Context c) throws Exception {
        AtomicFile f=file(c);if(!f.getBaseFile().exists())return new JSONArray();
        if(f.getBaseFile().length()>512000)throw new IOException("Speed history is too large.");
        JSONArray a=new JSONArray(new String(f.readFully(),StandardCharsets.UTF_8));if(a.length()>20)throw new IOException("Invalid speed history.");return a;
    }
    public static synchronized void add(Context c,JSONObject report) throws Exception {
        JSONArray out=prepend(read(c),report);byte[] data=out.toString().getBytes(StandardCharsets.UTF_8);
        if(data.length>512000)throw new IOException("Speed history exceeds its storage limit.");
        AtomicFile f=file(c);FileOutputStream stream=null;
        try {stream=f.startWrite();stream.write(data);f.finishWrite(stream);}
        catch(Exception e) {if(stream!=null)f.failWrite(stream);throw e;}
    }
    public static synchronized void clear(Context c) {file(c).delete();}
    static JSONArray prepend(JSONArray old,JSONObject report) throws JSONException {
        JSONArray out=new JSONArray();out.put(report);for(int i=0;i<old.length()&&i<19;i++)out.put(old.get(i));return out;
    }
    public static String outcome(JSONObject report) {
        if(report.optBoolean("cancelled"))return "Stopped";
        if(report.optBoolean("complete"))return "Complete";
        if(!report.optBoolean("aborted")) {
            boolean down=partial(report.optJSONObject("download")),up=partial(report.optJSONObject("upload"));
            if(down||up)return (down&&up?"Transfers interrupted":down?"Download interrupted":"Upload interrupted")+" · speeds saved";
        }
        if(report.optBoolean("throughput_complete")&&!report.optBoolean("aborted")&&report.isNull("http_latency_ms")&&report.optJSONObject("download")!=null&&report.optJSONObject("upload")!=null)return "Speeds measured · ping unavailable";
        if(report.optJSONObject("download")!=null||report.optJSONObject("upload")!=null||
            report.has("http_latency_ms")&&!report.isNull("http_latency_ms"))return "Partial results";
        return "Failed";
    }
    public static boolean partial(JSONObject rate) {return rate!=null&&rate.optBoolean("partial");}
    static Object rateReport(SpeedTestEngine.Rate rate) throws JSONException {
        if(rate==null)return JSONObject.NULL;JSONObject r=new JSONObject();r.put("mbps",rate.mbps());r.put("payload_bytes",rate.bytes);
        r.put("elapsed_ms",rate.nanos/1000000.0);r.put("data_cap_reached",rate.capped);r.put("short_sample",rate.nanos<2000000000L);
        r.put("complete",!rate.partial);r.put("partial",rate.partial);r.put("completion_reason",rate.completionReason);return r;
    }
    public static String measurementNote(JSONObject report) {
        StringBuilder out=new StringBuilder();
        for(String key:new String[]{"download","upload"}) {
            JSONObject rate=report.optJSONObject(key);if(!partial(rate))continue;
            if(out.length()>0)out.append("\n");
            out.append(key.equals("download")?"Download":"Upload").append(String.format(Locale.ROOT," interrupted at %.1fs",rate.optDouble("elapsed_ms")/1000.0));
            if("http_429".equals(rate.optString("completion_reason")))out.append(" · server rate limit (HTTP 429)");
        }
        return out.toString();
    }
    public static String render(JSONArray history,int slot) {
        StringBuilder out=new StringBuilder();for(int i=0;i<history.length();i++) {
            JSONObject r=history.optJSONObject(i);if(r==null||r.optInt("slot",-1)!=slot)continue;
            if(out.length()>0)out.append("\n\n────────\n\n");
            out.append(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(r.optLong("started_ms"))))
                .append(" · SIM ").append(slot+1).append(" · ").append(outcome(r))
                .append("\nServing bands (sampled): ").append(r.optString("serving_bands_sampled","unknown"))
                .append("\n↓ ").append(rate(r,"download")).append("   ↑ ").append(rate(r,"upload"))
                .append("\nPing (HTTP): ").append(r.isNull("http_latency_ms")?"—":String.format(Locale.ROOT,"%.0f ms",r.optDouble("http_latency_ms")))
                .append(String.format(Locale.ROOT,"\nPayload: %.1f MB received · %.1f MB sent",r.optLong("received_payload_bytes")/1000000.0,r.optLong("sent_payload_bytes")/1000000.0));
            for(String key:new String[]{"download","upload"}) {
                JSONObject rate=r.optJSONObject(key);if(rate==null||!rate.has("elapsed_ms"))continue;
                out.append("\n").append(key.equals("download")?"Download":"Upload").append(String.format(Locale.ROOT," measured: %.1fs",rate.optDouble("elapsed_ms")/1000.0));
                if(r.has("duration_per_direction_s"))out.append(" / ").append(r.optInt("duration_per_direction_s")).append("s requested");
                if(partial(rate))out.append(" · interrupted sample; measured speed retained");
            }
            JSONArray samples=r.optJSONArray("serving_samples");if(samples!=null)for(int n=0;n<samples.length();n++) {
                JSONObject s=samples.optJSONObject(n);if(s==null)continue;
                out.append("\n").append(s.optString("phase")).append(" · ").append(DateFormat.getTimeInstance().format(new Date(s.optLong("captured_ms"))))
                    .append(": ").append(s.optString("serving_band_label","unknown")).append(" · ").append(s.optString("rat_label","RAT unknown"));
                if(s.has("observation_error"))out.append(" (cell update unavailable)");
            }
            if(!r.optString("error").isEmpty())out.append("\n").append(r.optString("error"));
            if(!r.optString("failed_phase").isEmpty())out.append("\nIncomplete phase: ").append(r.optString("failed_phase"));
            if(r.has("latency_attempts"))out.append("\nHTTP ping samples: ").append(r.optInt("latency_valid_samples")).append(" / ").append(r.optInt("latency_attempts"));
            JSONArray failures=r.optJSONArray("phase_failures");if(failures!=null)for(int n=0;n<failures.length();n++) {
                JSONObject failure=failures.optJSONObject(n);if(failure==null)continue;JSONArray requests=failure.optJSONArray("recent_requests");
                if(requests!=null)for(int k=0;k<requests.length();k++) {
                    JSONObject request=requests.optJSONObject(k);if(request==null)continue;
                    out.append("\n").append(failure.optString("phase")).append(" request: ").append(request.optString("operation"))
                        .append(" · ").append(request.optLong("elapsed_ms")).append(" ms");
                    if(!request.isNull("http_status"))out.append(" · HTTP ").append(request.optInt("http_status"));
                }
            }
            if(r.has("duration_per_direction_s"))out.append("\nTimed test: ").append(r.optInt("duration_per_direction_s")).append("s per direction");
            if(r.has("upload_acknowledged_bytes"))out.append(String.format(Locale.ROOT,"\nUpload acknowledged: %.1f MB (sent bytes alone do not establish upload speed)",r.optLong("upload_acknowledged_bytes")/1000000.0));
            out.append("\nCloudflare · cellular HTTP average; sampled serving bands may omit aggregated carriers.");
        }
        return out.length()==0?"No tests for this SIM yet. Apply a selection, wait for service, then start a test.":out.toString();
    }
    private static String rate(JSONObject report,String key) {
        JSONObject r=report.optJSONObject(key);return r==null?"—":String.format(Locale.ROOT,"%.1f Mbps%s%s",r.optDouble("mbps"),partial(r)?" (interrupted)":"",r.optBoolean("short_sample")?" (short sample)":"");
    }
}
