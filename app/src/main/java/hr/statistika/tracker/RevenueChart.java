package hr.statistika.tracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Twelve months are always present. Touch a bar to hear/read its exact value. */
public final class RevenueChart extends View {
    public static final String[] MONTHS = {"Sij","Velj","Ožu","Tra","Svi","Lip","Srp","Kol","Ruj","Lis","Stu","Pro"};
    private final long[] values;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int textColor, lineColor;
    private int selected = -1;
    private java.util.function.IntConsumer listener;
    public RevenueChart(Context context,long[] values,int textColor,int lineColor) {
        super(context); this.values=values; this.textColor=textColor; this.lineColor=lineColor;
        setContentDescription(description()); setFocusable(true); setMinimumHeight(dp(230));
    }
    public void onMonthSelected(java.util.function.IntConsumer listener) { this.listener=listener; }
    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density); }
    private String description() {
        StringBuilder text = new StringBuilder("Mjesečna naplata. ");
        for(int i=0;i<12;i++) text.append(MONTHS[i]).append(": ").append(Domain.money(values[i])).append(". ");
        return text.toString();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left=dp(54), right=getWidth()-dp(8), top=dp(22), bottom=getHeight()-dp(35);
        long maximum=0; for(long value:values) maximum=Math.max(maximum,value);
        double scale=maximum == 0 ? 10000 : maximum*1.15;
        paint.setTextSize(dp(10)); paint.setStrokeWidth(dp(1));
        for(int i=0;i<=3;i++) {
            float y=bottom-(bottom-top)*i/3;
            paint.setColor(lineColor); canvas.drawLine(left,y,right,y,paint);
            paint.setColor(textColor); paint.setTextAlign(Paint.Align.RIGHT);
            long cents=(long)(scale*i/3);
            String label=cents >= 100000 ? String.format(java.util.Locale.ROOT,"%.1fk",cents/100000.0) : String.valueOf(cents/100);
            canvas.drawText(label+" €",left-dp(8),y+dp(3),paint);
        }
        float step=(right-left)/12;
        for(int i=0;i<12;i++) {
            float x=left+step*i+step/2;
            float h=(float)(values[i]/scale*(bottom-top));
            paint.setColor(i==selected ? 0xff183aa6 : 0xff5278ee);
            if(values[i] > 0) canvas.drawRoundRect(new RectF(x-step*.29f,bottom-Math.max(h,dp(2)),x+step*.29f,bottom),dp(4),dp(4),paint);
            else { paint.setColor(lineColor); canvas.drawCircle(x,bottom-dp(2),dp(2),paint); }
            paint.setColor(textColor); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(dp(9));
            canvas.drawText(MONTHS[i],x,bottom+dp(20),paint);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(event.getAction()==MotionEvent.ACTION_UP) {
            float width=getWidth()-dp(62);
            selected=Math.max(0,Math.min(11,(int)((event.getX()-dp(54))/width*12)));
            if(listener!=null) listener.accept(selected);
            announceForAccessibility(MONTHS[selected]+": "+Domain.money(values[selected]));
            invalidate(); performClick(); return true;
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
