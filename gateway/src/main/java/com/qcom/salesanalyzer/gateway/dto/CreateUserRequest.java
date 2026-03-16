package com.qcom.salesanalyzer.gateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateUserRequest {
    @NotNull
    private UUID tenantId;
    @NotBlank @Email
    private String email;
    @NotBlank
    private String password;
    private String role = "Viewer";
    private String firstName;
    private String lastName;
}
