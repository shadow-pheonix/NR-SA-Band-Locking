package dev.saband.lab;

import android.content.Context;
import android.os.Build;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** All accesses that can lead to SET share one process-wide transaction lock. */
public final class PreferenceStore {
    public static final Object LOCK=new Object();
    public static JSONObject read(Context context,String name) throws Exception {
        AtomicFile file=new AtomicFile(new File(context.getFilesDir(),name));
        return file.getBaseFile().exists()?new JSONObject(new String(file.readFully(),StandardCharsets.UTF_8)):null;
    }
    public static void write(Context context,String name,JSONObject value) throws Exception {
        AtomicFile file=new AtomicFile(new File(context.getFilesDir(),name));FileOutputStream stream=null;
        try {stream=file.startWrite();stream.write(value.toString(2).getBytes(StandardCharsets.UTF_8));file.finishWrite(stream);}
        catch(Exception e) {if(stream!=null)file.failWrite(stream);throw e;}
    }
    public static String baseband() {return Build.getRadioVersion()==null?"unknown":Build.getRadioVersion();}
    public static boolean matchesDevice(Context context,JSONObject b) {
        String binding=android.provider.Settings.Secure.getString(context.getContentResolver(),android.provider.Settings.Secure.ANDROID_ID);
        return b!=null&&b.optInt("schema")==2&&"qmi-nas-sa-v1".equals(b.optString("read_profile"))&&Build.FINGERPRINT.equals(b.optString("fingerprint"))&&baseband().equals(b.optString("baseband"))&&binding!=null&&binding.equals(b.optString("device_binding"));
    }
    public static PreferenceJournal.Snapshot snapshot(JSONObject r) {
        return new PreferenceJournal.Snapshot(r.optString("sa_hex"),r.optString("nsa_hex"),r.optInt("rat_mode_preference",-1));
    }
    public static PreferenceJournal journal(JSONObject b) {
        return new PreferenceJournal(b.optString("original_sa"),b.optString("original_nsa"),b.optString("expected_sa"),b.optString("pending_sa"),
                b.optInt("original_radio_mode",-1),b.optInt("expected_radio_mode",-1),b.optInt("pending_radio_mode",-1),b.optBoolean("radio_mode_dirty"));
    }
    public static void storeJournal(JSONObject b,PreferenceJournal journal) throws Exception {
        b.put("expected_sa",journal.expectedSa);b.put("pending_sa",journal.pendingSa);
        if(journal.originalMode>0)b.put("original_radio_mode",journal.originalMode);
        if(journal.expectedMode>0)b.put("expected_radio_mode",journal.expectedMode);
        b.put("pending_radio_mode",journal.pendingMode);b.put("radio_mode_dirty",journal.modeDirty);
    }
}
