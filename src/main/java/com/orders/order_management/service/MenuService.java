package com.orders.order_management.service;

import com.orders.order_management.dto.Item.ItemRequest;
import com.orders.order_management.dto.Item.ItemResponse;
import com.orders.order_management.dto.category.CategoryRequest;
import com.orders.order_management.dto.category.CategoryResponse;
import com.orders.order_management.entity.Category;
import com.orders.order_management.entity.Item;
import com.orders.order_management.repos.CategoryRepository;
import com.orders.order_management.repos.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class MenuService {

    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;

    // ==========================================
    // CATEGORY CRUD OPERATIONS
    // ==========================================

    public CategoryResponse createCategory(CategoryRequest request) {
        Category category = Category.builder()
                .name(request.getName())
                .isActive(true)
                .build();

        return mapToCategoryResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository.findAllByIsActiveTrue().stream()
                .map(this::mapToCategoryResponse).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        return mapToCategoryResponse(category);
    }

    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        category.setName(request.getName());
        category.setActive(request.getActive());
        return mapToCategoryResponse(categoryRepository.save(category));
    }

    public void deleteCategory(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        // Soft delete: Disable the category instead of removing it from the DB
        category.setActive(false);
        categoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategories(String search) {
        if (search != null && !search.trim().isEmpty()) {
            return categoryRepository.findByNameContainingIgnoreCaseAndIsActive(search.trim(), true).stream()
                    .map(this::mapToCategoryResponse).toList();
        }
        return categoryRepository.findAllByIsActiveTrue().stream()
                .map(this::mapToCategoryResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(String search, boolean active) {
        if (search != null && !search.trim().isEmpty()) {
            return categoryRepository.findByNameContainingIgnoreCaseAndIsActive(search.trim(), active).stream()
                    .map(this::mapToCategoryResponse).toList();
        }
        return categoryRepository.findAllByIsActive(active).stream()
                .map(this::mapToCategoryResponse).toList();
    }
    // ==========================================
    // ITEM CRUD OPERATIONS
    // ==========================================

    public ItemResponse createItem(ItemRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        Item item = Item.builder()
                .category(category)
                .name(request.getName())
                .price(request.getPrice())
                .isAvailable(true)
                .build();

        return mapToItemResponse(itemRepository.save(item));
    }

    @Transactional(readOnly = true)
    public ItemResponse getItemById(UUID id) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));
        return mapToItemResponse(item);
    }

    @Transactional(readOnly = true)
    public List<ItemResponse> getItemsByCategory(UUID categoryId) {
        // Assumes you added findByCategoryIdAndIsAvailableTrue to ItemRepository
        return itemRepository.findByCategoryIdAndIsAvailableTrue(categoryId).stream()
                .map(this::mapToItemResponse).toList();
    }

    public ItemResponse updateItem(UUID id, ItemRequest request) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        // Only update category if it changed to avoid unnecessary DB hits
        if (!item.getCategory().getId().equals(request.getCategoryId())) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found"));
            item.setCategory(category);
        }

        item.setName(request.getName());
        item.setPrice(request.getPrice());
        item.setAvailable(request.isAvailable());
        return mapToItemResponse(itemRepository.save(item));
    }

    public void deleteItem(UUID id) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        // Soft delete: Mark as unavailable so historical receipts don't break
        item.setAvailable(false);
        itemRepository.save(item);
    }

    // ==========================================
    // MAPPERS
    // ==========================================

    private CategoryResponse mapToCategoryResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .isActive(category.isActive())
                .build();
    }

    private ItemResponse mapToItemResponse(Item item) {
        return ItemResponse.builder()
                .id(item.getId())
                .categoryId(item.getCategory().getId())
                .categoryName(item.getCategory().getName())
                .name(item.getName())
                .price(item.getPrice())
                .isAvailable(item.isAvailable())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ItemResponse> getItemsByCategory(UUID categoryId, String search) {
        if (search != null && !search.trim().isEmpty()) {
            return itemRepository.findByCategoryIdAndNameContainingIgnoreCaseAndIsAvailableTrue(categoryId, search.trim()).stream()
                    .map(this::mapToItemResponse).toList();
        }
        return itemRepository.findByCategoryId(categoryId).stream()
                .map(this::mapToItemResponse).toList();
    }

    // New (Highly Recommended for POS Systems): Global item search
    @Transactional(readOnly = true)
    public List<ItemResponse> getAllAvailableItems(String search) {
        if (search != null && !search.trim().isEmpty()) {
            return itemRepository.findByNameContainingIgnoreCaseAndIsAvailableTrue(search.trim()).stream()
                    .map(this::mapToItemResponse).toList();
        }

        // If no search term is provided, you might want to return all items or limit it.
        // For now, assuming you have a findAllByIsAvailableTrue() in repository, or just return empty.
        throw new IllegalArgumentException("Search term is required for global item search");
    }
}
