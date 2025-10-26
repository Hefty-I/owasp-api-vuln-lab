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

    // FIX(API1): Implement proper ownership validation for balance access
    @GetMapping("/{id}/balance")
    public ResponseEntity<?> balance(@PathVariable Long id, Authentication auth) {
        Account a = accounts.findById(id).orElseThrow(() -> new RuntimeException("Account not found"));
        AppUser me = users.findByUsername(auth != null ? auth.getName() : "anonymous").orElse(null);
        
        // FIX(API1): Verify the authenticated user owns this account
        if (me == null || !a.getOwnerUserId().equals(me.getId())) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "forbidden: not your account");
            return ResponseEntity.status(403).body(error);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("balance", a.getBalance());
        return ResponseEntity.ok(result);
    }

    // FIX(API1/API5): Implement proper ownership validation and input validation for transfers
    @PostMapping("/{id}/transfer")
    public ResponseEntity<?> transfer(@PathVariable Long id, @RequestParam Double amount, Authentication auth) {
        Account a = accounts.findById(id).orElseThrow(() -> new RuntimeException("Account not found"));
        AppUser me = users.findByUsername(auth != null ? auth.getName() : "anonymous").orElse(null);
        
        // FIX(API1): Verify the authenticated user owns this account
        if (me == null || !a.getOwnerUserId().equals(me.getId())) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "forbidden: not your account");
            return ResponseEntity.status(403).body(error);
        }
        
        // FIX(API5): Validate transfer amount to prevent negative transfers and overdrafts
        if (amount == null || amount <= 0) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid transfer amount");
            return ResponseEntity.status(400).body(error);
        }
        
        if (amount > a.getBalance()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Insufficient balance");
            return ResponseEntity.status(400).body(error);
        }
        
        a.setBalance(a.getBalance() - amount);
        accounts.save(a);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("remaining", a.getBalance());
        return ResponseEntity.ok(response);
    }

    // FIX(API3): Safe endpoint to view user's own accounts with minimal data exposure
    @GetMapping("/mine")
    public Object mine(Authentication auth) {
        AppUser me = users.findByUsername(auth != null ? auth.getName() : "anonymous").orElse(null);
        return me == null ? Collections.emptyList() : accounts.findByOwnerUserId(me.getId());
    }
}
