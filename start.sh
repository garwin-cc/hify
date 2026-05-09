#!/usr/bin/env bash
set -uo pipefail   # 不用 -e：改为各处显式检查，避免 (( i++ )) / [[ ]] 误触退出

# ── 颜色 ─────────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()  { printf "${GREEN}[Hify]${NC} %s\n" "$*"; }
warn()  { printf "${YELLOW}[Hify]${NC} %s\n" "$*"; }
error() { printf "${RED}[Hify]${NC} %s\n" "$*" >&2; }

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/hify-app"
FRONTEND_DIR="$ROOT_DIR/hify-web"
LOG_DIR="$ROOT_DIR/logs"
BACKEND_LOG="$LOG_DIR/backend.log"
FRONTEND_LOG="$LOG_DIR/frontend.log"
PID_FILE="$LOG_DIR/hify.pid"

mkdir -p "$LOG_DIR"

# ── 工具检查 ──────────────────────────────────────────────────────────────────
check_command() {
  if ! command -v "$1" &>/dev/null; then
    error "未找到命令：$1。请先安装后再运行。"
    exit 1
  fi
}

# ── Java 17 ───────────────────────────────────────────────────────────────────
setup_java() {
  if [[ -z "${JAVA_HOME:-}" ]]; then
    if /usr/libexec/java_home -v 17 &>/dev/null; then
      export JAVA_HOME=$(/usr/libexec/java_home -v 17)
      export PATH="$JAVA_HOME/bin:$PATH"
    fi
  fi
  local java_ver
  java_ver=$(java -version 2>&1 | awk -F '"' '/version/{print $2}' | cut -d. -f1)
  if [[ "$java_ver" -lt 17 ]]; then
    error "需要 Java 17+，当前版本：${java_ver}"
    error "请运行：brew install --cask temurin@17"
    exit 1
  fi
}

# ── 停止已有进程 ───────────────────────────────────────────────────────────────
stop_all() {
  if [[ -f "$PID_FILE" ]]; then
    while IFS= read -r pid; do
      if kill -0 "$pid" 2>/dev/null; then
        kill "$pid" 2>/dev/null && info "已停止进程 ${pid}"
      fi
    done < "$PID_FILE"
    rm -f "$PID_FILE"
  fi

  # 兜底：按端口杀进程（用 if 代替 && 链，避免 [[ ]] 返回 1 被 set -e 捕获）
  local be_pid fe_pid
  be_pid=$(lsof -ti:8080 2>/dev/null || true)
  fe_pid=$(lsof -ti:5173 2>/dev/null || true)

  if [[ -n "$be_pid" ]]; then
    kill "$be_pid" 2>/dev/null || true
    warn "已终止占用 8080 的进程 ${be_pid}"
  fi
  if [[ -n "$fe_pid" ]]; then
    kill "$fe_pid" 2>/dev/null || true
    warn "已终止占用 5173 的进程 ${fe_pid}"
  fi
}

# ── 等待端口就绪 ───────────────────────────────────────────────────────────────
wait_for_port() {
  local port=$1 name=$2 timeout=${3:-60}
  info "等待 ${name} 就绪（端口 ${port}，超时 ${timeout}s）..."
  local i=0
  while ! lsof -ti:"$port" &>/dev/null; do
    sleep 1
    i=$((i + 1))   # 用赋值代替 (( i++ ))，避免 i=0 时算术表达式值为 0 触发 set -e
    if [[ $i -ge $timeout ]]; then
      error "${name} 启动超时，请查看日志：${LOG_DIR}"
      return 1
    fi
  done
  info "${name} 已就绪 ✓"
}

# ── 启动后端 ───────────────────────────────────────────────────────────────────
start_backend() {
  info "构建后端..."
  cd "$ROOT_DIR"
  if ! mvn clean install -DskipTests -q 2>>"$BACKEND_LOG"; then
    error "Maven 构建失败，查看：${BACKEND_LOG}"
    exit 1
  fi

  info "启动后端..."
  cd "$BACKEND_DIR"
  mvn spring-boot:run >> "$BACKEND_LOG" 2>&1 &
  local pid=$!
  echo "$pid" >> "$PID_FILE"
  info "后端进程 PID=${pid}  log: ${BACKEND_LOG}"

  if ! wait_for_port 8080 "后端" 90; then
    error "后端启动失败，最近日志："
    tail -20 "$BACKEND_LOG" >&2
    exit 1
  fi
}

# ── 启动前端 ───────────────────────────────────────────────────────────────────
start_frontend() {
  info "启动前端开发服务器..."
  cd "$FRONTEND_DIR"
  npm run dev >> "$FRONTEND_LOG" 2>&1 &
  local pid=$!
  echo "$pid" >> "$PID_FILE"
  info "前端进程 PID=${pid}  log: ${FRONTEND_LOG}"

  if ! wait_for_port 5173 "前端" 30; then
    error "前端启动失败，最近日志："
    tail -10 "$FRONTEND_LOG" >&2
    exit 1
  fi
}

# ── 退出时清理 ────────────────────────────────────────────────────────────────
cleanup() {
  printf "\n"
  warn "收到退出信号，正在停止服务..."
  stop_all
  info "已全部停止。"
}
trap cleanup INT TERM

# ── 主流程 ────────────────────────────────────────────────────────────────────
main() {
  info "===== Hify 一键启动 ====="

  check_command mvn
  check_command npm
  check_command node
  setup_java

  stop_all
  start_backend
  start_frontend

  printf "\n"
  info "============================="
  info "后端：http://localhost:8080"
  info "前端：http://localhost:5173"
  info "============================="
  info "按 Ctrl+C 停止所有服务"
  printf "\n"

  tail -f "$BACKEND_LOG" "$FRONTEND_LOG"
}

main "$@"
