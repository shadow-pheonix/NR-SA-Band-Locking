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

/** Native navigation shell. Views survive tab changes; no modem calls here. */
public final class LabLayout {
    public interface Actions {
        void selectSim(int slot);void read();void refreshNetwork();void apply();void restore();
        void selectAll();void clearAll();void copyReport();void exportBackup();void importBackup();
        void disableReapply();void frequencies();void efs();void networkSettings();void draftChanged();void statusDetails();
        void startSpeed();void stopSpeed();void copySpeed();void clearSpeed();
    }
    public static final int BG=Color.rgb(10,16,24), SURFACE=Color.rgb(18,28,40), LINE=Color.rgb(37,51,66);
    public static final int INK=Color.rgb(235,241,248), MUTED=Color.rgb(154,174,193), MINT=Color.rgb(84,211,190), AMBER=Color.rgb(245,190,109);
    private final Activity activity;private final Actions callbacks;
    public final LinearLayout root;
    public final ArrayList<Button> actions=new ArrayList<>();
    public final GridLayout bandBox;
    public final TextView status,current,radio,supported,modemRadio,bootStatus;
    public final Button apply,restore,selectAll,deselectAll;
    public final Switch nrOnly,persistLock;
    public final Button startSpeed,stopSpeed,copySpeed,clearSpeed;
    public final TextView speedStatus,speedHistory;
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

        LinearLayout speed=scrollPage(UiPresentation.SPEED);heading(speed,"Speedtest","Apply a selection, then compare mobile-data performance.");
        LinearLayout test=card(speed);eyebrow(test,"CELLULAR SPEED TEST");
        speedStatus=text(test,"Ready when you are",22,INK,true);speedStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        text(test,"Download · Upload · Ping (HTTP)\nUses the selected mobile-data SIM. Turn Wi-Fi and VPN off. Apply or discard pending choices first.",13,MUTED,false);
        text(test,"Up to 100 MB download + 20 MB upload payload per run, plus protocol overhead. About 30–60 seconds; slow requests can take longer. Cloudflare receives your IP address and test traffic. Band observations and reports stay on this phone.",12,MUTED,false);
        navigationButton(test,"About the test provider",() -> {
            try {activity.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://speed.cloudflare.com/about")));}
            catch(Exception e) {Toast.makeText(activity,"Open speed.cloudflare.com/about in a browser.",Toast.LENGTH_LONG).show();}
        });
        startSpeed=button(test,"Start speed test",true,callbacks::startSpeed);
        stopSpeed=baseButton("Stop test",false);stopSpeed.setOnClickListener(v -> callbacks.stopSpeed());test.addView(stopSpeed,buttonParams());stopSpeed.setVisibility(View.GONE);
        text(test,"Serving bands are sampled during the test. If Location, permissions or fresh modem observations are unavailable, reports say unknown. Sampling does not prove the band stayed unchanged between observations.",12,MUTED,false);
        LinearLayout history=card(speed);eyebrow(history,"COMPARE RECENT TESTS");
        text(history,"Last 20 runs, stored locally. Only this SIM's runs appear here. Repeat tests in the same place; traffic load and radio conditions can change the result.",12,MUTED,false);
        speedHistory=text(history,"No tests yet.",14,INK,false);speedHistory.setTextIsSelectable(true);
        copySpeed=button(history,"Copy speed test reports",false,callbacks::copySpeed);
        clearSpeed=button(history,"Clear all speed test history",false,callbacks::clearSpeed);

        LinearLayout tools=scrollPage(UiPresentation.TOOLS);heading(tools,"Tools & recovery","Keep your backup. Find details when you need them.");
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
