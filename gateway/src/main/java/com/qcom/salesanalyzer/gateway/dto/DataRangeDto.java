package com.qcom.salesanalyzer.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class DataRangeDto {
    private LocalDate minDate;
    private LocalDate maxDate;
    private long totalDays;
    private LocalDate forecastMinDate;
    private LocalDate forecastMaxDate;
    private long forecastDays;
}
