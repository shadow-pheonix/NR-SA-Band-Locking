package dev.saband.lab;

import java.util.Arrays;
import java.util.Collections;

public final class UiPresentationTest {
    private static void check(boolean condition) {if(!condition)throw new AssertionError();}
    private static FiveGBands.Cell cell(int band,boolean registered,long age,int rsrp) {
        return new FiveGBands.Cell(new int[]{band},registered,634080,185,rsrp,-11,18,age,"example");
    }
    public static void main(String[] args) {
        check(UiPresentation.canApply(true,true,false,1,64));
        check(!UiPresentation.canApply(true,true,false,0,64));
        check(!UiPresentation.canApply(true,true,true,1,64));
        check(!UiPresentation.canApply(false,true,false,1,64));
        check(!UiPresentation.canApply(true,false,false,1,64));
        for(int mode:new int[]{-1,0,128})check(!UiPresentation.canApply(true,true,false,1,mode));
        check(!UiPresentation.dirty("ab","AB",true,64,false,false));
        check(UiPresentation.dirty("ab","cd",true,64,false,false));
        check(UiPresentation.dirty("ab","ab",false,64,false,false));
        check(UiPresentation.dirty("ab","ab",true,64,true,false));
        check(UiPresentation.mode(64).equals("NR only (5G)"));
        check(UiPresentation.mode(28).equals("5G not enabled"));
        check(UiPresentation.mode(92).equals("5G with fallback"));
        char[] mask=new char[128];Arrays.fill(mask,'0');mask[18]='2';
        check(UiPresentation.bandNames(new String(mask)).equals("n78"));
        mask[1]='1';mask[126]='8'; // Byte 63 bit 7 is n512; byte 0 bit 0 is n1.
        check(UiPresentation.bandNames(new String(mask)).equals("n1, n78, n512"));
        check(UiPresentation.bandNames("invalid").equals("no explicit band bits"));
        UiPresentation.Network empty=UiPresentation.network(null,0,false);
        check(empty.band.equals("—")&&!empty.recent&&empty.count==-1);
        FiveGBands report=new FiveGBands(Arrays.asList(cell(78,true,20,-105),cell(28,false,100,-110)));
        UiPresentation.Network fresh=UiPresentation.network(report,0,false);
        check(fresh.band.equals("n78")&&fresh.nearby.equals("n28")&&fresh.recent&&fresh.count==2);
        check(fresh.rsrp.equals("-105")&&fresh.sinr.equals("18"));
        check(UiPresentation.bandState(report,78,0).equals("Serving"));
        check(UiPresentation.bandState(report,28,0).equals("Nearby cell"));
        check(UiPresentation.bandState(report,258,0).equals("Not observed"));
        UiPresentation.Network aged=UiPresentation.network(report,31000,false);
        check(!aged.recent&&aged.label.equals("Previously observed"));
        check(aged.nearby.equals("Older observations only"));
        check(UiPresentation.bandState(report,78,31000).equals("Older observation"));
        check(UiPresentation.network(report,0,true).detail.contains("other SIMs"));
        check(UiPresentation.network(report,0,true).label.equals("Cached serving cell"));
        FiveGBands multiple=new FiveGBands(Arrays.asList(cell(1,true,25000,-80),cell(78,true,100,-110)));
        check(UiPresentation.network(multiple,10000,false).band.equals("n78"));
        FiveGBands undated=new FiveGBands(Collections.singletonList(cell(78,true,-1,Integer.MAX_VALUE)));
        check(!UiPresentation.network(undated,0,false).recent);
        check(UiPresentation.network(undated,0,false).rsrp.equals("—"));
        FiveGBands nearby=new FiveGBands(Collections.singletonList(cell(28,false,10,-100)));
        check(UiPresentation.network(nearby,0,false).band.equals("—")); // Neighbor is never promoted to serving.
        FiveGBands old=new FiveGBands(Collections.singletonList(cell(78,true,Long.MAX_VALUE,-100)));
        check(!UiPresentation.network(old,Long.MAX_VALUE,false).recent); // No age overflow.
        check(UiPresentation.network(new FiveGBands(Collections.emptyList()),0,false).label.equals("No serving NR cell"));
        System.out.println("UI presentation checks passed: Apply gates, drafts, radio labels, serving/nearby separation, stale and cached observations.");
    }
}
