package com.omragul.knowledge.service;

import com.omragul.knowledge.entity.Tag;
import com.omragul.knowledge.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TagService {

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    public Tag create(
            Tag tag,
            UUID userId
    ) {

        validateTag(tag);
        validateUserId(userId);

        UUID knowledgeBaseId =
                tag.getKnowledgeBaseId();

        String name =
                tag.getName().trim();

        if (tagRepository
                .existsByKnowledgeBaseIdAndName(
                        knowledgeBaseId,
                        name
                )) {

            throw new IllegalArgumentException(
                    "Tag with this name already exists"
            );
        }

        LocalDateTime now =
                LocalDateTime.now();

        tag.setName(name);

        tag.setCreatedBy(userId);
        tag.setUpdatedBy(userId);

        tag.setCreatedAt(now);
        tag.setUpdatedAt(now);

        return tagRepository.save(tag);
    }

    @Transactional(readOnly = true)
    public Tag getById(UUID id) {

        validateId(id, "Tag ID");

        return tagRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Tag not found: " + id
                        )
                );
    }

    @Transactional(readOnly = true)
    public List<Tag> getByKnowledgeBase(
            UUID knowledgeBaseId
    ) {

        validateId(
                knowledgeBaseId,
                "Knowledge base ID"
        );

        return tagRepository
                .findByKnowledgeBaseId(
                        knowledgeBaseId
                );
    }

    @Transactional(readOnly = true)
    public Tag getByName(
            UUID knowledgeBaseId,
            String name
    ) {

        validateId(
                knowledgeBaseId,
                "Knowledge base ID"
        );

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Tag name is required"
            );
        }

        return tagRepository
                .findByKnowledgeBaseIdAndName(
                        knowledgeBaseId,
                        name.trim()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Tag not found: " + name
                        )
                );
    }

    public Tag update(
            UUID id,
            String name,
            String description,
            String color,
            UUID userId
    ) {

        validateId(id, "Tag ID");
        validateUserId(userId);

        Tag tag = getById(id);

        if (name != null && !name.isBlank()) {

            String newName = name.trim();

            if (!newName.equals(tag.getName())
                    && tagRepository
                    .existsByKnowledgeBaseIdAndName(
                            tag.getKnowledgeBaseId(),
                            newName
                    )) {

                throw new IllegalArgumentException(
                        "Tag with this name already exists"
                );
            }

            tag.setName(newName);
        }

        if (description != null) {
            tag.setDescription(description);
        }

        if (color != null) {
            tag.setColor(color);
        }

        tag.setUpdatedBy(userId);
        tag.setUpdatedAt(
                LocalDateTime.now()
        );

        return tagRepository.save(tag);
    }

    public void delete(
            UUID id,
            UUID userId
    ) {

        validateId(id, "Tag ID");
        validateUserId(userId);

        Tag tag = getById(id);

        /*
         * Authorization will eventually be handled
         * through KnowledgeBase ownership/permissions.
         */

        tagRepository.delete(tag);
    }

    private void validateTag(Tag tag) {

        if (tag == null) {
            throw new IllegalArgumentException(
                    "Tag cannot be null"
            );
        }

        validateId(
                tag.getKnowledgeBaseId(),
                "Knowledge base ID"
        );

        if (tag.getName() == null
                || tag.getName().isBlank()) {

            throw new IllegalArgumentException(
                    "Tag name is required"
            );
        }
    }

    private void validateUserId(UUID userId) {

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
}