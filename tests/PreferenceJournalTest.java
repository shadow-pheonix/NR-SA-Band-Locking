package dev.saband.lab;

/** Reproduce a reboot between Apply operations without Android or a real modem. */
public class PreferenceJournalTest {
    private static String mask(int... bands) {
        byte[] bytes=new byte[64];for(int n:bands)bytes[(n-1)/8]|=1<<((n-1)%8);
        StringBuilder hex=new StringBuilder();for(byte b:bytes)hex.append(String.format("%02x",b&255));return hex.toString();
    }
    private static final String ORIGINAL=mask(1,3,28,78),LOCK=mask(78),NSA=mask(1,78);
    private static void check(boolean value) {if(!value)throw new AssertionError();}
    private static PreferenceJournal previousLock() {return new PreferenceJournal(ORIGINAL,NSA,LOCK,LOCK,28,28,64,true);}
    private static PreferenceJournal.Snapshot live(String sa,int mode) {return new PreferenceJournal.Snapshot(sa,NSA,mode);}
    private static void originals(PreferenceJournal j) {check(j.originalSa.equals(ORIGINAL));check(j.originalNsa.equals(NSA));check(j.originalMode==28);}
    public static void main(String[] args) {
        // Reported failure: persisted n78 intent, rebooted modem at original bands/mode 28.
        PreferenceJournal j=previousLock();j.refresh(live(ORIGINAL,28));
        check(j.expectedSa.equals(ORIGINAL));check(j.pendingSa.isEmpty());check(j.expectedMode==28);
        check(j.pendingMode==-1&&!j.modeDirty);originals(j);
        check(j.stage(LOCK,false,true)==64);
        check(j.expectedSa.equals(ORIGINAL));check(j.expectedMode==28); // Actual helper compare arguments.
        check(j.pendingSa.equals(LOCK)&&j.pendingMode==64);originals(j);
        j.finish(true,false,true,live(LOCK,64));
        check(j.expectedSa.equals(LOCK)&&j.expectedMode==64&&j.modeDirty);
        check(j.pendingSa.isEmpty()&&j.pendingMode==-1);originals(j);

        // An external change is also read explicitly; it must not overwrite the backup.
        j.refresh(live(mask(3),92));check(j.expectedSa.equals(mask(3)));originals(j);
        check(j.stage(LOCK,false,false)==92); // Unticked means preserve this non-NR-only mode.
        j.finish(false,false,false,live(ORIGINAL,28)); // Race refused before SET.
        check(j.expectedSa.equals(ORIGINAL)&&j.pendingSa.isEmpty()&&j.pendingMode==-1&&!j.modeDirty);
        originals(j);

        // A missing response after an attempted write cannot establish the live outcome.
        j.stage(LOCK,false,true);j.finish(false,false,true,live(ORIGINAL,28));
        check(j.pendingSa.equals(LOCK)&&j.pendingMode==64&&j.expectedSa.equals(ORIGINAL));
        j.finish(false,false,true,null);check(j.pendingSa.equals(LOCK)&&j.pendingMode==64);
        j.refresh(live(LOCK,64));check(j.pendingSa.isEmpty()&&j.pendingMode==-1&&j.modeDirty);
        j.stage(ORIGINAL,false,false);j.finish(false,true,true,live(LOCK,64));
        check(j.pendingSa.isEmpty()&&j.expectedSa.equals(LOCK)&&j.expectedMode==64);originals(j);

        // NSA drift blocks rebasing, and a no-write response must still cancel new intent.
        j.stage(ORIGINAL,false,false);
        PreferenceJournal.Snapshot foreign=new PreferenceJournal.Snapshot(ORIGINAL,mask(3),28);
        boolean failed=false;try {j.refresh(foreign);}catch(IllegalArgumentException expected) {failed=true;}
        check(failed&&j.expectedSa.equals(LOCK)&&j.pendingSa.equals(ORIGINAL));
        j.finish(false,false,false,foreign);
        check(j.pendingSa.isEmpty()&&j.pendingMode==-1&&j.expectedSa.equals(LOCK));originals(j);
        failed=false;try {new PreferenceJournal.Snapshot("",NSA,28);}catch(IllegalArgumentException expected) {failed=true;}
        check(failed);

        // Capture the fallback once; subsequent reads and retries do not replace it.
        j=new PreferenceJournal(ORIGINAL,NSA,ORIGINAL,"",-1,-1,-1,false);
        j.refresh(live(ORIGINAL,92));check(j.stage(LOCK,false,true)==64&&j.originalMode==92);
        j.finish(false,false,false,null);check(j.pendingSa.isEmpty()&&j.pendingMode==-1);
        j.refresh(live(ORIGINAL,28));j.stage(LOCK,false,true);check(j.originalMode==92);
        j.refresh(live(LOCK,64));check(j.stage(ORIGINAL,true,false)==-1&&j.pendingMode==-1);
        System.out.println("Preference journal checks passed: reboot recovery, live baseline, immutable originals, refused and uncertain writes.");
    }
}
