package com.qcom.salesanalyzer.gateway.entity;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

public class SalesInsightId implements Serializable {
    private UUID tenantId;
    private OffsetDateTime periodMonth;
    private Integer categoryId;

    public SalesInsightId() {}

    public SalesInsightId(UUID tenantId, OffsetDateTime periodMonth, Integer categoryId) {
        this.tenantId = tenantId;
        this.periodMonth = periodMonth;
        this.categoryId = categoryId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SalesInsightId that)) return false;
        return java.util.Objects.equals(tenantId, that.tenantId)
                && java.util.Objects.equals(periodMonth, that.periodMonth)
                && java.util.Objects.equals(categoryId, that.categoryId);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(tenantId, periodMonth, categoryId);
    }
}
