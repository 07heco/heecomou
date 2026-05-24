#!/bin/bash
set -e

echo "===================================="
echo "  HeecoMou Cloud Server Deployment"
echo "===================================="

APP_DIR="/opt/heecomou"
LOG_DIR="$APP_DIR/logs"

mkdir -p "$APP_DIR"/{backend,gateway,asr,logs}

# ============================================
# 1. Install system dependencies
# ============================================
echo "[1/6] Installing system dependencies..."

if ! command -v java &>/dev/null; then
    echo "  Installing JDK 17..."
    sudo apt update -qq
    sudo apt install -y -qq openjdk-17-jdk
fi
echo "  Java: $(java -version 2>&1 | head -1)"

if ! command -v python3 &>/dev/null; then
    sudo apt install -y -qq python3 python3-pip python3-venv
fi
echo "  Python: $(python3 --version)"

# ============================================
# 2. Verify MySQL and Redis
# ============================================
echo "[2/6] Verifying MySQL and Redis..."
docker ps --format "table {{.Names}}\t{{.Status}}" | grep -E "heecomou-mysql|heecomou-redis" || {
    echo "  WARNING: heecomou-mysql or heecomou-redis not running!"
    echo "  Starting with docker-compose..."
    cd "$APP_DIR" && docker compose up -d mysql redis 2>/dev/null || true
}

# ============================================
# 3. Stop old processes
# ============================================
echo "[3/6] Stopping existing services..."
pkill -f "heecomou-backend" 2>/dev/null || true
pkill -f "gateway-go" 2>/dev/null || true
pkill -f "uvicorn.*main:app" 2>/dev/null || true
sleep 2

# ============================================
# 4. Java Backend (:8081)
# ============================================
echo "[4/6] Starting Backend (:8081)..."

BACKEND_JAR="$APP_DIR/backend/heecomou-backend-0.1.0-SNAPSHOT.jar"

if [ ! -f "$BACKEND_JAR" ]; then
    echo "  ERROR: $BACKEND_JAR not found!"
    echo "  Upload it first: scp backend-java/target/heecomou-backend-0.1.0-SNAPSHOT.jar root@117.72.201.26:$APP_DIR/backend/"
    exit 1
fi

# On server, MySQL/Redis are on localhost (Docker)
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/heecomou?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
export SPRING_DATASOURCE_USERNAME="root"
export SPRING_DATASOURCE_PASSWORD="heecomou123"
export SPRING_DATA_REDIS_HOST="localhost"
export SPRING_DATA_REDIS_PORT="6379"
export SPRING_PROFILES_ACTIVE="dev"
export JWT_SECRET="heecomou_jwt_secret_key_2026_min_32chars"
export JWT_EXPIRATION="1800000"
export JWT_REFRESH_EXPIRATION="604800000"

nohup java -jar "$BACKEND_JAR" \
    --server.port=8081 \
    > "$LOG_DIR/backend.log" 2>&1 &
echo "  Backend PID: $!"

# Wait for startup
echo "  Waiting for Backend healthy..."
for i in $(seq 1 30); do
    if curl -s http://localhost:8081/api/v1/health | grep -q "UP"; then
        echo "  Backend started OK"
        break
    fi
    sleep 2
done

# ============================================
# 5. Go Gateway (:8080)
# ============================================
echo "[5/6] Starting Gateway (:8080)..."

GATEWAY_BIN="$APP_DIR/gateway/gateway"

if [ ! -f "$GATEWAY_BIN" ]; then
    echo "  ERROR: $GATEWAY_BIN not found!"
    echo "  Build locally: cd gateway-go && GOOS=linux GOARCH=amd64 go build -o gateway ./cmd/gateway/"
    echo "  Then: scp gateway root@117.72.201.26:$APP_DIR/gateway/"
    exit 1
fi

chmod +x "$GATEWAY_BIN"
nohup "$GATEWAY_BIN" > "$LOG_DIR/gateway.log" 2>&1 &
echo "  Gateway PID: $!"

sleep 2
if curl -s http://localhost:8080/health | grep -q "UP"; then
    echo "  Gateway started OK"
else
    echo "  WARNING: Gateway may not have started. Check $LOG_DIR/gateway.log"
fi

# ============================================
# 6. Python ASR (:8082) [optional]
# ============================================
echo "[6/6] Setting up Python ASR (:8082)..."
echo "  This step is optional and downloads ~3.5GB model."
echo "  Press Enter to skip, or type 'yes' to install:"
read -t 5 -r INSTALL_ASR || INSTALL_ASR="no"

if [ "$INSTALL_ASR" = "yes" ]; then
    ASR_DIR="$APP_DIR/asr"

    cd "$ASR_DIR"
    pip3 install -r requirements.txt -q

    export ASR_MODEL_ID="${ASR_MODEL_ID:-Qwen/Qwen3-ASR-1.7B}"
    export BACKEND_URL="http://localhost:8081"

    nohup python3 src/main.py > "$LOG_DIR/asr.log" 2>&1 &
    echo "  ASR PID: $!"
    echo "  Model download may take several minutes. Check: tail -f $LOG_DIR/asr.log"
else
    echo "  ASR skipped (add 'yes' later to install)"
fi

# ============================================
# Summary
# ============================================
echo ""
echo "===================================="
echo "  Deployment Complete"
echo "===================================="
echo "  Backend:  http://117.72.201.26:8081/api/v1/health"
echo "  Gateway:  http://117.72.201.26:8080/health"
echo "  ASR:      http://117.72.201.26:8082/health  (if installed)"
echo ""
echo "  Logs:     $LOG_DIR/"
echo "  Stop all: pkill -f 'heecomou-backend|gateway-go|uvicorn'"
echo ""
echo "  Test:"
echo "    curl http://117.72.201.26:8081/api/v1/health"
echo "    curl http://117.72.201.26:8080/health"
echo "===================================="