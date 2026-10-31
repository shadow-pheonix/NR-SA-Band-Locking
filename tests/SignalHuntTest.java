package dev.saband.lab;

import java.util.*;

public final class SignalHuntTest {
    private static void check(boolean value) {if(!value)throw new AssertionError();}
    private static SignalHunt.Sample sample(long stamp,long nci,int rsrp,int sinr) {
        return new SignalHunt.Sample(new int[]{78},634080,210,123,nci,"00101",rsrp,-11,sinr,stamp);
    }
    public static void main(String[] args) {
        SignalHunt hunt=new SignalHunt(0);check(!hunt.running&&!hunt.canMark(10000));hunt.resume();
        check(hunt.accept(sample(10000,1,-110,10),10000));check(hunt.baseline.rsrp==-110&&hunt.best.rsrp==-110);
        check(hunt.fresh(15000)&&hunt.canMark(15000)&&!hunt.fresh(15001)&&!hunt.canMark(15001));
        check(hunt.mark(10001)&&hunt.marks().size()==1&&hunt.marks().get(0).rsrp==-110);
        // Duplicate callbacks never append data or improve best, even if their values differ.
        check(!hunt.accept(sample(10000,1,-80,20),10001));check(hunt.current.rsrp==-110&&hunt.points(10001).size()==1);
        check(!hunt.accept(sample(9999,2,-70,20),10001));check(hunt.current.nci==1);
        check(hunt.accept(sample(12000,1,-98,7),12000));check(hunt.best.rsrp==-98&&hunt.baseline.rsrp==-110);
        check(hunt.current.sinr==7); // Strength and quality are independent, never derived.
        check(hunt.accept(sample(14000,1,-106,18),14000));check(hunt.best.rsrp==-98&&hunt.current.rsrp==-106);
        check(hunt.accept(sample(16000,2,-112,16),16000));check(hunt.best.rsrp==-112&&hunt.baseline.rsrp==-112&&hunt.points(16000).size()==1);
        check(hunt.message.contains("changed")&&hunt.marks().get(0).nci==1);
        // Changed band/channel/context also restarts comparisons, even for an unchanged NCI.
        check(hunt.accept(new SignalHunt.Sample(new int[]{28},155000,210,123,2,"00101",-100,-10,12,18000),18000));
        check(hunt.baseline.rsrp==-100&&hunt.points(18000).size()==1);
        hunt.pause();check(!hunt.canMark(18001)&&!hunt.accept(sample(19000,2,-80,20),19000));
        hunt.resume();check(!hunt.fresh(18001));
        check(!hunt.accept(new SignalHunt.Sample(new int[]{28},155000,210,123,2,"00101",-100,-10,12,18000),18001));
        check(hunt.fresh(18001)&&hunt.points(18001).size()==1); // Repeat can be observed again, age is not reset.
        check(!hunt.fresh(23001));
        hunt.unavailable("No serving NR cell");check(!hunt.fresh(18001)&&!hunt.canMark(18001));
        check(!hunt.accept(sample(25000,2,-80,20),24999));check(!hunt.canMark(24999)); // Future modem timestamp.
        check(!hunt.accept(sample(1,2,-80,20),10000));check(!hunt.accept(sample(0,2,-80,20),10000));
        hunt=new SignalHunt(1);hunt.resume();
        check(!hunt.accept(sample(10000,1,Integer.MAX_VALUE,Integer.MAX_VALUE),10000));
        check(hunt.fresh(10001)&&hunt.current.rsrp==SignalHunt.UNKNOWN&&hunt.current.sinr==SignalHunt.UNKNOWN&&hunt.best==null&&!hunt.canMark(10001));
        check(hunt.status(10001).contains("not reported"));
        check(hunt.accept(sample(12000,1,-100,14),12000));
        check(hunt.best.rsrp==-100&&hunt.points(12000).size()==1);
        int segment=hunt.points(12000).get(0).segment;hunt.unavailable("No fresh sample");
        check(hunt.accept(sample(14000,1,-99,13),14000));check(hunt.points(14000).get(1).segment!=segment);
        hunt.pause();hunt.resume();check(hunt.accept(sample(16000,1,-98,13),16000));
        check(hunt.points(16000).get(2).segment!=hunt.points(16000).get(1).segment);
        for(int i=0;i<30;i++) {long time=18000+i*100;hunt.accept(sample(time,1,-100+i%5,11),time);check(hunt.mark(time));}
        check(hunt.marks().size()==20&&hunt.marks().get(0).stamp==20900);
        for(int i=0;i<200;i++)hunt.accept(sample(22000+i*100,1,-105,12),22000+i*100);
        check(hunt.points(42000).size()==SignalHunt.MAX_POINTS);check(hunt.points(103000).isEmpty());
        SignalHunt.Sample incomplete=new SignalHunt.Sample(new int[]{78},634080,210,Integer.MAX_VALUE,Long.MAX_VALUE,"00101",-90,-999,999,200000);
        hunt=new SignalHunt(0);hunt.resume();check(hunt.accept(incomplete,200000)&&hunt.fresh(200000));
        check(incomplete.key()==null&&incomplete.rsrq==SignalHunt.UNKNOWN&&incomplete.sinr==SignalHunt.UNKNOWN&&hunt.best==null&&!hunt.canMark(200000));
        SignalHunt.Sample fallback=new SignalHunt.Sample(new int[]{78},634080,210,123,Long.MAX_VALUE,"00101",-90,-11,14,202000);
        check(fallback.key()!=null&&hunt.accept(fallback,202000)&&hunt.canMark(202000));
        int[] bands={78,78,0,28,999};SignalHunt.Sample sorted=new SignalHunt.Sample(bands,634080,210,123,2,"00101",-100,-11,18,204000);bands[0]=1;
        check(Arrays.equals(sorted.bands,new int[]{28,78})&&sorted.bandLabel().equals("n28, n78"));
        check(new SignalHunt.Sample(null,-1,2000,-1,-1,"unknown",-1,30,80,0).rsrp==SignalHunt.UNKNOWN);
        check(SignalHunt.validRsrp(-156)&&SignalHunt.validRsrp(-31)&&!SignalHunt.validRsrp(-157)&&!SignalHunt.validRsrp(0));
        check(SignalHunt.fraction(-156)==0&&SignalHunt.fraction(-31)==1&&SignalHunt.fraction(-90)>SignalHunt.fraction(-110));
        check(SignalHunt.toneHz(-90)>SignalHunt.toneHz(-110));
        check(UiPresentation.PAGE_COUNT==5&&UiPresentation.SIGNAL==2&&UiPresentation.SPEED==3&&UiPresentation.TOOLS==4);
        check(UiPresentation.restoredPage(2,0)==UiPresentation.SPEED&&UiPresentation.restoredPage(3,0)==UiPresentation.TOOLS);
        check(UiPresentation.restoredPage(2,2)==UiPresentation.SIGNAL&&UiPresentation.restoredPage(99,2)==0);
        System.out.println("Signal hunt checks passed: timestamp freshness, duplicate/out-of-order rejection, independent quality, cell/band reset, gaps, marking bounds, missing identity/values and navigation migration.");
    }
}
