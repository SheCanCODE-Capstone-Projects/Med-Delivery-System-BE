package com.meddelivery.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InventoryDashboardStats {

    private long totalMedicines;
    private long totalStockUnits;
    private long lowStockItems;
    private long expiringSoonItems;
}
