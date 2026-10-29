package dev.saband.lab;

import org.json.*;

/** Uses the JVM org.json artifact; Android framework storage is not executed here. */
public final class SpeedTestReportsTest {
    private static void check(boolean condition) {if(!condition)throw new AssertionError();}
    private static JSONObject report(int slot,String served,String choice) throws Exception {
        JSONObject r=new JSONObject();r.put("slot",slot);r.put("started_ms",1700000000000L);r.put("complete",true);
        r.put("serving_bands_sampled",served);r.put("last_read_sa_preference",choice);r.put("http_latency_ms",35.0);
        r.put("download",new JSONObject().put("mbps",450.25).put("short_sample",true));r.put("upload",new JSONObject().put("mbps",41.2));
        r.put("received_payload_bytes",100000000);r.put("sent_payload_bytes",20000000);
        r.put("serving_samples",new JSONArray().put(new JSONObject().put("phase","Before download").put("captured_ms",1700000001000L).put("serving_band_label",served).put("rat_label","NR")));
        return r;
    }
    public static void main(String[] args) throws Exception {
        JSONObject first=report(0,"n78","n28");JSONArray history=new JSONArray();history=SpeedTestStore.prepend(history,first);
        String shown=SpeedTestStore.render(new JSONArray(history.toString()),0);
        check(shown.contains("Serving bands (sampled): n78")&&!shown.contains("n28"));
        check(shown.contains("450.3 Mbps (short sample)")&&shown.contains("41.2 Mbps")&&shown.contains("Ping (HTTP): 35 ms"));
        check(shown.contains("100.0 MB received")&&shown.contains("Before download")&&shown.contains("NR"));
        history=SpeedTestStore.prepend(history,report(1,"n1","n1"));check(!SpeedTestStore.render(history,0).contains("n1"));
        JSONObject unknown=report(0,"unknown","n78");unknown.remove("http_latency_ms");unknown.put("download",JSONObject.NULL);unknown.put("upload",JSONObject.NULL);unknown.put("complete",false);unknown.put("cancelled",true);
        shown=SpeedTestStore.render(new JSONArray().put(unknown),0);check(shown.contains("Stopped")&&shown.contains("sampled): unknown")&&shown.contains("Ping (HTTP): —")&&!shown.contains("NaN"));
        for(int i=0;i<30;i++)history=SpeedTestStore.prepend(history,report(0,"n"+(i+1),"n78"));
        check(history.length()==20&&history.getJSONObject(0).getString("serving_bands_sampled").equals("n30"));
        check(history.getJSONObject(19).getString("serving_bands_sampled").equals("n11"));
        check(first.getString("serving_bands_sampled").equals("n78"));
        check(SpeedTestStore.render(new JSONArray(),0).contains("No tests"));
        JSONObject partial=report(0,"n78","not read");partial.put("complete",false);partial.put("upload",JSONObject.NULL);
        partial.put("error","timeout");partial.put("failed_phase","Upload");partial.put("duration_per_direction_s",60);partial.put("upload_acknowledged_bytes",0);
        shown=SpeedTestStore.render(new JSONArray().put(partial),0);
        check(SpeedTestStore.outcome(partial).equals("Partial results")&&shown.contains("Partial results"));
        check(shown.contains("450.3 Mbps")&&shown.contains("Ping (HTTP): 35 ms")&&shown.contains("Incomplete phase: Upload")&&shown.contains("60s per direction"));
        check(!shown.contains("↑ 0.0")&&shown.contains("Upload acknowledged: 0.0 MB"));
        check(SpeedTestStore.outcome(new JSONObject()).equals("Failed"));
        check(SpeedTestStore.outcome(new JSONObject().put("cancelled",true).put("complete",true)).equals("Stopped"));
        JSONObject noPing=report(0,"n78","not read");noPing.put("complete",false);noPing.put("http_latency_ms",JSONObject.NULL);noPing.put("throughput_complete",true);
        noPing.put("latency_attempts",1);noPing.put("latency_valid_samples",0);noPing.put("error","Latency request timed out.");noPing.put("failed_phase","Latency");
        check(SpeedTestStore.outcome(noPing).equals("Speeds measured · ping unavailable"));
        shown=SpeedTestStore.render(new JSONArray().put(noPing),0);check(shown.contains("450.3 Mbps")&&shown.contains("41.2 Mbps")&&shown.contains("Ping (HTTP): —")&&shown.contains("HTTP ping samples: 0 / 1"));
        noPing.put("aborted",true);check(!SpeedTestStore.outcome(noPing).startsWith("Speeds measured"));
        System.out.println("Speed history checks passed: serving versus selected bands, roundtrip, download/upload/ping, samples, missing values, SIM filtering, short runs and latest-20 retention.");
    }
}
