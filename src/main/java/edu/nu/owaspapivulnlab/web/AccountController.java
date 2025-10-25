package edu.nu.owaspapivulnlab.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import edu.nu.owaspapivulnlab.model.Account;
import edu.nu.owaspapivulnlab.model.AppUser;
import edu.nu.owaspapivulnlab.repo.AccountRepository;
import edu.nu.owaspapivulnlab.repo.AppUserRepository;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountRepository accounts;
    private final AppUserRepository users;

    public AccountController(AccountRepository accounts, AppUserRepository users) {
        this.accounts = accounts;
        this.users = users;
    }

    // FIX(API1): Only account owner can view balance
    @GetMapping("/{id}/balance")
    public ResponseEntity<?> balance(@PathVariable Long id, Authentication auth) {
        Account a = accounts.findById(id).orElseThrow(() -> new RuntimeException("Account not found"));
        AppUser me = users.findByUsername(auth != null ? auth.getName() : "anonymous").orElse(null);
        if (me == null || !a.getOwnerUserId().equals(me.getId())) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "forbidden: not your account");
            return ResponseEntity.status(403).body(error);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("balance", a.getBalance());
        return ResponseEntity.ok(result);
    }
    }

    // FIX(API5/1): Only account owner can transfer from their account
    @PostMapping("/{id}/transfer")
    public ResponseEntity<?> transfer(@PathVariable Long id, @RequestParam Double amount, Authentication auth) {
        Account a = accounts.findById(id).orElseThrow(() -> new RuntimeException("Account not found"));
        AppUser me = users.findByUsername(auth != null ? auth.getName() : "anonymous").orElse(null);
        if (me == null || !a.getOwnerUserId().equals(me.getId())) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "forbidden: not your account");
            return ResponseEntity.status(403).body(error);
        }
        a.setBalance(a.getBalance() - amount);
        accounts.save(a);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("remaining", a.getBalance());
        return ResponseEntity.ok(response);
    }
    }

    // Safe-ish helper to view my accounts (still leaks more than needed)
    @GetMapping("/mine")
    public Object mine(Authentication auth) {
        AppUser me = users.findByUsername(auth != null ? auth.getName() : "anonymous").orElse(null);
        return me == null ? Collections.emptyList() : accounts.findByOwnerUserId(me.getId());
    }
}
