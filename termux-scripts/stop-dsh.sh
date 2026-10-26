#!/data/data/com.termux/files/usr/bin/bash
# 停止 DSH Web 服务，并自动清理 Termux 后台进程
export PATH="/data/data/com.termux/files/usr/bin:${PATH}"
URL="http://127.0.0.1:3080/"

# 1. 先停止 dsh web
pkill -f "dsh web" 2>/dev/null || true
sleep 1
if curl -s -o /dev/null --max-time 2 "${URL}"; then
  pkill -9 -f "bin.js web" 2>/dev/null || true
fi

# 2. 再清理 Termux 自身后台进程（放最后，杀完即退）
pkill -9 -f "com.termux.app.TermuxService" 2>/dev/null || true
pkill -9 -f "com.termux.app.TermuxActivity" 2>/dev/null || true
pkill -9 -f "com.termux.app.RunCommandService" 2>/dev/null || true
pkill -9 -f "com.termux" 2>/dev/null || true

echo "DSH Web 已停止，Termux 后台进程已清理"
