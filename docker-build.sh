#!/bin/bash

# Colors for terminal output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}NorseIntel Cloud Docker Build Script${NC}"
echo "========================================"
echo ""

# Function to check if Docker is running
check_docker() {
  if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}Error: Docker is not running.${NC}"
    echo "Please start Docker and try again."
    exit 1
  fi
}

# Check Docker
check_docker

# Create tmp directory if it doesn't exist
if [ ! -d "./tmp" ]; then
  echo -e "${YELLOW}Creating tmp directory for volume mounting...${NC}"
  mkdir -p ./tmp
fi

# Show options menu
echo "Select an option:"
echo "1. Build and run with Docker Compose (recommended)"
echo "2. Build Docker image only"
echo "3. Run Docker image (after building)"
echo "4. Stop running containers"
echo "5. Exit"

read -p "Enter your choice (1-5): " choice

case $choice in
  1)
    echo -e "${GREEN}Building and starting with Docker Compose...${NC}"
    docker-compose up --build -d
    
    echo ""
    echo -e "${GREEN}Container started successfully!${NC}"
    echo "Access the application at: http://localhost:8080"
    echo "Access Swagger UI at: http://localhost:8080/swagger-ui.html"
    ;;
    
  2)
    echo -e "${GREEN}Building Docker image only...${NC}"
    docker build -t norseintel-cloud:latest .
    echo -e "${GREEN}Image built successfully!${NC}"
    ;;
    
  3)
    echo -e "${GREEN}Running Docker container...${NC}"
    docker run -d -p 8080:8080 --name norseintel-cloud norseintel-cloud:latest
    echo -e "${GREEN}Container started successfully!${NC}"
    echo "Access the application at: http://localhost:8080"
    echo "Access Swagger UI at: http://localhost:8080/swagger-ui.html"
    ;;
    
  4)
    echo -e "${YELLOW}Stopping running containers...${NC}"
    docker-compose down
    echo -e "${GREEN}Containers stopped successfully!${NC}"
    ;;
    
  5)
    echo -e "${GREEN}Exiting...${NC}"
    exit 0
    ;;
    
  *)
    echo -e "${RED}Invalid option. Exiting.${NC}"
    exit 1
    ;;
esac