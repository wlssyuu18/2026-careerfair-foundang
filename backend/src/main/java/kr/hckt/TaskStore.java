package kr.hckt;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Repository
class TaskStore {
    private final JdbcClient db;
    private final ObjectMapper json;
    TaskStore(JdbcClient db, ObjectMapper json) {
        this.db = db; this.json = json;
    }
    Task get(UUID user, UUID id) {
        return db.sql("SELECT data::text FROM agent_task WHERE id=? AND user_id=?").params(id, user).query(String.class).optional().map(this::restore)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "작업을 찾을 수 없어요."));
    }
    List<Task> list(UUID user) { return db.sql("SELECT data::text FROM agent_task WHERE user_id=? ORDER BY updated_at DESC").param(user).query(String.class).list().stream().map(this::restore).toList(); }
    private Task restore(String data) {
        Task task = json.readValue(data, Task.class);
        if (task.simulated) {
            var root = json.readTree(data);
            root.path("availability").properties().forEach(e -> task.availability.put(e.getKey(), json.treeToValue(e.getValue(), Task.Availability.class)));
            root.path("bookingChecks").properties().forEach(e -> task.bookingChecks.put(e.getKey(), json.treeToValue(e.getValue(), Task.BookingCheck.class)));
        }
        return task;
    }
    String persisted(Task task) {
        tools.jackson.databind.node.ObjectNode data = json.valueToTree(task);
        if (!task.simulated) { data.remove("availability"); data.remove("bookingChecks"); }
        return json.writeValueAsString(data);
    }
    void insert(UUID user, Task task) { db.sql("INSERT INTO agent_task(id,user_id,data) VALUES(?,?,CAST(? AS jsonb))").params(task.id, user, persisted(task)).update(); }
    void save(UUID user, Task task) { db.sql("UPDATE agent_task SET data=CAST(? AS jsonb),updated_at=NOW() WHERE id=? AND user_id=?").params(persisted(task), task.id, user).update(); }
    void delete(UUID user) { db.sql("DELETE FROM agent_task WHERE user_id=?").param(user).update(); }
    void recover() {
        // shortcut: 브라우저 세션은 로컬 프로세스 수명 동안만 유지, 서버 배포 시 격리된 영속 브라우저로 교체.
        db.sql("UPDATE agent_task SET data=jsonb_set(jsonb_set(data,'{state}',to_jsonb(CASE WHEN data->>'submittedAt' IS NOT NULL THEN 'UNKNOWN'::text ELSE 'STOPPED'::text END)),'{message}',to_jsonb('서버가 다시 시작되었어요. 외부 예약 내역을 먼저 확인해 주세요.'::text)) WHERE data->>'state' IN ('ANALYZING','SEARCHING','PREPARING','AUTH_REQUIRED','INPUT_REQUIRED','APPROVAL_REQUIRED','SUBMITTING','VERIFYING','HANDOFF')").update();
    }
}
