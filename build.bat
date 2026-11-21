@echo off
REM Builds dist\: one runnable app.jar (all libraries inside) + config + SQL scripts + start files.
cd /d "%~dp0"
if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
mkdir build\classes\sql dist\sql
dir /s /b src\*.java > build\sources.txt
echo 1/4 Compiling (target Java 17)...
javac --release 17 -encoding UTF-8 -d build\classes -cp "lib/*" @build\sources.txt || (pause & exit /b 1)
echo 2/4 Adding libraries and SQL scripts...
copy sql\01_schema_and_data.sql build\classes\sql\ >nul
copy sql\01b_triggers.sql build\classes\sql\ >nul
pushd build\classes
for %%j in (..\..\lib\*.jar) do jar xf "%%j"
del /q module-info.class META-INF\MANIFEST.MF META-INF\*.SF META-INF\*.RSA META-INF\*.DSA 2>nul
popd
echo 3/4 Creating dist\app.jar...
jar --create --file dist\app.jar --main-class app.Main -C build\classes .
echo 4/4 Copying config, scripts and start files...
copy config.properties dist\ >nul
copy sql\*.sql dist\sql\ >nul
copy HOW-TO-RUN.txt dist\ >nul
(echo @echo off& echo cd /d "%%~dp0"& echo start "" javaw -jar app.jar) > "dist\Start App (Windows).bat"
rmdir /s /q build
echo Done: dist\app.jar
