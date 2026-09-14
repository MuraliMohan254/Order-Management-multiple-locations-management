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

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(menuService.getActiveCategories());
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

    @GetMapping("/categories/{categoryId}/items")
    public ResponseEntity<List<ItemResponse>> getItemsByCategory(@PathVariable UUID categoryId) {
        return ResponseEntity.ok(menuService.getItemsByCategory(categoryId));
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
}
