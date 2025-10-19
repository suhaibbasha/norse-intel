package com.norse.cloud.model.image;

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
}