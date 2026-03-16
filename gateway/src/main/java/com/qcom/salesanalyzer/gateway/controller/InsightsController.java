package com.qcom.salesanalyzer.gateway.controller;

import com.qcom.salesanalyzer.gateway.entity.SalesInsight;
import com.qcom.salesanalyzer.gateway.repository.SalesInsightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
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

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        // Subtract 1 day to account for timezone offset (e.g. IST UTC+5:30 causes
        // DATE_TRUNC('month', ...) to be stored as last day of prior month at 18:30 UTC)
        OffsetDateTime from = switch (period) {
            case "quarter" -> now.with(IsoFields.DAY_OF_QUARTER, 1)
                    .withHour(0).withMinute(0).withSecond(0).withNano(0).minusDays(1);
            case "year"    -> now.withMonth(1).withDayOfMonth(1)
                    .withHour(0).withMinute(0).withSecond(0).withNano(0).minusDays(1);
            default        -> now.withDayOfMonth(1)
                    .withHour(0).withMinute(0).withSecond(0).withNano(0).minusDays(1);
        };

        return ResponseEntity.ok(
                insightRepository.findByTenantIdAndPeriodMonthGreaterThanEqualOrderByPeriodMonthDescCategoryRankAsc(
                        tenantId, from));
    }
}
