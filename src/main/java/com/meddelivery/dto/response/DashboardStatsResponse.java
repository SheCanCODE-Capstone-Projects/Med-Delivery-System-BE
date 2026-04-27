package com.meddelivery.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {
    private long totalActiveUsers;
    private long ordersTodayPending;
    private long ordersTodayInProgress;
    private long ordersTodayCompleted;
    private double revenueTodayEstimated;
    private long pendingPharmacyApprovals;
    private long totalPharmacies;
}
