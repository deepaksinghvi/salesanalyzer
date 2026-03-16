package com.qcom.salesanalyzer.gateway.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Entity
@Immutable
@Subselect("SELECT tenant_id, period_month, category_id, category_name, actual_revenue, predicted_revenue, total_units, category_rank FROM mv_final_sales_insights")
@IdClass(SalesInsightId.class)
public class SalesInsight {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Id
    @Column(name = "period_month")
    private OffsetDateTime periodMonth;

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
