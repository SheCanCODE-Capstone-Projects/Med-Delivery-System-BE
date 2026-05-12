package com.meddelivery.controller;

import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.exception.ResourceNotFoundException;
import com.meddelivery.model.Medicine;
import com.meddelivery.repository.MedicineRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/medicines")
@RequiredArgsConstructor
@Tag(name = "Medicine Management")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class MedicineController {

    private final MedicineRepository medicineRepository;

    @GetMapping("/search")
    @Operation(summary = "Search medicines by name (autocomplete)")
    public ResponseEntity<ApiResponse<List<String>>> searchMedicines(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit) {

        if (q == null || q.trim().isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success("Provide a search query", List.of()));
        }

        List<String> suggestions = medicineRepository.findByNameContainingIgnoreCase(q.trim())
                .stream()
                .limit(limit)
                .map(Medicine::getName)
                .distinct()
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Found " + suggestions.size() + " medicines", suggestions));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get medicine details by ID")
    public ResponseEntity<ApiResponse<Medicine>> getMedicine(@PathVariable Long id) {
        Medicine medicine = medicineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine not found"));
        return ResponseEntity.ok(ApiResponse.success(medicine));
    }
}
