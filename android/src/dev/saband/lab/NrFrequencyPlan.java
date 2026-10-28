package dev.saband.lab;

import java.util.*;

/** Downlink band ranges for the target phone's 16 reported bands.
 * Protocol facts: 3GPP TS 38.104 / AOSP AccessNetworkUtils.
 * Return every supported candidate: NR-ARFCN alone cannot resolve overlaps. */
public final class NrFrequencyPlan {
    private static final int[][] RANGES={
        {1,422000,434000},{3,361000,376000},{5,173800,178800},
        {7,524000,538000},{8,185000,192000},{20,158200,164200},
        {28,151600,160600},{38,514000,524000},{40,460000,480000},
        {41,499200,537999},{66,422000,440000},{71,123400,130400},
        {75,286400,303400},{77,620000,680000},{78,620000,653333},
        {79,693334,733333}
    };
    public static List<Integer> candidates(long arfcn,String supportedHex) {
        ArrayList<Integer> result=new ArrayList<>();
        if(supportedHex==null||!supportedHex.matches("[0-9a-fA-F]{128}"))return result;
        for(int[] range:RANGES) {
            int n=range[0],at=((n-1)/8)*2;
            boolean supported=(Integer.parseInt(supportedHex.substring(at,at+2),16)&(1<<((n-1)%8)))!=0;
            if(supported&&arfcn>=range[1]&&arfcn<=range[2])result.add(n);
        }
        return result;
    }
    public static String label(long arfcn,String supportedHex) {
        List<Integer> candidates=candidates(arfcn,supportedHex);
        return candidates.isEmpty()?"band unresolved":("possible "+FiveGBands.names(candidates));
    }
}
