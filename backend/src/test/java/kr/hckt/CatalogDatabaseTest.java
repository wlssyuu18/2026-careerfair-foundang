package kr.hckt;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "HCKT_TEST_DB", matches = "true")
class CatalogDatabaseTest {
    @Test void catalogueAndImportConstraintsWorkOnPostgres() throws Exception {
        var config = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("../.env"), StandardCharsets.UTF_8)) { config.load(reader); }
        try (var db = DriverManager.getConnection(config.getProperty("DB_URL", "jdbc:postgresql://127.0.0.1:5432/hckt"), config.getProperty("DB_USER", "hckt"), config.getProperty("DB_PASSWORD"))) {
            db.setAutoCommit(false);
            try {
                String schema;
                try (var resource = getClass().getResourceAsStream("/schema.sql")) { schema = new String(resource.readAllBytes(), StandardCharsets.UTF_8); }
                String userId = UUID.randomUUID().toString(), taskId = UUID.randomUUID().toString();
                execute(db, "INSERT INTO app_user(id,email,password_hash,name,birth_date,budget) VALUES ('%s','%s@example.com','test-hash','Test','2000-01-01',10000)".formatted(userId, userId));
                execute(db, """
                    INSERT INTO agent_task(id,user_id,data) VALUES ('%s','%s',
                    '{"state":"CONFIRMED","bookingReference":"TEST-1234","approval":{"fingerprint":"approved"},"candidates":[{"id":"first","availability":"AVAILABLE","remainingQuantity":2,"remaining_quantity":2},{"id":"second","name":"Keep room"}]}'::jsonb)
                    """.formatted(taskId, userId));
                populate(db); populate(db);
                assertNull(scalar(db, "SELECT to_regclass('public.resource_availability')"));
                assertNull(scalar(db, "SELECT to_regclass('public.current_resource_availability')"));
                assertEquals("TEST-1234:approved:first:second", scalar(db, "SELECT concat_ws(':',data->>'bookingReference',data#>>'{approval,fingerprint}',data#>>'{candidates,0,id}',data#>>'{candidates,1,id}') FROM agent_task WHERE id='%s'".formatted(taskId)));
                assertEquals("f", scalar(db, "SELECT (data#>'{candidates,0}') ?| ARRAY['availability','remainingQuantity','remaining_quantity'] FROM agent_task WHERE id='%s'".formatted(taskId)));
                execute(db, "CREATE TABLE resource_availability (test_value INTEGER)");
                execute(db, "INSERT INTO resource_availability VALUES (1)");
                reject(db, schema);
                assertEquals("1", scalar(db, "SELECT count(*) FROM resource_availability"));
                execute(db, "DELETE FROM resource_availability");
                execute(db, "CREATE VIEW current_resource_availability AS SELECT * FROM resource_availability");
                populate(db);
                assertNull(scalar(db, "SELECT to_regclass('public.resource_availability')"));
                assertNull(scalar(db, "SELECT to_regclass('public.current_resource_availability')"));
                assertEquals("3", scalar(db, "SELECT count(*) FROM resource_source WHERE code IN ('eshare','seoul_reservation','naver_booking')"));
                String source = "qa_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
                execute(db, "INSERT INTO resource_source(code,name,homepage,enabled,availability_method) VALUES ('%s','Test only','https://example.com/',true,'API')".formatted(source));
                String room = scalar(db, "INSERT INTO resource(kind,sector,category,name,sido,sigungu,address,equipment,capacity_people) VALUES ('SPACE','PUBLIC','MEETING_ROOM','Test room','서울특별시','강남구','서울특별시 강남구',ARRAY['웹캠','마이크'],1) RETURNING id");
                String item = scalar(db, "INSERT INTO resource(kind,sector,category,name,sido,sigungu,address) VALUES ('ITEM','PRIVATE','LAPTOP','Test laptop','부산광역시','해운대구','부산광역시 해운대구') RETURNING id");
                String listing = scalar(db, "INSERT INTO resource_listing(resource_id,source_code,external_id,source_url,booking_channel,fetched_at) VALUES ('%s','%s','room','https://example.com/room','ONLINE',now()) RETURNING id".formatted(room, source));
                String rental = scalar(db, "INSERT INTO resource_listing(resource_id,source_code,external_id,source_url,booking_channel,quantity_total,price_amount,price_unit,fetched_at) VALUES ('%s','%s','laptop','https://example.com/laptop','ONLINE',5,10000,'DAY',now()) RETURNING id".formatted(item, source));
                assertNull(scalar(db, "SELECT price_amount FROM resource_listing WHERE id='%s'".formatted(listing)));
                assertEquals("1", scalar(db, "SELECT count(*) FROM resource WHERE id='%s' AND equipment @> ARRAY['웹캠']".formatted(room)));
                reject(db, "INSERT INTO resource_listing(resource_id,source_code,external_id,source_url,fetched_at) VALUES ('%s','%s','room','https://example.com/duplicate',now())".formatted(room, source));
                String secondSource = source + "_2";
                execute(db, "INSERT INTO resource_source(code,name,homepage) VALUES ('%s','Second test source','https://example.com/')".formatted(secondSource));
                execute(db, "INSERT INTO resource_listing(resource_id,source_code,external_id,source_url,fetched_at) VALUES ('%s','%s','room','https://example.com/alias',now())".formatted(room, secondSource));
                assertEquals("2", scalar(db, "SELECT count(*) FROM resource_listing WHERE resource_id='%s'".formatted(room)));

                var data = CatalogImport.bundled();
                assertEquals(data.path("rooms").size(), CatalogImport.apply(db, data));
                String count = scalar(db, "SELECT count(*) FROM resource_listing");
                CatalogImport.apply(db, data);
                assertEquals(count, scalar(db, "SELECT count(*) FROM resource_listing"));
                var catalog = new CatalogSearch(org.springframework.jdbc.core.simple.JdbcClient.create(new org.springframework.jdbc.datasource.SingleConnectionDataSource(db,true)));
                execute(db, "UPDATE resource_listing SET active=false WHERE source_code='demo'");
                var c = new Task.Conditions("화상면접","2099-11-27","13:00","15:00","서울특별시 성북구",0,List.of("웹캠","마이크","인터넷"),List.of("노트북"),List.of());
                // 성북의 인터넷 제공은 미확인: 장비 조건을 임의로 충족시키지 않는다.
                assertTrue(catalog.find(c).isEmpty());
                c = new Task.Conditions("화상면접","2099-11-27","13:00","15:00","서울 성북구",0,List.of("웹캠","마이크"),List.of("노트북"),List.of());
                assertEquals(2, catalog.find(c).size());
                assertTrue(catalog.find(c).stream().allMatch(e -> e.candidate().price()==0));
                assertEquals(1, catalog.find(c,Set.of(catalog.find(c).getFirst().candidate().id())).size());
                c = new Task.Conditions("화상면접","2099-11-27","13:00","15:00","경기도 성남시 분당구 서현동",10000,List.of("웹캠"),List.of("웹캠","노트북"),List.of());
                assertEquals(2, catalog.find(c).size());
                assertTrue(catalog.find(c).stream().allMatch(e -> e.candidate().price()==10000));
                c = new Task.Conditions("화상면접","2099-11-27","13:00","15:00","경기도 성남시 분당구 서현동",9999,List.of(),List.of(),List.of());
                assertTrue(catalog.find(c).isEmpty());
                c = new Task.Conditions("화상면접","2099-11-27","13:00","14:00","경기도 성남시 분당구 서현동",10000,List.of(),List.of(),List.of());
                assertTrue(catalog.find(c).isEmpty());
                c = new Task.Conditions("화상면접","2099-11-27","13:00","15:00","광주광역시 서구",0,List.of(),List.of(),List.of());
                assertEquals(1, catalog.find(c).size());
                assertNull(catalog.find(c).getFirst().candidate().price());
                c = new Task.Conditions("화상면접","2099-11-27","13:00","15:00","서울 은평구",5000,List.of(),List.of(),List.of());
                var perPerson = catalog.find(c).stream().filter(e -> e.candidate().name().contains("오늘모임")).findFirst().orElseThrow();
                assertEquals(5000, perPerson.candidate().price(), "1인당 시간 요금도 1인 이용 총액으로 계산한다");
                c = new Task.Conditions("화상면접","2099-11-27","13:00","16:00","서울 용산구",9999,List.of(),List.of(),List.of());
                assertTrue(catalog.find(c).isEmpty());
                c = new Task.Conditions("화상면접","2099-11-27","13:00","15:00","서울%' OR true --",10000,List.of(),List.of(),List.of());
                assertTrue(catalog.find(c).isEmpty());
                assertNull(scalar(db, "SELECT price_amount FROM resource_listing WHERE external_id='60538:2인실'"));
                assertEquals("UNKNOWN", scalar(db, "SELECT price_unit FROM resource_listing WHERE external_id='60538:2인실'"));
                assertNull(scalar(db, "SELECT booking_url FROM resource_listing WHERE external_id='EE09M0153094:미디어룸'"));
                assertEquals("PHONE", scalar(db, "SELECT booking_channel FROM resource_listing WHERE external_id='EE09M0153094:미디어룸'"));
                assertEquals("0", scalar(db, "SELECT count(*) FROM resource r JOIN resource_listing l ON l.resource_id=r.id WHERE l.external_id='44387:4인 회의실' AND r.equipment @> ARRAY['노트북']"));
                assertEquals("10000:BOOKING:ROOM:120", scalar(db, "SELECT concat_ws(':',price_amount,price_unit,price_basis,minimum_minutes) FROM resource_listing WHERE external_id='S260211135817886950'"));
                reject(db, "UPDATE resource_listing SET minimum_minutes=0 WHERE external_id='S260211135817886950'");
                assertFalse(schema.contains("CREATE TABLE IF NOT EXISTS resource_availability"));
                reject(db, "UPDATE resource_listing SET price_amount=-1 WHERE id='%s'".formatted(rental));
                reject(db, "UPDATE resource_listing SET quantity_total=-1 WHERE id='%s'".formatted(rental));
                execute(db, "UPDATE resource_listing SET quantity_total=0 WHERE id='%s'".formatted(rental));
                reject(db, "UPDATE resource SET latitude=37.5,longitude=NULL WHERE id='%s'".formatted(room));
            } finally { db.rollback(); }
        }
    }
    private static void populate(Connection db) {
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
            new org.springframework.core.io.ClassPathResource("schema.sql")).populate(db);
    }
    private static void execute(Connection db, String sql) throws SQLException { try (var statement = db.createStatement()) { statement.execute(sql); } }
    private static String scalar(Connection db, String sql) throws SQLException { try (var statement = db.createStatement(); var rows = statement.executeQuery(sql)) { assertTrue(rows.next()); return rows.getString(1); } }
    private static void reject(Connection db, String sql) throws SQLException {
        var savepoint = db.setSavepoint();
        try { assertThrows(SQLException.class, () -> execute(db, sql)); }
        finally { db.rollback(savepoint); db.releaseSavepoint(savepoint); }
    }
}
