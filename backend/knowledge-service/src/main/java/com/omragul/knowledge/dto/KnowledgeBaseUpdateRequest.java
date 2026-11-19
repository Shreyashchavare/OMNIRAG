package com.omragul.knowledge.dto;

import jakarta.validation.constraints.Size;

public class KnowledgeBaseUpdateRequest {

    @Size(max = 255, message = "Knowledge base name must not exceed 255 characters")
    private String name;

    private String description;

    @Size(max = 200, message = "Slug must not exceed 200 characters")
    private String slug;

    public KnowledgeBaseUpdateRequest() {
    }

    public KnowledgeBaseUpdateRequest(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public KnowledgeBaseUpdateRequest(String name, String description, String slug) {
        this.name = name;
        this.description = description;
        this.slug = slug;
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

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }
}
