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

    // VULNERABILITY(API1: BOLA) - no check whether account belongs to caller
    @GetMapping("/{id}/balance")
    public Double balance(@PathVariable Long id) {
        Account a = accounts.findById(id).orElseThrow(() -> new RuntimeException("Account not found"));
        return a.getBalance();
    }

    // FIX(API9): Add input validation for transfer amounts
    @PostMapping("/{id}/transfer")
    public ResponseEntity<?> transfer(@PathVariable Long id, @RequestParam Double amount, Authentication auth) {
        // Input validation: reject negative, zero, null, or excessively large amounts
        if (amount == null || amount <= 0) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "invalid_amount");
            error.put("message", "Transfer amount must be positive");
            return ResponseEntity.status(400).body(error);
        }
        
        if (amount > 1000000.0) { // Max transfer limit
            Map<String, String> error = new HashMap<>();
            error.put("error", "amount_too_large");
            error.put("message", "Transfer amount exceeds maximum limit");
            return ResponseEntity.status(400).body(error);
        }
        
        Account a = accounts.findById(id).orElseThrow(() -> new RuntimeException("Account not found"));
        AppUser me = users.findByUsername(auth != null ? auth.getName() : "anonymous").orElse(null);
        
        // Authorization check
        if (me == null || !a.getOwnerUserId().equals(me.getId())) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "forbidden: not your account");
            return ResponseEntity.status(403).body(error);
        }
        
        // Check sufficient balance
        if (a.getBalance() < amount) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "insufficient_balance");
            error.put("message", "Account does not have sufficient balance");
            return ResponseEntity.status(400).body(error);
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
