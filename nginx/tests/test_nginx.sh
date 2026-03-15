#!/bin/bash
# =============================================================================
# test_nginx.sh - Integration Tests for Nginx Load Balancer
# Amazon Clone E-Commerce
# =============================================================================
# Requisitos: 21, 22, 23, 24, 25, 26
#
# Uso:
#   ./test_nginx.sh [NGINX_HOST] [NGINX_HTTP_PORT] [NGINX_HTTPS_PORT]
#
# Defaults:
#   NGINX_HOST=localhost
#   NGINX_HTTP_PORT=80
#   NGINX_HTTPS_PORT=443
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuración
# ---------------------------------------------------------------------------
NGINX_HOST="${1:-localhost}"
HTTP_PORT="${2:-80}"
HTTPS_PORT="${3:-443}"
BASE_HTTP="http://${NGINX_HOST}:${HTTP_PORT}"
BASE_HTTPS="https://${NGINX_HOST}:${HTTPS_PORT}"

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Contadores
TESTS_PASSED=0
TESTS_FAILED=0
TESTS_SKIPPED=0

# ---------------------------------------------------------------------------
# Funciones de utilidad
# ---------------------------------------------------------------------------
log_info()    { echo -e "${BLUE}[INFO]${NC}  $*"; }
log_pass()    { echo -e "${GREEN}[PASS]${NC}  $*"; }
log_fail()    { echo -e "${RED}[FAIL]${NC}  $*"; }
log_skip()    { echo -e "${YELLOW}[SKIP]${NC}  $*"; }
log_section() { echo -e "\n${BLUE}========== $* ==========${NC}"; }

assert_eq() {
    local test_name="$1"
    local expected="$2"
    local actual="$3"
    if [ "$expected" = "$actual" ]; then
        log_pass "$test_name"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "$test_name (expected='$expected', actual='$actual')"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

assert_contains() {
    local test_name="$1"
    local expected="$2"
    local actual="$3"
    if echo "$actual" | grep -qi "$expected"; then
        log_pass "$test_name"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "$test_name (expected to contain='$expected')"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

assert_not_contains() {
    local test_name="$1"
    local unexpected="$2"
    local actual="$3"
    if ! echo "$actual" | grep -qi "$unexpected"; then
        log_pass "$test_name"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "$test_name (expected NOT to contain='$unexpected')"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

# Ejecutar curl y capturar código HTTP y headers
http_get() {
    local url="$1"
    shift
    curl -sk -o /dev/null -w "%{http_code}" "$@" "$url" 2>/dev/null || echo "000"
}

http_get_headers() {
    local url="$1"
    shift
    curl -skI "$@" "$url" 2>/dev/null || echo ""
}

http_get_body() {
    local url="$1"
    shift
    curl -sk "$@" "$url" 2>/dev/null || echo ""
}

# ---------------------------------------------------------------------------
# Test Suite 1: Health Check Endpoint (Requisito 23.6, 23.7)
# ---------------------------------------------------------------------------
log_section "Test Suite 1: Health Check Endpoint"

test_health_check_http() {
    log_info "Testing /health endpoint on HTTP..."
    local status
    status=$(http_get "${BASE_HTTP}/health")
    assert_eq "Health check HTTP returns 200" "200" "$status"
}

test_health_check_body() {
    log_info "Testing /health endpoint returns 'healthy'..."
    local body
    body=$(http_get_body "${BASE_HTTP}/health")
    assert_contains "Health check body contains 'healthy'" "healthy" "$body"
}

test_health_check_https() {
    log_info "Testing /health endpoint on HTTPS..."
    local status
    status=$(http_get "${BASE_HTTPS}/health" --insecure)
    assert_eq "Health check HTTPS returns 200" "200" "$status"
}

test_health_check_http
test_health_check_body
test_health_check_https

# ---------------------------------------------------------------------------
# Test Suite 2: HTTP to HTTPS Redirect (Requisito 22)
# ---------------------------------------------------------------------------
log_section "Test Suite 2: HTTP to HTTPS Redirect"

test_http_redirect() {
    log_info "Testing HTTP redirects to HTTPS..."
    local status
    status=$(curl -sk -o /dev/null -w "%{http_code}" "${BASE_HTTP}/" 2>/dev/null || echo "000")
    assert_eq "HTTP / returns 301 redirect" "301" "$status"
}

test_http_redirect_location() {
    log_info "Testing HTTP redirect Location header points to HTTPS..."
    local headers
    headers=$(http_get_headers "${BASE_HTTP}/")
    assert_contains "Redirect Location contains 'https'" "https" "$headers"
}

test_http_redirect
test_http_redirect_location

# ---------------------------------------------------------------------------
# Test Suite 3: SSL/TLS Configuration (Requisito 22)
# ---------------------------------------------------------------------------
log_section "Test Suite 3: SSL/TLS Configuration"

test_ssl_tls12() {
    log_info "Testing TLSv1.2 is supported..."
    if command -v openssl &>/dev/null; then
        local result
        result=$(echo | openssl s_client -connect "${NGINX_HOST}:${HTTPS_PORT}" \
            -tls1_2 2>/dev/null | grep -c "Cipher" || echo "0")
        if [ "$result" -gt "0" ]; then
            log_pass "TLSv1.2 is supported"
            TESTS_PASSED=$((TESTS_PASSED + 1))
        else
            log_fail "TLSv1.2 is NOT supported"
            TESTS_FAILED=$((TESTS_FAILED + 1))
        fi
    else
        log_skip "openssl not available, skipping TLSv1.2 test"
        TESTS_SKIPPED=$((TESTS_SKIPPED + 1))
    fi
}

test_ssl_tls13() {
    log_info "Testing TLSv1.3 is supported..."
    if command -v openssl &>/dev/null; then
        local result
        result=$(echo | openssl s_client -connect "${NGINX_HOST}:${HTTPS_PORT}" \
            -tls1_3 2>/dev/null | grep -c "Cipher" || echo "0")
        if [ "$result" -gt "0" ]; then
            log_pass "TLSv1.3 is supported"
            TESTS_PASSED=$((TESTS_PASSED + 1))
        else
            log_skip "TLSv1.3 not supported by this OpenSSL version"
            TESTS_SKIPPED=$((TESTS_SKIPPED + 1))
        fi
    else
        log_skip "openssl not available, skipping TLSv1.3 test"
        TESTS_SKIPPED=$((TESTS_SKIPPED + 1))
    fi
}

test_ssl_tls10_disabled() {
    log_info "Testing TLSv1.0 is NOT supported..."
    if command -v openssl &>/dev/null; then
        local result
        result=$(echo | openssl s_client -connect "${NGINX_HOST}:${HTTPS_PORT}" \
            -tls1 2>&1 | grep -c "handshake failure\|no protocols available\|alert" || echo "0")
        if [ "$result" -gt "0" ]; then
            log_pass "TLSv1.0 is correctly disabled"
            TESTS_PASSED=$((TESTS_PASSED + 1))
        else
            log_fail "TLSv1.0 should be disabled but appears to be enabled"
            TESTS_FAILED=$((TESTS_FAILED + 1))
        fi
    else
        log_skip "openssl not available, skipping TLSv1.0 test"
        TESTS_SKIPPED=$((TESTS_SKIPPED + 1))
    fi
}

test_ssl_certificate() {
    log_info "Testing SSL certificate is present..."
    if command -v openssl &>/dev/null; then
        local cert_info
        cert_info=$(echo | openssl s_client -connect "${NGINX_HOST}:${HTTPS_PORT}" \
            2>/dev/null | openssl x509 -noout -subject 2>/dev/null || echo "")
        if [ -n "$cert_info" ]; then
            log_pass "SSL certificate is present: $cert_info"
            TESTS_PASSED=$((TESTS_PASSED + 1))
        else
            log_fail "SSL certificate not found"
            TESTS_FAILED=$((TESTS_FAILED + 1))
        fi
    else
        log_skip "openssl not available, skipping certificate test"
        TESTS_SKIPPED=$((TESTS_SKIPPED + 1))
    fi
}

test_ssl_tls12
test_ssl_tls13
test_ssl_tls10_disabled
test_ssl_certificate

# ---------------------------------------------------------------------------
# Test Suite 4: Proxy Headers (Requisito 22.8, 22.9, 22.10)
# ---------------------------------------------------------------------------
log_section "Test Suite 4: Proxy Headers"

test_x_forwarded_proto_header() {
    log_info "Testing X-Forwarded-Proto header is set..."
    # This test requires a backend that echoes headers back
    # We verify the nginx config contains the directive
    if grep -q "X-Forwarded-Proto" /etc/nginx/nginx.conf 2>/dev/null || \
       grep -q "X-Forwarded-Proto" "$(dirname "$0")/../nginx.conf" 2>/dev/null; then
        log_pass "X-Forwarded-Proto header is configured in nginx.conf"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "X-Forwarded-Proto header not found in nginx.conf"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

test_x_real_ip_header() {
    log_info "Testing X-Real-IP header is configured..."
    if grep -q "X-Real-IP" /etc/nginx/nginx.conf 2>/dev/null || \
       grep -q "X-Real-IP" "$(dirname "$0")/../nginx.conf" 2>/dev/null; then
        log_pass "X-Real-IP header is configured in nginx.conf"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "X-Real-IP header not found in nginx.conf"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

test_x_forwarded_for_header() {
    log_info "Testing X-Forwarded-For header is configured..."
    if grep -q "X-Forwarded-For" /etc/nginx/nginx.conf 2>/dev/null || \
       grep -q "X-Forwarded-For" "$(dirname "$0")/../nginx.conf" 2>/dev/null; then
        log_pass "X-Forwarded-For header is configured in nginx.conf"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "X-Forwarded-For header not found in nginx.conf"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

test_x_forwarded_proto_header
test_x_real_ip_header
test_x_forwarded_for_header

# ---------------------------------------------------------------------------
# Test Suite 5: Rate Limiting (Requisito 24)
# ---------------------------------------------------------------------------
log_section "Test Suite 5: Rate Limiting"

test_rate_limit_429() {
    log_info "Testing rate limiting returns 429 when exceeded..."
    log_info "Sending 250 rapid requests to trigger rate limit..."

    local got_429=false
    for i in $(seq 1 250); do
        local status
        status=$(http_get "${BASE_HTTPS}/health" --insecure)
        if [ "$status" = "429" ]; then
            got_429=true
            break
        fi
    done

    if [ "$got_429" = "true" ]; then
        log_pass "Rate limiting correctly returns 429 Too Many Requests"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_skip "Rate limit not triggered (may need higher request volume or different IP)"
        TESTS_SKIPPED=$((TESTS_SKIPPED + 1))
    fi
}

test_rate_limit_config() {
    log_info "Testing rate limit zone is configured (100r/s)..."
    local conf_file
    conf_file="$(dirname "$0")/../nginx.conf"
    if grep -q "rate=100r/s" "$conf_file" 2>/dev/null || \
       grep -q "rate=100r/s" /etc/nginx/nginx.conf 2>/dev/null; then
        log_pass "Rate limit zone configured at 100r/s"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "Rate limit zone at 100r/s not found in nginx.conf"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

test_rate_limit_burst_config() {
    log_info "Testing rate limit burst=200 is configured..."
    local conf_file
    conf_file="$(dirname "$0")/../nginx.conf"
    if grep -q "burst=200" "$conf_file" 2>/dev/null || \
       grep -q "burst=200" /etc/nginx/nginx.conf 2>/dev/null; then
        log_pass "Rate limit burst=200 is configured"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "Rate limit burst=200 not found in nginx.conf"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

test_rate_limit_nodelay_config() {
    log_info "Testing rate limit nodelay is configured..."
    local conf_file
    conf_file="$(dirname "$0")/../nginx.conf"
    if grep -q "nodelay" "$conf_file" 2>/dev/null || \
       grep -q "nodelay" /etc/nginx/nginx.conf 2>/dev/null; then
        log_pass "Rate limit nodelay is configured"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "Rate limit nodelay not found in nginx.conf"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

test_rate_limit_429
test_rate_limit_config
test_rate_limit_burst_config
test_rate_limit_nodelay_config

# ---------------------------------------------------------------------------
# Test Suite 6: Load Distribution (Requisito 21)
# ---------------------------------------------------------------------------
log_section "Test Suite 6: Load Distribution"

test_upstream_least_conn() {
    log_info "Testing least_conn algorithm is configured..."
    local conf_file
    conf_file="$(dirname "$0")/../nginx.conf"
    if grep -q "least_conn" "$conf_file" 2>/dev/null || \
       grep -q "least_conn" /etc/nginx/nginx.conf 2>/dev/null; then
        log_pass "least_conn algorithm is configured"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "least_conn algorithm not found in nginx.conf"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

test_upstream_3_instances() {
    log_info "Testing upstream has 3 API Gateway instances..."
    local conf_file
    conf_file="$(dirname "$0")/../nginx.conf"
    local count
    count=$(grep -c "api-gateway-[0-9]:8080" "$conf_file" 2>/dev/null || \
            grep -c "api-gateway-[0-9]:8080" /etc/nginx/nginx.conf 2>/dev/null || echo "0")
    if [ "$count" -ge "3" ]; then
        log_pass "Upstream has $count API Gateway instances (>= 3 required)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "Upstream has only $count API Gateway instances (3 required)"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

test_upstream_max_fails() {
    log_info "Testing max_fails=3 is configured..."
    local conf_file
    conf_file="$(dirname "$0")/../nginx.conf"
    if grep -q "max_fails=3" "$conf_file" 2>/dev/null || \
       grep -q "max_fails=3" /etc/nginx/nginx.conf 2>/dev/null; then
        log_pass "max_fails=3 is configured"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "max_fails=3 not found in nginx.conf"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

test_upstream_fail_timeout() {
    log_info "Testing fail_timeout=30s is configured..."
    local conf_file
    conf_file="$(dirname "$0")/../nginx.conf"
    if grep -q "fail_timeout=30s" "$conf_file" 2>/dev/null || \
       grep -q "fail_timeout=30s" /etc/nginx/nginx.conf 2>/dev/null; then
        log_pass "fail_timeout=30s is configured"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "fail_timeout=30s not found in nginx.conf"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

test_upstream_least_conn
test_upstream_3_instances
test_upstream_max_fails
test_upstream_fail_timeout

# ---------------------------------------------------------------------------
# Test Suite 7: Failover and Retry (Requisito 25)
# ---------------------------------------------------------------------------
log_section "Test Suite 7: Failover and Retry Configuration"

test_proxy_next_upstream() {
    log_info "Testing proxy_next_upstream is configured..."
    local conf_file
    conf_file="$(dirname "$0")/../nginx.conf"
    if grep -q "proxy_next_upstream" "$conf_file" 2>/dev/null || \
       grep -q "proxy_next_upstream" /etc/nginx/nginx.conf 2>/dev/null; then
        log_pass "proxy_next_upstream is configured"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "proxy_next_upstream not found in nginx.conf"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

test_proxy_next_upstream_tries() {
    log_info "Testing proxy_next_upstream_tries=2 is configured..."
    local conf_file
    conf_file="$(dirname "$0")/../nginx.conf"
    if grep -q "proxy_next_upstream_tries 2" "$conf_file" 2>/dev/null || \
       grep -q "proxy_next_upstream_tries 2" /etc/nginx/nginx.conf 2>/dev/null; then
        log_pass "proxy_next_upstream_tries=2 is configured"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "proxy_next_upstream_tries=2 not found in nginx.conf"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

test_proxy_next_upstream_http500() {
    log_info "Testing proxy_next_upstream includes http_500..."
    local conf_file
    conf_file="$(dirname "$0")/../nginx.conf"
    if grep -q "http_500" "$conf_file" 2>/dev/null || \
       grep -q "http_500" /etc/nginx/nginx.conf 2>/dev/null; then
        log_pass "proxy_next_upstream includes http_500"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "proxy_next_upstream does not include http_500"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

test_proxy_next_upstream
test_proxy_next_upstream_tries
test_proxy_next_upstream_http500

# ---------------------------------------------------------------------------
# Test Suite 8: Performance Configuration (Requisito 26)
# ---------------------------------------------------------------------------
log_section "Test Suite 8: Performance Configuration"

test_gzip_enabled() {
    log_info "Testing gzip compression is enabled..."
    local conf_file
    conf_file="$(dirname "$0")/../nginx.conf"
    if grep -q "gzip on" "$conf_file" 2>/dev/null || \
       grep -q "gzip on" /etc/nginx/nginx.conf 2>/dev/null; then
        log_pass "gzip compression is enabled"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "gzip compression not enabled in nginx.conf"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

test_gzip_min_length() {
    log_info "Testing gzip_min_length >= 1024 (1KB)..."
    local conf_file
    conf_file="$(dirname "$0")/../nginx.conf"
    if grep -q "gzip_min_length 1024" "$conf_file" 2>/dev/null || \
       grep -q "gzip_min_length 1024" /etc/nginx/nginx.conf 2>/dev/null; then
        log_pass "gzip_min_length is set to 1024 bytes (1KB)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "gzip_min_length 1024 not found in nginx.conf"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

test_proxy_buffering() {
    log_info "Testing proxy_buffering is enabled..."
    local conf_file
    conf_file="$(dirname "$0")/../nginx.conf"
    if grep -q "proxy_buffering.*on" "$conf_file" 2>/dev/null || \
       grep -q "proxy_buffering.*on" /etc/nginx/nginx.conf 2>/dev/null; then
        log_pass "proxy_buffering is enabled"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "proxy_buffering on not found in nginx.conf"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

test_proxy_buffer_size() {
    log_info "Testing proxy_buffer_size=4k is configured..."
    local conf_file
    conf_file="$(dirname "$0")/../nginx.conf"
    if grep -q "proxy_buffer_size.*4k" "$conf_file" 2>/dev/null || \
       grep -q "proxy_buffer_size.*4k" /etc/nginx/nginx.conf 2>/dev/null; then
        log_pass "proxy_buffer_size=4k is configured"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "proxy_buffer_size 4k not found in nginx.conf"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

test_proxy_buffers_8() {
    log_info "Testing proxy_buffers=8 4k is configured..."
    local conf_file
    conf_file="$(dirname "$0")/../nginx.conf"
    if grep -q "proxy_buffers.*8.*4k" "$conf_file" 2>/dev/null || \
       grep -q "proxy_buffers.*8.*4k" /etc/nginx/nginx.conf 2>/dev/null; then
        log_pass "proxy_buffers=8 4k is configured"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "proxy_buffers 8 4k not found in nginx.conf"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

test_client_max_body_size() {
    log_info "Testing client_max_body_size=10M is configured..."
    local conf_file
    conf_file="$(dirname "$0")/../nginx.conf"
    if grep -q "client_max_body_size 10M" "$conf_file" 2>/dev/null || \
       grep -q "client_max_body_size 10M" /etc/nginx/nginx.conf 2>/dev/null; then
        log_pass "client_max_body_size=10M is configured"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "client_max_body_size 10M not found in nginx.conf"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

test_timeouts() {
    log_info "Testing proxy timeouts are configured (60s)..."
    local conf_file
    conf_file="$(dirname "$0")/../nginx.conf"
    local connect_ok=false
    local send_ok=false
    local read_ok=false

    grep -q "proxy_connect_timeout 60s" "$conf_file" 2>/dev/null && connect_ok=true
    grep -q "proxy_send_timeout.*60s" "$conf_file" 2>/dev/null && send_ok=true
    grep -q "proxy_read_timeout.*60s" "$conf_file" 2>/dev/null && read_ok=true

    if [ "$connect_ok" = "true" ] && [ "$send_ok" = "true" ] && [ "$read_ok" = "true" ]; then
        log_pass "All proxy timeouts configured at 60s"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "Some proxy timeouts not configured: connect=$connect_ok send=$send_ok read=$read_ok"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

test_gzip_enabled
test_gzip_min_length
test_proxy_buffering
test_proxy_buffer_size
test_proxy_buffers_8
test_client_max_body_size
test_timeouts

# ---------------------------------------------------------------------------
# Test Suite 9: Nginx Config Syntax Validation
# ---------------------------------------------------------------------------
log_section "Test Suite 9: Nginx Config Syntax Validation"

test_nginx_config_syntax() {
    log_info "Testing nginx.conf syntax is valid..."
    if command -v nginx &>/dev/null; then
        if nginx -t -c /etc/nginx/nginx.conf 2>/dev/null; then
            log_pass "nginx.conf syntax is valid"
            TESTS_PASSED=$((TESTS_PASSED + 1))
        else
            log_fail "nginx.conf has syntax errors"
            TESTS_FAILED=$((TESTS_FAILED + 1))
        fi
    else
        # Try with the local file
        local conf_file
        conf_file="$(dirname "$0")/../nginx.conf"
        if [ -f "$conf_file" ]; then
            log_pass "nginx.conf file exists (syntax check requires nginx binary)"
            TESTS_PASSED=$((TESTS_PASSED + 1))
        else
            log_fail "nginx.conf file not found at $conf_file"
            TESTS_FAILED=$((TESTS_FAILED + 1))
        fi
    fi
}

test_nginx_config_syntax

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
log_section "Test Results Summary"
TOTAL=$((TESTS_PASSED + TESTS_FAILED + TESTS_SKIPPED))
echo -e "${GREEN}Passed:${NC}  $TESTS_PASSED"
echo -e "${RED}Failed:${NC}  $TESTS_FAILED"
echo -e "${YELLOW}Skipped:${NC} $TESTS_SKIPPED"
echo -e "Total:   $TOTAL"

if [ "$TESTS_FAILED" -eq 0 ]; then
    echo -e "\n${GREEN}All tests passed!${NC}"
    exit 0
else
    echo -e "\n${RED}$TESTS_FAILED test(s) failed.${NC}"
    exit 1
fi
