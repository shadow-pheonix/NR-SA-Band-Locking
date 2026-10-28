package dev.saband.lab;

public class RadioModeSelectionTest {
    private static void check(boolean condition) {if(!condition)throw new AssertionError();}
    public static void main(String[] args) {
        check(RadioModeSelection.target(true,92,-1)==64);
        check(RadioModeSelection.target(false,92,-1)==92);
        check(RadioModeSelection.target(false,64,92)==92);
        check(RadioModeSelection.target(false,80,92)==80);
        check(RadioModeSelection.target(true,64,92)==64);
        for(int captured:new int[]{-1,0,64,128}) {
            boolean failed=false;try {RadioModeSelection.target(false,64,captured);}catch(IllegalArgumentException expected) {failed=true;}
            check(failed);
        }
        boolean failed=false;try {RadioModeSelection.target(true,-1,92);}catch(IllegalArgumentException expected) {failed=true;}
        check(failed);
        System.out.println("Pending NR-only selection checks passed; unchecking restores only a known captured fallback mode.");
    }
}
