package com.omragul.knowledge.controller;

import com.omragul.knowledge.dto.KnowledgeBaseCreateRequest;
import com.omragul.knowledge.dto.KnowledgeBaseResponse;
import com.omragul.knowledge.dto.KnowledgeBaseStatisticsResponse;
import com.omragul.knowledge.dto.KnowledgeBaseUpdateRequest;
import com.omragul.knowledge.entity.KnowledgeBase;
import com.omragul.knowledge.mapper.KnowledgeBaseMapper;
import com.omragul.knowledge.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/knowledge-bases")
@Tag(
        name = "Knowledge Base",
        description = "APIs for managing knowledge bases"
)
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(
            KnowledgeBaseService knowledgeBaseService
    ) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping
    public ResponseEntity<KnowledgeBaseResponse> create(
            @Valid @RequestBody KnowledgeBaseCreateRequest request,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setName(request.getName());
        knowledgeBase.setDescription(request.getDescription());
        knowledgeBase.setSlug(request.getSlug());

        KnowledgeBase created =
                knowledgeBaseService.create(
                        knowledgeBase,
                        userId
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        KnowledgeBaseMapper.toResponse(
                                created
                        )
                );
    }

    @GetMapping
    public ResponseEntity<List<KnowledgeBaseResponse>> getByOwner(
            @RequestHeader("X-User-Id") UUID ownerId
    ) {

        List<KnowledgeBase> knowledgeBases =
                knowledgeBaseService.getByOwner(
                        ownerId
                );

        return ResponseEntity.ok(
                KnowledgeBaseMapper.toResponseList(
                        knowledgeBases
                )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<KnowledgeBaseResponse> getById(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId
    ) {

        KnowledgeBase knowledgeBase =
                knowledgeBaseService.getById(id, userId);

        return ResponseEntity.ok(
                KnowledgeBaseMapper.toResponse(
                        knowledgeBase
                )
        );
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<KnowledgeBaseResponse> getBySlug(
            @PathVariable String slug,
            @RequestHeader("X-User-Id") UUID userId
    ) {

        KnowledgeBase knowledgeBase =
                knowledgeBaseService.getBySlug(slug, userId);

        return ResponseEntity.ok(
                KnowledgeBaseMapper.toResponse(
                        knowledgeBase
                )
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<KnowledgeBaseResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) KnowledgeBaseUpdateRequest request,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String slug,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        String targetName = request != null && request.getName() != null ? request.getName() : name;
        String targetDesc = request != null && request.getDescription() != null ? request.getDescription() : description;
        String targetSlug = request != null && request.getSlug() != null ? request.getSlug() : slug;

        KnowledgeBase updated =
                knowledgeBaseService.update(
                        id,
                        targetName,
                        targetDesc,
                        targetSlug,
                        userId
                );

        return ResponseEntity.ok(
                KnowledgeBaseMapper.toResponse(
                        updated
                )
        );
    }

    @PatchMapping("/{id}/archive")
    public ResponseEntity<KnowledgeBaseResponse> archive(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId
    ) {

        KnowledgeBase archived =
                knowledgeBaseService.archive(
                        id,
                        userId
                );

        return ResponseEntity.ok(
                KnowledgeBaseMapper.toResponse(
                        archived
                )
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<KnowledgeBaseResponse> delete(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId
    ) {

        KnowledgeBase deleted =
                knowledgeBaseService.delete(
                        id,
                        userId
                );

        return ResponseEntity.ok(
                KnowledgeBaseMapper.toResponse(
                        deleted
                )
        );
    }

    @GetMapping("/{id}/statistics")
    public ResponseEntity<KnowledgeBaseStatisticsResponse> getStatistics(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId
    ) {

        KnowledgeBase knowledgeBase =
                knowledgeBaseService.getActiveById(id, userId);

        return ResponseEntity.ok(
                KnowledgeBaseMapper.toStatisticsResponse(
                        knowledgeBase
                )
        );
    }

    @PatchMapping("/{id}/restore")
    public ResponseEntity<KnowledgeBaseResponse> restore(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId
    ) {

        KnowledgeBase knowledgeBase = knowledgeBaseService.restore(id, userId);

        return ResponseEntity.ok(
                KnowledgeBaseMapper.toResponse(knowledgeBase)
        );
    }
}