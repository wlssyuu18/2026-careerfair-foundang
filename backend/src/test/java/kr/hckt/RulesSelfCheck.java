package kr.hckt;

import java.time.LocalDate;
import java.util.List;

public class RulesSelfCheck {
    public static void main(String[] args) {
        check(Rules.price("무료") == 0, "무료 금액");
        check(Rules.price("8,000원") == 8000, "원 단위 정수");
        reject(() -> Rules.price("시간당 4,000원"));
        reject(() -> Rules.price("8,000원 ~ 10,000원"));
        check(Rules.totalPrice("시간당 4,000원\n총 이용금액: 8,000원") == 8000, "시간당 요금과 총액 구분");
        reject(() -> Rules.totalPrice("이용요금 4,000원 / 시간"));
        reject(() -> Rules.totalPrice("총금액 8,000원 / 결제금액 9,000원"));
        reject(() -> Rules.site("https://yeyak.seoul.go.kr.evil.example/"));
        reject(() -> Rules.site("https://user:pass@yeyak.seoul.go.kr/"));
        reject(() -> Rules.site("http://127.0.0.1/"));
        reject(() -> Rules.site("https://yeyak.seoul.go.kr:8443/"));
        check(!Rules.readUrl("https://yeyak.seoul.go.kr/web/reservation/insertReserv.do"), "승인 전 쓰기 경로 차단");
        check(Rules.readUrl("https://yeyak.seoul.go.kr/web/reservation/selectReservView.do?rsv_svc_id=1"), "상세 조회 허용");
        check(Rules.formHash("name=a&slot=1").equals(Rules.formHash("slot=1&name=a")), "필드 순서는 무관");
        check(!Rules.formHash("x=a%26b%3Dc").equals(Rules.formHash("x=a&b=c")), "인코딩된 구분자는 다른 양식");
        check(!Rules.formHash("slot=1").equals(Rules.formHash("slot=2")), "승인 후 슬롯 변경 탐지");
        check(!Rules.formHash("slot=1&slot=2").equals(Rules.formHash("slot=1")), "중복 필드 유지");
        String day = LocalDate.now().plusDays(2).toString();
        Rules.conditions(new Task.Conditions("화상면접", day, "13:30", "15:30", "서울 마포구", 10000, List.of("웹캠"), List.of(), List.of()));
        reject(() -> Rules.conditions(new Task.Conditions("화상면접", day, "15:30", "13:30", "서울", 10000, List.of(), List.of(), List.of())));
        reject(() -> Rules.conditions(new Task.Conditions("화상면접", day, "13:30", "15:30", "서울", -1, List.of(), List.of(), List.of())));
        reject(() -> Rules.quote("무료 / 예약 가능 여부 미확인", "예약 확정"));
        check(Rules.missingEquipment(List.of("웹캠", "마이크"), List.of("고화질 웹캠"), List.of("마이크")).isEmpty(), "시설 장비와 보유 장비 조합");
        check(Rules.missingEquipment(List.of("웹캠"), List.of(), List.of("노트북")).equals(List.of("웹캠")), "노트북 보유로 웹캠을 추정하지 않음");
        String redacted = Rules.redact("회의 ID: 123456 암호: secret 연락처 010-1234-5678 https://example.com/meeting");
        check(!redacted.contains("123456") && !redacted.contains("secret") && !redacted.contains("5678") && !redacted.contains("https://"), "접속 정보·연락처 마스킹");
        var today = LocalDate.of(2026, 11, 21);
        var request = RequestConditions.read("금요일 오후 6시 ai 역량검사 노트북 있음 만원이하", today, "서울 마포구", 20000, List.of());
        check(request.purpose().equals("AI 역량검사") && request.date().equals("2026-11-27"), "목적과 다가오는 금요일");
        check(request.start().equals("18:00") && request.end().isEmpty(), "오후 시간과 미입력 종료 시간");
        check(request.budget() == 10000 && request.ownedEquipment().contains("노트북") && !request.equipment().contains("노트북"), "한글 금액과 보유 장비 분리");
        check(request.notes().size() == 1 && request.notes().getFirst().contains("종료"), "해결된 항목을 누락으로 표시하지 않음");
        check(RequestConditions.read("다음 주 금요일 18:30 AI 역량검사 2시간 1만5천원", today, "서울", null, List.of()).end().equals("20:30"), "명시한 이용시간만 반영");
        check(RequestConditions.read("다음 주 금요일 18:30 AI 역량검사 1.5만원", today, "서울", null, List.of()).budget() == 15000, "소수 단위 예산");
        check(RequestConditions.read("2026-11-27 10:15 온라인 면접 8,000원", today, "서울", null, List.of()).start().equals("10:15"), "24시간 표기");
        check(RequestConditions.read("다음 금요일 오후 6시 AI 역량검사 만원", LocalDate.of(2026,11,27), "서울", null, List.of()).date().equals("2026-12-04"), "오늘이 금요일이면 다음 금요일은 일주일 뒤");
        check(RequestConditions.read("이번 주 금요일 오후 6시 면접 만원", today, "서울", null, List.of()).date().equals("2026-11-20"), "이번 주를 임의로 다음 주로 변경하지 않음");
        check(RequestConditions.read("금요일 6시 AI 역량검사 만원", today, "서울", null, List.of()).start().isEmpty(), "오전 오후가 없는 12시간 표기는 확인");
        var changed = RequestConditions.read("금요일 오후 6시 AI 역량검사 강남역 근처 노트북 없음 2만원", today, "서울 마포구", 10000, List.of("노트북"));
        check(changed.region().equals("강남역") && changed.budget() == 20000 && !changed.ownedEquipment().contains("노트북"), "명시한 지역과 없는 장비가 기본 설정보다 우선");
        var merged = RequestConditions.merge("금요일 오후 6시 AI 역량검사 노트북 있음 만원이하", today, "서울", 20000, List.of(), new Task.Conditions("화상면접", "", "", "", "", null, List.of("노트북"), List.of(), List.of("예산 누락")));
        check(merged.purpose().equals("AI 역량검사") && merged.budget() == 10000 && merged.equipment().isEmpty(), "모델의 누락과 잘못된 목적 보정");
        check(RequestConditions.read("면접 말고 금요일 오후 6시 AI 역량검사 예산2만원", today, "서울", 10000, List.of()).purpose().equals("AI 역량검사"), "부정한 목적을 선택하지 않음");
        check(!RequestConditions.completeRequest(request, "금요일 오후 6시 AI 역량검사 홍대 근처 만원"), "해석 못한 위치를 저장된 지역으로 덮지 않음");
        System.out.println("Rules and request interpretation self-checks passed");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void reject(Runnable action) { try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("거부해야 하는 입력을 허용했어요."); }
}
