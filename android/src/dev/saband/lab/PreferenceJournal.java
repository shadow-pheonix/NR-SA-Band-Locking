package dev.saband.lab;

/** Private backup bookkeeping; no modem I/O and no changes to captured band bytes. */
public final class PreferenceJournal {
    public final String originalSa, originalNsa;
    public String expectedSa, pendingSa;
    public int originalMode, expectedMode, pendingMode;
    public boolean modeDirty;

    public PreferenceJournal(String originalSa,String originalNsa,String expectedSa,String pendingSa,
            int originalMode,int expectedMode,int pendingMode,boolean modeDirty) {
        this.originalSa=originalSa;this.originalNsa=originalNsa;
        this.expectedSa=expectedSa;this.pendingSa=pendingSa;
        this.originalMode=originalMode;this.expectedMode=expectedMode;
        this.pendingMode=pendingMode;this.modeDirty=modeDirty;
    }

    public static final class Snapshot {
        public final String sa, nsa;
        public final int mode;
        public Snapshot(String sa,String nsa,int mode) {
            if(!mask(sa)||!mask(nsa))throw new IllegalArgumentException("Incomplete live band preferences. Read preferences again.");
            this.sa=sa;this.nsa=nsa;this.mode=mode;
        }
    }
    private static boolean mask(String value) {return value!=null&&value.matches("[0-9a-fA-F]{128}");}
    private static boolean mode(int value) {return value>0&&value<128;}

    /** An explicit successful read resolves old intent, including modem/OS resets. */
    public void refresh(Snapshot live) {
        if(!originalNsa.equalsIgnoreCase(live.nsa))throw new IllegalArgumentException("Live NSA differs from the captured backup. No SET was sent. Keep the backup and copy the diagnostic report.");
        expectedSa=live.sa;pendingSa="";
        if(mode(live.mode)) {
            expectedMode=live.mode;pendingMode=-1;
            modeDirty=mode(originalMode)&&live.mode!=originalMode;
        }
    }

    /** Stage only after a fresh read; the helper still compares that read before SET. */
    public int stage(String target,boolean restoring,boolean nrOnly) {
        if(!mask(target))throw new IllegalArgumentException("Invalid SA target.");
        int targetMode=restoring?-1:RadioModeSelection.target(nrOnly,expectedMode,originalMode);
        if(restoring)pendingSa=target;else stagePlan(target,targetMode);
        return targetMode;
    }

    public void stagePlan(String target,int targetMode) {
        if(!mask(target)||!mode(targetMode)||!mode(expectedMode))throw new IllegalArgumentException("Read valid bands and radio mode before applying.");
        if(targetMode==64&&!mode(originalMode)&&expectedMode!=64)originalMode=expectedMode;
        pendingSa=target;pendingMode=targetMode;
    }

    /** Uncertain writes retain their pending targets until a later successful read. */
    public void finish(boolean ok,boolean rollbackVerified,boolean writeAttempted,Snapshot live) {
        if(!writeAttempted) {pendingSa="";pendingMode=-1;}
        if((ok||rollbackVerified||!writeAttempted)&&live!=null&&originalNsa.equalsIgnoreCase(live.nsa))refresh(live);
    }
}
