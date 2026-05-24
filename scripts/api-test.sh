#!/bin/bash
# ============================================================
# Phase 1 手工 API 验收测试脚本
# 前置条件: docker-compose up -d（MySQL + Redis + Backend）
# 使用方法: bash scripts/api-test.sh
# ============================================================
set -e

BASE_URL="${BASE_URL:-http://localhost:8081}"
PASSED=0
FAILED=0

echo "========================================"
echo " HeecoMou Phase 1 API 验收测试"
echo " BASE_URL = $BASE_URL"
echo "========================================"
echo ""

# ---------- helper ----------
check() {
    local desc="$1"
    local expected_code="$2"
    local response
    response=$(curl -s -w "\n%{http_code}" "$@")
    local http_code
    http_code=$(echo "$response" | tail -1)
    local body
    body=$(echo "$response" | sed '$d')

    local api_code
    api_code=$(echo "$body" | grep -o '"code":[0-9]*' | head -1 | cut -d: -f2)

    if [ "$expected_code" = "$api_code" ]; then
        echo "  PASS: $desc (http=$http_code, code=$api_code)"
        PASSED=$((PASSED + 1))
    else
        echo "  FAIL: $desc (expected code=$expected_code, got http=$http_code code=$api_code)"
        echo "        body: $body"
        FAILED=$((FAILED + 1))
    fi
    echo "$body"
}

# ============================================================
# 1. 健康检查
# ============================================================
echo "--- 1. 健康检查 ---"
check "Health Check" 200 \
    -X GET "$BASE_URL/api/v1/health"
echo ""

# ============================================================
# 2. 注册新用户
# ============================================================
echo "--- 2. 注册新用户 ---"
check "注册新用户" 200 \
    -X POST "$BASE_URL/api/v1/auth/register" \
    -H "Content-Type: application/json" \
    -d '{"username":"e2e_test_user","password":"testPass123","email":"e2e@heecomou.com"}'
echo ""

# ============================================================
# 3. 重复注册（应返回 409）
# ============================================================
echo "--- 3. 重复注册（预期 409）---"
check "重复注册" 409 \
    -X POST "$BASE_URL/api/v1/auth/register" \
    -H "Content-Type: application/json" \
    -d '{"username":"e2e_test_user","password":"testPass123","email":"e2e@heecomou.com"}'
echo ""

# ============================================================
# 4. 参数校验失败（预期 400）
# ============================================================
echo "--- 4. 参数校验失败（预期 400）---"
check "参数校验" 400 \
    -X POST "$BASE_URL/api/v1/auth/register" \
    -H "Content-Type: application/json" \
    -d '{"username":"ab","password":"12","email":"bad"}'
echo ""

# ============================================================
# 5. 登录获取 Token
# ============================================================
echo "--- 5. 登录获取 Token ---"
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"e2e_test_user","password":"testPass123"}')
echo "  Response: $LOGIN_RESPONSE"

ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
REFRESH_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"refreshToken":"[^"]*"' | cut -d'"' -f4)

if [ -n "$ACCESS_TOKEN" ]; then
    echo "  PASS: 获取 accessToken 成功"
    PASSED=$((PASSED + 1))
else
    echo "  FAIL: 未获取到 accessToken"
    FAILED=$((FAILED + 1))
fi
echo ""

# ============================================================
# 6. 错误密码登录（预期 401）
# ============================================================
echo "--- 6. 错误密码登录（预期 401）---"
check "错误密码" 401 \
    -X POST "$BASE_URL/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"e2e_test_user","password":"wrongPassword"}'
echo ""

# ============================================================
# 7. 获取个人信息
# ============================================================
echo "--- 7. 获取个人信息 ---"
check "个人信息" 200 \
    -X GET "$BASE_URL/api/v1/user/me" \
    -H "Authorization: Bearer $ACCESS_TOKEN"
echo ""

# ============================================================
# 8. 修改昵称
# ============================================================
echo "--- 8. 修改昵称 ---"
check "修改昵称" 200 \
    -X PUT "$BASE_URL/api/v1/user/profile" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -d '{"nickname":"端到端测试用户"}'
echo ""

# ============================================================
# 9. 修改密码
# ============================================================
echo "--- 9. 修改密码 ---"
check "修改密码" 200 \
    -X PUT "$BASE_URL/api/v1/user/password" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -d '{"oldPassword":"testPass123","newPassword":"newPass456"}'
echo ""

# 用新密码重新登录
LOGIN_RESPONSE2=$(curl -s -X POST "$BASE_URL/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"e2e_test_user","password":"newPass456"}')
ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE2" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
echo "  用新密码重登录: accessToken=${ACCESS_TOKEN:0:20}..."
echo ""

# ============================================================
# 10. 刷新 Token
# ============================================================
echo "--- 10. 刷新 Token ---"
REFRESH_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/auth/refresh" \
    -H "Content-Type: application/json" \
    -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}")
echo "  Response: $REFRESH_RESPONSE"

NEW_ACCESS=$(echo "$REFRESH_RESPONSE" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
if [ -n "$NEW_ACCESS" ]; then
    echo "  PASS: 刷新成功获取新 accessToken"
    PASSED=$((PASSED + 1))
    ACCESS_TOKEN=$NEW_ACCESS
else
    echo "  FAIL: 刷新失败（可能是旧 Token 已被撤销或过期）"
    FAILED=$((FAILED + 1))
fi
echo ""

# ============================================================
# 11. 无 Token 访问（预期 401）
# ============================================================
echo "--- 11. 无 Token 访问 /me（预期 401）---"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X GET "$BASE_URL/api/v1/user/me")
if [ "$HTTP_CODE" = "401" ]; then
    echo "  PASS: 无 Token 返回 401"
    PASSED=$((PASSED + 1))
else
    echo "  FAIL: 预期 401，实际 $HTTP_CODE"
    FAILED=$((FAILED + 1))
fi
echo ""

# ============================================================
# 12. 登出
# ============================================================
echo "--- 12. 登出 ---"
check "登出" 200 \
    -X POST "$BASE_URL/api/v1/auth/logout" \
    -H "Authorization: Bearer $ACCESS_TOKEN"
echo ""

# ============================================================
# 结果汇总
# ============================================================
echo "========================================"
echo "  测试完成: $PASSED 通过 / $((PASSED + FAILED)) 总计"
if [ "$FAILED" -eq 0 ]; then
    echo "  状态: ALL PASSED"
else
    echo "  状态: $FAILED FAILED"
fi
echo "========================================"

exit $FAILED
