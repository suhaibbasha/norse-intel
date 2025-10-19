package com.norse.cloud.model.image;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageMetadata {
    private String filename;
    private long fileSize;
    private Map<String, String> allTags;
    private GpsCoordinates gpsCoordinates;
    private Date dateTaken;
}