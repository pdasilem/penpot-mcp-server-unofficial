#!/usr/bin/env bash
set -euo pipefail

ENV_FILE=".env"
VERSION_FILE="src/penpot/mcp/penpot/version.clj"

info() { echo -e "\033[0;32m[+]\033[0m $1"; }
fail() { echo -e "\033[0;31m[x]\033[0m $1" >&2; exit 1; }

server_version() {
  local supported fix
  supported="$(sed -n 's/^(def supported "\(.*\)")$/\1/p' "$VERSION_FILE")"
  fix="$(sed -n 's/^(def ^:private fix-release \([0-9]*\))$/\1/p' "$VERSION_FILE")"
  [ -n "$supported" ] && [ -n "$fix" ] || fail "cannot read the server version from $VERSION_FILE"
  echo "$supported.$fix"
}

require() {
  command -v "$1" >/dev/null 2>&1 || fail "$1 is required"
}

ask() {
  local var="$1" prompt="$2" secret="${3:-}" value=""
  while [ -z "$value" ]; do
    if [ -n "$secret" ]; then
      read -r -s -p "$prompt: " value
      echo
    else
      read -r -p "$prompt: " value
    fi
  done
  case "$value" in
    *"'"*|*$'\n'*) fail "$var must not contain single quotes or line breaks" ;;
  esac
  printf "%s='%s'\n" "$var" "$value" >> "$ENV_FILE.tmp"
}

write_env() {
  if [ -f "$ENV_FILE" ]; then
    read -r -p "$ENV_FILE exists. Overwrite? [y/N] " answer
    if [ "$answer" != "y" ]; then
      chmod 600 "$ENV_FILE"
      info "Keeping existing $ENV_FILE"
      return
    fi
  fi
  rm -f "$ENV_FILE.tmp"
  umask 077
  printf "PENPOT_BASE_URL='http://penpot-frontend:8080'\nLOG_LEVEL='info'\n" > "$ENV_FILE.tmp"
  ask PENPOT_ACCESS_TOKEN "Penpot access token" secret
  ask PENPOT_EMAIL "Penpot account email"
  ask PENPOT_PASSWORD "Penpot account password" secret
  ask PENPOT_MCP_KEY "Penpot MCP key" secret
  mv "$ENV_FILE.tmp" "$ENV_FILE"
  info "Wrote $ENV_FILE"
}

verify() {
  local url key status
  read -r -p "Public Penpot URL to verify (empty to skip): " url
  [ -n "$url" ] || return 0
  key="$(sed -n "s/^PENPOT_MCP_KEY='\(.*\)'$/\1/p" "$ENV_FILE")"
  status="$(printf 'url = "%s/mcp/stream?userToken=%s"\n' "${url%/}" "$key" | curl -s -K - --max-time 15 -o /dev/null -w '%{http_code}' -X POST \
    -H 'content-type: application/json' -H 'accept: application/json, text/event-stream' \
    --data '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"setup","version":"1"}}}')"
  if [ "$status" = "200" ]; then
    info "MCP endpoint answers at ${url%/}/mcp/stream"
  else
    fail "MCP endpoint returned HTTP $status"
  fi
}

require docker
docker compose version >/dev/null 2>&1 || fail "docker compose is required"
require curl

cd "$(dirname "$0")"
IMAGE="ghcr.io/pdasilem/penpot-mcp-server-unofficial:$(server_version)"
write_env
info "Pulling $IMAGE"
docker pull "$IMAGE"
info "Add the penpot-mcp service from docker-compose.penpot.yml to your Penpot compose file, replacing the official one,"
info "set enable-mcp and enable-access-tokens in PENPOT_FLAGS, then run: docker compose up -d penpot-mcp"
read -r -p "Press Enter when the service is running to verify it, or Ctrl+C to finish now. " _
verify
