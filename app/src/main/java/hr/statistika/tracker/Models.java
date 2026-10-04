package hr.statistika.tracker;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Models {
    private Models() {}
    public enum ProjectStatus { TODO, IN_PROGRESS, DONE, ARCHIVED }
    public enum PaymentType { DEPOSIT, PARTIAL, FINAL, OTHER }
    public enum PaymentStatus { UNKNOWN, NOT_PAID, PARTIALLY_PAID, PAID }

    public static class Client {
        public String id = UUID.randomUUID().toString();
        public String firstName = "", lastName = "", email = "", phone = "", institution = "", notes = "";
        public String createdAt = Domain.timestamp(), updatedAt = createdAt;
        public String name() { return firstName + " " + lastName; }
    }
    public static class Project {
        public String id = UUID.randomUUID().toString();
        public String clientId = "", title = "", createdDate = Domain.today().toString();
        public String deadline = Domain.today().toString(), notes = "", completedDate;
        public Long price;
        public ProjectStatus status = ProjectStatus.TODO;
        public String createdAt = Domain.timestamp(), updatedAt = createdAt, deletedAt;
    }
    public static class Payment {
        public String id = UUID.randomUUID().toString();
        public String projectId = "", paymentDate = Domain.today().toString(), notes = "";
        public long amount;
        public PaymentType type = PaymentType.DEPOSIT;
        public String createdAt = Domain.timestamp(), updatedAt = createdAt;
    }
    public static class Settings {
        public String currency = "EUR", language = "hr";
        public boolean darkMode = false;
    }
    public static class Data {
        public final List<Client> clients = new ArrayList<>();
        public final List<Project> projects = new ArrayList<>();
        public final List<Payment> payments = new ArrayList<>();
        public Settings settings = new Settings();
        public Client client(String id) {
            for (Client c : clients) if (c.id.equals(id)) return c;
            return null;
        }
        public Project project(String id) {
            for (Project p : projects) if (p.id.equals(id)) return p;
            return null;
        }
        public List<Project> visibleProjects() {
            List<Project> result = new ArrayList<>();
            for (Project p : projects) if (p.deletedAt == null) result.add(p);
            return result;
        }
        public List<Payment> paymentsFor(String projectId) {
            List<Payment> result = new ArrayList<>();
            for (Payment p : payments) if (p.projectId.equals(projectId)) result.add(p);
            result.sort((a,b) -> a.paymentDate.compareTo(b.paymentDate));
            return result;
        }
    }
}
