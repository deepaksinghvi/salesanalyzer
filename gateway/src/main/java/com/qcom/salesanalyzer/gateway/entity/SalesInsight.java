package com.qcom.salesanalyzer.gateway.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "sales_insights_summary")
@IdClass(SalesInsightId.class)
public class SalesInsight {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Id
    @Column(name = "period_month")
    private LocalDate periodMonth;

    @Id
    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "category_name")
    private String categoryName;

    @Column(name = "actual_revenue")
    private BigDecimal actualRevenue;

    @Column(name = "predicted_revenue")
    private BigDecimal predictedRevenue;

    @Column(name = "total_units")
    private Long totalUnits;

    @Column(name = "category_rank")
    private Long categoryRank;
}
