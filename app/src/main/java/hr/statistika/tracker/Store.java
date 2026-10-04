package hr.statistika.tracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONObject;
import static hr.statistika.tracker.Models.*;

/** Internal, offline storage. A snapshot is committed as one SQLite transaction. */
public final class Store extends SQLiteOpenHelper {
    public Store(Context context) { this(context,"tracker.db"); }
    Store(Context context,String name) { super(context,name,null,1); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE clients (id TEXT PRIMARY KEY, payload TEXT NOT NULL)");
        db.execSQL("CREATE TABLE projects (id TEXT PRIMARY KEY, clientId TEXT NOT NULL REFERENCES clients(id), payload TEXT NOT NULL)");
        db.execSQL("CREATE TABLE payments (id TEXT PRIMARY KEY, projectId TEXT NOT NULL REFERENCES projects(id), payload TEXT NOT NULL)");
        db.execSQL("CREATE INDEX project_client ON projects(clientId)");
        db.execSQL("CREATE INDEX payment_project ON payments(projectId)");
        db.execSQL("CREATE TABLE settings (id INTEGER PRIMARY KEY, payload TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { throw new IllegalStateException("Database migration required"); }
    public Data load() {
        Data data = new Data();
        SQLiteDatabase db = getReadableDatabase();
        try {
            try (Cursor c = db.rawQuery("SELECT payload FROM clients",null)) { while(c.moveToNext()) data.clients.add(Backup.readClient(new JSONObject(c.getString(0)))); }
            try (Cursor c = db.rawQuery("SELECT payload FROM projects",null)) { while(c.moveToNext()) data.projects.add(Backup.readProject(new JSONObject(c.getString(0)))); }
            try (Cursor c = db.rawQuery("SELECT payload FROM payments",null)) { while(c.moveToNext()) data.payments.add(Backup.readPayment(new JSONObject(c.getString(0)))); }
            try (Cursor c = db.rawQuery("SELECT payload FROM settings WHERE id=1",null)) { if(c.moveToFirst()) data.settings = Backup.readSettings(new JSONObject(c.getString(0))); }
            return data;
        } catch (Exception e) { throw new IllegalStateException("Podaci nisu mogli biti učitani. Izvorna baza je sačuvana.",e); }
    }
    public void save(Data data) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("payments",null,null); db.delete("projects",null,null); db.delete("clients",null,null); db.delete("settings",null,null);
            for (Client c : data.clients) insert(db,"clients",c.id,null,null,Backup.clientJson(c).toString());
            for (Project p : data.projects) insert(db,"projects",p.id,"clientId",p.clientId,Backup.projectJson(p).toString());
            for (Payment p : data.payments) insert(db,"payments",p.id,"projectId",p.projectId,Backup.paymentJson(p).toString());
            ContentValues values = new ContentValues(); values.put("id",1); values.put("payload",Backup.settingsJson(data.settings).toString());
            db.insertOrThrow("settings",null,values);
            db.setTransactionSuccessful();
        } catch (Exception e) { throw new IllegalStateException("Promjene nisu spremljene.",e); }
        finally { db.endTransaction(); }
    }
    private void insert(SQLiteDatabase db,String table,String id,String fk,String related,String payload) {
        ContentValues values = new ContentValues(); values.put("id",id); values.put("payload",payload);
        if (fk != null) values.put(fk,related);
        db.insertOrThrow(table,null,values);
    }
}
