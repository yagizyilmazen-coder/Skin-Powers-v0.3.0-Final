@echo off
chcp 65001 >nul
echo Skin Powers v1.3.0 FIX1 temizligi uygulanıyor...

del /q "src\main\resources\assets\skinpowers\textures\gui\cards\time.png" 2>nul
del /q "src\main\resources\assets\skinpowers\textures\gui\cards\nature.png" 2>nul
del /q "src\main\resources\data\skinpowers\enchantment\kok_bagi.json" 2>nul
del /q "src\main\resources\data\skinpowers\enchantment\can_filizi.json" 2>nul
del /q "src\main\resources\data\skinpowers\enchantment\orman_sicrayisi.json" 2>nul
del /q "src\main\resources\data\skinpowers\enchantment\dikenli_savunma.json" 2>nul

echo.
echo Temizlik tamamlandi.
echo GitHub Desktop'a donup degisiklikleri kontrol edin.
pause
