package dev.saband.lab;

/** Resolve the staged checkbox only when Apply is pressed. No modem I/O. */
public final class RadioModeSelection {
    public static int target(boolean nrOnly,int current,int captured) {
        if(current<1||current>127)throw new IllegalArgumentException("Read a valid radio mode before applying.");
        if(nrOnly)return 64;
        if(current!=64)return current;
        if(captured<1||captured>127||captured==64)throw new IllegalArgumentException("No earlier fallback mode was captured. Choose a network mode in mobile network settings, then read preferences again.");
        return captured;
    }
}
