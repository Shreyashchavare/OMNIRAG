package com.omragul.knowledge.service;

import com.omragul.knowledge.entity.KnowledgeBase;
import com.omragul.knowledge.exception.DuplicateResourceException;
import com.omragul.knowledge.exception.ResourceNotFoundException;
import com.omragul.knowledge.exception.UnauthorizedException;
import com.omragul.knowledge.repository.KnowledgeBaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @InjectMocks
    private KnowledgeBaseService knowledgeBaseService;

    private UUID ownerId;
    private UUID attackerId;
    private UUID kbId;
    private KnowledgeBase privateKb;
    private KnowledgeBase publicKb;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        attackerId = UUID.randomUUID();
        kbId = UUID.randomUUID();

        privateKb = new KnowledgeBase();
        privateKb.setId(kbId);
        privateKb.setName("Private KB");
        privateKb.setSlug("private-kb");
        privateKb.setOwnerId(ownerId);
        privateKb.setVisibility(KnowledgeBase.Visibility.PRIVATE);
        privateKb.setStatus(KnowledgeBase.Status.ACTIVE);

        publicKb = new KnowledgeBase();
        publicKb.setId(UUID.randomUUID());
        publicKb.setName("Public KB");
        publicKb.setSlug("public-kb");
        publicKb.setOwnerId(ownerId);
        publicKb.setVisibility(KnowledgeBase.Visibility.PUBLIC);
        publicKb.setStatus(KnowledgeBase.Status.ACTIVE);
    }

    @Test
    @DisplayName("Create KB - assigns authenticated userId as owner and creator")
    void testCreate_AssignsAuthenticatedUser() {
        KnowledgeBase input = new KnowledgeBase();
        input.setName("New KB");
        input.setSlug("new-kb");

        when(knowledgeBaseRepository.existsBySlug("new-kb")).thenReturn(false);
        when(knowledgeBaseRepository.existsByNameAndOwnerId("New KB", ownerId)).thenReturn(false);
        when(knowledgeBaseRepository.save(any(KnowledgeBase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeBase created = knowledgeBaseService.create(input, ownerId);

        assertNotNull(created);
        assertEquals(ownerId, created.getOwnerId());
        assertEquals(ownerId, created.getCreatedBy());
        assertEquals(KnowledgeBase.Status.ACTIVE, created.getStatus());
    }

    @Test
    @DisplayName("Get by ID - allows owner for private KB")
    void testGetById_AllowsOwner() {
        when(knowledgeBaseRepository.findById(kbId)).thenReturn(Optional.of(privateKb));

        KnowledgeBase result = knowledgeBaseService.getById(kbId, ownerId);

        assertNotNull(result);
        assertEquals(kbId, result.getId());
    }

    @Test
    @DisplayName("Get by ID - denies non-owner for private KB")
    void testGetById_DeniesNonOwnerPrivate() {
        when(knowledgeBaseRepository.findById(kbId)).thenReturn(Optional.of(privateKb));

        assertThrows(UnauthorizedException.class, () ->
                knowledgeBaseService.getById(kbId, attackerId)
        );
    }

    @Test
    @DisplayName("Get by ID - allows non-owner for public KB")
    void testGetById_AllowsNonOwnerPublic() {
        when(knowledgeBaseRepository.findById(publicKb.getId())).thenReturn(Optional.of(publicKb));

        KnowledgeBase result = knowledgeBaseService.getById(publicKb.getId(), attackerId);

        assertNotNull(result);
        assertEquals(publicKb.getId(), result.getId());
    }

    @Test
    @DisplayName("Get by ID - throws UnauthorizedException when userId is null")
    void testGetById_RejectsNullUser() {
        assertThrows(UnauthorizedException.class, () ->
                knowledgeBaseService.getById(kbId, null)
        );
    }

    @Test
    @DisplayName("Get by slug - allows owner for private KB")
    void testGetBySlug_AllowsOwner() {
        when(knowledgeBaseRepository.findBySlug("private-kb")).thenReturn(Optional.of(privateKb));

        KnowledgeBase result = knowledgeBaseService.getBySlug("private-kb", ownerId);

        assertNotNull(result);
        assertEquals(kbId, result.getId());
    }

    @Test
    @DisplayName("Get by slug - denies non-owner for private KB")
    void testGetBySlug_DeniesNonOwnerPrivate() {
        when(knowledgeBaseRepository.findBySlug("private-kb")).thenReturn(Optional.of(privateKb));

        assertThrows(UnauthorizedException.class, () ->
                knowledgeBaseService.getBySlug("private-kb", attackerId)
        );
    }

    @Test
    @DisplayName("Get by slug - throws UnauthorizedException when userId is null")
    void testGetBySlug_RejectsNullUser() {
        assertThrows(UnauthorizedException.class, () ->
                knowledgeBaseService.getBySlug("private-kb", null)
        );
    }

    @Test
    @DisplayName("Update KB - denies non-owner")
    void testUpdate_DeniesNonOwner() {
        when(knowledgeBaseRepository.findById(kbId)).thenReturn(Optional.of(privateKb));

        assertThrows(UnauthorizedException.class, () ->
                knowledgeBaseService.update(kbId, "Hacked", null, null, attackerId)
        );
    }

    @Test
    @DisplayName("Archive KB - denies non-owner")
    void testArchive_DeniesNonOwner() {
        when(knowledgeBaseRepository.findById(kbId)).thenReturn(Optional.of(privateKb));

        assertThrows(UnauthorizedException.class, () ->
                knowledgeBaseService.archive(kbId, attackerId)
        );
    }

    @Test
    @DisplayName("Restore KB - allows owner and rejects non-owner and null user")
    void testRestore_AuthorizationChecks() {
        privateKb.setStatus(KnowledgeBase.Status.ARCHIVED);
        when(knowledgeBaseRepository.findById(kbId)).thenReturn(Optional.of(privateKb));
        when(knowledgeBaseRepository.save(any(KnowledgeBase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Rejects null userId
        assertThrows(UnauthorizedException.class, () ->
                knowledgeBaseService.restore(kbId, null)
        );

        // Rejects non-owner
        assertThrows(UnauthorizedException.class, () ->
                knowledgeBaseService.restore(kbId, attackerId)
        );

        // Allows owner
        KnowledgeBase restored = knowledgeBaseService.restore(kbId, ownerId);
        assertNotNull(restored);
        assertEquals(KnowledgeBase.Status.ACTIVE, restored.getStatus());
    }

    @Test
    @DisplayName("Delete KB - denies non-owner")
    void testDelete_DeniesNonOwner() {
        when(knowledgeBaseRepository.findById(kbId)).thenReturn(Optional.of(privateKb));

        assertThrows(UnauthorizedException.class, () ->
                knowledgeBaseService.delete(kbId, attackerId)
        );
    }

    @Test
    @DisplayName("Get by ID - soft-deleted KB throws ResourceNotFoundException even for owner")
    void testGetById_SoftDeletedKbThrowsResourceNotFound() {
        privateKb.setStatus(KnowledgeBase.Status.DELETED);
        when(knowledgeBaseRepository.findById(kbId)).thenReturn(Optional.of(privateKb));

        assertThrows(ResourceNotFoundException.class, () ->
                knowledgeBaseService.getById(kbId, ownerId)
        );
    }
}
