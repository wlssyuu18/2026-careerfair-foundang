package kr.hckt;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Imports catalogue metadata only; dates and reception status are not slot availability. */
@Component
class SeoulCatalogSync {
    static final String LIST = "ListPublicReservationInstitution", DETAIL = "ListPublicReservationDetail";
    static final String SOURCE = "seoul_reservation_api";
    private final DataSource db;
    private final ObjectMapper json;
    private final String key;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    SeoulCatalogSync(DataSource db, ObjectMapper json, @Value("${app.catalog.seoul-key:}") String key) {
        this.db = db; this.json = json; this.key = key.trim();
    }
    @EventListener(ApplicationReadyEvent.class) void start() {
        if (key.isBlank()) return;
        // A separate startup worker leaves the approval scheduler and application requests responsive.
        Thread.ofVirtual().name("seoul-catalog-sync").start(() -> {
            try { LoggerFactory.getLogger(getClass()).info("Seoul catalogue sync complete: {} listings", sync()); }
            catch (Exception e) {
                // HTTP exception messages and request URLs can contain the API key.
                LoggerFactory.getLogger(getClass()).warn("Seoul catalogue sync failed; existing data retained. Check API key, permissions and connectivity.");
            }
        });
    }
    int sync() throws Exception {
        if (key.isBlank()) return 0;
        if (org.springframework.jdbc.core.simple.JdbcClient.create(db).sql("SELECT EXISTS(SELECT 1 FROM resource_source WHERE code=? AND last_synced_at>now()-interval '24 hours')").param(SOURCE).query(Boolean.class).single()) return 0;
        var data = collect();
        if (data.path("rooms").isEmpty()) return 0;
        CatalogImport.validate(data);
        // Fetch every page before opening a transaction: failed/invalid responses never partially replace data.
        try (var connection = db.getConnection()) {
            connection.setAutoCommit(false);
            try { int count = CatalogImport.apply(connection, data); connection.commit(); return count; }
            catch (Exception e) { connection.rollback(); throw e; }
        }
    }
    ObjectNode collect() throws IOException, InterruptedException {
        var data = json.createObjectNode(); data.put("reviewedAt", Instant.now().toString());
        data.putArray("sources").addObject().put("code", SOURCE).put("name", "서울시 공공서비스예약 API").put("homepage", "https://yeyak.seoul.go.kr/");
        var rooms = data.putArray("rooms"); var seen = new HashSet<String>();
        int pageSize = key.equals("sample") ? 5 : 1000;
        for (int first = 1; ; first += pageSize) {
            var page = fetch(LIST, first, first + pageSize - 1, "");
            var rows = page.path("row");
            if (rows.isMissingNode() && page.path("RESULT").path("CODE").asText().equals("INFO-200")) break;
            if (!rows.isArray()) throw new IllegalStateException("Seoul list rows missing");
            if (!rows.isEmpty() && page.path("list_total_count").asInt(-1) < 0) throw new IllegalStateException("Seoul list count missing");
            for (var item : rows) {
                String id = item.path("SVCID").asText();
                if (!id.matches("[A-Za-z0-9_-]{1,200}")) throw new IllegalStateException("Invalid Seoul service ID");
                if (!seen.add(id)) continue;
                if (!(item.path("MINCLASSNM").asText() + " " + item.path("SVCNM").asText()).matches("(?s).*(?:회의|면접|스터디|강의|교육실|세미나|다목적|촬영|녹음|스튜디오).*")) continue;
                var detailPage = fetch(DETAIL, 1, 5, id);
                if (detailPage.path("RESULT").path("CODE").asText().equals("INFO-200")) continue;
                var details = detailPage.path("row");
                if (!details.isArray() || details.isEmpty()) throw new IllegalStateException("Seoul detail missing");
                var detail = details.get(0);
                if (!id.equals(detail.path("SVCID").asText())) throw new IllegalStateException("Seoul service ID mismatch");
                var room = normalize(item, detail);
                rooms.add(room);
            }
            if (rows.isEmpty() || first + rows.size() > page.path("list_total_count").asInt(-1) || key.equals("sample")) break;
        }
        return data;
    }
    JsonNode fetch(String service, int first, int last, String id) throws IOException, InterruptedException {
        // Seoul's documented API uses HTTP :8088 and a key path; never expose it to the frontend or logs.
        String encodedKey = java.net.URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20");
        var uri = URI.create("http://openapi.seoul.go.kr:8088/" + encodedKey + "/json/" + service + "/" + first + "/" + last + "/" + id);
        var response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) throw new IOException("Seoul API HTTP failure");
        return payload(json.readTree(response.body()), service);
    }
    static JsonNode payload(JsonNode root, String service) {
        var body = root.has(service) ? root.path(service) : root;
        String code = body.path("RESULT").path("CODE").asText();
        if (!code.equals("INFO-000") && !code.equals("INFO-200")) throw new IllegalStateException("Seoul API rejected the request");
        if (code.equals("INFO-000") && (!body.path("row").isArray() || body.path("list_total_count").asInt(-1) < 0)) throw new IllegalStateException("Invalid Seoul response");
        return body;
    }
    ObjectNode normalize(JsonNode item, JsonNode detail) {
        String id = item.path("SVCID").asText(), name = clean(item.path("SVCNM").asText());
        if (name.isBlank()) throw new IllegalArgumentException("Seoul venue name missing");
        String address = clean(detail.path("ADRES").asText()), district = clean(item.path("AREANM").asText());
        String sido = address.isBlank() ? "서울특별시" : address.split("\\s+")[0];
        sido = switch (sido) { case "서울", "서울시" -> "서울특별시"; case "경기" -> "경기도"; default -> sido; };
        if (!sido.matches(".*(?:도|시)$")) sido = "서울특별시";
        if (district.isBlank()) {
            var match = java.util.regex.Pattern.compile("(?:^|\\s)([가-힣]+(?:구|군|시))(?:\\s|$)").matcher(address.replaceFirst("^" + java.util.regex.Pattern.quote(sido), ""));
            district = match.find() ? match.group(1) : "지역 미확인";
        }
        if (address.isBlank()) address = sido + " " + district + " · " + clean(item.path("PLACENM").asText());
        var room = json.createObjectNode();
        room.put("key", "seoul-api/" + id).put("source", SOURCE).put("externalId", id).put("name", name);
        room.put("url", "https://yeyak.seoul.go.kr/web/reservation/selectReservView.do?rsv_svc_id=" + id);
        String operator = clean(detail.path("ORGNM").asText());
        room.put("operator", operator.isBlank() ? "서울시 공공서비스예약" : operator).put("sector", "PUBLIC");
        room.put("sido", sido).put("sigungu", district).put("dong", "").put("address", address);
        room.put("addressPrecision", detail.path("ADRES").asText().isBlank() ? "AREA" : "EXACT");
        room.put("category", "MEETING_ROOM").put("privacy", "UNKNOWN").put("interviewPolicy", "CHECK");
        room.putArray("equipment"); room.putArray("facilities");
        for (String field : List.of("minimumPeople", "minimumMinutes", "capacityPeople", "areaSqm")) room.putNull(field);
        String minimum = detail.path("ONEREQMINPR").asText();
        if (minimum.matches("[1-9][0-9]{0,5}")) room.put("minimumPeople", Integer.parseInt(minimum));
        room.put("bookingChannel", "ONLINE").put("eligibility", clean(item.path("USETGTINFO").asText()));
        boolean free = item.path("PAYATNM").asText().trim().equals("무료");
        var price = room.putObject("price"); price.put("basis", "UNKNOWN").put("unit", free ? "BOOKING" : "UNKNOWN");
        if (free) price.put("amount", 0); else price.putNull("amount");
        price.put("quote", free ? "무료 (서울시 API)" : "유료 여부·정확한 요금은 시설 상세 확인 필요");
        room.put("evidence", "서울시 시설대관 목록 + OA-2271 상세 API");
        room.put("active", item.path("SVCSTATNM").asText().trim().equals("접수중"));
        room.put("notes", "서울시 API 상태: " + clean(item.path("SVCSTATNM").asText()) + " · 시간별 잔여 현황은 별도 확인 · " + clean(detail.path("DTLCONT").asText()));
        return room;
    }
    private static String clean(String value) {
        return value.replaceAll("(?is)<(?:script|style)\\b[^>]*>.*?</(?:script|style)>", " ").replaceAll("<[^>]*>", " ").replace("&nbsp;", " ").replace("&amp;", "&").replaceAll("\\s+", " ").trim();
    }
}
