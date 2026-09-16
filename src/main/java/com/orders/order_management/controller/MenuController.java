package com.orders.order_management.controller;

import com.orders.order_management.dto.Item.ItemRequest;
import com.orders.order_management.dto.Item.ItemResponse;
import com.orders.order_management.dto.category.CategoryRequest;
import com.orders.order_management.dto.category.CategoryResponse;
import com.orders.order_management.service.MenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("${api.prefix}/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    // ==========================================
    // CATEGORY ENDPOINTS
    // ==========================================

    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.createCategory(request));
    }

    @GetMapping("/categories/{id}")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable UUID id) {
        return ResponseEntity.ok(menuService.getCategoryById(id));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(menuService.updateCategory(id, request));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        menuService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getAllCategories(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "true") boolean active) {
        return ResponseEntity.ok(menuService.getCategories(search, active));
    }

    @GetMapping("/categories/{categoryId}/items")
    public ResponseEntity<List<ItemResponse>> getItemsByCategory(
            @PathVariable UUID categoryId,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(menuService.getItemsByCategory(categoryId, search));
    }

    // ==========================================
    // ITEM ENDPOINTS
    // ==========================================

    @PostMapping("/items")
    public ResponseEntity<ItemResponse> createItem(@Valid @RequestBody ItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.createItem(request));
    }

    @GetMapping("/items/{id}")
    public ResponseEntity<ItemResponse> getItemById(@PathVariable UUID id) {
        return ResponseEntity.ok(menuService.getItemById(id));
    }

    @PutMapping("/items/{id}")
    public ResponseEntity<ItemResponse> updateItem(
            @PathVariable UUID id,
            @Valid @RequestBody ItemRequest request) {
        return ResponseEntity.ok(menuService.updateItem(id, request));
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable UUID id) {
        menuService.deleteItem(id);
        return ResponseEntity.noContent().build();
    }

    // New: Global item search endpoint (e.g., /api/v1/menu/items?search=burger)
    @GetMapping("/items")
    public ResponseEntity<List<ItemResponse>> searchAllItems(
            @RequestParam String search) {
        return ResponseEntity.ok(menuService.getAllAvailableItems(search));
    }
}
