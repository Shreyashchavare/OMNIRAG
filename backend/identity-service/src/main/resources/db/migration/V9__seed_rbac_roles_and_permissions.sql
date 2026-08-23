-- ============================================================================
-- OMNIRAG Identity Service
-- V9: Seed RBAC roles, permissions and role-permission mappings
-- ============================================================================


-- ============================================================================
-- SECTION 1: Seed Roles
-- ============================================================================

INSERT INTO roles (
    role_id,
    role_name,
    description
)
VALUES
(
    gen_random_uuid(),
    'USER',
    'Standard OMNIRAG user with personalized access and model/RAG querying capabilities'
),
(
    gen_random_uuid(),
    'CONTROLLER',
    'OMNIRAG controller responsible for knowledge base and multimodal RAG management'
),
(
    gen_random_uuid(),
    'ADMIN',
    'OMNIRAG administrator with full system access'
)
ON CONFLICT (role_name) DO NOTHING;


-- ============================================================================
-- SECTION 2: Seed Permissions
-- ============================================================================

INSERT INTO permissions (
    permission_id,
    permission_name,
    description
)
VALUES

-- ==========================
-- USER MANAGEMENT
-- ==========================
(gen_random_uuid(), 'USER_CREATE', 'Create users'),
(gen_random_uuid(), 'USER_READ', 'Read user information'),
(gen_random_uuid(), 'USER_UPDATE', 'Update user information'),
(gen_random_uuid(), 'USER_DELETE', 'Delete users'),

(gen_random_uuid(), 'USER_PROFILE_READ', 'Read user profile'),
(gen_random_uuid(), 'USER_PROFILE_UPDATE', 'Update user profile'),


-- ==========================
-- ROLE MANAGEMENT
-- ==========================
(gen_random_uuid(), 'ROLE_CREATE', 'Create roles'),
(gen_random_uuid(), 'ROLE_READ', 'Read roles'),
(gen_random_uuid(), 'ROLE_UPDATE', 'Update roles'),
(gen_random_uuid(), 'ROLE_DELETE', 'Delete roles'),

(gen_random_uuid(), 'ROLE_ASSIGN', 'Assign roles to users'),
(gen_random_uuid(), 'ROLE_REVOKE', 'Revoke roles from users'),


-- ==========================
-- PERMISSION MANAGEMENT
-- ==========================
(gen_random_uuid(), 'PERMISSION_CREATE', 'Create permissions'),
(gen_random_uuid(), 'PERMISSION_READ', 'Read permissions'),
(gen_random_uuid(), 'PERMISSION_UPDATE', 'Update permissions'),
(gen_random_uuid(), 'PERMISSION_DELETE', 'Delete permissions'),

(gen_random_uuid(), 'PERMISSION_ASSIGN', 'Assign permissions'),
(gen_random_uuid(), 'PERMISSION_REVOKE', 'Revoke permissions'),


-- ==========================
-- DOCUMENT MANAGEMENT
-- ==========================
(gen_random_uuid(), 'DOCUMENT_UPLOAD', 'Upload documents'),
(gen_random_uuid(), 'DOCUMENT_READ', 'Read documents'),
(gen_random_uuid(), 'DOCUMENT_UPDATE', 'Update documents'),
(gen_random_uuid(), 'DOCUMENT_DELETE', 'Delete documents'),

(gen_random_uuid(), 'DOCUMENT_DOWNLOAD', 'Download documents'),


-- ==========================
-- KNOWLEDGE BASE
-- ==========================
(gen_random_uuid(), 'KNOWLEDGE_CREATE', 'Create knowledge bases'),
(gen_random_uuid(), 'KNOWLEDGE_READ', 'Read knowledge bases'),
(gen_random_uuid(), 'KNOWLEDGE_UPDATE', 'Update knowledge bases'),
(gen_random_uuid(), 'KNOWLEDGE_DELETE', 'Delete knowledge bases'),

(gen_random_uuid(), 'KNOWLEDGE_CONTENT_UPLOAD', 'Upload content into knowledge bases'),


-- ==========================
-- EMBEDDINGS
-- ==========================
(gen_random_uuid(), 'EMBEDDING_GENERATE', 'Generate embeddings'),
(gen_random_uuid(), 'EMBEDDING_DELETE', 'Delete embeddings'),


-- ==========================
-- MULTIMODAL QUERY
-- ==========================
(gen_random_uuid(), 'MULTIMODAL_IMAGE_QUERY', 'Submit image as a model query'),
(gen_random_uuid(), 'MULTIMODAL_AUDIO_QUERY', 'Submit audio as a model query'),
(gen_random_uuid(), 'MULTIMODAL_VIDEO_QUERY', 'Submit video as a model query'),


-- ==========================
-- MULTIMODAL PROCESSING
-- ==========================
(gen_random_uuid(), 'IMAGE_PROCESS', 'Process images'),
(gen_random_uuid(), 'AUDIO_PROCESS', 'Process audio'),
(gen_random_uuid(), 'VIDEO_PROCESS', 'Process video'),


-- ==========================
-- RAG
-- ==========================
(gen_random_uuid(), 'RAG_QUERY', 'Execute RAG queries'),


-- ==========================
-- AGENT
-- ==========================
(gen_random_uuid(), 'AGENT_EXECUTE', 'Execute AI agents'),


-- ==========================
-- MODEL
-- ==========================
(gen_random_uuid(), 'MODEL_QUERY', 'Query AI models'),
(gen_random_uuid(), 'MODEL_TEST', 'Test AI models'),
(gen_random_uuid(), 'MODEL_EVALUATE', 'Evaluate AI models'),


-- ==========================
-- ANALYTICS
-- ==========================
(gen_random_uuid(), 'ANALYTICS_READ', 'Read analytics'),


-- ==========================
-- NOTIFICATIONS
-- ==========================
(gen_random_uuid(), 'NOTIFICATION_SEND', 'Send notifications'),


-- ==========================
-- APPROVAL
-- ==========================
(gen_random_uuid(), 'APPROVAL_READ', 'Read approval requests'),
(gen_random_uuid(), 'APPROVAL_APPROVE', 'Approve requests'),
(gen_random_uuid(), 'APPROVAL_REJECT', 'Reject requests'),


-- ==========================
-- SYSTEM
-- ==========================
(gen_random_uuid(), 'SYSTEM_SETTINGS', 'Manage system settings'),
(gen_random_uuid(), 'SYSTEM_MONITOR', 'Monitor system'),
(gen_random_uuid(), 'SYSTEM_BACKUP', 'Perform system backups')

ON CONFLICT (permission_name) DO NOTHING;


-- ============================================================================
-- SECTION 3: USER → Permissions
-- ============================================================================

INSERT INTO role_permissions (
    role_permission_id,
    role_id,
    permission_id
)
SELECT
    gen_random_uuid(),
    r.role_id,
    p.permission_id
FROM roles r
CROSS JOIN permissions p
WHERE r.role_name = 'USER'
AND p.permission_name IN (
    'USER_PROFILE_READ',
    'USER_PROFILE_UPDATE',

    'MULTIMODAL_IMAGE_QUERY',
    'MULTIMODAL_AUDIO_QUERY',
    'MULTIMODAL_VIDEO_QUERY',

    'RAG_QUERY',
    'MODEL_QUERY',
    'AGENT_EXECUTE'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ============================================================================
-- SECTION 4: CONTROLLER → Permissions
-- ============================================================================

INSERT INTO role_permissions (
    role_permission_id,
    role_id,
    permission_id
)
SELECT
    gen_random_uuid(),
    r.role_id,
    p.permission_id
FROM roles r
CROSS JOIN permissions p
WHERE r.role_name = 'CONTROLLER'
AND p.permission_name IN (

    -- USER capabilities
    'USER_PROFILE_READ',
    'USER_PROFILE_UPDATE',

    -- Multimodal queries
    'MULTIMODAL_IMAGE_QUERY',
    'MULTIMODAL_AUDIO_QUERY',
    'MULTIMODAL_VIDEO_QUERY',

    -- RAG / Model / Agent
    'RAG_QUERY',
    'MODEL_QUERY',
    'AGENT_EXECUTE',

    -- Document management
    'DOCUMENT_UPLOAD',
    'DOCUMENT_READ',
    'DOCUMENT_UPDATE',
    'DOCUMENT_DELETE',
    'DOCUMENT_DOWNLOAD',

    -- Knowledge base
    'KNOWLEDGE_CREATE',
    'KNOWLEDGE_READ',
    'KNOWLEDGE_UPDATE',
    'KNOWLEDGE_DELETE',
    'KNOWLEDGE_CONTENT_UPLOAD',

    -- Embeddings
    'EMBEDDING_GENERATE',
    'EMBEDDING_DELETE',

    -- Multimodal processing
    'IMAGE_PROCESS',
    'AUDIO_PROCESS',
    'VIDEO_PROCESS',

    -- Model testing/evaluation
    'MODEL_TEST',
    'MODEL_EVALUATE',

    -- Analytics
    'ANALYTICS_READ',

    -- Notifications
    'NOTIFICATION_SEND',

    -- System monitoring
    'SYSTEM_MONITOR'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ============================================================================
-- SECTION 5: ADMIN → ALL Permissions
-- ============================================================================

INSERT INTO role_permissions (
    role_permission_id,
    role_id,
    permission_id
)
SELECT
    gen_random_uuid(),
    r.role_id,
    p.permission_id
FROM roles r
CROSS JOIN permissions p
WHERE r.role_name = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ============================================================================
-- END OF V9
-- ============================================================================