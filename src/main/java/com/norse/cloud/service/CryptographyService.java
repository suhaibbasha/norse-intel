package com.norse.cloud.service;

import com.norse.cloud.exception.ForensicException;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class CryptographyService {
    
    static {
        Security.addProvider(new BouncyCastleProvider());
    }
    
    private static final Map<String, String> HASH_ALGORITHMS = Map.of(
        "MD5", "MD5",
        "SHA-1", "SHA-1",
        "SHA-256", "SHA-256",
        "SHA-384", "SHA-384",
        "SHA-512", "SHA-512",
        "SHA3-256", "SHA3-256",
        "SHA3-512", "SHA3-512"
    );
    
    public String calculateHash(File file, String algorithm) {
        try {
            String hashAlgorithm = validateHashAlgorithm(algorithm);
            
            MessageDigest digest = MessageDigest.getInstance(hashAlgorithm);
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            byte[] hashBytes = digest.digest(fileBytes);
            
            return bytesToHex(hashBytes);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new ForensicException("Failed to calculate hash: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    public String calculateHash(byte[] data, String algorithm) {
        try {
            String hashAlgorithm = validateHashAlgorithm(algorithm);
            
            MessageDigest digest = MessageDigest.getInstance(hashAlgorithm);
            byte[] hashBytes = digest.digest(data);
            
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new ForensicException("Failed to calculate hash: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    public Map<String, String> calculateMultipleHashes(File file) {
        Map<String, String> hashes = new HashMap<>();
        
        for (String algorithm : HASH_ALGORITHMS.keySet()) {
            String hash = calculateHash(file, algorithm);
            hashes.put(algorithm, hash);
        }
        
        return hashes;
    }
    
    public String generateRandomKey(int bits) {
        byte[] bytes = new byte[bits / 8];
        new SecureRandom().nextBytes(bytes);
        return bytesToHex(bytes);
    }
    
    public Map<String, String> generateRSAKeyPair(int keySize) {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(keySize);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            
            Map<String, String> result = new HashMap<>();
            result.put("publicKey", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
            result.put("privateKey", Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
            
            return result;
        } catch (NoSuchAlgorithmException e) {
            throw new ForensicException("Failed to generate RSA key pair: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    public Map<String, String> generateECKeyPair(String curve) {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
            keyPairGenerator.initialize(new ECGenParameterSpec(curve));
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            
            Map<String, String> result = new HashMap<>();
            result.put("publicKey", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
            result.put("privateKey", Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
            
            return result;
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new ForensicException("Failed to generate EC key pair: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    public Map<String, String> encryptWithPassword(String plainText, String password) {
        try {
            byte[] salt = new byte[16];
            SecureRandom random = new SecureRandom();
            random.nextBytes(salt);
            
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKey secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");
            
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] iv = new byte[16];
            random.nextBytes(iv);
            IvParameterSpec ivspec = new IvParameterSpec(iv);
            
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivspec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            Map<String, String> result = new HashMap<>();
            result.put("salt", Base64.getEncoder().encodeToString(salt));
            result.put("iv", Base64.getEncoder().encodeToString(iv));
            result.put("encryptedData", Base64.getEncoder().encodeToString(encrypted));
            
            return result;
        } catch (Exception e) {
            throw new ForensicException("Failed to encrypt data: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    public String decryptWithPassword(String encryptedBase64, String saltBase64, String ivBase64, String password) {
        try {
            byte[] salt = Base64.getDecoder().decode(saltBase64);
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            byte[] encryptedData = Base64.getDecoder().decode(encryptedBase64);
            
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKey secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");
            
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
            
            byte[] decrypted = cipher.doFinal(encryptedData);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ForensicException("Failed to decrypt data: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    public Map<String, String> generateSymmetricKey(String algorithm, int keySize) {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(algorithm);
            keyGenerator.init(keySize);
            SecretKey secretKey = keyGenerator.generateKey();
            
            Map<String, String> result = new HashMap<>();
            result.put("algorithm", algorithm);
            result.put("keySize", String.valueOf(keySize));
            result.put("key", Base64.getEncoder().encodeToString(secretKey.getEncoded()));
            result.put("format", secretKey.getFormat());
            
            return result;
        } catch (NoSuchAlgorithmException e) {
            throw new ForensicException("Failed to generate symmetric key: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    public String generateOTP(int length) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder otp = new StringBuilder();
        
        for (int i = 0; i < length; i++) {
            otp.append(random.nextInt(10));  // 0-9
        }
        
        return otp.toString();
    }
    
    public boolean validateOTP(String expected, String actual) {
        return expected != null && actual != null && expected.equals(actual);
    }
    
    public String generatePasswordHash(String password) {
        try {
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 512);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            
            // Format: iterations:salt:hash
            return "65536:" + bytesToHex(salt) + ":" + bytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new ForensicException("Failed to hash password: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    public boolean verifyPasswordHash(String password, String storedHash) {
        try {
            String[] parts = storedHash.split(":");
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = hexToBytes(parts[1]);
            byte[] hash = hexToBytes(parts[2]);
            
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, hash.length * 8);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
            byte[] testHash = factory.generateSecret(spec).getEncoded();
            
            int diff = hash.length ^ testHash.length;
            for (int i = 0; i < hash.length && i < testHash.length; i++) {
                diff |= hash[i] ^ testHash[i];
            }
            
            return diff == 0;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new ForensicException("Failed to verify password: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    private String validateHashAlgorithm(String algorithm) {
        String normalized = algorithm.toUpperCase();
        if (HASH_ALGORITHMS.containsKey(normalized)) {
            return HASH_ALGORITHMS.get(normalized);
        }
        
        throw new ForensicException("Unsupported hash algorithm: " + algorithm, HttpStatus.BAD_REQUEST);
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexBuilder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hexBuilder.append(String.format("%02x", b & 0xff));
        }
        return hexBuilder.toString();
    }
    
    private byte[] hexToBytes(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int index = i * 2;
            result[i] = (byte) Integer.parseInt(hex.substring(index, index + 2), 16);
        }
        return result;
    }
}