package kr.hckt;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountsTest {
    private JdbcClient db;
    private Accounts accounts;
    private BCryptPasswordEncoder passwords;
    private final UUID id = UUID.randomUUID();
    private final String password = "Example123!";

    @BeforeEach void setup() {
        db = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
        passwords = new BCryptPasswordEncoder(4);
        accounts = spy(new Accounts(db, passwords, mock(ObjectMapper.class)));
        doReturn(new Accounts.Profile(id, "test@example.com", "테스터", "2000-01-01", "서울", 10000, List.of(), true)).when(accounts).get(id);
    }
    private void user(boolean locked) {
        when(db.sql(startsWith("SELECT id,password_hash")).param(anyString()).query().listOfRows())
            .thenReturn(List.of(Map.of("id", id, "password_hash", passwords.encode(password), "locked", locked)));
    }
    @Test void correctPasswordRestoresOwnAccount() {
        user(false);
        assertEquals(id, accounts.authenticate(new Accounts.Login("TEST@example.com", password)).id());
        verify(db).sql(startsWith("UPDATE app_user SET failed_logins=0"));
    }
    @Test void wrongPasswordDoesNotReturnProfile() {
        user(false);
        var error = assertThrows(ResponseStatusException.class, () -> accounts.authenticate(new Accounts.Login("test@example.com", "wrong")));
        assertEquals(401, error.getStatusCode().value());
        verify(db).sql(startsWith("UPDATE app_user SET failed_logins=failed_logins+1"));
        verify(accounts, never()).get(id);
    }
    @Test void unknownEmailReturnsSameFailureMessage() {
        when(db.sql(startsWith("SELECT id,password_hash")).param(anyString()).query().listOfRows()).thenReturn(List.of());
        var error = assertThrows(ResponseStatusException.class, () -> accounts.authenticate(new Accounts.Login("missing@example.com", password)));
        assertEquals(401, error.getStatusCode().value());
        assertEquals("이메일 또는 비밀번호를 확인해 주세요.", error.getReason());
    }
    @Test void lockedAccountCannotLogInEvenWithCorrectPassword() {
        user(true);
        var error = assertThrows(ResponseStatusException.class, () -> accounts.authenticate(new Accounts.Login("test@example.com", password)));
        assertEquals(429, error.getStatusCode().value());
        verify(accounts, never()).get(id);
    }
    @Test void oversizedUtf8PasswordFailsWithoutBcryptException() {
        user(false);
        var error = assertThrows(ResponseStatusException.class, () -> accounts.authenticate(new Accounts.Login("test@example.com", "가".repeat(30))));
        assertEquals(401, error.getStatusCode().value());
    }
    @Test void invalidBirthDateIsRejectedBeforeWriting() {
        var input = new Accounts.Signup("new@example.com", password, "테스터", "2001-02-29", "서울", 10000, List.of(), true);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> accounts.create(input)).getStatusCode().value());
        verify(db, never()).sql(startsWith("INSERT"));
    }
}
