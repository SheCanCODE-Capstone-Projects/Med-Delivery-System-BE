package com.meddelivery.dto.response;

import com.meddelivery.model.enums.PharmacistAction;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ActionLogResponse {

    private Long id;
    private Long orderId;
    private String pharmacistUniqueId;
    private String pharmacistName;
    private PharmacistAction action;
    private String notes;

    private LocalDateTime performedAt;
}