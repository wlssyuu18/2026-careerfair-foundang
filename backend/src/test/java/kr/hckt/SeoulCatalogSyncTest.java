package kr.hckt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.Properties;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SeoulCatalogSyncTest {
    private final ObjectMapper json = new ObjectMapper();
    private ObjectNode item(String id) {
        return json.createObjectNode().put("SVCID", id).put("SVCNM", "청년 회의실").put("MINCLASSNM", "회의실").put("AREANM", "마포구").put("PAYATNM", "무료").put("SVCSTATNM", "접수중").put("USETGTINFO", "청년");
    }
    private ObjectNode detail(String id) {
        return json.createObjectNode().put("SVCID", id).put("ADRES", "서울특별시 마포구 월드컵로 1").put("DTLCONT", "<script>bad()</script><p>이용 안내</p>");
    }
    @Test void absentKeyNeverContactsNetworkOrDatabase() throws Exception {
        var db = mock(DataSource.class);
        assertEquals(0, new SeoulCatalogSync(db, json, "  ").sync());
        verifyNoInteractions(db);
    }
    @Test void handlesApiErrorsAndNeverInventsPricesEquipmentOrAvailability() {
        for (String code : new String[]{"INFO-100", "ERROR-300", "ERROR-335", "ERROR-500"}) {
            var root = json.createObjectNode(); root.putObject("RESULT").put("CODE", code);
            assertThrows(IllegalStateException.class, () -> SeoulCatalogSync.payload(root, SeoulCatalogSync.LIST));
        }
        var empty = json.createObjectNode(); empty.putObject("RESULT").put("CODE", "INFO-200");
        assertSame(empty, SeoulCatalogSync.payload(empty, SeoulCatalogSync.LIST));
        var api = new SeoulCatalogSync(null, json, "test-key");
        var row = item("S1"); var room = api.normalize(row, detail("S1"));
        assertEquals(0, room.path("price").path("amount").asInt());
        assertTrue(room.path("equipment").isEmpty()); assertFalse(room.has("availability"));
        assertEquals("UNKNOWN", room.path("privacy").asText());
        assertFalse(room.path("notes").asText().contains("bad()"));
        row.put("PAYATNM", "유료"); row.put("SVCSTATNM", "접수종료");
        room = api.normalize(row, detail("S1"));
        assertTrue(room.path("price").path("amount").isNull()); assertFalse(room.path("active").asBoolean());
    }
    @Test void paginatesDeduplicatesAndUsesDetailApi() throws Exception {
        var api = spy(new SeoulCatalogSync(null, json, "test-key"));
        var first = json.createObjectNode().put("list_total_count", 1001);
        var rows = first.putArray("row"); for (int i = 0; i < 1000; i++) rows.add(item("S1"));
        var last = json.createObjectNode().put("list_total_count", 1001); last.putArray("row").add(item("S2"));
        doReturn(first).when(api).fetch(SeoulCatalogSync.LIST, 1, 1000, "");
        doReturn(last).when(api).fetch(SeoulCatalogSync.LIST, 1001, 2000, "");
        for (String id : new String[]{"S1", "S2"}) {
            var response = json.createObjectNode(); response.putArray("row").add(detail(id));
            doReturn(response).when(api).fetch(SeoulCatalogSync.DETAIL, 1, 5, id);
        }
        var data = api.collect(); CatalogImport.validate(data);
        assertEquals(2, data.path("rooms").size());
        verify(api, times(1)).fetch(SeoulCatalogSync.DETAIL, 1, 5, "S1");
        doThrow(new IOException("test outage")).when(api).fetch(SeoulCatalogSync.LIST, 1001, 2000, "");
        assertThrows(IOException.class, api::collect);
        doReturn(last).when(api).fetch(SeoulCatalogSync.LIST, 1001, 2000, "");
        var removed = json.createObjectNode(); removed.putObject("RESULT").put("CODE", "INFO-200");
        doReturn(removed).when(api).fetch(SeoulCatalogSync.DETAIL, 1, 5, "S2");
        assertEquals(1, api.collect().path("rooms").size());
    }
    @Test @EnabledIfEnvironmentVariable(named="HCKT_TEST_DB", matches="true")
    void importsIdempotentlyAndUpdatesReceptionStatusWithoutDeletingHistory() throws Exception {
        var config = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("../.env"), StandardCharsets.UTF_8)) { config.load(reader); }
        try (var db = DriverManager.getConnection(config.getProperty("DB_URL", "jdbc:postgresql://127.0.0.1:5432/hckt"), config.getProperty("DB_USER", "hckt"), config.getProperty("DB_PASSWORD"))) {
            db.setAutoCommit(false);
            try {
                var api = spy(new SeoulCatalogSync(null, json, "sample"));
                var page = json.createObjectNode().put("list_total_count", 1); page.putArray("row").add(item("QA_SEOUL"));
                var details = json.createObjectNode(); details.putArray("row").add(detail("QA_SEOUL"));
                doReturn(page).when(api).fetch(SeoulCatalogSync.LIST, 1, 5, "");
                doReturn(details).when(api).fetch(SeoulCatalogSync.DETAIL, 1, 5, "QA_SEOUL");
                var data = api.collect();
                assertEquals(1, CatalogImport.apply(db, data));
                ((ObjectNode)data.path("rooms").get(0)).put("active", false);
                assertEquals(1, CatalogImport.apply(db, data));
                try (var query = db.createStatement(); var result = query.executeQuery("SELECT count(*),bool_or(active) FROM resource_listing WHERE source_code='seoul_reservation_api' AND external_id='QA_SEOUL'")) {
                    assertTrue(result.next()); assertEquals(1, result.getInt(1)); assertFalse(result.getBoolean(2));
                }
            } finally { db.rollback(); }
        }
    }
}
