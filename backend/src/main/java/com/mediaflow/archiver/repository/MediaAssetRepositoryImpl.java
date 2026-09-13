package com.mediaflow.archiver.repository;

import com.mediaflow.archiver.entity.MediaAsset;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class MediaAssetRepositoryImpl implements MediaAssetRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<MediaAsset> filterAssets(
            String queryStr,
            String status,
            String type,
            LocalDate startDate,
            LocalDate endDate,
            String make,
            String model,
            Boolean hasPerson,
            String dominantColor,
            Pageable pageable
    ) {
        StringBuilder sql = new StringBuilder(
                "SELECT a.* FROM media_assets a " +
                "LEFT JOIN metadata_specs m ON a.metadata_spec_id = m.id " +
                "WHERE 1=1 "
        );

        StringBuilder countSql = new StringBuilder(
                "SELECT COUNT(*) FROM media_assets a " +
                "LEFT JOIN metadata_specs m ON a.metadata_spec_id = m.id " +
                "WHERE 1=1 "
        );

        Map<String, Object> params = new HashMap<>();

        // 1. Text Query (Omni-search fallback)
        if (queryStr != null && !queryStr.isBlank()) {
            String q = "%" + queryStr + "%";
            String qCondition = " AND (a.original_filename ILIKE :q " +
                                " OR CAST(a.ai_tags_json AS TEXT) ILIKE :q " +
                                " OR a.relative_path ILIKE :q " +
                                " OR m.camera_model ILIKE :q) ";
            sql.append(qCondition);
            countSql.append(qCondition);
            params.put("q", q);
        }

        // 2. Status
        if (status != null && !status.isBlank()) {
            String sCondition = " AND a.status = :status ";
            sql.append(sCondition);
            countSql.append(sCondition);
            params.put("status", status);
        }

        // 3. Media Type (Video vs Image by extension)
        if (type != null && !type.isBlank()) {
            if ("VIDEO".equalsIgnoreCase(type)) {
                String tCondition = " AND (a.original_filename ILIKE '%.mp4' OR a.original_filename ILIKE '%.mov') ";
                sql.append(tCondition);
                countSql.append(tCondition);
            } else if ("IMAGE".equalsIgnoreCase(type)) {
                String tCondition = " AND (a.original_filename ILIKE '%.jpg' OR a.original_filename ILIKE '%.jpeg' OR a.original_filename ILIKE '%.png' OR a.original_filename ILIKE '%.heic') ";
                sql.append(tCondition);
                countSql.append(tCondition);
            }
        }

        // 4. Date Range
        if (startDate != null) {
            ZonedDateTime startZdt = startDate.atStartOfDay(ZoneId.systemDefault());
            String sdCondition = " AND a.created_at >= :startDate ";
            sql.append(sdCondition);
            countSql.append(sdCondition);
            params.put("startDate", startZdt);
        }
        if (endDate != null) {
            ZonedDateTime endZdt = endDate.atTime(23, 59, 59).atZone(ZoneId.systemDefault());
            String edCondition = " AND a.created_at <= :endDate ";
            sql.append(edCondition);
            countSql.append(edCondition);
            params.put("endDate", endZdt);
        }

        // 5. Camera Make & Model
        if (make != null && !make.isBlank()) {
            String makeCondition = " AND m.camera_make ILIKE :make ";
            sql.append(makeCondition);
            countSql.append(makeCondition);
            params.put("make", "%" + make + "%");
        }
        if (model != null && !model.isBlank()) {
            String modelCondition = " AND m.camera_model ILIKE :model ";
            sql.append(modelCondition);
            countSql.append(modelCondition);
            params.put("model", "%" + model + "%");
        }

        // 6. Strict AI Tags (hasPerson, dominantColor)
        if (hasPerson != null) {
            String personCondition = " AND a.ai_tags_json ->> 'hasPerson' = :hasPerson ";
            sql.append(personCondition);
            countSql.append(personCondition);
            params.put("hasPerson", hasPerson.toString());
        }
        if (dominantColor != null && !dominantColor.isBlank()) {
            String colorCondition = " AND a.ai_tags_json ->> 'dominantColor' ILIKE :dominantColor ";
            sql.append(colorCondition);
            countSql.append(colorCondition);
            params.put("dominantColor", "%" + dominantColor + "%");
        }

        // Ordering for main query
        sql.append(" ORDER BY a.created_at DESC");

        // Execute Count Query
        Query cQuery = entityManager.createNativeQuery(countSql.toString());
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            cQuery.setParameter(entry.getKey(), entry.getValue());
        }
        long total = ((Number) cQuery.getSingleResult()).longValue();

        // Execute Main Query
        Query mQuery = entityManager.createNativeQuery(sql.toString(), MediaAsset.class);
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            mQuery.setParameter(entry.getKey(), entry.getValue());
        }
        mQuery.setFirstResult((int) pageable.getOffset());
        mQuery.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<MediaAsset> assets = mQuery.getResultList();

        return new PageImpl<>(assets, pageable, total);
    }
}
