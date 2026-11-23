package kr.hckt;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountWithdrawalTest {
    @Test void withdrawalRequiresPasswordAndDeletesOnlyCurrentAccount() {
        var accounts = mock(Accounts.class);
        var tasks = mock(TaskService.class);
        var controller = new AccountController(accounts, new HttpSessionSecurityContextRepository(), new HttpSessionCsrfTokenRepository(), tasks);
        var id = UUID.randomUUID();
        var profile = new Accounts.Profile(id, "test@example.com", "Test", "2000-01-01", "서울", 10000, List.of(), true);
        when(accounts.get(id)).thenReturn(profile);
        var login = new Accounts.Login(profile.email(), "wrong");
        when(accounts.authenticate(login)).thenThrow(Accounts.bad("비밀번호 확인"));
        assertThrows(RuntimeException.class, () -> controller.withdraw(() -> id.toString(), new AccountController.Withdrawal("wrong"), new MockHttpServletRequest()));
        verifyNoInteractions(tasks);
        verify(accounts, never()).delete(any());
        var request = new MockHttpServletRequest();
        var session = request.getSession();
        controller.withdraw(() -> id.toString(), new AccountController.Withdrawal("correct"), request);
        var order = inOrder(accounts, tasks);
        order.verify(accounts).authenticate(new Accounts.Login(profile.email(), "correct"));
        order.verify(tasks).delete(id);
        order.verify(accounts).delete(id);
        assertThrows(IllegalStateException.class, () -> session.getAttribute("anything"));
    }
}
