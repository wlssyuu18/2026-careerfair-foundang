package kr.hckt;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class OllamaTest {
    @Test void clearImageUsesNativeOcrWithoutCallingImageModel() {
        try (var ocr = mockStatic(WindowsOcr.class)) {
            byte[] image = {1, 2, 3};
            ocr.when(() -> WindowsOcr.read(image)).thenReturn("09:41\n온라인 시험 안내\n일시: 2099-11-27 13:00~14:00\n준비물: 웹캠, 마이크");
            var client = new Ollama(new ObjectMapper(), "http://127.0.0.1:1", "not-installed");
            var profile = new Accounts.Profile(UUID.randomUUID(), "test@example.com", "테스트", "2000-01-01", "서울 마포구", 20000, List.of("노트북"), true);
            var result = client.extract("만원 이하", image, profile);
            assertEquals("2099-11-27", result.date());
            assertEquals("온라인 시험", result.purpose());
            assertEquals("13:00", result.start());
            assertEquals("14:00", result.end());
            assertEquals(10000, result.budget());
            assertEquals("서울 마포구", result.region());
            assertTrue(result.equipment().contains("웹캠"));
        }
    }
    @Test void imageExtractionPreservesOriginalPastDateAndMergesUserConditions() throws Exception {
        var json = new ObjectMapper();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var request = new java.util.concurrent.atomic.AtomicReference<tools.jackson.databind.JsonNode>();
        server.createContext("/api/chat", exchange -> {
            request.set(json.readTree(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
            var content = Map.of("purpose", "온라인 시험", "date", "2026/11/21(토)", "start", "2026-11-21T12:30", "end", "2026-11-21T14:50", "equipment", List.of("노트북", "웹캠"));
            byte[] response = json.writeValueAsString(Map.of("message", Map.of("content", json.writeValueAsString(content)))).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) { body.write(response); }
        });
        server.start();
        try (var ocr = mockStatic(WindowsOcr.class)) {
            byte[] image = {1, 2, 3};
            ocr.when(() -> WindowsOcr.read(image)).thenReturn("화상면접 안내 2026-11-21 12:30~14:50\n접수 마감 2026-11-20");
            var client = new Ollama(json, "http://127.0.0.1:" + server.getAddress().getPort(), "test-model");
            var profile = new Accounts.Profile(UUID.randomUUID(), "test@example.com", "테스트", "2000-01-01", "서울 마포구", 20000, List.of("노트북"), true);
            var result = client.extract("만원 이하", image, profile);
            assertEquals("2026-11-21", result.date());
            assertEquals("12:30", result.start());
            assertEquals("14:50", result.end());
            assertEquals(10000, result.budget());
            assertEquals("서울 마포구", result.region());
            assertEquals(List.of("웹캠"), result.equipment());
            assertEquals(256, request.get().path("options").path("num_predict").asInt());
            assertEquals(5, request.get().path("format").path("required").size());
            assertTrue(request.get().path("format").path("properties").path("purpose").path("enum").size() > 0);
            assertFalse(request.get().path("messages").get(1).path("images").isEmpty());
        } finally { server.stop(0); }
    }
    @Test void shortRequestDoesNotNeedModelConnection() {
        var client = new Ollama(mock(ObjectMapper.class), "http://127.0.0.1:1", "not-installed");
        var profile = new Accounts.Profile(UUID.randomUUID(), "test@example.com", "테스트", "2000-01-01", "서울 마포구", 20000, List.of(), true);
        var result = client.extract("금요일 오후 6시 ai 역량검사 노트북 있음 만원이하", null, profile);
        assertEquals("AI 역량검사", result.purpose());
        assertEquals(LocalDate.now(ZoneId.of("Asia/Seoul")).with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)).toString(), result.date());
        assertEquals("18:00", result.start());
        assertEquals(10000, result.budget());
        assertEquals(List.of("노트북"), result.ownedEquipment());
        assertFalse(result.equipment().contains("노트북"));
        assertEquals("", result.end());
    }
}
