@echo off
chcp 65001 >nul
echo Skin Powers v1.0.10 eski bot ve duello dosyalari siliniyor...
del /f /q "src\main\java\com\yagiz\skinpowers\BattlePanel.java" 2>nul
del /f /q "src\main\java\com\yagiz\skinpowers\DuelSystem.java" 2>nul
del /f /q "src\main\java\com\yagiz\skinpowers\PvpBotSystem.java" 2>nul
echo.
echo Tamamlandi. Bu pencereyi kapatabilirsiniz.
pause
