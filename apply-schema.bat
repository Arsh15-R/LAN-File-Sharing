@echo off
echo ========================================================
echo Applying MySQL Schema to fileshare_db...
echo ========================================================

rem Ensure classes are compiled
if not exist "bin\com\fileshare\database\ApplySchema.class" (
    echo Compiling source files first...
    call compile.bat --silent
)

java -cp "bin;lib\mysql-connector-j-8.3.0.jar;resources" com.fileshare.database.ApplySchema %*

if "%1"=="" pause

