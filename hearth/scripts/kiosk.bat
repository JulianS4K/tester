@echo off
REM Launch Hearth fullscreen (kiosk) on Windows and hook it to the TV.
REM Starts the server, then opens Microsoft Edge in kiosk mode.
REM Chrome alternative: replace the msedge line with
REM   start "" chrome --kiosk "http://localhost:%PORT%/"
setlocal
cd /d "%~dp0\.."
if "%PORT%"=="" set PORT=8080

REM Start the server minimized (skip if you run it as a service).
start "" /min cmd /c "node server\index.js"

REM Give it a moment to come up.
timeout /t 3 /nobreak >nul

start "" msedge --kiosk "http://localhost:%PORT%/" --edge-kiosk-type=fullscreen --no-first-run --disable-features=Translate
endlocal
