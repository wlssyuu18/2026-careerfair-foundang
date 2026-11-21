package kr.hckt;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Pattern;

final class RequestConditions {
    private static final Pattern PURPOSE = Pattern.compile("(?i)AI\\s*역량\\s*검사|화상\\s*면접|온라인\\s*면접|온라인\\s*시험|적성\\s*검사|적성\\s*진단|역량\\s*검사|면접|스터디");
    private static final Pattern DATE = Pattern.compile("(?:(\\d{4})\\s*(?:년|[-./])\\s*)?(\\d{1,2})\\s*(?:월|[-./])\\s*(\\d{1,2})\\s*일?(?!\\d|\\s*(?:만|천|원))");
    private static final Pattern WEEKDAY = Pattern.compile("(다다음\\s*주|다음\\s*주|이번\\s*주|다음|이번)?\\s*([월화수목금토일])\\s*(?:요일|욜)");
    private static final Pattern TIME = Pattern.compile("(오전|오후|아침|저녁|밤|새벽|낮)?\\s*(\\d{1,2})\\s*(?:시(?!간)|:)\\s*(?:(\\d{1,2})\\s*분?)?(반)?");
    private static final Pattern AMOUNT = Pattern.compile("(?<![\\d.,일이삼사오육칠팔구십백천만])(?:(\\d+(?:\\.\\d+)?|[일이삼사오육칠팔구])?\\s*만\\s*)?(?:(\\d+(?:\\.\\d+)?|[일이삼사오육칠팔구])?\\s*천\\s*)?(\\d[\\d,]*)?\\s*원");
    private static final Pattern DEVICE = Pattern.compile("(?i)노트북|웹캠|마이크|컴퓨터|이어폰|헤드셋|(?<![a-z])PC(?![a-z])");
    private static final Pattern REGION = Pattern.compile("(?:서울(?:특별시)?|부산(?:광역시)?|대구(?:광역시)?|인천(?:광역시)?|광주(?:광역시)?|대전(?:광역시)?|울산(?:광역시)?|세종(?:특별자치시)?|경기(?:도)?|강원(?:특별자치도)?|충청[남북]도|전라[남북]도|경상[남북]도|제주(?:특별자치도)?)(?:\\s*[가-힣]{1,6}(?:시|구|군|동))?|(?<![가-힣])(?:[가-힣]{2,6}(?:구|동)|[가-힣]{1,6}역)(?=\\s|근처|주변|에서|$)");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    static Task.Conditions read(String text, LocalDate today, String region, Integer budget, List<String> profileEquipment) {
        text = Rules.normalize(text);
        String purpose = "", date = "", start = "", end = "";
        var place = REGION.matcher(text);
        while (place.find()) if (!place.group().equals("지역")) { region = place.group().strip(); break; }
        var purposeMatch = PURPOSE.matcher(text);
        while (purposeMatch.find()) {
            if (text.substring(purposeMatch.end()).matches("(?s)^\\s*(?:이|가|은|는)?\\s*(?:아니|아닌|말고).*")) continue;
            String word = purposeMatch.group().replaceAll("\\s", "").toUpperCase(Locale.ROOT);
            purpose = word.equals("AI역량검사") ? "AI 역량검사" : word.equals("온라인시험") ? "온라인 시험" : word;
            break;
        }
        try {
            var explicit = DATE.matcher(text);
            var weekday = WEEKDAY.matcher(text);
            if (explicit.find()) date = LocalDate.of(explicit.group(1) == null ? today.getYear() : Integer.parseInt(explicit.group(1)), Integer.parseInt(explicit.group(2)), Integer.parseInt(explicit.group(3))).toString();
            else if (weekday.find()) {
                int day = "월화수목금토일".indexOf(weekday.group(2)) + 1;
                String week = weekday.group(1) == null ? "" : weekday.group(1).replaceAll("\\s", "");
                LocalDate target;
                if (week.endsWith("주")) {
                    int offset = week.equals("다다음주") ? 2 : week.equals("다음주") ? 1 : 0;
                    target = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset).plusDays(day - 1);
                } else target = today.with(week.equals("다음") ? TemporalAdjusters.next(DayOfWeek.of(day)) : TemporalAdjusters.nextOrSame(DayOfWeek.of(day)));
                date = target.toString();
                if (weekday.find()) date = "";
            } else if (text.contains("모레")) date = today.plusDays(2).toString();
            else if (text.contains("내일")) date = today.plusDays(1).toString();
            else if (text.contains("오늘")) date = today.toString();
        } catch (RuntimeException ignored) { date = ""; }

        var time = TIME.matcher(text);
        List<String> times = new ArrayList<>();
        String period = "";
        while (time.find()) {
            if (time.group(1) != null) period = time.group(1);
            int hour = Integer.parseInt(time.group(2));
            if (period.equals("밤") && hour == 12) { times.add(""); continue; }
            if (period.isEmpty() && hour >= 1 && hour <= 12 && !time.group().contains(":")) { times.add(""); continue; }
            int minute = time.group(4) != null ? 30 : time.group(3) == null ? 0 : Integer.parseInt(time.group(3));
            if (!period.isEmpty() && hour <= 12) {
                hour %= 12;
                if (Set.of("오후", "저녁", "밤", "낮").contains(period)) hour += 12;
            }
            try { times.add(LocalTime.of(hour, minute).format(HHMM)); } catch (RuntimeException ignored) { times.add(""); }
        }
        if (!times.isEmpty()) start = times.getFirst();
        if (times.size() == 2) end = times.getLast();
        if (times.size() > 2) start = "";
        if (!start.isBlank() && end.isBlank()) {
            var duration = Pattern.compile("(\\d+)\\s*시간(?:\\s*(\\d+)\\s*분)?|(?<!시)(\\d+)\\s*분\\s*(?:동안|이용)").matcher(text);
            if (duration.find()) {
                int minutes = duration.group(1) == null ? Integer.parseInt(duration.group(3)) : Integer.parseInt(duration.group(1)) * 60 + (duration.group(2) == null ? 0 : Integer.parseInt(duration.group(2)));
                if (minutes > 0 && minutes < 1440) end = LocalTime.parse(start).plusMinutes(minutes).format(HHMM);
            }
        }

        Set<Integer> amounts = new LinkedHashSet<>();
        var amount = AMOUNT.matcher(text);
        while (amount.find()) {
            if (amount.group().strip().equals("원")) continue;
            BigDecimal won = BigDecimal.ZERO;
            if (amount.group().contains("만")) won = won.add(number(amount.group(1)).multiply(BigDecimal.valueOf(10000)));
            if (amount.group().contains("천")) won = won.add(number(amount.group(2)).multiply(BigDecimal.valueOf(1000)));
            if (amount.group(3) != null) won = won.add(new BigDecimal(amount.group(3).replace(",", "")));
            try { amounts.add(won.intValueExact()); } catch (ArithmeticException ignored) { /* 원 단위로 표현할 수 없는 금액은 재확인. */ }
        }
        if (amounts.size() == 1) budget = amounts.iterator().next();
        else if (amounts.size() > 1) budget = null;
        else if (text.matches("(?s).*[\\d일이삼사오육칠팔구십백천만]\\s*원.*")) budget = null;
        else if (text.matches("(?s).*(?:무료|공짜)(?:만|로|인|\\s|$).*")) budget = 0;

        Set<String> owned = new LinkedHashSet<>(profileEquipment);
        Set<String> required = new LinkedHashSet<>();
        var devices = DEVICE.matcher(text);
        while (devices.find()) {
            String device = devices.group(); if (device.equalsIgnoreCase("pc")) device = "PC";
            String after = text.substring(devices.end(), Math.min(text.length(), devices.end() + 24));
            boolean missing = after.matches("(?s)^\\s*(?:은|는|이|가|도)?\\s*(?:없|있지\\s*않).*" );
            boolean have = after.matches("(?s)^\\s*(?:은|는|이|가|도)?\\s*(?:있(?:음|어|습니다)?|보유|(?:가지고|갖고)\\s*있).*" );
            if (missing) { owned.remove(device); required.add(device); }
            else if (have) owned.add(device);
            else required.add(device);
        }
        required.removeAll(owned);
        return result(purpose, date, start, end, region, budget, List.copyOf(required), List.copyOf(owned));
    }
    // shortcut: 흔한 요청 표현만 직접 해석, 지원하지 않는 표현은 모델로 넘기고 반복 사례는 테스트와 함께 추가.
    static boolean completeRequest(Task.Conditions value, String text) {
        boolean locationClear = !text.matches("(?s).*(?:근처|주변|지역|에서).*" ) || REGION.matcher(text).find() || text.matches("(?s).*(?:집\\s*근처|집\\s*주변|우리\\s*동네).*" );
        return !value.purpose().isBlank() && !value.date().isBlank() && !value.start().isBlank() && value.budget() != null && locationClear;
    }
    static Task.Conditions merge(String text, LocalDate today, String savedRegion, Integer savedBudget, List<String> savedEquipment, Task.Conditions model) {
        var owned = new LinkedHashSet<>(savedEquipment); owned.addAll(model.ownedEquipment());
        var explicit = read(text, today, "", null, List.copyOf(owned));
        var required = new LinkedHashSet<>(model.equipment()); required.addAll(explicit.equipment()); required.removeAll(explicit.ownedEquipment());
        return result(first(explicit.purpose(), model.purpose()), first(explicit.date(), model.date()), first(explicit.start(), model.start()), first(explicit.end(), model.end()), first(explicit.region(), first(model.region(), savedRegion)), explicit.budget() != null ? explicit.budget() : model.budget() != null ? model.budget() : savedBudget, List.copyOf(required), explicit.ownedEquipment());
    }
    private static String first(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static Task.Conditions result(String purpose, String date, String start, String end, String region, Integer budget, List<String> required, List<String> owned) {
        List<String> notes = new ArrayList<>();
        if (date.isBlank()) notes.add("이용할 날짜를 확인해 주세요.");
        if (start.isBlank()) notes.add("시작 시간과 오전·오후를 확인해 주세요.");
        if (end.isBlank()) notes.add("종료 시간이나 필요한 이용 시간을 알려주세요.");
        if (region == null || region.isBlank()) notes.add("이용할 지역을 입력해 주세요.");
        if (budget == null) notes.add("전체 이용 예산을 확인해 주세요.");
        return new Task.Conditions(purpose, date, start, end, region, budget, required, owned, notes);
    }
    private static BigDecimal number(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ONE;
        int digit = "일이삼사오육칠팔구".indexOf(value);
        return digit >= 0 ? BigDecimal.valueOf(digit + 1) : new BigDecimal(value);
    }
    private RequestConditions() {}
}
