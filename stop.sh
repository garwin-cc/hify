#!/usr/bin/env bash
set -uo pipefail

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()  { printf "${GREEN}[Hify]${NC} %s\n" "$*"; }
warn()  { printf "${YELLOW}[Hify]${NC} %s\n" "$*"; }
error() { printf "${RED}[Hify]${NC} %s\n" "$*" >&2; }

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/hify-app"
PID_FILE="$ROOT_DIR/logs/hify.pid"
GRACEFUL_TIMEOUT=15   # 等待 SIGTERM 生效的秒数

# 优雅停止单个进程：先 SIGTERM，超时后 SIGKILL
stop_pid() {
  local pid=$1

  if ! kill -0 "$pid" 2>/dev/null; then
    warn "进程 ${pid} 已不存在，跳过"
    return 0
  fi

  info "发送 SIGTERM 给进程 ${pid}..."
  kill -TERM "$pid" 2>/dev/null || true

  local i=0
  while kill -0 "$pid" 2>/dev/null; do
    sleep 1
    i=$((i + 1))
    if [[ $i -ge $GRACEFUL_TIMEOUT ]]; then
      warn "进程 ${pid} 未在 ${GRACEFUL_TIMEOUT}s 内退出，发送 SIGKILL..."
      kill -KILL "$pid" 2>/dev/null || true
      return 0
    fi
  done

  info "进程 ${pid} 已正常退出"
}

stop_port() {
  local port=$1 name=$2
  local pids
  pids=$(lsof -ti:"$port" 2>/dev/null || true)
  if [[ -z "$pids" ]]; then
    return 0
  fi
  warn "发现 ${name} 端口 ${port} 仍被占用：${pids}"
  # shellcheck disable=SC2086
  for pid in $pids; do
    stop_pid "$pid"
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

stop_backend_jars() {
  local pids
  pids=$(find_backend_jar_pids)
  if [[ -z "$pids" ]]; then
    return 0
  fi
  warn "发现脱管后端 jar 进程：${pids}"
  # shellcheck disable=SC2086
  for pid in $pids; do
    stop_pid "$pid"
  done
}

main() {
  info "===== Hify 停止 ====="

  if [[ ! -f "$PID_FILE" ]]; then
    warn "未找到 PID 文件（${PID_FILE}），改用端口兜底检查"
    stop_backend_jars
    stop_port 8080 "后端"
    stop_port 5173 "前端"
    exit 0
  fi

  local pids=()
  while IFS= read -r pid; do
    [[ -n "$pid" ]] && pids+=("$pid")
  done < "$PID_FILE"

  if [[ ${#pids[@]} -eq 0 ]]; then
    warn "PID 文件为空，无进程可停止"
    rm -f "$PID_FILE"
    exit 0
  fi

  # 并发发送 SIGTERM，再逐个等待，比串行等待快
  for pid in "${pids[@]}"; do
    if kill -0 "$pid" 2>/dev/null; then
      info "发送 SIGTERM 给进程 ${pid}..."
      kill -TERM "$pid" 2>/dev/null || true
    else
      warn "进程 ${pid} 已不存在，跳过"
    fi
  done

  for pid in "${pids[@]}"; do
    if ! kill -0 "$pid" 2>/dev/null; then
      continue
    fi

    local i=0
    while kill -0 "$pid" 2>/dev/null; do
      sleep 1
      i=$((i + 1))
      if [[ $i -ge $GRACEFUL_TIMEOUT ]]; then
        warn "进程 ${pid} 未在 ${GRACEFUL_TIMEOUT}s 内退出，发送 SIGKILL..."
        kill -KILL "$pid" 2>/dev/null || true
        break
      fi
    done

    if ! kill -0 "$pid" 2>/dev/null; then
      info "进程 ${pid} 已停止"
    fi
  done

  rm -f "$PID_FILE"
  stop_backend_jars
  stop_port 8080 "后端"
  stop_port 5173 "前端"
  info "===== 全部停止完成 ====="
}

main "$@"
