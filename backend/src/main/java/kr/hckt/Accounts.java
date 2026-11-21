package kr.hckt;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
class Accounts {
    record Profile(UUID id, String email, String name, String birthDate, String region, int budget, List<String> equipment, boolean notifications) {}
    record Signup(String email, String password, String name, String birthDate, String region, int budget, List<String> equipment, boolean notifications) {}
    record Login(String email, String password) {}
    private final JdbcClient db;
    private final PasswordEncoder passwords;
    private final ObjectMapper json;
    private final String dummy;
    Accounts(JdbcClient db, PasswordEncoder passwords, ObjectMapper json) { this.db = db; this.passwords = passwords; this.json = json; this.dummy = passwords.encode("unused-password-for-timing-only"); }

    Profile create(Signup input) {
        String email = email(input.email());
        if (input.password() == null || input.password().length() < 10 || input.password().getBytes(StandardCharsets.UTF_8).length > 72
            || !input.password().matches("(?s).*[a-zA-Z].*") || !input.password().matches("(?s).*\\d.*") || !input.password().matches("(?s).*[^a-zA-Z0-9].*")) throw bad("비밀번호는 10자 이상, 영문·숫자·특수문자를 포함하고 UTF-8 기준 72바이트 이하여야 해요.");
        Profile profile = validate(new Profile(UUID.randomUUID(), email, input.name(), input.birthDate(), input.region(), input.budget(), input.equipment(), input.notifications()));
        try {
            db.sql("INSERT INTO app_user(id,email,password_hash,name,birth_date,region,budget,equipment,notifications) VALUES(?,?,?,?,?,?,?,?,?)")
                .params(profile.id(), email, passwords.encode(input.password()), profile.name(), Date.valueOf(profile.birthDate()), profile.region(), profile.budget(), json.writeValueAsString(profile.equipment()), profile.notifications()).update();
        } catch (DuplicateKeyException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 이메일이에요."); }
        return profile;
    }
    Profile authenticate(Login input) {
        String normalized;
        try { normalized = email(input.email()); } catch (ResponseStatusException e) { normalized = "invalid"; }
        var rows = db.sql("SELECT id,password_hash,locked_until > NOW() AS locked FROM app_user WHERE email=?").param(normalized).query().listOfRows();
        String hash = rows.isEmpty() ? dummy : (String) rows.getFirst().get("password_hash");
        String supplied = input.password() == null ? "" : input.password();
        boolean validLength = supplied.getBytes(StandardCharsets.UTF_8).length <= 72;
        boolean matches = passwords.matches(validLength ? supplied : "", hash) && validLength;
        if (rows.isEmpty()) throw invalidLogin();
        UUID id = (UUID) rows.getFirst().get("id");
        if (Boolean.TRUE.equals(rows.getFirst().get("locked"))) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 로그인해 주세요.");
        if (!matches) {
            db.sql("UPDATE app_user SET failed_logins=failed_logins+1, locked_until=CASE WHEN failed_logins>=4 THEN NOW()+INTERVAL '5 minutes' ELSE locked_until END WHERE id=?").param(id).update();
            throw invalidLogin();
        }
        db.sql("UPDATE app_user SET failed_logins=0,locked_until=NULL WHERE id=?").param(id).update();
        return get(id);
    }
    Profile get(UUID id) {
        return db.sql("SELECT * FROM app_user WHERE id=?").param(id).query((r, n) -> new Profile(id, r.getString("email"), r.getString("name"), r.getDate("birth_date").toLocalDate().toString(), r.getString("region"), r.getInt("budget"), json.readValue(r.getString("equipment"), json.getTypeFactory().constructCollectionType(List.class, String.class)), r.getBoolean("notifications")))
            .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
    Profile update(UUID id, Profile input) {
        Profile p = validate(new Profile(id, get(id).email(), input.name(), input.birthDate(), input.region(), input.budget(), input.equipment(), input.notifications()));
        db.sql("UPDATE app_user SET name=?,birth_date=?,region=?,budget=?,equipment=?,notifications=? WHERE id=?")
            .params(p.name(), Date.valueOf(p.birthDate()), p.region(), p.budget(), json.writeValueAsString(p.equipment()), p.notifications(), id).update();
        return p;
    }
    private Profile validate(Profile p) {
        if (p.name() == null || p.name().isBlank() || p.name().length() > 40 || p.region() == null || p.region().length() > 100 || p.budget() < 0 || p.budget() > 10000000 || p.equipment() == null || p.equipment().size() > 20 || p.equipment().stream().anyMatch(e -> e == null || e.length() > 50)) throw bad("계정 정보와 예약 조건을 확인해 주세요.");
        try { LocalDate birth = LocalDate.parse(p.birthDate()); if (birth.isAfter(LocalDate.now()) || birth.isBefore(LocalDate.of(1900, 1, 1))) throw new IllegalArgumentException(); }
        catch (RuntimeException e) { throw bad("올바른 생년월일을 입력해 주세요."); }
        return new Profile(p.id(), p.email(), p.name().trim(), p.birthDate(), p.region().trim(), p.budget(), List.copyOf(p.equipment()), p.notifications());
    }
    private String email(String value) {
        if (value == null || value.length() > 254 || !value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw bad("이메일 형식을 확인해 주세요.");
        return value.toLowerCase(Locale.ROOT).trim();
    }
    static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException invalidLogin() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해 주세요."); }
}
