package com.meddelivery.controller;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/flyway")
public class FlywayRepairController {

    @Autowired
    private Flyway flyway;

    @PostMapping("/repair")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<String> repair() {
        flyway.repair();
        return ResponseEntity.ok("Flyway repair completed successfully");
    }
}
