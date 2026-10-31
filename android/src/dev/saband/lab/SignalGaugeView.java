package dev.saband.lab;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Original segmented strength gauge; numeric text always shows the actual sample. */
public final class SignalGaugeView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF ring=new RectF();
    private int reading=SignalHunt.UNKNOWN,best=SignalHunt.UNKNOWN;private float fraction;private ValueAnimator animator;
    public SignalGaugeView(Context context) {super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);setContentDescription("Signal meter, no fresh reading");}
    public void reading(int value,int bestValue) {
        if(value==reading&&bestValue==best)return;reading=value;best=bestValue;
        if(animator!=null)animator.cancel();float target=SignalHunt.fraction(value);
        if(value!=SignalHunt.UNKNOWN&&ValueAnimator.areAnimatorsEnabled()) {
            animator=ValueAnimator.ofFloat(fraction,target);animator.setDuration(250);animator.addUpdateListener(a -> {fraction=(Float)a.getAnimatedValue();invalidate();});animator.start();
        } else {fraction=target;invalidate();}
        setContentDescription(value==SignalHunt.UNKNOWN?"Signal meter, no fresh SS-RSRP reading":
            "SS-RSRP "+value+" dBm"+(bestValue==SignalHunt.UNKNOWN?"":", best this cell "+bestValue+" dBm")+". Less negative means stronger. Gauge spans minus 140 to minus 60 dBm.");
    }
    @Override protected void onDetachedFromWindow() {if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
    @Override protected void onMeasure(int width,int height) {
        int w=MeasureSpec.getSize(width);float font=getResources().getConfiguration().fontScale;
        setMeasuredDimension(resolveSize(w,width),resolveSize(Math.round(w*(font>1.4f?.85f:.73f)),height));
    }
    private float sp(float n) {return n*getResources().getDisplayMetrics().scaledDensity;}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);float w=getWidth(),h=getHeight(),d=getResources().getDisplayMetrics().density,cx=w/2,cy=h*.60f,r=Math.min(w*.40f,h*.55f);
        ring.set(cx-r,cy-r,cx+r,cy+r);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(10*d);
        for(int i=0;i<30;i++) {paint.setColor(reading!=SignalHunt.UNKNOWN&&i/30f<fraction?LabLayout.MINT:LabLayout.LINE);canvas.drawArc(ring,150+i*8,5.5f,false,paint);}
        paint.setStyle(Paint.Style.FILL);
        if(best!=SignalHunt.UNKNOWN&&reading!=SignalHunt.UNKNOWN) {
            double angle=Math.toRadians(150+240*SignalHunt.fraction(best));paint.setColor(LabLayout.INK);
            canvas.drawCircle(cx+(float)Math.cos(angle)*(r+13*d),cy+(float)Math.sin(angle)*(r+13*d),3*d,paint);
        }
        paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setColor(LabLayout.MUTED);paint.setTextSize(Math.min(sp(11),w*.045f));
        canvas.drawText("SS-RSRP · STRENGTH",cx,cy-r*.58f,paint);
        String number=reading==SignalHunt.UNKNOWN?"—":Integer.toString(reading);paint.setTextSize(Math.min(sp(70),w*.25f));
        while(paint.measureText(number)>w*.68f)paint.setTextSize(paint.getTextSize()*.95f);
        paint.setColor(LabLayout.INK);canvas.drawText(number,cx,cy+r*.10f,paint);
        paint.setColor(LabLayout.MUTED);paint.setTextSize(sp(13));canvas.drawText("dBm",cx,cy+r*.34f,paint);
        paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(Math.min(sp(11),w*.036f));
        paint.setTextAlign(Paint.Align.LEFT);canvas.drawText("−140 · weaker",2*d,h-3*d,paint);
        paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText("stronger · −60",w-2*d,h-3*d,paint);
    }
}
