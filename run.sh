#!/usr/bin/env bash
# Build (if needed) and run the app. Usage: ./run.sh [--rebuild]
set -e
cd "$(dirname "$0")"
if [ "$1" = "--rebuild" ] || [ ! -d bin ]; then
  rm -rf bin && mkdir bin
  javac -encoding UTF-8 -d bin -cp "lib/*" $(find src -name '*.java')
  echo "Build OK"
fi
java -cp "bin:lib/*" app.Main
