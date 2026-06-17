package com.meddelivery.dto.response.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A single point in a "last 6 months" analytics time series.
 * For order-count series {@code value} holds the count; for revenue series it holds the amount.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyPoint {
    private String month;       // short month label, e.g. "Jan"
    private BigDecimal value;
}
