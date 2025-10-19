package com.norse.cloud.controller;

import com.norse.cloud.model.ApiResponse;
import com.norse.cloud.service.FileForensicsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/file-forensics")
@RequiredArgsConstructor
@Tag(name = "File Forensics", description = "APIs for file forensic analysis")
public class FileForensicsController {

    private final FileForensicsService fileForensicsService;
    
    @PostMapping("/file-signature")
    @Operation(summary = "Analyze File Signature", description = "Analyzes file signatures to identify file type and tampering")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeFileSignature(
            @Parameter(description = "File to analyze") 
            @RequestParam("file") MultipartFile file) {
        
        Map<String, Object> result = fileForensicsService.analyzeFileSignature(file);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/document-analysis")
    @Operation(summary = "Analyze Document", description = "Extracts metadata and hidden content from document files")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeDocument(
            @RequestParam("file") MultipartFile file) {
        
        Map<String, Object> result = fileForensicsService.extractDocumentMetadata(file);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/binary-analysis")
    @Operation(summary = "Analyze Binary File", description = "Extracts strings and patterns from binary files")
    public ResponseEntity<ApiResponse<List<String>>> analyzeBinary(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "minLength", defaultValue = "4") int minLength) {
        
        List<String> result = fileForensicsService.extractStringsFromBinary(file, minLength);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/archive-analysis")
    @Operation(summary = "Analyze Archive", description = "Analyzes archive files for structure and content without extraction")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeArchive(
            @RequestParam("file") MultipartFile file) {
        
        Map<String, Object> result = fileForensicsService.analyzeArchiveFile(file);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/file-hash")
    @Operation(summary = "Calculate File Hash", description = "Calculates cryptographic hashes for file integrity")
    public ResponseEntity<ApiResponse<Map<String, String>>> calculateFileHash(
            @RequestParam("file") MultipartFile file) {
        
        Map<String, String> hashes = fileForensicsService.calculateFileHashes(file);
        return ResponseEntity.ok(ApiResponse.success(hashes));
    }
    
    @PostMapping("/file-structure")
    @Operation(summary = "Analyze File Structure", description = "Analyzes the structure of the file including entropy")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeFileStructure(
            @RequestParam("file") MultipartFile file) {
        
        Map<String, Object> result = fileForensicsService.analyzeFileStructure(file);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/binary-patterns")
    @Operation(summary = "Search Binary Patterns", description = "Searches for patterns in binary files")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> searchBinaryPatterns(
            @RequestParam("file") MultipartFile file,
            @RequestParam("pattern") String pattern,
            @RequestParam(value = "isHex", defaultValue = "false") boolean isHex) {
        
        List<Map<String, Object>> result = fileForensicsService.searchBinaryPatterns(file, pattern, isHex);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/extract-from-archive")
    @Operation(summary = "Extract File from Archive", description = "Extracts a specific file from an archive")
    public ResponseEntity<byte[]> extractFileFromArchive(
            @RequestParam("file") MultipartFile file,
            @RequestParam("entryPath") String entryPath) {
        
        byte[] fileContent = fileForensicsService.extractFileFromArchive(file, entryPath);
        return ResponseEntity.ok(fileContent);
    }
    
    @PostMapping("/compare-documents")
    @Operation(summary = "Compare Documents", description = "Compares two documents for differences")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> compareDocuments(
            @RequestParam("file1") MultipartFile file1,
            @RequestParam("file2") MultipartFile file2) {
        
        List<Map<String, Object>> result = fileForensicsService.compareDocuments(file1, file2);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}