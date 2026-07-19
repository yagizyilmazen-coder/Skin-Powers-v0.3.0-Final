@echo off
chcp 65001 >nul
echo Eski Doga dosyalari temizleniyor...

del /q "src\main\resources\data\skinpowers\enchantment\can_filizi.json" 2>nul
del /q "src\main\resources\data\skinpowers\enchantment\dikenli_savunma.json" 2>nul
del /q "src\main\resources\data\skinpowers\enchantment\kok_bagi.json" 2>nul
del /q "src\main\resources\data\skinpowers\enchantment\orman_sicrayisi.json" 2>nul
del /q "src\main\resources\assets\skinpowers\textures\gui\cards\nature.png" 2>nul

echo.
echo Eski Doga dosyalari silindi.
echo GitHub Desktop'a donup degisiklikleri kontrol edin.
pause
