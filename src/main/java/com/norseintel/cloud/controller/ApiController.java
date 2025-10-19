package com.norseintel.cloud.controller;

import com.norseintel.cloud.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "API Info", description = "General API information")
public class ApiController {

    @Value("${spring.application.name:NorseIntel Cloud}")
    private String applicationName;
    
    @Value("${spring.application.version:1.0.0}")
    private String applicationVersion;
    
    @GetMapping("/info")
    @Operation(summary = "Get API Information", description = "Returns general information about the API")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getApiInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", applicationName);
        info.put("version", applicationVersion);
        info.put("description", "NorseIntel Cloud - Image and File Forensics Platform");
        
        Map<String, String> features = new HashMap<>();
        features.put("imageForensics", "/api/v1/image-forensics");
        features.put("fileForensics", "/api/v1/file-forensics");
        features.put("cryptography", "/api/v1/cryptography");
        
        info.put("endpoints", features);
        
        return ResponseEntity.ok(ApiResponse.success(info));
    }
    
    @GetMapping("/health")
    @Operation(summary = "Health Check", description = "Simple health check endpoint")
    public ResponseEntity<ApiResponse<Map<String, String>>> healthCheck() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", String.valueOf(System.currentTimeMillis()));
        
        return ResponseEntity.ok(ApiResponse.success(health));
    }
}