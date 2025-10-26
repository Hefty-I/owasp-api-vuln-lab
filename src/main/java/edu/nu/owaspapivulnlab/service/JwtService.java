package edu.nu.owaspapivulnlab.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.ttl-seconds}")
    private long ttlSeconds;
    
    @Value("${app.jwt.issuer}")
    private String issuer;
    
    @Value("${app.jwt.audience}")
    private String audience;

    // FIX(API8): Hardened JWT with issuer, audience, and shorter TTL
    public String issue(String subject, Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(subject)
                .setIssuer(issuer)
                .setAudience(audience)
                .addClaims(claims)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + ttlSeconds * 1000))
                .signWith(SignatureAlgorithm.HS256, secret.getBytes())
                .compact();
    }
}
