@echo off
echo ========================================================
echo Compiling LAN File Sharing Application...
echo ========================================================

if not exist bin mkdir bin

javac -encoding UTF-8 -cp "lib\mysql-connector-j-8.3.0.jar;resources" -d bin src\com\fileshare\beans\*.java src\com\fileshare\utils\*.java src\com\fileshare\network\*.java src\com\fileshare\database\*.java src\com\fileshare\dao\*.java src\com\fileshare\server\*.java src\com\fileshare\client\*.java

if %ERRORLEVEL% EQU 0 (
    echo.
    echo Compilation successful! Class files generated in 'bin' folder.
) else (
    echo.
    echo Compilation failed with errors.
)
if "%1"=="" pause
