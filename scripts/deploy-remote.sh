#!/usr/bin/env bash
set -Eeuo pipefail

# Выполняется на GitHub runner. Секреты передаются только через окружение/stdin.
for name in DEPLOY_HOST DEPLOY_USER DEPLOY_SSH_KEY DEPLOY_KNOWN_HOSTS \
    DOCKERHUB_USERNAME DOCKERHUB_TOKEN TOGO_RELEASE_SHA; do
    if [[ -z "${!name:-}" ]]; then
        echo "Не задан GitHub Secret или параметр: $name" >&2
        exit 1
    fi
done

DEPLOY_PATH="${DEPLOY_PATH:-/root/ToGo}"
DEPLOY_PORT="${DEPLOY_PORT:-22}"
[[ "$TOGO_RELEASE_SHA" =~ ^[0-9a-f]{40}$ ]]
[[ "$DEPLOY_HOST" =~ ^[a-zA-Z0-9.:-]+$ ]]
[[ "$DEPLOY_USER" =~ ^[a-z_][a-z0-9_-]*$ ]]
[[ "$DOCKERHUB_USERNAME" =~ ^[a-z0-9][a-z0-9_-]*$ ]]
[[ "$DEPLOY_PORT" =~ ^[0-9]{1,5}$ ]] && (( 10#$DEPLOY_PORT > 0 && 10#$DEPLOY_PORT < 65536 ))
[[ "$DEPLOY_PATH" =~ ^/[a-zA-Z0-9_./-]+$ && "$DEPLOY_PATH" != *..* ]]

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ssh_dir=$(mktemp -d)
trap 'rm -rf "$ssh_dir"' EXIT
chmod 700 "$ssh_dir"
printf '%s\n' "$DEPLOY_SSH_KEY" > "$ssh_dir/key"
printf '%s\n' "$DEPLOY_KNOWN_HOSTS" > "$ssh_dir/known_hosts"
chmod 600 "$ssh_dir/key" "$ssh_dir/known_hosts"

ssh_options=(-i "$ssh_dir/key" -p "$DEPLOY_PORT" -o BatchMode=yes
    -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes
    -o "UserKnownHostsFile=$ssh_dir/known_hosts" -o ConnectTimeout=15)
destination="$DEPLOY_USER@$DEPLOY_HOST"
release_dir="$DEPLOY_PATH/.deploy/releases/$TOGO_RELEASE_SHA"

ssh "${ssh_options[@]}" "$destination" "mkdir -p '$release_dir'"
# .env, сертификаты и исходники приложения не входят в архив релиза.
tar -C "$root" -czf - infra/docker-compose.prod.yml infra/nginx scripts/deploy.sh |
    ssh "${ssh_options[@]}" "$destination" "tar -xzf - -C '$release_dir'"
printf '%s\n' "$DOCKERHUB_TOKEN" |
    ssh "${ssh_options[@]}" "$destination" \
        "bash '$release_dir/scripts/deploy.sh' '$TOGO_RELEASE_SHA' '$DEPLOY_PATH' '$DOCKERHUB_USERNAME'"
