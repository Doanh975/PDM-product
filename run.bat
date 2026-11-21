@echo off
REM Build (if needed) and run the app. Usage: run.bat [--rebuild]
cd /d "%~dp0"
if "%1"=="--rebuild" rmdir /s /q bin
if not exist bin (
  mkdir bin
  dir /s /b src\*.java > sources.txt
  javac -encoding UTF-8 -d bin -cp "lib/*" @sources.txt || (del sources.txt & pause & exit /b 1)
  del sources.txt
  echo Build OK
)
java -cp "bin;lib/*" app.Main
