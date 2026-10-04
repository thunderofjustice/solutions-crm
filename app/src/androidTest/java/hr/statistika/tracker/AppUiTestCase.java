package hr.statistika.tracker;

import android.app.Activity;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicReference;
import static hr.statistika.tracker.Models.*;

/** UI harness that restores the pre-test snapshot and reports UI failures on the test thread. */
public abstract class AppUiTestCase extends InstrumentationTestCase {
    protected Activity activity;
    protected Store store;
    private Data before;
    @Override protected void setUp() throws Exception {
        super.setUp();store=new Store(getInstrumentation().getTargetContext());before=store.load();store.save(new Data());launch();
    }
    protected void launch() {
        Intent intent=new Intent(getInstrumentation().getTargetContext(),MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity=getInstrumentation().startActivitySync(intent);getInstrumentation().waitForIdleSync();
    }
    @Override protected void tearDown() throws Exception {
        if(activity!=null)onUi(()->activity.finish());getInstrumentation().waitForIdleSync();store.save(before);store.close();super.tearDown();
    }
    protected void onUi(Runnable action) {
        AtomicReference<Throwable> failure=new AtomicReference<>();
        getInstrumentation().runOnMainSync(()->{try{action.run();}catch(Throwable e){failure.set(e);}});
        if(failure.get()!=null)throw new AssertionError("UI action failed",failure.get());
    }
    protected void input(String label,String value){onUi(()->{View v=findInput(activity.getWindow().getDecorView(),label);assertNotNull(label,v);((EditText)v).setText(value);});getInstrumentation().waitForIdleSync();}
    protected void click(String label){onUi(()->{View v=find(activity.getWindow().getDecorView(),label);assertNotNull(label,v);while(v!=null&&!v.isClickable()){if(v.getParent() instanceof View)v=(View)v.getParent();else break;}assertNotNull(v);v.performClick();});getInstrumentation().waitForIdleSync();}
    protected View find(View view,String label){if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof TextView&&label.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
    protected View findInput(View view,String label){if(view instanceof EditText&&label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findInput(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
    protected void capture(String name) throws Exception {Thread.sleep(200);android.graphics.Bitmap image=getInstrumentation().getUiAutomation().takeScreenshot();java.io.File directory=new java.io.File(getInstrumentation().getTargetContext().getExternalFilesDir(null),"previews");directory.mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(directory,name+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    protected void dialogClick(String label) throws Exception {
        long end=System.currentTimeMillis()+8000;
        while(System.currentTimeMillis()<end) {
            android.view.accessibility.AccessibilityNodeInfo root=getInstrumentation().getUiAutomation().getRootInActiveWindow();
            android.view.accessibility.AccessibilityNodeInfo node=findDialogNode(root,label);
            if(node!=null&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)){getInstrumentation().waitForIdleSync();Thread.sleep(200);return;}
            Thread.sleep(150);
        }
        fail("Dialog action unavailable: "+label);
    }
    private android.view.accessibility.AccessibilityNodeInfo findDialogNode(android.view.accessibility.AccessibilityNodeInfo node,String label) {
        if(node==null)return null;
        if(node.getText()!=null&&label.equalsIgnoreCase(node.getText().toString()))return node;
        for(int i=0;i<node.getChildCount();i++){android.view.accessibility.AccessibilityNodeInfo found=findDialogNode(node.getChild(i),label);if(found!=null)return found;}
        return null;
    }
    protected void recreateActivity() {
        android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
        onUi(()->activity.recreate());Activity replacement=monitor.waitForActivityWithTimeout(8000);getInstrumentation().removeMonitor(monitor);
        assertNotNull("Activity did not recreate",replacement);activity=replacement;getInstrumentation().waitForIdleSync();
    }
}
