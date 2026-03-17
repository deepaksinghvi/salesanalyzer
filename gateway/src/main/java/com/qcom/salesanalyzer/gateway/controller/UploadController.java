package com.qcom.salesanalyzer.gateway.controller;

import com.qcom.salesanalyzer.gateway.dto.UploadResponse;
import com.qcom.salesanalyzer.gateway.security.UserPrincipal;
import com.qcom.salesanalyzer.gateway.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @PostMapping
    @PreAuthorize("hasAnyRole('Admin', 'SuperAdmin')")
    public ResponseEntity<UploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("periodType") String periodType,
            @AuthenticationPrincipal UserPrincipal principal) throws IOException {

        UploadResponse response = uploadService.uploadFile(
                file, periodType, principal.getTenantId(), principal.getUserId());
        return ResponseEntity.ok(response);
    }
}
