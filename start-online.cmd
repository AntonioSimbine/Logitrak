@echo off
rem Arranca a API + tunel publico e mostra o link do site.
rem Usa ExecutionPolicy Bypass para funcionar mesmo com scripts bloqueados.
powershell -NoProfile -ExecutionPolicy Bypass -Command "& '%~dp0start-online.ps1'"
echo.
pause
