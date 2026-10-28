package dev.saband.lab;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.view.View;
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
    private LinearLayout content, bandBox;
    private TextView status, current, radio, supported, modemRadio;
    private Spinner sim;
    private Button apply, restore, selectAll, deselectAll;
    private CheckBox nrOnly;
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
    private final int ink = Color.rgb(229,236,246), muted = Color.rgb(161,177,198);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13,21,33));
        getWindow().setNavigationBarColor(Color.rgb(13,21,33));
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(13,21,33));
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(22),dp(26),dp(22),dp(32)); scroll.addView(content);
        setContentView(scroll);
        label("SA BAND LAB",13,Color.rgb(84,211,190));
        TextView title = label("Your SA band preferences",27,ink); title.setTypeface(null,Typeface.BOLD);
        label("Xiaomi 13 Pro · Snapdragon 8 Gen 2\nExperimental build 0.8 · root required for modem reads and locking",14,muted);
        label("Read live SA preferences first. Choose SA bands and NR-only mode, then apply both until reboot.",15,ink);
        sim = new Spinner(this);
        sim.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"SIM 1 · slot 0","SIM 2 · slot 1"}));
        content.addView(sim);
        sim.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id) {
                slot=position; probe=null; backup=null; bandBox.removeAllViews(); bands.clear();
                lastHelperResult=null;lastReport="No root operation has run for this SIM.";
                lastChange=null;lastEfsResult=null;nrOnly.setChecked(false);
                lastBandObservation=null;bandSnapshot=null;
                lastModemFrequencies=null;modemRadio.setText("Read the modem's neighbor-frequency list if Android reports serving cells only.");
                radio.setText("Refresh to see serving and nearby 5G cells reported for this SIM.");
                supported.setText("Read live SA preferences below to load your phone's supported bands.");
                current.setText("Read this SIM's preferences to begin."); refreshEnabled();
            }
        });
        label("Available 5G bands",20,ink);
        label("See the serving cell and nearby 5G cells reported by Android. Nearby cells may belong to other operators; this is not a complete coverage scan.",13,muted);
        button("Refresh detected 5G bands", () -> servingState());
        radio = label("Refresh to see serving and nearby 5G cells. This check does not change band preferences.",14,ink);
        button("Read modem neighbor frequencies · root", () -> modemFrequencies());
        modemRadio = label("Read the modem's neighbor-frequency list if Android reports serving cells only.",14,ink);
        supported = label("Read live SA preferences below to load your phone's supported bands.",13,muted);
        button("1  Read live SA preferences + backup", () -> readPreferences());
        status = label("Ready for a read-only modem check.",16,Color.rgb(84,211,190));
        button("Copy diagnostic report", () -> copyReport());
        current = label("No preference read yet.",14,muted);
        nrOnly=new CheckBox(this);nrOnly.setText("NR only (5G) · until reboot");nrOnly.setTextColor(ink);nrOnly.setTextSize(16);content.addView(nrOnly);
        nrOnly.setOnClickListener(v -> status.setText("NR-only choice updated. Press Apply SA bands until reboot to apply it with your selected bands."));
        label("NR only disables LTE fallback. Checkbox and band changes take effect together when you press Apply. Untick and apply to restore the captured earlier radio mode.",13,muted);
        label("Choose SA bands",19,ink);
        label("Choices are the intersection of your captured SA preference and the modem's NR capability list. An existing lock may narrow this list. Capabilities do not prove SA availability on your network.",13,muted);
        bandBox = new LinearLayout(this); bandBox.setOrientation(LinearLayout.VERTICAL); content.addView(bandBox);
        selectAll=button("Select all bands",() -> selectBands(true));
        deselectAll=button("Deselect all bands",() -> selectBands(false));
        apply = button("2  Apply SA bands until reboot", () -> confirmApply());
        restore = button("Restore captured SA preferences", () -> confirmRestore());
        label("Apply saves your previous settings, then applies SA bands and your NR-only choice together. NSA and LTE band preferences are preserved. Keep an exported backup. Restore captured SA preferences restores the original SA mask and keeps the current radio mode.",13,muted);
        button("Check original EFS path (read only)", () -> {
            final int s=slot;
            work("Checking the original EFS override…",() -> {
                JSONObject r=invoke("probe",""+s);
                ui(() -> status.setText(r.optBoolean("ok")?"EFS preferences exist. The primary controls still use the separate QMI backup.":r.optString("error")+"\nNo preference file was written. Use the live QMI read above."));
            });
        });
        button("Export backups", () -> exportBackups());
        button("Import backups", () -> importBackups());
        button("Open mobile network settings", () -> {
            try { startActivity(new Intent(android.provider.Settings.ACTION_NETWORK_OPERATOR_SETTINGS)); }
            catch(Exception e) { startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)); }
        });
        refreshEnabled();
    }
    private int dp(int n) {return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String text,int size,int color) {
        TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(6),0,dp(9));content.addView(v);return v;
    }
    private Button button(String text,Runnable action) {
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextColor(ink);b.setTextSize(15);
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(27,43,61));bg.setCornerRadius(dp(12));b.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(53));lp.topMargin=dp(9);lp.bottomMargin=dp(4);content.addView(b,lp);
        b.setOnClickListener(v -> action.run());actions.add(b);return b;
    }
    private void refreshEnabled() {
        for(Button b:actions)b.setEnabled(!busy);
        if(sim!=null)sim.setEnabled(!busy);
        for(CheckBox b:bands)b.setEnabled(!busy);
        boolean ready=probe!=null&&probe.optBoolean("ok")&&backup!=null&&matchesDevice(backup);
        if(nrOnly!=null)nrOnly.setEnabled(!busy&&ready&&probe.optInt("rat_mode_preference",-1)>0);
        if(selectAll!=null)selectAll.setEnabled(!busy&&ready&&!bands.isEmpty());
        if(deselectAll!=null)deselectAll.setEnabled(!busy&&ready&&!bands.isEmpty());
        if(apply!=null)apply.setEnabled(!busy&&ready&&probe.optBoolean("write_ready")&&!bands.isEmpty());
        if(restore!=null)restore.setEnabled(!busy&&ready);
        for(Button b:actions)b.setAlpha(b.isEnabled()?1f:0.35f);
    }
    private interface Job {void run() throws Exception;}
    private void work(String text,Job job) {
        busy=true;status.setText(text);refreshEnabled();
        worker.execute(() -> {
            try {job.run();}
            catch(Exception e) {lastHelperResult=null;lastReport="ERROR: "+e.toString();ui(() -> status.setText(lastReport));}
            finally {ui(() -> {busy=false;refreshEnabled();});}
        });
    }
    private void ui(Runnable r) {if(alive)runOnUiThread(() -> {if(alive)r.run();});}
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
    private static String quote(String s) {return "'"+s.replace("'","'\\''")+"'";}
    private JSONObject invoke(String... args) throws Exception {
        File helper=new File(getApplicationInfo().nativeLibraryDir,"libsahelper.so");
        if(!helper.isFile())throw new IOException("ARM64 root helper was not extracted by Android.");
        StringBuilder command=new StringBuilder(quote(helper.getAbsolutePath()));
        for(String a:args)command.append(' ').append(quote(a));
        Process p=new ProcessBuilder("su","-c",command.toString()).redirectErrorStream(true).start();
        ByteArrayOutputStream data=new ByteArrayOutputStream();AtomicReference<IOException> readError=new AtomicReference<>();
        // Drain while the helper runs: a long diagnostic reply must not fill
        // the subprocess pipe while its parent is waiting for it to exit.
        Thread reader=new Thread(() -> {
            try(InputStream in=p.getInputStream()) {byte[] chunk=new byte[2048];int n;
                while((n=in.read(chunk))!=-1) {if(data.size()+n>32768)throw new IOException("Unexpectedly large helper output.");data.write(chunk,0,n);}
            } catch(IOException e) {readError.set(e);}
        },"sa-helper-output");reader.setDaemon(true);reader.start();
        if(!p.waitFor(75,TimeUnit.SECONDS)) {p.destroyForcibly();reader.join(1000);throw new IOException("Root helper timed out. Retain the backup and read preferences before retrying.");}
        reader.join(2000);
        if(reader.isAlive()) {p.getInputStream().close();throw new IOException("Helper output did not close. Read preferences before retrying.");}
        if(readError.get()!=null)throw readError.get();
        String output=data.toString("UTF-8");lastReport=output;
        JSONObject result=null;
        for(String line:output.split("\n"))if(line.trim().startsWith("{")) {try {result=new JSONObject(line.trim());}catch(Exception ignored) {}}
        if(result==null)throw new IOException("No helper response. Grant root to SA Band Lab. Output: "+output);
        if("efs-get39-v2".equals(result.optString("read_profile")))lastEfsResult=result;
        else if("qmi-nas-sa-v1".equals(result.optString("read_profile")))lastHelperResult=result;
        return result;
    }
    private void readPreferences() {
        final int s=slot;
        work("Reading live preferences and NR capabilities…",() -> {
            JSONObject r=invoke("qmi-probe",""+s);JSONObject b=loadBackup(s);
            if(r.optBoolean("ok")) {
                if(b==null) {
                    b=new JSONObject();b.put("schema",2);b.put("read_profile",r.getString("read_profile"));b.put("slot",s);b.put("fingerprint",Build.FINGERPRINT);b.put("baseband",baseband());b.put("device_binding",deviceBinding());b.put("created_ms",System.currentTimeMillis());
                    b.put("original_sa",r.getString("sa_hex"));b.put("expected_sa",r.getString("sa_hex"));b.put("original_nsa",r.getString("nsa_hex"));b.put("pending_sa","");saveBackup(s,b);
                } else if(matchesDevice(b)&&r.getString("nsa_hex").equals(b.optString("original_nsa"))) {
                    String read=r.getString("sa_hex"),pending=b.optString("pending_sa");
                    if(read.equals(pending)||read.equals(b.optString("expected_sa"))) {b.put("expected_sa",read);b.put("pending_sa","");saveBackup(s,b);}
                }
                if(b!=null&&matchesDevice(b)&&b.has("original_radio_mode")) {
                    int mode=r.optInt("rat_mode_preference",-1);
                    if(mode==b.optInt("expected_radio_mode",-2)||mode==b.optInt("pending_radio_mode",-2)||mode==b.optInt("original_radio_mode",-2)) {
                        b.put("expected_radio_mode",mode);b.put("pending_radio_mode",-1);b.put("radio_mode_dirty",mode!=b.getInt("original_radio_mode"));saveBackup(s,b);
                    }
                }
            }
            JSONObject selected=b,change=loadLastChange(s);
            ui(() -> {probe=r;backup=selected;lastChange=change;status.setText(r.optBoolean("ok")?(r.optBoolean("write_ready")?"Live preferences read. Backup and NR capability list are ready.":"Live preferences backed up. Apply disabled: "+r.optString("capability_error")):r.optString("error","Read failed.")+"\nNo preference SET was sent. Copy the diagnostic report above.");showPreferences();});
        });
    }
    private void showPreferences() {
        if(nrOnly!=null)nrOnly.setChecked(probe!=null&&probe.optInt("rat_mode_preference",-1)==64);
        bandBox.removeAllViews();bands.clear();
        if(probe==null||!probe.optBoolean("ok")) {current.setText("Transport or preference read failed. Copy the diagnostic report.");return;}
        supported.setText("Phone-supported 5G bands: "+bandNames(probe.optString("supported_nr_hex"))+"\nSupport does not establish local coverage. Refresh above to see detected cells.");
        current.setText("SA preference: "+bandNames(probe.optString("sa_hex"))+"\nRadio mode: "+(probe.optInt("rat_mode_preference",-1)==64?"NR only":"RAT mask "+probe.optInt("rat_mode_preference",-1))+"\nTransport: "+probe.optString("transport"));
        if(backup==null)return;
        if(!matchesDevice(backup)) {status.setText("Backup is from a different device, firmware or older read format. It is preserved; writes are disabled. Export it and copy the diagnostic report for review.");probe=null;return;}
        String original=backup.optString("original_sa"),currentMask=probe.optString("sa_hex"),supported=probe.optString("supported_nr_hex");
        for(int n=1;n<=512;n++)if(hasBand(original,n)&&hasBand(supported,n)) {
            CheckBox b=new CheckBox(this);b.setText(bandChoiceLabel(n));b.setTextColor(ink);b.setTextSize(16);b.setTag(n);b.setChecked(hasBand(currentMask,n));bandBox.addView(b);bands.add(b);
        }
        if(bands.isEmpty()) {TextView t=new TextView(this);t.setText("No verified selectable NR bands. Apply is disabled; no defaults are guessed.");t.setTextColor(muted);bandBox.addView(t);}
    }
    private void selectBands(boolean checked) {
        for(CheckBox b:bands)b.setChecked(checked);
        status.setText(checked?"All selectable bands selected. Tap Apply to change the SA preference.":"All bands deselected. Select at least one before applying.");
    }
    private AtomicFile changeFile(int s) {return new AtomicFile(new File(getFilesDir(),"qmi-last-change-slot"+s+".json"));}
    private JSONObject loadLastChange(int s) throws Exception {AtomicFile f=changeFile(s);return f.getBaseFile().exists()?new JSONObject(new String(f.readFully(),StandardCharsets.UTF_8)):null;}
    private void recordChange(int s,String operation,JSONObject result) throws Exception {
        JSONObject record=new JSONObject();record.put("slot",s);record.put("operation",operation);record.put("captured_ms",System.currentTimeMillis());record.put("helper_result",result);
        AtomicFile f=changeFile(s);FileOutputStream stream=null;
        try {stream=f.startWrite();stream.write(record.toString().getBytes(StandardCharsets.UTF_8));f.finishWrite(stream);}catch(Exception e) {if(stream!=null)f.failWrite(stream);throw e;}
        ui(() -> lastChange=record);
    }
    private String bandChoiceLabel(int band) {return "n"+band+" · "+(bandSnapshot==null?"Detection not checked":bandSnapshot.bandState(band));}
    private void refreshBandLabels() {for(CheckBox b:bands)b.setText(bandChoiceLabel((Integer)b.getTag()));}
    private static boolean hasBand(String hex,int band) {
        if(!hex.matches("[0-9a-fA-F]{128}"))return false;
        int at=((band-1)/8)*2;return (Integer.parseInt(hex.substring(at,at+2),16)&(1<<((band-1)%8)))!=0;
    }
    private static String bandNames(String hex) {
        ArrayList<String> names=new ArrayList<>();for(int i=1;i<=512;i++)if(hasBand(hex,i))names.add("n"+i);
        return names.isEmpty()?"no explicit band bits":String.join(", ",names);
    }
    private void confirmApply() {
        byte[] mask=new byte[64];int count=0;
        for(CheckBox b:bands)if(b.isChecked()) {int n=(Integer)b.getTag()-1;mask[n/8]|=(byte)(1<<(n%8));count++;}
        if(count==0) {status.setText("Select at least one SA band.");return;}
        StringBuilder h=new StringBuilder();for(byte v:mask)h.append(String.format(Locale.ROOT,"%02x",v&255));final String target=h.toString();
        final boolean nrRequested=nrOnly.isChecked();
        try {RadioModeSelection.target(nrRequested,probe.optInt("rat_mode_preference",-1),backup.optInt("original_radio_mode",-1));}
        catch(IllegalArgumentException e) {status.setText(e.getMessage());return;}
        new AlertDialog.Builder(this).setTitle("Apply SA bands until reboot?").setMessage(bandNames(target)+"\n\n"+(nrRequested?"NR only: on. LTE fallback is disabled.":"NR only: off. Restore the captured earlier mode if currently NR only; otherwise keep the current mode.")+"\n\nApply bands and radio mode together, then verify both. NSA and LTE band preferences are preserved.").setNegativeButton("Cancel",null).setPositiveButton("Apply",(d,w) -> mutate(false,target,nrRequested)).show();
    }
    private void confirmRestore() {
        new AlertDialog.Builder(this).setTitle("Restore captured SA bytes?").setMessage("Restores the exact SA preference captured by this app. The current radio mode and NSA preference are preserved. Any pending NR-only checkbox change is not applied by this Restore button.").setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w) -> mutate(true,backup.optString("original_sa"),false)).show();
    }
    private void mutate(boolean restoring,String target,boolean nrRequested) {
        final int s=slot;final JSONObject b=backup;
        work(restoring?"Restoring and verifying SA…":"Applying and verifying SA…",() -> {
            JSONObject fresh=invoke("qmi-probe",""+s);
            if(!fresh.optBoolean("ok"))throw new IOException(fresh.optString("error"));
            String expected=restoring?fresh.getString("sa_hex"):b.getString("expected_sa");
            int expectedMode=fresh.optInt("rat_mode_preference",-1),targetMode=-1;
            if(!restoring) {
                targetMode=RadioModeSelection.target(nrRequested,expectedMode,b.optInt("original_radio_mode",-1));
                if(nrRequested&&(!b.has("original_radio_mode")||(!b.optBoolean("radio_mode_dirty")&&b.optInt("pending_radio_mode",-1)<0&&expectedMode!=64)))b.put("original_radio_mode",expectedMode);
                b.put("expected_radio_mode",expectedMode);b.put("pending_radio_mode",targetMode);
                if(b.has("original_radio_mode"))b.put("radio_mode_dirty",true);
            }
            b.put("pending_sa",target);saveBackup(s,b);
            JSONObject r=restoring?invoke("qmi-restore",""+s,b.getString("original_sa"),expected,b.getString("original_nsa")):invoke("qmi-apply-plan",""+s,b.getString("original_sa"),expected,b.getString("original_nsa"),target,""+expectedMode,""+targetMode);
            recordChange(s,restoring?"sa_restore":"sa_and_radio_mode_apply",r);
            if(r.optBoolean("ok")) {b.put("expected_sa",target);b.put("pending_sa","");saveBackup(s,b);}
            else if(r.optBoolean("rollback_verified")) {b.put("expected_sa",expected);b.put("pending_sa","");saveBackup(s,b);}
            if(!restoring&&(r.optBoolean("ok")||r.optBoolean("rollback_verified"))) {
                int actual=r.optInt("rat_mode_preference",-1);b.put("expected_radio_mode",actual);b.put("pending_radio_mode",-1);
                if(b.has("original_radio_mode"))b.put("radio_mode_dirty",actual!=b.getInt("original_radio_mode"));saveBackup(s,b);
            }
            ui(() -> {
                if(r.has("sa_hex"))probe=r;else probe=null;
                showPreferences();
                status.setText(r.optBoolean("ok")?(restoring?"Captured SA bytes restored; readback verified.":"SA bands and radio-mode readback verified. Check serving state separately."):r.optString("error"));
            });
        });
    }
    private String baseband() {return Build.getRadioVersion()==null?"unknown":Build.getRadioVersion();}
    private String deviceBinding() {return android.provider.Settings.Secure.getString(getContentResolver(),android.provider.Settings.Secure.ANDROID_ID);}
    private boolean matchesDevice(JSONObject b) {return b.optInt("schema")==2&&"qmi-nas-sa-v1".equals(b.optString("read_profile"))&&Build.FINGERPRINT.equals(b.optString("fingerprint"))&&baseband().equals(b.optString("baseband"))&&deviceBinding().equals(b.optString("device_binding"));}
    private void copyReport() {
        ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("SA Band Lab report",report()));
        Toast.makeText(this,"Report copied",Toast.LENGTH_SHORT).show();
    }
    private String report() {
        JSONObject r=new JSONObject();try {r.put("app","SA Band Lab 0.8");r.put("model",Build.MODEL);r.put("android",Build.VERSION.RELEASE);r.put("fingerprint",Build.FINGERPRINT);r.put("baseband",baseband());r.put("slot",slot);r.put("helper_result",lastHelperResult==null?lastReport:lastHelperResult);if(lastChange!=null)r.put("last_preference_change",lastChange);if(lastEfsResult!=null)r.put("efs_diagnostic",lastEfsResult);r.put("serving_state",radio.getText());if(lastBandObservation!=null)r.put("five_g_observation",lastBandObservation);if(lastModemFrequencies!=null)r.put("modem_frequencies",lastModemFrequencies);if(backup!=null)r.put("backup",backup);return r.toString(2);}catch(Exception e) {return lastReport;}
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
                ui(() -> {probe=null;backup=null;status.setText("Backup imported. Read preferences before restoring.");});
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
        if(checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE,Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},7);return;
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
            ui(() -> {bandSnapshot=snapshot;lastBandObservation=observation;radio.setText(text.toString());refreshBandLabels();status.setText("5G observations refreshed. Nearby cells are included when Android reports them.");});
          } catch(Exception e) {
            JSONObject failure=new JSONObject();failure.put("slot",s);failure.put("error",e.toString());failure.put("complete_band_scan",false);
            ui(() -> {bandSnapshot=null;lastBandObservation=failure;radio.setText("Could not refresh 5G cells: "+e.getMessage()+"\nEnable Location and grant Phone and precise location permissions, then retry.");refreshBandLabels();status.setText("5G detection failed. Band preferences were not changed.");});
          }
        });
    }
    private static Object availableNumber(int value) {return value==Integer.MAX_VALUE?JSONObject.NULL:value;}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==7&&grants.length==3&&grants[0]==0&&grants[1]==0)servingState();
        else if(request==7)status.setText("Serving-state permission was not granted. Preference operations remain available.");
    }
    @Override public void onBackPressed() {if(busy)Toast.makeText(this,"Wait for the modem transaction to finish.",Toast.LENGTH_SHORT).show();else super.onBackPressed();}
    @Override protected void onDestroy() {alive=false;worker.shutdown();super.onDestroy();}
}
