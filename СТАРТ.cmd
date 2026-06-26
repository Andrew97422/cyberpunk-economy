@echo off
chcp 65001 >nul
echo ============================================
echo   Запуск всего стека лагеря (Primary)
echo ============================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy\balancer\start-all.ps1" %*
echo.
pause
