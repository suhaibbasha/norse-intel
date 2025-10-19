package com.norse.cloud.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.jpeg.JpegDirectory;
import com.norse.cloud.exception.ForensicException;
import com.norse.cloud.model.image.ElaResult;
import com.norse.cloud.model.image.GpsCoordinates;
import com.norse.cloud.model.image.ImageMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.imgscalr.Scalr;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageForensicsService {
    
    private final FileStorageService fileStorageService;
    private final CryptographyService cryptographyService;
    
    public ImageMetadata extractMetadata(MultipartFile file) {
        File tempFile = null;
        try {
            tempFile = fileStorageService.storeFile(file);
            return extractMetadataFromFile(tempFile);
        } catch (ImageProcessingException | IOException | MetadataException e) {
            throw new ForensicException("Failed to extract metadata: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (tempFile != null) {
                fileStorageService.deleteFile(tempFile);
            }
        }
    }
    
    public Map<String, Object> analyzeJpegStructure(MultipartFile file) {
        File tempFile = null;
        try {
            tempFile = fileStorageService.storeFile(file);
            Metadata metadata = ImageMetadataReader.readMetadata(tempFile);
            
            Map<String, Object> result = new HashMap<>();
            
            Optional<JpegDirectory> jpegDirectory = getDirectoryOfType(metadata, JpegDirectory.class);
            if (jpegDirectory.isPresent()) {
                result.put("compressionType", jpegDirectory.get().getDescription(JpegDirectory.TAG_COMPRESSION_TYPE));
                result.put("dataPrecision", jpegDirectory.get().getInteger(JpegDirectory.TAG_DATA_PRECISION));
                result.put("imageHeight", jpegDirectory.get().getInteger(JpegDirectory.TAG_IMAGE_HEIGHT));
                result.put("imageWidth", jpegDirectory.get().getInteger(JpegDirectory.TAG_IMAGE_WIDTH));
                result.put("numberOfComponents", jpegDirectory.get().getInteger(JpegDirectory.TAG_NUMBER_OF_COMPONENTS));
                // TAG_COMPONENT_DATA is not available, use available component information instead
                if (jpegDirectory.get().hasTagName(JpegDirectory.TAG_COMPONENT_DATA_1)) {
                    result.put("componentData1", jpegDirectory.get().getDescription(JpegDirectory.TAG_COMPONENT_DATA_1));
                }
                if (jpegDirectory.get().hasTagName(JpegDirectory.TAG_COMPONENT_DATA_2)) {
                    result.put("componentData2", jpegDirectory.get().getDescription(JpegDirectory.TAG_COMPONENT_DATA_2));
                }
                if (jpegDirectory.get().hasTagName(JpegDirectory.TAG_COMPONENT_DATA_3)) {
                    result.put("componentData3", jpegDirectory.get().getDescription(JpegDirectory.TAG_COMPONENT_DATA_3));
                }
            }
            
            String fileHash = cryptographyService.calculateHash(tempFile, "SHA-256");
            result.put("fileHash", fileHash);
            
            return result;
        } catch (Exception e) {
            throw new ForensicException("Failed to analyze JPEG structure: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (tempFile != null) {
                fileStorageService.deleteFile(tempFile);
            }
        }
    }
    
    public ElaResult performErrorLevelAnalysis(MultipartFile file, float quality) {
        File originalFile = null;
        File resavedFile = null;
        File diffFile = null;
        
        try {
            originalFile = fileStorageService.storeFile(file);
            
            String extension = FilenameUtils.getExtension(originalFile.getName());
            if (!extension.equalsIgnoreCase("jpg") && !extension.equalsIgnoreCase("jpeg")) {
                throw new ForensicException("Error Level Analysis only supports JPEG images", HttpStatus.BAD_REQUEST);
            }
            
            BufferedImage originalImage = ImageIO.read(originalFile);
            
            String tempDir = originalFile.getParent();
            resavedFile = new File(tempDir, "resaved_" + originalFile.getName());
            
            saveJpegWithQuality(originalImage, resavedFile, quality);
            
            BufferedImage resavedImage = ImageIO.read(resavedFile);
            BufferedImage differenceImage = createDifferenceImage(originalImage, resavedImage);
            
            diffFile = new File(tempDir, "diff_" + originalFile.getName());
            ImageIO.write(differenceImage, "png", diffFile);
            
            byte[] diffBytes = Files.readAllBytes(diffFile.toPath());
            
            String originalHash = cryptographyService.calculateHash(originalFile, "SHA-256");
            String resavedHash = cryptographyService.calculateHash(resavedFile, "SHA-256");
            
            ElaResult result = new ElaResult();
            result.setDifferenceImageBase64(Base64.getEncoder().encodeToString(diffBytes));
            result.setQuality(quality);
            result.setOriginalImageHash(originalHash);
            result.setResavedImageHash(resavedHash);
            return result;
                    
        } catch (IOException e) {
            throw new ForensicException("Failed to perform Error Level Analysis: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (originalFile != null) fileStorageService.deleteFile(originalFile);
            if (resavedFile != null) fileStorageService.deleteFile(resavedFile);
            if (diffFile != null) fileStorageService.deleteFile(diffFile);
        }
    }
    
    public byte[] applyNoiseAnalysis(MultipartFile file) {
        File tempFile = null;
        try {
            tempFile = fileStorageService.storeFile(file);
            BufferedImage originalImage = ImageIO.read(tempFile);
            
            float[] matrix = {
                -1, -1, -1,
                -1,  8, -1,
                -1, -1, -1
            };
            
            Kernel kernel = new Kernel(3, 3, matrix);
            ConvolveOp convolveOp = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
            BufferedImage noiseImage = convolveOp.filter(originalImage, null);
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(noiseImage, "png", outputStream);
            return outputStream.toByteArray();
            
        } catch (IOException e) {
            throw new ForensicException("Failed to apply noise analysis: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (tempFile != null) {
                fileStorageService.deleteFile(tempFile);
            }
        }
    }
    
    public byte[] applyColorFilter(MultipartFile file, String filterType) {
        File tempFile = null;
        try {
            tempFile = fileStorageService.storeFile(file);
            BufferedImage originalImage = ImageIO.read(tempFile);
            BufferedImage filteredImage;
            
            switch (filterType.toLowerCase()) {
                case "invert":
                    filteredImage = applyInvertFilter(originalImage);
                    break;
                case "equalize":
                    filteredImage = applyEqualizeFilter(originalImage);
                    break;
                case "red":
                    filteredImage = applyChannelFilter(originalImage, 0);
                    break;
                case "green":
                    filteredImage = applyChannelFilter(originalImage, 1);
                    break;
                case "blue":
                    filteredImage = applyChannelFilter(originalImage, 2);
                    break;
                default:
                    throw new ForensicException("Unsupported filter type: " + filterType, HttpStatus.BAD_REQUEST);
            }
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(filteredImage, "png", outputStream);
            return outputStream.toByteArray();
            
        } catch (IOException e) {
            throw new ForensicException("Failed to apply color filter: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (tempFile != null) {
                fileStorageService.deleteFile(tempFile);
            }
        }
    }
    
    public Map<String, Object> analyzeThumbnail(MultipartFile file) {
        File tempFile = null;
        try {
            tempFile = fileStorageService.storeFile(file);
            
            Map<String, Object> result = new HashMap<>();
            
            // Read the image file
            BufferedImage originalImage = ImageIO.read(tempFile);
            if (originalImage == null) {
                result.put("error", "Could not read image file");
                return result;
            }
            
            try {
                // Use metadata-extractor library to check for Exif thumbnail indicators
                Metadata metadata = ImageMetadataReader.readMetadata(tempFile);
                
                com.drew.metadata.exif.ExifThumbnailDirectory exifThumbnailDir = 
                    metadata.getFirstDirectoryOfType(com.drew.metadata.exif.ExifThumbnailDirectory.class);
                
                if (exifThumbnailDir != null) {
                    // Check if we have thumbnail offset and length tags
                    if (exifThumbnailDir.containsTag(com.drew.metadata.exif.ExifThumbnailDirectory.TAG_THUMBNAIL_OFFSET) &&
                        exifThumbnailDir.containsTag(com.drew.metadata.exif.ExifThumbnailDirectory.TAG_THUMBNAIL_LENGTH)) {
                        
                        result.put("hasThumbnail", true);
                        result.put("thumbnailFormat", "JPEG");
                        result.put("thumbnailOffsetTag", exifThumbnailDir.getInteger(
                            com.drew.metadata.exif.ExifThumbnailDirectory.TAG_THUMBNAIL_OFFSET));
                        result.put("thumbnailLengthTag", exifThumbnailDir.getInteger(
                            com.drew.metadata.exif.ExifThumbnailDirectory.TAG_THUMBNAIL_LENGTH));
                            
                        // Since direct extraction is complex, we'll generate a thumbnail from the original
                        BufferedImage thumbnail = Scalr.resize(originalImage, Scalr.Method.SPEED, 
                                                               Scalr.Mode.AUTOMATIC, 160, 160);
                        
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(thumbnail, "JPEG", baos);
                        byte[] thumbnailData = baos.toByteArray();
                        
                        result.put("thumbnailLength", thumbnailData.length);
                        result.put("thumbnailBase64", Base64.getEncoder().encodeToString(thumbnailData));
                        result.put("thumbnailHash", cryptographyService.calculateHash(thumbnailData, "SHA-256"));
                        result.put("note", "Thumbnail was generated from original image as direct extraction is not supported");
                        return result;
                    }
                }
            } catch (Exception e) {
                // If metadata extraction fails, generate a thumbnail instead
                BufferedImage thumbnail = Scalr.resize(originalImage, Scalr.Method.SPEED, 
                                                     Scalr.Mode.AUTOMATIC, 160, 160);
                
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(thumbnail, "JPEG", baos);
                byte[] thumbnailData = baos.toByteArray();
                
                result.put("hasThumbnail", false);
                result.put("generatedThumbnail", true);
                result.put("thumbnailBase64", Base64.getEncoder().encodeToString(thumbnailData));
                result.put("thumbnailHash", cryptographyService.calculateHash(thumbnailData, "SHA-256"));
                result.put("note", "Generated thumbnail as no embedded thumbnail was found");
            }
            
            // If we've reached here, we didn't find a thumbnail in the EXIF data
            if (!result.containsKey("hasThumbnail")) {
                result.put("hasThumbnail", false);
                result.put("message", "This image does not contain an embedded thumbnail");
            }
            
            return result;
        } catch (Exception e) {
            throw new ForensicException("Failed to analyze thumbnail: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (tempFile != null) {
                fileStorageService.deleteFile(tempFile);
            }
        }
    }
    
    public Map<String, Object> detectPatterns(MultipartFile file) {
        File tempFile = null;
        try {
            tempFile = fileStorageService.storeFile(file);
            BufferedImage image = ImageIO.read(tempFile);
            
            Map<String, Object> results = new HashMap<>();
            Map<String, Integer> patternCount = new HashMap<>();
            
            int width = image.getWidth();
            int height = image.getHeight();
            
            int blockSize = 8;
            for (int y = 0; y < height - blockSize; y += blockSize) {
                for (int x = 0; x < width - blockSize; x += blockSize) {
                    String pattern = calculateBlockPattern(image, x, y, blockSize);
                    patternCount.put(pattern, patternCount.getOrDefault(pattern, 0) + 1);
                }
            }
            
            List<Map.Entry<String, Integer>> sortedPatterns = 
                patternCount.entrySet().stream()
                    .filter(e -> e.getValue() > 1)  
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(10)
                    .collect(Collectors.toList());
            
            results.put("repeatedPatternsFound", !sortedPatterns.isEmpty());
            results.put("totalUniquePatterns", patternCount.size());
            results.put("topRepeatedPatterns", sortedPatterns.size());
            results.put("potentialCopyPasteRegions", sortedPatterns);
            
            return results;
        } catch (IOException e) {
            throw new ForensicException("Failed to detect patterns: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (tempFile != null) {
                fileStorageService.deleteFile(tempFile);
            }
        }
    }
    
    public Map<String, Object> analyzeCompressionHistory(MultipartFile file) {
        File tempFile = null;
        try {
            tempFile = fileStorageService.storeFile(file);
            // We don't need metadata for compression analysis
            Map<String, Object> result = new HashMap<>();
            boolean isJpeg = FilenameUtils.getExtension(file.getOriginalFilename()).equalsIgnoreCase("jpg") || 
                            FilenameUtils.getExtension(file.getOriginalFilename()).equalsIgnoreCase("jpeg");
            
            if (!isJpeg) {
                result.put("compressionAnalysis", "Compression history analysis is only available for JPEG images");
                return result;
            }
            
            BufferedImage image = ImageIO.read(tempFile);
            int width = image.getWidth();
            int height = image.getHeight();
            
            double[] dctCoefficients = calculateDctCoefficients(image);
            int[] coefficientHistogram = generateHistogram(dctCoefficients);
            
            int estimatedCompressionCount = estimateJpegCompressionCount(coefficientHistogram);
            
            result.put("estimatedCompressionCycles", estimatedCompressionCount);
            result.put("imageWidth", width);
            result.put("imageHeight", height);
            result.put("compressionSignature", analyzeCompressionSignature(coefficientHistogram));
            
            return result;
        } catch (Exception e) {
            throw new ForensicException("Failed to analyze compression history: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (tempFile != null) {
                fileStorageService.deleteFile(tempFile);
            }
        }
    }
    
    public Map<String, Object> verifyImageHash(MultipartFile file, String providedHash, String algorithm) {
        File tempFile = null;
        try {
            tempFile = fileStorageService.storeFile(file);
            
            String calculatedHash = cryptographyService.calculateHash(tempFile, algorithm);
            boolean matches = calculatedHash.equalsIgnoreCase(providedHash);
            
            Map<String, Object> result = new HashMap<>();
            result.put("providedHash", providedHash);
            result.put("calculatedHash", calculatedHash);
            result.put("algorithm", algorithm);
            result.put("matches", matches);
            result.put("verified", matches);
            
            return result;
        } catch (Exception e) {
            throw new ForensicException("Failed to verify image hash: " + e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (tempFile != null) {
                fileStorageService.deleteFile(tempFile);
            }
        }
    }
    
    private ImageMetadata extractMetadataFromFile(File file) throws ImageProcessingException, IOException, MetadataException {
        Metadata metadata = ImageMetadataReader.readMetadata(file);
        
        Map<String, String> allTags = new HashMap<>();
        
        for (var directory : metadata.getDirectories()) {
            for (var tag : directory.getTags()) {
                allTags.put(tag.getTagName(), tag.getDescription());
            }
        }
        
        GpsCoordinates gpsCoordinates = extractGpsCoordinates(metadata);
        
        Optional<ExifSubIFDDirectory> exifDirectory = getDirectoryOfType(metadata, ExifSubIFDDirectory.class);
        Date originalDate = exifDirectory
                .map(dir -> dir.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL))
                .orElse(null);
                
        ImageMetadata imageMetadata = new ImageMetadata();
        imageMetadata.setAllTags(allTags);
        imageMetadata.setFilename(file.getName());
        imageMetadata.setFileSize(file.length());
        imageMetadata.setGpsCoordinates(gpsCoordinates);
        imageMetadata.setDateTaken(originalDate);
        return imageMetadata;
    }
    
    private GpsCoordinates extractGpsCoordinates(Metadata metadata) {
        Optional<GpsDirectory> gpsDirectory = getDirectoryOfType(metadata, GpsDirectory.class);
        
        if (gpsDirectory.isPresent() && gpsDirectory.get().containsTag(GpsDirectory.TAG_LATITUDE) &&
            gpsDirectory.get().containsTag(GpsDirectory.TAG_LONGITUDE)) {
            
            var dir = gpsDirectory.get();
            GpsCoordinates coordinates = new GpsCoordinates();
            coordinates.setLatitude(dir.getGeoLocation().getLatitude());
            coordinates.setLongitude(dir.getGeoLocation().getLongitude());
            return coordinates;
        }
        
        return null;
    }
    
    private <T> Optional<T> getDirectoryOfType(Metadata metadata, Class<T> directoryClass) {
        if (metadata == null) return Optional.empty();
        
        return StreamSupport
                .stream(metadata.getDirectories().spliterator(), false)
                .filter(directoryClass::isInstance)
                .map(directoryClass::cast)
                .findFirst();
    }
    
    private void saveJpegWithQuality(BufferedImage image, File output, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG image writer found");
        }
        
        ImageWriter writer = writers.next();
        try (FileImageOutputStream fos = new FileImageOutputStream(output)) {
            writer.setOutput(fos);
            
            JPEGImageWriteParam params = new JPEGImageWriteParam(null);
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(quality);
            
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
    }
    
    private BufferedImage createDifferenceImage(BufferedImage original, BufferedImage resaved) {
        int width = original.getWidth();
        int height = original.getHeight();
        
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color originalColor = new Color(original.getRGB(x, y));
                Color resavedColor = new Color(resaved.getRGB(x, y));
                
                int diffR = Math.abs(originalColor.getRed() - resavedColor.getRed());
                int diffG = Math.abs(originalColor.getGreen() - resavedColor.getGreen());
                int diffB = Math.abs(originalColor.getBlue() - resavedColor.getBlue());
                
                // Scale up differences to make them more visible
                diffR = Math.min(255, diffR * 10);
                diffG = Math.min(255, diffG * 10);
                diffB = Math.min(255, diffB * 10);
                
                Color diffColor = new Color(diffR, diffG, diffB);
                result.setRGB(x, y, diffColor.getRGB());
            }
        }
        
        return result;
    }
    
    private BufferedImage applyInvertFilter(BufferedImage original) {
        BufferedImage filtered = new BufferedImage(
                original.getWidth(), 
                original.getHeight(),
                original.getType());
                
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                Color color = new Color(original.getRGB(x, y));
                Color inverted = new Color(
                        255 - color.getRed(),
                        255 - color.getGreen(),
                        255 - color.getBlue());
                filtered.setRGB(x, y, inverted.getRGB());
            }
        }
        
        return filtered;
    }
    
    private BufferedImage applyEqualizeFilter(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();
        BufferedImage result = new BufferedImage(width, height, original.getType());
        
        int[] histogram = new int[256];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color color = new Color(original.getRGB(x, y));
                int grayLevel = (color.getRed() + color.getGreen() + color.getBlue()) / 3;
                histogram[grayLevel]++;
            }
        }
        
        int pixelCount = width * height;
        float[] cdf = new float[256];
        cdf[0] = histogram[0] / (float) pixelCount;
        
        for (int i = 1; i < 256; i++) {
            cdf[i] = cdf[i - 1] + histogram[i] / (float) pixelCount;
        }
        
        int[] equalized = new int[256];
        for (int i = 0; i < 256; i++) {
            equalized[i] = Math.round(cdf[i] * 255);
        }
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color color = new Color(original.getRGB(x, y));
                int r = equalized[color.getRed()];
                int g = equalized[color.getGreen()];
                int b = equalized[color.getBlue()];
                
                Color newColor = new Color(r, g, b);
                result.setRGB(x, y, newColor.getRGB());
            }
        }
        
        return result;
    }
    
    private BufferedImage applyChannelFilter(BufferedImage original, int channel) {
        int width = original.getWidth();
        int height = original.getHeight();
        BufferedImage result = new BufferedImage(width, height, original.getType());
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color color = new Color(original.getRGB(x, y));
                
                int r = (channel == 0) ? color.getRed() : 0;
                int g = (channel == 1) ? color.getGreen() : 0;
                int b = (channel == 2) ? color.getBlue() : 0;
                
                Color newColor = new Color(r, g, b);
                result.setRGB(x, y, newColor.getRGB());
            }
        }
        
        return result;
    }
    
    private String calculateBlockPattern(BufferedImage image, int startX, int startY, int blockSize) {
        StringBuilder patternBuilder = new StringBuilder();
        
        for (int y = 0; y < blockSize; y++) {
            for (int x = 0; x < blockSize; x++) {
                if (startX + x < image.getWidth() && startY + y < image.getHeight()) {
                    Color color = new Color(image.getRGB(startX + x, startY + y));
                    
                    int grayscale = (color.getRed() + color.getGreen() + color.getBlue()) / 3;
                    
                    // Simplify to reduce noise - just keep most significant bits
                    grayscale = grayscale & 0xF0;
                    
                    patternBuilder.append(Integer.toHexString(grayscale >> 4));
                }
            }
        }
        
        return patternBuilder.toString();
    }
    
    private double[] calculateDctCoefficients(BufferedImage image) {
        int width = Math.min(image.getWidth(), 512);  // Limit size for performance
        int height = Math.min(image.getHeight(), 512);
        
        BufferedImage scaledImage = Scalr.resize(image, Scalr.Method.QUALITY, width, height);
        
        double[] dctValues = new double[64];  // 8x8 DCT block
        
        for (int y = 0; y < height - 8; y += 8) {
            for (int x = 0; x < width - 8; x += 8) {
                double[][] block = new double[8][8];
                
                // Extract 8x8 block
                for (int j = 0; j < 8; j++) {
                    for (int i = 0; i < 8; i++) {
                        Color color = new Color(scaledImage.getRGB(x + i, y + j));
                        block[j][i] = (color.getRed() + color.getGreen() + color.getBlue()) / 3.0;
                    }
                }
                
                // Simple DCT calculation for each coefficient
                for (int v = 0; v < 8; v++) {
                    for (int u = 0; u < 8; u++) {
                        double sum = 0;
                        for (int j = 0; j < 8; j++) {
                            for (int i = 0; i < 8; i++) {
                                sum += block[j][i] * 
                                       Math.cos((2 * i + 1) * u * Math.PI / 16.0) * 
                                       Math.cos((2 * j + 1) * v * Math.PI / 16.0);
                            }
                        }
                        
                        double alpha_u = (u == 0) ? 1.0 / Math.sqrt(2) : 1.0;
                        double alpha_v = (v == 0) ? 1.0 / Math.sqrt(2) : 1.0;
                        double coefficient = 0.25 * alpha_u * alpha_v * sum;
                        
                        int index = v * 8 + u;
                        dctValues[index] += Math.abs(coefficient);
                    }
                }
            }
        }
        
        // Normalize by the number of blocks
        int numBlocks = ((width / 8) * (height / 8));
        for (int i = 0; i < dctValues.length; i++) {
            dctValues[i] /= numBlocks;
        }
        
        return dctValues;
    }
    
    private int[] generateHistogram(double[] dctCoefficients) {
        int[] histogram = new int[100];  // 100 bins for coefficient magnitudes
        double maxCoeff = Arrays.stream(dctCoefficients).max().orElse(1.0);
        
        for (double coeff : dctCoefficients) {
            int bin = (int) ((Math.abs(coeff) / maxCoeff) * 99);
            histogram[bin]++;
        }
        
        return histogram;
    }
    
    private int estimateJpegCompressionCount(int[] coefficientHistogram) {
        int zeroBin = coefficientHistogram[0];
        int totalCoeffs = Arrays.stream(coefficientHistogram).sum();
        
        double zeroRatio = (double) zeroBin / totalCoeffs;
        
        // Simplified heuristic: more zeros means more compression cycles
        if (zeroRatio > 0.8) return 3; // Heavily compressed, likely 3+ cycles
        else if (zeroRatio > 0.6) return 2; // Moderately compressed, likely 2 cycles
        else if (zeroRatio > 0.4) return 1; // Lightly compressed, likely 1 cycle
        else return 0; // Possibly uncompressed or very high quality
    }
    
    private String analyzeCompressionSignature(int[] histogram) {
        int sum = Arrays.stream(histogram).sum();
        
        // Calculate compression peaks
        List<Integer> peaks = new ArrayList<>();
        for (int i = 1; i < histogram.length - 1; i++) {
            if (histogram[i] > histogram[i-1] && histogram[i] > histogram[i+1] && 
                histogram[i] > sum * 0.05) { // At least 5% of total
                peaks.add(i);
            }
        }
        
        if (peaks.isEmpty()) {
            return "No distinctive compression signature detected";
        } else if (peaks.size() == 1) {
            return "Single compression cycle detected";
        } else {
            return "Multiple compression cycles detected, with " + peaks.size() + " distinct peaks";
        }
    }
}