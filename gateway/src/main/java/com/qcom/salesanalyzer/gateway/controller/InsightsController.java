package com.qcom.salesanalyzer.gateway.controller;

import com.qcom.salesanalyzer.gateway.dto.DataRangeDto;
import com.qcom.salesanalyzer.gateway.entity.SalesInsight;
import com.qcom.salesanalyzer.gateway.repository.SalesInsightRepository;
import com.qcom.salesanalyzer.gateway.service.InsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightsController {

    private final SalesInsightRepository insightRepository;
    private final InsightsService insightsService;

    /**
     * Get monthly-aggregated sales insights for a tenant.
     *
     * @param range  Predefined range: last_month, last_quarter, last_year, ytd, all (default: all)
     * @param from   Custom range start (YYYY-MM-DD), used when range=custom
     * @param to     Custom range end (YYYY-MM-DD), used when range=custom
     */
    @GetMapping("/{tenantId}")
    public ResponseEntity<List<SalesInsight>> getInsights(
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "all") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        if ("all".equalsIgnoreCase(range)) {
            return ResponseEntity.ok(
                    insightRepository.findByTenantIdOrderByPeriodMonthDescCategoryRankAsc(tenantId));
        }

        if ("custom".equalsIgnoreCase(range) && from != null && to != null) {
            LocalDate fromDate = LocalDate.parse(from).withDayOfMonth(1);
            LocalDate toDate = LocalDate.parse(to).withDayOfMonth(1);
            return ResponseEntity.ok(
                    insightRepository.findByTenantIdAndPeriodMonthBetweenOrderByPeriodMonthDescCategoryRankAsc(
                            tenantId, fromDate, toDate));
        }

        LocalDate now = LocalDate.now();
        LocalDate fromDate = switch (range.toLowerCase()) {
            case "last_month" -> now.minusMonths(1).withDayOfMonth(1);
            case "last_quarter" -> {
                LocalDate threeMonthsAgo = now.minusMonths(3);
                yield threeMonthsAgo.withDayOfMonth(1);
            }
            case "last_year" -> now.minusYears(1).withDayOfMonth(1);
            case "ytd" -> now.withMonth(1).withDayOfMonth(1);
            default -> now.withDayOfMonth(1); // fallback to current month
        };

        return ResponseEntity.ok(
                insightRepository.findByTenantIdAndPeriodMonthGreaterThanEqualOrderByPeriodMonthDescCategoryRankAsc(
                        tenantId, fromDate));
    }

    @GetMapping("/{tenantId}/data-range")
    public ResponseEntity<DataRangeDto> getDataRange(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(insightsService.getDataRange(tenantId));
    }

    @PostMapping("/{tenantId}/refresh")
    public ResponseEntity<Map<String, String>> refreshSummary(@PathVariable UUID tenantId) {
        insightsService.refreshSummaryForTenant(tenantId);
        return ResponseEntity.ok(Map.of("status", "OK", "tenantId", tenantId.toString()));
    }
}
