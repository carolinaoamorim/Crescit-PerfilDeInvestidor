#!/bin/sh
set -eu
runtime_dir=/home/ubuntu/crescit-runtime
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
umask 077
mkdir -p "$runtime_dir/caddy"
cp "$script_dir/Caddyfile" "$runtime_dir/caddy/Caddyfile"
docker pull caddy:2.11.7-alpine
docker run --rm -v "$runtime_dir/caddy:/etc/caddy:ro" caddy:2.11.7-alpine \
  caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
docker volume create crescit-caddy-data >/dev/null
docker volume create crescit-caddy-config >/dev/null
if docker container inspect crescit-proxy >/dev/null 2>&1; then
  docker stop crescit-proxy >/dev/null
  docker rename crescit-proxy "crescit-proxy-backup-$(date +%Y%m%d%H%M%S)"
fi
docker run -d --name crescit-proxy --restart unless-stopped \
    --network portfolio-network --memory 128m \
    --log-opt max-size=10m --log-opt max-file=3 \
    -p 80:80 -p 8081:8081 \
    -v "$runtime_dir/caddy:/etc/caddy:ro" \
    -v crescit-caddy-data:/data -v crescit-caddy-config:/config \
    caddy:2.11.7-alpine
echo 'HTTPS proxy configured on TCP 8081. Certificate renewal uses HTTP on TCP 80.'
docker logs --tail 20 crescit-proxy
