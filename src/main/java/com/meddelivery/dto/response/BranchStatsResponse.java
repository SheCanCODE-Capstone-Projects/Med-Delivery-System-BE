package com.meddelivery.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchStatsResponse {
    private Long branchId;
    private String branchName;
    private int pharmacistCount;
    private int inventoryItems;
    private int lowStockItems;
    private long totalOrders;
    private long pendingOrders;
    private long completedOrders;
    private BigDecimal totalRevenue;
}
