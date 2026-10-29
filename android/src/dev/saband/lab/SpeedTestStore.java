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
    public static String render(JSONArray history,int slot) {
        StringBuilder out=new StringBuilder();for(int i=0;i<history.length();i++) {
            JSONObject r=history.optJSONObject(i);if(r==null||r.optInt("slot",-1)!=slot)continue;
            if(out.length()>0)out.append("\n\n────────\n\n");
            out.append(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(r.optLong("started_ms"))))
                .append(" · SIM ").append(slot+1).append(" · ").append(r.optBoolean("complete")?"Complete":r.optBoolean("cancelled")?"Stopped":"Failed / incomplete")
                .append("\nServing bands (sampled): ").append(r.optString("serving_bands_sampled","unknown"))
                .append("\n↓ ").append(rate(r,"download")).append("   ↑ ").append(rate(r,"upload"))
                .append("\nPing (HTTP): ").append(r.isNull("http_latency_ms")?"—":String.format(Locale.ROOT,"%.0f ms",r.optDouble("http_latency_ms")))
                .append(String.format(Locale.ROOT,"\nPayload: %.1f MB received · %.1f MB sent",r.optLong("received_payload_bytes")/1000000.0,r.optLong("sent_payload_bytes")/1000000.0));
            JSONArray samples=r.optJSONArray("serving_samples");if(samples!=null)for(int n=0;n<samples.length();n++) {
                JSONObject s=samples.optJSONObject(n);if(s==null)continue;
                out.append("\n").append(s.optString("phase")).append(" · ").append(DateFormat.getTimeInstance().format(new Date(s.optLong("captured_ms"))))
                    .append(": ").append(s.optString("serving_band_label","unknown")).append(" · ").append(s.optString("rat_label","RAT unknown"));
                if(s.has("observation_error"))out.append(" (cell update unavailable)");
            }
            if(!r.optString("error").isEmpty())out.append("\n").append(r.optString("error"));
            out.append("\nCloudflare · cellular HTTP average; sampled serving bands may omit aggregated carriers.");
        }
        return out.length()==0?"No tests for this SIM yet. Apply a selection, wait for service, then start a test.":out.toString();
    }
    private static String rate(JSONObject report,String key) {
        JSONObject r=report.optJSONObject(key);return r==null?"—":String.format(Locale.ROOT,"%.1f Mbps%s",r.optDouble("mbps"),r.optBoolean("short_sample")?" (short sample)":"");
    }
}
