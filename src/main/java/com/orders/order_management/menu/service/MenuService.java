package com.orders.order_management.menu.service;


import com.orders.order_management.menu.dto.CategoryRequest;
import com.orders.order_management.menu.dto.CategoryResponse;
import com.orders.order_management.menu.dto.ItemRequest;
import com.orders.order_management.menu.dto.ItemResponse;
import com.orders.order_management.menu.entity.Category;
import com.orders.order_management.menu.entity.Item;
import com.orders.order_management.menu.repository.CategoryRepository;
import com.orders.order_management.menu.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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

    @CacheEvict(value = {"menuCategories", "menuItems"}, allEntries = true)
    public CategoryResponse createCategory(CategoryRequest request) {
        Category category = Category.builder()
                .name(request.getName())
                .isActive(true)
                .build();

        return mapToCategoryResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "menuCategories", key = "'active_all'")
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

    @CacheEvict(value = {"menuCategories", "menuItems"}, allEntries = true)
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        category.setName(request.getName());
        category.setActive(request.getActive());
        return mapToCategoryResponse(categoryRepository.save(category));
    }

    @CacheEvict(value = {"menuCategories", "menuItems"}, allEntries = true)
    public void deleteCategory(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        // Soft delete: Disable the category instead of removing it from the DB
        category.setActive(false);
        categoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "menuCategories", key = "'active_' + (#search != null ? #search.trim().toLowerCase() : '')")
    public List<CategoryResponse> getActiveCategories(String search) {
        if (search != null && !search.trim().isEmpty()) {
            return categoryRepository.findByNameContainingIgnoreCaseAndIsActive(search.trim(), true).stream()
                    .map(this::mapToCategoryResponse).toList();
        }
        return categoryRepository.findAllByIsActiveTrue().stream()
                .map(this::mapToCategoryResponse).toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "menuCategories", key = "'all_' + #active + '_' + (#search != null ? #search.trim().toLowerCase() : '')")
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

    @CacheEvict(value = {"menuItems", "menuCategories"}, allEntries = true)
    public ItemResponse createItem(ItemRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        Item item = Item.builder()
                .category(category)
                .name(request.getName())
                .description(request.getDescription())
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
    @Cacheable(value = "menuItems", key = "'cat_' + #categoryId")
    public List<ItemResponse> getItemsByCategory(UUID categoryId) {
        // Assumes you added findByCategoryIdAndIsAvailableTrue to ItemRepository
        return itemRepository.findByCategoryIdAndIsAvailableTrue(categoryId).stream()
                .map(this::mapToItemResponse).toList();
    }

    @CacheEvict(value = {"menuItems", "menuCategories"}, allEntries = true)
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
        item.setDescription(request.getDescription());
        item.setPrice(request.getPrice());
        item.setAvailable(request.isAvailable());
        return mapToItemResponse(itemRepository.save(item));
    }

    @CacheEvict(value = {"menuItems", "menuCategories"}, allEntries = true)
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
                .description(item.getDescription())
                .price(item.getPrice())
                .isAvailable(item.isAvailable())
                .build();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "menuItems", key = "'cat_' + #categoryId + '_' + (#search != null ? #search.trim().toLowerCase() : '')")
    public List<ItemResponse> getItemsByCategory(UUID categoryId, String search) {
        if (search != null && !search.trim().isEmpty()) {
            return itemRepository.findByCategoryIdAndNameContainingIgnoreCaseAndIsAvailableTrue(categoryId, search.trim()).stream()
                .map(this::mapToItemResponse).toList();
        }
        return itemRepository.findByCategoryId(categoryId).stream()
                .map(this::mapToItemResponse).toList();
    }

    // Global item search (returns all available items if search is empty)
    @Transactional(readOnly = true)
    @Cacheable(value = "menuItems", key = "'all_' + (#search != null ? #search.trim().toLowerCase() : '')")
    public List<ItemResponse> getAllAvailableItems(String search) {
        if (search != null && !search.trim().isEmpty()) {
            return itemRepository.findByNameContainingIgnoreCaseAndIsAvailableTrue(search.trim()).stream()
                    .map(this::mapToItemResponse).toList();
        }
        return itemRepository.findByIsAvailableTrue().stream()
                .map(this::mapToItemResponse).toList();
    }
}
