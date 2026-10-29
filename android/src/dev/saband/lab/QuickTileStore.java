package dev.saband.lab;

import android.content.Context;
import org.json.JSONObject;
import java.io.IOException;

/** One private, device-bound tile profile; never included in backup import. */
public final class QuickTileStore {
    private static final String FILE="quick-tile.json";
    public static JSONObject load(Context c) throws Exception {return PreferenceStore.read(c,FILE);}
    public static JSONObject backup(Context c,JSONObject plan) throws Exception {
        if(plan==null||plan.optInt("schema")!=1)throw new IOException("Set up the 5G tile in Tools first.");
        int slot=plan.optInt("slot",-1);
        if(slot<0||slot>1)throw new IOException("Invalid tile SIM. Configure the tile again.");
        JSONObject b=PreferenceStore.read(c,"qmi-sa-backup-slot"+slot+".json");
        if(!PreferenceStore.matchesDevice(c,b)||!BootPlans.matches(plan,b,slot))throw new IOException("Tile backup/device/firmware changed. Read preferences and configure the tile again.");
        return b;
    }
    public static void save(Context c,int slot,String target,JSONObject b,String support) throws Exception {
        if(slot<0||slot>1||b.optInt("slot",-1)!=slot||!PreferenceStore.matchesDevice(c,b))throw new IOException("A matching original backup is required. Read preferences first.");
        QuickTilePolicy.validate(target,b.optString("original_sa"),support);
        JSONObject p=new JSONObject();p.put("schema",1);p.put("slot",slot);p.put("target_sa",target);p.put("target_radio_mode",64);
        p.put("backup_created_ms",b.getLong("created_ms"));p.put("original_sa",b.getString("original_sa"));p.put("original_nsa",b.getString("original_nsa"));
        PreferenceStore.write(c,FILE,p);
    }
    /** Caller holds PreferenceStore.LOCK. null means toggle based on the fresh read. */
    public static JSONObject apply(Context c,Boolean enabled) throws Exception {
        JSONObject plan=load(c),b=backup(c,plan);int slot=plan.getInt("slot");
        final JSONObject[] fresh=new JSONObject[1],response=new JSONObject[1];
        QuickTileTransaction.run(PreferenceStore.journal(b),plan.getString("target_sa"),enabled,new QuickTileTransaction.Backend() {
            public PreferenceJournal.Snapshot read() throws Exception {
                fresh[0]=RootHelper.invoke(c,"qmi-probe",""+slot);
                if(!fresh[0].optBoolean("ok"))throw new IOException(fresh[0].optString("error","Preference read failed."));
                return PreferenceStore.snapshot(fresh[0]);
            }
            public String supported() {return fresh[0].optString("supported_nr_hex");}
            public void clearReapply() {BootPlans.clear(c,slot);}
            public void save(PreferenceJournal state) throws Exception {
                PreferenceStore.storeJournal(b,state);PreferenceStore.write(c,"qmi-sa-backup-slot"+slot+".json",b);
            }
            public BootReapply.Result apply(PreferenceJournal state,String target,int mode,boolean on) throws Exception {
                JSONObject r=on?RootHelper.invoke(c,"qmi-apply-plan",""+slot,state.originalSa,state.expectedSa,state.originalNsa,target,""+state.expectedMode,""+mode)
                    :RootHelper.invoke(c,"qmi-mode",""+slot,""+state.expectedMode,""+mode);
                response[0]=r;
                PreferenceJournal.Snapshot live=null;try {live=PreferenceStore.snapshot(r);}catch(IllegalArgumentException ignored) {}
                return new BootReapply.Result(r.optBoolean("ok"),r.optBoolean("rollback_verified"),r.optBoolean("write_attempted",true),live);
            }
            public void record() throws Exception {
                JSONObject record=new JSONObject();record.put("slot",slot);record.put("operation","quick_tile_radio_and_sa");record.put("captured_ms",System.currentTimeMillis());record.put("helper_result",response[0]);
                PreferenceStore.write(c,"qmi-last-change-slot"+slot+".json",record);
            }
        });
        return response[0];
    }
}
