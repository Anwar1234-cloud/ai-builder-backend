package com.aibuilder.version.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "project_version_files",
        indexes = {
                @Index(name = "idx_version_file_version_id", columnList = "version_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProjectVersionFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private ProjectVersion version;

    @Column(nullable = false, length = 500)
    private String path;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(length = 50)
    private String language;
}