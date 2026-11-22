package kr.hckt;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskServiceTest {
    @Test void extractedScheduleBecomesReservationOnceAndManualEditsStayUnchanged() {
        var user = UUID.randomUUID();
        var accounts = mock(Accounts.class); var store = mock(TaskStore.class); var ollama = mock(Ollama.class); var catalog = mock(CatalogSearch.class);
        var service = new TaskService(accounts, store, ollama, new ObjectMapper(), catalog);
        var profile = new Accounts.Profile(user, "test@example.com", "테스트", "2000-01-01", "서울", 10000, List.of(), true);
        when(accounts.get(user)).thenReturn(profile);
        byte[] image = {1};
        when(ollama.extract("", image, profile)).thenReturn(new Task.Conditions("온라인 시험", "2099-11-27", "12:30", "14:50", "서울", 10000, List.of(), List.of(), List.of()));
        try {
            var task = service.create(user, "", image);
            org.awaitility.Awaitility.await().until(() -> task.state.equals("NEEDS_CONDITIONS"));
            assertEquals("12:00", task.conditions.start());
            assertEquals("15:00", task.conditions.end());
            when(store.get(user, task.id)).thenReturn(task);
            var edited = new Task.Conditions("온라인 시험", "2099-11-27", "12:15", "15:15", "서울", 10000, List.of(), List.of(), List.of());
            when(catalog.find(edited)).thenReturn(List.of());
            org.awaitility.Awaitility.await().ignoreExceptions().untilAsserted(() -> {
                service.search(user, task.id, edited);
            });
            org.awaitility.Awaitility.await().until(() -> task.state.equals("FAILED"));
            verify(catalog).find(edited);
            assertEquals(edited, task.conditions);
        } finally { service.shutdown(); }
    }
    @Test void emptyCatalogStopsBeforeBrowserAndLlmSearch() throws Exception {
        var json = new ObjectMapper(); var store = mock(TaskStore.class); var ollama = mock(Ollama.class); var catalog = mock(CatalogSearch.class);
        var service = new TaskService(mock(Accounts.class),store,ollama,json,catalog);
        UUID user = UUID.randomUUID();
        var task = new Task(); task.id = UUID.randomUUID(); task.state = "NEEDS_CONDITIONS";
        task.conditions = new Task.Conditions("면접","2099-11-27","13:00","15:00","서울 마포구",10000,List.of(),List.of(),List.of());
        when(store.get(user,task.id)).thenReturn(task);
        when(catalog.find(task.conditions)).thenReturn(List.of());
        try {
            service.search(user,task.id,task.conditions);
            org.awaitility.Awaitility.await().until(() -> task.state.equals("FAILED"));
            assertTrue(task.candidates.isEmpty());
            assertTrue(task.message.contains("현재 등록된 시설"));
            verifyNoInteractions(ollama);
        } finally { service.shutdown(); }
    }
    @Test void legacyCandidateInventoryIsIgnoredWhileBookingHistorySurvives() {
        var json = new ObjectMapper();
        var task = json.readValue("""
            {"bookingReference":"TEST-1234","approval":{"fingerprint":"approved","totalPrice":0},
            "candidates":[{"id":"room","name":"Keep room","availability":"AVAILABLE","remainingQuantity":2,"remaining_quantity":2}]}
            """, Task.class);
        var saved = json.readTree(json.writeValueAsString(task));
        assertEquals("room", task.candidates.getFirst().id());
        assertEquals("TEST-1234", saved.path("bookingReference").asText());
        assertEquals("approved", saved.path("approval").path("fingerprint").asText());
        task.availability.put("room", TaskService.availability(new Task.Conditions("면접","2099-11-27","13:00","14:00","서울",10000,List.of(),List.of(),List.of()),"AVAILABLE","13:00~14:00 예약 가능"));
        var persisted = json.readTree(new TaskStore(mock(org.springframework.jdbc.core.simple.JdbcClient.class),json).persisted(task));
        assertFalse(persisted.has("availability"));
        assertEquals("TEST-1234", persisted.path("bookingReference").asText());
        assertTrue(json.readValue(json.writeValueAsString(task),Task.class).availability.isEmpty());
        for (String field : List.of("availability", "remainingQuantity", "remaining_quantity"))
            assertFalse(saved.path("candidates").get(0).has(field));
    }
    @Test void searchListsCandidatesAndSubmitsOnlyAfterApproval() throws Exception {
        var json = new ObjectMapper();
        var store = mock(TaskStore.class); var ollama = mock(Ollama.class); var browser = mock(BrowserSession.class);
        var catalog = mock(CatalogSearch.class);
        var accounts = mock(Accounts.class);
        var service = new TaskService(accounts, store, ollama, json, catalog);
        UUID user = UUID.randomUUID();
        when(accounts.get(user)).thenReturn(new Accounts.Profile(user, "test@example.com", "테스트", "2000-01-01", "서울 마포구", 10000, List.of("노트북"), true));
        var task = new Task(); task.id = UUID.randomUUID(); task.state = "NEEDS_CONDITIONS";
        task.conditions = new Task.Conditions("화상면접", "2099-11-27", "13:00", "14:00", "서울 마포구", 10000, List.of("웹캠"), List.of("노트북"), List.of());
        when(store.get(user, task.id)).thenReturn(task);
        var runMethod = TaskService.class.getDeclaredMethod("run", UUID.class, UUID.class); runMethod.setAccessible(true);
        Object run = runMethod.invoke(service, user, task.id);
        var browserField = run.getClass().getDeclaredField("browser"); browserField.setAccessible(true); browserField.set(run, browser);
        var busy = run.getClass().getDeclaredField("busy"); busy.setAccessible(true);
        String raw = "마포 면접실 서울 마포구 웹캠 무료 화상면접 가능 청년 이용 가능 2099-11-27 13:00~14:00 예약 가능 총 이용금액 무료 이용 전 취소 가능 예약 완료 예약번호 TEST-1234";
        when(browser.isOpen()).thenReturn(true);
        when(browser.blockedReason()).thenReturn("");
        when(browser.takeBlockedReason()).thenReturn("");
        when(browser.observe()).thenReturn(new BrowserSession.Snapshot("https://www.eshare.go.kr/detail", raw, List.of()));
        when(browser.rawText()).thenReturn(raw);
        when(browser.currentUrl()).thenReturn("https://www.eshare.go.kr/detail");
        when(browser.prepare("1")).thenReturn(new BrowserSession.Prepared("1", "https://www.eshare.go.kr/reserve", "body", "page", Instant.now()));
        when(browser.unchanged()).thenReturn(true);
        var candidate = new Task.Candidate("db-room", "마포 면접실", "공유누리 · 공공", "https://www.eshare.go.kr/detail", "서울 마포구", 0, List.of("웹캠"), "청년", "DB metadata", Instant.now());
        when(catalog.find(task.conditions)).thenReturn(List.of(new CatalogSearch.Entry(candidate,"마포 면접실","ONLINE","EXPLICIT","")));
        when(ollama.availabilityAction(anyString(),any(),any())).thenAnswer(invocation -> {
            assertTrue(task.candidates.isEmpty(), "조건을 확인하기 전에는 후보를 노출하지 않는다");
            return json.valueToTree(Map.of("action", "report_availability", "args", Map.ofEntries(
                Map.entry("status", "AVAILABLE"), Map.entry("roomQuote", "마포 면접실"), Map.entry("dateQuote", "2099-11-27"),
                Map.entry("startQuote", "13:00"), Map.entry("endQuote", "14:00"), Map.entry("evidence", "13:00~14:00 예약 가능"),
                Map.entry("eligible", true), Map.entry("eligibilityQuote", "청년 이용 가능"), Map.entry("purposeQuote", "화상면접 가능"),
                Map.entry("approval", Map.of("id", "1", "dateQuote", "2099-11-27", "startQuote", "13:00", "endQuote", "14:00", "priceQuote", "무료", "cancellationQuote", "이용 전 취소 가능")))));
        });
        when(ollama.action(anyString(), any())).thenReturn(
            json.valueToTree(Map.of("action", "report_result", "args", Map.of("state", "CONFIRMED", "reference", "TEST-1234", "evidence", "예약 완료")))
        );
        try {
            service.search(user, task.id, task.conditions);
            org.awaitility.Awaitility.await().until(() -> !busy.getBoolean(run));
            assertEquals("CANDIDATES", task.state);
            assertEquals(1, task.candidates.size());
            verify(catalog).find(task.conditions);
            verify(browser).navigate(candidate.url());
            verify(browser, never()).navigate("https://yeyak.seoul.go.kr/web/main.do");
            assertEquals("db-room", task.candidates.getFirst().id());
            assertEquals("AVAILABLE", service.get(user, task.id).availability.get("db-room").status());
            assertEquals("READY", task.bookingChecks.get("db-room").status());
            assertEquals(0, task.bookingChecks.get("db-room").totalPrice());
            assertTrue(task.events.stream().noneMatch(e -> e.message().contains("조작 로그")));
            service.select(user, task.id, task.candidates.getFirst().id());
            org.awaitility.Awaitility.await().until(() -> !busy.getBoolean(run));
            assertEquals("APPROVAL_REQUIRED", task.state);
            verify(ollama, never()).action(anyString(), any());
            verify(browser, never()).submit();
            assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.approve(user, task.id, "wrong"));
            service.approve(user, task.id, task.approval.fingerprint());
            org.awaitility.Awaitility.await().until(() -> !busy.getBoolean(run));
            assertEquals("CONFIRMED", task.state);
            assertEquals("TEST-1234", task.bookingReference);
            verify(browser, times(1)).submit();
            assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.approve(user, task.id, task.approval.fingerprint()));
        } finally { service.shutdown(); }
    }
}
