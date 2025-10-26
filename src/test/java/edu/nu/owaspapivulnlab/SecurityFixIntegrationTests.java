package edu.nu.owaspapivulnlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * FIX(API10): Integration tests to verify all security fixes are working correctly
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityFixIntegrationTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    String login(String user, String pw) throws Exception {
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\""+user+"\",\"password\":\""+pw+"\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode n = om.readTree(res);
        return n.get("token").asText();
    }

    @Test
    void testBCryptPasswordHashingWorks() throws Exception {
        // Test that BCrypt-hashed passwords work for authentication
        String token = login("alice", "alice123");
        assertNotNull(token);
        assertTrue(token.length() > 20);
    }

    @Test
    void testSignupEndpoint() throws Exception {
        // Test new signup endpoint with BCrypt hashing
        String payload = "{\"username\":\"newuser\",\"password\":\"password123\",\"email\":\"new@test.com\"}";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("signup successful")));
    }

    @Test
    void testSecurityFilterChainEnforcesAuth() throws Exception {
        // Test that previously open GET endpoints now require authentication
        mvc.perform(get("/api/accounts/1/balance"))
                .andExpect(status().isUnauthorized());
                
        mvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testOwnershipChecksForAccounts() throws Exception {
        String aliceToken = login("alice", "alice123");
        String bobToken = login("bob", "bob123");
        
        // Alice should not be able to access Bob's account (assuming account 2 belongs to Bob)
        mvc.perform(get("/api/accounts/2/balance").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("forbidden: not your account")));
                
        // Alice should not be able to transfer from Bob's account
        mvc.perform(post("/api/accounts/2/transfer?amount=100").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("forbidden: not your account")));
    }

    @Test
    void testDTOPreventsDataLeaks() throws Exception {
        String token = login("alice", "alice123");
        
        // Test that account responses use DTOs (no sensitive internal fields)
        MvcResult result = mvc.perform(get("/api/accounts/mine").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
                
        String response = result.getResponse().getContentAsString();
        JsonNode accounts = om.readTree(response);
        
        if (accounts.isArray() && accounts.size() > 0) {
            JsonNode account = accounts.get(0);
            // Should have safe fields only
            assertTrue(account.has("id"));
            assertTrue(account.has("iban"));
            assertTrue(account.has("balance"));
            // Should not have internal fields like ownerUserId
            assertFalse(account.has("ownerUserId"));
        }
    }

    @Test
    void testMassAssignmentPrevention() throws Exception {
        // Test that user creation prevents role escalation
        String payload = "{\"username\":\"hacker\",\"password\":\"password123\",\"email\":\"hacker@test.com\",\"role\":\"ADMIN\",\"isAdmin\":true}";
        
        MvcResult result = mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andReturn();
                
        String response = result.getResponse().getContentAsString();
        JsonNode user = om.readTree(response);
        
        // Should have safe defaults, not client-provided values
        assertEquals("USER", user.get("role").asText());
        assertFalse(user.get("isAdmin").asBoolean());
    }

    @Test
    void testInputValidationForTransfers() throws Exception {
        String token = login("alice", "alice123");
        
        // Test negative amount rejection
        mvc.perform(post("/api/accounts/1/transfer?amount=-100").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("invalid_amount")));
                
        // Test zero amount rejection
        mvc.perform(post("/api/accounts/1/transfer?amount=0").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("invalid_amount")));
                
        // Test excessive amount rejection
        mvc.perform(post("/api/accounts/1/transfer?amount=2000000").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("amount_too_large")));
    }

    @Test
    void testErrorHandlingDoesNotLeakDetails() throws Exception {
        // Test that error responses are generic and don't leak internal details
        mvc.perform(get("/api/accounts/99999/balance"))
                .andExpect(status().isUnauthorized()); // Should get auth error first
                
        // Test with authentication but invalid account
        String token = login("alice", "alice123");
        mvc.perform(get("/api/accounts/99999/balance").header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", is("internal_server_error")))
                .andExpect(jsonPath("$.message", is("An internal error occurred")))
                .andExpect(jsonPath("$.errorId", notNullValue()));
    }

    @Test
    void testJWTValidationIsStrict() throws Exception {
        // Test with malformed JWT
        mvc.perform(get("/api/accounts/mine").header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
                
        // Test with empty token
        mvc.perform(get("/api/accounts/mine").header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }
}