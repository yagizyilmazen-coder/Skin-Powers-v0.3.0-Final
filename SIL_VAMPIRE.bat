@echo off
chcp 65001 >nul
echo VampirePowerSystem.java siliniyor...
del /f /q "src\main\java\com\yagiz\skinpowers\VampirePowerSystem.java" 2>nul
if exist "src\main\java\com\yagiz\skinpowers\VampirePowerSystem.java" (
  echo HATA: Dosya silinemedi. GitHub Desktop'ta Show in Explorer ile elle sil.
) else (
  echo Tamam: VampirePowerSystem.java silindi.
)
echo.
echo GitHub Desktop'ta degisiklikleri kontrol et, Commit ve Push yap.
pause
