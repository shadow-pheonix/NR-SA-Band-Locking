package dev.saband.lab;

import android.app.job.JobParameters;
import android.app.job.JobService;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded, opt-in reapply using the same read/compare/SET/verify helper as the UI. */
public final class BootLockJobService extends JobService {
    private final ConcurrentHashMap<Integer,AtomicBoolean> running=new ConcurrentHashMap<>();
    @Override public boolean onStartJob(JobParameters params) {
        AtomicBoolean cancelled=new AtomicBoolean();running.put(params.getJobId(),cancelled);
        new Thread(() -> {
            int attempt=params.getExtras().getInt("attempt",0),slots=params.getExtras().getInt("slot_mask",3),retryMask=0;
            for(int slot=0;slot<2&&!cancelled.get();slot++) {
                if((slots&(1<<slot))==0)continue;
                PreferenceStore.LOCK.lock();
                try {if(!cancelled.get()&&applySlot(slot,attempt,cancelled))retryMask|=1<<slot;}
                finally {PreferenceStore.LOCK.unlock();}
            }
            // Retry only slots whose preflight failed, never another slot's uncertain write.
            if(!cancelled.get()) {
                jobFinished(params,false);
                if(retryMask!=0)BootPlans.schedule(this,attempt+1,retryMask);
            }
            running.remove(params.getJobId(),cancelled);
        },"sa-boot-reapply").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        AtomicBoolean cancelled=running.get(params.getJobId());if(cancelled!=null)cancelled.set(true);
        // Let an in-flight helper finish its bounded verification/rollback; do not interrupt it.
        return false;
    }
    private boolean applySlot(int slot,int attempt,AtomicBoolean cancelled) {
        boolean preflightFailed=false,mutationStarted=false;
        try {
            JSONObject plan=BootPlans.load(this,slot);if(plan==null)return false;
            JSONObject backup=PreferenceStore.read(this,"qmi-sa-backup-slot"+slot+".json");
            if(!PreferenceStore.matchesDevice(this,backup)||!BootPlans.matches(plan,backup,slot)) {
                BootPlans.clear(this,slot);
                throw new IOException("Reboot reapply disabled: backup/device/firmware does not match. Read preferences and apply again.");
            }
            PreferenceJournal journal=PreferenceStore.journal(backup);
            final JSONObject[] response=new JSONObject[1];
            BootReapply.Result applied=BootReapply.run(journal,plan.getString("target_sa"),plan.getInt("target_radio_mode"),new BootReapply.Backend() {
                public PreferenceJournal.Snapshot read() throws Exception {
                    JSONObject fresh=RootHelper.invoke(BootLockJobService.this,"qmi-probe",""+slot);
                    if(!fresh.optBoolean("ok")||!fresh.optBoolean("write_ready"))throw new IOException(fresh.optString("error")+" "+fresh.optString("capability_error"));
                    return PreferenceStore.snapshot(fresh);
                }
                public void save(PreferenceJournal state) throws Exception {
                    PreferenceStore.storeJournal(backup,state);PreferenceStore.write(BootLockJobService.this,"qmi-sa-backup-slot"+slot+".json",backup);
                }
                public BootReapply.Result apply(PreferenceJournal state,String target,int targetMode) throws Exception {
                    JSONObject result=RootHelper.invoke(BootLockJobService.this,"qmi-apply-plan",""+slot,state.originalSa,state.expectedSa,state.originalNsa,target,""+state.expectedMode,""+targetMode);
                    response[0]=result;
                    PreferenceJournal.Snapshot observed=null;
                    try {observed=PreferenceStore.snapshot(result);}catch(IllegalArgumentException ignored) {}
                    return new BootReapply.Result(result.optBoolean("ok"),result.optBoolean("rollback_verified"),result.optBoolean("write_attempted",true),observed);
                }
                public void record() throws Exception {
                    JSONObject record=new JSONObject();record.put("slot",slot);record.put("operation","boot_sa_and_radio_mode_apply");record.put("captured_ms",System.currentTimeMillis());record.put("helper_result",response[0]);
                    PreferenceStore.write(BootLockJobService.this,"qmi-last-change-slot"+slot+".json",record);
                }
            },cancelled::get);
            if(applied!=null)bootReport(slot,attempt,response[0],false);
            return false;
        } catch(Exception error) {
            if(error instanceof BootReapply.Failure) {
                preflightFailed=((BootReapply.Failure)error).preflightFailed;
                mutationStarted=((BootReapply.Failure)error).mutationStarted;
            }
            boolean retry=BootPolicy.retry(attempt,preflightFailed,mutationStarted,cancelled.get());
            try {
                JSONObject result=new JSONObject();result.put("ok",false);result.put("error",error.toString());result.put("outcome_uncertain",mutationStarted);
                if(!mutationStarted)result.put("write_attempted",false);
                bootReport(slot,attempt,result,retry);
            } catch(Exception ignored) {}
            return retry;
        }
    }
    private void bootReport(int slot,int attempt,JSONObject result,boolean retry) throws Exception {
        JSONObject report=new JSONObject();report.put("slot",slot);report.put("attempt",attempt+1);report.put("captured_ms",System.currentTimeMillis());report.put("retry_requested",retry);report.put("helper_result",result);
        PreferenceStore.write(this,"boot-result-slot"+slot+".json",report);
    }
}
