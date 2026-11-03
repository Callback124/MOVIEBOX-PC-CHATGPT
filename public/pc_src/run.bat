@echo off
title MovieBox Desktop - Fast Launcher
echo ========================================================
echo        MovieBox PC Desktop (Pure API Edition)
echo ========================================================
echo.

echo Checking requirements (requests, pillow)...
pip install requests pillow

echo.
echo Launching MovieBox...
python pythonflix.py
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Application exited.
    pause
)
