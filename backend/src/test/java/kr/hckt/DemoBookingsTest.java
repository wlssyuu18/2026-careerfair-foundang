package kr.hckt;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class DemoBookingsTest {
    @Test void syntheticRegionsCoverAllBundledProvinces() throws Exception {
        try (var input = getClass().getResourceAsStream("/catalog/demo-regions.json")) {
            var regions = new ObjectMapper().readTree(input);
            assertEquals(301, regions.size());
            Set<String> provinces = new HashSet<>(), keys = new HashSet<>();
            for (var region : regions) {
                provinces.add(region.path("sido").asText());
                assertTrue(keys.add(region.toString()));
            }
            assertEquals(16, provinces.size());
        }
    }
    @Test @EnabledIfEnvironmentVariable(named="HCKT_TEST_DB", matches="true")
    void inventoryApprovalCancellationAndRestartUsePersistentData() throws Exception {
        var config = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("../.env"), StandardCharsets.UTF_8)) { config.load(reader); }
        try (var connection = DriverManager.getConnection(config.getProperty("DB_URL", "jdbc:postgresql://127.0.0.1:5432/hckt"), config.getProperty("DB_USER", "hckt"), config.getProperty("DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            try {
                var db = JdbcClient.create(new SingleConnectionDataSource(connection, true));
                var json = new ObjectMapper(); var store = new TaskStore(db, json);
                var demo = new DemoBookings(db, store, new CatalogSearch(db), json);
                demo.seed();
                assertEquals(903, db.sql("SELECT count(*) FROM resource_listing WHERE source_code='demo'").query(Integer.class).single());
                var user = UUID.randomUUID();
                db.sql("INSERT INTO app_user(id,email,password_hash,name,birth_date,budget) VALUES(?,?,?,'Test','2000-01-01',10000)").params(user,user+"@example.com","test-hash").update();
                var conditions = new Task.Conditions("화상면접","2099-11-27","13:30","15:30","부산광역시 사하구 다대동",10000,List.of("웹캠","마이크"),List.of(),List.of());
                var task = demo.draft(user, conditions); demo.search(user, task, conditions);
                assertFalse(task.candidates.isEmpty());
                assertTrue(task.candidates.stream().allMatch(c -> c.price() != null && c.url().startsWith("https://example.com/spaces/")));
                assertTrue(task.candidates.stream().allMatch(c -> "READY".equals(task.bookingChecks.get(c.id()).status())));
                String room = task.candidates.getFirst().id(); demo.select(user, task, room);
                assertThrows(RuntimeException.class, () -> demo.approve(user, task, "wrong"));
                demo.approve(user, task, task.approval.fingerprint());
                assertEquals("PENDING", store.get(user, task.id).state);
                assertTrue(db.sql("SELECT approve_at BETWEEN now()+interval '4 seconds' AND now()+interval '121 seconds' FROM demo_booking WHERE task_id=?").param(task.id).query(Boolean.class).single());
                assertFalse(new TaskStore(db, json).get(user, task.id).bookingChecks.isEmpty());
                assertFalse(demo.available(room, conditions));
                assertThrows(RuntimeException.class, () -> demo.approve(user, task, task.approval.fingerprint()));
                var adjacent = new Task.Conditions("화상면접",conditions.date(),"15:30","16:30",conditions.region(),10000,List.of(),List.of(),List.of());
                assertTrue(demo.available(room, adjacent));
                db.sql("UPDATE demo_booking SET approve_at=now()-interval '1 second' WHERE task_id=?").param(task.id).update();
                demo.confirmDue();
                assertEquals("CONFIRMED", store.get(user, task.id).state);
                demo.cancel(user, task);
                assertEquals("STOPPED", store.get(user, task.id).state);
                assertTrue(demo.available(room, conditions));
                demo.confirmDue();
                assertEquals("STOPPED", store.get(user, task.id).state);
                db.sql("INSERT INTO demo_space_block(listing_id,starts_at,ends_at) VALUES(?,?,?)").params(UUID.fromString(room), DemoBookings.start(conditions), DemoBookings.end(conditions)).update();
                assertFalse(demo.available(room, conditions));
            } finally { connection.rollback(); }
        }
    }
}
