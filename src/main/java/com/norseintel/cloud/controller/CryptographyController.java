package com.norseintel.cloud.controller;

import com.norseintel.cloud.model.ApiResponse;
import com.norseintel.cloud.service.CryptographyService;
import com.norseintel.cloud.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cryptography")
@Tag(name = "Cryptography", description = "APIs for cryptographic operations")
public class CryptographyController {

    private final CryptographyService cryptographyService;
    private final FileStorageService fileStorageService;
    
    public CryptographyController(CryptographyService cryptographyService, 
                                 FileStorageService fileStorageService) {
        this.cryptographyService = cryptographyService;
        this.fileStorageService = fileStorageService;
    }
    
    @PostMapping("/hash-file")
    @Operation(summary = "Calculate File Hash", description = "Calculate cryptographic hash of a file")
    public ResponseEntity<ApiResponse<Map<String, String>>> calculateFileHash(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "algorithm", defaultValue = "SHA-256") String algorithm) {
        
        File tempFile = null;
        try {
            tempFile = fileStorageService.storeFile(file);
            String hash = cryptographyService.calculateHash(tempFile, algorithm);
            return ResponseEntity.ok(ApiResponse.success(Map.of("hash", hash, "algorithm", algorithm)));
        } finally {
            if (tempFile != null) {
                fileStorageService.deleteFile(tempFile);
            }
        }
    }
    
    @PostMapping("/hash-data")
    @Operation(summary = "Calculate Data Hash", description = "Calculate cryptographic hash of text data")
    public ResponseEntity<ApiResponse<Map<String, String>>> calculateDataHash(
            @RequestParam("data") String data,
            @RequestParam(value = "algorithm", defaultValue = "SHA-256") String algorithm) {
        
        String hash = cryptographyService.calculateHash(data.getBytes(), algorithm);
        return ResponseEntity.ok(ApiResponse.success(Map.of("hash", hash, "algorithm", algorithm)));
    }
    
    @PostMapping("/multiple-hashes")
    @Operation(summary = "Calculate Multiple Hashes", description = "Calculate multiple hash algorithms on a file")
    public ResponseEntity<ApiResponse<Map<String, String>>> calculateMultipleHashes(
            @RequestParam("file") MultipartFile file) {
        
        File tempFile = null;
        try {
            tempFile = fileStorageService.storeFile(file);
            Map<String, String> hashes = cryptographyService.calculateMultipleHashes(tempFile);
            return ResponseEntity.ok(ApiResponse.success(hashes));
        } finally {
            if (tempFile != null) {
                fileStorageService.deleteFile(tempFile);
            }
        }
    }
    
    @GetMapping("/random-key")
    @Operation(summary = "Generate Random Key", description = "Generate a random cryptographic key")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateRandomKey(
            @RequestParam(value = "bits", defaultValue = "256") int bits) {
        
        String key = cryptographyService.generateRandomKey(bits);
        return ResponseEntity.ok(ApiResponse.success(Map.of("key", key, "bits", String.valueOf(bits))));
    }
    
    @GetMapping("/rsa-keypair")
    @Operation(summary = "Generate RSA Key Pair", description = "Generate an RSA public/private key pair")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateRSAKeyPair(
            @RequestParam(value = "keySize", defaultValue = "2048") int keySize) {
        
        Map<String, String> keyPair = cryptographyService.generateRSAKeyPair(keySize);
        return ResponseEntity.ok(ApiResponse.success(keyPair));
    }
    
    @GetMapping("/ec-keypair")
    @Operation(summary = "Generate EC Key Pair", description = "Generate an Elliptic Curve public/private key pair")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateECKeyPair(
            @RequestParam(value = "curve", defaultValue = "secp256r1") String curve) {
        
        Map<String, String> keyPair = cryptographyService.generateECKeyPair(curve);
        return ResponseEntity.ok(ApiResponse.success(keyPair));
    }
    
    @PostMapping("/encrypt-password")
    @Operation(summary = "Encrypt with Password", description = "Encrypt data with a password")
    public ResponseEntity<ApiResponse<Map<String, String>>> encryptWithPassword(
            @RequestParam("plainText") String plainText,
            @RequestParam("password") String password) {
        
        Map<String, String> result = cryptographyService.encryptWithPassword(plainText, password);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/decrypt-password")
    @Operation(summary = "Decrypt with Password", description = "Decrypt data with a password")
    public ResponseEntity<ApiResponse<Map<String, String>>> decryptWithPassword(
            @RequestParam("encryptedData") String encryptedData,
            @RequestParam("salt") String salt,
            @RequestParam("iv") String iv,
            @RequestParam("password") String password) {
        
        String decrypted = cryptographyService.decryptWithPassword(encryptedData, salt, iv, password);
        return ResponseEntity.ok(ApiResponse.success(Map.of("decryptedData", decrypted)));
    }
    
    @GetMapping("/symmetric-key")
    @Operation(summary = "Generate Symmetric Key", description = "Generate a symmetric encryption key")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateSymmetricKey(
            @RequestParam(value = "algorithm", defaultValue = "AES") String algorithm,
            @RequestParam(value = "keySize", defaultValue = "256") int keySize) {
        
        Map<String, String> result = cryptographyService.generateSymmetricKey(algorithm, keySize);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @GetMapping("/generate-otp")
    @Operation(summary = "Generate OTP", description = "Generate a one-time password")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateOTP(
            @RequestParam(value = "length", defaultValue = "6") int length) {
        
        String otp = cryptographyService.generateOTP(length);
        return ResponseEntity.ok(ApiResponse.success(Map.of("otp", otp, "length", String.valueOf(length))));
    }
    
    @PostMapping("/password-hash")
    @Operation(summary = "Generate Password Hash", description = "Generate a secure hash of a password")
    public ResponseEntity<ApiResponse<Map<String, String>>> generatePasswordHash(
            @RequestParam("password") String password) {
        
        String hash = cryptographyService.generatePasswordHash(password);
        return ResponseEntity.ok(ApiResponse.success(Map.of("hash", hash)));
    }
    
    @PostMapping("/verify-password")
    @Operation(summary = "Verify Password Hash", description = "Verify a password against a stored hash")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> verifyPasswordHash(
            @RequestParam("password") String password,
            @RequestParam("storedHash") String storedHash) {
        
        boolean result = cryptographyService.verifyPasswordHash(password, storedHash);
        return ResponseEntity.ok(ApiResponse.success(Map.of("valid", result)));
    }
}