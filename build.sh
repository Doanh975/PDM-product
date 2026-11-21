#!/usr/bin/env bash
# Builds dist/: one runnable app.jar (all libraries inside) + config + SQL scripts + start files.
# Needs a JDK 17+ (javac + jar). The result runs on any machine with Java 17+.
set -e
cd "$(dirname "$0")"
rm -rf build dist
mkdir -p build/classes dist

echo "1/4 Compiling (target Java 17)..."
javac --release 17 -encoding UTF-8 -d build/classes -cp "lib/*" $(find src -name '*.java')

echo "2/4 Adding libraries and SQL scripts..."
mkdir -p build/classes/sql && cp sql/01_schema_and_data.sql sql/01b_triggers.sql build/classes/sql/
( cd build/classes && for j in ../../lib/*.jar; do jar xf "$j"; done
  rm -f module-info.class META-INF/MANIFEST.MF META-INF/*.SF META-INF/*.RSA META-INF/*.DSA )

echo "3/4 Creating dist/app.jar..."
jar --create --file dist/app.jar --main-class app.Main -C build/classes .

echo "4/4 Copying config, scripts and start files..."
cp config.properties dist/
mkdir -p dist/sql && cp sql/*.sql dist/sql/
cat > "dist/Start App (Mac).command" <<'EOS'
#!/bin/bash
cd "$(dirname "$0")"
if ! command -v java >/dev/null 2>&1; then
  osascript -e 'display alert "Java is not installed" message "Install Java 17 or newer (for example from adoptium.net), then open this file again."'
  exit 1
fi
java -jar app.jar || { echo; echo "The app needs Java 17 or newer. Your version:"; java -version; read -p "Press Enter to close"; }
EOS
cat > "dist/Start App (Linux).sh" <<'EOS'
#!/bin/bash
cd "$(dirname "$0")"
java -jar app.jar
EOS
printf '@echo off\r\ncd /d "%%~dp0"\r\nwhere java >nul 2>nul || (echo Java is not installed. Install Java 17 or newer, e.g. from adoptium.net & pause & exit /b 1)\r\nstart "" javaw -jar app.jar\r\n' > "dist/Start App (Windows).bat"
chmod +x "dist/Start App (Mac).command" "dist/Start App (Linux).sh"
cp HOW-TO-RUN.txt dist/ 2>/dev/null || true
rm -rf build
echo "Done: dist/ ($(du -h dist/app.jar | cut -f1) app.jar)"
