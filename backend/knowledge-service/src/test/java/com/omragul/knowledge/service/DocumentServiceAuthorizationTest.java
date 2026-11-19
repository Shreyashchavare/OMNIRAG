package com.omragul.knowledge.service;

import com.omragul.knowledge.entity.Category;
import com.omragul.knowledge.entity.Document;
import com.omragul.knowledge.entity.DocumentPermission;
import com.omragul.knowledge.entity.Folder;
import com.omragul.knowledge.entity.KnowledgeBase;
import com.omragul.knowledge.exception.ResourceNotFoundException;
import com.omragul.knowledge.exception.UnauthorizedException;
import com.omragul.knowledge.repository.CategoryRepository;
import com.omragul.knowledge.repository.DocumentRepository;
import com.omragul.knowledge.repository.FolderRepository;
import com.omragul.knowledge.repository.KnowledgeBaseRepository;
import com.omragul.knowledge.service.impl.DocumentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceAuthorizationTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private DocumentPermissionService documentPermissionService;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private DocumentServiceImpl documentService;

    private UUID ownerId;
    private UUID attackerId;
    private UUID kbId;
    private UUID docId;
    private KnowledgeBase privateKb;
    private Document testDoc;

    @BeforeEach
    void setUp() {
        documentService = new DocumentServiceImpl(
                documentRepository,
                knowledgeBaseRepository,
                documentPermissionService,
                folderRepository,
                categoryRepository
        );

        ownerId = UUID.randomUUID();
        attackerId = UUID.randomUUID();
        kbId = UUID.randomUUID();
        docId = UUID.randomUUID();

        privateKb = new KnowledgeBase();
        privateKb.setId(kbId);
        privateKb.setName("Private KB");
        privateKb.setOwnerId(ownerId);
        privateKb.setVisibility(KnowledgeBase.Visibility.PRIVATE);

        testDoc = new Document();
        testDoc.setId(docId);
        testDoc.setName("Confidential.pdf");
        testDoc.setKnowledgeBaseId(kbId);
        testDoc.setOwnerId(ownerId);
        testDoc.setStatus(Document.Status.ACTIVE);
        testDoc.setProcessingStatus(Document.ProcessingStatus.COMPLETED);
    }

    @Test
    @DisplayName("Create document - sets ownerId to authenticated userId, not client value")
    void testCreateDocument_SetsAuthenticatedOwner() {
        when(knowledgeBaseRepository.findById(kbId)).thenReturn(Optional.of(privateKb));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Document inputDoc = new Document();
        inputDoc.setKnowledgeBaseId(kbId);
        inputDoc.setName("New Doc");
        inputDoc.setOwnerId(attackerId); // Client tries to forge another owner

        Document created = documentService.create(inputDoc, ownerId);

        assertNotNull(created);
        assertEquals(ownerId, created.getOwnerId(), "Document owner must match the authenticated userId");
        verify(documentRepository).save(inputDoc);
    }

    @Test
    @DisplayName("Create document - non-owner cannot create in private knowledge base")
    void testCreateDocument_RejectsUnauthorizedUserInPrivateKb() {
        when(knowledgeBaseRepository.findById(kbId)).thenReturn(Optional.of(privateKb));

        Document inputDoc = new Document();
        inputDoc.setKnowledgeBaseId(kbId);
        inputDoc.setName("Intruder Doc");

        assertThrows(UnauthorizedException.class, () ->
                documentService.create(inputDoc, attackerId)
        );
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Get by ID - allows document owner")
    void testGetById_AllowsOwner() {
        when(documentRepository.findById(docId)).thenReturn(Optional.of(testDoc));

        Document result = documentService.getById(docId, ownerId);

        assertNotNull(result);
        assertEquals(docId, result.getId());
    }

    @Test
    @DisplayName("Get by ID - allows internal service call with isInternalService=true")
    void testGetById_AllowsInternalServiceCall() {
        when(documentRepository.findById(docId)).thenReturn(Optional.of(testDoc));

        Document result = documentService.getById(docId, null, true);

        assertNotNull(result);
        assertEquals(docId, result.getId());
    }

    @Test
    @DisplayName("Get by ID - rejects unauthenticated external call with null userId")
    void testGetById_RejectsUnauthenticatedNullUser() {
        when(documentRepository.findById(docId)).thenReturn(Optional.of(testDoc));

        assertThrows(UnauthorizedException.class, () ->
                documentService.getById(docId, null, false)
        );
    }

    @Test
    @DisplayName("Get by ID - denies unauthorized user without permissions on private KB")
    void testGetById_DeniesUnauthorizedUser() {
        when(documentRepository.findById(docId)).thenReturn(Optional.of(testDoc));
        when(documentPermissionService.hasPermission(docId, attackerId, DocumentPermission.Permission.READ)).thenReturn(false);
        when(documentPermissionService.hasPermission(docId, attackerId, DocumentPermission.Permission.WRITE)).thenReturn(false);
        when(documentPermissionService.hasPermission(docId, attackerId, DocumentPermission.Permission.OWNER)).thenReturn(false);
        when(knowledgeBaseRepository.findById(kbId)).thenReturn(Optional.of(privateKb));

        assertThrows(UnauthorizedException.class, () ->
                documentService.getById(docId, attackerId)
        );
    }

    @Test
    @DisplayName("Get by ID - allows user with READ permission")
    void testGetById_AllowsUserWithPermission() {
        when(documentRepository.findById(docId)).thenReturn(Optional.of(testDoc));
        when(documentPermissionService.hasPermission(docId, attackerId, DocumentPermission.Permission.READ)).thenReturn(true);

        Document result = documentService.getById(docId, attackerId);

        assertNotNull(result);
        assertEquals(docId, result.getId());
    }

    @Test
    @DisplayName("Get by ID - throws ResourceNotFoundException for non-existent document")
    void testGetById_ThrowsNotFound() {
        UUID nonExistentId = UUID.randomUUID();
        when(documentRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                documentService.getById(nonExistentId, ownerId)
        );
    }

    @Test
    @DisplayName("Update document - denies unauthorized user (BOLA/IDOR protection)")
    void testUpdateDocument_DeniesUnauthorizedUser() {
        when(documentRepository.findById(docId)).thenReturn(Optional.of(testDoc));
        when(documentPermissionService.hasPermission(docId, attackerId, DocumentPermission.Permission.WRITE)).thenReturn(false);
        when(documentPermissionService.hasPermission(docId, attackerId, DocumentPermission.Permission.OWNER)).thenReturn(false);
        when(knowledgeBaseRepository.findById(kbId)).thenReturn(Optional.of(privateKb));

        Document updateData = new Document();
        updateData.setName("Hacked Name");

        assertThrows(UnauthorizedException.class, () ->
                documentService.update(docId, updateData, attackerId)
        );
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Update document - allows owner")
    void testUpdateDocument_AllowsOwner() {
        when(documentRepository.findById(docId)).thenReturn(Optional.of(testDoc));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Document updateData = new Document();
        updateData.setName("Updated Title");

        Document updated = documentService.update(docId, updateData, ownerId);

        assertNotNull(updated);
        assertEquals("Updated Title", updated.getName());
        verify(documentRepository).save(any(Document.class));
    }

    @Test
    @DisplayName("Archive document - denies unauthorized user")
    void testArchiveDocument_DeniesUnauthorizedUser() {
        when(documentRepository.findById(docId)).thenReturn(Optional.of(testDoc));
        when(documentPermissionService.hasPermission(docId, attackerId, DocumentPermission.Permission.OWNER)).thenReturn(false);
        when(knowledgeBaseRepository.findById(kbId)).thenReturn(Optional.of(privateKb));

        assertThrows(UnauthorizedException.class, () ->
                documentService.archive(docId, attackerId)
        );
    }

    @Test
    @DisplayName("Archive document - allows owner and sets status ARCHIVED")
    void testArchiveDocument_AllowsOwner() {
        when(documentRepository.findById(docId)).thenReturn(Optional.of(testDoc));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Document archived = documentService.archive(docId, ownerId);

        assertNotNull(archived);
        assertEquals(Document.Status.ARCHIVED, archived.getStatus());
    }

    @Test
    @DisplayName("Restore document - allows owner and sets status ACTIVE")
    void testRestoreDocument_AllowsOwner() {
        testDoc.setStatus(Document.Status.ARCHIVED);
        when(documentRepository.findById(docId)).thenReturn(Optional.of(testDoc));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Document restored = documentService.restore(docId, ownerId);

        assertNotNull(restored);
        assertEquals(Document.Status.ACTIVE, restored.getStatus());
    }

    @Test
    @DisplayName("Soft delete - denies unauthorized user")
    void testSoftDelete_DeniesUnauthorizedUser() {
        when(documentRepository.findById(docId)).thenReturn(Optional.of(testDoc));
        when(documentPermissionService.hasPermission(docId, attackerId, DocumentPermission.Permission.DELETE)).thenReturn(false);
        when(documentPermissionService.hasPermission(docId, attackerId, DocumentPermission.Permission.OWNER)).thenReturn(false);
        when(knowledgeBaseRepository.findById(kbId)).thenReturn(Optional.of(privateKb));

        assertThrows(UnauthorizedException.class, () ->
                documentService.softDelete(docId, attackerId)
        );
    }

    @Test
    @DisplayName("Soft delete - allows owner and sets status DELETED")
    void testSoftDelete_AllowsOwner() {
        when(documentRepository.findById(docId)).thenReturn(Optional.of(testDoc));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Document deleted = documentService.softDelete(docId, ownerId);

        assertNotNull(deleted);
        assertEquals(Document.Status.DELETED, deleted.getStatus());
        assertNotNull(deleted.getDeletedAt());
    }

    @Test
    @DisplayName("Get by owner - allows matching user")
    void testGetByOwner_AllowsMatchingUser() {
        when(documentRepository.findByOwnerId(ownerId)).thenReturn(List.of(testDoc));

        List<Document> docs = documentService.getByOwner(ownerId, ownerId);

        assertNotNull(docs);
        assertEquals(1, docs.size());
    }

    @Test
    @DisplayName("Get by owner - rejects mismatched user")
    void testGetByOwner_RejectsMismatchedUser() {
        assertThrows(UnauthorizedException.class, () ->
                documentService.getByOwner(ownerId, attackerId)
        );
    }

    @Test
    @DisplayName("Get by folder - checks knowledge base read access")
    void testGetByFolder_EnforcesKbAccess() {
        UUID folderId = UUID.randomUUID();
        Folder folder = new Folder();
        folder.setId(folderId);
        folder.setKnowledgeBaseId(kbId);

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(knowledgeBaseRepository.findById(kbId)).thenReturn(Optional.of(privateKb));

        // Attacker has no access to privateKb
        assertThrows(UnauthorizedException.class, () ->
                documentService.getByFolder(folderId, attackerId)
        );

        // Owner has access
        when(documentRepository.findByFolderId(folderId)).thenReturn(List.of(testDoc));
        List<Document> docs = documentService.getByFolder(folderId, ownerId);
        assertNotNull(docs);
        assertEquals(1, docs.size());
    }

    @Test
    @DisplayName("Get by category - checks knowledge base read access")
    void testGetByCategory_EnforcesKbAccess() {
        UUID categoryId = UUID.randomUUID();
        Category category = new Category();
        category.setId(categoryId);
        category.setKnowledgeBaseId(kbId);

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(knowledgeBaseRepository.findById(kbId)).thenReturn(Optional.of(privateKb));

        // Attacker has no access to privateKb
        assertThrows(UnauthorizedException.class, () ->
                documentService.getByCategory(categoryId, attackerId)
        );

        // Owner has access
        when(documentRepository.findByCategoryId(categoryId)).thenReturn(List.of(testDoc));
        List<Document> docs = documentService.getByCategory(categoryId, ownerId);
        assertNotNull(docs);
        assertEquals(1, docs.size());
    }

    @Test
    @DisplayName("Get by KB and status - checks knowledge base read access")
    void testGetByKbAndStatus_EnforcesKbAccess() {
        when(knowledgeBaseRepository.findById(kbId)).thenReturn(Optional.of(privateKb));

        assertThrows(UnauthorizedException.class, () ->
                documentService.getByKnowledgeBaseAndStatus(kbId, Document.Status.ACTIVE, attackerId)
        );

        when(documentRepository.findByKnowledgeBaseIdAndStatus(kbId, Document.Status.ACTIVE)).thenReturn(List.of(testDoc));
        List<Document> docs = documentService.getByKnowledgeBaseAndStatus(kbId, Document.Status.ACTIVE, ownerId);
        assertNotNull(docs);
        assertEquals(1, docs.size());
    }
}
