#!/usr/bin/env bash
set -Eeuo pipefail

# Выполняется на GitHub runner.
# Секреты передаются только через окружение/stdin.
for name in USER_IP USERNAME ROOT_PASSWORD DEPLOY_KNOWN_HOSTS \
    DOCKERHUB_USERNAME DOCKERHUB_TOKEN TOGO_RELEASE_SHA \
    GRAFANA_USER GRAFANA_PASSWORD; do

    if [[ -z "${!name:-}" ]]; then
        echo "Не задан GitHub Secret или параметр: $name" >&2
        exit 1
    fi
done

DEPLOY_PATH="${DEPLOY_PATH:-/root/ToGo}"
DEPLOY_PORT="${DEPLOY_PORT:-22}"

[[ "$TOGO_RELEASE_SHA" =~ ^[0-9a-f]{40}$ ]]
[[ "$USER_IP" =~ ^[a-zA-Z0-9.:-]+$ ]]
[[ "$USERNAME" =~ ^[a-z_][a-z0-9_-]*$ ]]
[[ "$DOCKERHUB_USERNAME" =~ ^[a-z0-9][a-z0-9_-]*$ ]]
[[ "$DEPLOY_PORT" =~ ^[0-9]{1,5}$ ]] &&
    (( 10#$DEPLOY_PORT > 0 && 10#$DEPLOY_PORT < 65536 ))
[[ "$DEPLOY_PATH" =~ ^/[a-zA-Z0-9_./-]+$ && "$DEPLOY_PATH" != *..* ]]

command -v sshpass >/dev/null

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

ssh_dir="$(mktemp -d)"
trap 'rm -rf "$ssh_dir"' EXIT

chmod 700 "$ssh_dir"

printf '%s\n' "$DEPLOY_KNOWN_HOSTS" > "$ssh_dir/known_hosts"
chmod 600 "$ssh_dir/known_hosts"

ssh_command=(
    sshpass -e ssh
    -p "$DEPLOY_PORT"
    -o BatchMode=no
    -o PreferredAuthentications=password
    -o PubkeyAuthentication=no
    -o NumberOfPasswordPrompts=1
    -o StrictHostKeyChecking=yes
    -o "UserKnownHostsFile=$ssh_dir/known_hosts"
    -o ConnectTimeout=15
)

destination="$USERNAME@$USER_IP"
release_dir="$DEPLOY_PATH/.deploy/releases/$TOGO_RELEASE_SHA"

# Создаём директорию релиза.
SSHPASS="$ROOT_PASSWORD" \
    "${ssh_command[@]}" "$destination" \
    "mkdir -p '$release_dir'"

# Передаём файлы релиза.
# .env, сертификаты и исходники приложения не входят в архив.
tar -C "$root" -czf - \
    infra/docker-compose.prod.yml \
    infra/nginx \
    infra/monitoring \
    scripts/deploy.sh |
    SSHPASS="$ROOT_PASSWORD" \
    "${ssh_command[@]}" "$destination" \
    "tar -xzf - -C '$release_dir'"

# Передаём секреты через stdin:
# 1. Docker Hub token
# 2. Grafana user
# 3. Grafana password
{
    printf '%s\n' "$DOCKERHUB_TOKEN"
    printf '%s\n' "$GRAFANA_USER"
    printf '%s\n' "$GRAFANA_PASSWORD"
} |
    SSHPASS="$ROOT_PASSWORD" \
    "${ssh_command[@]}" "$destination" \
    "bash '$release_dir/scripts/deploy.sh' \
        '$TOGO_RELEASE_SHA' \
        '$DEPLOY_PATH' \
        '$DOCKERHUB_USERNAME'"
