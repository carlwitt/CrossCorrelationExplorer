# Building CrossCorrelationExplorer on macOS

This project targets **Java 11+ with JavaFX**. Since JDK 11, JavaFX is no
longer bundled with the JDK, so the JavaFX modules are pulled in as regular
Maven dependencies (see `org.openjfx:javafx-*` in `pom.xml`) instead of
requiring a special "FX" JDK build. The steps below get you from a fresh
checkout to a working, double-clickable macOS app.

## 1. Install a JDK

Any JDK 11+ works (tested with JDK 17). If you don't have one:

```bash
brew install openjdk@17
```

The JavaFX Maven dependencies in `pom.xml` use the `mac` classifier, which
matches an x86_64 JVM (including Apple Silicon Macs running under
Rosetta). If you run a native arm64 JDK, change the classifier of the
`org.openjfx:javafx-*` dependencies to `mac-aarch64`.

## 2. Install Maven

```bash
brew install maven
mvn -version   # confirm it reports a Java 11+ runtime
```

## 3. Open the project in an IDE (optional but recommended)

Source files live directly under `src/` (not the Maven-standard
`src/main/java/`), and IntelliJ won't index them until you tell it to.

1. Open the project via `pom.xml` (File → Open).
2. **File → Project Structure → SDKs**: add the JDK 11+ you installed.
3. Set this as the **Project SDK** (Project Structure → Project) and
   as the **Maven runner JRE** (Settings → Build Tools → Maven →
   Runner).
4. Right-click the `src` folder → **Mark Directory as → Sources Root**.
5. Reload the Maven project (Maven tool window → Reload All Maven
   Projects).
6. Run `Global.Main` directly from the IDE to test.

## 4. Build a runnable jar from the command line

```bash
mvn clean package
```

This produces a self-contained fat jar at
`target/CrossCorrelationExplorer-1.0.jar`. Test it directly:

```bash
java -jar target/CrossCorrelationExplorer-1.0.jar
```

The jar's main class is `Global.Launcher`, a thin wrapper around
`Global.Main` (which extends `javafx.application.Application`). This
indirection avoids `java`'s module-path check for JavaFX — if the
manifest's main class extends `Application` directly, `java -jar` fails
with "JavaFX runtime components are missing" even though JavaFX is on
the classpath inside the fat jar.

## 5. Package it as a macOS `.app`

`javapackager` was removed after Java 8, so we assemble the app bundle
ourselves with [`package-macos.sh`](package-macos.sh) instead of relying
on it — it's simpler and more robust, and works the same way regardless
of JDK vendor.

```bash
./package-macos.sh
```

This script:

1. Runs `mvn clean package` to produce the fat jar.
2. Compiles `img/icon.iconset` into `img/CrossCorrelationExplorer.icns`
   (see "Regenerating the icon" below if you want to change the
   artwork first).
3. Assembles `dist/Cross Correlation Explorer.app`, embedding the
   current `$JAVA_HOME` as the app's runtime (unlike Java 8, JDK 9+
   doesn't ship a separate `jre/` subfolder — the full `$JAVA_HOME` *is*
   the runtime) and setting the icon both for Finder/Dock and for the
   Dock while the app is actually running (JavaFX doesn't set that on
   its own; it needs the `-Xdock:icon` flag, which the script's launcher
   shell script passes).
4. Opens the resulting app.

Embedding the JDK makes the bundle self-contained (~150–300 MB) — no
separate Java install needed on the machine that runs it. If bundle size
matters, edit the script to replace the `cp -R "$JAVA_HOME" ...` step
with a `jlink` custom runtime image containing only the modules you
need.

## 6. (Optional) Regenerating the icon

Only needed if you want to change the artwork — `package-macos.sh`
compiles `img/icon.iconset` into `img/CrossCorrelationExplorer.icns` on
every run, so just replace the PNGs in the iconset.

1. Produce a 1024×1024 PNG (transparent background, macOS
   rounded-square style), e.g. `img/icon_1024.png`.
2. Regenerate the iconset's PNGs at each required size:

```bash
cd img
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
```

3. Run `./package-macos.sh` again to recompile the `.icns` and rebuild
   the app.

## Known gotchas

- **`cannot find symbol ... FontLoader`/`FontMetrics`**: some
  visualization code used the internal, unsupported
  `com.sun.javafx.tk.FontLoader` API to measure text. Its method
  signatures changed between JavaFX versions; `renderedTextSize()` in
  `NumberAxis.java` and `CorrelogramLegend.java` now measures text with
  the public `javafx.scene.text.Text` API instead.
- **`Zum Ausführen dieser Anwendung benötigte JavaFX-Runtime-Komponenten
  fehlen` / "JavaFX runtime components are missing"**: happens if the
  jar's main class extends `Application` directly — `java -jar` checks
  for JavaFX on the module path before running any of your code. Fixed
  by using `Global.Launcher` (not extending `Application`) as the
  manifest's main class.
- **`NoClassDefFoundError: com/sun/javafx/Utils`**: internal JavaFX
  APIs used by old ControlsFX versions moved between Java 8 update
  releases — use a recent ControlsFX 8.x release, not an old one.
- Gatekeeper will refuse to open the `.app` on a different Mac
  unless it's signed and notarized with an Apple Developer ID — fine
  for local use, but relevant if you plan to distribute the app.