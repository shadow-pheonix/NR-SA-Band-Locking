package dev.saband.lab;

/** A tile is a radio preference switch, not an observation of 5G coverage. */
public final class QuickTilePolicy {
    public static final int NR_ONLY=64, LTE_ONLY=16;
    public static boolean active(int mode) {return mode==NR_ONLY;}
    public static boolean nextEnabled(int mode) {
        if(mode<1||mode>127)throw new IllegalArgumentException("Live radio mode is unavailable. No change was sent.");
        return !active(mode);
    }
    public static void validate(String target,String original,String supported) {
        if(!mask(target)||!mask(original)||!mask(supported))throw new IllegalArgumentException("Read valid NR capabilities before setting up the tile.");
        int any=0;
        for(int i=0;i<128;i+=2) {
            int value=Integer.parseInt(target.substring(i,i+2),16);any|=value;
            if((value&~Integer.parseInt(original.substring(i,i+2),16))!=0||(value&~Integer.parseInt(supported.substring(i,i+2),16))!=0)
                throw new IllegalArgumentException("Tile bands exceed the captured preferences or reported NR support. Configure the tile again.");
        }
        if(any==0)throw new IllegalArgumentException("Choose at least one tile band.");
    }
    private static boolean mask(String value) {return value!=null&&value.matches("[0-9a-fA-F]{128}");}
}
