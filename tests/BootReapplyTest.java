package dev.saband.lab;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class BootReapplyTest {
    private static String mask(int... bands) {
        byte[] bytes=new byte[64];for(int band:bands)bytes[(band-1)/8]|=1<<((band-1)%8);
        StringBuilder value=new StringBuilder();for(byte b:bytes)value.append(String.format("%02x",b&255));return value.toString();
    }
    private static final String ORIGINAL=mask(1,28,78),TARGET=mask(78),NSA=mask(3);
    private static void check(boolean value) {if(!value)throw new AssertionError();}
    private static PreferenceJournal journal() {return new PreferenceJournal(ORIGINAL,NSA,TARGET,TARGET,28,28,64,true);}
    private static class Backend implements BootReapply.Backend {
        ArrayList<String> events=new ArrayList<>();AtomicBoolean cancelled=new AtomicBoolean();
        String fault="";int saves;
        public PreferenceJournal.Snapshot read() throws Exception {
            events.add("read");if(fault.equals("not_ready"))throw new IOException("NAS not ready");
            if(fault.equals("cancel_after_read"))cancelled.set(true);
            return new PreferenceJournal.Snapshot(ORIGINAL,fault.equals("nsa_changed")?TARGET:NSA,fault.equals("missing_mode")?-1:28);
        }
        public void save(PreferenceJournal j) {
            events.add("save");saves++;
            if(saves==1) {check(j.expectedSa.equals(ORIGINAL)&&j.expectedMode==28);check(j.pendingSa.equals(TARGET)&&j.pendingMode==64);}
            if(fault.equals("cancel_after_save"))cancelled.set(true);
        }
        public BootReapply.Result apply(PreferenceJournal j,String target,int mode) throws Exception {
            events.add("apply");check(saves==1);check(j.expectedSa.equals(ORIGINAL)&&j.expectedMode==28);
            check(j.originalSa.equals(ORIGINAL)&&j.originalNsa.equals(NSA)&&j.originalMode==28);
            check(target.equals(TARGET)&&mode==64);
            if(fault.equals("lost_response"))throw new IOException("helper disconnected");
            if(fault.equals("stale"))return new BootReapply.Result(false,false,false,new PreferenceJournal.Snapshot(TARGET,NSA,28));
            if(fault.equals("uncertain"))return new BootReapply.Result(false,false,true,new PreferenceJournal.Snapshot(ORIGINAL,NSA,28));
            return new BootReapply.Result(true,false,true,new PreferenceJournal.Snapshot(TARGET,NSA,64));
        }
        public void record() {events.add("record");}
    }
    public static void main(String[] args) throws Exception {
        Backend b=new Backend();PreferenceJournal j=journal();
        check(BootReapply.run(j,TARGET,64,b,b.cancelled::get).ok);
        check(b.events.toString().equals("[read, save, apply, record, save]"));
        check(j.expectedSa.equals(TARGET)&&j.expectedMode==64&&j.pendingSa.isEmpty()&&j.modeDirty);

        for(String fault:new String[]{"not_ready","nsa_changed","missing_mode","lost_response"}) {
            b=new Backend();b.fault=fault;j=journal();boolean failed=false;
            try {BootReapply.run(j,TARGET,64,b,b.cancelled::get);}catch(BootReapply.Failure expected) {
                failed=true;check(expected.preflightFailed==fault.equals("not_ready"));
                check(expected.mutationStarted==fault.equals("lost_response"));
                check(BootPolicy.retry(0,expected.preflightFailed,expected.mutationStarted,false)==fault.equals("not_ready"));
            }
            check(failed);check(j.pendingSa.equals(TARGET));
            if(!fault.equals("lost_response"))check(!b.events.contains("apply"));
        }
        for(String fault:new String[]{"stale","uncertain"}) {
            b=new Backend();b.fault=fault;j=journal();
            check(!BootReapply.run(j,TARGET,64,b,b.cancelled::get).ok);
            check(j.pendingSa.isEmpty()==fault.equals("stale"));
            check(b.events.toString().equals("[read, save, apply, record, save]")); // One attempt only.
        }
        for(String fault:new String[]{"cancel_before_read","cancel_after_read","cancel_after_save"}) {
            b=new Backend();b.fault=fault;j=journal();if(fault.equals("cancel_before_read"))b.cancelled.set(true);
            check(BootReapply.run(j,TARGET,64,b,b.cancelled::get)==null);check(!b.events.contains("apply"));
            if(fault.equals("cancel_after_save"))check(j.pendingSa.isEmpty()&&j.pendingMode==-1&&b.saves==2);
        }
        check(BootPolicy.retry(0,true,false,false)&&BootPolicy.retry(1,true,false,false));
        check(!BootPolicy.retry(2,true,false,false)&&!BootPolicy.retry(0,true,true,false)&&!BootPolicy.retry(0,true,false,true));
        check(BootPolicy.verified(TARGET,64,true,TARGET,64));
        check(!BootPolicy.verified(TARGET,64,false,TARGET,64)&&!BootPolicy.verified(TARGET,64,true,ORIGINAL,64)&&!BootPolicy.verified(TARGET,64,true,TARGET,28));
        System.out.println("Boot reapply checks passed: fresh preflight, saved intent before SET, verification, cancellation and bounded preflight-only retries.");
    }
}
