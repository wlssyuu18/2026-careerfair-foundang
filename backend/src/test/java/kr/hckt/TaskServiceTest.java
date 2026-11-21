package kr.hckt;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskServiceTest {
    @Test void searchListsCandidatesAndSubmitsOnlyAfterApproval() throws Exception {
        var json = new ObjectMapper();
        var store = mock(TaskStore.class); var ollama = mock(Ollama.class); var browser = mock(BrowserSession.class);
        var service = new TaskService(mock(Accounts.class), store, ollama, json);
        UUID user = UUID.randomUUID();
        var task = new Task(); task.id = UUID.randomUUID(); task.state = "NEEDS_CONDITIONS";
        task.conditions = new Task.Conditions("화상면접", "2099-11-27", "13:00", "14:00", "서울 마포구", 10000, List.of("웹캠"), List.of("노트북"), List.of());
        when(store.get(user, task.id)).thenReturn(task);
        var runMethod = TaskService.class.getDeclaredMethod("run", UUID.class, UUID.class); runMethod.setAccessible(true);
        Object run = runMethod.invoke(service, user, task.id);
        var browserField = run.getClass().getDeclaredField("browser"); browserField.setAccessible(true); browserField.set(run, browser);
        var busy = run.getClass().getDeclaredField("busy"); busy.setAccessible(true);
        String raw = "마포 면접실 서울 마포구 웹캠 무료 2099-11-27 13:00 14:00 총 이용금액 무료 이용 전 취소 가능 예약 완료 예약번호 TEST-1234";
        when(browser.isOpen()).thenReturn(true);
        when(browser.observe()).thenReturn(new BrowserSession.Snapshot("https://www.eshare.go.kr/detail", raw, List.of()));
        when(browser.rawText()).thenReturn(raw);
        when(browser.currentUrl()).thenReturn("https://www.eshare.go.kr/detail");
        when(browser.prepare("1")).thenReturn(new BrowserSession.Prepared("1", "https://www.eshare.go.kr/reserve", "body", "page", Instant.now()));
        when(browser.unchanged()).thenReturn(true);
        when(ollama.action(anyString(), any())).thenReturn(
            json.valueToTree(Map.of("action", "remember_candidate", "reason", "클릭 상세 조작 로그", "args", Map.of("name", "마포 면접실", "address", "서울 마포구", "priceQuote", "무료", "equipment", List.of("웹캠"), "evidence", "마포 면접실"))),
            json.valueToTree(Map.of("action", "finish", "args", Map.of())),
            json.valueToTree(Map.of("action", "finish", "args", Map.of())),
            json.valueToTree(Map.of("action", "prepare_approval", "args", Map.of("id", "1", "dateQuote", "2099-11-27", "startQuote", "13:00", "endQuote", "14:00", "priceQuote", "무료", "cancellationQuote", "이용 전 취소 가능"))),
            json.valueToTree(Map.of("action", "report_result", "args", Map.of("state", "CONFIRMED", "reference", "TEST-1234", "evidence", "예약 완료")))
        );
        try {
            service.search(user, task.id, task.conditions);
            org.awaitility.Awaitility.await().until(() -> !busy.getBoolean(run));
            assertEquals("CANDIDATES", task.state);
            assertEquals(1, task.candidates.size());
            verify(browser).navigate("https://yeyak.seoul.go.kr/web/main.do");
            assertTrue(task.events.stream().noneMatch(e -> e.message().contains("조작 로그")));
            service.select(user, task.id, task.candidates.getFirst().id());
            org.awaitility.Awaitility.await().until(() -> !busy.getBoolean(run));
            assertEquals("APPROVAL_REQUIRED", task.state);
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
