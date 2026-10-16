package com.omragul.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class CategoryCreateRequest {

    @NotNull
    private UUID knowledgeBaseId;

    private UUID parentCategoryId;

    @NotBlank
    @Size(max = 100)
    private String name;

    private String description;

    @Size(max = 20)
    private String color;

    @Size(max = 100)
    private String icon;

    public CategoryCreateRequest() {
    }

    public UUID getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(UUID knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public UUID getParentCategoryId() {
        return parentCategoryId;
    }

    public void setParentCategoryId(UUID parentCategoryId) {
        this.parentCategoryId = parentCategoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }
}