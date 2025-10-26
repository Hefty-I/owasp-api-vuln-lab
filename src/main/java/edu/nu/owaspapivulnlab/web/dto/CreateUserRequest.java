package edu.nu.owaspapivulnlab.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateUserRequest {
    @NotBlank
    private String username;
    
    @NotBlank
    private String password;
    
    @Email
    private String email;
    
    // FIX(API6): Exclude role and isAdmin from request DTO to prevent mass assignment
    // These fields will be set server-side with safe defaults
}