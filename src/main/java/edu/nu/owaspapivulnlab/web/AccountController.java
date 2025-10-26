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
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Refill;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    // FIX(API4): Simple in-memory rate limiter per user for transfers
    private final Map<Long, Bucket> transferBuckets = new ConcurrentHashMap<>();

    private final AccountRepository accounts;
    private final AppUserRepository users;

    public AccountController(AccountRepository accounts, AppUserRepository users) {
        this.accounts = accounts;
        this.users = users;
    }

    // VULNERABILITY(API1: BOLA) - no check whether account belongs to caller
    @GetMapping("/{id}/balance")
    public Double balance(@PathVariable Long id) {
        Account a = accounts.findById(id).orElseThrow(() -> new RuntimeException("Account not found"));
        return a.getBalance();
    }

    // VULNERABILITY(API4: Unrestricted Resource Consumption) - no rate limiting on transfer
    // VULNERABILITY(API5/1): no authorization check on owner
    @PostMapping("/{id}/transfer")
    public ResponseEntity<?> transfer(@PathVariable Long id, @RequestParam Double amount, Authentication auth) {
        Account a = accounts.findById(id).orElseThrow(() -> new RuntimeException("Account not found"));
        AppUser me = users.findByUsername(auth != null ? auth.getName() : "anonymous").orElse(null);
        if (me == null || !a.getOwnerUserId().equals(me.getId())) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "forbidden: not your account");
            return ResponseEntity.status(403).body(error);
        }
        // Rate limit: 5 transfers per minute per user
        Bucket bucket = transferBuckets.computeIfAbsent(me.getId(), k -> Bucket.builder()
                .addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1))))
                .build());
        if (!bucket.tryConsume(1)) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "rate limit exceeded: try again later");
            return ResponseEntity.status(429).body(error);
        }
        a.setBalance(a.getBalance() - amount);
        accounts.save(a);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("remaining", a.getBalance());
        return ResponseEntity.ok(response);
    }

    // Safe-ish helper to view my accounts (still leaks more than needed)
    @GetMapping("/mine")
    public Object mine(Authentication auth) {
        AppUser me = users.findByUsername(auth != null ? auth.getName() : "anonymous").orElse(null);
        return me == null ? Collections.emptyList() : accounts.findByOwnerUserId(me.getId());
    }
}
