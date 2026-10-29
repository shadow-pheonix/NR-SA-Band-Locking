package dev.saband.lab;

/** Monotonic logarithmic dial positions; numerical readout is never capped. */
public final class SpeedometerScale {
    public static double fraction(double mbps) {
        if(Double.isNaN(mbps)||mbps<=0)return 0;
        return Math.min(1,Math.log1p(mbps/10)/Math.log1p(500));
    }
}
