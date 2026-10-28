package dev.saband.lab;

import java.util.*;

/** A single reported NR measurement snapshot, not an exhaustive coverage scan. */
public final class FiveGBands {
    public static final long RECENT_MS = 30000;
    public static final class Cell {
        public final int[] bands;
        public final boolean registered;
        public final int arfcn, pci, rsrp, rsrq, sinr;
        public final long ageMs;
        public final String operator;
        public Cell(int[] bands, boolean registered, int arfcn, int pci,
                    int rsrp, int rsrq, int sinr, long ageMs, String operator) {
            TreeSet<Integer> clean=new TreeSet<>();
            if(bands!=null)for(int band:bands)if(band>0&&band<=512)clean.add(band);
            this.bands=new int[clean.size()];int i=0;for(int band:clean)this.bands[i++]=band;
            this.registered=registered;this.arfcn=arfcn;this.pci=pci;
            this.rsrp=rsrp;this.rsrq=rsrq;this.sinr=sinr;this.ageMs=ageMs;
            this.operator=operator==null?"unknown":operator;
        }
        public boolean recent() {return ageMs>=0&&ageMs<=RECENT_MS;}
    }
    public final List<Cell> cells;
    public FiveGBands(List<Cell> cells) {
        ArrayList<Cell> sorted=new ArrayList<>(cells);
        sorted.sort(Comparator.comparing((Cell c) -> !c.registered)
            .thenComparing(c -> !c.recent()).thenComparingInt(c -> c.rsrp==Integer.MAX_VALUE?Integer.MAX_VALUE:-c.rsrp));
        this.cells=Collections.unmodifiableList(sorted);
    }
    public String bandState(int band) {
        boolean serving=false,nearby=false,older=false;
        for(Cell cell:cells)for(int b:cell.bands)if(b==band) {
            if(!cell.recent())older=true;
            else if(cell.registered)serving=true;else nearby=true;
        }
        if(serving)return "Serving";
        if(nearby)return "Nearby cell";
        if(older)return "Older observation";
        return "Not observed";
    }
    public Set<Integer> bands(boolean registeredOnly) {
        TreeSet<Integer> result=new TreeSet<>();
        for(Cell cell:cells)if(!registeredOnly||cell.registered)for(int b:cell.bands)result.add(b);
        return result;
    }
    public static String names(Collection<Integer> bands) {
        ArrayList<String> result=new ArrayList<>();for(int band:bands)result.add("n"+band);
        return result.isEmpty()?"none reported":String.join(", ",result);
    }
    public static String measurement(int value,String unit) {
        return value==Integer.MAX_VALUE?"unavailable":value+unit;
    }
    public String render() {
        StringBuilder out=new StringBuilder("Detected 5G bands: ").append(names(bands(false)))
            .append("\nServing cell bands: ").append(names(bands(true))).append("\n");
        if(cells.isEmpty())out.append("No 5G cells reported. Enable Location and precise location permission, then refresh.\n");
        boolean nearby=false;
        for(Cell cell:cells) {
            TreeSet<Integer> b=new TreeSet<>();for(int band:cell.bands)b.add(band);
            out.append("\n").append(b.isEmpty()?"Band not reported":names(b)).append(" · ")
                .append(cell.registered?"Serving":"Nearby · not registered").append("\n");
            if(!cell.registered)nearby=true;
            out.append("Operator: ").append(cell.operator).append(" · NR-ARFCN: ").append(measurement(cell.arfcn,""))
                .append(" · PCI: ").append(measurement(cell.pci,"")).append("\n")
                .append("SS-RSRP: ").append(measurement(cell.rsrp," dBm"))
                .append(" · SS-RSRQ: ").append(measurement(cell.rsrq," dB"))
                .append(" · SS-SINR: ").append(measurement(cell.sinr," dB")).append("\n")
                .append(cell.ageMs<0?"Observation age unavailable":("Age at refresh: "+cell.ageMs/1000+"s"))
                .append(cell.recent()?"":" · older or undated sample").append("\n");
        }
        if(!nearby&&!cells.isEmpty())out.append("\nAndroid returned serving cells only; no nearby 5G cells were reported.\n");
        out.append("\nThis is the modem's reported cell list. It may omit nearby bands, other operators or extra aggregated carriers. Not observed does not mean unavailable. A nearby cell does not guarantee this SIM can use it.");
        return out.toString();
    }
}
