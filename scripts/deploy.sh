#!/usr/bin/env bash
set -e

APP_DIR="/opt/heecomou/app"
VENV_PATH="/opt/heecomou/venv"
LOG_DIR="/opt/heecomou/logs"
REPO_URL="https://github.com/07heco/heecomou.git"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

check_root() {
    if [ "$EUID" -ne 0 ]; then
        log_error "请使用 root 用户执行此脚本"
        exit 1
    fi
}

install_docker() {
    log_info "安装 Docker..."
    if ! command -v docker &> /dev/null; then
        curl -fsSL https://get.docker.com | bash
        systemctl enable docker
        systemctl start docker
    else
        log_info "Docker 已安装，跳过"
    fi

    if ! command -v docker-compose &> /dev/null; then
        curl -SL https://github.com/docker/compose/releases/download/v2.24.0/docker-compose-linux-x86_64 -o /usr/local/bin/docker-compose
        chmod +x /usr/local/bin/docker-compose
    fi
    log_info "Docker 就绪"
}

install_go() {
    log_info "安装 Go 1.22..."
    if command -v go &> /dev/null; then
        log_info "Go 已安装，跳过"
        return
    fi
    cd /tmp
    wget -q https://go.dev/dl/go1.22.3.linux-amd64.tar.gz
    rm -rf /usr/local/go
    tar -C /usr/local -xzf go1.22.3.linux-amd64.tar.gz
    grep -q '/usr/local/go/bin' ~/.bashrc || {
        echo 'export PATH=$PATH:/usr/local/go/bin' >> ~/.bashrc
        echo 'export GOPATH=$HOME/go' >> ~/.bashrc
    }
    export PATH=$PATH:/usr/local/go/bin
    export GOPATH=$HOME/go
    log_info "Go 安装完成"
}

install_java() {
    log_info "安装 Java 17..."
    if command -v java &> /dev/null; then
        log_info "Java 已安装，跳过"
        return
    fi
    apt update -qq && apt install -y -qq openjdk-17-jdk
    log_info "Java 安装完成"
}

install_maven() {
    log_info "安装 Maven 3.9..."
    if command -v mvn &> /dev/null; then
        log_info "Maven 已安装，跳过"
        return
    fi
    cd /tmp
    wget -q https://dlcdn.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz
    tar -C /opt -xzf apache-maven-3.9.6-bin.tar.gz
    grep -q 'maven' ~/.bashrc || echo 'export PATH=$PATH:/opt/apache-maven-3.9.6/bin' >> ~/.bashrc
    export PATH=$PATH:/opt/apache-maven-3.9.6/bin
    log_info "Maven 安装完成"
}

install_python() {
    log_info "安装 Python 3.11..."
    apt install -y -qq python3.11 python3.11-venv python3-pip
    if [ ! -d "$VENV_PATH" ]; then
        python3.11 -m venv "$VENV_PATH"
    fi
    source "$VENV_PATH/bin/activate"
    pip install --upgrade pip -q
}

clone_project() {
    log_info "克隆项目..."
    mkdir -p /opt/heecomou "$LOG_DIR"
    if [ -d "$APP_DIR/.git" ]; then
        cd "$APP_DIR" && git pull origin main
    else
        git clone "$REPO_URL" "$APP_DIR"
    fi
}

build_go() {
    log_info "编译 Go 网关..."
    cd "$APP_DIR/gateway-go"
    export PATH=$PATH:/usr/local/go/bin
    go mod tidy
    go build -o /opt/heecomou/gateway ./cmd/gateway/
    log_info "Go 网关编译完成"
}

build_java() {
    log_info "编译 Java 后端..."
    cd "$APP_DIR/backend-java"
    mvn clean package -DskipTests -q
    log_info "Java 编译完成"
}

install_python_deps() {
    log_info "安装 Python 依赖..."
    source "$VENV_PATH/bin/activate"
    cd "$APP_DIR/asr-python"
    pip install -r requirements.txt -q
    log_info "Python 依赖安装完成"
}

start_services() {
    log_info "启动 MySQL + Redis..."
    cd "$APP_DIR" && docker-compose up -d
    sleep 15

    log_info "启动 Java 后端..."
    nohup java -jar -Dserver.port=8081 -Dspring.profiles.active=dev \
        "$APP_DIR/backend-java/target/heecomou-backend-0.1.0-SNAPSHOT.jar" \
        > "$LOG_DIR/backend.log" 2>&1 &
    echo $! > "$LOG_DIR/backend.pid"
    sleep 5

    log_info "启动 Python ASR..."
    source "$VENV_PATH/bin/activate"
    nohup python "$APP_DIR/asr-python/src/main.py" \
        > "$LOG_DIR/asr.log" 2>&1 &
    echo $! > "$LOG_DIR/asr.pid"
    sleep 3

    log_info "启动 Go 网关..."
    nohup /opt/heecomou/gateway \
        > "$LOG_DIR/gateway.log" 2>&1 &
    echo $! > "$LOG_DIR/gateway.pid"
    sleep 2
}

verify() {
    echo ""
    echo "=============================================="
    echo "  验证部署结果"
    echo "=============================================="
    for svc in "Java:8081:/api/v1/health" "Python:8082:/health" "Go:8080:/health"; do
        name=$(echo $svc | cut -d: -f1)
        port=$(echo $svc | cut -d: -f2)
        path=$(echo $svc | cut -d: -f3)
        if curl -s --max-time 3 "http://localhost:$port$path" | grep -q UP; then
            echo -e "  ${GREEN}[UP]${NC}   $name (port $port)"
        else
            echo -e "  ${RED}[DOWN]${NC} $name (port $port)"
        fi
    done
    echo ""
    echo "部署完成！日志: $LOG_DIR"
}

main() {
    echo ""
    echo "=============================================="
    echo "  HeecoMou 一键部署脚本"
    echo "=============================================="
    echo ""
    check_root
    install_docker
    install_go
    install_java
    install_maven
    install_python
    clone_project
    build_go
    build_java
    install_python_deps
    start_services
    verify
}

main