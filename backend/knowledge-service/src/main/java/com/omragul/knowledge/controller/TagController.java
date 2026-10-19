package com.omragul.knowledge.controller;

import com.omragul.knowledge.dto.TagResponse;
import com.omragul.knowledge.entity.Tag;
import com.omragul.knowledge.mapper.TagMapper;
import com.omragul.knowledge.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tags")
@io.swagger.v3.oas.annotations.tags.Tag(
        name = "Tag",
        description = "APIs for managing document tags"
)
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    // =========================================================
    // CREATE TAG
    // =========================================================

    @PostMapping
    @Operation(
            summary = "Create tag",
            description = "Creates a new tag inside a knowledge base"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Tag created successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request"
            )
    })
    public ResponseEntity<TagResponse> createTag(

            @Parameter(
                    description = "Knowledge base ID",
                    required = true
            )
            @RequestParam UUID knowledgeBaseId,

            @Parameter(
                    description = "Tag name",
                    required = true
            )
            @RequestParam String name,

            @Parameter(
                    description = "Tag description"
            )
            @RequestParam(required = false)
            String description,

            @Parameter(
                    description = "Tag color"
            )
            @RequestParam(required = false)
            String color,

            @Parameter(
                    description = "ID of the user creating the tag",
                    required = true
            )
            @RequestHeader("X-User-Id")
            UUID userId
    ) {

        Tag tag = new Tag();

        tag.setKnowledgeBaseId(
                knowledgeBaseId
        );

        tag.setName(name);
        tag.setDescription(description);
        tag.setColor(color);

        Tag created =
                tagService.create(
                        tag,
                        userId
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        TagMapper.toResponse(created)
                );
    }

    // =========================================================
    // GET TAG BY ID
    // =========================================================

    @GetMapping("/{id}")
    @Operation(
            summary = "Get tag",
            description = "Returns a tag by its ID"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tag found"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Tag not found"
            )
    })
    public ResponseEntity<TagResponse> getTagById(

            @Parameter(
                    description = "Tag ID",
                    required = true
            )
            @PathVariable UUID id
    ) {

        Tag tag =
                tagService.getById(id);

        return ResponseEntity.ok(
                TagMapper.toResponse(tag)
        );
    }

    // =========================================================
    // GET TAGS BY KNOWLEDGE BASE
    // =========================================================

    @GetMapping("/knowledge-base/{knowledgeBaseId}")
    @Operation(
            summary = "Get tags by knowledge base",
            description = "Returns all tags belonging to a knowledge base"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tags retrieved successfully"
            )
    })
    public ResponseEntity<List<TagResponse>>
    getTagsByKnowledgeBase(

            @Parameter(
                    description = "Knowledge base ID",
                    required = true
            )
            @PathVariable UUID knowledgeBaseId
    ) {

        List<Tag> tags =
                tagService.getByKnowledgeBase(
                        knowledgeBaseId
                );

        return ResponseEntity.ok(
                TagMapper.toResponseList(tags)
        );
    }

    // =========================================================
    // GET TAG BY NAME
    // =========================================================

    @GetMapping("/knowledge-base/{knowledgeBaseId}/by-name")
    @Operation(
            summary = "Get tag by name",
            description = "Returns a tag by name within a knowledge base"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tag found"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Tag not found"
            )
    })
    public ResponseEntity<TagResponse>
    getTagByName(

            @Parameter(
                    description = "Knowledge base ID",
                    required = true
            )
            @PathVariable UUID knowledgeBaseId,

            @Parameter(
                    description = "Tag name",
                    required = true
            )
            @RequestParam String name
    ) {

        Tag tag =
                tagService.getByName(
                        knowledgeBaseId,
                        name
                );

        return ResponseEntity.ok(
                TagMapper.toResponse(tag)
        );
    }

    // =========================================================
    // UPDATE TAG
    // =========================================================

    @PutMapping("/{id}")
    @Operation(
            summary = "Update tag",
            description = "Updates tag details"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tag updated successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Tag not found"
            )
    })
    public ResponseEntity<TagResponse>
    updateTag(

            @Parameter(
                    description = "Tag ID",
                    required = true
            )
            @PathVariable UUID id,

            @Parameter(
                    description = "New tag name"
            )
            @RequestParam(required = false)
            String name,

            @Parameter(
                    description = "Tag description"
            )
            @RequestParam(required = false)
            String description,

            @Parameter(
                    description = "Tag color"
            )
            @RequestParam(required = false)
            String color,

            @Parameter(
                    description = "ID of the user performing the update",
                    required = true
            )
            @RequestHeader("X-User-Id")
            UUID userId
    ) {

        Tag tag =
                tagService.update(
                        id,
                        name,
                        description,
                        color,
                        userId
                );

        return ResponseEntity.ok(
                TagMapper.toResponse(tag)
        );
    }

    // =========================================================
    // DELETE TAG
    // =========================================================

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete tag",
            description = "Deletes an existing tag"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Tag deleted successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Tag not found"
            )
    })
    public ResponseEntity<Void> deleteTag(

            @Parameter(
                    description = "Tag ID",
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

        tagService.delete(
                id,
                userId
        );

        return ResponseEntity.noContent().build();
    }
}