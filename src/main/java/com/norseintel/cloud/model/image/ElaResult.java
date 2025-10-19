package com.norseintel.cloud.model.image;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ElaResult {
    private String differenceImageBase64;
    private float quality;
    private String originalImageHash;
    private String resavedImageHash;
    
    // Explicitly adding getters and setters
    public String getDifferenceImageBase64() {
        return differenceImageBase64;
    }
    
    public void setDifferenceImageBase64(String differenceImageBase64) {
        this.differenceImageBase64 = differenceImageBase64;
    }
    
    public float getQuality() {
        return quality;
    }
    
    public void setQuality(float quality) {
        this.quality = quality;
    }
    
    public String getOriginalImageHash() {
        return originalImageHash;
    }
    
    public void setOriginalImageHash(String originalImageHash) {
        this.originalImageHash = originalImageHash;
    }
    
    public String getResavedImageHash() {
        return resavedImageHash;
    }
    
    public void setResavedImageHash(String resavedImageHash) {
        this.resavedImageHash = resavedImageHash;
    }
}