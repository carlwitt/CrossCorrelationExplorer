#!/bin/bash
# Builds the fat jar and assembles a double-clickable "Cross Correlation
# Explorer.app" bundle in dist/, embedding the current $JAVA_HOME as the
# app's runtime and compiling img/icon.iconset into the app icon.
# Usage: ./package-macos.sh
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

if [ -z "${JAVA_HOME:-}" ]; then
    echo "JAVA_HOME is not set; point it at a JDK 11+ install." >&2
    exit 1
fi

APP="dist/Cross Correlation Explorer.app"
ICONSET="img/icon.iconset"
ICNS="img/CrossCorrelationExplorer.icns"

echo "==> Building jar"
mvn -q clean package

echo "==> Compiling app icon from $ICONSET"
iconutil -c icns "$ICONSET" -o "$ICNS"

echo "==> Assembling app bundle"
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources" "$APP/Contents/Java"

cp target/CrossCorrelationExplorer-1.0.jar "$APP/Contents/Java/CrossCorrelationExplorer.jar"
cp -R "$JAVA_HOME" "$APP/Contents/Java/jdk"
cp "$ICNS" "$APP/Contents/Resources/CrossCorrelationExplorer.icns"

cat > "$APP/Contents/MacOS/CrossCorrelationExplorer" << 'EOF'
#!/bin/bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../Java" && pwd)"
RES="$(cd "$(dirname "${BASH_SOURCE[0]}")/../Resources" && pwd)"
"$DIR/jdk/bin/java" \
  -Xdock:icon="$RES/CrossCorrelationExplorer.icns" \
  -Xdock:name="Cross Correlation Explorer" \
  -Xmx8G \
  -jar "$DIR/CrossCorrelationExplorer.jar"
EOF
chmod +x "$APP/Contents/MacOS/CrossCorrelationExplorer"

cat > "$APP/Contents/Info.plist" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key>
    <string>Cross Correlation Explorer</string>
    <key>CFBundleDisplayName</key>
    <string>Cross Correlation Explorer</string>
    <key>CFBundleIdentifier</key>
    <string>de.gfz-potsdam.crosscorrelationexplorer</string>
    <key>CFBundleVersion</key>
    <string>1.0</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0</string>
    <key>CFBundleExecutable</key>
    <string>CrossCorrelationExplorer</string>
    <key>CFBundleIconFile</key>
    <string>CrossCorrelationExplorer</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>NSHighResolutionCapable</key>
    <true/>
</dict>
</plist>
EOF

# force Finder to pick up the new icon instead of a cached/generic one
touch "$APP"
killall Finder 2>/dev/null || true

echo "==> Done: $APP"
open "$APP"
