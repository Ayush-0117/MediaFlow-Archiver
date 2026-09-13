package com.mediaflow.archiver.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.mediaflow.archiver.entity.MetadataSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;

@Service
@Slf4j
public class ExifMetadataService {

    /**
     * Decouples raw EXIF extraction from the main database commit.
     * Calculates absolute mathematical UTC and preserves the historical offset boundary.
     * Also extracts camera make, model, and resolution metadata.
     */
    public MetadataSpec extractMetadata(Path mediaFile) {
        MetadataSpec spec = new MetadataSpec();
        try {
            File file = mediaFile.toFile();
            Metadata metadata = ImageMetadataReader.readMetadata(file);

            // 1. Extract GPS (Latitude / Longitude) natively
            GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDir != null && gpsDir.getGeoLocation() != null) {
                spec.setLatitude(gpsDir.getGeoLocation().getLatitude());
                spec.setLongitude(gpsDir.getGeoLocation().getLongitude());
            }

            // 2. Extract EXIF 'Taken At' DateTime and normalize to Strict UTC
            ExifSubIFDDirectory exifSubDir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (exifSubDir != null) {
                Date originalDate = exifSubDir.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                if (originalDate != null) {
                    // Force the timestamp into a mathematical UTC ZonedDateTime
                    ZonedDateTime utcTime = originalDate.toInstant().atZone(ZoneId.of("UTC"));
                    spec.setTakenAtUtc(utcTime);
                    
                    // Default preservation
                    spec.setOriginalTimezoneOffset("+00:00");
                }

                // 3. Extract image resolution from EXIF tags
                Integer width = exifSubDir.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH);
                Integer height = exifSubDir.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT);
                if (width != null && height != null) {
                    spec.setResolution(width + "x" + height);
                }
            }

            // 4. Extract Camera Make and Model from IFD0 directory (the main EXIF block)
            ExifIFD0Directory ifd0Dir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0Dir != null) {
                String make = ifd0Dir.getString(ExifIFD0Directory.TAG_MAKE);
                String model = ifd0Dir.getString(ExifIFD0Directory.TAG_MODEL);
                if (make != null) spec.setCameraMake(make.trim());
                if (model != null) spec.setCameraModel(model.trim());
            }

            log.info("Successfully extracted EXIF: GPS={}, Camera={} {}, Resolution={}", 
                     spec.getLatitude() != null ? spec.getLatitude() + "," + spec.getLongitude() : "N/A",
                     spec.getCameraMake() != null ? spec.getCameraMake() : "Unknown",
                     spec.getCameraModel() != null ? spec.getCameraModel() : "",
                     spec.getResolution() != null ? spec.getResolution() : "N/A");

        } catch (Exception e) {
            log.error("Failed to extract EXIF for file: {}", mediaFile, e);
        }
        return spec;
    }
}
