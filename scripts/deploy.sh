#!/usr/bin/env bash
set -Eeuo pipefail

# Выполняется на VPS; токен реестра поступает через stdin, .env остаётся на VPS.
export TOGO_RELEASE_SHA="${1:?Нужен SHA релиза}"
deploy_path="${2:?Нужен путь установки}"
registry_user="${3:?Нужен логин Docker Hub}"
[[ "$TOGO_RELEASE_SHA" =~ ^[0-9a-f]{40}$ ]]
release_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="$deploy_path/infra/.env"
if [[ ! -s "$env_file" ]]; then
    echo "Не найден файл настроек: $env_file" >&2
    exit 1
fi
for command in docker flock curl; do
    command -v "$command" > /dev/null
done
docker compose version > /dev/null

mkdir -p "$deploy_path/.deploy"
exec 9> "$deploy_path/.deploy/lock"
flock -w 300 9

# Сохраняем Compose project и том существующей базы, не создаём пустую базу.
project=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' togodb)
TOGO_POSTGRES_VOLUME=$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql"}}{{.Name}}{{end}}{{end}}' togodb)
export TOGO_POSTGRES_VOLUME
if [[ ! "$project" =~ ^[a-z0-9][a-z0-9_-]*$ || -z "$TOGO_POSTGRES_VOLUME" ]]; then
    echo 'Не удалось определить Compose project или именованный том существующей базы.' >&2
    exit 1
fi
if [[ "$(docker inspect -f '{{.State.Running}}' togodb)" != true ]]; then
    echo 'Существующий PostgreSQL должен быть запущен перед деплоем.' >&2
    exit 1
fi

# Значения приложения берём из серверного .env, а не из SSH-сессии.
unset DB_USER DB_PASSWORD TELEGRAM_BOT_TOKEN TELEGRAM_WEBHOOK_SECRET
compose=(docker compose --project-name "$project" --env-file "$env_file"
    -f "$release_dir/infra/docker-compose.prod.yml")
"${compose[@]}" config --quiet

auth_dir=$(mktemp -d)
trap 'rm -rf "$auth_dir"' EXIT
export DOCKER_CONFIG="$auth_dir"
IFS= read -r registry_token
printf '%s\n' "$registry_token" | docker login -u "$registry_user" --password-stdin > /dev/null
unset registry_token

# Сначала скачиваем все образы и проверяем конфиг, пока старый релиз ещё работает.
"${compose[@]}" pull backend frontend nginx
"${compose[@]}" run --rm --no-deps nginx nginx -t

# PostgreSQL намеренно не пересоздаётся; Flyway выполняется новым backend.
"${compose[@]}" up -d --no-deps --no-build --wait --wait-timeout 240 backend frontend nginx
"${compose[@]}" exec -T nginx nginx -t
"${compose[@]}" exec -T nginx nginx -s reload

for container in togo-backend togo-frontend; do
    actual=$(docker inspect -f '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$container")
    if [[ "$actual" != "$TOGO_RELEASE_SHA" ]]; then
        echo "Контейнер $container запущен с другой версией." >&2
        exit 1
    fi
done
curl --fail --silent --show-error --retry 5 --retry-all-errors --retry-delay 3 \
    --max-time 15 --resolve dev-togo.ru:443:127.0.0.1 https://dev-togo.ru/ -o /dev/null

if [[ -f "$deploy_path/.deploy/current" ]]; then
    cp "$deploy_path/.deploy/current" "$deploy_path/.deploy/previous"
fi
printf '%s\n' "$TOGO_RELEASE_SHA" > "$deploy_path/.deploy/current"
echo "Релиз $TOGO_RELEASE_SHA успешно развёрнут."
