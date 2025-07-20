# build.sh
#!/bin/bash

set -e

echo "Building PostgreSQL MCP Server..."

# Clean previous builds
echo "Cleaning previous builds..."
./gradlew clean

# Run tests
echo "Running tests..."
./gradlew test

# Build the application
echo "Building application..."
./gradlew build

# Build Docker image
echo "Building Docker image..."
docker build -t postgres-mcp-server:latest .

echo "Build completed successfully!"
echo "Docker image: postgres-mcp-server:latest"