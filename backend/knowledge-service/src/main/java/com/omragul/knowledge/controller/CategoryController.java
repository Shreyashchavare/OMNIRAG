package com.omragul.knowledge.controller;

import com.omragul.knowledge.dto.CategoryResponse;
import com.omragul.knowledge.entity.Category;
import com.omragul.knowledge.mapper.CategoryMapper;
import com.omragul.knowledge.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(
        name = "Category",
        description = "APIs for managing document categories"
)
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(
            CategoryService categoryService
    ) {
        this.categoryService = categoryService;
    }

    // =========================================================
    // CREATE CATEGORY
    // =========================================================

    @PostMapping
    @Operation(
            summary = "Create category",
            description = "Creates a new category inside a knowledge base"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Category created successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request"
            )
    })
    public ResponseEntity<CategoryResponse> createCategory(

            @Parameter(
                    description = "Knowledge base ID",
                    required = true
            )
            @RequestParam UUID knowledgeBaseId,

            @Parameter(
                    description = "Parent category ID. Leave empty for a root category"
            )
            @RequestParam(required = false)
            UUID parentCategoryId,

            @Parameter(
                    description = "Category name",
                    required = true
            )
            @RequestParam String name,

            @Parameter(
                    description = "Category description"
            )
            @RequestParam(required = false)
            String description,

            @Parameter(
                    description = "Category color"
            )
            @RequestParam(required = false)
            String color,

            @Parameter(
                    description = "Category icon"
            )
            @RequestParam(required = false)
            String icon,

            @Parameter(
                    description = "ID of the user creating the category",
                    required = true
            )
            @RequestHeader("X-User-Id")
            UUID userId
    ) {

        Category category = new Category();

        category.setKnowledgeBaseId(
                knowledgeBaseId
        );

        category.setParentCategoryId(
                parentCategoryId
        );

        category.setName(name);
        category.setDescription(description);
        category.setColor(color);
        category.setIcon(icon);

        Category created =
                categoryService.create(
                        category,
                        userId
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        CategoryMapper.toResponse(created)
                );
    }

    // =========================================================
    // GET CATEGORY BY ID
    // =========================================================

    @GetMapping("/{id}")
    @Operation(
            summary = "Get category",
            description = "Returns a category by its ID"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Category found"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Category not found"
            )
    })
    public ResponseEntity<CategoryResponse> getCategoryById(

            @Parameter(
                    description = "Category ID",
                    required = true
            )
            @PathVariable UUID id
    ) {

        Category category =
                categoryService.getById(id);

        return ResponseEntity.ok(
                CategoryMapper.toResponse(category)
        );
    }

    // =========================================================
    // GET CATEGORIES BY KNOWLEDGE BASE
    // =========================================================

    @GetMapping("/knowledge-base/{knowledgeBaseId}")
    @Operation(
            summary = "Get categories by knowledge base",
            description = "Returns all active categories belonging to a knowledge base"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Categories retrieved successfully"
            )
    })
    public ResponseEntity<List<CategoryResponse>>
    getCategoriesByKnowledgeBase(

            @Parameter(
                    description = "Knowledge base ID",
                    required = true
            )
            @PathVariable UUID knowledgeBaseId
    ) {

        List<Category> categories =
                categoryService.getByKnowledgeBase(
                        knowledgeBaseId
                );

        return ResponseEntity.ok(
                CategoryMapper.toResponseList(categories)
        );
    }

    // =========================================================
    // GET ROOT CATEGORIES
    // =========================================================

    @GetMapping("/knowledge-base/{knowledgeBaseId}/root")
    @Operation(
            summary = "Get root categories",
            description = "Returns all root-level categories of a knowledge base"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Root categories retrieved successfully"
            )
    })
    public ResponseEntity<List<CategoryResponse>>
    getRootCategories(

            @Parameter(
                    description = "Knowledge base ID",
                    required = true
            )
            @PathVariable UUID knowledgeBaseId
    ) {

        List<Category> categories =
                categoryService.getRootCategories(
                        knowledgeBaseId
                );

        return ResponseEntity.ok(
                CategoryMapper.toResponseList(categories)
        );
    }

    // =========================================================
    // GET CHILD CATEGORIES
    // =========================================================

    @GetMapping("/{id}/children")
    @Operation(
            summary = "Get child categories",
            description = "Returns all active child categories of a parent category"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Child categories retrieved successfully"
            )
    })
    public ResponseEntity<List<CategoryResponse>>
    getChildren(

            @Parameter(
                    description = "Parent category ID",
                    required = true
            )
            @PathVariable UUID id
    ) {

        List<Category> categories =
                categoryService.getChildren(id);

        return ResponseEntity.ok(
                CategoryMapper.toResponseList(categories)
        );
    }

    // =========================================================
    // GET CATEGORY BY NAME
    // =========================================================

    @GetMapping("/knowledge-base/{knowledgeBaseId}/by-name")
    @Operation(
            summary = "Get category by name",
            description = "Returns a category by name within a knowledge base"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Category found"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Category not found"
            )
    })
    public ResponseEntity<CategoryResponse>
    getCategoryByName(

            @Parameter(
                    description = "Knowledge base ID",
                    required = true
            )
            @PathVariable UUID knowledgeBaseId,

            @Parameter(
                    description = "Category name",
                    required = true
            )
            @RequestParam String name
    ) {

        Category category =
                categoryService.getByName(
                        knowledgeBaseId,
                        name
                );

        return ResponseEntity.ok(
                CategoryMapper.toResponse(category)
        );
    }

    // =========================================================
    // UPDATE CATEGORY
    // =========================================================

    @PutMapping("/{id}")
    @Operation(
            summary = "Update category",
            description = "Updates category details"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Category updated successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Category not found"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "User is not authorized"
            )
    })
    public ResponseEntity<CategoryResponse>
    updateCategory(

            @Parameter(
                    description = "Category ID",
                    required = true
            )
            @PathVariable UUID id,

            @Parameter(
                    description = "Category name"
            )
            @RequestParam(required = false)
            String name,

            @Parameter(
                    description = "Category description"
            )
            @RequestParam(required = false)
            String description,

            @Parameter(
                    description = "Category color"
            )
            @RequestParam(required = false)
            String color,

            @Parameter(
                    description = "Category icon"
            )
            @RequestParam(required = false)
            String icon,

            @Parameter(
                    description = "New parent category ID"
            )
            @RequestParam(required = false)
            UUID parentCategoryId,

            @Parameter(
                    description = "ID of the user performing the update",
                    required = true
            )
            @RequestHeader("X-User-Id")
            UUID userId
    ) {

        Category category =
                categoryService.update(
                        id,
                        name,
                        description,
                        color,
                        icon,
                        parentCategoryId,
                        userId
                );

        return ResponseEntity.ok(
                CategoryMapper.toResponse(category)
        );
    }

    // =========================================================
    // ARCHIVE CATEGORY
    // =========================================================

    @PatchMapping("/{id}/archive")
    @Operation(
            summary = "Archive category",
            description = "Archives an active category"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Category archived successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Category not found"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "User is not authorized"
            )
    })
    public ResponseEntity<CategoryResponse>
    archiveCategory(

            @Parameter(
                    description = "Category ID",
                    required = true
            )
            @PathVariable UUID id,

            @Parameter(
                    description = "ID of the user performing the archive",
                    required = true
            )
            @RequestHeader("X-User-Id")
            UUID userId
    ) {

        Category category =
                categoryService.archive(
                        id,
                        userId
                );

        return ResponseEntity.ok(
                CategoryMapper.toResponse(category)
        );
    }

    // =========================================================
    // DELETE CATEGORY
    // =========================================================

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete category",
            description = "Soft deletes an active category"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Category deleted successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Category not found"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "User is not authorized"
            )
    })
    public ResponseEntity<Void> deleteCategory(

            @Parameter(
                    description = "Category ID",
                    required = true
            )
            @PathVariable UUID id,

            @Parameter(
                    description = "ID of the user performing the deletion",
                    required = true
            )
            @RequestHeader("X-User-Id")
            UUID userId
    ) {

        categoryService.delete(
                id,
                userId
        );

        return ResponseEntity.noContent().build();
    }
}