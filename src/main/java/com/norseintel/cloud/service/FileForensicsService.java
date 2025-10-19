package com.norseintel.cloud.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;

import com.norseintel.cloud.exception.ForensicException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileForensicsService {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileForensicsService.class);
    private static final Map<String, byte[]> FILE_SIGNATURES = initializeSignatures();
    private static final int MAX_BYTES_FOR_SIGNATURE = 16;
    private static final int MAX_STRINGS_TO_EXTRACT = 1000;
    private static final int MIN_STRING_LENGTH = 4;
    
    private final FileStorageService fileStorageService;
    private final CryptographyService cryptographyService;
    private final Tika tika = new Tika();
    
    public Map<String, Object> analyzeFileSignature(MultipartFile file) {
        File tempFile = null;
        try {
            tempFile = fileStorageService.storeFile(file);
            
            Map<String, Object> result = new HashMap<>();
            result.put("filename", file.getOriginalFilename());
            result.put("declaredContentType", file.getContentType());
            
            String extension = FilenameUtils.getExtension(file.getOriginalFilename());
            result.put("extension", extension);
            
            String detectedMimeType = tika.detect(tempFile);
            result.put("detectedMimeType", detectedMimeType);
            
            byte[] signature = readFileSignature(tempFile);
            String hexSignature = bytesToHex(signature);
            result.put("hexSignature", hexSignature);
            
            String detectedType = detectFileType(signature);
            result.put("detectedType", detectedType);
            
            boolean mismatch = !detectedMimeType.toLowerCase().contains(extension.toLowerCase()) 
                            && !extension.isEmpty();
            result.put("possibleMismatch", mismatch);
            
            return result;
        } catch (IOException e) {
            throw new ForensicException("Failed to analyze file signature: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (tempFile != null) {
                fileStorageService.deleteFile(tempFile);
            }
        }
    }
    
    public Map<String, String> calculateFileHashes(MultipartFile file) {
        File tempFile = null;
        try {
            tempFile = fileStorageService.storeFile(file);
            return cryptographyService.calculateMultipleHashes(tempFile);
        } finally {
            if (tempFile != null) {
                fileStorageService.deleteFile(tempFile);
            }
        }
    }
    
    public Map<String, Object> analyzeFileStructure(MultipartFile file) {
        File tempFile = null;
        try {
            tempFile = fileStorageService.storeFile(file);
            
            Map<String, Object> result = new HashMap<>();
            result.put("filename", file.getOriginalFilename());
            result.put("fileSize", file.getSize());
            
            String mimeType = tika.detect(tempFile);
            result.put("mimeType", mimeType);
            
            Parser parser = new AutoDetectParser();
            ContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            metadata.set("resourceName", file.getOriginalFilename());
            
            try (InputStream stream = new FileInputStream(tempFile)) {
                parser.parse(stream, handler, metadata, new ParseContext());
                
                Map<String, String> metadataMap = new HashMap<>();
                for (String name : metadata.names()) {
                    metadataMap.put(name, metadata.get(name));
                }
                
                result.put("metadata", metadataMap);
                
                if (mimeType.startsWith("text/") || mimeType.contains("json") || mimeType.contains("xml")) {
                    // For text files, include sample content
                    try (BufferedReader reader = new BufferedReader(new FileReader(tempFile))) {
                        List<String> lines = reader.lines().limit(50).collect(Collectors.toList());
                        result.put("sampleContent", String.join("\n", lines));
                    }
                } else {
                    // For binary files, include statistical analysis
                    byte[] fileBytes = new byte[Math.min((int)tempFile.length(), 10240)]; // First 10KB
                    try (FileInputStream fis = new FileInputStream(tempFile)) {
                        fis.read(fileBytes);
                    }
                    
                    // Byte frequency analysis
                    int[] byteFrequency = new int[256];
                    for (byte b : fileBytes) {
                        byteFrequency[b & 0xFF]++;
                    }
                    
                    result.put("entropyScore", calculateEntropy(byteFrequency, fileBytes.length));
                }
                
                if (isArchive(mimeType)) {
                    result.put("archiveDetails", analyzeArchive(tempFile));
                }
            }
            
            return result;
        } catch (Exception e) {
            throw new ForensicException("Failed to analyze file structure: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (tempFile != null) {
                fileStorageService.deleteFile(tempFile);
            }
        }
    }
    
    public Map<String, Object> extractDocumentMetadata(MultipartFile file) {
        File tempFile = null;
        try {
            tempFile = fileStorageService.storeFile(file);
            
            Map<String, Object> result = new HashMap<>();
            
            Parser parser = new AutoDetectParser();
            ContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            metadata.set("resourceName", file.getOriginalFilename());
            
            try (InputStream stream = new FileInputStream(tempFile)) {
                parser.parse(stream, handler, metadata, new ParseContext());
                
                Map<String, String> metadataMap = new HashMap<>();
                for (String name : metadata.names()) {
                    metadataMap.put(name, metadata.get(name));
                }
                
                result.put("filename", file.getOriginalFilename());
                result.put("metadata", metadataMap);
                
                // Extract specific important metadata fields
                result.put("author", metadata.get("Author"));
                result.put("creator", metadata.get("Creator"));
                result.put("creationDate", metadata.get("Creation-Date"));
                result.put("lastModified", metadata.get("Last-Modified"));
                result.put("lastSavedBy", metadata.get("Last-Saved-By"));
                result.put("application", metadata.get("Application-Name"));
                result.put("applicationVersion", metadata.get("Application-Version"));
                result.put("editTime", metadata.get("Edit-Time"));
                result.put("revisionNumber", metadata.get("Revision-Number"));
                
                // Calculate document hash for verification
                String docHash = cryptographyService.calculateHash(tempFile, "SHA-256");
                result.put("documentHash", docHash);
            }
            
            return result;
        } catch (Exception e) {
            throw new ForensicException("Failed to extract document metadata: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (tempFile != null) {
                fileStorageService.deleteFile(tempFile);
            }
        }
    }
    
    public List<Map<String, Object>> compareDocuments(MultipartFile file1, MultipartFile file2) {
        File tempFile1 = null;
        File tempFile2 = null;
        try {
            tempFile1 = fileStorageService.storeFile(file1);
            tempFile2 = fileStorageService.storeFile(file2);
            
            Parser parser = new AutoDetectParser();
            Metadata metadata1 = new Metadata();
            Metadata metadata2 = new Metadata();
            
            ContentHandler handler1 = new BodyContentHandler(-1);
            ContentHandler handler2 = new BodyContentHandler(-1);
            
            try (InputStream stream1 = new FileInputStream(tempFile1);
                 InputStream stream2 = new FileInputStream(tempFile2)) {
                
                parser.parse(stream1, handler1, metadata1, new ParseContext());
                parser.parse(stream2, handler2, metadata2, new ParseContext());
                
                List<Map<String, Object>> differences = new ArrayList<>();
                
                // Compare content
                String content1 = handler1.toString();
                String content2 = handler2.toString();
                boolean contentMatches = content1.equals(content2);
                
                Map<String, Object> contentDiff = new HashMap<>();
                contentDiff.put("type", "content");
                contentDiff.put("matches", contentMatches);
                contentDiff.put("lengthDiff", content2.length() - content1.length());
                differences.add(contentDiff);
                
                // Compare metadata
                for (String name : metadata1.names()) {
                    String value1 = metadata1.get(name);
                    String value2 = metadata2.get(name);
                    
                    if (metadata2.get(name) == null || !value1.equals(value2)) {
                        Map<String, Object> diff = new HashMap<>();
                        diff.put("type", "metadata");
                        diff.put("field", name);
                        diff.put("file1Value", value1);
                        diff.put("file2Value", value2 != null ? value2 : "(not present)");
                        differences.add(diff);
                    }
                }
                
                // Check for metadata in file2 not in file1
                for (String name : metadata2.names()) {
                    if (metadata1.get(name) == null) {
                        Map<String, Object> diff = new HashMap<>();
                        diff.put("type", "metadata");
                        diff.put("field", name);
                        diff.put("file1Value", "(not present)");
                        diff.put("file2Value", metadata2.get(name));
                        differences.add(diff);
                    }
                }
                
                return differences;
            }
        } catch (Exception e) {
            throw new ForensicException("Failed to compare documents: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (tempFile1 != null) {
                fileStorageService.deleteFile(tempFile1);
            }
            if (tempFile2 != null) {
                fileStorageService.deleteFile(tempFile2);
            }
        }
    }
    
    public List<String> extractStringsFromBinary(MultipartFile file, int minLength) {
        File tempFile = null;
        try {
            tempFile = fileStorageService.storeFile(file);
            int actualMinLength = minLength > 0 ? minLength : MIN_STRING_LENGTH;
            
            List<String> strings = new ArrayList<>();
            
            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(tempFile))) {
                byte[] buffer = new byte[4096];
                StringBuilder currentString = new StringBuilder();
                int bytesRead;
                
                while ((bytesRead = bis.read(buffer)) != -1 && strings.size() < MAX_STRINGS_TO_EXTRACT) {
                    for (int i = 0; i < bytesRead; i++) {
                        byte b = buffer[i];
                        if (isPrintableAscii(b)) {
                            currentString.append((char) b);
                        } else if (currentString.length() >= actualMinLength) {
                            strings.add(currentString.toString());
                            currentString = new StringBuilder();
                        } else {
                            currentString = new StringBuilder();
                        }
                    }
                }
                
                if (currentString.length() >= actualMinLength) {
                    strings.add(currentString.toString());
                }
            }
            
            return strings;
        } catch (IOException e) {
            throw new ForensicException("Failed to extract strings: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (tempFile != null) {
                fileStorageService.deleteFile(tempFile);
            }
        }
    }
    
    public List<Map<String, Object>> searchBinaryPatterns(MultipartFile file, String patternString, boolean isHex) {
        File tempFile = null;
        try {
            tempFile = fileStorageService.storeFile(file);
            
            List<Map<String, Object>> matches = new ArrayList<>();
            
            if (isHex) {
                // Convert hex string to byte pattern
                byte[] pattern = hexStringToByteArray(patternString);
                
                try (FileInputStream fis = new FileInputStream(tempFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long offset = 0;
                    
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        for (int i = 0; i <= bytesRead - pattern.length; i++) {
                            boolean found = true;
                            for (int j = 0; j < pattern.length; j++) {
                                if (buffer[i + j] != pattern[j]) {
                                    found = false;
                                    break;
                                }
                            }
                            
                            if (found) {
                                Map<String, Object> match = new HashMap<>();
                                match.put("offset", offset + i);
                                match.put("pattern", patternString);
                                
                                // Extract context around the match
                                int contextStart = Math.max(0, i - 8);
                                int contextEnd = Math.min(bytesRead, i + pattern.length + 8);
                                byte[] context = Arrays.copyOfRange(buffer, contextStart, contextEnd);
                                match.put("context", bytesToHex(context));
                                
                                matches.add(match);
                            }
                        }
                        offset += bytesRead;
                    }
                }
            } else {
                // Use regex pattern for text search
                Pattern pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(tempFile), StandardCharsets.UTF_8))) {
                    String line;
                    int lineNumber = 0;
                    
                    while ((line = reader.readLine()) != null) {
                        lineNumber++;
                        Matcher matcher = pattern.matcher(line);
                        
                        while (matcher.find()) {
                            Map<String, Object> match = new HashMap<>();
                            match.put("lineNumber", lineNumber);
                            match.put("position", matcher.start());
                            match.put("pattern", patternString);
                            match.put("matchedText", matcher.group());
                            match.put("context", line);
                            
                            matches.add(match);
                        }
                    }
                }
            }
            
            return matches;
        } catch (Exception e) {
            throw new ForensicException("Failed to search binary patterns: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (tempFile != null) {
                fileStorageService.deleteFile(tempFile);
            }
        }
    }
    
    public Map<String, Object> analyzeArchiveFile(MultipartFile file) {
        File tempFile = null;
        try {
            tempFile = fileStorageService.storeFile(file);
            String mimeType = tika.detect(tempFile);
            
            if (!isArchive(mimeType)) {
                throw new ForensicException("The provided file is not a recognized archive format", HttpStatus.BAD_REQUEST);
            }
            
            return analyzeArchive(tempFile);
        } catch (IOException e) {
            throw new ForensicException("Failed to analyze archive: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (tempFile != null) {
                fileStorageService.deleteFile(tempFile);
            }
        }
    }
    
    public byte[] extractFileFromArchive(MultipartFile archiveFile, String entryPath) {
        File tempFile = null;
        try {
            tempFile = fileStorageService.storeFile(archiveFile);
            
            if (FilenameUtils.getExtension(archiveFile.getOriginalFilename()).equalsIgnoreCase("zip")) {
                try (ZipFile zipFile = new ZipFile(tempFile)) {
                    ZipArchiveEntry entry = zipFile.getEntry(entryPath);
                    if (entry == null) {
                        throw new ForensicException("Entry not found in archive: " + entryPath, HttpStatus.NOT_FOUND);
                    }
                    
                    try (InputStream inputStream = zipFile.getInputStream(entry)) {
                        return IOUtils.toByteArray(inputStream);
                    }
                }
            } else {
                try (FileInputStream fis = new FileInputStream(tempFile);
                     BufferedInputStream bis = new BufferedInputStream(fis);
                     ArchiveInputStream ais = new ArchiveStreamFactory().createArchiveInputStream(bis)) {
                     
                    ArchiveEntry entry;
                    while ((entry = ais.getNextEntry()) != null) {
                        if (!ais.canReadEntryData(entry)) {
                            continue;
                        }
                        
                        if (entry.getName().equals(entryPath)) {
                            return IOUtils.toByteArray(ais);
                        }
                    }
                    
                    throw new ForensicException("Entry not found in archive: " + entryPath, HttpStatus.NOT_FOUND);
                }
            }
        } catch (ForensicException e) {
            throw e;
        } catch (Exception e) {
            throw new ForensicException("Failed to extract file from archive: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (tempFile != null) {
                fileStorageService.deleteFile(tempFile);
            }
        }
    }
    
    private byte[] readFileSignature(File file) throws IOException {
        byte[] signature = new byte[MAX_BYTES_FOR_SIGNATURE];
        try (FileInputStream fis = new FileInputStream(file)) {
            int bytesRead = fis.read(signature, 0, MAX_BYTES_FOR_SIGNATURE);
            if (bytesRead < MAX_BYTES_FOR_SIGNATURE) {
                return Arrays.copyOf(signature, bytesRead);
            }
            return signature;
        }
    }
    
    private String detectFileType(byte[] signature) {
        for (Map.Entry<String, byte[]> entry : FILE_SIGNATURES.entrySet()) {
            byte[] knownSignature = entry.getValue();
            boolean match = true;
            
            for (int i = 0; i < knownSignature.length && i < signature.length; i++) {
                if (knownSignature[i] != signature[i]) {
                    match = false;
                    break;
                }
            }
            
            if (match) {
                return entry.getKey();
            }
        }
        
        return "Unknown";
    }
    
    private boolean isArchive(String mimeType) {
        return mimeType.contains("zip") || 
               mimeType.contains("tar") || 
               mimeType.contains("gzip") ||
               mimeType.contains("x-7z") ||
               mimeType.contains("x-rar");
    }
    
    private Map<String, Object> analyzeArchive(File file) {
        Map<String, Object> archiveInfo = new HashMap<>();
        
        try {
            String extension = FilenameUtils.getExtension(file.getName()).toLowerCase();
            archiveInfo.put("archiveType", extension);
            
            if (extension.equals("zip")) {
                try (ZipFile zipFile = new ZipFile(file)) {
                    int entryCount = 0;
                    Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
                    
                    boolean isEncrypted = false;
                    List<Map<String, Object>> entryList = new ArrayList<>();
                    
                    while (entries.hasMoreElements()) {
                        entryCount++;
                        ZipArchiveEntry entry = entries.nextElement();
                        
                        Map<String, Object> entryInfo = new HashMap<>();
                        entryInfo.put("name", entry.getName());
                        entryInfo.put("size", entry.getSize());
                        entryInfo.put("compressedSize", entry.getCompressedSize());
                        entryInfo.put("lastModified", new Date(entry.getTime()));
                        entryInfo.put("isDirectory", entry.isDirectory());
                        
                        if (entry.getGeneralPurposeBit().usesEncryption()) {
                            isEncrypted = true;
                            entryInfo.put("encrypted", true);
                        }
                        
                        entryList.add(entryInfo);
                    }
                    
                    archiveInfo.put("totalEntries", entryCount);
                    archiveInfo.put("entries", entryList);
                    archiveInfo.put("encrypted", isEncrypted);
                }
            } else {
                try (FileInputStream fis = new FileInputStream(file);
                     BufferedInputStream bis = new BufferedInputStream(fis);
                     ArchiveInputStream ais = new ArchiveStreamFactory().createArchiveInputStream(bis)) {
                     
                    List<Map<String, Object>> entryList = new ArrayList<>();
                    int entryCount = 0;
                    
                    ArchiveEntry entry;
                    while ((entry = ais.getNextEntry()) != null) {
                        entryCount++;
                        
                        Map<String, Object> entryInfo = new HashMap<>();
                        entryInfo.put("name", entry.getName());
                        entryInfo.put("size", entry.getSize());
                        entryInfo.put("lastModified", new Date(entry.getLastModifiedDate().getTime()));
                        entryInfo.put("isDirectory", entry.isDirectory());
                        
                        entryList.add(entryInfo);
                    }
                    
                    archiveInfo.put("totalEntries", entryCount);
                    archiveInfo.put("entries", entryList);
                }
            }
            
            // Calculate archive hash for verification
            String archiveHash = cryptographyService.calculateHash(file, "SHA-256");
            archiveInfo.put("archiveHash", archiveHash);
            
            return archiveInfo;
        } catch (Exception e) {
            log.error("Error analyzing archive", e);
            archiveInfo.put("error", "Failed to analyze archive: " + e.getMessage());
            return archiveInfo;
        }
    }
    
    private double calculateEntropy(int[] byteFrequency, int totalBytes) {
        double entropy = 0;
        for (int frequency : byteFrequency) {
            if (frequency > 0) {
                double probability = (double) frequency / totalBytes;
                entropy -= probability * (Math.log(probability) / Math.log(2));
            }
        }
        return entropy;
    }
    
    private boolean isPrintableAscii(byte b) {
        return b >= 32 && b <= 126; // Printable ASCII range
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexBuilder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hexBuilder.append(String.format("%02x", b & 0xff));
        }
        return hexBuilder.toString();
    }
    
    private byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
    
    private static Map<String, byte[]> initializeSignatures() {
        Map<String, byte[]> signatures = new HashMap<>();
        
        signatures.put("JPEG", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        signatures.put("PNG", new byte[]{(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47, (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A});
        signatures.put("GIF", "GIF87a".getBytes(StandardCharsets.US_ASCII));
        signatures.put("GIF", "GIF89a".getBytes(StandardCharsets.US_ASCII));
        signatures.put("BMP", new byte[]{(byte) 0x42, (byte) 0x4D});
        signatures.put("PDF", new byte[]{(byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46});
        signatures.put("ZIP", new byte[]{(byte) 0x50, (byte) 0x4B, (byte) 0x03, (byte) 0x04});
        signatures.put("RAR", new byte[]{(byte) 0x52, (byte) 0x61, (byte) 0x72, (byte) 0x21, (byte) 0x1A, (byte) 0x07});
        signatures.put("7Z", new byte[]{(byte) 0x37, (byte) 0x7A, (byte) 0xBC, (byte) 0xAF, (byte) 0x27, (byte) 0x1C});
        signatures.put("DOCX/XLSX/PPTX", new byte[]{(byte) 0x50, (byte) 0x4B, (byte) 0x03, (byte) 0x04, (byte) 0x14, (byte) 0x00, (byte) 0x06, (byte) 0x00});
        signatures.put("DOC", new byte[]{(byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1});
        signatures.put("EXE", new byte[]{(byte) 0x4D, (byte) 0x5A});
        
        return signatures;
    }
}