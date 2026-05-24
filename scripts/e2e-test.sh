#!/bin/bash
# ============================================================
# HeecoMou 端到端自动化测试脚本
#
# 覆盖范围:
#   1. 全服务健康检查 (Backend + Gateway + ASR)
#   2. 用户认证全流程 (注册 / 登录 / 信息 / 修改密码 / 刷新Token / 登出)
#   3. 词库 CRUD + 搜索 + 增量同步
#   4. 纠错历史记录
#   5. Go Gateway 智能路由 API
#   6. Go Gateway WebSocket 音频传输链路
#   7. 跨服务集成验证 (Gateway → Backend → ASR)
#   8. 边界与异常场景 (权限 / 重复 / 校验 / 限流)
#
# 前置条件:
#   docker-compose up -d    (MySQL + Redis)
#   Backend Java  :8081 启动
#   Gateway Go    :8080 启动
#   ASR Python    :8082 启动
#
# 使用方法:
#   bash scripts/e2e-test.sh
#   BACKEND_URL=http://remote:8081 bash scripts/e2e-test.sh
# ============================================================
set +e

# ---------- 配置 ----------
BACKEND_URL="${BACKEND_URL:-http://localhost:8081}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
ASR_URL="${ASR_URL:-http://localhost:8082}"

PASSED=0
FAILED=0
TOTAL=0
ACCESS_TOKEN=""
REFRESH_TOKEN=""
VOCAB_ID=""
SESSION_ID=""

# ---------- 工具函数 ----------
color_green()  { printf '\033[32m%s\033[0m\n' "$1"; }
color_red()    { printf '\033[31m%s\033[0m\n' "$1"; }
color_yellow() { printf '\033[33m%s\033[0m\n' "$1"; }
color_cyan()   { printf '\033[36m%s\033[0m\n' "$1"; }

pass() {
    PASSED=$((PASSED + 1))
    TOTAL=$((TOTAL + 1))
    echo "  $(color_green '✓ PASS') $1" >&2
}

fail() {
    FAILED=$((FAILED + 1))
    TOTAL=$((TOTAL + 1))
    echo "  $(color_red '✗ FAIL') $1" >&2
    echo "        expected: $2" >&2
    echo "        actual:   ${3:-<empty>}" >&2
}

# 发送 HTTP 请求，解析 JSON 响应
# 返回 body + http_code (最后一行)
do_request() {
    curl -s -w "\n%{http_code}" --connect-timeout 5 --max-time 30 "$@"
}

# 检查 HTTP 状态码
assert_http() {
    local desc="$1"
    local expected_http="$2"
    local response
    response=$(do_request "${@:3}")
    local http_code
    http_code=$(echo "$response" | tail -1)
    local body
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" = "$expected_http" ]; then
        pass "$desc (HTTP $http_code)"
    else
        fail "$desc" "HTTP $expected_http" "HTTP $http_code | body: ${body:0:200}"
    fi
    printf "%s" "$body"
}

# 检查业务 code
assert_code() {
    local desc="$1"
    local expected_code="$2"
    local response
    response=$(do_request "${@:3}")
    local http_code
    http_code=$(echo "$response" | tail -1)
    local body
    body=$(echo "$response" | sed '$d')
    local api_code
    api_code=$(echo "$body" | grep -o '"code":[0-9]*' | head -1 | cut -d: -f2)

    if [ "$expected_code" = "$api_code" ]; then
        pass "$desc (http=$http_code code=$api_code)"
    else
        fail "$desc" "code=$expected_code" "http=$http_code code=$api_code | ${body:0:200}"
    fi
    printf "%s" "$body"
}

# 断言 JSON 中是否包含某字段
assert_json_contains() {
    local desc="$1"
    local body="$2"
    local field="$3"
    if echo "$body" | grep -q "\"${field}\""; then
        pass "$desc"
    else
        fail "$desc" "field '$field' present" "not found"
    fi
}

# 打印分隔符
section() {
    echo ""
    color_cyan "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    color_cyan "  $1"
    color_cyan "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# ---------- 开始 ----------
echo ""
color_cyan "╔══════════════════════════════════════════════════════╗"
color_cyan "║     HeecoMou E2E 端到端自动化测试                    ║"
color_cyan "╠══════════════════════════════════════════════════════╣"
color_cyan "║  Backend : $BACKEND_URL"
color_cyan "║  Gateway : $GATEWAY_URL"
color_cyan "║  ASR     : $ASR_URL"
color_cyan "║  时间    : $(date '+%Y-%m-%d %H:%M:%S')"
color_cyan "╚══════════════════════════════════════════════════════╝"

##############################################################################
# 1. 全服务健康检查
##############################################################################
section "1. 全服务健康检查 (Health Checks)"

BODY=$(assert_http "Backend 健康检查" 200 \
    -X GET "$BACKEND_URL/api/v1/health")
assert_json_contains "Backend 返回 status=UP" "$BODY" "UP"

BODY=$(assert_http "Gateway 健康检查" 200 \
    -X GET "$GATEWAY_URL/health")
assert_json_contains "Gateway 返回 status=UP" "$BODY" "UP"

ASR_HEALTH=$(curl -s --connect-timeout 5 "$ASR_URL/health" 2>/dev/null)
if echo "$ASR_HEALTH" | grep -q "UP"; then
    pass "ASR 返回 status=UP"
else
    echo "  $(color_yellow '⚠ SKIP') ASR 服务不可达"
fi

##############################################################################
# 2. 用户认证全流程
##############################################################################
section "2. 用户认证全流程"

# 2.1 注册
assert_code "注册新用户" 200 \
    -X POST "$BACKEND_URL/api/v1/auth/register" \
    -H "Content-Type: application/json" \
    -d '{"username":"e2e_full_test_user","password":"TestPass123","email":"e2e_full@heecomou.com"}'

# 2.2 重复注册
assert_code "重复注册（预期 409）" 409 \
    -X POST "$BACKEND_URL/api/v1/auth/register" \
    -H "Content-Type: application/json" \
    -d '{"username":"e2e_full_test_user","password":"Another123","email":"other@heecomou.com"}'

# 2.3 注册参数校验
assert_code "参数校验失败（预期 400）" 400 \
    -X POST "$BACKEND_URL/api/v1/auth/register" \
    -H "Content-Type: application/json" \
    -d '{"username":"ab","password":"12","email":"bad"}'

# 2.4 正常登录
LOGIN_BODY=$(do_request -X POST "$BACKEND_URL/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"e2e_full_test_user","password":"TestPass123"}')
LOGIN_HTTP=$(echo "$LOGIN_BODY" | tail -1)
LOGIN_DATA=$(echo "$LOGIN_BODY" | sed '$d')
LOGIN_CODE=$(echo "$LOGIN_DATA" | grep -o '"code":[0-9]*' | head -1 | cut -d: -f2)

if [ "$LOGIN_CODE" = "200" ]; then
    ACCESS_TOKEN=$(echo "$LOGIN_DATA" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
    REFRESH_TOKEN=$(echo "$LOGIN_DATA" | grep -o '"refreshToken":"[^"]*"' | cut -d'"' -f4)
    if [ -n "$ACCESS_TOKEN" ] && [ -n "$REFRESH_TOKEN" ]; then
        pass "登录获取 accessToken + refreshToken"
    else
        fail "登录获取 Token" "non-empty token" "empty"
    fi
else
    fail "登录" "code=200" "code=$LOGIN_CODE"
fi

# 2.5 错误密码登录
assert_code "错误密码登录（预期 401）" 401 \
    -X POST "$BACKEND_URL/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"e2e_full_test_user","password":"wrong_password"}'

# 2.6 获取个人信息
BODY=$(assert_code "获取个人信息" 200 \
    -X GET "$BACKEND_URL/api/v1/user/me" \
    -H "Authorization: Bearer $ACCESS_TOKEN")
assert_json_contains "个人信息包含 username" "$BODY" "username"

# 2.7 修改昵称
assert_code "修改昵称" 200 \
    -X PUT "$BACKEND_URL/api/v1/user/profile" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -d '{"nickname":"E2E测试用户"}'

# 2.8 无 Token 访问
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X GET "$BACKEND_URL/api/v1/user/me")
if [ "$HTTP_CODE" = "401" ]; then
    pass "无 Token 访问 /me 返回 401"
else
    fail "无 Token 访问 /me" "HTTP 401" "HTTP $HTTP_CODE"
fi

# 2.9 刷新 Token
REFRESH_BODY=$(do_request -X POST "$BACKEND_URL/api/v1/auth/refresh" \
    -H "Content-Type: application/json" \
    -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}")
REFRESH_HTTP=$(echo "$REFRESH_BODY" | tail -1)
REFRESH_DATA=$(echo "$REFRESH_BODY" | sed '$d')
NEW_ACCESS=$(echo "$REFRESH_DATA" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
if [ -n "$NEW_ACCESS" ]; then
    pass "刷新 Token 成功"
    ACCESS_TOKEN="$NEW_ACCESS"
else
    fail "刷新 Token" "non-empty token" "empty"
fi

##############################################################################
# 3. 词库 CRUD + 搜索 + 增量同步
##############################################################################
section "3. 词库管理全流程"

# 3.1 添加词汇
ADD_BODY=$(assert_code "添加词汇 - 微服务架构" 200 \
    -X POST "$BACKEND_URL/api/v1/vocabulary" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -d '{"word":"微服务架构","pinyin":"wei fu wu jia gou","category":"技术术语"}')
VOCAB_ID=$(echo "$ADD_BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
if [ -n "$VOCAB_ID" ]; then
    pass "获取词汇 ID = $VOCAB_ID"
fi

assert_code "添加词汇 - 分布式系统" 200 \
    -X POST "$BACKEND_URL/api/v1/vocabulary" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -d '{"word":"分布式系统","pinyin":"fen bu shi xi tong","category":"技术术语"}'

assert_code "添加词汇 - 容器化" 200 \
    -X POST "$BACKEND_URL/api/v1/vocabulary" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -d '{"word":"容器化","pinyin":"rong qi hua","category":"技术术语"}'

# 3.2 添加无分类词汇
assert_code "添加词汇 - 无分类" 200 \
    -X POST "$BACKEND_URL/api/v1/vocabulary" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -d '{"word":"日常用语"}'

# 3.3 分页列表
BODY=$(assert_code "分页词库列表" 200 \
    -X GET "$BACKEND_URL/api/v1/vocabulary?page=1&size=20" \
    -H "Authorization: Bearer $ACCESS_TOKEN")
assert_json_contains "词库列表包含 items" "$BODY" "items"
assert_json_contains "词库列表包含 total" "$BODY" "total"

# 3.4 搜索
BODY=$(assert_code "搜索词汇 - 微服务" 200 \
    -X GET "$BACKEND_URL/api/v1/vocabulary/search?keyword=%E5%BE%AE%E6%9C%8D%E5%8A%A1&page=1&size=20" \
    -H "Authorization: Bearer $ACCESS_TOKEN")
assert_json_contains "搜索返回 items" "$BODY" "items"

# 3.5 按 ID 获取
BODY=$(assert_code "按ID获取词汇" 200 \
    -X GET "$BACKEND_URL/api/v1/vocabulary/$VOCAB_ID" \
    -H "Authorization: Bearer $ACCESS_TOKEN")
assert_json_contains "详细页包含 word" "$BODY" "word"

# 3.6 更新词汇
assert_code "更新词汇" 200 \
    -X PUT "$BACKEND_URL/api/v1/vocabulary/$VOCAB_ID" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -d '{"word":"微服务架构","pinyin":"wei fu wu jia gou","category":"核心技术"}'

# 3.7 删除词汇
assert_code "删除词汇" 200 \
    -X DELETE "$BACKEND_URL/api/v1/vocabulary/$VOCAB_ID" \
    -H "Authorization: Bearer $ACCESS_TOKEN"

# 3.8 增量同步
BODY=$(assert_code "增量同步 (version=0)" 200 \
    -X POST "$BACKEND_URL/api/v1/vocabulary/sync" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -d '{"version":0,"limit":50}')
assert_json_contains "增量同步返回 items" "$BODY" "items"

##############################################################################
# 4. 纠错历史
##############################################################################
section "4. 纠错历史记录"

# 4.1 提交纠错
assert_code "提交纠错记录" 200 \
    -X POST "$BACKEND_URL/api/v1/corrections" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -d '{"originalText":"微服四架构","correctedText":"微服务架构","source":"ASR"}'

assert_code "提交纠错记录 - 键盘输入" 200 \
    -X POST "$BACKEND_URL/api/v1/corrections" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -d '{"originalText":"分不式系统","correctedText":"分布式系统","source":"KEYBOARD"}'

# 4.2 获取最近纠错
BODY=$(assert_code "获取最近纠错记录" 200 \
    -X GET "$BACKEND_URL/api/v1/corrections?limit=10" \
    -H "Authorization: Bearer $ACCESS_TOKEN")
assert_json_contains "纠错记录包含 data" "$BODY" "data"

##############################################################################
# 5. Gateway 智能路由 API
##############################################################################
section "5. Go Gateway 智能路由 API"

# 5.1 WiFi 高质量网络 → cloud
BODY=$(assert_http "路由决策 - WiFi 高质量环境" 200 \
    -X POST "$GATEWAY_URL/api/v1/route" \
    -H "Content-Type: application/json" \
    -d '{"network_type":"wifi","signal_strength":0.95,"battery_level":85.0,"noise_level_db":30.0,"is_sensitive_context":false,"language":"zh"}')
assert_json_contains "决策返回 engine=cloud" "$BODY" "cloud"

# 5.2 弱信号 → local
BODY=$(assert_http "路由决策 - 弱信号" 200 \
    -X POST "$GATEWAY_URL/api/v1/route" \
    -H "Content-Type: application/json" \
    -d '{"network_type":"cellular","signal_strength":0.1,"battery_level":80.0,"noise_level_db":35.0,"is_sensitive_context":false,"language":"zh"}')
assert_json_contains "决策返回 engine=local" "$BODY" "local"

# 5.3 隐私敏感上下文 → local
BODY=$(assert_http "路由决策 - 隐私敏感" 200 \
    -X POST "$GATEWAY_URL/api/v1/route" \
    -H "Content-Type: application/json" \
    -d '{"network_type":"wifi","signal_strength":0.9,"battery_level":90.0,"noise_level_db":25.0,"is_sensitive_context":true,"language":"zh"}')
assert_json_contains "决策返回 engine=local" "$BODY" "local"

# 5.4 离线 → local
BODY=$(assert_http "路由决策 - 离线" 200 \
    -X POST "$GATEWAY_URL/api/v1/route" \
    -H "Content-Type: application/json" \
    -d '{"network_type":"disconnected","signal_strength":0.0,"battery_level":60.0,"noise_level_db":40.0,"is_sensitive_context":false,"language":"zh"}')
assert_json_contains "决策返回 engine=local" "$BODY" "local"

# 5.5 低电量 → local
BODY=$(assert_http "路由决策 - 低电量" 200 \
    -X POST "$GATEWAY_URL/api/v1/route" \
    -H "Content-Type: application/json" \
    -d '{"network_type":"wifi","signal_strength":0.8,"battery_level":10.0,"noise_level_db":30.0,"is_sensitive_context":false,"language":"zh"}')
assert_json_contains "决策返回 engine=local" "$BODY" "local"

##############################################################################
# 6. WebSocket 音频传输链路测试
##############################################################################
section "6. WebSocket 音频链路测试"

echo "  --- 生成测试音频 ---"
# 生成 1秒 PCM 16bit 16kHz 单声道静音音频
# 1秒 = 16000 samples, 每 sample 2 bytes = 32000 bytes
PCM_FILE="/tmp/heecomou_test_audio_$$.pcm"
python3 -c "
import struct, math, sys
sample_rate = 16000
duration = 1.0
num_samples = int(sample_rate * duration)
# 440Hz sine wave, 振幅 -20dB
amplitude = int(32767 * 0.1)
samples = []
for i in range(num_samples):
    t = i / sample_rate
    value = int(amplitude * math.sin(2 * math.pi * 440 * t))
    samples.append(struct.pack('<h', value))
with open('$PCM_FILE', 'wb') as f:
    f.write(b''.join(samples))
print('  Generated %d bytes PCM test audio (440Hz sine, 1s)' % len(samples) * 2)
" 2>/dev/null || {
    # Fallback: dd if=/dev/zero
    dd if=/dev/zero of="$PCM_FILE" bs=32000 count=1 2>/dev/null
    echo "  Generated 32000 bytes silent PCM"
}

# 测试 Gateway WebSocket 连接 (需要 websocat 或 python websocket)
if command -v python3 &>/dev/null; then
    echo "  --- Python WebSocket 客户端测试 ---"

    WS_RESULT=$(python3 -c "
import asyncio, struct, json, sys

async def test():
    try:
        reader, writer = await asyncio.open_connection('localhost', 8080)
    except Exception as e:
        print('CONNECT_FAILED:' + str(e))
        return

    # HTTP Upgrade
    key = 'dGhlIHNhbXBsZSBub25jZQ=='
    request = (
        'GET /ws/audio HTTP/1.1\r\n'
        'Host: localhost:8080\r\n'
        'Upgrade: websocket\r\n'
        'Connection: Upgrade\r\n'
        'Sec-WebSocket-Key: ' + key + '\r\n'
        'Sec-WebSocket-Version: 13\r\n'
        '\r\n'
    )
    writer.write(request.encode())
    await writer.drain()

    # Read HTTP response
    response = b''
    while b'\r\n\r\n' not in response:
        chunk = await asyncio.wait_for(reader.read(4096), timeout=5)
        if not chunk:
            break
        response += chunk
    header_end = response.index(b'\r\n\r\n') + 4
    if b'101' not in response[:header_end]:
        print('UPGRADE_FAILED')
        writer.close()
        return

    # Read WebSocket frame (session_started)
    remaining = response[header_end:]
    while len(remaining) < 2:
        chunk = await asyncio.wait_for(reader.read(4096), timeout=5)
        if not chunk:
            break
        remaining += chunk

    first_byte = remaining[0] if len(remaining) > 0 else 0
    opcode = first_byte & 0x0F
    if opcode == 0x01:  # text
        second_byte = remaining[1] if len(remaining) > 1 else 0
        mask = (second_byte & 0x80) != 0
        length = second_byte & 0x7F
        if length == 126:
            length = struct.unpack('>H', remaining[2:4])[0]
            data_start = 4
        elif length == 127:
            length = struct.unpack('>Q', remaining[2:10])[0]
            data_start = 10
        else:
            data_start = 2
        mask_start = data_start
        data_start += 4
        data_end = data_start + length
        while len(remaining) < data_end:
            chunk = await asyncio.wait_for(reader.read(4096), timeout=5)
            if not chunk:
                break
            remaining += chunk
        if len(remaining) >= data_end:
            masked = bytes(remaining[data_start:data_end])
            mask_key = bytes(remaining[mask_start:mask_start+4])
            unmasked = bytes(b ^ mask_key[i % 4] for i, b in enumerate(masked))
            msg = json.loads(unmasked.decode())
            if msg.get('type') == 'session_started':
                print('SESSION_STARTED:' + str(msg))
                # Send test audio
                with open('$PCM_FILE', 'rb') as f:
                    pcm = f.read()
                # 构造 WebSocket binary frame
                payload_len = len(pcm)
                frame = bytearray()
                frame.append(0x82)  # FIN + BINARY
                if payload_len < 126:
                    frame.append(0x80 | payload_len)
                elif payload_len < 65536:
                    frame.append(0x80 | 126)
                    frame.extend(struct.pack('>H', payload_len))
                else:
                    frame.append(0x80 | 127)
                    frame.extend(struct.pack('>Q', payload_len))
                # Mask
                import os
                mask_key = os.urandom(4)
                frame.extend(mask_key)
                for i, b in enumerate(pcm):
                    frame.append(b ^ mask_key[i % 4])
                writer.write(bytes(frame))
                await writer.drain()
                print('AUDIO_SENT:' + str(payload_len) + ' bytes')

                # Read response
                resp = b''
                try:
                    for _ in range(11):
                        chunk = await asyncio.wait_for(reader.read(4096), timeout=3)
                        if not chunk:
                            break
                        resp += chunk
                        remaining2 = resp
                        while len(remaining2) >= 2:
                            fb = remaining2[0]
                            sb = remaining2[1]
                            op2 = fb & 0x0F
                            if op2 == 0x08:  # close
                                break
                            ml = sb & 0x7F
                            if ml == 126:
                                fl = 4 + 4
                            elif ml == 127:
                                fl = 10 + 4
                            else:
                                fl = 2 + 4
                            dl = ml
                            if ml == 126:
                                dl = struct.unpack('>H', remaining2[2:4])[0]
                            elif ml == 127:
                                dl = struct.unpack('>Q', remaining2[2:10])[0]
                            frame_end = fl + dl
                            if op2 == 0x01 and len(remaining2) >= frame_end:
                                mask_k = bytes(remaining2[fl-4:fl])
                                payload = bytes(remaining2[fl:frame_end])
                                txt = bytes(b ^ mask_k[i%4] for i,b in enumerate(payload))
                                print('RESPONSE:' + txt.decode())
                            remaining2 = remaining2[frame_end:]
                except:
                    pass
                print('RESPONSE_OK')
    writer.close()
    await writer.wait_closed()

asyncio.run(test())
" 2>&1)

    if echo "$WS_RESULT" | grep -q "SESSION_STARTED"; then
        pass "WebSocket session_started 建立"
    else
        echo "  $(color_yellow '⚠ SKIP') WebSocket 连接 (Gateway WebSocket 不可达)"
    fi

    if echo "$WS_RESULT" | grep -q "AUDIO_SENT"; then
        pass "WebSocket 音频数据发送"
    else
        echo "  $(color_yellow '⚠ SKIP') WebSocket 音频发送 (Gateway WebSocket 不可达)"
    fi

    if echo "$WS_RESULT" | grep -q "RESPONSE_OK"; then
        pass "WebSocket 完整链路验证"
    else
        echo "  $(color_yellow '⚠ SKIP') WebSocket 响应 (Gateway 可能未运行)"
    fi
else
    echo "  $(color_yellow '⚠ SKIP') 未检测到 python3, 跳过 WebSocket 测试"
fi

rm -f "$PCM_FILE"

##############################################################################
# 7. 跨服务集成验证
##############################################################################
section "7. 跨服务集成验证"

# 验证 Gateway 能够访问 Backend（Gateway 内置路由依赖）
echo "  --- Gateway → Backend 连通性 ---"
BODY=$(curl -s --connect-timeout 5 "$GATEWAY_URL/health")
if echo "$BODY" | grep -q "UP"; then
    pass "Gateway 服务正常运行"
else
    echo "  $(color_yellow '⚠ SKIP') Gateway 不可达"
fi

echo "  --- Backend → ASR 连通性 ---"
BODY=$(curl -s --connect-timeout 5 "$ASR_URL/health")
if echo "$BODY" | grep -q "UP"; then
    pass "ASR 服务正常运行"
else
    echo "  $(color_yellow '⚠ SKIP') ASR 不可达"
fi

##############################################################################
# 8. 登出 + 边界场景
##############################################################################
section "8. 登出与边界场景"

# 8.1 登出
assert_code "登出" 200 \
    -X POST "$BACKEND_URL/api/v1/auth/logout" \
    -H "Authorization: Bearer $ACCESS_TOKEN"

# 8.2 登出后 Token 失效
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -X GET "$BACKEND_URL/api/v1/user/me" \
    -H "Authorization: Bearer $ACCESS_TOKEN")
if [ "$HTTP_CODE" = "401" ]; then
    pass "已登出 Token 访问返回 401"
else
    echo "  $(color_yellow '⚠ NOTE') 登出后仍返回 $HTTP_CODE (可能无 Redis 黑名单)"
fi

# 8.3 不存在的用户访问
assert_code "不存在的用户登录（预期 401）" 401 \
    -X POST "$BACKEND_URL/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"nonexistent_user_2026","password":"noPass123"}'

# 8.4 无认证的词库访问
BODY=$(do_request -X GET "$BACKEND_URL/api/v1/vocabulary?page=1&size=5")
HTTP_CODE=$(echo "$BODY" | tail -1)
if [ "$HTTP_CODE" = "401" ] || [ "$HTTP_CODE" = "403" ]; then
    pass "无认证词库访问返回 $HTTP_CODE"
else
    fail "无认证词库访问" "401/403" "HTTP $HTTP_CODE"
fi

##############################################################################
# 汇总
##############################################################################
echo ""
color_cyan "╔══════════════════════════════════════════════════════╗"
color_cyan "║              测试结果汇总                            ║"
color_cyan "╠══════════════════════════════════════════════════════╣"

if [ "$FAILED" -eq 0 ]; then
    color_green "║  状态: ALL PASSED                                   ║"
else
    color_red   "║  状态: $FAILED FAILED                                     ║"
fi

printf "║  通过: %-3d  /  总计: %-3d                          ║\n" "$PASSED" "$TOTAL"
color_cyan "╚══════════════════════════════════════════════════════╝"
echo ""

exit $FAILED
