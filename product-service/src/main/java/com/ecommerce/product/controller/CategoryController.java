package com.ecommerce.product.controller;

import com.ecommerce.product.dto.CategoryResponse;
import com.ecommerce.product.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for category hierarchy.
 * Requirements: 3.8
 */
@RestController
@RequestMapping("/api/products/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * GET /api/products/categories - Returns all categories as a hierarchical tree.
     */
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        return ResponseEntity.ok(categoryService.getAllCategoriesHierarchical());
    }
}
