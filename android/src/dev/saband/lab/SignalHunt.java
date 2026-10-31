package dev.saband.lab;

import java.util.*;

/** Foreground NR hunt state. Times are modem observation times on the elapsed clock. */
public final class SignalHunt {
    public static final long FRESH_MS=5000,HISTORY_MS=60000;
    public static final int UNKNOWN=Integer.MAX_VALUE,MAX_POINTS=120,MAX_MARKS=20;
    public final int slot;
    public boolean running;
    public Sample current,best,baseline;
    public String message="Start a hunt to read this SIM's serving 5G signal.";
    private boolean observed;
    private long newestStamp=-1;private int segment;
    private final ArrayList<Point> history=new ArrayList<>();
    private final ArrayList<Sample> marks=new ArrayList<>();
    public SignalHunt(int slot) {if(slot<0||slot>1)throw new IllegalArgumentException();this.slot=slot;}
    public static final class Sample {
        public final int[] bands;public final int arfcn,pci,tac,rsrp,rsrq,sinr;
        public final long nci,stamp;public final String operator;
        public Sample(int[] bands,int arfcn,int pci,int tac,long nci,String operator,int rsrp,int rsrq,int sinr,long stamp) {
            TreeSet<Integer> clean=new TreeSet<>();if(bands!=null)for(int n:bands)if(n>0&&n<=512)clean.add(n);
            this.bands=new int[clean.size()];int i=0;for(int n:clean)this.bands[i++]=n;
            this.arfcn=arfcn>=0&&arfcn<=3279165?arfcn:UNKNOWN;this.pci=pci>=0&&pci<=1007?pci:UNKNOWN;
            this.tac=tac>=0&&tac<16777215?tac:UNKNOWN;this.nci=nci;this.operator=operator==null?"":operator;
            this.rsrp=validRsrp(rsrp)?rsrp:UNKNOWN;
            this.rsrq=rsrq>=-43&&rsrq<=20?rsrq:UNKNOWN;this.sinr=sinr>=-23&&sinr<=40?sinr:UNKNOWN;this.stamp=stamp;
        }
        public long age(long now) {return stamp>0&&stamp<=now?now-stamp:-1;}
        public boolean fresh(long now) {long age=age(now);return age>=0&&age<=FRESH_MS;}
        public String key() {
            // Do not call reused PCI alone a globally unique cell identity.
            if(!operator.matches("[0-9]{5,6}")||arfcn<0||arfcn==UNKNOWN||pci<0||pci>1007)return null;
            String identity=nci>=0&&nci<=(1L<<36)-1?"nci:"+nci:tac>=0&&tac<16777215?"tac:"+tac:null;
            return identity==null?null:operator+":"+identity+":"+arfcn+":"+pci+":"+Arrays.toString(bands);
        }
        public String bandLabel() {TreeSet<Integer> values=new TreeSet<>();for(int b:bands)values.add(b);return values.isEmpty()?"Band not reported":FiveGBands.names(values);}
    }
    public static final class Point {
        public final long stamp;public final int rsrp,segment;
        Point(Sample sample,int segment) {stamp=sample.stamp;rsrp=sample.rsrp;this.segment=segment;}
    }
    public static boolean validRsrp(int value) {return value>=-156&&value<=-31;}
    public static float fraction(int value) {return validRsrp(value)?Math.max(0,Math.min(1,(value+140)/80f)):0;}
    public static int toneHz(int value) {return 400+Math.round(fraction(value)*1000);}
    public void resume() {running=true;observed=false;segment++;message="Waiting for a fresh serving NR reading…";}
    public void pause() {running=false;segment++;message="Hunt paused. Press Resume to continue.";}
    public void unavailable(String reason) {if(!running)return;observed=false;segment++;message=reason;}
    public boolean fresh(long now) {return observed&&current!=null&&current.fresh(now);}
    public boolean canMark(long now) {return running&&fresh(now)&&current.rsrp!=UNKNOWN&&current.key()!=null;}
    /** Returns true only for a newly observed valid strength, not a repeated callback. */
    public boolean accept(Sample sample,long now) {
        if(!running)return false;
        if(sample==null||!sample.fresh(now)) {unavailable("Waiting for fresh serving NR data. Android may delay updates.");return false;}
        if(sample.stamp<newestStamp)return false;
        String key=sample.key(),previous=current==null?null:current.key();
        if(sample.stamp==newestStamp) {
            if(current!=null&&key!=null&&key.equals(previous))observed=true;
            return false; // A duplicate never advances history, best, sound or freshness.
        }
        boolean changed=current!=null&&!Objects.equals(previous,key);
        if(current==null||changed||key==null) {baseline=null;best=null;history.clear();segment++;}
        newestStamp=sample.stamp;current=sample;observed=true;
        message=changed?"Serving cell changed · comparison restarted.":key==null?"Cell identity incomplete · comparison unavailable.":"Hunting · fresh serving NR sample";
        if(sample.rsrp==UNKNOWN) {segment++;return false;}
        if(key!=null) {if(baseline==null)baseline=sample;if(best==null||sample.rsrp>best.rsrp)best=sample;}
        history.add(new Point(sample,segment));prune(now);return true;
    }
    private void prune(long now) {while(!history.isEmpty()&&(now-history.get(0).stamp>HISTORY_MS||history.size()>MAX_POINTS))history.remove(0);}
    public List<Point> points(long now) {prune(now);return Collections.unmodifiableList(new ArrayList<>(history));}
    public List<Sample> marks() {return Collections.unmodifiableList(new ArrayList<>(marks));}
    public boolean mark(long now) {if(!canMark(now))return false;marks.add(0,current);if(marks.size()>MAX_MARKS)marks.remove(marks.size()-1);return true;}
    public String status(long now) {
        if(!running)return message;
        if(!fresh(now))return observed?"Reading is stale · waiting for the modem":message;
        if(current.rsrp==UNKNOWN)return "Serving NR cell found · SS-RSRP not reported";
        return message;
    }
}
