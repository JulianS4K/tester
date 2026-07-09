@echo off
REM Start the Hearth server on Windows (mini PC / laptop hooked to a TV).
REM Requires Node.js 18+ (https://nodejs.org). Run once: npm install
cd /d "%~dp0\.."
node server\index.js
