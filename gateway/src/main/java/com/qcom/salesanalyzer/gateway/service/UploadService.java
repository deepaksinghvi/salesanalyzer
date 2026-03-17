package com.qcom.salesanalyzer.gateway.service;

import com.qcom.salesanalyzer.gateway.dto.UploadResponse;
import com.qcom.salesanalyzer.gateway.entity.Tenant;
import com.qcom.salesanalyzer.gateway.entity.UploadJob;
import com.qcom.salesanalyzer.gateway.repository.TenantRepository;
import com.qcom.salesanalyzer.gateway.repository.UploadJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadService {

    private final UploadJobRepository uploadJobRepository;
    private final TenantRepository tenantRepository;
    private final RestTemplate restTemplate;

    @Value("${app.upload.tmp-dir}")
    private String tmpDir;

    @Value("${app.orchestrator.base-url}")
    private String orchestratorBaseUrl;

    @Transactional
    public UploadResponse uploadFile(MultipartFile file, String periodType,
                                     UUID tenantId, UUID uploadedBy) throws IOException {
        Path uploadPath = Paths.get(tmpDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(fileName);
        file.transferTo(filePath.toFile());

        UploadJob job = UploadJob.builder()
                .tenantId(tenantId)
                .uploadedBy(uploadedBy)
                .fileName(file.getOriginalFilename())
                .filePath(filePath.toAbsolutePath().toString())
                .periodType(periodType)
                .status("PENDING")
                .build();
        job = uploadJobRepository.save(job);

        notifyOrchestrator(job);

        return UploadResponse.builder()
                .jobId(job.getJobId())
                .status(job.getStatus())
                .message("File uploaded successfully. Processing started.")
                .fileName(file.getOriginalFilename())
                .build();
    }

    private void notifyOrchestrator(UploadJob job) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> payload = new HashMap<>();
            payload.put("jobId", job.getJobId().toString());
            payload.put("tenantId", job.getTenantId().toString());
            payload.put("tenantName", tenantRepository.findById(job.getTenantId())
                    .map(Tenant::getCompanyName).orElse("unknown"));
            payload.put("filePath", job.getFilePath());
            payload.put("periodType", job.getPeriodType());
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(orchestratorBaseUrl + "/api/workflows/process-upload", request, String.class);
        } catch (Exception e) {
            log.error("Failed to notify orchestrator for job {}: {}", job.getJobId(), e.getMessage());
        }
    }
}
