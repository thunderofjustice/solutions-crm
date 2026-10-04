package hr.statistika.tracker;

import android.test.InstrumentationTestCase;
import android.content.Context;
import org.json.JSONObject;
import static hr.statistika.tracker.Models.*;

public class StorageTest extends InstrumentationTestCase {
    private Context context;
    private Store store;
    @Override protected void setUp() throws Exception {super.setUp();context=getInstrumentation().getTargetContext();context.deleteDatabase("storage-test.db");store=new Store(context,"storage-test.db");}
    @Override protected void tearDown() throws Exception {store.close();context.deleteDatabase("storage-test.db");super.tearDown();}
    private Data fixture() {Data data=new Data();Client c=new Client();c.firstName="Ana";c.lastName="Horvat";c.institution="Medicinski fakultet";data.clients.add(c);Project p=new Project();p.clientId=c.id;p.title="Analiza ankete";p.price=30000L;p.deadline="2026-10-15";p.notes="Prvi redak\nDrugi redak · čćšđž";data.projects.add(p);Payment payment=new Payment();payment.projectId=p.id;payment.amount=10000;payment.paymentDate="2026-09-30";data.payments.add(payment);data.settings.darkMode=true;return data;}
    public void testBackupRoundTripPreservesRelationshipsAndCents() throws Exception {Data data=fixture();Data restored=Backup.decode(Backup.encode(data));assertEquals(data.clients.get(0).id,restored.projects.get(0).clientId);assertEquals(data.projects.get(0).id,restored.payments.get(0).projectId);assertEquals(Long.valueOf(30000),restored.projects.get(0).price);assertEquals(data.projects.get(0).notes,restored.projects.get(0).notes);assertTrue(restored.settings.darkMode);assertEquals(10000,Domain.monthlyRevenue(restored,2026)[8]);}
    public void testStoreSurvivesClosingAndReopening() {Data data=fixture();store.save(data);store.close();store=new Store(context,"storage-test.db");Data loaded=store.load();assertEquals(1,loaded.clients.size());assertEquals(1,loaded.projects.size());assertEquals(1,loaded.payments.size());assertEquals(20000,Domain.outstanding(loaded));assertTrue(loaded.settings.darkMode);}
    public void testFailedTransactionPreservesOriginalData() {Data original=fixture();store.save(original);Data invalid=fixture();invalid.projects.get(0).clientId="missing-client";try{store.save(invalid);fail("Foreign key must fail");}catch(IllegalStateException expected){}Data loaded=store.load();assertEquals(original.projects.get(0).id,loaded.projects.get(0).id);assertEquals(10000,Domain.paid(loaded,loaded.projects.get(0)));}
    public void testBackupRejectsOrphanPaymentsAndDuplicateIds() throws Exception {Data data=fixture();JSONObject json=new JSONObject(Backup.encode(data));json.getJSONArray("payments").getJSONObject(0).put("projectId","missing-project");reject(json);json=new JSONObject(Backup.encode(data));json.getJSONArray("clients").put(json.getJSONArray("clients").getJSONObject(0));reject(json);}
    public void testBackupRejectsNegativeAndFractionalCentsAndInvalidDates() throws Exception {Data data=fixture();for(Object amount:new Object[]{-1,0,0.5}){JSONObject json=new JSONObject(Backup.encode(data));json.getJSONArray("payments").getJSONObject(0).put("amountCents",amount);reject(json);}JSONObject json=new JSONObject(Backup.encode(data));json.getJSONArray("projects").getJSONObject(0).put("deadline","2026-02-30");reject(json);}
    public void testSoftDeletedRecordsSurviveBackupWithoutAffectingAnalytics() throws Exception {Data data=fixture();data.projects.get(0).deletedAt=Domain.timestamp();Data restored=Backup.decode(Backup.encode(data));assertEquals(1,restored.projects.size());assertEquals(1,restored.payments.size());assertEquals(0,restored.visibleProjects().size());assertEquals(0,Domain.outstanding(restored));assertEquals(0,Domain.monthlyRevenue(restored,2026)[8]);}
    private void reject(JSONObject json) throws Exception {try{Backup.decode(json.toString());fail("Invalid backup accepted");}catch(IllegalArgumentException expected){}}
}
