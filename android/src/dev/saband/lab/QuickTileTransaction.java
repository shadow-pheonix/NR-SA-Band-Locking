package dev.saband.lab;

/** Fresh read, journal before SET, one attempt, then verified journal completion. */
public final class QuickTileTransaction {
    public interface Backend {
        PreferenceJournal.Snapshot read() throws Exception;
        String supported();
        void clearReapply() throws Exception;
        void save(PreferenceJournal journal) throws Exception;
        BootReapply.Result apply(PreferenceJournal journal,String target,int mode,boolean enabled) throws Exception;
        void record() throws Exception;
    }
    public static BootReapply.Result run(PreferenceJournal journal,String configuredBands,Boolean requested,Backend backend) throws Exception {
        PreferenceJournal.Snapshot fresh=backend.read();
        boolean enabled=requested==null?QuickTilePolicy.nextEnabled(fresh.mode):requested;
        if(fresh.mode<1||fresh.mode>127)throw new IllegalArgumentException("Live radio mode is unavailable. No change was sent.");
        journal.refresh(fresh);
        if(enabled)QuickTilePolicy.validate(configuredBands,journal.originalSa,backend.supported());
        // Capture the original mode even when the first operation is LTE-only.
        if(journal.originalMode<1)journal.originalMode=fresh.mode;
        String target=enabled?configuredBands:fresh.sa;
        int mode=enabled?QuickTilePolicy.NR_ONLY:QuickTilePolicy.LTE_ONLY;
        backend.clearReapply();
        journal.stagePlan(target,mode);backend.save(journal);
        BootReapply.Result result=backend.apply(journal,target,mode,enabled);
        backend.record();
        if(result.ok&&(result.live==null||result.live.mode!=mode||!target.equalsIgnoreCase(result.live.sa)||!journal.originalNsa.equalsIgnoreCase(result.live.nsa)))
            throw new IllegalStateException("Tile result did not match the requested preferences. Read preferences before retrying.");
        journal.finish(result.ok,result.rollbackVerified,result.writeAttempted,result.live);backend.save(journal);
        return result;
    }
}
