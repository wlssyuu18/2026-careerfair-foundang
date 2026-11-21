package kr.hckt;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.Set;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

final class Rules {
    static final Set<String> SITE_HOSTS = Set.of("www.eshare.go.kr", "eshare.go.kr", "yeyak.seoul.go.kr");
    static boolean searchPost(String url) {
        URI uri = site(url);
        return uri.getHost().equals("yeyak.seoul.go.kr") ? Set.of(
            "/web/search/selectPageListTotalSearch.do", "/web/selectListLocaleSVCAjax.do",
            "/web/reservation/selectListReservPlaceAjax.do", "/web/reservation/selectListReservRegionAjax.do", "/web/reservation/selectListReservSvcAjax.do",
            "/web/reservation/selectListReservCalAjax.do", "/web/reservation/selectAllTimeCheckAjax.do", "/web/reservation/selectListReservCalUnitAjax.do").contains(uri.getPath())
            : Set.of("/UserPortal/adv/resources/AdvEsSearchResourcesList.do", "/UserPortal/comm/zip/selectsigunguList.do",
                "/UserPortal/Upm/searchUserPortalRsrcCls.do", "/UserPortal/Upm/searchNewUserPortalRsrcCls.do",
                "/UserPortal/adv/resources/searchRsrcConvenInfo.do", "/UserPortal/adv/resources/searchRsrcMngtagncyInfo.do").contains(uri.getPath());
    }
    static URI site(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equals(uri.getScheme()) || !SITE_HOSTS.contains(uri.getHost()) || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) throw new IllegalArgumentException();
            return uri;
        } catch (RuntimeException e) { throw new IllegalArgumentException("허용된 예약 포털의 HTTPS 주소만 사용할 수 있어요."); }
    }
    static boolean readUrl(String url) {
        URI uri = site(url);
        String path = uri.getPath().toLowerCase();
        if (path.matches(".*(?:delete|insert|update|cancel|payment|payproc|submit|save|confirmproc|reservereg).*")) return false;
        return path.equals("/") || !path.endsWith(".do") || path.matches(".*(?:select|list|search|view|detail|main|contents|index|info|popup).*\\.do");
    }
    static void conditions(Task.Conditions c) {
        if (c == null || c.purpose().isBlank() || c.purpose().length() > 300 || c.region().isBlank() || c.region().length() > 100 || c.budget() == null || c.budget() < 0 || c.budget() > 10000000 || c.equipment().size() > 20 || c.equipment().stream().anyMatch(s -> s == null || s.length() > 80)) throw new IllegalArgumentException("목적·지역·예산·장비 조건을 확인해 주세요.");
        if (c.ownedEquipment().size() > 20 || c.ownedEquipment().stream().anyMatch(s -> s == null || s.length() > 80)) throw new IllegalArgumentException("보유 장비를 확인해 주세요.");
        try {
            LocalDate date = LocalDate.parse(c.date());
            var now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Seoul"));
            if (date.isBefore(now.toLocalDate()) || (date.equals(now.toLocalDate()) && !LocalTime.parse(c.start()).isAfter(now.toLocalTime())) || !LocalTime.parse(c.end()).isAfter(LocalTime.parse(c.start()))) throw new IllegalArgumentException();
        } catch (RuntimeException e) { throw new IllegalArgumentException("예약 날짜와 입실·퇴실 시간을 확인해 주세요."); }
    }
    static int price(String quote) {
        String clean = quote.replaceAll("\\s", "");
        if (clean.equals("무료") || clean.equals("0원")) return 0;
        if (!clean.matches("(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)원")) throw new IllegalArgumentException("화면에서 전체 이용금액을 확실하게 확인하지 못했어요.");
        return Integer.parseInt(clean.replaceAll("[^0-9]", ""));
    }
    static int totalPrice(String text) {
        var matcher = java.util.regex.Pattern.compile("(?:총\\s*(?:이용\\s*)?(?:금액|요금|비용)|결제\\s*(?:예정\\s*)?금액|합계)\\s*[:：]?\\s*((?:[0-9][0-9,]*\\s*원)|무료)").matcher(text);
        Set<Integer> amounts = new java.util.HashSet<>();
        while (matcher.find()) amounts.add(price(matcher.group(1)));
        if (amounts.size() != 1) throw new IllegalArgumentException("예약 화면에 표시된 총 이용금액을 확실하게 확인하지 못했어요. 직접 확인해 주세요.");
        return amounts.iterator().next();
    }
    static String quote(String text, String quote) {
        if (quote == null || quote.isBlank() || quote.length() > 2000 || !normalize(text).contains(normalize(quote))) throw new IllegalArgumentException("예약 화면에서 확인 근거를 다시 읽어야 해요.");
        return quote;
    }
    static List<String> missingEquipment(List<String> required, List<String> supplied, List<String> owned) {
        List<String> available = java.util.stream.Stream.concat(supplied.stream(), owned.stream()).map(s -> normalize(s).toLowerCase(java.util.Locale.ROOT)).toList();
        return required.stream().filter(need -> available.stream().noneMatch(item -> item.contains(normalize(need).toLowerCase(java.util.Locale.ROOT)))).toList();
    }
    static String normalize(String text) { return text.replaceAll("\\s+", " ").trim(); }
    static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static String formHash(String body) {
        return hash(Arrays.stream(body.split("&")).map(part -> {
            String[] pair = part.split("=", 2);
            var encoder = java.util.Base64.getEncoder();
            return encoder.encodeToString(URLDecoder.decode(pair[0], StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8)) + ":" + encoder.encodeToString(URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
        }).sorted().collect(Collectors.joining("&")));
    }
    static String redact(String text) {
        if (text == null) return "";
        String value = text.replaceAll("https?://[^\\s<>]+", "[링크 숨김]")
            .replaceAll("(?i)(회의\\s*ID|meeting\\s*ID|암호|비밀번호|password|passcode|인증코드)\\s*[:：]?\\s*[^\\s,]+", "$1 [숨김]")
            .replaceAll("(?<!\\d)(?:01[016789]|02|0[3-6][1-5]|070)[- .]?\\d{3,4}[- .]?\\d{4}(?!\\d)", "[연락처 숨김]")
            .replaceAll("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", "[이메일 숨김]");
        return value;
    }
    private Rules() {}
}
