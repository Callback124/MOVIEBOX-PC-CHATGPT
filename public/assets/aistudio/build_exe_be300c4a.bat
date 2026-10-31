@echo off
setlocal
cd /d "%~dp0"
echo === PythonFlix EXE builder ===
echo Folder: %cd%
echo.

set PY=
where py >nul 2>nul && set PY=py
if "%PY%"=="" where python >nul 2>nul && set PY=python
if "%PY%"=="" (
    echo [ERROR] Python not found.
    echo Install it with:  winget install -e --id Python.Python.3.12
    echo then open a NEW Command Prompt and run this again.
    pause
    exit /b 1
)
echo Using: %PY%
%PY% --version

echo.
echo [1/4] Checking VLC (needed by the built-in player)...
if exist "%ProgramFiles%\VideoLAN\VLC\libvlc.dll" (
    echo VLC found.
) else (
    echo VLC not found. Installing with winget...
    winget install -e --id VideoLAN.VLC --accept-source-agreements --accept-package-agreements
)

echo.
echo [2/4] Installing libraries...
%PY% -m pip install --upgrade pip
%PY% -m pip install -r requirements.txt
if errorlevel 1 (
    echo [ERROR] pip install failed. Check your internet and try again.
    pause
    exit /b 1
)

echo.
echo [3/4] Building EXE (takes 1-3 minutes)...
%PY% -m PyInstaller --noconfirm --onefile --windowed --name PythonFlix pythonflix.py
if errorlevel 1 (
    echo [ERROR] Build failed. Copy the error text above and send it.
    pause
    exit /b 1
)

echo.
if exist "dist\PythonFlix.exe" (
    echo [4/4] SUCCESS!
    echo Your app: %cd%\dist\PythonFlix.exe
    explorer "%cd%\dist"
) else (
    echo [ERROR] Build finished but dist\PythonFlix.exe is missing.
    echo Windows Defender may have deleted it. Open Windows Security ^> Protection history and restore it.
)
pause
