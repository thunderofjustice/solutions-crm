package hr.statistika.tracker;

import android.app.Activity;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import static hr.statistika.tracker.Models.*;

/** Full UI path; preserves and restores the device's previous database. */
public class WorkflowTest extends AppUiTestCase {
    public void testNewProjectDepositStartDoneQuickPayAndAnalytics() throws Exception {
        click("+  Novi projekt");input("Ime *","Ana");input("Prezime *","Horvat");input("Naziv projekta","Diplomski rad");input("Ukupna cijena (€)","300");
        getInstrumentation().runOnMainSync(()->((Switch)find(activity.getWindow().getDecorView(),"Polog uplaćen?")).setChecked(true));
        input("Iznos pologa (€) *","100");click("Spremi projekt");
        Data data=store.load();assertEquals(1,data.clients.size());assertEquals(1,data.projects.size());assertEquals(1,data.payments.size());Project p=data.projects.get(0);assertEquals(ProjectStatus.TODO,p.status);assertEquals(20000,Domain.remaining(data,p));
        capture("project");click("Započni rad");assertEquals(ProjectStatus.IN_PROGRESS,store.load().projects.get(0).status);
        click("Označi završeno");data=store.load();p=data.projects.get(0);assertEquals(ProjectStatus.DONE,p.status);assertEquals(Domain.today().toString(),p.completedDate);assertEquals(PaymentStatus.PARTIALLY_PAID,Domain.paymentStatus(data,p));
        click("Označi plaćeno");data=store.load();p=data.projects.get(0);assertEquals(2,data.payments.size());assertEquals(PaymentStatus.PAID,Domain.paymentStatus(data,p));assertEquals(30000,Domain.monthlyRevenue(data,Domain.today().getYear())[Domain.today().getMonthValue()-1]);
        click("Natrag");capture("dashboard");click("Projekti");capture("projects");click("Klijenti");capture("clients");click("Analitika");assertNotNull(find(activity.getWindow().getDecorView(),"Mjesečna naplata"));capture("analytics");
    }
    public void testUnknownPriceAndEditingPriceAfterPayment() throws Exception {
        click("+  Novi projekt");input("Ime *","Marko");input("Prezime *","Marić");click("Spremi projekt");Data data=store.load();assertNull(data.projects.get(0).price);assertNotNull(find(activity.getWindow().getDecorView(),"Bez cijene"));
        click("+  Uplata");input("Iznos uplate (€) *","50");click("Spremi uplatu");click("Uredi projekt");input("Ukupna cijena (€)","250");click("Spremi projekt");data=store.load();assertEquals(PaymentStatus.PARTIALLY_PAID,Domain.paymentStatus(data,data.projects.get(0)));assertEquals(20000,Domain.remaining(data,data.projects.get(0)));
    }
    public void testExistingClientIsReusedAndEditablePaymentRecalculates() throws Exception {
        click("+  Novi projekt");input("Ime *","Ana");input("Prezime *","Horvat");input("Ukupna cijena (€)","300");click("Spremi projekt");click("Natrag");click("+  Novi projekt");input("Ime *","Ana");input("Prezime *","Horvat");input("Ukupna cijena (€)","500");click("Spremi projekt");assertEquals(1,store.load().clients.size());assertEquals(2,store.load().projects.size());
        click("+  Uplata");input("Iznos uplate (€) *","100");click("Spremi uplatu");click("Polog");input("Iznos uplate (€) *","150");click("Spremi uplatu");Data data=store.load();assertEquals(1,data.payments.size());assertEquals(15000,data.payments.get(0).amount);
    }
}
