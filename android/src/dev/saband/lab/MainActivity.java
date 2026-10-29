package dev.saband.lab;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.WindowInsetsController;
import android.os.Build;
import android.os.Bundle;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoNr;
import android.telephony.CellSignalStrengthNr;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.AtomicFile;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Experimental SA preference controller. All modem I/O is in the root helper. */
public class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayList<Button> actions = new ArrayList<>();
    private final ArrayList<CheckBox> bands = new ArrayList<>();
    private LabLayout screen;
    private GridLayout bandBox;
    private TextView status, current, radio, supported, modemRadio;
    private Button apply, restore, selectAll, deselectAll;
    private Switch nrOnly, persistLock;
    private TextView bootStatus;
    private JSONObject activeBootPlan, lastBootResult;
    private boolean busy;
    private volatile boolean alive = true;
    private volatile int slot;
    private JSONObject probe, backup;
    private JSONObject lastHelperResult;
    private JSONObject lastBandObservation;
    private JSONObject lastModemFrequencies;
    private JSONObject lastChange, lastEfsResult;
    private FiveGBands bandSnapshot;
    private String lastReport = "No root operation has run.";
    private boolean bindingUi;
    private int[] restoredBands;
    private boolean restoredNr,restoredReboot;
    private int restoredBandScroll=-1;
    private long bandObservedElapsed=-1;
    private SpeedTestSession speedSession;
    private boolean speedRunning;
    private JSONArray speedHistory=new JSONArray();
    private final Handler uiHandler=new Handler(Looper.getMainLooper());
    private final Runnable ageTick=new Runnable() {public void run() {if(alive) {refreshPresentation();refreshBandLabels();uiHandler.postDelayed(this,15000);}}};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(LabLayout.BG);getWindow().setNavigationBarColor(LabLayout.SURFACE);
        getWindow().setNavigationBarContrastEnforced(false);
        slot=state==null?0:Math.max(0,Math.min(1,state.getInt("slot",0)));
        screen=new LabLayout(this,new LabLayout.Actions() {
            public void selectSim(int next) {selectSlot(next);}
            public void read() {readPreferences();}
            public void refreshNetwork() {servingState();}
            public void apply() {confirmApply();}
            public void restore() {confirmRestore();}
            public void selectAll() {selectBands(true);}
            public void clearAll() {selectBands(false);}
            public void copyReport() {MainActivity.this.copyReport();}
            public void exportBackup() {exportBackups();}
            public void importBackup() {importBackups();}
            public void disableReapply() {disableRebootReapply();}
            public void frequencies() {modemFrequencies();}
            public void efs() {checkLegacyEfs();}
            public void networkSettings() {openNetworkSettings();}
            public void startSpeed() {confirmSpeedTest();}
            public void stopSpeed() {stopSpeedTest();}
            public void copySpeed() {copySpeedReports();}
            public void clearSpeed() {clearSpeedHistory();}
            public void draftChanged() {if(!bindingUi)refreshEnabled();}
            public void statusDetails() {new AlertDialog.Builder(MainActivity.this).setTitle("Operation details").setMessage(status.getText()).setPositiveButton("Close",null).setNeutralButton("Copy report",(d,w) -> MainActivity.this.copyReport()).show();}
        });
        actions.addAll(screen.actions);bandBox=screen.bandBox;status=screen.status;current=screen.current;radio=screen.radio;
        supported=screen.supported;modemRadio=screen.modemRadio;bootStatus=screen.bootStatus;
        nrOnly=screen.nrOnly;persistLock=screen.persistLock;apply=screen.apply;restore=screen.restore;selectAll=screen.selectAll;deselectAll=screen.deselectAll;
        setContentView(screen.root);
        screen.speedDuration.setSelection(getPreferences(MODE_PRIVATE).getInt("speed_seconds",20)==60?1:0);
        WindowInsetsController bars=getWindow().getInsetsController();if(bars!=null)bars.setSystemBarsAppearance(0,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        resetSlot(slot);
        if(state!=null) {
            restoredBands=state.getIntArray("draft_bands");restoredNr=state.getBoolean("draft_nr");restoredReboot=state.getBoolean("draft_reboot");
            restoredBandScroll=state.getInt("scroll_"+UiPresentation.BANDS,0);
            screen.showPage(state.getInt("page",UiPresentation.OVERVIEW));
            for(int page=0;page<UiPresentation.PAGE_COUNT;page++)screen.restoreScroll(page,state.getInt("scroll_"+page,0));
            if(restoredBands!=null)status.setText("Read preferences again to restore your previous selection for review.");
        }
        refreshEnabled();
    }
    private void selectSlot(int next) {
        if(busy||next==slot)return;
        if(hasDraft())new AlertDialog.Builder(this).setTitle("Switch SIM?").setMessage("Your unapplied band and radio choices will be cleared. The modem will not be changed.")
                .setNegativeButton("Keep editing",null).setPositiveButton("Switch SIM",(d,w) -> resetSlot(next)).show();
        else resetSlot(next);
    }
    private void resetSlot(int next) {
        bindingUi=true;
        slot=next;probe=null;backup=null;bandBox.removeAllViews();bands.clear();restoredBands=null;restoredBandScroll=-1;
        lastHelperResult=null;lastReport="No root operation has run for this SIM.";
        lastChange=null;lastEfsResult=null;nrOnly.setChecked(false);persistLock.setChecked(false);activeBootPlan=null;lastBootResult=null;
        lastBandObservation=null;bandSnapshot=null;bandObservedElapsed=-1;lastModemFrequencies=null;
        modemRadio.setText("Optional query. Some firmware does not support neighbor-frequency discovery.");
        radio.setText("Refresh network to see serving and nearby cells reported for this SIM.");
        supported.setText("Read preferences to load your phone's reported NR band support.");
        current.setText("No modem preferences read yet.");bootStatus.setText("Saved reboot plan: not checked.");
        status.setText("SIM "+(slot+1)+" · Read preferences to get started.");screen.setSlot(slot);
        screen.showBandEmpty("Your selectable bands will appear after a successful read.");bindingUi=false;loadSpeedHistory();refreshEnabled();
    }
    private void disableRebootReapply() {
        final int s=slot;
        work("Disabling reboot reapply…",() -> {BootPlans.clear(this,s);ui(() -> {
            activeBootPlan=null;persistLock.setChecked(false);showBootStatus();status.setText("Reboot reapply disabled for this SIM slot. Current modem preferences were not changed.");
        });});
    }
    private void checkLegacyEfs() {
        final int s=slot;
        work("Checking the legacy EFS path…",() -> {
            JSONObject r=invoke("probe",""+s);
            ui(() -> status.setText(r.optBoolean("ok")?"Legacy EFS preferences exist. Normal band control uses QMI.":"Legacy EFS check: "+r.optString("error")+"\nThis check does not affect QMI band locking. See Tools for the report."));
        });
    }
    private void openNetworkSettings() {
        try {startActivity(new Intent(android.provider.Settings.ACTION_NETWORK_OPERATOR_SETTINGS));}
        catch(Exception e) {startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS));}
    }
    /** Only interrupt the user's flow when the system Location switch is actually off. */
    private boolean requireLocationEnabled() {
        LocationManager location=getSystemService(LocationManager.class);
        if(location!=null&&location.isLocationEnabled())return true;
        new AlertDialog.Builder(this).setTitle("Enable Location for serving bands")
            .setMessage("Turn on Location so Android can provide the serving band. Return to Band Lab and tap the action again. The app does not request GPS coordinates.")
            .setNegativeButton("Cancel",null).setPositiveButton("Open Location settings",(d,w) -> {
                try {startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));}
                catch(Exception e) {status.setText("Open your phone's Settings → Location and enable Location, then retry.");}
            }).show();return false;
    }
    private void confirmSpeedTest() {
        if(busy)return;
        if(hasDraft()||restoredBands!=null) {status.setText("Apply or discard your pending band choices before testing speed.");return;}
        if(!requireLocationEnabled())return;
        if(checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this).setTitle("Serving band in speed reports")
                .setMessage("Android needs Phone and Precise location permission to read this SIM's serving band. Band observations stay in your local speed reports; they are not sent to the speed server.")
                .setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w) -> requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE,Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},8)).show();return;
        }
        final int seconds=screen.speedDuration.getSelectedItemPosition()==1?60:20;
        new AlertDialog.Builder(this).setTitle("Test SIM "+(slot+1)+" mobile-data speed?")
            .setMessage("Make SIM "+(slot+1)+" your mobile-data SIM. Turn Wi-Fi and VPN off and wait for service after Apply.\n\nDownload: "+seconds+" seconds. Upload: "+seconds+" seconds, plus latency and band sampling. There is no fixed data-volume cutoff. Fast 5G may use several GB (at 1 Gbps, about "+(seconds==60?"7.5":"2.5")+" GB per direction), plus overhead.\n\nCloudflare receives your IP address and test traffic. Results and serving-band observations stay local. HTTP measurements are approximate. The test does not change modem preferences and stops if you leave the app.")
            .setNegativeButton("Cancel",null).setPositiveButton("Start test",(d,w) -> startSpeedTest()).show();
    }
    private void startSpeedTest() {
        if(busy||hasDraft()||restoredBands!=null||!requireLocationEnabled())return;
        final int s=slot;final String preference=probe==null?"not read":bandNames(probe.optString("sa_hex"));
        final int seconds=screen.speedDuration.getSelectedItemPosition()==1?60:20;getPreferences(MODE_PRIVATE).edit().putInt("speed_seconds",seconds).apply();
        speedRunning=true;busy=true;screen.showPage(UiPresentation.SPEED);status.setText("Testing SIM "+(s+1)+" mobile-data speed…");
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        screen.beginSpeedTest();screen.speedStatus.setText("Checking mobile-data connection…");refreshEnabled();
        final SpeedTestSession session=new SpeedTestSession(getApplicationContext(),s,seconds,new SpeedTestSession.Progress() {
            public void update(String text) {ui(() -> screen.speedStatus.setText(text));}
            public void phase(String name) {ui(() -> screen.speedPhase(name));}
            public void transfer(String name,double mbps,long bytes,int elapsed,int duration) {ui(() -> screen.speedProgress(name,mbps,bytes,elapsed,duration));}
            public void latency(double ms) {ui(() -> screen.speedPing.setText(String.format(Locale.ROOT,"%.0f",ms)));}
            public void serving(String bands) {ui(() -> screen.speedServing.setText("Serving (sampled): "+bands));}
        });speedSession=session;
        worker.execute(() -> {
            try {
                JSONObject result;
                synchronized(PreferenceStore.LOCK) {result=session.run(preference);}
                String saveError="";
                try {SpeedTestStore.add(this,result);}catch(Exception e) {saveError=" History could not be saved: "+e.getMessage();}
                final String warning=saveError;final JSONObject finished=result;
                ui(() -> {
                    loadSpeedHistory();screen.showSpeedResult(finished);
                    if(!warning.isEmpty())screen.speedHistory.setText(SpeedTestStore.render(new JSONArray().put(finished),s)+warning);
                    status.setText((finished.optBoolean("complete")?"Speed test saved. Serving bands (sampled): "+finished.optString("serving_bands_sampled","unknown"):SpeedTestStore.outcome(finished)+" saved. "+finished.optString("error","Speed test incomplete."))+warning);
                });
            } catch(Exception e) {ui(() -> {screen.speedStatus.setText("Test failed");status.setText("Speed test: "+e.getMessage());});}
            finally {ui(() -> {getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);speedSession=null;speedRunning=false;busy=false;refreshEnabled();});}
        });
    }
    private void stopSpeedTest() {
        SpeedTestSession session=speedSession;if(session!=null)session.cancel();
        screen.speedStatus.setText("Stopping test…");
    }
    private void loadSpeedHistory() {
        try {
            speedHistory=SpeedTestStore.read(this);screen.showSpeedHistory(speedHistory,slot);
            if(!speedRunning) {
                boolean found=false;for(int i=0;i<speedHistory.length();i++) {JSONObject r=speedHistory.optJSONObject(i);if(r!=null&&r.optInt("slot",-1)==slot) {screen.showSpeedResult(r);found=true;break;}}
                if(!found)screen.idleSpeed();
            }
        }
        catch(Exception e) {speedHistory=new JSONArray();screen.speedHistory.setText("Could not read speed history. Clear history to start fresh. "+e.getMessage());}
    }
    private void copySpeedReports() {
        JSONArray selected=new JSONArray();for(int i=0;i<speedHistory.length();i++) {JSONObject r=speedHistory.optJSONObject(i);if(r!=null&&r.optInt("slot",-1)==slot)selected.put(r);}
        if(selected.length()==0) {Toast.makeText(this,"No reports for this SIM",Toast.LENGTH_SHORT).show();return;}
        String text=SpeedTestStore.render(selected,slot)+"\n\nJSON\n"+selected.toString();
        ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("SA Band Lab speed reports",text));Toast.makeText(this,"Speed reports copied",Toast.LENGTH_SHORT).show();
    }
    private void clearSpeedHistory() {
        if(busy)return;
        new AlertDialog.Builder(this).setTitle("Clear all speed reports?").setMessage("Deletes local speed-test history for both SIM slots. Backups and band preferences are kept.")
            .setNegativeButton("Cancel",null).setPositiveButton("Clear history",(d,w) -> {SpeedTestStore.clear(this);loadSpeedHistory();}).show();
    }
    private boolean ready() {return probe!=null&&probe.optBoolean("ok")&&backup!=null&&matchesDevice(backup);}
    private int selectedCount() {int count=0;for(CheckBox band:bands)if(band.isChecked())count++;return count;}
    private int[] selectedBands() {
        int[] result=new int[selectedCount()];int i=0;for(CheckBox band:bands)if(band.isChecked())result[i++]=(Integer)band.getTag();return result;
    }
    private String selectedMask() {
        byte[] mask=new byte[64];for(int band:selectedBands())mask[(band-1)/8]|=(byte)(1<<((band-1)%8));
        StringBuilder hex=new StringBuilder();for(byte b:mask) {hex.append(Character.forDigit((b&255)>>>4,16));hex.append(Character.forDigit(b&15,16));}return hex.toString();
    }
    private boolean hasDraft() {return ready()&&UiPresentation.dirty(selectedMask(),probe.optString("sa_hex"),nrOnly.isChecked(),probe.optInt("rat_mode_preference",-1),persistLock.isChecked(),activeBootPlan!=null);}
    private void restoreChoices(int[] selected,boolean nr,boolean reboot) {
        if(selected==null||!ready())return;
        bindingUi=true;HashSet<Integer> wanted=new HashSet<>();for(int n:selected)wanted.add(n);
        for(CheckBox band:bands)band.setChecked(wanted.contains((Integer)band.getTag()));nrOnly.setChecked(nr);persistLock.setChecked(reboot);bindingUi=false;
    }
    private long observationElapsed() {return bandObservedElapsed<0?-1:Math.max(0,SystemClock.elapsedRealtime()-bandObservedElapsed);}
    private void refreshPresentation() {
        if(screen==null)return;
        String freshness=lastBandObservation==null?"Not refreshed":lastBandObservation.has("error")?"Refresh failed":
                "Snapshot · "+java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new Date(lastBandObservation.optLong("captured_ms")));
        int rat=lastBandObservation==null?-1:lastBandObservation.optInt("data_rat",-1);
        screen.showNetwork(UiPresentation.network(bandSnapshot,observationElapsed(),lastBandObservation!=null&&lastBandObservation.optBoolean("cached_may_include_other_sims")),freshness,rat==20?"5G NR":rat==13?"LTE":"");
        screen.showPreferencesSummary(probe==null?"":bandNames(probe.optString("sa_hex")),UiPresentation.mode(probe==null?-1:probe.optInt("rat_mode_preference",-1)),ready(),backup!=null,backup!=null&&matchesDevice(backup),probe==null?"":probe.optString("transport"));
        screen.showDraft(selectedCount(),hasDraft(),ready(),nrOnly.isChecked(),persistLock.isChecked());screen.setBusy(busy);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);state.putInt("slot",slot);state.putInt("page",screen.page());
        for(int page=0;page<UiPresentation.PAGE_COUNT;page++)state.putInt("scroll_"+page,screen.scrollY(page));
        if(ready()) {state.putIntArray("draft_bands",selectedBands());state.putBoolean("draft_nr",nrOnly.isChecked());state.putBoolean("draft_reboot",persistLock.isChecked());}
        else if(restoredBands!=null) {state.putIntArray("draft_bands",restoredBands);state.putBoolean("draft_nr",restoredNr);state.putBoolean("draft_reboot",restoredReboot);}
    }
    @Override protected void onResume() {super.onResume();uiHandler.removeCallbacks(ageTick);uiHandler.post(ageTick);}
    @Override protected void onPause() {uiHandler.removeCallbacks(ageTick);if(speedRunning)stopSpeedTest();super.onPause();}
    private void refreshEnabled() {
        for(Button b:actions)b.setEnabled(!busy);
        for(CheckBox b:bands) {b.setEnabled(!busy);b.setAlpha(busy?0.55f:1f);}
        boolean ready=ready();
        if(nrOnly!=null)nrOnly.setEnabled(!busy&&ready&&probe.optInt("rat_mode_preference",-1)>0);
        if(persistLock!=null)persistLock.setEnabled(!busy&&ready);
        if(nrOnly!=null)nrOnly.setAlpha(nrOnly.isEnabled()?1f:0.42f);
        if(persistLock!=null)persistLock.setAlpha(persistLock.isEnabled()?1f:0.42f);
        if(selectAll!=null)selectAll.setEnabled(!busy&&ready&&!bands.isEmpty());
        if(deselectAll!=null)deselectAll.setEnabled(!busy&&ready&&!bands.isEmpty());
        if(apply!=null)apply.setEnabled(UiPresentation.canApply(ready,probe!=null&&probe.optBoolean("write_ready"),busy,selectedCount(),probe==null?-1:probe.optInt("rat_mode_preference",-1)));
        if(restore!=null)restore.setEnabled(!busy&&ready);
        screen.startSpeed.setEnabled(!busy&&!hasDraft()&&restoredBands==null);
        screen.startSpeed.setVisibility(speedRunning?android.view.View.GONE:android.view.View.VISIBLE);
        screen.stopSpeed.setVisibility(speedRunning?android.view.View.VISIBLE:android.view.View.GONE);
        screen.stopSpeed.setEnabled(speedRunning);
        screen.speedDuration.setEnabled(!busy);
        for(Button b:actions)b.setAlpha(b.isEnabled()?1f:0.42f);
        refreshPresentation();
    }
    private interface Job {void run() throws Exception;}
    private void work(String text,Job job) {
        if(busy)return;
        busy=true;status.setText(text);refreshEnabled();
        worker.execute(() -> {
            try {synchronized(PreferenceStore.LOCK) {job.run();}}
            catch(Exception e) {lastHelperResult=null;lastReport="ERROR: "+e.toString();ui(() -> status.setText(lastReport));}
            finally {ui(() -> {busy=false;refreshEnabled();});}
        });
    }
    private void ui(Runnable r) {if(alive)runOnUiThread(() -> {if(alive) {r.run();refreshPresentation();}});}
    private AtomicFile backupFile(int s) {return new AtomicFile(new File(getFilesDir(),"qmi-sa-backup-slot"+s+".json"));}
    private JSONObject loadBackup(int s) throws Exception {
        AtomicFile f=backupFile(s);
        if(!f.getBaseFile().exists())return null;
        return new JSONObject(new String(f.readFully(),StandardCharsets.UTF_8));
    }
    private void saveBackup(int s,JSONObject b) throws Exception {
        AtomicFile f=backupFile(s);FileOutputStream stream=null;
        try {stream=f.startWrite();stream.write(b.toString(2).getBytes(StandardCharsets.UTF_8));f.finishWrite(stream);}
        catch(Exception e) {if(stream!=null)f.failWrite(stream);throw e;}
    }
    private JSONObject invoke(String... args) throws Exception {
        JSONObject result=RootHelper.invoke(this,args);lastReport=result.toString();
        if("efs-get39-v2".equals(result.optString("read_profile")))lastEfsResult=result;
        else if("qmi-nas-sa-v1".equals(result.optString("read_profile")))lastHelperResult=result;
        return result;
    }
    private void readPreferences() {
        final int s=slot;
        final int[] keep=hasDraft()?selectedBands():restoredBands;
        final boolean keepNr=hasDraft()?nrOnly.isChecked():restoredNr,keepReboot=hasDraft()?persistLock.isChecked():restoredReboot;
        work("Reading live preferences and NR capabilities…",() -> {
            JSONObject r=invoke("qmi-probe",""+s);JSONObject b=loadBackup(s);
            if(r.optBoolean("ok")) {
                if(b==null) {
                    b=new JSONObject();b.put("schema",2);b.put("read_profile",r.getString("read_profile"));b.put("slot",s);b.put("fingerprint",Build.FINGERPRINT);b.put("baseband",baseband());b.put("device_binding",deviceBinding());b.put("created_ms",System.currentTimeMillis());
                    b.put("original_sa",r.getString("sa_hex"));b.put("expected_sa",r.getString("sa_hex"));b.put("original_nsa",r.getString("nsa_hex"));b.put("pending_sa","");saveBackup(s,b);
                }
                if(matchesDevice(b)) {
                    PreferenceJournal journal=journal(b);
                    try {journal.refresh(snapshot(r));storeJournal(b,journal);saveBackup(s,b);}
                    catch(IllegalArgumentException e) {r.put("ok",false);r.put("write_ready",false);r.put("error",e.getMessage());}
                }
            }
            JSONObject selected=b,change=loadLastChange(s),plan=BootPlans.load(this,s),bootResult=PreferenceStore.read(this,"boot-result-slot"+s+".json");
            ui(() -> {probe=r;backup=selected;lastChange=change;activeBootPlan=plan;lastBootResult=bootResult;persistLock.setChecked(plan!=null);showBootStatus();status.setText(r.optBoolean("ok")?(r.optBoolean("write_ready")?"Live preferences read. Backup and NR capability list are ready.":"Live preferences backed up. Apply disabled: "+r.optString("capability_error")):r.optString("error","Read failed.")+"\nNo preference SET was sent. Open Tools to copy the diagnostic report.");showPreferences();
                if(ready()&&keep!=null) {restoreChoices(keep,keepNr,keepReboot);restoredBands=null;status.setText(r.optBoolean("write_ready")?"Previous choices restored where still selectable. Review them, then press Apply.":"Preferences read. Apply is unavailable: "+r.optString("capability_error"));}
                if(ready()&&restoredBandScroll>=0) {screen.restoreScroll(UiPresentation.BANDS,restoredBandScroll);restoredBandScroll=-1;}
            });
        });
    }
    private void showPreferences() {
        bindingUi=true;
        try {
            nrOnly.setChecked(probe!=null&&probe.optInt("rat_mode_preference",-1)==64);
            bandBox.removeAllViews();bands.clear();
            if(probe==null||!probe.optBoolean("ok")) {
                current.setText(probe!=null&&probe.has("sa_hex")?"Last observed: "+bandNames(probe.optString("sa_hex"))+"\n"+UiPresentation.mode(probe.optInt("rat_mode_preference",-1)):"No verified preference read available.");
                screen.showBandEmpty("Read live preferences before choosing bands. Tools contains the diagnostic report.");return;
            }
            supported.setText("Reported NR support: "+bandNames(probe.optString("supported_nr_hex"))+"\nSupported bands do not establish local coverage.");
            current.setText("Current: "+bandNames(probe.optString("sa_hex"))+"\n"+UiPresentation.mode(probe.optInt("rat_mode_preference",-1)));
            if(backup==null) {screen.showBandEmpty("Read preferences to capture a matching backup.");return;}
            if(!matchesDevice(backup)) {status.setText("The captured backup does not match this device or firmware. It is preserved; writes are disabled. Export it from Tools for review.");probe=null;screen.showBandEmpty("A matching original backup is required before Apply.");return;}
            String original=backup.optString("original_sa"),currentMask=probe.optString("sa_hex"),support=probe.optString("supported_nr_hex");
            for(int n=1;n<=512;n++)if(hasBand(original,n)&&hasBand(support,n))bands.add(screen.addBand(n,bandObservation(n),hasBand(currentMask,n)));
            screen.showBandEmpty(bands.isEmpty()?"No verified selectable NR bands. Apply stays disabled; no defaults are guessed.":"");
        } finally {bindingUi=false;}
    }
    private void selectBands(boolean checked) {
        bindingUi=true;for(CheckBox b:bands)b.setChecked(checked);bindingUi=false;refreshEnabled();
        status.setText(checked?"All selectable bands selected. Press Apply when ready.":"Selection cleared. Choose at least one band to apply.");
    }
    private AtomicFile changeFile(int s) {return new AtomicFile(new File(getFilesDir(),"qmi-last-change-slot"+s+".json"));}
    private JSONObject loadLastChange(int s) throws Exception {AtomicFile f=changeFile(s);return f.getBaseFile().exists()?new JSONObject(new String(f.readFully(),StandardCharsets.UTF_8)):null;}
    private void recordChange(int s,String operation,JSONObject result) throws Exception {
        JSONObject record=new JSONObject();record.put("slot",s);record.put("operation",operation);record.put("captured_ms",System.currentTimeMillis());record.put("helper_result",result);
        AtomicFile f=changeFile(s);FileOutputStream stream=null;
        try {stream=f.startWrite();stream.write(record.toString().getBytes(StandardCharsets.UTF_8));f.finishWrite(stream);}catch(Exception e) {if(stream!=null)f.failWrite(stream);throw e;}
        ui(() -> lastChange=record);
    }
    private String bandObservation(int band) {
        String label=UiPresentation.bandState(bandSnapshot,band,observationElapsed());
        if(lastBandObservation!=null&&lastBandObservation.optBoolean("cached_may_include_other_sims")) {
            if("Serving".equals(label))return "Cached serving";if("Nearby cell".equals(label))return "Cached nearby";
        }
        return label;
    }
    private void refreshBandLabels() {if(screen!=null)for(CheckBox b:bands)screen.labelBand(b,(Integer)b.getTag(),bandObservation((Integer)b.getTag()));}
    private static boolean hasBand(String hex,int band) {
        if(!hex.matches("[0-9a-fA-F]{128}"))return false;
        int at=((band-1)/8)*2;return (Integer.parseInt(hex.substring(at,at+2),16)&(1<<((band-1)%8)))!=0;
    }
    private static String bandNames(String hex) {
        return UiPresentation.bandNames(hex);
    }
    private void confirmApply() {
        if(busy||!ready()||!probe.optBoolean("write_ready"))return;
        byte[] mask=new byte[64];int count=0;
        for(CheckBox b:bands)if(b.isChecked()) {int n=(Integer)b.getTag()-1;mask[n/8]|=(byte)(1<<(n%8));count++;}
        if(count==0) {status.setText("Select at least one SA band.");return;}
        StringBuilder h=new StringBuilder();for(byte v:mask)h.append(String.format(Locale.ROOT,"%02x",v&255));final String target=h.toString();
        final boolean nrRequested=nrOnly.isChecked(),persistRequested=persistLock.isChecked();
        try {RadioModeSelection.target(nrRequested,probe.optInt("rat_mode_preference",-1),backup.optInt("original_radio_mode",-1));}
        catch(IllegalArgumentException e) {status.setText(e.getMessage());return;}
        new AlertDialog.Builder(this).setTitle(persistRequested?"Apply SA bands and reapply after reboot?":"Apply SA bands until reboot?").setMessage(bandNames(target)+"\n\n"+(nrRequested?"NR only: on. LTE fallback is disabled.":"NR only: off. Restore the captured earlier mode if currently NR only; otherwise keep the current mode.")+"\n\n"+(persistRequested?"After verification, save this selection for automatic reapply after boot and first unlock.":"Reboot reapply will be disabled for this SIM slot.")+"\n\nApply bands and radio mode together, then verify both. NSA and LTE band preferences are preserved.").setNegativeButton("Cancel",null).setPositiveButton("Apply",(d,w) -> mutate(false,target,nrRequested,persistRequested)).show();
    }
    private void confirmRestore() {
        if(busy||!ready())return;
        new AlertDialog.Builder(this).setTitle("Restore captured SA preferences?").setMessage("Restores the original SA preferences saved by this app and disables reboot reapply for this SIM slot. Your current radio mode is kept. Any pending NR-only choice is not applied by Restore.").setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w) -> mutate(true,backup.optString("original_sa"),false,false)).show();
    }
    private void mutate(boolean restoring,String target,boolean nrRequested,boolean persistRequested) {
        final int s=slot;final int[] choices=selectedBands();
        work(restoring?"Restoring and verifying SA…":"Applying and verifying SA…",() -> {
            // Cancel old intent first. A failed/uncertain replacement cannot silently rearm it.
            BootPlans.clear(this,s);
            ui(() -> {activeBootPlan=null;persistLock.setChecked(false);showBootStatus();});
            JSONObject b=loadBackup(s);
            if(b==null)throw new IOException("Read preferences to capture a backup first. No SET was sent.");
            JSONObject fresh=invoke("qmi-probe",""+s);
            if(!fresh.optBoolean("ok"))throw new IOException(fresh.optString("error"));
            if(!matchesDevice(b))throw new IOException("Backup does not match this device. No SET was sent.");
            PreferenceJournal journal=journal(b);
            journal.refresh(snapshot(fresh));
            String expected=journal.expectedSa;
            if(!restoring&&(fresh.optInt("rat_mode_preference",-1)<1||fresh.optInt("rat_mode_preference",-1)>127))throw new IOException("Live radio mode is unavailable. No SET was sent. Read preferences again.");
            int expectedMode=journal.expectedMode,targetMode=journal.stage(target,restoring,nrRequested);
            storeJournal(b,journal);saveBackup(s,b);
            JSONObject r=restoring?invoke("qmi-restore",""+s,journal.originalSa,expected,journal.originalNsa):invoke("qmi-apply-plan",""+s,journal.originalSa,expected,journal.originalNsa,target,""+expectedMode,""+targetMode);
            recordChange(s,restoring?"sa_restore":"sa_and_radio_mode_apply",r);
            PreferenceJournal.Snapshot observed=null;
            if(r.has("sa_hex")&&r.has("nsa_hex")) {
                try {observed=snapshot(r);}catch(IllegalArgumentException ignored) {}
            }
            // Missing write_attempted is uncertain, never evidence that no write happened.
            journal.finish(r.optBoolean("ok"),r.optBoolean("rollback_verified"),r.optBoolean("write_attempted",true),observed);
            storeJournal(b,journal);saveBackup(s,b);
            if(!restoring&&persistRequested&&r.optBoolean("ok"))BootPlans.save(this,s,b,target,targetMode,r);
            JSONObject plan=BootPlans.load(this,s);
            ui(() -> {
                backup=b;activeBootPlan=plan;persistLock.setChecked(plan!=null);showBootStatus();
                if(r.has("sa_hex"))probe=r;else probe=null;
                showPreferences();
                if(!r.optBoolean("ok")&&!restoring) {restoredBands=choices;restoredNr=nrRequested;restoredReboot=persistRequested;}
                status.setText(r.optBoolean("ok")?(restoring?"Captured SA preferences restored and verified.":"SA bands and radio mode verified. "+(plan!=null?"Reboot reapply saved. ":"Reboot reapply is off. ")+"Refresh network to check the serving cell."):r.optString("error"));
            });
        });
    }
    private void showBootStatus() {
        String text=activeBootPlan==null?"Saved reboot plan: off.":"Saved reboot plan: "+bandNames(activeBootPlan.optString("target_sa"))+" · "+UiPresentation.mode(activeBootPlan.optInt("target_radio_mode",-1))+". Applies to this SIM slot.";
        if(lastBootResult!=null) {
            JSONObject result=lastBootResult.optJSONObject("helper_result");
            text+="\nLast boot attempt: "+(result!=null&&result.optBoolean("ok")?"verified.":result==null?"unknown.":result.optString("error","Failed; copy diagnostic report."));
        }
        bootStatus.setText(text);
    }
    private static PreferenceJournal.Snapshot snapshot(JSONObject r) {return PreferenceStore.snapshot(r);}
    private static PreferenceJournal journal(JSONObject b) {return PreferenceStore.journal(b);}
    private static void storeJournal(JSONObject b,PreferenceJournal journal) throws Exception {PreferenceStore.storeJournal(b,journal);}
    private String baseband() {return PreferenceStore.baseband();}
    private String deviceBinding() {return android.provider.Settings.Secure.getString(getContentResolver(),android.provider.Settings.Secure.ANDROID_ID);}
    private boolean matchesDevice(JSONObject b) {return PreferenceStore.matchesDevice(this,b);}
    private void copyReport() {
        ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("SA Band Lab report",report()));
        Toast.makeText(this,"Report copied",Toast.LENGTH_SHORT).show();
    }
    private String report() {
        JSONObject r=new JSONObject();try {r.put("app","SA Band Lab 0.12");r.put("model",Build.MODEL);r.put("android",Build.VERSION.RELEASE);r.put("fingerprint",Build.FINGERPRINT);r.put("baseband",baseband());r.put("slot",slot);r.put("helper_result",lastHelperResult==null?lastReport:lastHelperResult);if(lastChange!=null)r.put("last_preference_change",lastChange);r.put("reapply_after_reboot_enabled",activeBootPlan!=null);if(activeBootPlan!=null)r.put("boot_reapply_plan",activeBootPlan);if(lastBootResult!=null)r.put("last_boot_reapply",lastBootResult);if(lastEfsResult!=null)r.put("efs_diagnostic",lastEfsResult);r.put("serving_state",radio.getText());if(lastBandObservation!=null)r.put("five_g_observation",lastBandObservation);if(lastModemFrequencies!=null)r.put("modem_frequencies",lastModemFrequencies);if(backup!=null)r.put("backup",backup);for(int i=0;i<speedHistory.length();i++) {JSONObject test=speedHistory.optJSONObject(i);if(test!=null&&test.optInt("slot",-1)==slot) {r.put("latest_speed_test",test);break;}}return r.toString(2);}catch(Exception e) {return lastReport;}
    }
    private void exportBackups() {
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"sa-band-lab-backup.json");startActivityForResult(i,100);
    }
    private void importBackups() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,101);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;
        work(request==100?"Exporting backup…":"Importing backup…",() -> {
            if(request==100) {
                JSONArray list=new JSONArray();for(int s=0;s<2;s++) {JSONObject b=loadBackup(s);if(b!=null)list.put(b);}
                if(list.length()==0)throw new IOException("No captured backup to export.");
                try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")) {if(out==null)throw new IOException("Cannot open document.");out.write(list.toString(2).getBytes(StandardCharsets.UTF_8));}
                ui(() -> status.setText("Exact SA backups exported."));
            } else {
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=getContentResolver().openInputStream(data.getData())) {if(in==null)throw new IOException("Cannot open backup.");byte[] buf=new byte[2048];int n;while((n=in.read(buf))!=-1) {if(bytes.size()+n>16384)throw new IOException("Backup too large.");bytes.write(buf,0,n);}}
                JSONArray list=new JSONArray(bytes.toString("UTF-8"));if(list.length()<1||list.length()>2)throw new IOException("Invalid backup count.");
                HashSet<Integer> seen=new HashSet<>();
                for(int j=0;j<list.length();j++) {JSONObject b=list.getJSONObject(j);int s=b.getInt("slot");
                    if(s<0||s>1||!seen.add(s)||!matchesDevice(b))throw new IOException("Backup does not match this phone, firmware and read format, or repeats a SIM slot.");
                    for(String key:new String[]{"original_sa","expected_sa","original_nsa"})if(!b.optString(key).matches("[0-9a-fA-F]{128}"))throw new IOException("Invalid mask in backup.");
                    if(!b.optString("pending_sa").isEmpty()&&!b.optString("pending_sa").matches("[0-9a-fA-F]{128}"))throw new IOException("Invalid pending mask in backup.");
                    if(b.has("original_radio_mode")) {
                        for(String key:new String[]{"original_radio_mode","expected_radio_mode"})if(b.getInt(key)<1||b.getInt(key)>127)throw new IOException("Invalid radio mode in backup.");
                        int pending=b.optInt("pending_radio_mode",-1);if(pending!=-1&&(pending<1||pending>127))throw new IOException("Invalid pending radio mode in backup.");
                    }
                    if(loadBackup(s)!=null)throw new IOException("A backup already exists for SIM "+(s+1)+". Import will not overwrite it.");
                }
                for(int j=0;j<list.length();j++) {JSONObject b=list.getJSONObject(j);saveBackup(b.getInt("slot"),b);}
                ui(() -> {probe=null;backup=null;bandBox.removeAllViews();bands.clear();screen.showBandEmpty("Backup imported. Read preferences to verify this SIM before applying or restoring.");status.setText("Backup imported. Read preferences before restoring.");});
            }
        });
    }
    private void modemFrequencies() {
        final int s=slot;
        work("Reading modem NR serving and neighbor frequencies…",() -> {
            JSONObject previous=lastHelperResult;String previousReport=lastReport;
            try {
                JSONObject result=invoke("qmi-neighbors",""+s);
                StringBuilder text=new StringBuilder("Modem frequency snapshot: ").append(java.text.DateFormat.getTimeInstance().format(new Date())).append("\n");
                if(!result.optBoolean("ok")) {
                    if(result.optBoolean("frequency_query_unsupported"))text.append("Neighbor-band discovery unavailable through this modem query. Your firmware rejects it as DeviceUnsupported (0x0019). Android's serving-only result cannot establish n28 coverage. A separate DIAG measurement backend is still needed.");
                    else text.append(result.optString("error","Modem read failed.")).append("\nCopy the diagnostic report.");
                }
                else {
                    String capabilities=result.optString("supported_nr_hex");
                    appendFrequencyList(text,"Serving NR frequencies",result.optJSONArray("nr_serving_arfcns"),capabilities);
                    appendFrequencyList(text,"Neighbor NR frequencies",result.optJSONArray("nr_neighbor_arfcns"),capabilities);
                    text.append("\nPossible bands are derived from frequency and phone support; overlaps stay ambiguous. Neighbor frequencies may be configured measurement targets. They do not prove a detected cell, usable service or signal strength. This is not a full band scan.");
                }
                result.put("captured_ms",System.currentTimeMillis());
                ui(() -> {lastModemFrequencies=result;modemRadio.setText(text.toString());status.setText(result.optBoolean("ok")?"Modem frequency list read. Preferences were not changed.":"Modem frequency read failed. Copy the diagnostic report.");});
            } catch(Exception e) {
                JSONObject failure=new JSONObject();failure.put("slot",s);failure.put("error",e.toString());failure.put("write_attempted",false);
                ui(() -> {lastModemFrequencies=failure;modemRadio.setText("Modem frequency read failed: "+e.getMessage());status.setText("Frequency read failed. Preferences were not changed.");});
            } finally {lastHelperResult=previous;lastReport=previousReport;}
        });
    }
    private static void appendFrequencyList(StringBuilder text,String title,JSONArray list,String capabilities) throws Exception {
        text.append("\n").append(title).append(":\n");
        if(list==null) {text.append("Not provided by this firmware.\n");return;}
        if(list.length()==0) {text.append("Modem returned an empty list.\n");return;}
        TreeSet<Long> unique=new TreeSet<>();for(int i=0;i<list.length();i++)unique.add(list.getLong(i));
        for(long arfcn:unique)text.append("NR-ARFCN ").append(arfcn).append(" · ").append(NrFrequencyPlan.label(arfcn,capabilities)).append("\n");
    }
    private void servingState() {
        if(busy||!requireLocationEnabled())return;
        if(checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this).setTitle("Read cellular observations?")
                .setMessage("Android requires Phone and Precise location permission to expose serving and nearby cells. Cell identities can reveal location. This app does not request GPS coordinates or upload location. Root-based band control works without these permissions.")
                .setNegativeButton("Not now",null).setPositiveButton("Continue",(d,w) -> requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE,Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},7)).show();return;
        }
        final int s=slot;
        work("Reading serving and nearby 5G cells…",() -> {
          try {
            SubscriptionManager subscriptions=getSystemService(SubscriptionManager.class);SubscriptionInfo info=subscriptions.getActiveSubscriptionInfoForSimSlotIndex(s);
            if(info==null)throw new IOException("No active Android subscription in this SIM slot.");
            TelephonyManager tm=getSystemService(TelephonyManager.class).createForSubscriptionId(info.getSubscriptionId());
            AtomicReference<List<CellInfo>> updated=new AtomicReference<>();CountDownLatch latch=new CountDownLatch(1);
            AtomicReference<String> updateError=new AtomicReference<>("");
            tm.requestCellInfoUpdate(getMainExecutor(),new TelephonyManager.CellInfoCallback() {
                @Override public void onCellInfo(List<CellInfo> cells) {updated.set(cells);latch.countDown();}
                @Override public void onError(int code,Throwable detail) {updateError.set("Cell update error "+code);latch.countDown();}
            });
            boolean completed=latch.await(8,TimeUnit.SECONDS);int rat=tm.getDataNetworkType();
            StringBuilder text=new StringBuilder("Snapshot: ").append(java.text.DateFormat.getTimeInstance().format(new Date()))
                .append("\nAndroid data RAT: ").append(rat==20?"NR":rat==13?"LTE":"type "+rat).append("\n");
            List<CellInfo> cells=updated.get();boolean cached=cells==null;
            if(cached) {cells=tm.getAllCellInfo();text.append("Update unavailable; using cached observations, which may include other SIMs.\n");}
            else text.append("Cell update returned; check each observation's age.\n");
            ArrayList<FiveGBands.Cell> found=new ArrayList<>();JSONArray entries=new JSONArray();
            long now=android.os.SystemClock.elapsedRealtime();
            if(cells!=null)for(CellInfo cell:cells)if(cell instanceof CellInfoNr&&found.size()<128) {
                CellIdentityNr id=(CellIdentityNr)cell.getCellIdentity();CellSignalStrengthNr signal=(CellSignalStrengthNr)((CellInfoNr)cell).getCellSignalStrength();
                long stamp=cell.getTimestampMillis(),age=stamp>0&&stamp<=now?now-stamp:-1;
                String operator=id.getMccString()==null||id.getMncString()==null?"unknown":id.getMccString()+id.getMncString();
                FiveGBands.Cell observed=new FiveGBands.Cell(id.getBands(),cell.isRegistered(),id.getNrarfcn(),id.getPci(),signal.getSsRsrp(),signal.getSsRsrq(),signal.getSsSinr(),age,operator);
                found.add(observed);JSONObject entry=new JSONObject();JSONArray observedBands=new JSONArray();for(int b:observed.bands)observedBands.put(b);
                entry.put("bands",observedBands);entry.put("registered",observed.registered);entry.put("operator",operator);
                entry.put("nrarfcn",availableNumber(observed.arfcn));entry.put("pci",availableNumber(observed.pci));
                entry.put("ss_rsrp_dbm",availableNumber(observed.rsrp));entry.put("ss_rsrq_db",availableNumber(observed.rsrq));entry.put("ss_sinr_db",availableNumber(observed.sinr));
                entry.put("age_ms",age<0?JSONObject.NULL:age);entries.put(entry);
            }
            FiveGBands snapshot=new FiveGBands(found);text.append(snapshot.render());
            JSONObject observation=new JSONObject();observation.put("slot",s);observation.put("captured_ms",System.currentTimeMillis());observation.put("data_rat",rat);observation.put("source",cached?"android_cached":"android_cell_update");observation.put("cached_may_include_other_sims",cached);observation.put("request_completed",completed);observation.put("update_error",updateError.get());observation.put("nr_cells",entries);observation.put("complete_band_scan",false);
            ui(() -> {bandSnapshot=snapshot;bandObservedElapsed=SystemClock.elapsedRealtime();lastBandObservation=observation;radio.setText(text.toString());refreshBandLabels();status.setText("5G observations refreshed. Nearby cells are included when Android reports them.");});
          } catch(Exception e) {
            JSONObject failure=new JSONObject();failure.put("slot",s);failure.put("error",e.toString());failure.put("complete_band_scan",false);
            ui(() -> {bandSnapshot=null;bandObservedElapsed=-1;lastBandObservation=failure;radio.setText("Could not refresh 5G cells: "+e.getMessage()+"\nEnable Location and grant Phone and precise location permissions, then retry.");refreshBandLabels();status.setText("5G detection failed. Band preferences were not changed.");});
          }
        });
    }
    private static Object availableNumber(int value) {return value==Integer.MAX_VALUE?JSONObject.NULL:value;}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==7&&grants.length==3&&grants[0]==0&&grants[1]==0)servingState();
        else if(request==7)status.setText("Serving-state permission was not granted. Preference operations remain available.");
        else if(request==8&&grants.length==3&&grants[0]==0&&grants[1]==0)confirmSpeedTest();
        else if(request==8)status.setText("Phone and Precise location permission are needed for speed reports with serving-band observations.");
    }
    @Override public void onBackPressed() {
        if(screen.page()!=UiPresentation.OVERVIEW) {screen.showPage(UiPresentation.OVERVIEW);return;}
        if(busy) {Toast.makeText(this,speedRunning?"Use Stop test in Speedtest to stop the transfer.":"Wait for the modem operation to finish.",Toast.LENGTH_SHORT).show();return;}
        if(hasDraft())new AlertDialog.Builder(this).setTitle("Leave without applying?").setMessage("Your current choices have not been applied. The modem will not be changed.")
                .setNegativeButton("Keep editing",null).setPositiveButton("Leave",(d,w) -> finish()).show();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {if(speedSession!=null)speedSession.cancel();alive=false;uiHandler.removeCallbacks(ageTick);worker.shutdown();super.onDestroy();}
}
