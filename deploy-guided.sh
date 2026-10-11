#!/usr/bin/env bash
#
# Suwairo — guided production deployment.
#
# Interactive setup that asks for everything the stack needs, generates secrets
# automatically (printing them at the end), builds the production image, starts
# the containers, and verifies the deployment.
#
# Supported modes:
#   1. UI login  - local accounts, owner created on first start
#   2. SSO (OIDC) - login handled by an external provider (Tinyauth + LLDAP)
#
# Usage: ./deploy-guided.sh
set -euo pipefail

cd "$(dirname "$0")"

# ── styling ──────────────────────────────────────────────────────────────────
if [ -t 1 ]; then
    BOLD=$'\033[1m'; DIM=$'\033[2m'; RESET=$'\033[0m'
    RED=$'\033[31m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; BLUE=$'\033[34m'; CYAN=$'\033[36m'
else
    BOLD=''; DIM=''; RESET=''; RED=''; GREEN=''; YELLOW=''; BLUE=''; CYAN=''
fi

step()    { printf '\n%s== %s ==%s\n' "$BOLD$BLUE" "$1" "$RESET"; }
info()    { printf '  %s\n' "$1"; }
hint()    { printf '  %s%s%s\n' "$DIM" "$1" "$RESET"; }
ok()      { printf '  %s✔%s %s\n' "$GREEN" "$RESET" "$1"; }
warn()    { printf '  %s!%s %s\n' "$YELLOW" "$RESET" "$1"; }
die()     { printf '  %s✘ %s%s\n' "$RED" "$1" "$RESET" >&2; exit 1; }

prompt() { # prompt "label" "default" -> echoes answer
    local label="$1" default="${2:-}" answer
    if [ -n "$default" ]; then
        read -r -p "$(printf '%s%s%s [%s%s%s]: ' "$BOLD" "$label" "$RESET" "$DIM" "$default" "$RESET")" answer
        printf '%s' "${answer:-$default}"
    else
        read -r -p "$(printf '%s%s%s: ' "$BOLD" "$label" "$RESET")" answer
        printf '%s' "$answer"
    fi
}

prompt_secret() { # hidden input; blank = keep existing or generate
    local label="$1" existing="${2:-}" answer
    if [ -n "$existing" ]; then
        read -r -s -p "$(printf '%s%s%s (blank = keep existing): ' "$BOLD" "$label" "$RESET")" answer
        printf '\n' >&2
        printf '%s' "${answer:-$existing}"
    else
        read -r -s -p "$(printf '%s%s%s (blank = autogenerate): ' "$BOLD" "$label" "$RESET")" answer
        printf '\n' >&2
        printf '%s' "$answer"
    fi
}

generate_secret() { # 32-byte hex
    openssl rand -hex 24 2>/dev/null || head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n'
}

require() {
    command -v "$1" >/dev/null 2>&1 || die "$1 is required but not installed"
}

# ── summary collection ───────────────────────────────────────────────────────
SUMMARY_SECRETS=()
note_secret() { # "label" "value" "origin"  origin: generated|entered|kept
    SUMMARY_SECRETS+=("$1|$2|$3")
}

# ── preflight ────────────────────────────────────────────────────────────────
step "Preflight"
require docker
require openssl
docker compose version >/dev/null 2>&1 || die "Docker Compose v2 is required"
ok "docker and docker compose available"

if [ -f .env ]; then
    info "Existing .env found - values shown as defaults, secrets kept unless regenerated."
    KEEPING=1
else
    KEEPING=0
fi

# ── basics ───────────────────────────────────────────────────────────────────
step "Basics"
hint "The port the server listens on. Data is persisted in the suwairo-data volume."
PORT="$(prompt "HTTP port" "${SUWAIRO_PORT:-4567}")"
TIMEZONE="$(prompt "Timezone" "${TZ_KEPT:-Etc/UTC}")"

# ── authentication ───────────────────────────────────────────────────────────
step "Authentication"
hint "1) UI login   - local accounts, passwords stored in Suwairo"
hint "2) SSO (OIDC) - login handled by an external provider (e.g. Tinyauth + LLDAP)"
hint "   SSO users are auto-provisioned on first login with a default role."
while true; do
    MODE="$(prompt "Authentication mode [1/2]" "1")"
    case "$MODE" in
        1) AUTH_MODE="UI_LOGIN"; break ;;
        2) AUTH_MODE="SSO"; break ;;
        *) warn "Please enter 1 or 2" ;;
    esac
done

SSO_ISSUER_URL='' SSO_CLIENT_ID='' SSO_CLIENT_SECRET='' SSO_DEFAULT_ROLE='' SSO_SCOPE='openid profile email'
if [ "$AUTH_MODE" = "SSO" ]; then
    hint ""
    hint "Register this redirect URI with your OIDC provider before continuing:"
    printf '  %s%s/sso/callback%s\n\n' "$BOLD$CYAN" "${PUBLIC_URL:-http://<server-address>:${PORT}}" "$RESET"
    SSO_ISSUER_URL="$(prompt "OIDC issuer URL (Tinyauth base URL)" "${SUWAIRO_SSO_ISSUER_URL:-https://auth.example.com}")"
    SSO_CLIENT_ID="$(prompt "OIDC client ID" "${SUWAIRO_SSO_CLIENT_ID:-suwairo}")"
    SSO_CLIENT_SECRET="$(prompt_secret "OIDC client secret" "${SUWAIRO_SSO_CLIENT_SECRET:-}")"
    if [ -z "$SSO_CLIENT_SECRET" ]; then
        SSO_CLIENT_SECRET="$(generate_secret)"
        note_secret "OIDC client secret" "$SSO_CLIENT_SECRET" "generated"
    else
        note_secret "OIDC client secret" "$SSO_CLIENT_SECRET" "entered"
    fi
    hint "The default role is assigned to SSO users on their first login. It must exist in Suwairo"
    hint "before they log in (create it under Administration afterwards if it does not exist yet)."
    SSO_DEFAULT_ROLE="$(prompt "Default role for new SSO users" "${SUWAIRO_SSO_DEFAULT_ROLE:-sso}")"
    SSO_SCOPE="$(prompt "OIDC scopes" "${SUWAIRO_SSO_SCOPE:-openid profile email}")"
fi

# ── vpn ──────────────────────────────────────────────────────────────────────
step "VPN"
hint "Optionally route outbound traffic (sources, FlareSolverr) through Mullvad over WireGuard,"
hint "e.g. when Cloudflare blocks this server's IP. Generate a WireGuard key and download a Linux"
hint "config at https://mullvad.net/en/account/wireguard-config, then copy PrivateKey and Address."
VPN_DEFAULT="n"
if [ -n "${MULLVAD_WIREGUARD_PRIVATE_KEY:-}" ]; then VPN_DEFAULT="y"; fi
USE_VPN="$(prompt "Use Mullvad VPN? [y/n]" "$VPN_DEFAULT")"
WG_PRIVATE_KEY='' WG_ADDRESSES='' VPN_COUNTRIES='' VPN_CITIES=''
if [[ "$USE_VPN" == "y" || "$USE_VPN" == "Y" ]]; then
    USE_VPN="y"
    while [ -z "$WG_PRIVATE_KEY" ]; do
        WG_PRIVATE_KEY="$(prompt_secret "WireGuard private key" "${MULLVAD_WIREGUARD_PRIVATE_KEY:-}")"
        [ -n "$WG_PRIVATE_KEY" ] || warn "The private key is required"
    done
    while [ -z "$WG_ADDRESSES" ]; do
        WG_ADDRESSES="$(prompt "WireGuard address (e.g. 10.64.12.34/32)" "${MULLVAD_WIREGUARD_ADDRESSES:-}")"
    done
    VPN_COUNTRIES="$(prompt "Server countries (comma separated, blank = any)" "${MULLVAD_SERVER_COUNTRIES:-}")"
    VPN_CITIES="$(prompt "Server cities (comma separated, blank = any)" "${MULLVAD_SERVER_CITIES:-}")"
else
    USE_VPN="n"
fi

# ── write .env ───────────────────────────────────────────────────────────────
step "Writing .env"
{
    echo "# Generated by deploy-guided.sh - contains secrets, do not commit"
    echo "SUWAIRO_PORT=$PORT"
    echo "TZ=$TIMEZONE"
    echo "SUWAIRO_AUTH_MODE=$AUTH_MODE"
    if [ -n "$SSO_ISSUER_URL" ];      then echo "SUWAIRO_SSO_ISSUER_URL=$SSO_ISSUER_URL"; fi
    if [ -n "$SSO_CLIENT_ID" ];       then echo "SUWAIRO_SSO_CLIENT_ID=$SSO_CLIENT_ID"; fi
    if [ -n "$SSO_CLIENT_SECRET" ];   then echo "SUWAIRO_SSO_CLIENT_SECRET=$SSO_CLIENT_SECRET"; fi
    if [ -n "$SSO_DEFAULT_ROLE" ];    then echo "SUWAIRO_SSO_DEFAULT_ROLE=$SSO_DEFAULT_ROLE"; fi
    if [ -n "$SSO_SCOPE" ];           then echo "SUWAIRO_SSO_SCOPE=$SSO_SCOPE"; fi
    if [ "$USE_VPN" = "y" ]; then
        echo "MULLVAD_WIREGUARD_PRIVATE_KEY=$WG_PRIVATE_KEY"
        echo "MULLVAD_WIREGUARD_ADDRESSES=$WG_ADDRESSES"
        if [ -n "$VPN_COUNTRIES" ]; then echo "MULLVAD_SERVER_COUNTRIES=$VPN_COUNTRIES"; fi
        if [ -n "$VPN_CITIES" ];    then echo "MULLVAD_SERVER_CITIES=$VPN_CITIES"; fi
    fi
} > .env
chmod 600 .env
ok ".env written (permissions 600)"

COMPOSE_FILE="compose.yml"
if [ "$AUTH_MODE" = "SSO" ]; then
    # compose.sso.yml has the SSO environment wired up and reads overrides from .env
    COMPOSE_FILE="compose.sso.yml"
fi
COMPOSE_ARGS=(-f "$COMPOSE_FILE")
if [ "$USE_VPN" = "y" ]; then
    # routes the server and FlareSolverr through Mullvad
    COMPOSE_ARGS+=(-f compose.vpn.yml)
    COMPOSE_FILE="$COMPOSE_FILE + compose.vpn.yml"
fi
info "Using compose file: $COMPOSE_FILE"

# ── build ────────────────────────────────────────────────────────────────────
step "Building production image"
hint "First build compiles the Kotlin server and the WebUI - this can take several minutes."
docker compose "${COMPOSE_ARGS[@]}" build

# ── start ────────────────────────────────────────────────────────────────────
step "Starting containers"
docker compose "${COMPOSE_ARGS[@]}" up -d

# ── health ───────────────────────────────────────────────────────────────────
step "Waiting for the server to become healthy"
hint "The health check runs wget against the server's HTTP endpoint."
ATTEMPTS=0
until docker compose "${COMPOSE_ARGS[@]}" ps server --format '{{.Health}}' 2>/dev/null | grep -q healthy; do
    ATTEMPTS=$((ATTEMPTS + 1))
    if [ "$ATTEMPTS" -ge 36 ]; then
        docker compose "${COMPOSE_ARGS[@]}" logs --tail 40 server
        die "Server did not become healthy within 3 minutes - recent logs shown above"
    fi
    printf '.'
    sleep 5
done
printf '\n'
ok "Server is healthy"

# ── verification ─────────────────────────────────────────────────────────────
step "Verifying deployment"
HTTP_CODE="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${PORT}/" || echo 000)"
if [ "$HTTP_CODE" = "200" ]; then
    ok "HTTP check: http://127.0.0.1:${PORT}/ -> $HTTP_CODE"
else
    warn "HTTP check returned $HTTP_CODE (expected 200)"
fi

if [ "$AUTH_MODE" = "SSO" ]; then
    SSO_CODE="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${PORT}/sso/login" || echo 000)"
    case "$SSO_CODE" in
        000|500) warn "SSO endpoint check returned $SSO_CODE - verify the issuer URL is reachable from the server and the discovery document exists" ;;
        *) ok "SSO endpoint responded ($SSO_CODE)" ;;
    esac
fi

# ── summary ──────────────────────────────────────────────────────────────────
step "Deployment summary"
info "URL:        http://<server-address>:${PORT}"
info "Auth mode:  $AUTH_MODE"
info "Compose:    $COMPOSE_FILE (.env holds the values)"
if [ "$USE_VPN" = "y" ]; then
    info "VPN:        outbound traffic goes through Mullvad (check: docker compose ${COMPOSE_ARGS[*]} logs vpn)"
fi
if [ "$AUTH_MODE" = "UI_LOGIN" ]; then
    info "First run:  open the URL and create the owner account (username + password of your choice)."
else
    info "SSO login:  \"Sign in with SSO\" on the login page redirects to $SSO_ISSUER_URL"
    info "New users:  auto-provisioned with role \"$SSO_DEFAULT_ROLE\" (create it in Administration if missing)."
fi

if [ "${#SUMMARY_SECRETS[@]}" -gt 0 ]; then
    printf '\n%sGenerated secrets%s\n' "$BOLD" "$RESET"
    for entry in "${SUMMARY_SECRETS[@]}"; do
        IFS='|' read -r label value origin <<< "$entry"
        if [ "$origin" = "generated" ]; then
            printf '  %s: %s%s%s  %s(skipped - autogenerated)%s\n' "$label" "$BOLD" "$value" "$RESET" "$DIM" "$RESET"
        else
            printf '  %s: %s\n' "$label" "$value"
        fi
    done
else
    info "No secrets were autogenerated."
fi

printf '\n%sSecrets are stored in .env (mode 600). Keep a copy in your password manager.%s\n' "$DIM" "$RESET"

# ── follow logs ──────────────────────────────────────────────────────────────
printf '\n'
read -r -p "$(printf '%sFollow server logs?%s [Y/n] ' "$BOLD" "$RESET")" follow
if [[ "${follow:-y}" != "n" && "${follow:-y}" != "N" ]]; then
    docker compose "${COMPOSE_ARGS[@]}" logs -f server
fi
