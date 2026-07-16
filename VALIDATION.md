# Skin Powers 0.3.0 — Doğrulama Raporu

## Paket hazırlanırken yapılan kontroller

- Proje klasör yapısı, `fabric.mod.json`, Gradle dosyaları ve GitHub Actions iş akışı denetlendi.
- 17 Java kaynak dosyasının paket yolları ve temel sözdizimsel parantez dengesi kontrol edildi.
- Bütün JSON dosyaları gerçek bir JSON ayrıştırıcısıyla açıldı.
- Mod ikonu ile üç sınıf kartının PNG imzaları kontrol edildi.
- Minecraft 26.1 ile değişen eski API adlarının kaynakta kalmadığı kontrol edildi.
- Minecraft'tan bağımsız sınıf, XP, seviye, ustalık, cooldown, seçim döngüsü, sıfırlama ve meteor ayarı kodları `javac` ile derlenip küçük bir çalışma testinden geçirildi.
- ZIP arşivi oluşturulduktan sonra arşiv bütünlük testi yapılır.

## GitHub Actions sırasında yapılacak kesin derleme

GitHub iş akışı şunları kullanır:

```text
Java 25
Gradle 9.5.1
Fabric Loom 1.17-SNAPSHOT
Minecraft 26.1.2
Fabric Loader 0.19.3
Fabric API 0.154.2+26.1.2
```

İş akışı önce `python3 tools/verify_project.py`, ardından `gradle clean build --stacktrace --warning-mode all` komutunu çalıştırır. Başarılı olursa `build/libs/skinpowers-0.3.0.jar` dosyasını GitHub Artifact olarak verir.

## Dürüst sınırlama

Bu hazırlama ortamında yalnızca Java 21 vardı ve Gradle/Minecraft bağımlılıklarını internetten çözmek mümkün değildi. Bu nedenle **tam Minecraft/Fabric derlemesi ve oyun içi çalışma testi burada çalıştırılamadı**. Kaynak ve çekirdek mantık denetimleri geçti; son ve belirleyici kontrol GitHub Actions'taki Java 25 derlemesidir. Yazılım için “hiç hata çıkmayacağı” garantisi verilemez.
