# Building CrossCorrelationExplorer on macOS

This project targets **Java 8 with JavaFX**, which is no longer bundled
with modern JDKs. The steps below get you from a fresh checkout to a
working, double-clickable macOS app.

## 1. Install a Java 8 JDK that includes JavaFX ("FX" build)

Regular JDK 8 builds (and anything JDK 11+) do **not** include JavaFX.
You need a build explicitly labeled "FX".

Using [SDKMAN](https://sdkman.io/) (recommended):

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

sdk list java | grep -i fx      # find an "8.x.x.fx-zulu" entry
sdk install java 8.0.502.fx-zulu   # use the version you found above
```

## 2. Install Maven

```bash
brew install maven
```

Make sure Maven uses the Java 8 FX JDK, not your system default:

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/8.0.502.fx-zulu"
mvn -version   # confirm it reports Java 1.8.x
```

## 3. Open the project in an IDE (optional but recommended)

Source files live directly under `src/` (not the Maven-standard
`src/main/java/`), and IntelliJ won't index them until you tell it to.

1. Open the project via `pom.xml` (File → Open).
2. **File → Project Structure → SDKs**: add the Java 8 FX JDK you
   installed (point it at
   `~/.sdkman/candidates/java/8.0.502.fx-zulu`).
3. Set this as the **Project SDK** (Project Structure → Project) and
   as the **Maven runner JRE** (Settings → Build Tools → Maven →
   Runner).
4. Right-click the `src` folder → **Mark Directory as → Sources Root**.
5. Reload the Maven project (Maven tool window → Reload All Maven
   Projects).
6. Run `Global.Main` directly from the IDE to test.

## 4. Build a runnable jar from the command line

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/8.0.502.fx-zulu"
mvn clean package
```

This produces a self-contained fat jar at
`target/CrossCorrelationExplorer-1.0.jar`. Test it directly:

```bash
"$JAVA_HOME/bin/java" -jar target/CrossCorrelationExplorer-1.0.jar
```

## 5. Package it as a macOS `.app`

`javapackager` (bundled with the JDK) is unreliable for detecting the
JRE layout on Zulu builds, so we assemble the app bundle by hand
instead — it's simpler and more robust.

The repo already ships a pre-built icon at `dist/CrossCorrelationExplorer.icns`
(see "Regenerating the icon" below if you ever need to recreate it),
so the script below picks it up automatically — both as the Finder/Dock
icon and as the Dock icon while the app is actually running (JavaFX
doesn't set that on its own; it needs the `-Xdock:icon` flag).

```bash
APP="dist/Cross Correlation Explorer.app"
ICON_SRC="img/CrossCorrelationExplorer.icns"

mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources" "$APP/Contents/Java"

cp target/CrossCorrelationExplorer-1.0.jar "$APP/Contents/Java/CrossCorrelationExplorer.jar"
cp -R "$JAVA_HOME/jre" "$APP/Contents/Java/jre"
cp "$ICON_SRC" "$APP/Contents/Resources/CrossCorrelationExplorer.icns"

cat > "$APP/Contents/MacOS/CrossCorrelationExplorer" << 'EOF'
#!/bin/bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../Java" && pwd)"
RES="$(cd "$(dirname "${BASH_SOURCE[0]}")/../Resources" && pwd)"
"$DIR/jre/bin/java" \
  -Xdock:icon="$RES/CrossCorrelationExplorer.icns" \
  -Xdock:name="Cross Correlation Explorer" \
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
killall Finder

open "$APP"
```

Embedding the JRE makes the bundle self-contained (~150–200 MB) — no
separate Java install needed on the machine that runs it.

## 6. (Optional) Regenerating the icon

Only needed if you want to change the artwork — the compiled `.icns`
is already checked into `dist/CrossCorrelationExplorer.icns` and
picked up automatically by step 5.

1. Produce a 1024×1024 PNG (transparent background, macOS
   rounded-square style).
2. Generate the iconset and compile it to `.icns`:

```bash
mkdir icon.iconset
sips -z 16 16     icon_1024.png --out icon.iconset/icon_16x16.png
sips -z 32 32     icon_1024.png --out icon.iconset/icon_16x16@2x.png
sips -z 32 32     icon_1024.png --out icon.iconset/icon_32x32.png
sips -z 64 64     icon_1024.png --out icon.iconset/icon_32x32@2x.png
sips -z 128 128   icon_1024.png --out icon.iconset/icon_128x128.png
sips -z 256 256   icon_1024.png --out icon.iconset/icon_128x128@2x.png
sips -z 256 256   icon_1024.png --out icon.iconset/icon_256x256.png
sips -z 512 512   icon_1024.png --out icon.iconset/icon_256x256@2x.png
sips -z 512 512   icon_1024.png --out icon.iconset/icon_512x512.png
cp icon_1024.png icon.iconset/icon_512x512@2x.png
iconutil -c icns icon.iconset -o CrossCorrelationExplorer.icns
```

3. Replace the checked-in file:

```bash
cp CrossCorrelationExplorer.icns dist/CrossCorrelationExplorer.icns
```

## Known gotchas

- **`javax`/`com.sun.istack.internal` errors**: any code referencing
  Oracle-internal packages won't compile on OpenJDK-based Java 8
  builds (Zulu, Temurin, etc.) — those packages simply don't exist
  there.
- **`JavaScript script engine is disabled`**: JavaFX disabled
  scripting in FXML by default starting with 8u371
  (CVE-2023-22043). Any leftover `<?language javascript?>`
  processing instructions in `.fxml` files will fail to load.
- **`NoClassDefFoundError: com/sun/javafx/Utils`**: internal JavaFX
  APIs used by old ControlsFX versions moved between Java 8 update
  releases — use a recent ControlsFX 8.x release, not an old one.
- Gatekeeper will refuse to open the `.app` on a different Mac
  unless it's signed and notarized with an Apple Developer ID — fine
  for local use, but relevant if you plan to distribute the app.