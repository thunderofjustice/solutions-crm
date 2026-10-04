package hr.statistika.tracker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import static hr.statistika.tracker.Models.*;

public final class Domain {
    public static final long MAX_AMOUNT = 100_000_000_000L;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu.").withResolverStyle(ResolverStyle.STRICT);
    private Domain() {}
    public static LocalDate today() { return LocalDate.now(); }
    public static String timestamp() { return Instant.now().toString(); }
    public static String date(String iso) { return iso == null ? "—" : LocalDate.parse(iso).format(DATE); }
    public static String parseDate(String value) {
        String s = value.trim();
        if (!s.endsWith(".")) s += ".";
        try { return LocalDate.parse(s, DATE).toString(); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Unesite datum kao DD.MM.GGGG."); }
    }
    public static long parseMoney(String input) {
        String s = input.trim().replace("€", "").replace(" ", "").replace("\u00a0", "");
        if (s.contains(",")) s = s.replace(".", "").replace(',', '.');
        if (!s.matches("[0-9]+(\\.[0-9]{1,2})?")) throw new IllegalArgumentException("Unesite iznos s najviše dvije decimale.");
        try {
            long cents = new BigDecimal(s).movePointRight(2).longValueExact();
            if (cents > MAX_AMOUNT) throw new IllegalArgumentException("Iznos je prevelik.");
            return cents;
        } catch (ArithmeticException e) { throw new IllegalArgumentException("Iznos je prevelik."); }
    }
    public static String money(long cents) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.forLanguageTag("hr-HR"));
        f.setMinimumFractionDigits(cents % 100 == 0 ? 0 : 2);
        f.setMaximumFractionDigits(2);
        return f.format(BigDecimal.valueOf(cents, 2)) + " €";
    }
    public static String inputMoney(Long cents) { return cents == null ? "" : BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ','); }
    public static long paid(Data data, Project project) {
        long sum = 0;
        for (Payment p : data.payments) if (p.projectId.equals(project.id)) sum = Math.addExact(sum, p.amount);
        return sum;
    }
    public static long remaining(Data data, Project p) { return p.price == null ? 0 : Math.max(p.price - paid(data,p),0); }
    public static long overpayment(Data data, Project p) { return p.price == null ? 0 : Math.max(paid(data,p) - p.price,0); }
    public static PaymentStatus paymentStatus(Data data, Project p) {
        if (p.price == null) return PaymentStatus.UNKNOWN;
        long paid = paid(data,p);
        if (paid >= p.price) return PaymentStatus.PAID;
        return paid == 0 ? PaymentStatus.NOT_PAID : PaymentStatus.PARTIALLY_PAID;
    }
    public static boolean active(Project p) { return p.deletedAt == null && (p.status == ProjectStatus.TODO || p.status == ProjectStatus.IN_PROGRESS); }
    public static long days(Project p, LocalDate today) { return ChronoUnit.DAYS.between(today, LocalDate.parse(p.deadline)); }
    public static boolean overdue(Project p, LocalDate today) { return active(p) && days(p,today) < 0; }
    public static String deadlineLabel(Project p, LocalDate today) {
        long n = days(p,today);
        if (n < 0) return "Kasni " + -n + " " + dayWord(-n);
        if (n == 0) return "Danas";
        if (n == 1) return "Sutra";
        return "Za " + n + " " + dayWord(n);
    }
    private static String dayWord(long n) { return n % 10 == 1 && n % 100 != 11 ? "dan" : "dana"; }
    public static void changeStatus(Project p, ProjectStatus next, LocalDate today) {
        if (next == ProjectStatus.DONE && p.completedDate == null) p.completedDate = today.toString();
        if (next == ProjectStatus.TODO || next == ProjectStatus.IN_PROGRESS) p.completedDate = null;
        p.status = next;
        p.updatedAt = timestamp();
    }
    public static PaymentType suggestedType(Data data, Project p, long amount) {
        if (p.price != null && amount >= remaining(data,p)) return PaymentType.FINAL;
        return data.paymentsFor(p.id).isEmpty() ? PaymentType.DEPOSIT : PaymentType.PARTIAL;
    }
    public static long outstanding(Data data) {
        long sum = 0;
        for (Project p : data.visibleProjects()) sum = Math.addExact(sum,remaining(data,p));
        return sum;
    }
    public static long[] monthlyRevenue(Data data, int year) {
        long[] months = new long[12];
        for (Payment p : data.payments) {
            Project project = data.project(p.projectId);
            if (project == null || project.deletedAt != null) continue;
            LocalDate date = LocalDate.parse(p.paymentDate);
            if (date.getYear() == year) months[date.getMonthValue()-1] = Math.addExact(months[date.getMonthValue()-1], p.amount);
        }
        return months;
    }
    public static String title(Data data, Project p) {
        Client c = data.client(p.clientId);
        return p.title.trim().isEmpty() ? (c == null ? "Projekt" : c.name()) + " – " + date(p.createdDate) : p.title;
    }
    public static String normalize(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('đ','d');
    }
    public static boolean matches(Data data, Project p, String search) {
        Client c = data.client(p.clientId);
        String haystack = title(data,p) + " " + (c == null ? "" : c.name() + " " + c.institution);
        return normalize(haystack).contains(normalize(search.trim()));
    }
    public static long averagePrice(Data data, int year) {
        long sum = 0, count = 0;
        for (Project p : data.visibleProjects()) if (LocalDate.parse(p.createdDate).getYear() == year && p.price != null) { sum += p.price; count++; }
        return count == 0 ? 0 : BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count),0, RoundingMode.HALF_UP).longValue();
    }
}
