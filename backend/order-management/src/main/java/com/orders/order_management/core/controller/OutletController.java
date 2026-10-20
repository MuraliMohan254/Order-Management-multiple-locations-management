package com.orders.order_management.core.controller;

import com.orders.order_management.core.dto.MultiOutletMonitoringResponse;
import com.orders.order_management.core.entity.Outlet;
import com.orders.order_management.core.service.OutletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/outlets")
@RequiredArgsConstructor
@Tag(name = "Outlet Management")
public class OutletController {

    private final OutletService outletService;

    @GetMapping
    @Operation(summary = "Get All Outlets")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Outlet>> getAllOutlets() {
        return ResponseEntity.ok(outletService.getAllOutlets());
    }

    @GetMapping("/active")
    @Operation(summary = "Get Active Outlets with optional search query and limit")
    public ResponseEntity<List<Outlet>> getActiveOutlets(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        return ResponseEntity.ok(outletService.getActiveOutlets(search, limit));
    }

    @GetMapping("/monitoring")
    @Operation(summary = "Get Multi-Outlet Monitoring Summary with optional pagination and search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MultiOutletMonitoringResponse> getMonitoringSummary(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(outletService.getMonitoringSummary(page, size, search, status));
    }

    @PostMapping
    @Operation(summary = "Create Outlet")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Outlet> createOutlet(@RequestBody Outlet outlet) {
        return ResponseEntity.ok(outletService.createOutlet(outlet));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update Outlet")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Outlet> updateOutlet(@PathVariable String id, @RequestBody Outlet outletDetails) {
        return ResponseEntity.ok(outletService.updateOutlet(id, outletDetails));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Outlet")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteOutlet(@PathVariable String id) {
        outletService.deleteOutlet(id);
        return ResponseEntity.ok().build();
    }
}
