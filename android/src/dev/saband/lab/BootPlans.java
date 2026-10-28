package dev.saband.lab;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.File;

/** Opt-in plans are private, separate from backups, and never imported with backups. */
public final class BootPlans {
    private static final int JOB_ID=78028;
    private static String name(int slot) {return "boot-lock-slot"+slot+".json";}
    public static JSONObject load(Context context,int slot) throws Exception {return PreferenceStore.read(context,name(slot));}
    public static void clear(Context context,int slot) {
        new AtomicFile(new File(context.getFilesDir(),name(slot))).delete();
        if(!exists(context,0)&&!exists(context,1))for(int i=0;i<BootPolicy.MAX_ATTEMPTS;i++)context.getSystemService(JobScheduler.class).cancel(JOB_ID+i);
    }
    private static boolean exists(Context context,int slot) {return new File(context.getFilesDir(),name(slot)).exists();}
    public static void save(Context context,int slot,JSONObject backup,String target,int mode,JSONObject result) throws Exception {
        if(!BootPolicy.verified(target,mode,result.optBoolean("ok"),result.optString("sa_hex"),result.optInt("rat_mode_preference",-1)))throw new IllegalArgumentException("Reboot reapply was not saved: live verification did not match.");
        JSONObject plan=new JSONObject();plan.put("schema",1);plan.put("slot",slot);plan.put("target_sa",target);plan.put("target_radio_mode",mode);
        plan.put("backup_created_ms",backup.getLong("created_ms"));plan.put("original_sa",backup.getString("original_sa"));plan.put("original_nsa",backup.getString("original_nsa"));
        PreferenceStore.write(context,name(slot),plan);
    }
    public static boolean matches(JSONObject plan,JSONObject backup,int slot) {
        return plan!=null&&backup!=null&&plan.optInt("schema")==1&&plan.optInt("slot",-1)==slot&&backup.optInt("slot",-1)==slot
                &&plan.optLong("backup_created_ms",-1)==backup.optLong("created_ms",-2)
                &&plan.optString("original_sa").equals(backup.optString("original_sa"))&&plan.optString("original_nsa").equals(backup.optString("original_nsa"))
                &&plan.optString("target_sa").matches("[0-9a-fA-F]{128}")&&plan.optInt("target_radio_mode",-1)>0&&plan.optInt("target_radio_mode",-1)<128;
    }
    public static boolean schedule(Context context,int attempt) {
        return schedule(context,attempt,3);
    }
    public static boolean schedule(Context context,int attempt,int slotMask) {
        if(attempt<0||attempt>=BootPolicy.MAX_ATTEMPTS||(!exists(context,0)&&!exists(context,1)))return false;
        PersistableBundle extras=new PersistableBundle();extras.putInt("attempt",attempt);extras.putInt("slot_mask",slotMask);
        JobInfo job=new JobInfo.Builder(JOB_ID+attempt,new ComponentName(context,BootLockJobService.class))
                .setMinimumLatency(30000L*(1L<<attempt)).setExtras(extras).build();
        return context.getSystemService(JobScheduler.class).schedule(job)==JobScheduler.RESULT_SUCCESS;
    }
}
