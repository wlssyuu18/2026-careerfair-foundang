package kr.hckt;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Task {
    public record Conditions(String purpose, String date, String start, String end, String region, Integer budget, List<String> equipment, List<String> ownedEquipment, List<String> notes) {
        public Conditions {
            purpose = purpose == null ? "" : purpose; date = date == null ? "" : date;
            start = start == null ? "" : start; end = end == null ? "" : end; region = region == null ? "" : region;
            equipment = equipment == null ? List.of() : List.copyOf(equipment); notes = notes == null ? List.of() : List.copyOf(notes);
            ownedEquipment = ownedEquipment == null ? List.of() : List.copyOf(ownedEquipment);
        }
    }
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"availability", "remainingQuantity", "remaining_quantity"})
    public record Candidate(String id, String name, String provider, String url, String address, Integer price, List<String> equipment, String eligibility, String evidence, Instant observedAt) {}
    public record Approval(String candidateId, String name, String url, String date, String start, String end, int totalPrice, String cancellation, String evidence, String fingerprint) {}
    public record Event(Instant at, String message) {}
    public record Availability(String status, String evidence, Instant checkedAt, String date, String start, String end) {}
    public record BookingCheck(String status, String message, String url, Integer totalPrice, String cancellation) {}
    public UUID id;
    public String state;
    public Conditions conditions;
    public List<Candidate> candidates = new ArrayList<>();
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public java.util.Map<String, Availability> availability = new java.util.concurrent.ConcurrentHashMap<>();
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public java.util.Map<String, BookingCheck> bookingChecks = new java.util.concurrent.ConcurrentHashMap<>();
    public String selectedId;
    public Approval approval;
    public String question;
    public String message;
    public String bookingReference;
    public boolean simulated;
    public String evidence;
    public Instant submittedAt;
    public Instant updatedAt;
    public List<Event> events = new ArrayList<>();

    public void state(String value, String message) {
        this.state = value; this.message = Rules.redact(message); this.updatedAt = Instant.now();
        events.add(new Event(updatedAt, this.message));
        if (events.size() > 60) events.removeFirst();
    }
    public static boolean running(String state) { return List.of("ANALYZING", "SEARCHING", "PREPARING", "SUBMITTING", "VERIFYING").contains(state); }
}
