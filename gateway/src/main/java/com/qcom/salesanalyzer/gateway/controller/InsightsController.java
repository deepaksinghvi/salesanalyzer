package com.qcom.salesanalyzer.gateway.controller;

import com.qcom.salesanalyzer.gateway.entity.SalesInsight;
import com.qcom.salesanalyzer.gateway.repository.SalesInsightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightsController {

    private final SalesInsightRepository insightRepository;

    @GetMapping("/{tenantId}")
    public ResponseEntity<List<SalesInsight>> getInsights(
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "month") String period) {

        if ("all".equalsIgnoreCase(period)) {
            return ResponseEntity.ok(
                    insightRepository.findByTenantIdOrderByPeriodMonthDescCategoryRankAsc(tenantId));
        }

        LocalDate now = LocalDate.now();
        LocalDate from = switch (period) {
            case "quarter" -> now.withDayOfMonth(1).minusMonths((now.getMonthValue() - 1) % 3);
            case "year"    -> now.withMonth(1).withDayOfMonth(1);
            default        -> now.withDayOfMonth(1);
        };

        return ResponseEntity.ok(
                insightRepository.findByTenantIdAndPeriodMonthGreaterThanEqualOrderByPeriodMonthDescCategoryRankAsc(
                        tenantId, from));
    }
}
