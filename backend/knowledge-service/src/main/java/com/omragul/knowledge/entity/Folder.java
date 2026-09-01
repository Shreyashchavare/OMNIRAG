package com.omragul.knowledge.entity;

import com.omragul.knowledge.entity.common.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "folders",
        indexes = {
                @Index(name = "idx_folders_knowledge_base_id", columnList = "knowledge_base_id"),
                @Index(name = "idx_folders_parent_folder_id", columnList = "parent_folder_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Folder extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "folder_name", nullable = false, length = 150)
    private String folderName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "knowledge_base_id", nullable = false)
    private KnowledgeBase knowledgeBase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_folder_id")
    private Folder parentFolder;

    @Builder.Default
    @OneToMany(
            mappedBy = "parentFolder",
            fetch = FetchType.LAZY
    )
    private List<Folder> subFolders = new ArrayList<>();

    @Builder.Default
    @OneToMany(
            mappedBy = "folder",
            fetch = FetchType.LAZY
    )
    private List<Document> documents = new ArrayList<>();
}