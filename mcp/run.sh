#!/bin/bash

set -e

PROFILE=${1:-development}

echo "Starting PostgreSQL MCP Server with profile: $PROFILE"

if [ "$PROFILE" = "development" ]; then
    echo "Starting development environment..."
    docker-compose -f docker-compose.dev.yml up -d
    echo "Development environment started!"
    echo "Application: http://localhost:8080"
    echo "Database: localhost:5433"
elif [ "$PROFILE" = "production" ]; then
    echo "Starting production environment..."
    docker-compose up -d
    echo "Production environment started!"
    echo "Application: http://localhost:8080"
    echo "Database: localhost:5432"
    echo "PgAdmin: http://localhost:5050"
    echo "Prometheus: http://localhost:9090"
else
    echo "Unknown profile: $PROFILE"
    echo "Usage: ./run.sh [development|production]"
    exit 1
fi

echo ""
echo "Logs: docker-compose logs -f postgres-mcp"
echo "Stop: docker-compose down"