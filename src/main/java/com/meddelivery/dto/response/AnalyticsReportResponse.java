package com.meddelivery.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsReportResponse {
    private String period;
    private double totalRevenue;
    private double avgDeliveryTimeMinutes;
    private double orderCancellationRatePercent;
    private long newPatientRegistrations;
    private Map<String, Object> chartData;

    private long totalOrders;
    private long completedOrders;
    private long cancelledOrders;
    private long newPatients;
    private long activePharmacies;
}
