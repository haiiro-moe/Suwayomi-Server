#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

if [[ "${1:-}" != "--yes" ]]; then
    printf 'This removes only the Suwairo Compose containers, image, network, and volume.\n'
    printf 'It does not remove files outside Docker or the branding directory.\n'
    read -r -p 'Continue? [y/N] ' answer
    [[ "$answer" =~ ^[Yy]$ ]] || { printf 'Cancelled.\n'; exit 0; }
fi

read -r -p 'Owner username [admin]: ' owner_username
owner_username="${owner_username:-admin}"
read -r -s -p 'Owner password: ' owner_password
printf '\n'

if [[ -z "$owner_password" ]]; then
    printf 'Owner password must not be empty.\n' >&2
    exit 1
fi
if [[ "$owner_password" == *$'\n'* || "$owner_password" == *$'\r'* ]]; then
    printf 'Owner password must not contain line breaks.\n' >&2
    exit 1
fi
if [[ "$owner_username" == *$'\n'* || "$owner_username" == *$'\r'* ]]; then
    printf 'Owner username must not contain line breaks.\n' >&2
    exit 1
fi

export COMPOSE_PROJECT_NAME=suwairo
export SUWAIRO_AUTH_MODE=UI_LOGIN
export SUWAIRO_AUTH_USERNAME="$owner_username"
export SUWAIRO_AUTH_PASSWORD="$owner_password"

printf 'Removing the existing test environment...\n'
docker compose down --volumes --remove-orphans --rmi local

printf 'Building the server from scratch...\n'
docker compose build --no-cache

printf 'Starting the fresh test environment...\n'
docker compose up -d

printf 'Waiting for the server health check...\n'
for _ in {1..60}; do
    status="$(docker compose ps --format '{{.Health}}' server 2>/dev/null || true)"
    if [[ "$status" == 'healthy' ]]; then
        printf 'Ready at http://127.0.0.1:%s/\n' "${SUWAIRO_PORT:-4567}"
        printf 'Log in with the owner username and password supplied above.\n'
        exit 0
    fi
    sleep 2
done

printf 'The server did not become healthy. Recent logs:\n' >&2
docker compose logs --tail=100 server >&2
exit 1
