# Norse Cloud - Image and File Forensics Platform

Norse Cloud is a comprehensive forensics platform for image and file analysis, providing a wide range of tools for digital forensics investigations.

## Features

### Image Forensics
- Metadata extraction (EXIF, IPTC, XMP)
- Error Level Analysis (ELA) for manipulation detection
- Noise analysis for tampering detection
- JPEG structure analysis
- Thumbnail analysis
- Pattern detection for copy-paste manipulation
- Compression history analysis
- Color filter analysis
- Image hash verification

### File Forensics
- File signature analysis
- Document metadata extraction
- Binary file analysis
- Archive file inspection
- String extraction from binary files
- File hash calculation
- File structure analysis
- Binary pattern search
- Archive file extraction
- Document comparison

### Cryptography
- File and data hashing (multiple algorithms)
- Random key generation
- RSA and EC key pair generation
- Password-based encryption/decryption
- Symmetric key generation
- One-time password (OTP) generation
- Password hashing and verification

## Technology Stack
- Spring Boot 3.2.0
- Java 21
- Apache Tika for file type detection and metadata extraction
- Apache Commons IO and Compress for file operations
- BouncyCastle for cryptography operations
- OpenAPI/Swagger for API documentation
- Spring Security for API key authentication

## Getting Started

### Prerequisites
- Java 21 or higher
- Maven 3.8.x or higher
- Docker and Docker Compose (for containerized deployment)

### Building the Application
```bash
./mvnw clean package
```

### Running Locally
```bash
./mvnw spring-boot:run
```

### Running with Docker
```bash
docker-compose up -d
```

## API Documentation
Once the application is running, the API documentation is available at:
```
http://localhost:8080/swagger-ui.html
```

## Authentication
All API endpoints are secured using API key authentication. You need to include the API key in your requests:
```
X-API-KEY: your-api-key-here
```

## Configuration
Configure your API key and other settings in `application.properties`.

## Sample Usage

### Image Metadata Extraction
```bash
curl -X POST "http://localhost:8080/api/v1/image-forensics/metadata" \
  -H "X-API-KEY: your-api-key-here" \
  -F "file=@/path/to/image.jpg"
```

### File Signature Analysis
```bash
curl -X POST "http://localhost:8080/api/v1/file-forensics/file-signature" \
  -H "X-API-KEY: your-api-key-here" \
  -F "file=@/path/to/file"
```

### Calculate File Hash
```bash
curl -X POST "http://localhost:8080/api/v1/cryptography/hash-file" \
  -H "X-API-KEY: your-api-key-here" \
  -F "file=@/path/to/file" \
  -F "algorithm=SHA-256"
```

## License
[Specify your license here]

## Security Notice
This tool should be used responsibly and ethically. It is designed for legitimate forensic investigations and security research purposes only.