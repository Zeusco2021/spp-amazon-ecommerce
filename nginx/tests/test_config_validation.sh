#!/bin/bash
# =============================================================================
# test_config_validation.sh - Nginx Configuration Validation Tests
# Amazon Clone E-Commerce
# =============================================================================
# Valida que nginx.conf contiene todas las directivas requeridas por los
# requisitos 21-26 sin necesidad de un servidor Nginx en ejecución.
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONF_FILE="${SCRIPT_DIR}/../nginx.conf"

# Colores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

TESTS_PASSED=0
TESTS_FAILED=0

log_pass() { echo -e "${GREEN}[PASS]${NC} $*"; TESTS_PASSED=$((TESTS_PASSED + 1)); }
log_fail() { echo -e "${RED}[FAIL]${NC} $*"; TESTS_FAILED=$((TESTS_FAILED + 1)); }
log_section() { echo -e "\n${BLUE}=== $* ===${NC}"; }

assert_in_conf() {
    local desc="$1"
    local pattern="$2"
    if grep -qE "$pattern" "$CONF_FILE"; then
        log_pass "$desc"
    else
        log_fail "$desc (pattern: '$pattern' not found in nginx.conf)"
    fi
}

# Verify config file exists
if [ ! -f "$CONF_FILE" ]; then
    echo -e "${RED}ERROR: nginx.conf not found at $CONF_FILE${NC}"
    exit 1
fi

echo "Validating: $CONF_FILE"

# ---------------------------------------------------------------------------
# Requisito 21: Load Balancer - Distribución de Carga
# ---------------------------------------------------------------------------
log_section "Requisito 21: Load Distribution"

assert_in_conf "21.1 - upstream block defined" "upstream api_gateway_backend"
assert_in_conf "21.2 - least_conn algorithm" "least_conn"
assert_in_conf "21.6 - api-gateway-1:8080 in upstream" "api-gateway-1:8080"
assert_in_conf "21.6 - api-gateway-2:8080 in upstream" "api-gateway-2:8080"
assert_in_conf "21.6 - api-gateway-3:8080 in upstream" "api-gateway-3:8080"

# ---------------------------------------------------------------------------
# Requisito 22: SSL/TLS Termination
# ---------------------------------------------------------------------------
log_section "Requisito 22: SSL/TLS Termination"

assert_in_conf "22.1 - listen 443 ssl" "listen 443 ssl"
assert_in_conf "22.2 - listen 80" "listen 80"
assert_in_conf "22.3 - ssl_certificate configured" "ssl_certificate[^_]"
assert_in_conf "22.3 - ssl_certificate_key configured" "ssl_certificate_key"
assert_in_conf "22.4 - TLSv1.2 protocol" "TLSv1\.2"
assert_in_conf "22.4 - TLSv1.3 protocol" "TLSv1\.3"
assert_in_conf "22.5 - ssl_ciphers HIGH" "ssl_ciphers.*HIGH"
assert_in_conf "22.5 - ssl_ciphers excludes aNULL" "!aNULL"
assert_in_conf "22.6 - ssl_prefer_server_ciphers on" "ssl_prefer_server_ciphers on"
assert_in_conf "22.7 - proxy_pass to http backend" "proxy_pass http://"
assert_in_conf "22.8 - X-Forwarded-Proto header" "X-Forwarded-Proto"
assert_in_conf "22.9 - X-Real-IP header" "X-Real-IP"
assert_in_conf "22.10 - X-Forwarded-For header" "X-Forwarded-For"

# ---------------------------------------------------------------------------
# Requisito 23: Health Checks
# ---------------------------------------------------------------------------
log_section "Requisito 23: Health Checks"

assert_in_conf "23.2 - max_fails=3" "max_fails=3"
assert_in_conf "23.4/23.5 - fail_timeout=30s" "fail_timeout=30s"
assert_in_conf "23.6 - /health endpoint" "location /health"
assert_in_conf "23.7 - /health returns 200" "return 200"

# ---------------------------------------------------------------------------
# Requisito 24: Rate Limiting
# ---------------------------------------------------------------------------
log_section "Requisito 24: Rate Limiting"

assert_in_conf "24.1 - limit_req_zone by IP" "limit_req_zone \\\$binary_remote_addr"
assert_in_conf "24.2 - rate=100r/s" "rate=100r/s"
assert_in_conf "24.3 - burst=200" "burst=200"
assert_in_conf "24.4 - limit_req_status 429" "limit_req_status 429"
assert_in_conf "24.5 - zone size 10m" "10m"
assert_in_conf "24.6 - nodelay" "nodelay"

# ---------------------------------------------------------------------------
# Requisito 25: Failover and Retry
# ---------------------------------------------------------------------------
log_section "Requisito 25: Failover and Retry"

assert_in_conf "25.1 - retry on http_500" "http_500"
assert_in_conf "25.2 - retry on http_502" "http_502"
assert_in_conf "25.3 - retry on http_503" "http_503"
assert_in_conf "25.4 - retry on timeout" "proxy_next_upstream.*timeout"
assert_in_conf "25.5 - retry on error" "proxy_next_upstream.*error"
assert_in_conf "25.6 - max 2 retries" "proxy_next_upstream_tries 2"
assert_in_conf "25.7 - connect timeout 60s" "proxy_connect_timeout 60s"
assert_in_conf "25.8 - send timeout 60s" "proxy_send_timeout.*60s"
assert_in_conf "25.9 - read timeout 60s" "proxy_read_timeout.*60s"

# ---------------------------------------------------------------------------
# Requisito 26: Buffering and Performance
# ---------------------------------------------------------------------------
log_section "Requisito 26: Buffering and Performance"

assert_in_conf "26.1 - proxy_buffering on" "proxy_buffering.*on"
assert_in_conf "26.2 - proxy_buffer_size 4k" "proxy_buffer_size.*4k"
assert_in_conf "26.3 - proxy_buffers 8 4k" "proxy_buffers.*8.*4k"
assert_in_conf "26.4 - HTTP/1.1 to backend" "proxy_http_version 1\.1"
assert_in_conf "26.5 - HTTP/2 for clients" "http2"
assert_in_conf "26.6 - Host header preserved" "proxy_set_header Host"
assert_in_conf "26.7 - gzip enabled" "gzip on"
assert_in_conf "26.7 - gzip_min_length 1024" "gzip_min_length 1024"

# ---------------------------------------------------------------------------
# Additional: client_max_body_size
# ---------------------------------------------------------------------------
log_section "Additional: Client Body Size"
assert_in_conf "client_max_body_size 10M" "client_max_body_size 10M"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
echo "================================"
TOTAL=$((TESTS_PASSED + TESTS_FAILED))
echo -e "${GREEN}Passed:${NC} $TESTS_PASSED / $TOTAL"
echo -e "${RED}Failed:${NC} $TESTS_FAILED / $TOTAL"

if [ "$TESTS_FAILED" -eq 0 ]; then
    echo -e "\n${GREEN}All configuration validation tests passed!${NC}"
    exit 0
else
    echo -e "\n${RED}$TESTS_FAILED configuration validation test(s) failed.${NC}"
    exit 1
fi
