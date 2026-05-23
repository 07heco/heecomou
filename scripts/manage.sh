#!/usr/bin/env bash

APP_DIR="/opt/heecomou/app"
VENV_PATH="/opt/heecomou/venv"
LOG_DIR="/opt/heecomou/logs"
GATEWAY_BIN="/opt/heecomou/gateway"
JAVA_JAR="$APP_DIR/backend-java/target/heecomou-backend-0.1.0-SNAPSHOT.jar"
ASR_SCRIPT="$APP_DIR/asr-python/src/main.py"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

usage() {
    echo "用法: $0 {start|stop|restart|status|logs}"
    echo ""
    echo "  start   启动全部服务"
    echo "  stop    停止全部服务"
    echo "  restart 重启全部服务"
    echo "  status  查看服务状态"
    echo "  logs    查看实时日志"
    exit 1
}

check_status() {
    local name=$1
    local port=$2
    local url=$3

    if [ -n "$port" ] && curl -s --max-time 3 "$url" &> /dev/null; then
        echo -e "${GREEN}  RUNNING${NC}"
    elif [ -n "$port" ]; then
        echo -e "${RED}  STOPPED${NC}"
    else
        docker-compose -f "$APP_DIR/docker-compose.yml" ps "$name" 2>/dev/null | grep -q healthy && \
            echo -e "${GREEN}  RUNNING${NC}" || echo -e "${RED}  STOPPED${NC}"
    fi
}

cmd_status() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}  HeecoMou 服务状态${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""

    echo -n "Go Gateway    (port 8080) :"
    check_status "gateway" 8080 "http://localhost:8080/health"

    echo -n "Java Backend  (port 8081) :"
    check_status "backend" 8081 "http://localhost:8081/api/v1/health"

    echo -n "Python ASR    (port 8082) :"
    check_status "asr" 8082 "http://localhost:8082/health"

    echo -n "MySQL         (port 3306) :"
    check_status "mysql"

    echo -n "Redis         (port 6379) :"
    check_status "redis"

    echo ""
}

cmd_start() {
    echo -e "${GREEN}启动 MySQL + Redis...${NC}"
    cd "$APP_DIR"
    docker-compose up -d
    echo "等待 MySQL/Redis 就绪 (15s)..."
    sleep 15

    echo -e "${GREEN}启动 Java 后端...${NC}"
    if [ -f "$LOG_DIR/backend.pid" ] && kill -0 $(cat "$LOG_DIR/backend.pid") 2>/dev/null; then
        echo "  Java 后端已在运行"
    else
        nohup java -jar \
            -Dserver.port=8081 \
            -Dspring.profiles.active=dev \
            "$JAVA_JAR" \
            > "$LOG_DIR/backend.log" 2>&1 &
        echo $! > "$LOG_DIR/backend.pid"
        echo "  PID: $(cat $LOG_DIR/backend.pid)"
    fi
    sleep 5

    echo -e "${GREEN}启动 Python ASR...${NC}"
    if [ -f "$LOG_DIR/asr.pid" ] && kill -0 $(cat "$LOG_DIR/asr.pid") 2>/dev/null; then
        echo "  Python ASR 已在运行"
    else
        source "$VENV_PATH/bin/activate"
        nohup python "$ASR_SCRIPT" \
            > "$LOG_DIR/asr.log" 2>&1 &
        echo $! > "$LOG_DIR/asr.pid"
        echo "  PID: $(cat $LOG_DIR/asr.pid)"
    fi
    sleep 3

    echo -e "${GREEN}启动 Go 网关...${NC}"
    if [ -f "$LOG_DIR/gateway.pid" ] && kill -0 $(cat "$LOG_DIR/gateway.pid") 2>/dev/null; then
        echo "  Go 网关已在运行"
    else
        nohup "$GATEWAY_BIN" \
            > "$LOG_DIR/gateway.log" 2>&1 &
        echo $! > "$LOG_DIR/gateway.pid"
        echo "  PID: $(cat $LOG_DIR/gateway.pid)"
    fi
    sleep 2

    echo ""
    echo -e "${GREEN}全部服务启动完成${NC}"
    cmd_status
}

cmd_stop() {
    echo -e "${YELLOW}停止 Go 网关...${NC}"
    if [ -f "$LOG_DIR/gateway.pid" ]; then
        kill $(cat "$LOG_DIR/gateway.pid") 2>/dev/null && echo "  OK" || echo "  已停止"
        rm -f "$LOG_DIR/gateway.pid"
    fi

    echo -e "${YELLOW}停止 Python ASR...${NC}"
    if [ -f "$LOG_DIR/asr.pid" ]; then
        kill $(cat "$LOG_DIR/asr.pid") 2>/dev/null && echo "  OK" || echo "  已停止"
        rm -f "$LOG_DIR/asr.pid"
    fi

    echo -e "${YELLOW}停止 Java 后端...${NC}"
    if [ -f "$LOG_DIR/backend.pid" ]; then
        kill $(cat "$LOG_DIR/backend.pid") 2>/dev/null && echo "  OK" || echo "  已停止"
        rm -f "$LOG_DIR/backend.pid"
    fi

    echo -e "${YELLOW}停止 MySQL + Redis...${NC}"
    cd "$APP_DIR"
    docker-compose down 2>/dev/null && echo "  Docker 服务已停止"

    echo ""
    echo -e "${YELLOW}全部服务已停止${NC}"
}

cmd_restart() {
    cmd_stop
    sleep 3
    cmd_start
}

cmd_logs() {
    echo ""
    echo -e "${BLUE}实时日志输出 (Ctrl+C 退出)${NC}"
    echo ""
    echo -e "${YELLOW}可单独查看某个服务日志:${NC}"
    echo "  tail -f $LOG_DIR/gateway.log"
    echo "  tail -f $LOG_DIR/backend.log"
    echo "  tail -f $LOG_DIR/asr.log"
    echo ""

    tail -f "$LOG_DIR/gateway.log" "$LOG_DIR/backend.log" "$LOG_DIR/asr.log"
}

mkdir -p "$LOG_DIR"

case "$1" in
    start)   cmd_start ;;
    stop)    cmd_stop ;;
    restart) cmd_restart ;;
    status)  cmd_status ;;
    logs)    cmd_logs ;;
    *)       usage ;;
esac