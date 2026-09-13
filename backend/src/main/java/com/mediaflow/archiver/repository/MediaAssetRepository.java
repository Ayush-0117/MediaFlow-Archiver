package com.mediaflow.archiver.repository;

import com.mediaflow.archiver.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long>, MediaAssetRepositoryCustom {

    // The entire Database Queue engine operates on this single targeted query.
    // Fetches the oldest unfinished AI task safely.
    Optional<MediaAsset> findFirstByStatusOrderByCreatedAtAsc(MediaAsset.AiStatus status);

    // Used to detect power-failure Zombies on startup
    java.util.List<MediaAsset> findByStatus(MediaAsset.AiStatus status);

    // Used for SHA-256 deduplication — prevents re-importing already archived photos
    boolean existsBySha256Hash(String sha256Hash);

    // Used for AI usage stats dashboard
    long countByStatus(MediaAsset.AiStatus status);

    // Full-text search across AI tags, filenames, paths, and camera metadata.
    // Uses native PostgreSQL ILIKE since Hibernate 7 is strict about LOWER() on JSONB columns.
    @org.springframework.data.jpa.repository.Query(value = """
        SELECT a.* FROM media_assets a
        LEFT JOIN metadata_specs m ON a.metadata_spec_id = m.id
        WHERE a.original_filename ILIKE CONCAT('%', :q, '%')
           OR CAST(a.ai_tags_json AS TEXT) ILIKE CONCAT('%', :q, '%')
           OR a.relative_path ILIKE CONCAT('%', :q, '%')
           OR m.camera_model ILIKE CONCAT('%', :q, '%')
        ORDER BY a.created_at DESC
    """, nativeQuery = true)
    java.util.List<MediaAsset> searchAll(@org.springframework.data.repository.query.Param("q") String query);

    @org.springframework.data.jpa.repository.Query(value = """
        SELECT a.* FROM media_assets a
        LEFT JOIN metadata_specs m ON a.metadata_spec_id = m.id
        WHERE a.original_filename ILIKE CONCAT('%', :q, '%')
           OR CAST(a.ai_tags_json AS TEXT) ILIKE CONCAT('%', :q, '%')
           OR a.relative_path ILIKE CONCAT('%', :q, '%')
           OR m.camera_model ILIKE CONCAT('%', :q, '%')
        ORDER BY a.created_at DESC
    """, countQuery = """
        SELECT COUNT(*) FROM media_assets a
        LEFT JOIN metadata_specs m ON a.metadata_spec_id = m.id
        WHERE a.original_filename ILIKE CONCAT('%', :q, '%')
           OR CAST(a.ai_tags_json AS TEXT) ILIKE CONCAT('%', :q, '%')
           OR a.relative_path ILIKE CONCAT('%', :q, '%')
           OR m.camera_model ILIKE CONCAT('%', :q, '%')
    """, nativeQuery = true)
    org.springframework.data.domain.Page<MediaAsset> searchAllPaginated(
            @org.springframework.data.repository.query.Param("q") String query,
            org.springframework.data.domain.Pageable pageable);
}
