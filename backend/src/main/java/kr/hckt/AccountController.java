package kr.hckt;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class AccountController {
    private final Accounts accounts;
    private final HttpSessionSecurityContextRepository contexts;
    private final HttpSessionCsrfTokenRepository csrf;
    AccountController(Accounts accounts, HttpSessionSecurityContextRepository contexts, HttpSessionCsrfTokenRepository csrf) { this.accounts = accounts; this.contexts = contexts; this.csrf = csrf; }
    @GetMapping("/auth/csrf") CsrfToken csrf(CsrfToken token) { return token; }
    @PostMapping("/auth/signup") Accounts.Profile signup(@RequestBody Accounts.Signup input) { return accounts.create(input); }
    @PostMapping("/auth/login") Accounts.Profile login(@RequestBody Accounts.Login input, HttpServletRequest request, HttpServletResponse response) {
        Accounts.Profile p = accounts.authenticate(input);
        request.getSession(); request.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p.id().toString(), null, List.of()));
        SecurityContextHolder.setContext(context); contexts.saveContext(context, request, response); csrf.saveToken(null, request, response);
        return p;
    }
    @GetMapping("/account") Accounts.Profile me(Principal user) { return accounts.get(UUID.fromString(user.getName())); }
    @PutMapping("/account") Accounts.Profile update(Principal user, @RequestBody Accounts.Profile input) { return accounts.update(UUID.fromString(user.getName()), input); }
}
