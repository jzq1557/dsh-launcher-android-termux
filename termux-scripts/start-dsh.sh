#!/data/data/com.termux/files/usr/bin/bash
# DSH Web 一键启动脚本（幂等：已在运行/启动中则不重复启动）
set -u
export PATH="/data/data/com.termux/files/usr/bin:${PATH}"
LOG="${HOME}/.dsh/dsh-web.log"
URL="http://127.0.0.1:3080/"

wait_for_port() {
  for i in $(seq 1 45); do
    sleep 1
    if curl -s -o /dev/null --max-time 2 "${URL}"; then
      return 0
    fi
  done
  return 1
}

# 已在运行
if curl -s -o /dev/null --max-time 2 "${URL}"; then
  echo "dsh web 已在运行"
  exit 0
fi

# 已有 dsh web 进程在启动中（node 冷启动可能很慢）→ 等待端口就绪
if pgrep -f "dsh web" >/dev/null 2>&1; then
  echo "dsh web 正在启动中，等待端口就绪…"
  if wait_for_port; then
    echo "dsh web 已就绪"
    exit 0
  fi
  echo "等待超时"
  exit 1
fi

# 全新启动
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock 2>/dev/null || true
mkdir -p "${HOME}/.dsh"
# dsh 的 shebang 是 #!/usr/bin/env node，Android 上 /usr/bin/env 不存在，
# 必须用 node 直接执行，否则 nohup 会报 "failed to run command 'dsh'"
NODE="/data/data/com.termux/files/usr/bin/node"
DSH="/data/data/com.termux/files/usr/bin/dsh"
nohup "${NODE}" "${DSH}" web >"${LOG}" 2>&1 &
echo "dsh web 已启动，等待端口就绪…"

if wait_for_port; then
  echo "dsh web 已就绪"
  exit 0
fi
echo "启动超时，请查看日志: ${LOG}"
exit 1
