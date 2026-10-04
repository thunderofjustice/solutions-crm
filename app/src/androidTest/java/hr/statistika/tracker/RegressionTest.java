package hr.statistika.tracker;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static hr.statistika.tracker.Models.*;

public class RegressionTest extends AppUiTestCase {
    private void createProject() {click("+  Novi projekt");input("Ime *","Luka");input("Prezime *","Test");input("Naziv projekta","Test analize");input("Ukupna cijena (€)","300");click("Spremi projekt");}
    public void testDraftSurvivesActivityRecreation() {
        click("+  Novi projekt");input("Ime *","Luka");input("Prezime *","Test");input("Naziv projekta","Nespremljeni nacrt");input("Ukupna cijena (€)","350,50");input("Napomena","Prvi redak\nDrugi redak");
        onUi(()->((Switch)find(activity.getWindow().getDecorView(),"Polog uplaćen?")).setChecked(true));input("Iznos pologa (€) *","100,25");recreateActivity();click("Spremi projekt");
        Data data=store.load();assertEquals(1,data.projects.size());assertEquals("Nespremljeni nacrt",data.projects.get(0).title);assertEquals(Long.valueOf(35050),data.projects.get(0).price);assertEquals(10025,data.payments.get(0).amount);assertEquals("Prvi redak\nDrugi redak",data.projects.get(0).notes);
    }
    public void testOverpaymentAndArchivedDebtStayCorrect() {
        createProject();click("+  Uplata");input("Iznos uplate (€) *","320");click("Spremi uplatu");Data data=store.load();assertEquals(2000,Domain.overpayment(data,data.projects.get(0)));assertNotNull(find(activity.getWindow().getDecorView(),"+20 €"));
        click("Uredi projekt");input("Ukupna cijena (€)","400");onUi(()->((Spinner)findByDescription(activity.getWindow().getDecorView(),"Status projekta")).setSelection(ProjectStatus.ARCHIVED.ordinal()));click("Spremi projekt");data=store.load();assertEquals(8000,Domain.outstanding(data));assertEquals(ProjectStatus.ARCHIVED,data.projects.get(0).status);assertEquals(PaymentStatus.PARTIALLY_PAID,Domain.paymentStatus(data,data.projects.get(0)));
        click("Natrag");assertNull(find(activity.getWindow().getDecorView(),"Luka Test"));click("Projekti");click("Arhivirano");assertNotNull(find(activity.getWindow().getDecorView(),"Luka Test"));
    }
    public void testDeleteConfirmationAndRestorePreservePayments() throws Exception {
        createProject();click("Označi plaćeno");click("Obriši projekt");dialogClick("Odustani");assertEquals(1,store.load().visibleProjects().size());click("Obriši projekt");dialogClick("Obriši");Data data=store.load();assertEquals(0,data.visibleProjects().size());assertEquals(1,data.payments.size());assertEquals(0,Domain.monthlyRevenue(data,Domain.today().getYear())[Domain.today().getMonthValue()-1]);
        click("Postavke");click("Vrati projekt i njegove uplate");data=store.load();assertEquals(1,data.visibleProjects().size());assertEquals(1,data.payments.size());assertEquals(30000,Domain.paid(data,data.projects.get(0)));
    }
    public void testDarkModeAndYearSelectionSurviveRecreation() throws Exception {
        createProject();click("Označi plaćeno");click("Natrag");click("Postavke");click("Tamni način rada");assertTrue(store.load().settings.darkMode);recreateActivity();assertTrue(((Switch)find(activity.getWindow().getDecorView(),"Tamni način rada")).isChecked());capture("settings-dark");click("Analitika");capture("analytics-dark");
        Spinner year=findSpinner(activity.getWindow().getDecorView());assertNotNull(year);onUi(()->year.setSelection(0));getInstrumentation().waitForIdleSync();recreateActivity();assertNotNull(find(activity.getWindow().getDecorView(),"Nema uplata u odabranoj godini."));
    }
    public void testBackupImportCancellationAndReplacement() throws Exception {
        createProject();Data original=store.load();Data replacement=new Data();Client c=new Client();c.firstName="Backup";c.lastName="Klijent";replacement.clients.add(c);
        File file=new File(getInstrumentation().getTargetContext().getCacheDir(),"regression-import.json");Files.write(file.toPath(),Backup.encode(replacement).getBytes(StandardCharsets.UTF_8));
        Intent result=new Intent().setData(Uri.fromFile(file));onUi(()->((MainActivity)activity).onActivityResult(102,Activity.RESULT_OK,result));dialogClick("Odustani");assertEquals(original.projects.get(0).id,store.load().projects.get(0).id);
        onUi(()->((MainActivity)activity).onActivityResult(102,Activity.RESULT_OK,result));dialogClick("Zamijeni podatke");Data loaded=store.load();assertEquals(0,loaded.projects.size());assertEquals("Backup",loaded.clients.get(0).firstName);file.delete();
    }
    public void testBackupAndCsvExportWriteFiles() throws Exception {
        createProject();click("Označi plaćeno");Data original=store.load();java.lang.reflect.Method stage=MainActivity.class.getDeclaredMethod("stageExport",String.class);stage.setAccessible(true);
        File json=new File(getInstrumentation().getTargetContext().getCacheDir(),"regression-export.json");onUi(()->{try{stage.invoke(activity,Backup.encode(original));((MainActivity)activity).onActivityResult(101,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(json)));}catch(Exception e){throw new RuntimeException(e);}});awaitFile(json);Data restored=Backup.decode(new String(Files.readAllBytes(json.toPath()),StandardCharsets.UTF_8));assertEquals(original.projects.get(0).id,restored.projects.get(0).id);assertEquals(30000,Domain.paid(restored,restored.projects.get(0)));
        java.lang.reflect.Method csv=MainActivity.class.getDeclaredMethod("paymentsCsv");csv.setAccessible(true);String payload=(String)csv.invoke(activity);File output=new File(getInstrumentation().getTargetContext().getCacheDir(),"regression-export.csv");onUi(()->{try{stage.invoke(activity,payload);((MainActivity)activity).onActivityResult(103,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(output)));}catch(Exception e){throw new RuntimeException(e);}});awaitFile(output);String saved=new String(Files.readAllBytes(output.toPath()),StandardCharsets.UTF_8);assertTrue(saved.startsWith("\ufeffDatum;Klijent;Projekt;Vrsta uplate;Iznos (€)"));assertTrue(saved.contains("Luka Test"));assertTrue(saved.contains("300,00"));json.delete();output.delete();
    }
    private void awaitFile(File file) throws Exception {long end=System.currentTimeMillis()+5000;while((!file.exists()||file.length()==0)&&System.currentTimeMillis()<end)Thread.sleep(100);Thread.sleep(200);assertTrue("Export not written",file.length()>0);}
    private Spinner findSpinner(View view){if(view instanceof Spinner)return (Spinner)view;if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Spinner found=findSpinner(group.getChildAt(i));if(found!=null)return found;}}return null;}
    private View findByDescription(View view,String name){if(name.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findByDescription(group.getChildAt(i),name);if(found!=null)return found;}}return null;}
}
