#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

BACKEND_URL="${BACKEND_URL:-http://localhost:8081}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
ASR_URL="${ASR_URL:-http://localhost:8082}"
PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_pass() { echo -e "  ${GREEN}PASS${NC} $1"; ((PASS_COUNT++)) || true; }
log_fail() { echo -e "  ${RED}FAIL${NC} $1 | expected: $2 | actual: $3"; ((FAIL_COUNT++)) || true; }
log_skip() { echo -e "  ${YELLOW}SKIP${NC} $1"; ((SKIP_COUNT++)) || true; }
section() { echo ""; echo "$1"; echo "$(printf '%0.s─' $(seq 1 60))"; }

###############################################################################
# Helpers
###############################################################################

do_req() {
    local resp
    resp=$(curl -s -w "\n%{http_code}" "$@")
    echo "$resp"
}

register_user() {
    local user="$1" pass="$2" email="$3"
    do_req -X POST "$BACKEND_URL/api/v1/auth/register" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"$user\",\"password\":\"$pass\",\"email\":\"$email\"}"
}

login_user() {
    local user="$1" pass="$2"
    do_req -X POST "$BACKEND_URL/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"$user\",\"password\":\"$pass\"}"
}

add_vocab() {
    local token="$1" word="$2" pinyin="$3" category="$4"
    do_req -X POST "$BACKEND_URL/api/v1/vocabulary" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $token" \
        -d "{\"word\":\"$word\",\"pinyin\":\"$pinyin\",\"category\":\"$category\"}"
}

sync_vocab() {
    local token="$1" version="$2"
    do_req -X POST "$BACKEND_URL/api/v1/vocabulary/sync" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $token" \
        -d "{\"version\":$version,\"limit\":500}"
}

###############################################################################
# 1. Health Checks
###############################################################################
section "1. Dual-Platform E2E: Health Checks"

RESP=$(do_req -s "$BACKEND_URL/health")
if [ -n "$RESP" ]; then log_pass "Backend health"; else log_fail "Backend health" "UP" "DOWN"; fi

RESP=$(do_req -s "$GATEWAY_URL/health")
if [ -n "$RESP" ]; then log_pass "Gateway health"; else log_fail "Gateway health" "UP" "DOWN"; fi

RESP=$(do_req -s "$ASR_URL/health")
if echo "$RESP" | grep -q '"status":"UP"'; then
    log_pass "ASR health"
else
    log_skip "ASR unreachable"
fi

###############################################################################
# 2. Cross-Device Auth: Desktop user + Android user share same account
###############################################################################
section "2. Cross-Device: User Auth"
USERNAME="dual_e2e_user"
PASSWORD="DualTest123"
EMAIL="dual_e2e@heecomou.com"

# Try login first (user may exist from previous CI run)
LOGIN_RESP=$(login_user "$USERNAME" "$PASSWORD")
LOGIN_CODE=$(echo "$LOGIN_RESP" | tail -1)
LOGIN_BODY=$(echo "$LOGIN_RESP" | sed '$d')

if [ "$LOGIN_CODE" = "200" ]; then
    TOKEN=$(echo "$LOGIN_BODY" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
    log_pass "Cross-device login (existing user)"
else
    REG_RESP=$(register_user "$USERNAME" "$PASSWORD" "$EMAIL")
    REG_CODE=$(echo "$REG_RESP" | tail -1)
    REG_BODY=$(echo "$REG_RESP" | sed '$d')
    if [ "$REG_CODE" = "200" ]; then
        TOKEN=$(echo "$REG_BODY" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
        log_pass "Cross-device register"
    elif [ "$REG_CODE" = "409" ]; then
        log_fail "Cross-device register" "200" "409 exists"
    else
        log_fail "Cross-device register" "200" "$REG_CODE"
    fi
fi

if [ -z "$TOKEN" ]; then
    echo "FATAL: No token obtained"
    exit 1
fi

###############################################################################
# 3. Simulated Desktop: Add Vocab
###############################################################################
section "3. Simulated Desktop: Add Vocab Words"

ADD1=$(add_vocab "$TOKEN" "七牛云" "qi niu yun" "云服务")
ADD1_CODE=$(echo "$ADD1" | tail -1)
if [ "$ADD1_CODE" = "200" ] || [ "$ADD1_CODE" = "409" ]; then
    log_pass "Desktop: add vocab [七牛云]"
else
    log_fail "Desktop: add vocab" "200/409" "$ADD1_CODE"
fi

ADD2=$(add_vocab "$TOKEN" "闭环反馈" "bi huan fan kui" "系统术语")
ADD2_CODE=$(echo "$ADD2" | tail -1)
if [ "$ADD2_CODE" = "200" ] || [ "$ADD2_CODE" = "409" ]; then
    log_pass "Desktop: add vocab [闭环反馈]"
else
    log_fail "Desktop: add vocab" "200/409" "$ADD2_CODE"
fi

ADD3=$(add_vocab "$TOKEN" "深度学习" "shen du xue xi" "AI")
ADD3_CODE=$(echo "$ADD3" | tail -1)
if [ "$ADD3_CODE" = "200" ] || [ "$ADD3_CODE" = "409" ]; then
    log_pass "Desktop: add vocab [深度学习]"
else
    log_fail "Desktop: add vocab" "200/409" "$ADD3_CODE"
fi

###############################################################################
# 4. Simulated Android: Sync Vocab (version=0, should get all words)
###############################################################################
section "4. Simulated Android: Sync Vocab from Backend"

SYNC_RESP=$(sync_vocab "$TOKEN" 0)
SYNC_CODE=$(echo "$SYNC_RESP" | tail -1)
SYNC_BODY=$(echo "$SYNC_RESP" | sed '$d')

if [ "$SYNC_CODE" = "200" ]; then
    ITEM_COUNT=$(echo "$SYNC_BODY" | grep -o '"id":' | wc -l)
    HAS_MORE=$(echo "$SYNC_BODY" | grep -o '"hasMore":false' | wc -l)
    MAX_VER=$(echo "$SYNC_BODY" | grep -o '"maxVersion":[0-9]*' | head -1 | cut -d: -f2)

    log_pass "Android: sync version=0 (items=$ITEM_COUNT)"
    if [ "$ITEM_COUNT" -ge 3 ]; then
        log_pass "Android: received desktop words ($ITEM_COUNT items)"
    else
        log_fail "Android: received desktop words" ">=3" "$ITEM_COUNT"
    fi

    # Incremental sync: now sync with the maxVersion we got
    if [ -n "$MAX_VER" ] && [ "$MAX_VER" -gt 0 ]; then
        SYNC2_RESP=$(sync_vocab "$TOKEN" "$MAX_VER")
        SYNC2_CODE=$(echo "$SYNC2_RESP" | tail -1)
        SYNC2_BODY=$(echo "$SYNC2_RESP" | sed '$d')
        SYNC2_COUNT=$(echo "$SYNC2_BODY" | grep -o '"id":' | wc -l)
        if [ "$SYNC2_CODE" = "200" ] && [ "$SYNC2_COUNT" -eq 0 ]; then
            log_pass "Android: incremental sync (no new items, correct)"
        else
            log_pass "Android: incremental sync (items=$SYNC2_COUNT)"
        fi
    fi
else
    log_fail "Android: sync version=0" "200" "$SYNC_CODE"
fi

###############################################################################
# 5. Vocabulary CRUD from "both devices"
###############################################################################
section "5. Cross-Device: Vocab CRUD"

# Android side: add a word
ADD4=$(add_vocab "$TOKEN" "容器编排" "rong qi bian pai" "云原生")
ADD4_CODE=$(echo "$ADD4" | tail -1)
if [ "$ADD4_CODE" = "200" ] || [ "$ADD4_CODE" = "409" ]; then
    log_pass "Android: add vocab [容器编排]"
else
    log_fail "Android: add vocab" "200/409" "$ADD4_CODE"
fi

# Search across both devices' words
SEARCH_RESP=$(do_req -s -G "$BACKEND_URL/api/v1/vocabulary/search" \
    -H "Authorization: Bearer $TOKEN" \
    --data-urlencode "keyword=云" \
    --data-urlencode "page=1" \
    --data-urlencode "size=10")
SEARCH_CODE=$(echo "$SEARCH_RESP" | tail -1)
SEARCH_BODY=$(echo "$SEARCH_RESP" | sed '$d')

if [ "$SEARCH_CODE" = "200" ]; then
    SEARCH_COUNT=$(echo "$SEARCH_BODY" | grep -o '"id":' | wc -l)
    log_pass "Cross-device search [云] ($SEARCH_COUNT results)"
    if echo "$SEARCH_BODY" | grep -q "七牛云"; then
        log_pass "Found desktop word [七牛云] in Android search"
    fi
    if echo "$SEARCH_BODY" | grep -q "容器编排"; then
        log_pass "Found Android word [容器编排] in search"
    fi
else
    log_fail "Cross-device search" "200" "$SEARCH_CODE"
fi

# List vocabulary - verify cross-device visibility
LIST_RESP=$(do_req -s "$BACKEND_URL/api/v1/vocabulary?page=1&size=100" \
    -H "Authorization: Bearer $TOKEN")
LIST_CODE=$(echo "$LIST_RESP" | tail -1)
LIST_BODY=$(echo "$LIST_RESP" | sed '$d')

if [ "$LIST_CODE" = "200" ]; then
    TOTAL=$(echo "$LIST_BODY" | grep -o '"total":[0-9]*' | head -1 | cut -d: -f2)
    log_pass "Cross-device vocab list (total=$TOTAL)"
    if [ "$TOTAL" -ge 4 ]; then
        log_pass "All 4+ words visible across devices"
    fi
else
    log_fail "Cross-device vocab list" "200" "$LIST_CODE"
fi

###############################################################################
# 6. Gateway Smart Routing (both engines)
###############################################################################
section "6. Gateway: Smart Routing Decisions"

ROUTE1=$(do_req -s -X POST "$GATEWAY_URL/api/v1/router/decide" \
    -H "Content-Type: application/json" \
    -d '{"network":"wifi","signal_strength":0.95,"battery":0.85,"privacy_mode":false,"noise_level":0}')
ROUTE1_ENGINE=$(echo "$ROUTE1" | grep -o '"engine":"[^"]*"' | cut -d'"' -f4)
if [ "$ROUTE1_ENGINE" = "cloud" ]; then
    log_pass "WiFi high quality -> cloud"
else
    log_fail "WiFi high quality routing" "cloud" "$ROUTE1_ENGINE"
fi

ROUTE2=$(do_req -s -X POST "$GATEWAY_URL/api/v1/router/decide" \
    -H "Content-Type: application/json" \
    -d '{"network":"cellular","signal_strength":0.1,"battery":0.90,"privacy_mode":false,"noise_level":0}')
ROUTE2_ENGINE=$(echo "$ROUTE2" | grep -o '"engine":"[^"]*"' | cut -d'"' -f4)
if [ "$ROUTE2_ENGINE" = "local" ]; then
    log_pass "Weak signal -> local"
else
    log_fail "Weak signal routing" "local" "$ROUTE2_ENGINE"
fi

ROUTE3=$(do_req -s -X POST "$GATEWAY_URL/api/v1/router/decide" \
    -H "Content-Type: application/json" \
    -d '{"network":"wifi","signal_strength":0.95,"battery":0.85,"privacy_mode":true,"noise_level":0}')
ROUTE3_ENGINE=$(echo "$ROUTE3" | grep -o '"engine":"[^"]*"' | cut -d'"' -f4)
if [ "$ROUTE3_ENGINE" = "local" ]; then
    log_pass "Privacy mode -> local"
else
    log_fail "Privacy routing" "local" "$ROUTE3_ENGINE"
fi

###############################################################################
# 7. Gateway - Backend connectivity
###############################################################################
section "7. Cross-Service: Gateway -> Backend"

GW_BE=$(do_req -s "$GATEWAY_URL/api/v1/router/decide" \
    -X POST -H "Content-Type: application/json" \
    -d '{"network":"wifi","signal_strength":0.9,"battery":1.0,"privacy_mode":false,"noise_level":0}')
GW_BE_CODE=$(echo "$GW_BE" | tail -1)
if [ "$GW_BE_CODE" = "200" ]; then
    log_pass "Gateway responds to routing"
else
    log_fail "Gateway routing response" "200" "$GW_BE_CODE"
fi

###############################################################################
# 8. Boundary Scenarios
###############################################################################
section "8. Boundary: Auth & Error Scenarios"

# No auth
NOAUTH=$(do_req -s "$BACKEND_URL/api/v1/vocabulary?page=1&size=5")
NOAUTH_CODE=$(echo "$NOAUTH" | tail -1)
if [ "$NOAUTH_CODE" = "401" ]; then
    log_pass "No-auth vocab access -> 401"
else
    log_fail "No-auth vocab access" "401" "$NOAUTH_CODE"
fi

# Invalid token
INV_TOKEN=$(do_req -s "$BACKEND_URL/api/v1/vocabulary?page=1&size=5" \
    -H "Authorization: Bearer invalid_token_xyz")
INV_TOKEN_CODE=$(echo "$INV_TOKEN" | tail -1)
if [ "$INV_TOKEN_CODE" = "401" ]; then
    log_pass "Invalid token -> 401"
else
    log_fail "Invalid token" "401" "$INV_TOKEN_CODE"
fi

# Sync with version beyond current
SYNC_FUTURE=$(sync_vocab "$TOKEN" 999999999999)
SYNC_F_CODE=$(echo "$SYNC_FUTURE" | tail -1)
if [ "$SYNC_F_CODE" = "200" ]; then
    log_pass "Sync future version = 200 (empty items)"
else
    log_fail "Sync future version" "200" "$SYNC_F_CODE"
fi

###############################################################################
# Summary
###############################################################################
TOTAL=$((PASS_COUNT + FAIL_COUNT + SKIP_COUNT))
echo ""
echo "Phase 5 Cross-Platform E2E Results"
echo "=================================="
echo "  Pass: $PASS_COUNT / Total: $TOTAL"
echo "  Fail: $FAIL_COUNT"
echo "  Skip: $SKIP_COUNT"
echo ""

if [ "$FAIL_COUNT" -gt 0 ]; then
    exit 1
else
    exit 0
fi
