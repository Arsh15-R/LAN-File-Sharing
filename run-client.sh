#!/bin/bash
# LAN File Sharing Client Launcher for Linux & macOS
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "========================================================"
echo "  Launching LAN File Sharing Client (Linux / macOS)     "
echo "========================================================"

java -cp "bin:lib/mysql-connector-j-8.3.0.jar:resources" com.fileshare.client.LoginFrame "$@"
