#!/bin/bash
# LAN File Sharing Compilation Script for Linux & macOS
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "========================================================"
echo "  Compiling LAN File Sharing Application...             "
echo "========================================================"

mkdir -p bin

javac -encoding UTF-8 -cp "lib/mysql-connector-j-8.3.0.jar:resources" -d bin \
  src/com/fileshare/beans/*.java \
  src/com/fileshare/utils/*.java \
  src/com/fileshare/network/*.java \
  src/com/fileshare/database/*.java \
  src/com/fileshare/dao/*.java \
  src/com/fileshare/server/*.java \
  src/com/fileshare/client/*.java

if [ $? -eq 0 ]; then
    echo ""
    echo "Compilation successful! Class files generated in 'bin' folder."
else
    echo ""
    echo "Compilation failed with errors."
    exit 1
fi
