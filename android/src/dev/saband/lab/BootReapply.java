package dev.saband.lab;

import java.util.function.BooleanSupplier;

/** Testable boot transaction. Backend methods perform platform-specific I/O. */
public final class BootReapply {
    public interface Backend {
        PreferenceJournal.Snapshot read() throws Exception;
        void save(PreferenceJournal journal) throws Exception;
        Result apply(PreferenceJournal journal,String target,int targetMode) throws Exception;
        void record() throws Exception;
    }
    public static final class Result {
        public final boolean ok, rollbackVerified, writeAttempted;
        public final PreferenceJournal.Snapshot live;
        public Result(boolean ok,boolean rollbackVerified,boolean writeAttempted,PreferenceJournal.Snapshot live) {
            this.ok=ok;this.rollbackVerified=rollbackVerified;this.writeAttempted=writeAttempted;this.live=live;
        }
    }
    public static final class Failure extends Exception {
        public final boolean preflightFailed, mutationStarted;
        private Failure(Exception error,boolean preflightFailed,boolean mutationStarted) {
            super(error);this.preflightFailed=preflightFailed;this.mutationStarted=mutationStarted;
        }
    }
    public static Result run(PreferenceJournal journal,String target,int targetMode,Backend backend,BooleanSupplier cancelled) throws Failure {
        boolean preflightFailed=false,mutationStarted=false;
        try {
            if(cancelled.getAsBoolean())return null;
            preflightFailed=true;
            PreferenceJournal.Snapshot fresh=backend.read();
            preflightFailed=false;
            if(fresh.mode<1||fresh.mode>127)throw new IllegalArgumentException("Live radio mode is unavailable; no boot SET was sent.");
            journal.refresh(fresh);
            if(cancelled.getAsBoolean())return null;
            journal.stagePlan(target,targetMode);backend.save(journal);
            if(cancelled.getAsBoolean()) {
                journal.finish(false,false,false,null);backend.save(journal);return null;
            }
            mutationStarted=true;
            Result result=backend.apply(journal,target,targetMode);backend.record();
            journal.finish(result.ok,result.rollbackVerified,result.writeAttempted,result.live);backend.save(journal);
            return result;
        } catch(Exception error) {throw new Failure(error,preflightFailed,mutationStarted);}
    }
}
