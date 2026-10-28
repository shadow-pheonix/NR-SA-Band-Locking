package dev.saband.lab;

/** Only transient preflight failures may be retried automatically. */
public final class BootPolicy {
    public static final int MAX_ATTEMPTS=3;
    public static boolean retry(int attempt,boolean preflightFailed,boolean mutationStarted,boolean cancelled) {
        return !cancelled&&preflightFailed&&!mutationStarted&&attempt+1<MAX_ATTEMPTS;
    }
    public static boolean verified(String target,int mode,boolean ok,String actual,int actualMode) {
        return ok&&target!=null&&target.matches("[0-9a-fA-F]{128}")&&mode>0&&mode<128&&target.equalsIgnoreCase(actual)&&mode==actualMode;
    }
}
