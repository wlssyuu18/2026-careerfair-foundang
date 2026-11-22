package kr.hckt;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Imports reviewed public venue metadata; never starts Spring or a reservation browser. */
public final class CatalogImport {
    static JsonNode bundled() throws Exception {
        try (var input = CatalogImport.class.getResourceAsStream("/catalog/interview-rooms.json")) {
            if (input == null) throw new IllegalStateException("시설 수집 파일이 없어요.");
            var data = new ObjectMapper().readTree(input);
            validate(data);
            return data;
        }
    }
    static void validate(JsonNode data) {
        Instant.parse(required(data, "reviewedAt"));
        if (!data.path("sources").isArray() || !data.path("rooms").isArray() || data.path("rooms").isEmpty()) throw new IllegalArgumentException("시설 목록을 확인해 주세요.");
        var hosts = new HashMap<String, String>();
        for (var source : data.path("sources")) {
            String code = required(source, "code"), host = url(required(source, "homepage")).getHost();
            required(source, "name");
            if (!code.matches("[a-z][a-z0-9_]{0,39}") || hosts.put(code, host) != null) throw new IllegalArgumentException("중복 제공처 코드예요.");
        }
        Set<String> keys = new HashSet<>(), listings = new HashSet<>();
        for (var room : data.path("rooms")) {
            if (room.has("active") && !room.path("active").isBoolean()) throw new IllegalArgumentException("활성 상태는 참/거짓이어야 해요.");
            String key = required(room, "key"), source = required(room, "source"), external = required(room, "externalId");
            if (!keys.add(key) || !listings.add(source + "/" + external)) throw new IllegalArgumentException("중복 시설 식별자예요.");
            if (!Objects.equals(hosts.get(source), url(required(room, "url")).getHost())) throw new IllegalArgumentException("제공처와 출처 주소가 다릅니다.");
            for (String field : List.of("name", "operator", "sido", "sigungu", "address", "evidence", "notes")) required(room, field);
            for (String field : List.of("equipment", "facilities")) {
                if (!room.path(field).isArray()) throw new IllegalArgumentException("장비·시설 목록이 필요해요.");
                for (var item : room.path(field)) if (!item.isTextual() || item.asText().isBlank()) throw new IllegalArgumentException("장비·시설명을 확인해 주세요.");
            }
            oneOf(room, "sector", "PUBLIC", "PRIVATE");
            oneOf(room, "category", "INTERVIEW_ROOM", "STUDY_ROOM", "MEETING_ROOM", "MEDIA_ROOM");
            oneOf(room, "privacy", "PRIVATE", "SHARED", "UNKNOWN");
            oneOf(room, "interviewPolicy", "EXPLICIT", "CHECK");
            oneOf(room, "addressPrecision", "EXACT", "AREA", "UNKNOWN");
            oneOf(room, "bookingChannel", "ONLINE", "PHONE", "VISIT", "UNKNOWN");
            var price = room.path("price");
            oneOf(price, "unit", "HOUR", "DAY", "BOOKING", "UNKNOWN");
            oneOf(price, "basis", "ROOM", "PERSON", "UNKNOWN");
            required(price, "quote");
            var amount = price.path("amount");
            if (!(amount.isNull() || amount.isIntegralNumber() && amount.asLong() >= 0)) throw new IllegalArgumentException("가격 미확인은 null로 남겨주세요.");
            if (amount.isNull() && !price.path("unit").asText().equals("UNKNOWN")) throw new IllegalArgumentException("미확인 가격 단위는 UNKNOWN이어야 해요.");
            for (var field : List.of("minimumPeople", "minimumMinutes", "capacityPeople")) {
                var value = room.path(field);
                if (!value.isNull() && (!value.isIntegralNumber() || value.asInt() <= 0)) throw new IllegalArgumentException("인원·이용시간을 확인해 주세요.");
            }
            var area = room.path("areaSqm");
            if (!area.isNull() && (!area.isNumber() || area.asDouble() <= 0)) throw new IllegalArgumentException("면적을 확인해 주세요.");
            if (room.has("availability") || room.has("remainingQuantity")) throw new IllegalArgumentException("잔여 현황은 카탈로그에 저장하지 않아요.");
        }
    }
    static int apply(Connection db, JsonNode data) throws SQLException {
        validate(data);
        for (var source : data.path("sources")) {
            execute(db, "INSERT INTO resource_source(code,name,homepage,last_synced_at) VALUES(?,?,?,now()) ON CONFLICT(code) DO UPDATE SET name=excluded.name,homepage=excluded.homepage,last_synced_at=excluded.last_synced_at", source.path("code").asText(), source.path("name").asText(), source.path("homepage").asText());
        }
        var observedAt = Timestamp.from(Instant.parse(data.path("reviewedAt").asText()));
        for (var room : data.path("rooms")) {
            UUID id = UUID.nameUUIDFromBytes(("hckt/catalog/" + room.path("key").asText()).getBytes(StandardCharsets.UTF_8));
            var gear = db.createArrayOf("text", strings(room.path("equipment")));
            var facilities = db.createArrayOf("text", strings(room.path("facilities")));
            try {
                execute(db, """
                    INSERT INTO resource(id,kind,sector,category,name,operator_name,sido,sigungu,eupmyeondong,address,equipment,capacity_people,description,facilities,privacy,interview_policy,area_sqm,address_precision)
                    VALUES(?,'SPACE',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    ON CONFLICT(id) DO UPDATE SET sector=excluded.sector,category=excluded.category,name=excluded.name,operator_name=excluded.operator_name,sido=excluded.sido,sigungu=excluded.sigungu,eupmyeondong=excluded.eupmyeondong,address=excluded.address,equipment=excluded.equipment,capacity_people=excluded.capacity_people,description=excluded.description,facilities=excluded.facilities,privacy=excluded.privacy,interview_policy=excluded.interview_policy,area_sqm=excluded.area_sqm,address_precision=excluded.address_precision,updated_at=now()
                    """, id, text(room,"sector"), text(room,"category"), text(room,"name"), text(room,"operator"), text(room,"sido"), text(room,"sigungu"), text(room,"dong"), text(room,"address"), gear, number(room,"capacityPeople"), text(room,"notes"), facilities, text(room,"privacy"), text(room,"interviewPolicy"), room.path("areaSqm").isNull() ? null : room.path("areaSqm").decimalValue(), text(room,"addressPrecision"));
            } finally { gear.free(); facilities.free(); }
            var price = room.path("price");
            execute(db, """
                INSERT INTO resource_listing(resource_id,source_code,external_id,source_url,booking_url,booking_channel,price_amount,price_unit,price_quote,eligibility,evidence,fetched_at,price_basis,minimum_people,minimum_minutes,booking_notes)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(source_code,external_id) DO UPDATE SET resource_id=excluded.resource_id,source_url=excluded.source_url,booking_url=excluded.booking_url,booking_channel=excluded.booking_channel,price_amount=excluded.price_amount,price_unit=excluded.price_unit,price_quote=excluded.price_quote,eligibility=excluded.eligibility,evidence=excluded.evidence,fetched_at=excluded.fetched_at,price_basis=excluded.price_basis,minimum_people=excluded.minimum_people,minimum_minutes=excluded.minimum_minutes,booking_notes=excluded.booking_notes
                """, id, text(room,"source"), text(room,"externalId"), text(room,"url"), room.path("bookingChannel").asText().equals("ONLINE") ? text(room,"url") : null, text(room,"bookingChannel"), price.path("amount").isNull() ? null : price.path("amount").asLong(), text(price,"unit"), text(price,"quote"), text(room,"eligibility"), text(room,"evidence"), observedAt, text(price,"basis"), number(room,"minimumPeople"), number(room,"minimumMinutes"), text(room,"notes"));
            if (room.has("active")) execute(db, "UPDATE resource_listing SET active=? WHERE source_code=? AND external_id=?", room.path("active").asBoolean(), text(room,"source"), text(room,"externalId"));
        }
        return data.path("rooms").size();
    }
    public static void main(String[] args) throws Exception {
        var config = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("../.env"), StandardCharsets.UTF_8)) { config.load(reader); }
        var data = bundled();
        try (var db = DriverManager.getConnection(config.getProperty("DB_URL", "jdbc:postgresql://127.0.0.1:5432/hckt"), config.getProperty("DB_USER", "hckt"), config.getProperty("DB_PASSWORD"))) {
            db.setAutoCommit(false);
            try {
                try (var input = CatalogImport.class.getResourceAsStream("/schema.sql")) {
                    if (input == null) throw new IllegalStateException("schema.sql missing");
                    execute(db, new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
                int count = apply(db, data);
                db.commit();
                System.out.println("Imported " + count + " venue rooms; no availability data written.");
            }
            catch (Exception e) { db.rollback(); throw e; }
        }
        String key = System.getenv().getOrDefault("SEOUL_OPENAPI_KEY", config.getProperty("SEOUL_OPENAPI_KEY", ""));
        if (!key.isBlank()) {
            var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(config.getProperty("DB_URL", "jdbc:postgresql://127.0.0.1:5432/hckt"), config.getProperty("DB_USER", "hckt"), config.getProperty("DB_PASSWORD"));
            try { System.out.println("Seoul API imported " + new SeoulCatalogSync(source, new ObjectMapper(), key).sync() + " listings (24-hour cache applies)."); }
            catch (Exception e) { System.err.println("Seoul API sync failed; existing data retained. Check API key and connectivity."); }
        }
    }
    private static String required(JsonNode node, String field) { var value = node.path(field); if (!value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException("필수 항목 누락: " + field); return value.asText(); }
    private static void oneOf(JsonNode node, String field, String... options) { if (!List.of(options).contains(required(node, field))) throw new IllegalArgumentException("잘못된 항목: " + field); }
    private static URI url(String value) { var uri = URI.create(value); if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException("출처는 HTTPS 주소여야 해요."); return uri; }
    private static String text(JsonNode node, String field) { return node.path(field).asText(""); }
    private static Integer number(JsonNode node, String field) { return node.path(field).isNull() ? null : node.path(field).asInt(); }
    private static String[] strings(JsonNode array) { var result = new ArrayList<String>(); array.forEach(value -> result.add(value.asText())); return result.toArray(String[]::new); }
    private static void execute(Connection db, String sql, Object... args) throws SQLException { try (var query = db.prepareStatement(sql)) { for (int i=0; i<args.length; i++) query.setObject(i+1,args[i]); query.executeUpdate(); } }
}
