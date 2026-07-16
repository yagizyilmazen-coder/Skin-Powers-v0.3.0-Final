# Skin Powers 0.3.5 — Doğrulama Raporu

## Paket hazırlanırken yapılan kontroller

- Proje klasör yapısı, `fabric.mod.json`, Gradle dosyaları ve GitHub Actions iş akışı denetlendi.
- 17 Java kaynak dosyasının paket yolları, parantez dengesi ve Java ayrıştırma kontrolü yapıldı.
- Bütün JSON dosyaları gerçek bir JSON ayrıştırıcısıyla açıldı.
- Mod ikonu ile dört sınıf kartının PNG imzaları kontrol edildi.
- Minecraft 26.1 ile uyuşmayan, önceki derlemelerde hata üreten eski API adlarının kaynakta kalmadığı kontrol edildi.
- Minecraft'tan bağımsız sınıf, XP, seviye, ustalık, cooldown, seçim döngüsü, sıfırlama ve Su zamanlayıcısı kodları `javac` ile derlenip çalıştırıldı.
- Skin analizi ayrı bir Java smoke testinde denetlendi: turkuaz→Su, açık mavi/beyaz→Uçuş, turuncu→Ateş; yalnızca dört beyaz pikselin sahte öneri üretmediği doğrulandı.
- Dört kartlı ekran yerleşimi 320×240 ile 1280×720 arasındaki örnek GUI boyutlarında matematiksel olarak kontrol edildi; kartların ekran dışına taşmadığı doğrulandı.
- ZIP arşivi oluşturulduktan sonra arşiv bütünlüğü ve SHA-256 özeti kontrol edilir.

## GitHub Actions sırasında yapılacak kesin derleme

```text
Java 25
Gradle 9.5.1
Fabric Loom 1.17-SNAPSHOT
Minecraft 26.1.2
Fabric Loader 0.19.3
Fabric API 0.154.2+26.1.2
```

İş akışı önce `python3 tools/verify_project.py`, ardından `gradle clean build --stacktrace --warning-mode all` komutunu çalıştırır. Başarılı olursa `build/libs/skinpowers-0.3.5.jar` dosyasını artifact olarak verir.

## Sınırlama

Hazırlama ortamında Minecraft/Fabric bağımlılıklarıyla tam Java 25 Gradle derlemesi çalıştırılamadı. Kaynak, JSON, PNG, çekirdek davranış, skin puanlama ve ekran yerleşimi testleri geçti; kesin Minecraft uyumluluk sonucu GitHub Actions derlemesidir.
