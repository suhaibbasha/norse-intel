package com.norseintel.cloud.model.image;

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
    
    // Explicitly adding getters and setters
    public String getFilename() {
        return filename;
    }
    
    public void setFilename(String filename) {
        this.filename = filename;
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
    
    public Map<String, String> getAllTags() {
        return allTags;
    }
    
    public void setAllTags(Map<String, String> allTags) {
        this.allTags = allTags;
    }
    
    public GpsCoordinates getGpsCoordinates() {
        return gpsCoordinates;
    }
    
    public void setGpsCoordinates(GpsCoordinates gpsCoordinates) {
        this.gpsCoordinates = gpsCoordinates;
    }
    
    public Date getDateTaken() {
        return dateTaken;
    }
    
    public void setDateTaken(Date dateTaken) {
        this.dateTaken = dateTaken;
    }
}