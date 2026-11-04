@echo off
rem Arranca o backend Logitrack (le backend\.env).
rem Usa ExecutionPolicy Bypass para funcionar mesmo com scripts bloqueados.
powershell -NoProfile -ExecutionPolicy Bypass -Command "& '%~dp0run-local.ps1'"
