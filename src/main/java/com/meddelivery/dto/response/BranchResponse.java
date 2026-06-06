package com.meddelivery.dto.response;

import com.meddelivery.model.enums.BranchStatus;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchResponse {
    private Long id;
    private String name;
    private String address;
    private Double latitude;
    private Double longitude;
    private String contactInfo;
    private BranchStatus status;
    private Long pharmacyId;
    private String pharmacyName;
    private String managerName;
    private String managerEmail;
    private int pharmacistCount;
    private int inventoryItemCount;
    private LocalDateTime createdAt;
}
