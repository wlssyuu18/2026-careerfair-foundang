package kr.hckt;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import com.microsoft.playwright.options.WaitUntilState;
import java.net.URI;
import java.time.Instant;
import java.util.*;

final class BrowserSession implements AutoCloseable {
    record Snapshot(String url, String text, List<Map<String, Object>> elements) {}
    record Prepared(String elementId, String action, String bodyHash, String pageHash, Instant at) {}
    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext context;
    private Page page;
    private Prepared prepared;
    private boolean submitting;
    private boolean mutationSent;
    private String blocked = "";

    BrowserSession() {
        playwright = Playwright.create();
        try {
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            context = browser.newContext(new Browser.NewContextOptions().setLocale("ko-KR").setTimezoneId("Asia/Seoul").setViewportSize(1280, 900).setAcceptDownloads(false).setServiceWorkers(ServiceWorkerPolicy.BLOCK));
            context.route("**/*", this::route);
            context.onPage(p -> { page = p; p.setDefaultTimeout(12000); p.onDialog(Dialog::dismiss); });
            page = context.newPage();
        } catch (RuntimeException e) { playwright.close(); throw e; }
    }
    private void route(Route route) {
        Request request = route.request();
        try {
            URI uri = URI.create(request.url());
            boolean host = Rules.SITE_HOSTS.contains(uri.getHost());
            boolean secure = "https".equals(uri.getScheme()) && uri.getUserInfo() == null && (uri.getPort() < 0 || uri.getPort() == 443);
            if (!secure || (!host && !"www.seoul.go.kr".equals(uri.getHost()))) { route.abort(); if (request.isNavigationRequest()) blocked = "지원하지 않는 외부 인증 또는 주소예요."; return; }
            boolean read = request.method().equals("GET") || request.method().equals("HEAD");
            if (!host && read && Set.of("script", "stylesheet", "image", "font").contains(request.resourceType())) { route.resume(); return; }
            if (!host) { route.abort(); return; }
            if (read && Rules.readUrl(request.url())) { route.resume(); return; }
            if (request.method().equals("POST") && Rules.searchPost(request.url())) { route.resume(); return; }
            if (submitting && !mutationSent && prepared != null && request.method().equals("POST") && request.url().equals(prepared.action()) && request.postData() != null
                && request.headers().getOrDefault("content-type", "").startsWith("application/x-www-form-urlencoded") && Rules.formHash(request.postData()).equals(prepared.bodyHash())) {
                mutationSent = true; route.resume(); return;
            }
            blocked = "승인되지 않았거나 확인한 양식과 다른 요청을 차단했어요."; route.abort();
        } catch (RuntimeException e) { route.abort(); }
    }
    void navigate(String url) { Rules.site(url); if (!Rules.readUrl(url)) throw new IllegalArgumentException("읽기 가능한 페이지로만 이동할 수 있어요."); blocked = ""; page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(30000)); }
    String blockedReason() { return blocked; }
    boolean mutationSent() { return mutationSent; }
    boolean isOpen() { return !page.isClosed(); }
    void pumpEvents() { if (isOpen()) page.waitForTimeout(50); }
    Snapshot observe() {
        Rules.site(page.url());
        @SuppressWarnings("unchecked") Map<String, Object> data = (Map<String, Object>) page.evaluate("""
            () => {
              document.querySelectorAll('[data-hckt-id]').forEach(e => e.removeAttribute('data-hckt-id'));
              const root = document.querySelector('#searchList') || document.querySelector('main, #contents, #container, #content') || document.body;
              const controls = [...document.querySelectorAll('input,select,textarea,button,[role="button"]')];
              const elements = [...new Set([...controls, ...root.querySelectorAll('a')])]
                .filter(e => e.getClientRects().length && getComputedStyle(e).visibility !== 'hidden' && e.type !== 'hidden' && !e.matches('.mic,[id^="mic_"]')).slice(0,100)
                .map((e,i) => { const id = String(i); e.setAttribute('data-hckt-id',id);
                  const label = e.getAttribute('aria-label') || e.getAttribute('title') || e.placeholder || e.innerText || e.labels?.[0]?.innerText || '';
                  return {id,tag:e.tagName.toLowerCase(),type:e.type || '',label:label.replace(/\\s+/g,' ').trim().slice(0,90),href:e.dataset.linkurl ? new URL(e.dataset.linkurl,location.href).href : e.tagName==='A' ? e.href : '',
                    options:e.tagName==='SELECT' ? [...e.options].map(o=>({value:o.value,label:o.text})).slice(0,60) : []};
                });
              return {text:root.innerText.slice(0,5000),elements};
            }
            """);
        @SuppressWarnings("unchecked") List<Map<String, Object>> elements = (List<Map<String, Object>>) data.get("elements");
        return new Snapshot(page.url(), Rules.redact((String) data.get("text")), elements);
    }
    String rawText() { return page.locator("body").innerText(); }
    String currentUrl() { return page.url(); }
    private Locator element(String id) {
        if (id == null || !id.matches("[0-9]{1,3}")) throw new IllegalArgumentException("관찰한 요소 번호가 필요해요.");
        Locator e = page.locator("[data-hckt-id='" + id + "']");
        if (e.count() != 1 || !e.isVisible()) throw new IllegalArgumentException("화면이 바뀌었어요. 다시 관찰해 주세요.");
        return e;
    }
    void click(String id) {
        Locator e = element(id);
        String text = (String) e.evaluate("e => e.innerText || e.labels?.[0]?.innerText || e.getAttribute('aria-label') || ''");
        if (text.matches("(?s).*(?:결제|최종.*신청|신청.*완료|예약.*완료|제출|취소하기|변경하기|확정하기|동의).*")) throw new IllegalArgumentException("최종 동작은 승인 확인 또는 사용자 직접 진행이 필요해요.");
        String type = e.getAttribute("type");
        if ("password".equals(type) || "file".equals(type)) throw new IllegalArgumentException("인증과 파일 첨부는 사용자가 직접 진행해 주세요.");
        e.click(); page.waitForTimeout(600);
    }
    void fill(String id, String value) {
        Locator e = element(id);
        String type = e.getAttribute("type");
        String hint = String.valueOf(e.getAttribute("name")) + " " + String.valueOf(e.getAttribute("placeholder")) + " " + String.valueOf(e.getAttribute("autocomplete"));
        if (Set.of("password", "file", "email", "tel").contains(type == null ? "" : type) || hint.toLowerCase().matches(".*(?:password|passwd|phone|mobile|email|jumin|login|user.?id|member.?id|fullname|applicant|주민|휴대|연락처|인증|암호|아이디|성명|이름).*")) throw new IllegalArgumentException("개인정보·인증정보 입력은 사용자가 직접 진행해 주세요.");
        if (value.length() > 1000) throw new IllegalArgumentException("입력값이 너무 길어요.");
        e.fill(value);
    }
    void select(String id, String value) { element(id).selectOption(value); page.waitForTimeout(500); }
    void waitBriefly() { page.waitForTimeout(1200); }
    Prepared prepare(String elementId) {
        Locator control = element(elementId);
        @SuppressWarnings("unchecked") Map<String, Object> form = (Map<String, Object>) control.evaluate("""
            e => {
              const f=e.form || e.closest('form');
              if(!f || (e.hasAttribute('formmethod') ? e.formMethod : f.method).toLowerCase()!=='post'
                || (e.hasAttribute('formenctype') ? e.formEnctype : f.enctype)!=='application/x-www-form-urlencoded'
                || f.querySelector('input[type=password],input[type=file]')) return {};
              const data=new FormData(f); if(e.type==='submit' && e.name) data.append(e.name,e.value);
              return {action:e.hasAttribute('formaction') ? e.formAction : f.action,body:new URLSearchParams(data).toString()};
            }
            """);
        String action = (String) form.get("action");
        if (action == null) throw new IllegalArgumentException("이 사이트의 최종 양식은 자동 제출을 검증하지 못했어요. 열린 브라우저에서 직접 진행해 주세요.");
        Rules.site(action);
        // shortcut: 표준 POST 양식만 자동 제출, AJAX·파일·결제 동선은 사이트별 검증 후 추가.
        prepared = new Prepared(elementId, action, Rules.formHash((String) form.get("body")), Rules.hash(Rules.normalize(rawText())), Instant.now());
        return prepared;
    }
    boolean unchanged() {
        if (prepared == null || prepared.at().isBefore(Instant.now().minusSeconds(600))) return false;
        String pageHash = Rules.hash(Rules.normalize(rawText()));
        Prepared old = prepared;
        Prepared current;
        try { current = prepare(old.elementId()); } catch (RuntimeException e) { prepared = old; return false; }
        boolean same = old.action().equals(current.action()) && old.bodyHash().equals(current.bodyHash()) && old.pageHash().equals(pageHash);
        prepared = old; return same;
    }
    void submit() {
        if (!unchanged()) throw new IllegalArgumentException("예약 화면이나 양식이 바뀌었어요. 조건을 다시 확인해 주세요.");
        submitting = true;
        try { element(prepared.elementId()).click(); page.waitForTimeout(1500); }
        finally { submitting = false; }
        if (!mutationSent) throw new IllegalArgumentException("확인한 표준 양식으로 제출하지 못했어요. 열린 브라우저에서 직접 진행해 주세요.");
    }
    @Override public void close() { try { context.close(); } finally { try { browser.close(); } finally { playwright.close(); } } }
}
