CREATE TABLE folder_permissions
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    folder_id           UUID NOT NULL,

    principal_type      VARCHAR(20) NOT NULL
                            CHECK (
                                principal_type IN
                                ('USER','ROLE')
                            ),

    principal_id        UUID NOT NULL,

    permission          VARCHAR(20) NOT NULL
                            CHECK (
                                permission IN
                                (
                                    'READ',
                                    'WRITE',
                                    'DELETE',
                                    'UPLOAD',
                                    'OWNER'
                                )
                            ),

    granted_by          UUID,

    granted_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    expires_at          TIMESTAMP,

    CONSTRAINT fk_folder_permission
        FOREIGN KEY(folder_id)
        REFERENCES folders(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_folder_permission_folder
ON folder_permissions(folder_id);

CREATE INDEX idx_folder_permission_principal
ON folder_permissions(principal_id);