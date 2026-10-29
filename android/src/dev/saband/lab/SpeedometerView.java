package dev.saband.lab;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.Locale;

/** Original native dial. Displays measured phase averages, never a synthetic speed. */
public final class SpeedometerView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float shown;private String phase="Ready";private boolean available;
    private ValueAnimator animator;
    public SpeedometerView(Context context) {super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);setContentDescription("Speed meter, ready");}
    public void reset(String phase) {if(animator!=null)animator.cancel();shown=0;available=false;this.phase=phase;invalidate();setContentDescription(phase+", no speed measurement yet");}
    public void speed(double mbps,String phase) {
        if(Double.isNaN(mbps)||Double.isInfinite(mbps)||mbps<0)return;
        this.phase=phase;available=true;if(animator!=null)animator.cancel();
        animator=ValueAnimator.ofFloat(shown,(float)mbps);animator.setDuration(250);animator.addUpdateListener(a -> {shown=(Float)a.getAnimatedValue();invalidate();});animator.start();
        setContentDescription(String.format(Locale.ROOT,"%s, %.1f megabits per second",phase,mbps));
    }
    @Override protected void onDetachedFromWindow() {if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
    @Override protected void onMeasure(int width,int height) {int w=MeasureSpec.getSize(width);setMeasuredDimension(resolveSize(w,width),resolveSize(Math.round(Math.min(w,360*getResources().getDisplayMetrics().density)*0.74f),height));}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);float w=getWidth(),h=getHeight(),cx=w/2,cy=h*0.48f,r=Math.min(w*0.39f,h*0.43f),scale=r/125f;
        RectF ring=new RectF(cx-r,cy-r,cx+r,cy+r);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(10*scale);paint.setColor(LabLayout.LINE);canvas.drawArc(ring,135,270,false,paint);
        float angle=(float)(270*SpeedometerScale.fraction(shown));
        if(available) {
            paint.setColor(0x3354D3BE);paint.setStrokeWidth(18*scale);canvas.drawArc(ring,135,angle,false,paint);
            paint.setShader(new LinearGradient(cx-r,cy,cx+r,cy,new int[]{0xff409BD7,LabLayout.MINT},null,Shader.TileMode.CLAMP));paint.setStrokeWidth(9*scale);canvas.drawArc(ring,135,angle,false,paint);paint.setShader(null);
        }
        paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setTextAlign(Paint.Align.CENTER);
        for(int tick:new int[]{0,10,50,100,250,500,1000,2000,5000}) {
            double radians=Math.toRadians(135+270*SpeedometerScale.fraction(tick));float x=(float)Math.cos(radians),y=(float)Math.sin(radians);
            paint.setColor(LabLayout.MUTED);paint.setStrokeWidth(scale);canvas.drawLine(cx+x*(r-12*scale),cy+y*(r-12*scale),cx+x*(r-17*scale),cy+y*(r-17*scale),paint);
            paint.setStyle(Paint.Style.FILL);paint.setTextSize(9*scale);String label=tick>=1000?(tick/1000)+"k":Integer.toString(tick);
            canvas.drawText(label,cx+x*(r-30*scale),cy+y*(r-30*scale)+3*scale,paint);paint.setStyle(Paint.Style.STROKE);
        }
        if(available) {
            double radians=Math.toRadians(135+angle);float x=(float)Math.cos(radians),y=(float)Math.sin(radians);
            paint.setColor(LabLayout.INK);paint.setStrokeWidth(3*scale);canvas.drawLine(cx+x*(r-8*scale),cy+y*(r-8*scale),cx+x*(r+5*scale),cy+y*(r+5*scale),paint);
        }
        paint.setStyle(Paint.Style.FILL);paint.setTextSize(available&&shown>=1000?36*scale:43*scale);paint.setColor(LabLayout.INK);
        canvas.drawText(available?String.format(Locale.ROOT,"%.1f",shown):"—",cx,cy+15*scale,paint);
        paint.setTextSize(12*scale);paint.setColor(LabLayout.MUTED);canvas.drawText("Mbps",cx,cy+36*scale,paint);
        paint.setTextSize(11*scale);paint.setColor(LabLayout.MINT);canvas.drawText(phase,cx,h-13*scale,paint);
    }
}
