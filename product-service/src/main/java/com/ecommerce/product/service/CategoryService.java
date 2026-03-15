package com.ecommerce.product.service;

import com.ecommerce.product.dto.CategoryResponse;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for hierarchical category management.
 * Requirements: 3.8
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * Returns all root categories with their nested children loaded recursively.
     */
    public List<CategoryResponse> getAllCategoriesHierarchical() {
        List<Category> allCategories = categoryRepository.findAll();
        List<Category> rootCategories = allCategories.stream()
                .filter(c -> c.getParentId() == null)
                .collect(Collectors.toList());

        return rootCategories.stream()
                .map(root -> toCategoryResponse(root, allCategories))
                .collect(Collectors.toList());
    }

    /**
     * Maps a Category entity to a CategoryResponse, recursively building children.
     */
    private CategoryResponse toCategoryResponse(Category category, List<Category> allCategories) {
        List<CategoryResponse> children = allCategories.stream()
                .filter(c -> category.getId().equals(c.getParentId()))
                .map(child -> toCategoryResponse(child, allCategories))
                .collect(Collectors.toList());

        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .parentId(category.getParentId())
                .imageUrl(category.getImageUrl())
                .isActive(category.isActive())
                .children(children)
                .build();
    }
}
