#!/data/data/com.termux/files/usr/bin/bash
# DSH 启动器 APK 构建脚本
set -euo pipefail

PROJ="/storage/emulated/0/#DSH_TERMUX/dsh-launcher"
SDK="/data/data/com.termux/files/home/android-sdk"
ANDROID_JAR="$SDK/platforms/android-34-ext12/android.jar"
OUT="$PROJ/dist"
GEN="$PROJ/gen"
CLS="$PROJ/classes"
DEX="$PROJ/dexout"
APK_NAME="dsh-launcher-v1.0.apk"

echo "==> 清理"
rm -rf "$OUT" "$GEN" "$CLS" "$DEX"
mkdir -p "$OUT" "$GEN" "$CLS" "$DEX"

echo "==> [1/6] aapt2 compile 资源"
aapt2 compile --dir "$PROJ/res" -o "$OUT/resources.zip"

echo "==> [2/6] aapt2 link"
aapt2 link -o "$OUT/unsigned.apk" -I "$ANDROID_JAR" \
  --manifest "$PROJ/AndroidManifest.xml" \
  --java "$GEN" \
  --min-sdk-version 24 --target-sdk-version 34 \
  "$OUT/resources.zip"

echo "==> [3/6] javac 编译"
find "$GEN" "$PROJ/java" -name '*.java' > "$OUT/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options \
  -bootclasspath "$ANDROID_JAR" \
  -d "$CLS" @"$OUT/sources.txt"
echo "    编译了 $(wc -l < "$OUT/sources.txt") 个源文件"

echo "==> [4/6] d8 -> classes.dex"
find "$CLS" -name '*.class' > "$OUT/classes.txt"
d8 --lib "$ANDROID_JAR" --release --output "$DEX" @"$OUT/classes.txt"
ls -la "$DEX/classes.dex"

echo "==> [5/6] 注入 dex 并签名"
python3 - "$OUT/unsigned.apk" "$DEX/classes.dex" <<'PY'
import sys, zipfile, shutil
apk, dex = sys.argv[1], sys.argv[2]
tmp = apk + ".tmp"
with zipfile.ZipFile(apk) as zin, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        zout.writestr(item, zin.read(item.filename))
    zout.write(dex, "classes.dex")
shutil.move(tmp, apk)
print("    classes.dex 已注入")
PY

if [ ! -f "$PROJ/keystore.jks" ]; then
  echo "==> 生成签名密钥 (keystore.jks)"
  keytool -genkeypair -keystore "$PROJ/keystore.jks" -alias dsh \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass dsh123456 -keypass dsh123456 \
    -dname "CN=DSH Launcher, OU=DSH, O=DSH, L=Unknown, ST=Unknown, C=CN"
fi

apksigner sign --ks "$PROJ/keystore.jks" --ks-pass pass:dsh123456 --key-pass pass:dsh123456 \
  --out "$OUT/$APK_NAME" "$OUT/unsigned.apk"

echo "==> [6/6] 验证"
apksigner verify --verbose "$OUT/$APK_NAME" | head -6
echo ""
echo "构建完成: $OUT/$APK_NAME"
ls -la "$OUT/$APK_NAME"
