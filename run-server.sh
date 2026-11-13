#!/bin/bash
# LAN File Sharing Server Launcher for Linux & macOS
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "========================================================"
echo "  Starting LAN File Sharing Server (Linux / macOS)      "
echo "========================================================"

java -cp "bin:lib/mysql-connector-j-8.3.0.jar:resources" com.fileshare.server.ServerFrame --autostart "$@"
