package com.qcom.salesanalyzer.forecaster.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "fact_sales_daily")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactSalesDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "category_id", nullable = false)
    private Integer categoryId;

    @Column(name = "location_id", nullable = false)
    private Integer locationId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "units_sold", nullable = false)
    private Integer unitsSold;

    @Column(name = "is_forecast", nullable = false)
    private boolean isForecast;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
