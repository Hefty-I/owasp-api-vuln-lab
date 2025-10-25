package edu.nu.owaspapivulnlab.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AccountDTO {
    private Long id;
    private String iban;
    private Double balance;
}
