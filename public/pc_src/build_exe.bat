@echo off
title Building MovieBox Standalone EXE
echo ========================================================
echo       MovieBox Standalone Executable Builder
echo ========================================================
echo.

pip install requests pillow pyinstaller

pyinstaller --noconfirm --onedir --windowed ^
    --name "MovieBox" ^
    --add-data "player_page.py;." ^
    --add-data "moviebox_player.py;." ^
    pythonflix.py

echo.
echo ========================================================
echo  BUILD COMPLETE!
echo  Executable location: dist\MovieBox\MovieBox.exe
echo ========================================================
pause
