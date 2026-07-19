# Skin Powers 1.2.1 FIX1

- Kum Zırhı ve diğer oyuncuya bağlı görseller artık oyuncunun ölüm anında doğrudan temizlenir.
- Yalnızca sunucu tick'indeki `isAlive()` kontrolüne güvenilmez; Fabric `AFTER_DEATH` olayı kullanılır.
- Hızlı yeniden doğmada aynı UUID'ye sahip yeni oyuncunun eski kumları devralması engellenir.
- Önceki sürümlerden dünyada kalmış, yerçekimsiz kum/kumtaşı görselleri ölüm konumunun 48 blok çevresinde temizlenir.
- Manyetik ve Kum güç dengeleri değiştirilmedi.
- Doğa sınıfına dokunulmadı.
