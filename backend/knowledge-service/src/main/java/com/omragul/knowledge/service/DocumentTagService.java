package com.omragul.knowledge.service;

import com.omragul.knowledge.entity.DocumentTag;

import java.util.List;
import java.util.UUID;

public interface DocumentTagService {

    DocumentTag addTag(
            UUID documentId,
            UUID tagId,
            UUID taggedBy
    );

    List<DocumentTag> getByDocumentId(
            UUID documentId
    );

    List<DocumentTag> getByTagId(
            UUID tagId
    );

    boolean exists(
            UUID documentId,
            UUID tagId
    );

    void removeTag(
            UUID documentId,
            UUID tagId
    );

    void removeAllTagsFromDocument(
            UUID documentId
    );

    void removeAllDocumentsFromTag(
            UUID tagId
    );
}