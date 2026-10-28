#!/bin/sh
set -eu
: "${IMAGE_TAG:?IMAGE_TAG is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
DOCKER_NETWORK=${DOCKER_NETWORK:-portfolio-network}
DB_HOST=${DB_HOST:-portfolio-postgres}
DB_NAME=${DB_NAME:-investor_profile}
DB_USERNAME=${DB_USERNAME:-portfolio}
AUTH0_ISSUER=${AUTH0_ISSUER:-https://dev-s41b58hqkvvmdu13.us.auth0.com/}
AUTH0_AUDIENCE=${AUTH0_AUDIENCE:-https://api.crescit-investimentos}
FRONTEND_URL=${FRONTEND_URL:-https://crescit-frontend.vercel.app}
runtime_env_file=${RABBITMQ_ENV_FILE:-/home/ubuntu/crescit-runtime/rabbitmq.env}
if [ -r "$runtime_env_file" ]; then . "$runtime_env_file"; fi
RABBITMQ_HOST=${RABBITMQ_HOST:-crescit-rabbitmq}
case "$IMAGE_TAG" in larinhazzz/perfilinvestidor-ci:?*) ;; *) echo 'Unexpected Profile image' >&2; exit 1 ;; esac
docker network inspect "$DOCKER_NETWORK" >/dev/null
docker exec "$DB_HOST" pg_isready -U "$DB_USERNAME" -d postgres
PGPASSWORD=$DB_PASSWORD docker exec -i -e PGPASSWORD "$DB_HOST" \
  psql -X -h 127.0.0.1 -U "$DB_USERNAME" -d postgres -v ON_ERROR_STOP=1 \
  -v db_name="$DB_NAME" -v db_user="$DB_USERNAME" <<'SQL'
SELECT format('CREATE DATABASE %I OWNER %I', :'db_name', :'db_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'db_name')\gexec
SQL
if [ "${PULL_IMAGE:-true}" = true ]; then docker pull "$IMAGE_TAG"; fi
# Retain the previous container configuration for recovery.
if docker container inspect perfilinvestidor >/dev/null 2>&1; then
  if docker container inspect perfilinvestidor-previous >/dev/null 2>&1; then
    umask 077
    backup_dir=/home/ubuntu/crescit-runtime/profile-backups
    mkdir -p "$backup_dir"
    docker inspect perfilinvestidor-previous > "$backup_dir/$(date +%Y%m%d%H%M%S).json"
    docker rm perfilinvestidor-previous >/dev/null
  fi
  docker stop perfilinvestidor >/dev/null
  docker rename perfilinvestidor perfilinvestidor-previous
fi
docker run -d --name perfilinvestidor --restart unless-stopped \
  --network "$DOCKER_NETWORK" --memory 512m -p 8080:8080 \
  --log-opt max-size=10m --log-opt max-file=3 \
  -e JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60.0 \
  -e DB_URL="jdbc:postgresql://$DB_HOST:5432/$DB_NAME" \
  -e DB_USERNAME="$DB_USERNAME" -e DB_PASSWORD \
  -e AUTH0_ISSUER="$AUTH0_ISSUER" -e AUTH0_AUDIENCE="$AUTH0_AUDIENCE" \
  -e FRONTEND_URL="$FRONTEND_URL" -e RABBITMQ_HOST="$RABBITMQ_HOST" \
  -e RABBITMQ_USERNAME="${RABBITMQ_USERNAME:-crescit}" \
  -e RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD:-guest}" \
  "$IMAGE_TAG"
attempt=0
while [ "$attempt" -lt 60 ]; do
  code=$(curl -sS --max-time 3 -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/profiles/me || true)
  if [ "$code" = 401 ]; then echo 'Profile is ready; authentication is enforced.'; exit 0; fi
  if [ "$(docker inspect --format '{{.State.Running}}' perfilinvestidor)" != true ]; then break; fi
  attempt=$((attempt + 1))
  sleep 2
done
docker logs --tail 80 perfilinvestidor >&2
echo 'Profile did not become ready.' >&2
exit 1
