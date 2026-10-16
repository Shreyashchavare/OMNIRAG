package com.omragul.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class FolderUpdateRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    public FolderUpdateRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}