package edu.nu.owaspapivulnlab.web;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import edu.nu.owaspapivulnlab.model.AppUser;
import edu.nu.owaspapivulnlab.repo.AppUserRepository;
import edu.nu.owaspapivulnlab.service.JwtService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AppUserRepository users;
    private final JwtService jwt;
    // FIX(API2): Inject BCryptPasswordEncoder for secure password hashing
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public AuthController(AppUserRepository users, JwtService jwt, org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.users = users;
        this.jwt = jwt;
        this.passwordEncoder = passwordEncoder;
    }

    public static class LoginReq {
        @NotBlank
        private String username;
        @NotBlank
        private String password;

        public LoginReq() {}

        public LoginReq(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public String username() { return username; }
        public String password() { return password; }

        public void setUsername(String username) { this.username = username; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class TokenRes {
        private String token;

        public TokenRes() {}

        public TokenRes(String token) {
            this.token = token;
        }

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginReq req) {
        // FIX(API2): Use BCrypt for secure password comparison instead of plaintext
        AppUser user = users.findByUsername(req.username()).orElse(null);
        if (user != null && passwordEncoder.matches(req.password(), user.getPassword())) {
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", user.getRole());
            claims.put("isAdmin", user.isAdmin());
            String token = jwt.issue(user.getUsername(), claims);
            return ResponseEntity.ok(new TokenRes(token));
        }
        Map<String, String> error = new HashMap<>();
        error.put("error", "invalid credentials");
        return ResponseEntity.status(401).body(error);
    }

    // FIX(API2): Add secure user registration endpoint with BCrypt password hashing
    public static class SignupReq {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
        @NotBlank
        private String email;

        public SignupReq() {}
        public SignupReq(String username, String password, String email) {
            this.username = username;
            this.password = password;
            this.email = email;
        }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getEmail() { return email; }
        public void setUsername(String username) { this.username = username; }
        public void setPassword(String password) { this.password = password; }
        public void setEmail(String email) { this.email = email; }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupReq req) {
        // Check if username already exists
        if (users.findByUsername(req.getUsername()).isPresent()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "username already exists");
            return ResponseEntity.status(409).body(error);
        }
        
        // Hash password with BCrypt before storing
        String hashedPassword = passwordEncoder.encode(req.getPassword());
        AppUser user = AppUser.builder()
                .username(req.getUsername())
                .password(hashedPassword)
                .email(req.getEmail())
                .role("USER")  // Safe default role
                .isAdmin(false)  // Safe default admin flag
                .build();
        users.save(user);
        
        Map<String, String> result = new HashMap<>();
        result.put("status", "signup successful");
        return ResponseEntity.ok(result);
    }
}
