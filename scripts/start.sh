#!/usr/bin/env bash

set -Eeuo pipefail

# Корень проекта
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/infra/docker-compose.yml"

echo "=================================="
echo " Starting ToGo from source code"
echo "=================================="

# Проверяем Docker
if ! command -v docker &> /dev/null; then
    echo "ERROR: Docker is not installed"
    exit 1
fi

if ! docker info &> /dev/null; then
    echo "ERROR: Docker is not running"
    exit 1
fi

# Проверяем Docker Compose
if ! docker-compose version &> /dev/null; then
    echo "ERROR: Docker Compose is not installed"
    exit 1
fi

# Проверяем compose-файл
if [[ ! -f "$COMPOSE_FILE" ]]; then
    echo "ERROR: docker-compose.yml not found at $COMPOSE_FILE"
    exit 1
fi

cd "$ROOT_DIR"

echo "Building and starting services..."

if docker-compose \
    -f "$COMPOSE_FILE" \
    up --build -d --remove-orphans; then

    echo ""
    echo "=================================="
    echo " ToGo started successfully!"
    echo "=================================="
    echo ""
    docker-compose -f "$COMPOSE_FILE" ps
else
    echo ""
    echo "ERROR: Failed to start services"
    exit 1
fi
