package dev.saband.lab;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.Locale;
import org.json.*;

/** Native navigation shell. Views survive tab changes; no modem calls here. */
public final class LabLayout {
    public interface Actions {
        void selectSim(int slot);void read();void refreshNetwork();void apply();void restore();
        void selectAll();void clearAll();void copyReport();void exportBackup();void importBackup();
        void disableReapply();void frequencies();void efs();void networkSettings();void draftChanged();void statusDetails();
        void startSpeed();void stopSpeed();void copySpeed();void clearSpeed();void configureTile();void addTile();
    }
    public static final int BG=Color.rgb(10,16,24), SURFACE=Color.rgb(18,28,40), LINE=Color.rgb(37,51,66);
    public static final int INK=Color.rgb(235,241,248), MUTED=Color.rgb(154,174,193), MINT=Color.rgb(84,211,190), AMBER=Color.rgb(245,190,109);
    private final Activity activity;private final Actions callbacks;
    public final LinearLayout root;
    public final ArrayList<Button> actions=new ArrayList<>();
    public final GridLayout bandBox;
    public final TextView status,current,radio,supported,modemRadio,bootStatus,tileSummary;
    public final Button apply,restore,selectAll,deselectAll;
    public final Switch nrOnly,persistLock;
    public final Button startSpeed,stopSpeed,copySpeed,clearSpeed;
    public final TextView speedStatus,speedHistory;
    public final Spinner speedDuration;
    public final SpeedometerView speedometer;
    public final TextView speedDownload,speedUpload,speedPing,speedElapsed,speedServing;
    private final LinearLayout speedHistoryCards;
    private boolean showOlderSpeedTests;
    private final FrameLayout pages;
    private final View[] destinations=new View[UiPresentation.PAGE_COUNT];private final ScrollView[] scrolls=new ScrollView[UiPresentation.PAGE_COUNT];
    private final LinearLayout[] navigation=new LinearLayout[UiPresentation.PAGE_COUNT];private final TextView[] navLabels=new TextView[UiPresentation.PAGE_COUNT];private final Glyph[] navIcons=new Glyph[UiPresentation.PAGE_COUNT];
    private final Button[] sims=new Button[2];private final ProgressBar progress;
    private final TextView heroBand,heroLabel,heroDetail,rsrpValue,sinrValue,cellValue,nearbyValue,preferenceValue,backupValue,readHint,selectionTitle,selectionHint,draftLabel,deviceValue;
    private final TextView bandEmpty;
    private int page=UiPresentation.OVERVIEW;

    public LabLayout(Activity activity,Actions callbacks) {
        this.activity=activity;this.callbacks=callbacks;
        root=column();root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((view,insets) -> {
            Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
        LinearLayout header=row();header.setPadding(dp(20),dp(12),dp(20),dp(10));
        ImageView mark=new ImageView(activity);mark.setImageDrawable(activity.getApplicationInfo().loadIcon(activity.getPackageManager()));mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams markSize=new LinearLayout.LayoutParams(dp(32),dp(32));markSize.setMarginEnd(dp(10));header.addView(mark,markSize);
        LinearLayout brand=column();text(brand,"SA BAND LAB",11,MINT,true);text(brand,"Band Lab",24,INK,true);
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout simBar=row();simBar.setPadding(dp(4),dp(4),dp(4),dp(4));simBar.setBackground(shape(SURFACE,16,LINE));
        for(int i=0;i<2;i++) {
            final int slot=i;Button b=new Button(activity);b.setText("SIM "+(i+1));b.setTextSize(12);b.setTypeface(null,Typeface.BOLD);b.setAllCaps(false);
            b.setPadding(dp(10),0,dp(10),0);b.setMinWidth(0);b.setMinimumWidth(dp(54));b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));
            b.setOnClickListener(v -> callbacks.selectSim(slot));b.setContentDescription("Select SIM "+(i+1));sims[i]=b;simBar.addView(b,new LinearLayout.LayoutParams(-2,-2));
        }
        header.addView(simBar);root.addView(header);
        LinearLayout notice=row();notice.setPadding(dp(20),0,dp(20),dp(8));
        progress=new ProgressBar(activity);progress.setIndeterminateTintList(ColorStateList.valueOf(MINT));progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(18),dp(18));pp.setMarginEnd(dp(10));notice.addView(progress,pp);
        status=new TextView(activity);status.setText("Read preferences to get started.");status.setTextColor(MUTED);status.setTextSize(12);status.setMaxLines(3);
        status.setEllipsize(TextUtils.TruncateAt.END);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setMinHeight(dp(48));
        status.setPadding(0,dp(8),0,dp(8));status.setOnClickListener(v -> callbacks.statusDetails());status.setTooltipText("Tap for full operation details");
        notice.addView(status,new LinearLayout.LayoutParams(0,-2,1));root.addView(notice);
        pages=new FrameLayout(activity);root.addView(pages,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout overview=scrollPage(UiPresentation.OVERVIEW);heading(overview,"Network overview","See what your phone reports, then choose how it connects.");
        LinearLayout hero=card(overview);eyebrow(hero,"SERVING 5G BAND");
        heroBand=text(hero,"—",56,INK,true);heroBand.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        heroLabel=text(hero,"Not checked",13,MINT,true);heroDetail=text(hero,"Refresh to see cells reported by your phone.",12,MUTED,false);
        button(hero,"Refresh network",true,callbacks::refreshNetwork);
        LinearLayout metrics=row();margin(overview,metrics,0,0,0,14);
        if(activity.getResources().getConfiguration().fontScale>1.4f)metrics.setOrientation(LinearLayout.VERTICAL);
        rsrpValue=metric(metrics,"SS-RSRP","dBm",true);sinrValue=metric(metrics,"SS-SINR","dB",true);cellValue=metric(metrics,"NR CELLS","reported",false);
        LinearLayout neighbors=card(overview);eyebrow(neighbors,"NEARBY BANDS");nearbyValue=text(neighbors,"Not checked",21,INK,true);
        text(neighbors,"Only cells Android reports are shown. Missing bands may still be available.",12,MUTED,false);
        LinearLayout preferences=card(overview);eyebrow(preferences,"MODEM PREFERENCE");preferenceValue=text(preferences,"Not read yet",20,INK,true);
        text(preferences,"A selected preference and a serving cell are different checks.",12,MUTED,false);
        navigationButton(preferences,"Manage SA bands",() -> showPage(UiPresentation.BANDS));
        navigationButton(preferences,"Compare speeds",() -> showPage(UiPresentation.SPEED));
        LinearLayout details=card(overview);radio=text(details,"Refresh network to see serving and nearby cell details.",13,MUTED,false);radio.setTextIsSelectable(true);
        fold(details,"Cell details",radio,false);

        LinearLayout bandsPage=column();destinations[UiPresentation.BANDS]=bandsPage;pages.addView(bandsPage,new FrameLayout.LayoutParams(-1,-1));
        ScrollView bandScroll=scroll();scrolls[UiPresentation.BANDS]=bandScroll;bandsPage.addView(bandScroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bandContent=column();bandContent.setPadding(dp(20),dp(8),dp(20),dp(14));bandScroll.addView(bandContent);
        heading(bandContent,"Band selection","SA bands and radio mode. One Apply action.");
        LinearLayout readCard=card(bandContent);eyebrow(readCard,"01  READ & BACK UP");
        readHint=text(readCard,"Read this SIM's live preferences to unlock the controls. Root is required.",13,MUTED,false);
        current=text(readCard,"No modem preferences read yet.",13,INK,false);current.setTextIsSelectable(true);
        button(readCard,"Read live preferences",false,callbacks::read);
        section(bandContent,"02  Choose SA bands","Phone support does not establish local coverage.");
        LinearLayout bulk=row();selectAll=button(bulk,"Select all",false,callbacks::selectAll);deselectAll=button(bulk,"Clear selection",false,callbacks::clearAll);
        equalButtons(bulk,selectAll,deselectAll);margin(bandContent,bulk,0,0,0,8);
        bandEmpty=text(bandContent,"Your selectable bands will appear after a successful read.",13,MUTED,false);
        bandBox=new GridLayout(activity);bandBox.setColumnCount(Math.max(2,Math.min(4,(activity.getResources().getConfiguration().screenWidthDp-40)/150)));bandBox.setUseDefaultMargins(false);bandContent.addView(bandBox,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout options=card(bandContent);eyebrow(options,"03  RADIO & REBOOT");
        nrOnly=toggle(options,"NR only (5G)","Use 5G without LTE fallback. Saved with your band selection.");
        divider(options);persistLock=toggle(options,"Reapply after reboot","Optional. Reapply the verified selection after boot and first unlock.");
        bootStatus=text(options,"Saved reboot plan: not checked.",12,MUTED,false);
        LinearLayout footer=column();footer.setPadding(dp(20),dp(12),dp(20),dp(10));footer.setBackground(shape(SURFACE,0,LINE));
        LinearLayout summary=row();selectionTitle=text(summary,"Read preferences first",16,INK,true);summary.getChildAt(0).setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        draftLabel=text(summary,"",11,MINT,true);draftLabel.setLayoutParams(new LinearLayout.LayoutParams(-2,-2));footer.addView(summary);
        selectionHint=text(footer,"Nothing changes until you press Apply.",12,MUTED,false);
        apply=button(footer,"Apply SA bands until reboot",true,callbacks::apply);
        if(activity.getResources().getConfiguration().screenHeightDp<540)bandContent.addView(footer);else bandsPage.addView(footer);

        LinearLayout speed=scrollPage(UiPresentation.SPEED);heading(speed,"Speedtest","Measure your current connection.");
        LinearLayout test=card(speed);
        speedStatus=text(test,"Ready to test",14,MINT,true);speedStatus.setGravity(Gravity.CENTER);speedStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        speedometer=new SpeedometerView(activity);test.addView(speedometer,new LinearLayout.LayoutParams(-1,-2));
        speedElapsed=text(test,"Choose a duration and start",12,MUTED,false);speedElapsed.setGravity(Gravity.CENTER);
        speedServing=text(test,"SIM 1 · mobile data · Cloudflare",12,MUTED,false);speedServing.setGravity(Gravity.CENTER);speedServing.setPadding(0,dp(6),0,dp(10));
        text(test,"Duration per direction",12,MUTED,false);
        speedDuration=new Spinner(activity);ArrayAdapter<String> durations=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,new String[]{"20 seconds · Standard","60 seconds · Extended"});
        speedDuration.setAdapter(durations);speedDuration.setMinimumHeight(dp(48));speedDuration.setContentDescription("Speed test duration per direction");test.addView(speedDuration,new LinearLayout.LayoutParams(-1,-2));
        startSpeed=button(test,"Start speed test",true,callbacks::startSpeed);
        stopSpeed=baseButton("Stop test",false);stopSpeed.setOnClickListener(v -> callbacks.stopSpeed());test.addView(stopSpeed,buttonParams());stopSpeed.setVisibility(View.GONE);
        text(test,"Uses mobile data. Longer tests can use several GB.",11,MUTED,false).setPadding(0,dp(8),0,0);
        LinearLayout speedMetrics=row();if(activity.getResources().getConfiguration().fontScale>1.4f)speedMetrics.setOrientation(LinearLayout.VERTICAL);
        speedDownload=metric(speedMetrics,"DOWNLOAD","Mbps",true);speedUpload=metric(speedMetrics,"UPLOAD","Mbps",true);speedPing=metric(speedMetrics,"PING · HTTP","ms",false);margin(speed,speedMetrics,0,0,0,14);
        speedDownload.setTextSize(19);speedUpload.setTextSize(19);speedPing.setTextSize(19);
        LinearLayout information=card(speed),informationContents=column();
        text(informationContents,"Use the selected mobile-data SIM with Wi-Fi/VPN off. Apply or discard pending band choices first. Location and Phone/Precise location permissions let Android report the serving band.",13,MUTED,false);
        text(informationContents,"Standard tests run 20 seconds per direction; Extended runs 60 seconds each. No fixed MB cutoff. At 1 Gbps, 20s is about 2.5 GB per direction; 60s is about 7.5 GB, plus overhead. Stop or leaving the app ends a run.",13,MUTED,false).setPadding(0,dp(10),0,dp(10));
        text(informationContents,"Cloudflare receives your IP address and generated test traffic. Reports and cell observations stay local. Serving bands are sampled at phase boundaries and may be unknown. The meter shows the measured phase average; Ping is HTTP response latency.",13,MUTED,false);
        navigationButton(informationContents,"About the test provider",() -> {
            try {activity.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://speed.cloudflare.com/about")));}
            catch(Exception e) {Toast.makeText(activity,"Open speed.cloudflare.com/about in a browser.",Toast.LENGTH_LONG).show();}
        });
        information.addView(informationContents);fold(information,"Test details & data use",informationContents,false);
        LinearLayout history=card(speed);eyebrow(history,"RECENT RESULTS");
        speedHistory=text(history,"No tests yet.",13,MUTED,false);speedHistory.setTextIsSelectable(true);
        speedHistoryCards=column();history.addView(speedHistoryCards);
        copySpeed=button(history,"Copy reports",false,callbacks::copySpeed);
        clearSpeed=button(history,"Clear history",false,callbacks::clearSpeed);

        LinearLayout tools=scrollPage(UiPresentation.TOOLS);heading(tools,"Tools & recovery","Keep your backup. Find details when you need them.");
        LinearLayout tile=card(tools);eyebrow(tile,"5G QUICK SETTINGS TILE");
        tileSummary=text(tile,"On: tile bands + NR only. Off: LTE only.",16,INK,true);
        text(tile,"Choose the SIM above, read live preferences, then configure the tile's own bands. Setup uses LTE only by default. Tile actions turn off reboot reapply for that SIM; changes last until reboot.",12,MUTED,false);
        button(tile,"Configure 5G tile",false,callbacks::configureTile);button(tile,"Add tile to Quick Settings",false,callbacks::addTile);
        LinearLayout backups=card(tools);eyebrow(backups,"YOUR BACKUP");backupValue=text(backups,"No backup loaded",18,INK,true);
        text(backups,"Your original SA preferences stay saved on this phone. Export a copy before testing.",13,MUTED,false);
        button(backups,"Export backups",false,callbacks::exportBackup);button(backups,"Import backups",false,callbacks::importBackup);
        LinearLayout recovery=card(tools);eyebrow(recovery,"RECOVERY");
        restore=button(recovery,"Restore captured SA preferences",false,callbacks::restore);restore.setTextColor(AMBER);
        text(recovery,"Restores the captured SA bands and turns off reboot reapply. Keeps the current radio mode.",12,MUTED,false);
        button(recovery,"Disable reboot reapply",false,callbacks::disableReapply);
        LinearLayout diagnostics=card(tools);eyebrow(diagnostics,"DIAGNOSTICS");text(diagnostics,"Something needs a closer look?",17,INK,true);
        text(diagnostics,"Copy the report after an operation. It includes modem replies and backup details; review it before sharing.",12,MUTED,false);
        button(diagnostics,"Copy diagnostic report",true,callbacks::copyReport);
        LinearLayout advanced=column();
        button(advanced,"Read modem neighbor frequencies",false,callbacks::frequencies);
        modemRadio=text(advanced,"Optional query. Some firmware does not support neighbor-frequency discovery.",12,MUTED,false);modemRadio.setTextIsSelectable(true);
        divider(advanced);button(advanced,"Check legacy EFS path",false,callbacks::efs);
        text(advanced,"A missing legacy EFS path does not mean QMI band locking failed.",12,MUTED,false);
        diagnostics.addView(advanced);fold(diagnostics,"Advanced modem checks",advanced,false);
        LinearLayout device=card(tools);eyebrow(device,"DEVICE & CAPABILITIES");
        deviceValue=text(device,Build.MODEL+" · Android "+Build.VERSION.RELEASE,16,INK,true);
        supported=text(device,"Read preferences to load reported NR band support.",12,MUTED,false);supported.setTextIsSelectable(true);
        button(device,"Open mobile network settings",false,callbacks::networkSettings);
        text(tools,"SA Band Lab · experimental\nSA only · root required for modem control",12,MUTED,false);

        LinearLayout bottom=row();bottom.setPadding(dp(10),dp(6),dp(10),dp(6));bottom.setBackgroundColor(SURFACE);
        String[] labels={"Overview","Bands","Speedtest","Tools"};
        for(int i=0;i<UiPresentation.PAGE_COUNT;i++) {
            final int target=i;LinearLayout item=column();item.setGravity(Gravity.CENTER);item.setMinimumHeight(dp(64));item.setPadding(dp(8),dp(6),dp(8),dp(6));
            Glyph icon=new Glyph(activity,i);item.addView(icon,new LinearLayout.LayoutParams(dp(42),dp(28)));
            TextView label=text(item,labels[i],11,MUTED,true);label.setGravity(Gravity.CENTER);label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            item.setOnClickListener(v -> showPage(target));item.setFocusable(true);item.setContentDescription(labels[i]+" tab");
            navigation[i]=item;navIcons[i]=icon;navLabels[i]=label;bottom.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        root.addView(bottom);showPage(UiPresentation.OVERVIEW);setSlot(0);setBusy(false);
    }

    public int page() {return page;}
    public void showPage(int next) {
        int target=next<0||next>=UiPresentation.PAGE_COUNT?UiPresentation.OVERVIEW:next;boolean changed=target!=page;page=target;
        for(int i=0;i<UiPresentation.PAGE_COUNT;i++) {
            destinations[i].animate().cancel();destinations[i].setAlpha(1f);
            destinations[i].setVisibility(i==page?View.VISIBLE:View.GONE);
            if(i==page&&changed) {destinations[i].setAlpha(0f);destinations[i].animate().alpha(1f).setDuration(140).start();}
            navigation[i].setSelected(i==page);navLabels[i].setTextColor(i==page?MINT:MUTED);navIcons[i].active=i==page;navIcons[i].invalidate();
        }
        destinations[page].setAccessibilityPaneTitle(UiPresentation.pageTitle(page));
    }
    public void setSlot(int slot) {
        speedServing.setText("SIM "+(slot+1)+" · mobile data · Cloudflare");
        for(int i=0;i<2;i++) {sims[i].setSelected(i==slot);sims[i].setTextColor(i==slot?BG:MUTED);sims[i].setBackground(shape(i==slot?MINT:Color.TRANSPARENT,12,Color.TRANSPARENT));}
    }
    public void setBusy(boolean busy) {progress.setVisibility(busy?View.VISIBLE:View.GONE);for(Button sim:sims)sim.setEnabled(!busy);}
    public void showNetwork(UiPresentation.Network network,String freshness,String rat) {
        update(heroBand,network.band);heroBand.setTextSize(network.band.length()>10?32:56);update(heroLabel,rat.isEmpty()?network.label:rat+" · "+network.label);heroLabel.setTextColor(network.recent?MINT:MUTED);
        update(heroDetail,freshness+"\n"+network.detail);update(rsrpValue,network.rsrp);update(sinrValue,network.sinr);update(cellValue,network.count<0?"—":Integer.toString(network.count));update(nearbyValue,network.nearby);
    }
    public void showPreferencesSummary(String bands,String mode,boolean ready,boolean hasBackup,boolean matching,String transport) {
        update(preferenceValue,ready?bands+"\n"+mode:"Read preferences to check");
        update(readHint,ready?"Live preferences read. Your original backup is preserved.":"Read this SIM's live preferences to unlock the controls. Root is required.");
        update(backupValue,!hasBackup?"Read preferences to capture a backup":matching?"Original preferences captured":"Backup does not match this firmware");
        update(deviceValue,Build.MODEL+" · Android "+Build.VERSION.RELEASE+(transport.isEmpty()?"":"\n"+transport));
    }
    public void showDraft(int count,boolean dirty,boolean ready,boolean nr,boolean reboot) {
        update(selectionTitle,ready?UiPresentation.selected(count):"Read preferences first");update(draftLabel,ready&&dirty?"UNAPPLIED":"");
        update(selectionHint,ready?(nr?"5G only":"Keep or restore fallback")+" · "+(reboot?"Reapply after reboot":"Until reboot"):"Nothing changes until you press Apply.");
    }
    public void speedPhase(String phase) {
        speedStatus.setText(phase.equals("Latency")?"Measuring ping":phase);speedometer.reset(phase);speedElapsed.setText("Preparing measurement…");
    }
    public void beginSpeedTest() {
        speedDownload.setContentDescription(null);speedUpload.setContentDescription(null);
        speedometer.reset("Checking connection");speedDownload.setText("—");speedUpload.setText("—");speedPing.setText("—");speedElapsed.setText("Connecting…");speedServing.setText("Serving band: not sampled yet");
    }
    public void idleSpeed() {beginSpeedTest();speedStatus.setText("Ready to test");speedometer.reset("Ready");speedElapsed.setText("Choose a duration and start");}
    public void speedProgress(String phase,double mbps,long bytes,int elapsed,int duration) {
        speedometer.speed(mbps,phase+" · average");
        speedElapsed.setText(String.format(Locale.ROOT,"%d / %d sec · %.1f MB",elapsed,duration,bytes/1000000.0));
        (phase.equals("Download")?speedDownload:speedUpload).setText(String.format(Locale.ROOT,"%.1f",mbps));
    }
    public void showSpeedResult(JSONObject report) {
        speedStatus.setText(SpeedTestStore.outcome(report));
        JSONObject down=report.optJSONObject("download"),up=report.optJSONObject("upload");
        speedDownload.setText(speedValue(down));speedUpload.setText(speedValue(up));
        speedPing.setText(report.isNull("http_latency_ms")?"—":String.format(Locale.ROOT,"%.0f",report.optDouble("http_latency_ms")));
        JSONObject last=up!=null?up:down;
        if(last!=null)speedometer.speed(last.optDouble("mbps"),up!=null?"Last upload":"Last download");else speedometer.reset("No speed result");
        String note=SpeedTestStore.measurementNote(report);
        speedElapsed.setText(note.isEmpty()?"Results saved · tap a history entry for details":note+"\n* Measured speed retained · see history for details");
        speedDownload.setContentDescription("Download "+speedValue(down)+" Mbps"+(SpeedTestStore.partial(down)?", interrupted sample":""));
        speedUpload.setContentDescription("Upload "+speedValue(up)+" Mbps"+(SpeedTestStore.partial(up)?", interrupted sample":""));
        speedServing.setText("Serving (sampled): "+report.optString("serving_bands_sampled","unknown"));
    }
    private static String speedValue(JSONObject rate) {return rate==null?"—":String.format(Locale.ROOT,"%.1f%s",rate.optDouble("mbps"),SpeedTestStore.partial(rate)?"*":"");}
    public void showSpeedHistory(JSONArray history,int slot) {
        speedHistoryCards.removeAllViews();int count=0;
        for(int i=0;i<history.length();i++) {JSONObject r=history.optJSONObject(i);if(r!=null&&r.optInt("slot",-1)==slot)count++;}
        speedHistory.setText(count==0?"No tests for this SIM yet.":count+" saved · tap a result for details");
        int shown=0;
        for(int i=0;i<history.length();i++) {
            final JSONObject report=history.optJSONObject(i);if(report==null||report.optInt("slot",-1)!=slot)continue;
            if(!showOlderSpeedTests&&shown>=3)break;shown++;
            LinearLayout entry=column();entry.setPadding(dp(2),dp(14),dp(2),dp(14));entry.setMinimumHeight(dp(72));
            text(entry,report.optString("serving_bands_sampled","unknown"),16,MINT,true);
            String when=java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(new java.util.Date(report.optLong("started_ms")));
            text(entry,when+" · "+SpeedTestStore.outcome(report),11,MUTED,false).setPadding(0,dp(4),0,dp(7));
            String values="↓ "+speedValue(report.optJSONObject("download"))+"   ↑ "+speedValue(report.optJSONObject("upload"))+" Mbps";
            String ping=report.isNull("http_latency_ms")?"—":String.format(Locale.ROOT,"%.0f",report.optDouble("http_latency_ms"));
            text(entry,values+" · Ping "+ping+" ms",13,INK,true);
            entry.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2254D3BE),null,null));entry.setFocusable(true);
            entry.setOnClickListener(v -> new android.app.AlertDialog.Builder(activity).setTitle("Speed test report")
                .setMessage(SpeedTestStore.render(new JSONArray().put(report),slot)).setPositiveButton("Close",null).setNeutralButton("Copy",(d,w) -> {
                    android.content.ClipboardManager clipboard=activity.getSystemService(android.content.ClipboardManager.class);
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Speed test report",SpeedTestStore.render(new JSONArray().put(report),slot)+"\n\n"+report.toString()));
                    Toast.makeText(activity,"Report copied",Toast.LENGTH_SHORT).show();
                }).show());
            speedHistoryCards.addView(entry,new LinearLayout.LayoutParams(-1,-2));
            View line=new View(activity);line.setBackgroundColor(LINE);speedHistoryCards.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        }
        if(count>3)navigationButton(speedHistoryCards,showOlderSpeedTests?"Show latest 3":"Show older results ("+(count-3)+")",() -> {showOlderSpeedTests=!showOlderSpeedTests;showSpeedHistory(history,slot);});
    }
    public void showBandEmpty(String message) {bandEmpty.setText(message);bandEmpty.setVisibility(message.isEmpty()?View.GONE:View.VISIBLE);}
    public CheckBox addBand(int band,String observation,boolean checked) {
        CheckBox box=new CheckBox(activity);box.setTag(band);box.setTextColor(INK);box.setTextSize(16);box.setButtonTintList(ColorStateList.valueOf(MINT));
        box.setPadding(dp(10),dp(12),dp(8),dp(12));box.setMinHeight(dp(78));box.setChecked(checked);
        StateListDrawable backgrounds=new StateListDrawable();backgrounds.addState(new int[]{android.R.attr.state_checked},shape(Color.rgb(20,53,49),16,MINT));backgrounds.addState(new int[]{},shape(SURFACE,16,LINE));
        box.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2254D3BE),backgrounds,null));labelBand(box,band,observation);
        int index=bandBox.getChildCount(),columns=bandBox.getColumnCount(),column=index%columns;
        GridLayout.LayoutParams p=new GridLayout.LayoutParams(GridLayout.spec(index/columns),GridLayout.spec(column,1f));
        p.width=0;p.height=-2;p.setMargins(column==0?0:dp(5),dp(5),column==columns-1?0:dp(5),dp(5));box.setLayoutParams(p);bandBox.addView(box);
        box.setOnCheckedChangeListener((button,value) -> callbacks.draftChanged());return box;
    }
    public void labelBand(CheckBox box,int band,String observation) {
        String title="n"+band;if(TextUtils.equals(box.getText(),title+"\n"+observation))return;
        SpannableString text=new SpannableString(title+"\n"+observation);
        text.setSpan(new StyleSpan(Typeface.BOLD),0,title.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new RelativeSizeSpan(0.70f),title.length()+1,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(observation.equals("Serving")?MINT:MUTED),title.length()+1,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        box.setText(text);box.setContentDescription(title+", "+observation);
    }
    public int scrollY(int page) {return scrolls[page].getScrollY();}
    public void restoreScroll(int page,int y) {scrolls[page].post(() -> scrolls[page].scrollTo(0,y));}
    private LinearLayout scrollPage(int index) {
        ScrollView scroll=scroll();scrolls[index]=scroll;destinations[index]=scroll;pages.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout content=column();content.setPadding(dp(20),dp(8),dp(20),dp(24));scroll.addView(content);return content;
    }
    private ScrollView scroll() {ScrollView v=new ScrollView(activity);v.setFillViewport(true);v.setClipToPadding(false);v.setVerticalScrollBarEnabled(false);return v;}
    private LinearLayout card(LinearLayout parent) {LinearLayout v=column();v.setPadding(dp(18),dp(16),dp(18),dp(16));v.setBackground(shape(SURFACE,22,LINE));margin(parent,v,0,0,0,14);return v;}
    private void heading(LinearLayout parent,String title,String subtitle) {text(parent,title,27,INK,true);TextView hint=text(parent,subtitle,13,MUTED,false);hint.setPadding(0,dp(5),0,dp(20));}
    private void section(LinearLayout parent,String title,String subtitle) {text(parent,title,16,INK,true);TextView sub=text(parent,subtitle,12,MUTED,false);sub.setPadding(0,dp(3),0,dp(10));}
    private void eyebrow(LinearLayout parent,String title) {TextView v=text(parent,title,10,MUTED,true);v.setLetterSpacing(0.13f);v.setPadding(0,0,0,dp(8));}
    private TextView metric(LinearLayout row,String name,String unit,boolean margin) {
        LinearLayout cell=column();cell.setBackground(shape(SURFACE,18,LINE));cell.setPadding(dp(13),dp(13),dp(10),dp(13));
        TextView title=text(cell,name,9,MUTED,true);title.setLetterSpacing(0.04f);TextView value=text(cell,"—",23,INK,true);text(cell,unit,10,MUTED,false);
        boolean horizontal=row.getOrientation()==LinearLayout.HORIZONTAL;
        LinearLayout.LayoutParams p=horizontal?new LinearLayout.LayoutParams(0,-2,1):new LinearLayout.LayoutParams(-1,-2);
        if(margin) {if(horizontal)p.setMarginEnd(dp(8));else p.bottomMargin=dp(8);}row.addView(cell,p);return value;
    }
    private Switch toggle(LinearLayout parent,String title,String hint) {
        Switch v=new Switch(activity);v.setText(title);v.setTextSize(15);v.setTextColor(INK);v.setTypeface(null,Typeface.BOLD);v.setMinHeight(dp(48));v.setShowText(false);
        v.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{MINT,MUTED}));
        v.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{0x8854D3BE,LINE}));
        parent.addView(v,new LinearLayout.LayoutParams(-1,-2));text(parent,hint,12,MUTED,false);v.setOnCheckedChangeListener((button,checked) -> callbacks.draftChanged());return v;
    }
    private Button button(LinearLayout parent,String label,boolean primary,Runnable action) {
        Button b=baseButton(label,primary);b.setOnClickListener(v -> action.run());actions.add(b);parent.addView(b,buttonParams());return b;
    }
    private void navigationButton(LinearLayout parent,String label,Runnable action) {Button b=baseButton(label,false);b.setOnClickListener(v -> action.run());parent.addView(b,buttonParams());}
    private Button baseButton(String label,boolean primary) {
        Button b=new Button(activity);b.setText(label);b.setTextSize(14);b.setTypeface(null,Typeface.BOLD);b.setAllCaps(false);b.setTextColor(primary?BG:INK);
        b.setMinHeight(dp(52));b.setMinimumHeight(dp(52));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(12),dp(9),dp(12),dp(9));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(primary?0x220A1018:0x2254D3BE),shape(primary?MINT:Color.TRANSPARENT,14,primary?MINT:LINE),null));return b;
    }
    private LinearLayout.LayoutParams buttonParams() {LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);return p;}
    private void equalButtons(LinearLayout row,Button first,Button second) {
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1);left.setMarginEnd(dp(5));first.setLayoutParams(left);
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,-2,1);right.setMarginStart(dp(5));second.setLayoutParams(right);
    }
    private void fold(LinearLayout parent,String label,View contents,boolean expanded) {
        parent.removeView(contents);Button disclosure=baseButton(label+(expanded?"  −":"  +"),false);disclosure.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        disclosure.setOnClickListener(v -> {boolean show=contents.getVisibility()!=View.VISIBLE;contents.setVisibility(show?View.VISIBLE:View.GONE);disclosure.setText(label+(show?"  −":"  +"));});
        parent.addView(disclosure,buttonParams());parent.addView(contents);contents.setVisibility(expanded?View.VISIBLE:View.GONE);
    }
    private void divider(LinearLayout parent) {View line=new View(activity);line.setBackgroundColor(LINE);margin(parent,line,0,14,0,8);line.getLayoutParams().height=dp(1);}
    private void margin(LinearLayout parent,View child,int left,int top,int right,int bottom) {LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(left),dp(top),dp(right),dp(bottom));parent.addView(child,p);}
    private TextView text(LinearLayout parent,String value,int size,int color,boolean bold) {
        TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);v.setLineSpacing(dp(3),1f);
        if(bold)v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;
    }
    private LinearLayout column() {LinearLayout v=new LinearLayout(activity);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row() {LinearLayout v=new LinearLayout(activity);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private GradientDrawable shape(int color,int radius,int stroke) {GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=Color.TRANSPARENT)d.setStroke(dp(1),stroke);return d;}
    private int dp(float n) {return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private static void update(TextView view,String value) {if(!TextUtils.equals(view.getText(),value))view.setText(value);}
    private static final class Glyph extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final int kind;boolean active;
        Glyph(Activity context,int kind) {super(context);this.kind=kind;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);float s=getHeight()/28f;canvas.save();canvas.scale(s,s);canvas.translate((getWidth()/s-24)/2,2);
            paint.setColor(active?0x2254D3BE:Color.TRANSPARENT);paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(-8,-2,32,26,13,13,paint);
            paint.setColor(active?MINT:MUTED);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            if(kind==0) {canvas.drawRoundRect(3,3,21,21,5,5,paint);canvas.drawLine(7,15,7,17,paint);canvas.drawLine(12,10,12,17,paint);canvas.drawLine(17,7,17,17,paint);}
            else if(kind==1) {canvas.drawLine(4,7,20,7,paint);canvas.drawLine(4,17,20,17,paint);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(9,7,3,paint);canvas.drawCircle(16,17,3,paint);}
            else if(kind==UiPresentation.SPEED) {canvas.drawArc(3,5,21,23,180,180,false,paint);canvas.drawLine(12,14,18,8,paint);canvas.drawCircle(12,14,2,paint);}
            else {canvas.drawRoundRect(4,4,20,21,3,3,paint);canvas.drawLine(8,9,16,9,paint);canvas.drawLine(8,13,16,13,paint);canvas.drawLine(8,17,12,17,paint);}
            canvas.restore();
        }
    }
}
