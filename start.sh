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
STOP_TIMEOUT=15

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
wait_pid_exit() {
  local pid=$1 name=${2:-进程}
  local i=0
  while kill -0 "$pid" 2>/dev/null; do
    sleep 1
    i=$((i + 1))
    if [[ $i -ge $STOP_TIMEOUT ]]; then
      warn "${name} ${pid} 未在 ${STOP_TIMEOUT}s 内退出，发送 SIGKILL"
      kill -KILL "$pid" 2>/dev/null || true
      break
    fi
  done
}

stop_pids() {
  local name=$1
  shift
  local pids=("$@")
  if [[ ${#pids[@]} -eq 0 ]]; then
    return 0
  fi
  for pid in "${pids[@]}"; do
    if kill -0 "$pid" 2>/dev/null; then
      kill -TERM "$pid" 2>/dev/null || true
      warn "已发送 SIGTERM 给 ${name} 进程 ${pid}"
    fi
  done
  for pid in "${pids[@]}"; do
    wait_pid_exit "$pid" "$name"
  done
}

wait_port_free() {
  local port=$1 name=$2
  local i=0
  while lsof -ti:"$port" &>/dev/null; do
    sleep 1
    i=$((i + 1))
    if [[ $i -ge $STOP_TIMEOUT ]]; then
      local pids
      pids=$(lsof -ti:"$port" 2>/dev/null || true)
      if [[ -n "$pids" ]]; then
        warn "${name} 端口 ${port} 仍被占用，发送 SIGKILL：${pids}"
        # shellcheck disable=SC2086
        kill -KILL $pids 2>/dev/null || true
      fi
      break
    fi
  done
}

find_backend_jar_pids() {
  local pids=""
  if compgen -G "$BACKEND_DIR/target/hify-app-*.jar" >/dev/null; then
    pids=$(lsof -t "$BACKEND_DIR"/target/hify-app-*.jar 2>/dev/null || true)
  fi
  if [[ -n "$pids" ]]; then
    printf "%s\n" "$pids" | sort -u
  fi
}

stop_all() {
  if [[ -f "$PID_FILE" ]]; then
    local pid_file_pids=()
    while IFS= read -r pid; do
      [[ -n "$pid" ]] && pid_file_pids+=("$pid")
    done < "$PID_FILE"
    stop_pids "PID 文件记录" "${pid_file_pids[@]}"
    rm -f "$PID_FILE"
  fi

  # 兜底：清理被 nohup/IDE/旧脚本脱管的 packaged jar 后端进程。
  local jar_pids
  jar_pids=$(find_backend_jar_pids)
  if [[ -n "$jar_pids" ]]; then
    # shellcheck disable=SC2206
    stop_pids "脱管后端 jar" ${jar_pids}
  fi

  # 兜底：按端口杀进程，并等待端口真正释放。
  local be_pid fe_pid
  be_pid=$(lsof -ti:8080 2>/dev/null || true)
  fe_pid=$(lsof -ti:5173 2>/dev/null || true)

  if [[ -n "$be_pid" ]]; then
    # shellcheck disable=SC2206
    stop_pids "占用 8080" ${be_pid}
    wait_port_free 8080 "后端"
  fi
  if [[ -n "$fe_pid" ]]; then
    # shellcheck disable=SC2206
    stop_pids "占用 5173" ${fe_pid}
    wait_port_free 5173 "前端"
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
  wait_port_free 8080 "后端"

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
