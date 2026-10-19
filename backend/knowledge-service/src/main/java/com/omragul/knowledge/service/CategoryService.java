package com.omragul.knowledge.service;

import com.omragul.knowledge.entity.Category;
import com.omragul.knowledge.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(
            CategoryRepository categoryRepository
    ) {
        this.categoryRepository = categoryRepository;
    }

    public Category create(
            Category category,
            UUID userId
    ) {

        validateCategory(category);
        validateUserId(userId);

        UUID knowledgeBaseId =
                category.getKnowledgeBaseId();

        UUID parentCategoryId =
                category.getParentCategoryId();

        String name =
                category.getName().trim();

        validateDuplicateName(
                knowledgeBaseId,
                name
        );

        if (parentCategoryId != null) {

            Category parent =
                    getActiveById(parentCategoryId);

            validateSameKnowledgeBase(
                    knowledgeBaseId,
                    parent
            );

            validateNoCircularReference(
                    category,
                    parent
            );
        }

        LocalDateTime now =
                LocalDateTime.now();

        category.setName(name);

        category.setCreatedBy(userId);
        category.setUpdatedBy(userId);

        category.setCreatedAt(now);
        category.setUpdatedAt(now);

        category.setStatus(
                Category.Status.ACTIVE
        );

        category.setVersion(1);

        return categoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    public Category getById(UUID id) {

        validateId(id, "Category ID");

        return categoryRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Category not found: " + id
                        )
                );
    }

    @Transactional(readOnly = true)
    public Category getActiveById(UUID id) {

        Category category = getById(id);

        if (category.getStatus()
                != Category.Status.ACTIVE) {

            throw new IllegalArgumentException(
                    "Category is not active"
            );
        }

        return category;
    }

    @Transactional(readOnly = true)
    public List<Category> getByKnowledgeBase(
            UUID knowledgeBaseId
    ) {

        validateId(
                knowledgeBaseId,
                "Knowledge base ID"
        );

        return categoryRepository
                .findByKnowledgeBaseIdAndStatus(
                        knowledgeBaseId,
                        Category.Status.ACTIVE
                );
    }

    @Transactional(readOnly = true)
    public List<Category> getRootCategories(
            UUID knowledgeBaseId
    ) {

        validateId(
                knowledgeBaseId,
                "Knowledge base ID"
        );

        return categoryRepository
                .findByKnowledgeBaseIdAndParentCategoryIdIsNull(
                        knowledgeBaseId
                )
                .stream()
                .filter(category ->
                        category.getStatus()
                                == Category.Status.ACTIVE
                )
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Category> getChildren(
            UUID parentCategoryId
    ) {

        validateId(
                parentCategoryId,
                "Parent category ID"
        );

        return categoryRepository
                .findByParentCategoryId(
                        parentCategoryId
                )
                .stream()
                .filter(category ->
                        category.getStatus()
                                == Category.Status.ACTIVE
                )
                .toList();
    }

    @Transactional(readOnly = true)
    public Category getByName(
            UUID knowledgeBaseId,
            String name
    ) {

        validateId(
                knowledgeBaseId,
                "Knowledge base ID"
        );

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Category name is required"
            );
        }

        return categoryRepository
                .findByKnowledgeBaseIdAndName(
                        knowledgeBaseId,
                        name.trim()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Category not found: "
                                        + name
                        )
                );
    }

    public Category update(
            UUID id,
            String name,
            String description,
            String color,
            String icon,
            UUID parentCategoryId,
            UUID userId
    ) {

        validateId(id, "Category ID");
        validateUserId(userId);

        Category category =
                getActiveById(id);

        validateDuplicateNameForUpdate(
                category,
                name
        );

        if (name != null && !name.isBlank()) {
            category.setName(
                    name.trim()
            );
        }

        if (description != null) {
            category.setDescription(
                    description
            );
        }

        if (color != null) {
            category.setColor(color);
        }

        if (icon != null) {
            category.setIcon(icon);
        }

        if (parentCategoryId != null) {

            if (category.getId()
                    .equals(parentCategoryId)) {

                throw new IllegalArgumentException(
                        "Category cannot be its own parent"
                );
            }

            Category parent =
                    getActiveById(
                            parentCategoryId
                    );

            validateSameKnowledgeBase(
                    category.getKnowledgeBaseId(),
                    parent
            );

            validateNoCircularReference(
                    category,
                    parent
            );

            category.setParentCategoryId(
                    parentCategoryId
            );
        }

        category.setUpdatedBy(userId);
        category.setUpdatedAt(
                LocalDateTime.now()
        );

        category.setVersion(
                category.getVersion() + 1
        );

        return categoryRepository.save(category);
    }

    public Category archive(
            UUID id,
            UUID userId
    ) {

        validateId(id, "Category ID");
        validateUserId(userId);

        Category category =
                getActiveById(id);

        validateOwnership(
                category,
                userId
        );

        category.setStatus(
                Category.Status.ARCHIVED
        );

        category.setUpdatedBy(userId);
        category.setUpdatedAt(
                LocalDateTime.now()
        );

        category.setVersion(
                category.getVersion() + 1
        );

        return categoryRepository.save(category);
    }

    public Category delete(
            UUID id,
            UUID userId
    ) {

        validateId(id, "Category ID");
        validateUserId(userId);

        Category category =
                getActiveById(id);

        validateOwnership(
                category,
                userId
        );

        category.setStatus(
                Category.Status.DELETED
        );

        category.setDeletedAt(
                LocalDateTime.now()
        );

        category.setUpdatedBy(userId);
        category.setUpdatedAt(
                LocalDateTime.now()
        );

        category.setVersion(
                category.getVersion() + 1
        );

        return categoryRepository.save(category);
    }

    private void validateCategory(
            Category category
    ) {

        if (category == null) {
            throw new IllegalArgumentException(
                    "Category cannot be null"
            );
        }

        validateId(
                category.getKnowledgeBaseId(),
                "Knowledge base ID"
        );

        if (category.getName() == null
                || category.getName().isBlank()) {

            throw new IllegalArgumentException(
                    "Category name is required"
            );
        }
    }

    private void validateUserId(
            UUID userId
    ) {

        validateId(userId, "User ID");
    }

    private void validateId(
            UUID id,
            String field
    ) {

        if (id == null) {
            throw new IllegalArgumentException(
                    field + " cannot be null"
            );
        }
    }

    private void validateDuplicateName(
            UUID knowledgeBaseId,
            String name
    ) {

        if (categoryRepository
                .existsByKnowledgeBaseIdAndName(
                        knowledgeBaseId,
                        name
                )) {

            throw new IllegalArgumentException(
                    "Category with this name already exists"
            );
        }
    }

    private void validateDuplicateNameForUpdate(
            Category category,
            String name
    ) {

        if (name == null
                || name.isBlank()
                || name.trim().equals(
                category.getName()
        )) {

            return;
        }

        if (categoryRepository
                .existsByKnowledgeBaseIdAndName(
                        category.getKnowledgeBaseId(),
                        name.trim()
                )) {

            throw new IllegalArgumentException(
                    "Category with this name already exists"
            );
        }
    }

    private void validateSameKnowledgeBase(
            UUID knowledgeBaseId,
            Category parent
    ) {

        if (!knowledgeBaseId.equals(
                parent.getKnowledgeBaseId()
        )) {

            throw new IllegalArgumentException(
                    "Parent category belongs to another "
                            + "knowledge base"
            );
        }
    }

    private void validateOwnership(
            Category category,
            UUID userId
    ) {

        /*
         * Category does not currently have an owner_id.
         *
         * Ownership is therefore expected to be validated
         * through the KnowledgeBase/authorization layer.
         */
    }

    private void validateNoCircularReference(
            Category category,
            Category newParent
    ) {

        Category current =
                newParent;

        while (current != null) {

            if (current.getId()
                    .equals(category.getId())) {

                throw new IllegalArgumentException(
                        "Cannot create a circular "
                                + "category hierarchy"
                );
            }

            UUID parentId =
                    current.getParentCategoryId();

            if (parentId == null) {
                break;
            }

            current =
                    getActiveById(parentId);
        }
    }
}