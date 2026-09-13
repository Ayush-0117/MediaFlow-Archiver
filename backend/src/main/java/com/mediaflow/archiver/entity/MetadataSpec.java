package com.mediaflow.archiver.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;

@Entity
@Table(name = "metadata_specs")
@Data
@NoArgsConstructor
public class MetadataSpec {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Dual-Timezone fields
    private ZonedDateTime takenAtUtc; // The exact absolute time
    
    @Column(length = 10)
    private String originalTimezoneOffset; // e.g., "+09:00"

    private Double latitude;
    private Double longitude;

    @Column(length = 100)
    private String cameraMake;
    
    @Column(length = 100)
    private String cameraModel;

    @Column(length = 20)
    private String resolution; // e.g. "1920x1080"
}
