package hr.statistika.tracker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import static hr.statistika.tracker.Models.*;

public final class Backup {
    private Backup() {}
    public static JSONObject clientJson(Client c) throws JSONException {
        return new JSONObject().put("id",c.id).put("firstName",c.firstName).put("lastName",c.lastName)
            .put("email",c.email).put("phone",c.phone).put("institution",c.institution).put("notes",c.notes)
            .put("createdAt",c.createdAt).put("updatedAt",c.updatedAt);
    }
    public static JSONObject projectJson(Project p) throws JSONException {
        return new JSONObject().put("id",p.id).put("clientId",p.clientId).put("title",p.title)
            .put("createdDate",p.createdDate).put("deadline",p.deadline).put("priceCents",p.price == null ? JSONObject.NULL : p.price)
            .put("status",p.status.name()).put("notes",p.notes).put("completedDate",p.completedDate == null ? JSONObject.NULL : p.completedDate)
            .put("deletedAt",p.deletedAt == null ? JSONObject.NULL : p.deletedAt).put("createdAt",p.createdAt).put("updatedAt",p.updatedAt);
    }
    public static JSONObject paymentJson(Payment p) throws JSONException {
        return new JSONObject().put("id",p.id).put("projectId",p.projectId).put("amountCents",p.amount)
            .put("paymentDate",p.paymentDate).put("paymentType",p.type.name()).put("notes",p.notes)
            .put("createdAt",p.createdAt).put("updatedAt",p.updatedAt);
    }
    public static JSONObject settingsJson(Settings s) throws JSONException {
        return new JSONObject().put("currency",s.currency).put("language",s.language).put("darkMode",s.darkMode);
    }
    public static String encode(Data data) throws JSONException {
        JSONArray clients = new JSONArray(), projects = new JSONArray(), payments = new JSONArray();
        for (Client c : data.clients) clients.put(clientJson(c));
        for (Project p : data.projects) projects.put(projectJson(p));
        for (Payment p : data.payments) payments.put(paymentJson(p));
        return new JSONObject().put("format","project-payment-tracker").put("version",1).put("exportedAt",Domain.timestamp())
            .put("clients",clients).put("projects",projects).put("payments",payments).put("settings",settingsJson(data.settings)).toString(2);
    }
    public static Client readClient(JSONObject o) throws JSONException {
        Client c = new Client();
        c.id = required(o,"id"); c.firstName = required(o,"firstName"); c.lastName = required(o,"lastName");
        c.email = text(o,"email"); c.phone = text(o,"phone"); c.institution = text(o,"institution"); c.notes = text(o,"notes");
        c.createdAt = instant(o,"createdAt"); c.updatedAt = instant(o,"updatedAt");
        return c;
    }
    public static Project readProject(JSONObject o) throws JSONException {
        Project p = new Project();
        p.id = required(o,"id"); p.clientId = required(o,"clientId"); p.title = text(o,"title");
        p.createdDate = day(o,"createdDate"); p.deadline = day(o,"deadline");
        p.price = o.isNull("priceCents") ? null : amount(o,"priceCents",false);
        p.status = ProjectStatus.valueOf(required(o,"status")); p.notes = text(o,"notes");
        p.completedDate = o.isNull("completedDate") ? null : day(o,"completedDate");
        p.deletedAt = o.isNull("deletedAt") ? null : instant(o,"deletedAt");
        p.createdAt = instant(o,"createdAt"); p.updatedAt = instant(o,"updatedAt");
        return p;
    }
    public static Payment readPayment(JSONObject o) throws JSONException {
        Payment p = new Payment();
        p.id = required(o,"id"); p.projectId = required(o,"projectId"); p.amount = amount(o,"amountCents",true);
        p.paymentDate = day(o,"paymentDate"); p.type = PaymentType.valueOf(required(o,"paymentType")); p.notes = text(o,"notes");
        p.createdAt = instant(o,"createdAt"); p.updatedAt = instant(o,"updatedAt");
        return p;
    }
    public static Settings readSettings(JSONObject o) throws JSONException {
        Settings s = new Settings(); s.currency = required(o,"currency"); s.language = required(o,"language"); s.darkMode = o.getBoolean("darkMode");
        if (!s.currency.equals("EUR") || !s.language.equals("hr")) throw new IllegalArgumentException("Nepodržana valuta ili jezik sigurnosne kopije.");
        return s;
    }
    public static Data decode(String source) throws JSONException {
        JSONObject root = new JSONObject(source);
        if (!root.getString("format").equals("project-payment-tracker") || root.getInt("version") != 1)
            throw new IllegalArgumentException("Nepodržan format sigurnosne kopije.");
        Data data = new Data();
        JSONArray clients = root.getJSONArray("clients"), projects = root.getJSONArray("projects"), payments = root.getJSONArray("payments");
        if (clients.length()+projects.length()+payments.length() > 100000) throw new IllegalArgumentException("Sigurnosna kopija sadrži previše zapisa.");
        Set<String> clientIds = new HashSet<>(), projectIds = new HashSet<>(), paymentIds = new HashSet<>();
        for (int i=0;i<clients.length();i++) {
            Client c = readClient(clients.getJSONObject(i));
            if (!clientIds.add(c.id)) throw new IllegalArgumentException("Ponovljen ID klijenta.");
            data.clients.add(c);
        }
        for (int i=0;i<projects.length();i++) {
            Project p = readProject(projects.getJSONObject(i));
            if (!projectIds.add(p.id) || !clientIds.contains(p.clientId)) throw new IllegalArgumentException("Neispravna povezanost projekta i klijenta.");
            data.projects.add(p);
        }
        for (int i=0;i<payments.length();i++) {
            Payment p = readPayment(payments.getJSONObject(i));
            if (!paymentIds.add(p.id) || !projectIds.contains(p.projectId)) throw new IllegalArgumentException("Neispravna povezanost uplate i projekta.");
            data.payments.add(p);
        }
        data.settings = readSettings(root.getJSONObject("settings"));
        // Detect overflow before a valid-looking backup can replace existing records.
        for (Project p : data.projects) Domain.paid(data,p);
        Domain.outstanding(data);
        return data;
    }
    private static String text(JSONObject o, String key) throws JSONException {
        Object value = o.get(key);
        if (!(value instanceof String) || ((String)value).length() > 100000) throw new IllegalArgumentException("Neispravno tekstualno polje: " + key);
        return (String)value;
    }
    private static String required(JSONObject o, String key) throws JSONException {
        String value = text(o,key);
        if (value.trim().isEmpty()) throw new IllegalArgumentException("Nedostaje obavezno polje: " + key);
        return value;
    }
    private static String day(JSONObject o, String key) throws JSONException {
        String value = required(o,key);
        try {
            if (!LocalDate.parse(value).toString().equals(value)) throw new IllegalArgumentException("Neispravan datum.");
        } catch (java.time.DateTimeException e) { throw new IllegalArgumentException("Neispravan datum: " + key,e); }
        return value;
    }
    private static String instant(JSONObject o, String key) throws JSONException { String value = required(o,key); Instant.parse(value); return value; }
    private static long amount(JSONObject o, String key, boolean positive) throws JSONException {
        Object value = o.get(key);
        if (!(value instanceof Number)) throw new IllegalArgumentException("Neispravan iznos.");
        long cents;
        try { cents = new BigDecimal(value.toString()).longValueExact(); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Iznos mora biti cijeli broj centi.",e); }
        if (cents < (positive ? 1 : 0) || cents > Domain.MAX_AMOUNT) throw new IllegalArgumentException("Neispravan iznos.");
        return cents;
    }
}
