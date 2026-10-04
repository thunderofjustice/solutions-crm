package hr.statistika.tracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Small vector icons drawn in device-independent coordinates. */
public final class IconView extends View {
    private final String name;
    private final int color;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    public IconView(Context context,String name,int color) { super(context); this.name=name; this.color=color; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); c.save(); c.scale(getWidth()/24f,getHeight()/24f);
        p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.7f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        switch(name) {
            case "dashboard":
                c.drawRoundRect(new RectF(3,3,10,10),1.5f,1.5f,p); c.drawRoundRect(new RectF(14,3,21,10),1.5f,1.5f,p);
                c.drawRoundRect(new RectF(3,14,10,21),1.5f,1.5f,p); c.drawRoundRect(new RectF(14,14,21,21),1.5f,1.5f,p); break;
            case "projects": c.drawRoundRect(new RectF(3,6,21,21),2,2,p); c.drawRoundRect(new RectF(8,3,16,8),1,1,p); c.drawLine(8,12,16,12,p); c.drawLine(8,16,14,16,p); break;
            case "clients": c.drawCircle(9,7,3,p); c.drawArc(new RectF(2,12,16,25),180,180,false,p); c.drawArc(new RectF(14,4,21,10),270,180,false,p); c.drawArc(new RectF(14,12,23,23),270,90,false,p); break;
            case "analytics": c.drawLine(3,3,3,21,p); c.drawLine(3,21,22,21,p); c.drawLine(8,17,8,12,p); c.drawLine(13,17,13,8,p); c.drawLine(18,17,18,4,p); break;
            case "settings":
                c.drawCircle(12,12,7,p); c.drawCircle(12,12,2.5f,p);
                for(int i=0;i<8;i++) { c.save(); c.rotate(i*45,12,12); c.drawLine(12,2,12,5,p); c.restore(); } break;
            case "search": c.drawCircle(10,10,6,p); c.drawLine(14.5f,14.5f,21,21,p); break;
            case "clock": c.drawCircle(12,12,9,p); c.drawLine(12,7,12,12,p); c.drawLine(12,12,16,14,p); break;
            case "back": c.drawLine(19,12,5,12,p); c.drawLine(5,12,11,6,p); c.drawLine(5,12,11,18,p); break;
            case "plus": c.drawLine(12,4,12,20,p); c.drawLine(4,12,20,12,p); break;
            case "check": Path path=new Path(); path.moveTo(4,12); path.lineTo(9,17); path.lineTo(20,6); c.drawPath(path,p); break;
            case "arrow": c.drawLine(5,12,19,12,p); c.drawLine(19,12,13,6,p); c.drawLine(19,12,13,18,p); break;
            case "wallet": c.drawRoundRect(new RectF(3,5,21,20),2,2,p); c.drawRoundRect(new RectF(14,10,22,16),1,1,p); c.drawCircle(17,13,.5f,p); break;
            case "archive": c.drawRoundRect(new RectF(4,7,20,21),1,1,p); c.drawRect(2,3,22,7,p); c.drawLine(9,12,15,12,p); break;
            default: c.drawCircle(12,12,9,p); c.drawLine(12,7,12,13,p); c.drawCircle(12,17,.5f,p);
        }
        c.restore();
    }
}
