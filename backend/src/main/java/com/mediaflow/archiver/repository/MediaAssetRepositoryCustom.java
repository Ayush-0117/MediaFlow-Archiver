package com.mediaflow.archiver.repository;

import com.mediaflow.archiver.entity.MediaAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface MediaAssetRepositoryCustom {
    /**
     * Advanced dynamic faceted filter for MediaAssets.
     */
    Page<MediaAsset> filterAssets(
            String query,
            String status,
            String type,
            LocalDate startDate,
            LocalDate endDate,
            String make,
            String model,
            Boolean hasPerson,
            String dominantColor,
            Pageable pageable
    );
}
