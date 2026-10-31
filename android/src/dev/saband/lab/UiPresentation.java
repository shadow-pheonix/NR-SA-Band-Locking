package dev.saband.lab;

import java.util.TreeSet;
import java.util.ArrayList;

/** UI decisions only: observing a cell and choosing a preference stay distinct. */
public final class UiPresentation {
    public static final int OVERVIEW=0, BANDS=1, SIGNAL=2, SPEED=3, TOOLS=4, PAGE_COUNT=5;
    public static boolean canApply(boolean ready,boolean writeReady,boolean busy,int count,int mode) {
        return ready&&writeReady&&!busy&&count>0&&mode>0&&mode<128;
    }
    public static boolean dirty(String selected,String live,boolean nrOnly,int mode,boolean reboot,boolean savedReboot) {
        return !selected.equalsIgnoreCase(live)||nrOnly!=(mode==64)||reboot!=savedReboot;
    }
    public static String mode(int mode) {
        if(mode==64)return "NR only (5G)";
        if(mode==16)return "LTE only (4G)";
        if(mode>0&&mode<128)return (mode&64)!=0?"5G with fallback":"5G not enabled";
        return "Not read yet";
    }
    public static String selected(int count) {return count==1?"1 band selected":count+" bands selected";}
    public static String bandNames(String mask) {
        if(mask==null||!mask.matches("[0-9a-fA-F]{128}"))return "no explicit band bits";
        ArrayList<String> names=new ArrayList<>();
        for(int i=0;i<64;i++) {
            int value=Integer.parseInt(mask.substring(i*2,i*2+2),16);
            for(int bit=0;bit<8;bit++)if((value&(1<<bit))!=0)names.add("n"+(i*8+bit+1));
        }
        return names.isEmpty()?"no explicit band bits":String.join(", ",names);
    }
    public static String bandState(FiveGBands snapshot,int band,long elapsed) {
        if(snapshot==null)return "Not checked";
        boolean nearby=false,older=false;
        for(FiveGBands.Cell cell:snapshot.cells)for(int n:cell.bands)if(n==band) {
            if(!recent(cell,elapsed))older=true;else if(cell.registered)return "Serving";else nearby=true;
        }
        return nearby?"Nearby cell":older?"Older observation":"Not observed";
    }
    public static String pageTitle(int page) {return page==BANDS?"Band selection":page==SIGNAL?"Signal hunt":page==SPEED?"Speedtest":page==TOOLS?"Tools & recovery":"Network overview";}
    public static int restoredPage(int saved,int schema) {
        if(schema<2) {if(saved==2)return SPEED;if(saved==3)return TOOLS;if(saved<0||saved>3)return OVERVIEW;}
        return saved<0||saved>=PAGE_COUNT?OVERVIEW:saved;
    }
    public static final class Network {
        public final String band, label, rsrp, sinr, nearby, detail;
        public final boolean recent;
        public final int count;
        Network(String band,String label,String rsrp,String sinr,String nearby,String detail,boolean recent,int count) {
            this.band=band;this.label=label;this.rsrp=rsrp;this.sinr=sinr;this.nearby=nearby;this.detail=detail;this.recent=recent;this.count=count;
        }
    }
    public static Network network(FiveGBands snapshot,long elapsedSinceRefresh,boolean cached) {
        if(snapshot==null)return new Network("—","Not checked","—","—","Not checked","Refresh to see cells reported by your phone.",false,-1);
        FiveGBands.Cell serving=null;TreeSet<Integer> nearby=new TreeSet<>();boolean olderNearby=false;
        for(FiveGBands.Cell cell:snapshot.cells) {
            if(cell.registered&&(serving==null||!recent(serving,elapsedSinceRefresh)&&recent(cell,elapsedSinceRefresh)))serving=cell;
            if(!cell.registered) {
                if(recent(cell,elapsedSinceRefresh))for(int b:cell.bands)nearby.add(b);else olderNearby=true;
            }
        }
        String neighbors=nearby.isEmpty()?(olderNearby?"Older observations only":"None reported"):FiveGBands.names(nearby);
        if(serving==null)return new Network("—","No serving NR cell","—","—",neighbors,
                cached?"Cached data may include other SIMs.":"No registered 5G cell was reported in this refresh.",false,snapshot.cells.size());
        boolean fresh=recent(serving,elapsedSinceRefresh);TreeSet<Integer> bands=new TreeSet<>();for(int b:serving.bands)bands.add(b);
        String detail="NR-ARFCN "+number(serving.arfcn)+" · PCI "+number(serving.pci);
        if(cached)detail="Cached; may include other SIMs. "+detail;
        if(!fresh)detail="Older or undated sample. "+detail;
        return new Network(bands.isEmpty()?"Unknown":FiveGBands.names(bands),fresh?(cached?"Cached serving cell":"Serving at refresh"):"Previously observed",
                number(serving.rsrp),number(serving.sinr),neighbors,detail,fresh,snapshot.cells.size());
    }
    private static String number(int value) {return value==Integer.MAX_VALUE?"—":Integer.toString(value);}
    private static boolean recent(FiveGBands.Cell cell,long elapsed) {
        return elapsed>=0&&cell.ageMs>=0&&cell.ageMs<=FiveGBands.RECENT_MS&&elapsed<=FiveGBands.RECENT_MS-cell.ageMs;
    }
}
