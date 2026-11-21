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
    @Test void catalogueAndAvailabilityConstraintsWorkOnPostgres() throws Exception {
        var config = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("../.env"), StandardCharsets.UTF_8)) { config.load(reader); }
        try (var db = DriverManager.getConnection(config.getProperty("DB_URL", "jdbc:postgresql://127.0.0.1:5432/hckt"), config.getProperty("DB_USER", "hckt"), config.getProperty("DB_PASSWORD"))) {
            db.setAutoCommit(false);
            try {
                String schema;
                try (var resource = getClass().getResourceAsStream("/schema.sql")) { schema = new String(resource.readAllBytes(), StandardCharsets.UTF_8); }
                execute(db, schema); execute(db, schema);
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

                String interval = "'2099-11-27 13:00+09','2099-11-27 15:00+09'";
                execute(db, "INSERT INTO resource_availability(listing_id,starts_at,ends_at,requested_quantity,status,remaining_quantity,evidence,checked_at,expires_at) VALUES ('%s',%s,1,'AVAILABLE',1,'Test only',now(),now()+interval '30 seconds')".formatted(listing, interval));
                assertEquals("AVAILABLE", scalar(db, "SELECT status FROM current_resource_availability WHERE listing_id='%s'".formatted(listing)));
                assertEquals("0", scalar(db, "SELECT count(*) FROM current_resource_availability WHERE listing_id='%s' AND ends_at='2099-11-27 16:00+09'".formatted(listing)));
                execute(db, "UPDATE resource_availability SET checked_at=now()-interval '2 minutes',expires_at=now()-interval '1 minute' WHERE listing_id='%s'".formatted(listing));
                assertEquals("UNKNOWN", scalar(db, "SELECT status FROM current_resource_availability WHERE listing_id='%s'".formatted(listing)));
                execute(db, "UPDATE resource_availability SET checked_at=now(),expires_at=now()+interval '30 seconds' WHERE listing_id='%s'".formatted(listing));
                execute(db, "UPDATE resource_source SET enabled=false WHERE code='%s'".formatted(source));
                assertEquals("UNKNOWN", scalar(db, "SELECT status FROM current_resource_availability WHERE listing_id='%s'".formatted(listing)));
                execute(db, "UPDATE resource_source SET enabled=true,availability_method='NONE' WHERE code='%s'".formatted(source));
                assertEquals("UNKNOWN", scalar(db, "SELECT status FROM current_resource_availability WHERE listing_id='%s'".formatted(listing)));
                execute(db, "UPDATE resource_source SET availability_method='API' WHERE code='%s'".formatted(source));
                execute(db, "UPDATE resource_listing SET active=false WHERE id='%s'".formatted(listing));
                assertEquals("UNKNOWN", scalar(db, "SELECT status FROM current_resource_availability WHERE listing_id='%s'".formatted(listing)));
                reject(db, "INSERT INTO resource_availability(listing_id,starts_at,ends_at,requested_quantity,status,remaining_quantity,evidence,checked_at,expires_at) VALUES ('%s',%s,2,'AVAILABLE',1,'Test only',now(),now()+interval '30 seconds')".formatted(rental, interval));
                reject(db, "INSERT INTO resource_availability(listing_id,starts_at,ends_at,requested_quantity,status,checked_at,expires_at) VALUES ('%s',%s,2,'AVAILABLE',now(),now()+interval '30 seconds')".formatted(rental, interval));
                execute(db, "INSERT INTO resource_availability(listing_id,starts_at,ends_at,requested_quantity,status,remaining_quantity,total_price,evidence,checked_at,expires_at) VALUES ('%s',%s,2,'AVAILABLE',3,40000,'Test only',now(),now()+interval '30 seconds')".formatted(rental, interval));
                assertEquals("AVAILABLE", scalar(db, "SELECT status FROM current_resource_availability WHERE listing_id='%s' AND requested_quantity=2".formatted(rental)));
                execute(db, "UPDATE resource_availability SET status='ERROR',failure_reason='Test timeout' WHERE listing_id='%s'".formatted(rental));
                assertEquals("ERROR", scalar(db, "SELECT status FROM current_resource_availability WHERE listing_id='%s'".formatted(rental)));
                reject(db, "INSERT INTO resource_availability(listing_id,starts_at,ends_at,requested_quantity,status,checked_at,expires_at) VALUES ('%s','2099-11-27 15:00+09','2099-11-27 13:00+09',1,'UNKNOWN',now(),now()+interval '30 seconds')".formatted(rental));
                reject(db, "UPDATE resource_listing SET price_amount=-1 WHERE id='%s'".formatted(rental));
                reject(db, "UPDATE resource_listing SET quantity_total=-1 WHERE id='%s'".formatted(rental));
                execute(db, "UPDATE resource_listing SET quantity_total=0 WHERE id='%s'".formatted(rental));
                reject(db, "UPDATE resource SET latitude=37.5,longitude=NULL WHERE id='%s'".formatted(room));
            } finally { db.rollback(); }
        }
    }
    private static void execute(Connection db, String sql) throws SQLException { try (var statement = db.createStatement()) { statement.execute(sql); } }
    private static String scalar(Connection db, String sql) throws SQLException { try (var statement = db.createStatement(); var rows = statement.executeQuery(sql)) { assertTrue(rows.next()); return rows.getString(1); } }
    private static void reject(Connection db, String sql) throws SQLException {
        var savepoint = db.setSavepoint();
        try { assertThrows(SQLException.class, () -> execute(db, sql)); }
        finally { db.rollback(savepoint); db.releaseSavepoint(savepoint); }
    }
}
