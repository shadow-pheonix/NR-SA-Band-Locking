package dev.saband.lab;

import java.util.*;

/** Host checks for honest classification of measured bands, with no modem I/O. */
public class FiveGBandsTest {
    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
    private static FiveGBands.Cell cell(int[] bands,boolean registered,long age) {
        return new FiveGBands.Cell(bands,registered,634080,100,-95,-12,18,age,"405xxx");
    }
    public static void main(String[] args) {
        FiveGBands snapshot=new FiveGBands(Arrays.asList(cell(new int[]{78},true,0),cell(new int[]{28},false,1000)));
        check(snapshot.bands(false).equals(new TreeSet<>(Arrays.asList(28,78))),"Nearby n28 must be included, even though serving is n78");
        check(snapshot.bands(true).equals(Collections.singleton(78)),"Neighbor must not be claimed as serving");
        check(snapshot.bandState(28).equals("Nearby cell")&&snapshot.bandState(78).equals("Serving"),"Serving and neighbor classification");
        check(snapshot.bandState(258).equals("Not observed"),"No fabricated band observation");
        check(snapshot.render().contains("Nearby · not registered"),"Neighbor must be visible in rendered results");
        snapshot=new FiveGBands(Arrays.asList(cell(new int[]{28},true,60000),cell(new int[]{78},false,-1)));
        check(snapshot.bandState(28).equals("Older observation")&&snapshot.bandState(78).equals("Older observation"),"Stale and unknown-age samples must not be presented as recent availability");
        snapshot=new FiveGBands(Arrays.asList(cell(new int[]{28},true,60000),cell(new int[]{28},false,1000)));
        check(snapshot.bandState(28).equals("Nearby cell"),"Old registration must not override recent neighbor measurement");
        FiveGBands.Cell unknown=new FiveGBands.Cell(new int[0],false,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,-1,null);
        snapshot=new FiveGBands(Collections.singletonList(unknown));
        check(snapshot.cells.size()==1&&snapshot.bands(false).isEmpty(),"Cells without a band must be retained without inventing a band");
        check(snapshot.render().contains("Band not reported")&&snapshot.render().contains("SS-RSRP: unavailable"),"Missing measurements must be explicit");
        check(!snapshot.render().contains("2147483647"),"Unavailable sentinel must not look like a measurement");
        int[] input={78,28,78,0,-1,Integer.MAX_VALUE};FiveGBands.Cell clean=cell(input,true,0);input[0]=258;
        check(Arrays.equals(clean.bands,new int[]{28,78}),"Invalid values, duplicate bands and mutable input must not corrupt observations");
        snapshot=new FiveGBands(Collections.emptyList());
        check(snapshot.bandState(28).equals("Not observed")&&snapshot.render().contains("No 5G cells reported"),"Empty lists are inconclusive");
        check(snapshot.render().contains("does not mean unavailable"),"Absence must not be described as absent coverage");
        String hw="d5000808a00100004274000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
        check(NrFrequencyPlan.candidates(155000,hw).equals(Collections.singletonList(28)),"A lower n28 downlink must resolve to supported n28");
        check(NrFrequencyPlan.candidates(634080,hw).equals(Arrays.asList(77,78)),"Overlapping n77/n78 must stay ambiguous");
        check(NrFrequencyPlan.candidates(159000,hw).equals(Arrays.asList(20,28)),"Overlapping n20/n28 must stay ambiguous");
        check(NrFrequencyPlan.candidates(634080,"").isEmpty(),"No hardware support must not become assumed support");
        check(NrFrequencyPlan.candidates(-1,hw).isEmpty()&&NrFrequencyPlan.candidates(99999999,hw).isEmpty(),"Invalid channels must not resolve");
        check(NrFrequencyPlan.candidates(2050000,hw).isEmpty(),"Unsupported mmWave must not be offered");
        check(NrFrequencyPlan.label(155000,hw).startsWith("possible"),"Derived candidates must not be labeled measured bands");
        System.out.println("5G observation checks passed: serving/neighbor bands, no fabricated detections, stale/undated data, missing fields, duplicate bands and empty results.");
    }
}
