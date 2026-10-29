package dev.saband.lab;

import java.util.*;

/** Test reports use timestamped registered cells, never checkbox/preference masks. */
public final class SpeedTestBands {
    public static final long MAX_AGE_MS=5000;
    public static int[] serving(int[] bands,boolean registered,long ageMs) {
        if(!registered||ageMs<0||ageMs>MAX_AGE_MS)return new int[0];
        TreeSet<Integer> valid=new TreeSet<>();if(bands!=null)for(int band:bands)if(band>0&&band<=1024)valid.add(band);
        int[] result=new int[valid.size()];int i=0;for(int b:valid)result[i++]=b;return result;
    }
    public static String names(int[] bands) {StringJoiner out=new StringJoiner(", ");for(int b:bands)out.add("n"+b);return bands.length==0?"unknown":out.toString();}
    public static String summary(List<int[]> samples) {
        TreeSet<Integer> seen=new TreeSet<>();boolean missing=false,changed=false;int[] previous=null;
        for(int[] sample:samples) {if(sample.length==0) {missing=true;continue;}for(int b:sample)seen.add(b);if(previous!=null&&!Arrays.equals(previous,sample))changed=true;previous=sample;}
        int[] union=new int[seen.size()];int i=0;for(int b:seen)union[i++]=b;
        return names(union)+(changed?" · bands changed between samples":"")+(missing&&!seen.isEmpty()?" · some samples unknown":"");
    }
}
