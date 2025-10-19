package com.norse.cloud.controller;

import com.norse.cloud.model.ApiResponse;
import com.norse.cloud.model.image.ElaResult;
import com.norse.cloud.model.image.ImageMetadata;
import com.norse.cloud.service.ImageForensicsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/image-forensics")
@RequiredArgsConstructor
@Tag(name = "Image Forensics", description = "APIs for image forensic analysis")
public class ImageForensicsController {

    private final ImageForensicsService imageForensicsService;
    
    @PostMapping("/metadata")
    @Operation(summary = "Extract image metadata", description = "Extracts EXIF and other metadata from an image")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "Metadata extracted successfully",
            content = @Content(schema = @Schema(implementation = ImageMetadata.class))
        )
    })
    public ResponseEntity<ApiResponse<ImageMetadata>> extractMetadata(
            @Parameter(description = "Image file to analyze") 
            @RequestParam("file") MultipartFile file) {
        
        ImageMetadata metadata = imageForensicsService.extractMetadata(file);
        return ResponseEntity.ok(ApiResponse.success(metadata));
    }
    
    @PostMapping("/jpeg-structure")
    @Operation(summary = "Analyze JPEG structure", description = "Analyzes the JPEG file structure for technical metadata")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeJpegStructure(
            @RequestParam("file") MultipartFile file) {
        
        Map<String, Object> result = imageForensicsService.analyzeJpegStructure(file);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/error-level-analysis")
    @Operation(summary = "Perform Error Level Analysis", description = "Detects image manipulation through error level analysis")
    public ResponseEntity<ApiResponse<ElaResult>> performErrorLevelAnalysis(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "quality", defaultValue = "0.95") float quality) {
        
        ElaResult result = imageForensicsService.performErrorLevelAnalysis(file, quality);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/noise-analysis")
    @Operation(summary = "Perform Noise Analysis", description = "Detects image tampering by analyzing noise patterns")
    public ResponseEntity<byte[]> analyzeNoise(@RequestParam("file") MultipartFile file) {
        byte[] noiseImage = imageForensicsService.applyNoiseAnalysis(file);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(noiseImage);
    }
    
    @PostMapping("/color-filter")
    @Operation(summary = "Apply Color Filter", description = "Apply various color filters to detect manipulations")
    public ResponseEntity<byte[]> applyColorFilter(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "filter", defaultValue = "invert") String filterType) {
        
        byte[] filteredImage = imageForensicsService.applyColorFilter(file, filterType);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(filteredImage);
    }
    
    @PostMapping("/thumbnail-analysis")
    @Operation(summary = "Analyze Thumbnail", description = "Compare embedded thumbnail with full image")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeThumbnail(
            @RequestParam("file") MultipartFile file) {
        
        Map<String, Object> result = imageForensicsService.analyzeThumbnail(file);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/pattern-detection")
    @Operation(summary = "Detect Copy-Paste Patterns", description = "Identify recurring patterns that might indicate copy-paste manipulation")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detectPatterns(
            @RequestParam("file") MultipartFile file) {
        
        Map<String, Object> result = imageForensicsService.detectPatterns(file);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/compression-analysis")
    @Operation(summary = "Analyze Compression History", description = "Detect multiple compression cycles suggesting manipulation")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeCompression(
            @RequestParam("file") MultipartFile file) {
        
        Map<String, Object> result = imageForensicsService.analyzeCompressionHistory(file);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/verify-hash")
    @Operation(summary = "Verify Image Hash", description = "Verify the integrity of an image using cryptographic hashing")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyHash(
            @RequestParam("file") MultipartFile file,
            @RequestParam("hash") String providedHash,
            @RequestParam(value = "algorithm", defaultValue = "SHA-256") String algorithm) {
        
        Map<String, Object> result = imageForensicsService.verifyImageHash(file, providedHash, algorithm);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}