package kr.hckt;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

class BrowserSessionTest {
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
