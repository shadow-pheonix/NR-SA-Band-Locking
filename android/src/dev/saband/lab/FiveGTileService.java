package dev.saband.lab;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;
import org.json.JSONObject;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only while the panel opens; explicit, unlocked taps make temporary changes. */
public final class FiveGTileService extends TileService {
    private static final AtomicBoolean RUNNING=new AtomicBoolean();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static volatile FiveGTileService visible;
    private boolean listening;
    private static int icon(Context c) {return c.getResources().getIdentifier("ic_tile_5g","drawable",c.getPackageName());}
    public static ComponentName component(Context c) {return new ComponentName(c,FiveGTileService.class);}
    public static void refresh() {MAIN.post(() -> {FiveGTileService s=visible;if(s!=null)s.readState();});}
    @Override public void onStartListening() {super.onStartListening();listening=true;visible=this;readState();}
    @Override public void onStopListening() {listening=false;if(visible==this)visible=null;super.onStopListening();}
    @Override public void onDestroy() {listening=false;if(visible==this)visible=null;super.onDestroy();}
    @Override public void onClick() {super.onClick();unlockAndRun(() -> run(true));}
    private void readState() {run(false);}
    private void run(boolean mutate) {
        if(!RUNNING.compareAndSet(false,true)) {show(Tile.STATE_UNAVAILABLE,"Working…");return;}
        show(Tile.STATE_UNAVAILABLE,mutate?"Applying…":"Reading…");
        Context c=getApplicationContext();
        new Thread(() -> {
            JSONObject plan=null,result=null;String error=null;boolean setup=false,locked=false;
            try {
                if(!PreferenceStore.LOCK.tryLock())throw new IllegalStateException("App busy · retry after operation");
                locked=true;
                plan=QuickTileStore.load(c);
                if(plan==null)setup=true;
                else {
                    QuickTileStore.backup(c,plan);
                    result=mutate?QuickTileStore.apply(c,null):RootHelper.invoke(c,"qmi-probe",""+plan.getInt("slot"));
                    if(!result.optBoolean("ok"))error=result.optString("error","Read preferences before retrying.");
                }
            } catch(Exception e) {error=e.getMessage();}
            finally {if(locked)PreferenceStore.LOCK.unlock();}
            final JSONObject p=plan,r=result;final String failed=error;final boolean needsSetup=setup;
            MAIN.post(() -> {
                RUNNING.set(false);
                FiveGTileService current=visible;
                if(current!=null) {
                    if(needsSetup)current.show(Tile.STATE_INACTIVE,"Set up in Band Lab");
                    else if(failed!=null)current.show(Tile.STATE_INACTIVE,"Unknown · tap to retry");
                    else current.observed(p,r);
                }
                if(mutate) {
                    if(needsSetup)openSetup();
                    else Toast.makeText(c,failed!=null?"5G tile: "+failed:QuickTilePolicy.active(r.optInt("rat_mode_preference",-1))?"NR only and tile bands verified":"LTE only verified",Toast.LENGTH_LONG).show();
                }
            });
        },mutate?"sa-tile-apply":"sa-tile-read").start();
    }
    private void observed(JSONObject p,JSONObject r) {
        int mode=r.optInt("rat_mode_preference",-1);
        if(mode<1||mode>127) {show(Tile.STATE_INACTIVE,"Unknown · tap to retry");return;}
        String sim="SIM "+(p.optInt("slot")+1)+" · ";
        boolean on=QuickTilePolicy.active(mode);
        show(on?Tile.STATE_ACTIVE:Tile.STATE_INACTIVE,sim+(on?(p.optString("target_sa").equalsIgnoreCase(r.optString("sa_hex"))?"NR only":"NR · other bands"):mode==16?"LTE only":"Other mode"));
    }
    private void show(int state,String subtitle) {
        if(!listening)return;
        Tile tile=getQsTile();if(tile==null)return;
        tile.setIcon(Icon.createWithResource(this,icon(this)));tile.setLabel("5G");tile.setState(state);tile.setSubtitle(subtitle);
        tile.setContentDescription("5G, "+subtitle+". On applies tile bands and NR only. Off applies LTE only.");
        try {tile.updateTile();}catch(RuntimeException ignored) {}
    }
    @SuppressWarnings("deprecation") private void openSetup() {
        Intent intent=new Intent(this,MainActivity.class).setAction(TileService.ACTION_QS_TILE_PREFERENCES)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            if(Build.VERSION.SDK_INT>=34)startActivityAndCollapse(PendingIntent.getActivity(this,7816,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
            else startActivityAndCollapse(intent);
        } catch(RuntimeException e) {Toast.makeText(this,"Open Band Lab → Tools → Configure 5G tile",Toast.LENGTH_LONG).show();}
    }
}
