package kr.hckt;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Local simulation only: no provider login, external submission or payment. */
@Service
class DemoBookings {
    private final JdbcClient db;
    private final TaskStore store;
    private final CatalogSearch catalog;
    private final ObjectMapper json;
    DemoBookings(JdbcClient db, TaskStore store, CatalogSearch catalog, ObjectMapper json) {
        this.db = db; this.store = store; this.catalog = catalog; this.json = json;
    }
    @PostConstruct void seed() throws Exception {
        try (var input = getClass().getResourceAsStream("/catalog/demo-regions.json")) {
            var regions = json.readTree(input);
            db.sql("INSERT INTO resource_source(code,name,homepage) VALUES('demo','가상 공간','https://example.com/') ON CONFLICT DO NOTHING").update();
            // Existing collected metadata remains intact; synthetic venues have stable, separate IDs.
            for (var region : regions) {
                String sido = region.path("sido").asText(), district = region.path("district").asText();
                for (int i = 0; i < 3; i++) {
                    UUID id = UUID.nameUUIDFromBytes(("demo/" + sido + "/" + district + "/" + i).getBytes(StandardCharsets.UTF_8));
                    String area = district.isBlank() ? sido : district;
                    String name = area + List.of(" 청년센터 화상면접실", " 스터디 다온 1인룸", " 커뮤니티 회의실").get(i);
                    int price = List.of(0, 4000, 2000).get(i);
                    db.sql("""
                        INSERT INTO resource(id,kind,sector,category,name,sido,sigungu,address,equipment,facilities,privacy,interview_policy,capacity_people)
                        VALUES(?,'SPACE',?,'INTERVIEW_ROOM',?,?,?,?,ARRAY['노트북','웹캠','마이크','인터넷','모니터','이어폰','삼각대'],ARRAY['독립 공간','방음','콘센트'],'PRIVATE','EXPLICIT',1)
                        ON CONFLICT(id) DO UPDATE SET equipment=array_replace(resource.equipment,'PC','노트북')
                        """).params(id, i == 1 ? "PRIVATE" : "PUBLIC", name, sido, district, sido + " " + district + " · 가상 주소").update();
                    db.sql("""
                        INSERT INTO resource_listing(id,resource_id,source_code,external_id,source_url,booking_channel,price_amount,price_unit,price_basis,price_quote,eligibility,cancellation,evidence,fetched_at,minimum_people,minimum_minutes,booking_notes)
                        VALUES(?,?,'demo',?,?,'ONLINE',?,'HOUR','ROOM',?,'누구나 이용 · 가상 자격','이용 시작 전 무료 취소','시연용 가상 시설·요금',now(),1,30,'시연용 가상 공간')
                        ON CONFLICT(source_code,external_id) DO UPDATE SET price_amount=excluded.price_amount,price_quote=excluded.price_quote
                        """).params(id, id, id.toString(), "https://example.com/spaces/" + id, price, price == 0 ? "무료 (가상)" : "시간당 " + price + "원 (가상)").update();
                }
            }
            db.sql("""
                INSERT INTO demo_space_block(listing_id,starts_at,ends_at)
                SELECT l.id, (d::date + time '12:00') AT TIME ZONE 'Asia/Seoul', (d::date + time '13:00') AT TIME ZONE 'Asia/Seoul'
                FROM resource_listing l CROSS JOIN generate_series(current_date,current_date+30,interval '1 day') d
                WHERE l.source_code='demo' AND l.price_amount=2000
                ON CONFLICT DO NOTHING
                """).update();
        }
    }
    Task search(UUID user, Task task, Task.Conditions c) {
        if (task.submittedAt != null) throw Accounts.bad("예약 내역에서 기존 예약을 먼저 취소해 주세요.");
        Rules.conditions(c);
        task.simulated = true; task.conditions = c; task.approval = null; task.selectedId = null;
        task.candidates.clear(); task.availability.clear(); task.bookingChecks.clear();
        for (var entry : catalog.findBookable(c)) {
            var candidate = entry.candidate();
            boolean free = available(candidate.id(), c);
            if (!free) continue;
            task.candidates.add(candidate);
            task.availability.put(candidate.id(), new Task.Availability(free ? "AVAILABLE" : "UNAVAILABLE", "예약 현황 조회", Instant.now(), c.date(), c.start(), c.end()));
            if (free && candidate.price() != null) task.bookingChecks.put(candidate.id(), new Task.BookingCheck("READY", "요청 시간 예약 가능", "", candidate.price(), "이용 시작 전 무료 취소"));
            else task.bookingChecks.put(candidate.id(), new Task.BookingCheck("UNKNOWN", free ? "요금 미확인 · 다른 공간을 선택해 주세요" : "선택한 시간은 예약 마감이에요", "", null, ""));
        }
        task.state("CANDIDATES", "예약 가능 시간을 확인했어요."); store.save(user, task); return task;
    }
    boolean available(String listing, Task.Conditions c) {
        return db.sql("""
            SELECT NOT EXISTS(SELECT 1 FROM demo_space_block WHERE listing_id=? AND starts_at<? AND ends_at>?)
            AND NOT EXISTS(SELECT 1 FROM demo_booking WHERE listing_id=? AND status<>'CANCELLED' AND starts_at<? AND ends_at>?)
            """).params(UUID.fromString(listing), end(c), start(c), UUID.fromString(listing), end(c), start(c)).query(Boolean.class).single();
    }
    Task select(UUID user, Task task, String id) {
        if (!"CANDIDATES".equals(task.state) && !"APPROVAL_REQUIRED".equals(task.state)) throw Accounts.bad("공간을 다시 검색해 주세요.");
        var c = task.candidates.stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow(() -> Accounts.bad("공간을 다시 선택해 주세요."));
        Rules.conditions(task.conditions);
        if (c.price() == null || !available(id, task.conditions)) throw Accounts.bad("선택한 시간은 마감되었어요. 다른 공간을 선택해 주세요.");
        task.selectedId = id;
        var t = task.conditions;
        task.approval = new Task.Approval(id, c.name(), "", t.date(), t.start(), t.end(), c.price(), "이용 시작 전 무료 취소", "예약 내용 확인", UUID.randomUUID().toString());
        task.state("APPROVAL_REQUIRED", "예약 내용을 확인해 주세요."); store.save(user, task); return task;
    }
    @Transactional Task approve(UUID user, Task task, String fingerprint) {
        if (!"APPROVAL_REQUIRED".equals(task.state) || task.approval == null || !task.approval.fingerprint().equals(fingerprint) || task.submittedAt != null) throw Accounts.bad("예약 내용을 다시 확인해 주세요.");
        Rules.conditions(task.conditions);
        // All writers lock the listing before checking overlapping time ranges.
        db.sql("SELECT id FROM resource_listing WHERE id=? FOR UPDATE").param(UUID.fromString(task.selectedId)).query(UUID.class).single();
        if (!available(task.selectedId, task.conditions)) throw Accounts.bad("방금 다른 예약이 접수됐어요. 다시 검색해 주세요.");
        db.sql("INSERT INTO demo_booking(task_id,listing_id,starts_at,ends_at,approve_at,status) VALUES(?,?,?,?,?,'PENDING')")
            .params(task.id, UUID.fromString(task.selectedId), start(task.conditions), end(task.conditions), OffsetDateTime.now().plusSeconds(ThreadLocalRandom.current().nextInt(5,121))).update();
        task.submittedAt = Instant.now(); task.bookingReference = "DEMO-" + task.id.toString().substring(0,8).toUpperCase(Locale.ROOT);
        task.state("PENDING", "예약 신청이 완료됐어요. 승인되면 예약 내역에서 확인할 수 있어요."); store.save(user, task); return task;
    }
    @Transactional Task cancel(UUID user, Task task) {
        Task saved = store.get(user, task.id);
        if (saved.submittedAt != null && !start(saved.conditions).isAfter(OffsetDateTime.now())) throw Accounts.bad("이미 시작한 예약은 취소할 수 없어요.");
        db.sql("UPDATE demo_booking SET status='CANCELLED' WHERE task_id=?").param(task.id).update();
        saved.state("STOPPED", "예약을 취소했어요."); store.save(user, saved);
        task.state = saved.state; task.message = saved.message; return saved;
    }
    @Scheduled(fixedDelay = 1000) @Transactional void confirmDue() {
        // One SQL update keeps task state and inventory consistent, including after restart.
        db.sql("""
            WITH approved AS (
                UPDATE demo_booking SET status='CONFIRMED' WHERE status='PENDING' AND approve_at<=now() RETURNING task_id
            )
            UPDATE agent_task t SET data=t.data || jsonb_build_object(
                'state','CONFIRMED','message','예약이 승인됐어요. 예약 내역에서 확인해 주세요.',
                'updatedAt',now(),'events',coalesce(t.data->'events','[]'::jsonb) || jsonb_build_array(jsonb_build_object('at',now(),'message','예약이 승인됐어요.'))),updated_at=now()
            FROM approved a WHERE t.id=a.task_id AND t.data->>'state'='PENDING'
            """).update();
    }
    static OffsetDateTime start(Task.Conditions c) { return OffsetDateTime.parse(c.date() + "T" + c.start() + ":00+09:00"); }
    Task draft(UUID user, Task.Conditions conditions) {
        Rules.conditions(conditions);
        Task task = new Task(); task.id = UUID.randomUUID(); task.conditions = conditions; task.simulated = true;
        task.state("NEEDS_CONDITIONS", "이용할 날짜와 시간을 확인해 주세요."); store.insert(user, task); return task;
    }
    List<Task.Candidate> nearby(String region) {
        var c = new Task.Conditions("화상면접", LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1).toString(), "13:30", "15:30", region, 10000, List.of(), List.of(), List.of());
        return catalog.findBookable(c).stream().map(CatalogSearch.Entry::candidate).toList();
    }
    static OffsetDateTime end(Task.Conditions c) { return OffsetDateTime.parse(c.date() + "T" + c.end() + ":00+09:00"); }
}
