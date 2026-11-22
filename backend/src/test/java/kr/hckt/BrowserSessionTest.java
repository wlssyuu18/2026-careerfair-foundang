package kr.hckt;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

class BrowserSessionTest {
    @Test void incompleteFormsCannotBecomeApprovalCandidates() throws Exception {
        try (var session = new BrowserSession()) {
            var field = BrowserSession.class.getDeclaredField("page"); field.setAccessible(true);
            Page page = (Page) field.get(session);
            page.route("**/*", route -> route.fulfill(new Route.FulfillOptions().setContentType("text/html; charset=UTF-8").setBody("<form method='post' action='/reserve'><input name='purpose' aria-label='Purpose' required><button type='submit'>Reserve</button></form>")));
            session.navigate("https://www.eshare.go.kr/");
            var elements = session.observe().elements();
            String button = elements.stream().filter(e -> e.get("tag").equals("button")).findFirst().orElseThrow().get("id").toString();
            String input = elements.stream().filter(e -> e.get("label").equals("Purpose")).findFirst().orElseThrow().get("id").toString();
            assertThrows(IllegalArgumentException.class, () -> session.prepare(button));
            session.fill(input, "Interview");
            assertEquals("https://www.eshare.go.kr/reserve", session.prepare(button).action());
            assertFalse(session.mutationSent());
        }
    }
    @Test void hwaseongSlotsAreReadWithoutSendingSmsOrSubmitting() throws Exception {
        assertTrue(Rules.searchPost("https://youthjob.hscity.go.kr/place/place_time.jsp"));
        assertTrue(Rules.searchPost("https://youthjob.hscity.go.kr/place/resnumchk.jsp"));
        assertFalse(Rules.searchPost("https://youthjob.hscity.go.kr/place/SmsIn.jsp"));
        assertFalse(Rules.searchPost("https://youthjob.hscity.go.kr/place/SurResWriteAct.jsp"));
        try (var session = new BrowserSession()) {
            var field = BrowserSession.class.getDeclaredField("page"); field.setAccessible(true);
            Page page = (Page) field.get(session);
            var writes = new java.util.ArrayList<String>();
            page.route("**/*", route -> {
                String path = java.net.URI.create(route.request().url()).getPath();
                if (path.equals("/place/place_time.jsp")) route.fulfill(new Route.FulfillOptions().setContentType("text/html").setBody("<label><input type='checkbox' name='time1'>10:00 ~ 10:50</label><label><input type='checkbox' name='time1'>11:00 ~ 11:50</label>"));
                else if (route.request().method().equals("POST")) { writes.add(path); route.abort(); }
                else route.fulfill(new Route.FulfillOptions().setContentType("text/html").setBody("""
                    <meta charset="UTF-8"><p>2099-11-25</p><label><input type="radio" name="title" onclick="fetch('/place/place_time.jsp',{method:'POST'}).then(r=>r.text()).then(t=>document.querySelector('#slots').innerHTML=t)">화상면접실</label><div id="slots"></div>
                    <p>인증번호 발송</p><input name="smsinstr"><form method="post" action="SurResWriteAct.jsp"><button>예약하기</button></form>
                    """));
            });
            var c = new Task.Conditions("화상면접", "2099-11-25", "10:00", "12:00", "경기 화성시", 10000, java.util.List.of(), java.util.List.of(), java.util.List.of());
            var live = session.hwaseongAvailability(c, "화상면접실");
            assertEquals("UNAVAILABLE", live.status(), live.evidence());
            assertTrue(session.bookingRequirement().contains("휴대폰 인증"));
            c = new Task.Conditions(c.purpose(), c.date(), "12:00", "15:00", c.region(), c.budget(), c.equipment(), c.ownedEquipment(), c.notes());
            assertEquals("UNAVAILABLE", session.hwaseongAvailability(c, "화상면접실").status());
            assertTrue(writes.isEmpty());
        }
    }
    @Test @EnabledIfEnvironmentVariable(named = "HCKT_LIVE_SEARCH", matches = "true")
    void publicPortalSearchReturnsResultsInBackground() throws Exception {
        try (var session = new BrowserSession()) {
            var field = BrowserSession.class.getDeclaredField("page"); field.setAccessible(true);
            Page page = (Page) field.get(session);
            session.navigate("https://www.eshare.go.kr/UserPortal/adv/resources/AdvEsSearchResourcesMainView.do?rsrcClsCd=010000");
            var controls = session.observe().elements();
            String input = controls.stream().filter(e -> e.get("label").equals("자원명 입력")).findFirst().orElseThrow().get("id").toString();
            String button = controls.stream().filter(e -> e.get("label").toString().contains("상세검색을 실행")).findFirst().orElseThrow().get("id").toString();
            session.fill(input, "면접");
            Response response = page.waitForResponse(r -> r.url().contains("/AdvEsSearchResourcesList.do") && r.request().method().equals("POST") && r.request().postData() != null && r.request().postData().contains(java.net.URLEncoder.encode("면접", java.nio.charset.StandardCharsets.UTF_8)), () -> session.click(button));
            assertEquals(200, response.status());
            page.locator("#searchList").getByText("면접", new Locator.GetByTextOptions().setExact(false)).first().waitFor();
            assertTrue(page.locator("#searchList").innerText().contains("면접"));
        }
    }
    @Test void observesHeaderSearchWithoutOpeningAWindow() throws Exception {
        try (var session = new BrowserSession()) {
            var field = BrowserSession.class.getDeclaredField("page"); field.setAccessible(true);
            Page page = (Page) field.get(session);
            page.route("**/*", route -> route.fulfill(new Route.FulfillOptions().setContentType("text/html").setBody("""
                <header><input placeholder="Search resources"><button>Search</button></header>
                <main><h1>Facilities</h1><a href="/detail">Facility details</a></main>
                """)));
            session.navigate("https://www.eshare.go.kr/");
            var snapshot = session.observe();
            assertTrue(snapshot.elements().stream().anyMatch(e -> e.get("label").equals("Search resources")));
            assertTrue(snapshot.elements().stream().anyMatch(e -> e.get("tag").equals("a")));
            assertEquals(true, page.evaluate("navigator.webdriver"));
        }
    }
}
