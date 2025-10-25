package edu.nu.owaspapivulnlab.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import edu.nu.owaspapivulnlab.model.Account;
import edu.nu.owaspapivulnlab.model.AppUser;
import edu.nu.owaspapivulnlab.web.dto.AccountDTO;
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
    // FIX(API3): Use AccountDTO to prevent sensitive data leaks
    @GetMapping("/{id}/balance")
    public ResponseEntity<?> balance(@PathVariable Long id, Authentication auth) {
        Account a = accounts.findById(id).orElseThrow(() -> new RuntimeException("Account not found"));
        AppUser me = users.findByUsername(auth != null ? auth.getName() : "anonymous").orElse(null);
        if (me == null || !a.getOwnerUserId().equals(me.getId())) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "forbidden: not your account");
            return ResponseEntity.status(403).body(error);
        }
        AccountDTO dto = new AccountDTO(a.getId(), a.getIban(), a.getBalance());
        return ResponseEntity.ok(dto);
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
        a.setBalance(a.getBalance() - amount);
        accounts.save(a);
        AccountDTO dto = new AccountDTO(a.getId(), a.getIban(), a.getBalance());
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("account", dto);
        return ResponseEntity.ok(response);
    }
    

    // Safe-ish helper to view my accounts (still leaks more than needed)
    // FIX(API3): Return list of AccountDTOs for user's accounts
    @GetMapping("/mine")
    public Object mine(Authentication auth) {
        AppUser me = users.findByUsername(auth != null ? auth.getName() : "anonymous").orElse(null);
        if (me == null) return Collections.emptyList();
        return accounts.findByOwnerUserId(me.getId())
                .stream()
                .map(a -> new AccountDTO(a.getId(), a.getIban(), a.getBalance()))
                .toList();
    }

}