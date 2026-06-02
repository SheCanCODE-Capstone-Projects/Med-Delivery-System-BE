package com.meddelivery.dto.request;

import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FillFromPrescriptionRequest {

    private List<FillItem> items;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FillItem {
        private String medicineName;
        private Integer quantity;
    }
}
