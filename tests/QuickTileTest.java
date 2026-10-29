package dev.saband.lab;

import java.io.IOException;
import java.util.ArrayList;

public final class QuickTileTest {
    private static String mask(int... bands) {
        byte[] bytes=new byte[64];for(int band:bands)bytes[(band-1)/8]|=1<<((band-1)%8);
        StringBuilder h=new StringBuilder();for(byte b:bytes)h.append(String.format("%02x",b&255));return h.toString();
    }
    private static final String ORIGINAL=mask(1,28,78),TARGET=mask(78),NSA=mask(3);
    private static void check(boolean value) {if(!value)throw new AssertionError();}
    private static PreferenceJournal journal() {return new PreferenceJournal(ORIGINAL,NSA,TARGET,TARGET,-1,64,64,false);}
    private static final class Backend implements QuickTileTransaction.Backend {
        ArrayList<String> events=new ArrayList<>();int mode=16,saves;String sa=ORIGINAL,nsa=NSA,support=ORIGINAL,fault="";
        boolean expectedOn=true;
        public PreferenceJournal.Snapshot read() throws Exception {
            events.add("read");if(fault.equals("read"))throw new IOException("No root");return new PreferenceJournal.Snapshot(sa,nsa,mode);
        }
        public String supported() {return support;}
        public void clearReapply() {events.add("clear_boot");}
        public void save(PreferenceJournal j) throws Exception {
            events.add("save");saves++;
            if(saves==1) {
                check(j.expectedMode==mode&&j.expectedSa.equals(sa));
                check(j.originalSa.equals(ORIGINAL)&&j.originalNsa.equals(NSA)&&j.originalMode==mode);
                check(j.pendingMode==(expectedOn?64:16)&&j.pendingSa.equals(expectedOn?TARGET:sa));
                if(fault.equals("save"))throw new IOException("Storage full");
            }
        }
        public BootReapply.Result apply(PreferenceJournal j,String target,int targetMode,boolean on) throws Exception {
            events.add(on?"combined_set":"mode_only_set");check(on==expectedOn&&saves==1);
            check(j.pendingSa.equals(target)&&j.pendingMode==targetMode);
            if(fault.equals("lost_response"))throw new IOException("Root disconnected");
            if(fault.equals("stale"))return new BootReapply.Result(false,false,false,new PreferenceJournal.Snapshot(sa,nsa,mode));
            if(fault.equals("rollback"))return new BootReapply.Result(false,true,true,new PreferenceJournal.Snapshot(sa,nsa,mode));
            if(fault.equals("uncertain"))return new BootReapply.Result(false,false,true,null);
            return new BootReapply.Result(true,false,true,new PreferenceJournal.Snapshot(fault.equals("bad_result")?ORIGINAL:target,nsa,targetMode));
        }
        public void record() {events.add("record");}
    }
    public static void main(String[] args) throws Exception {
        check(QuickTilePolicy.LTE_ONLY==16&&QuickTilePolicy.NR_ONLY==64);
        for(int mode:new int[]{16,28,64,80,92})check(QuickTilePolicy.nextEnabled(mode)==(mode!=64));
        for(int mode:new int[]{-1,0,128}) {
            boolean failed=false;try {QuickTilePolicy.nextEnabled(mode);}catch(IllegalArgumentException e) {failed=true;}check(failed);
        }
        QuickTilePolicy.validate(TARGET,ORIGINAL,ORIGINAL);
        for(String target:new String[]{mask(),mask(258),"bad"}) {
            boolean failed=false;try {QuickTilePolicy.validate(target,ORIGINAL,ORIGINAL);}catch(IllegalArgumentException e) {failed=true;}check(failed);
        }
        // Default LTE -> NR enables with the configured mask in the same transaction.
        Backend b=new Backend();PreferenceJournal j=journal();
        check(QuickTileTransaction.run(j,TARGET,null,b).ok);
        check(b.events.toString().equals("[read, clear_boot, save, combined_set, record, save]"));
        check(j.originalMode==16&&j.expectedMode==64&&j.expectedSa.equals(TARGET)&&j.pendingSa.isEmpty());
        // NR -> LTE never resets or writes SA/NSA/LTE bands; no NR capability is needed to turn off.
        b=new Backend();b.mode=64;b.sa=TARGET;b.expectedOn=false;b.support="";j=journal();
        check(QuickTileTransaction.run(j,TARGET,null,b).ok);
        check(b.events.toString().equals("[read, clear_boot, save, mode_only_set, record, save]"));
        check(j.expectedMode==16&&j.expectedSa.equals(TARGET)&&j.originalMode==64);
        // Setup explicitly establishes LTE even when live mode is an automatic mode.
        b=new Backend();b.mode=92;b.expectedOn=false;j=journal();check(QuickTileTransaction.run(j,TARGET,false,b).ok);
        check(j.originalMode==92&&j.expectedMode==16&&j.expectedSa.equals(ORIGINAL));
        // Enabling from LTE doesn't capture the previously locked mask as an original backup.
        b=new Backend();b.sa=TARGET;j=journal();check(QuickTileTransaction.run(j,TARGET,true,b).ok);check(j.originalSa.equals(ORIGINAL));
        for(String fault:new String[]{"read","nsa_changed","unsupported","missing_mode","save","lost_response","bad_result"}) {
            b=new Backend();b.fault=fault;j=journal();
            if(fault.equals("nsa_changed"))b.nsa=TARGET;
            if(fault.equals("unsupported"))b.support=mask(28);
            if(fault.equals("missing_mode"))b.mode=-1;
            boolean failed=false;try {QuickTileTransaction.run(j,TARGET,null,b);}catch(Exception e) {failed=true;}check(failed);
            if(!fault.equals("lost_response")&&!fault.equals("bad_result"))check(!b.events.contains("combined_set"));
            if(fault.equals("lost_response")||fault.equals("bad_result"))check(j.pendingSa.equals(TARGET)&&j.pendingMode==64);
            if(fault.equals("nsa_changed")||fault.equals("unsupported"))check(!b.events.contains("clear_boot"));
        }
        for(String fault:new String[]{"stale","rollback","uncertain"}) {
            b=new Backend();b.fault=fault;j=journal();check(!QuickTileTransaction.run(j,TARGET,null,b).ok);
            check(b.events.toString().equals("[read, clear_boot, save, combined_set, record, save]"));
            check(j.pendingSa.isEmpty()!=fault.equals("uncertain"));
        }
        System.out.println("5G tile checks passed: live toggle, LTE default, combined NR bands, mode-only off, original backup, capability/NSA guards, journal and uncertain writes.");
    }
}
