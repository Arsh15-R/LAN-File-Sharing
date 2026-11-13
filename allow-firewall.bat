@echo off
echo ========================================================
echo Adding Windows Defender Firewall Rule for Port 8888...
echo ========================================================
powershell -Command "Start-Process powershell -ArgumentList '-NoProfile -Command New-NetFirewallRule -DisplayName \"LAN FileShare Port 8888\" -Direction Inbound -LocalPort 8888 -Protocol TCP -Action Allow' -Verb RunAs"
echo.
echo If prompted by Windows UAC, click YES to allow incoming connections on port 8888.
pause
