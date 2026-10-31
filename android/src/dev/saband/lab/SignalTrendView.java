package dev.saband.lab;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

/** Actual observation times, with breaks across missing samples and pauses. */
public final class SignalTrendView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path line=new Path();
    private List<SignalHunt.Point> points=Collections.emptyList();private long now;
    public SignalTrendView(Context context) {super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);setContentDescription("Signal trend, no samples yet");}
    public void samples(List<SignalHunt.Point> values,long elapsed) {points=values;now=elapsed;invalidate();setContentDescription("SS-RSRP trend over the last 60 seconds, "+values.size()+" fresh observations. Gaps indicate missing readings.");}
    @Override protected void onMeasure(int width,int height) {setMeasuredDimension(resolveSize(MeasureSpec.getSize(width),width),resolveSize(Math.round((getResources().getConfiguration().fontScale>1.4f?144:116)*getResources().getDisplayMetrics().density),height));}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);float d=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight(),right=w-8*d;
        int min=-115,max=-85;for(SignalHunt.Point p:points) {min=Math.min(min,p.rsrp-4);max=Math.max(max,p.rsrp+4);}
        paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(11*getResources().getDisplayMetrics().scaledDensity);paint.setStrokeWidth(d);
        float left=Math.max(42*d,Math.max(paint.measureText(""+min),paint.measureText(""+max))+9*d),top=paint.getFontSpacing(),bottom=h-paint.getFontSpacing()-12*d;
        paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.RIGHT);
        for(int value:new int[]{max,min}) {float y=bottom-(value-min)/(float)(max-min)*(bottom-top);paint.setColor(LabLayout.LINE);canvas.drawLine(left,y,right,y,paint);paint.setColor(LabLayout.MUTED);canvas.drawText(""+value,left-6*d,y+4*d,paint);}
        paint.setTextAlign(Paint.Align.LEFT);canvas.drawText("−60s",left,h-4*d,paint);paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText("Now",right,h-4*d,paint);
        if(right<=left)return;line.reset();SignalHunt.Point previous=null;float lastX=0,lastY=0;
        for(SignalHunt.Point p:points) {
            if(p.stamp<now-SignalHunt.HISTORY_MS||p.stamp>now)continue;
            float x=right-(now-p.stamp)/(float)SignalHunt.HISTORY_MS*(right-left),y=bottom-(p.rsrp-min)/(float)(max-min)*(bottom-top);
            if(previous==null||p.segment!=previous.segment||p.stamp-previous.stamp>SignalHunt.FRESH_MS)line.moveTo(x,y);else line.lineTo(x,y);
            // Each observation stays visible even when its segment has a single sample.
            paint.setColor(LabLayout.MINT);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(x,y,2*d,paint);previous=p;lastX=x;lastY=y;
        }
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*d);paint.setStrokeJoin(Paint.Join.ROUND);paint.setColor(LabLayout.MINT);canvas.drawPath(line,paint);
        paint.setStyle(Paint.Style.FILL);if(previous!=null)canvas.drawCircle(lastX,lastY,3*d,paint);
    }
}
