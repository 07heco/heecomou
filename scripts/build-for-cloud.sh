#!/bin/bash
set -e
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "=== HeecoMou 云部署构建 ==="
echo "构建目录: $PROJECT_ROOT"

# Backend
echo "[1/2] Building Java Backend..."
cd "$PROJECT_ROOT/backend-java"
mvn clean package -DskipTests -q
BACKEND_JAR="$PROJECT_ROOT/backend-java/target/heecomou-backend-0.1.0-SNAPSHOT.jar"
if [ -f "$BACKEND_JAR" ]; then
    echo "  -> $BACKEND_JAR ($(du -sh "$BACKEND_JAR" | cut -f1))"
else
    echo "  ERROR: build failed"
    exit 1
fi

# Gateway
echo "[2/2] Building Go Gateway..."
cd "$PROJECT_ROOT/gateway-go"
GOOS=linux GOARCH=amd64 go build -o gateway ./cmd/gateway/
GATEWAY_BIN="$PROJECT_ROOT/gateway-go/gateway"
if [ -f "$GATEWAY_BIN" ]; then
    echo "  -> $GATEWAY_BIN ($(du -sh "$GATEWAY_BIN" | cut -f1))"
else
    echo "  ERROR: build failed"
    exit 1
fi

echo ""
echo "Build complete. Upload to server:"
echo ""
echo "  scp scripts/deploy-server.sh root@117.72.201.26:/root/"
echo "  scp $BACKEND_JAR root@117.72.201.26:/opt/heecomou/backend/"
echo "  scp $GATEWAY_BIN root@117.72.201.26:/opt/heecomou/gateway/"
echo "  scp -r backend-java/src/main/resources root@117.72.201.26:/opt/heecomou/backend/"
echo "  scp -r gateway-go root@117.72.201.26:/opt/heecomou/"
echo "  scp -r asr-python/src asr-python/requirements.txt asr-python/proto root@117.72.201.26:/opt/heecomou/asr/"
echo ""
echo "Then on server: bash /root/deploy-server.sh"