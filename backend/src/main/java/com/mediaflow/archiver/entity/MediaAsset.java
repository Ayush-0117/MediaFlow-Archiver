package com.mediaflow.archiver.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.ZonedDateTime;

@Entity
@Table(name = "media_assets")
@Data
@NoArgsConstructor
public class MediaAsset {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String sha256Hash;

    @Column(length = 64)
    private String pHash; // Perceptual Hash for similarity checks

    @Column(nullable = false, length = 500)
    private String relativePath; // e.g. 2021/Nature/001.jpg

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(length = 20)
    private String fileType; // JPG, MP4, HEIC

    private Long fileSize; // In bytes

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "metadata_spec_id")
    private MetadataSpec metadata;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String aiTagsJson; // Raw JSON mapping tags/categories from Gemini

    // Note: To use pgvector natively with Spring Data, you typically use hibernate-vector.
    // Keeping this commented until vector operations are required.
    // @Column(columnDefinition = "vector(768)")
    // private float[] embedding; 
    
    public enum AiStatus {
        PENDING_AI, PROCESSING, COMPLETED, FAILED
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private AiStatus status = AiStatus.PENDING_AI;

    /** Tracks how many times the AI has attempted and failed to process this asset. */
    @Column(nullable = false)
    private Integer retryCount = 0;

    @Column(name = "created_at")
    private ZonedDateTime createdAt = ZonedDateTime.now();
}
