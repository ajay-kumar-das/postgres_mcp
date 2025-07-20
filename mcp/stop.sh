#!/bin/bash

PROFILE=${1:-development}

echo "Stopping PostgreSQL MCP Server..."

if [ "$PROFILE" = "development" ]; then
    docker-compose -f docker-compose.dev.yml down
elif [ "$PROFILE" = "production" ]; then
    docker-compose down
else
    echo "Unknown profile: $PROFILE"
    echo "Usage: ./stop.sh [development|production]"
    exit 1
fi

echo "PostgreSQL MCP Server stopped!"