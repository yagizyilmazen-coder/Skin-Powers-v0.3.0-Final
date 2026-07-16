# Skin Powers 0.3.2 — Fabric 26.1.2

Skin renklerini analiz ederek Warden, Uçuş ve Ateş sınıfları öneren beş seviyeli Fabric modudur.

## Sürüm ve derleme

- Minecraft: **26.1.2**
- Fabric Loader: **0.19.3**
- Fabric API: **0.154.2+26.1.2**
- Java: **25**
- Mod: **0.3.2**

GitHub’a proje içeriğini yükleyin. Actions içindeki **Fabric JAR Derle** işlemi başarılı olduğunda `skinpowers-0.3.2-jar` artifact’ini indirin ve içindeki `skinpowers-0.3.2.jar` dosyasını `mods` klasörüne koyun.

## Kontroller

- **O:** Güç ağacını açar.
- **R:** Seçili aktif gücü kullanır.
- **Y:** Uçuş sınıfında Yavaş Düşüşü açar/kapatır.
- **Sol/Sağ:** Seçili gücü değiştirir.
- **Çift boşluk:** Süreli Elytra açıkken Roketsiz Kalkış.

## 0.3.2 değişiklikleri

- Uçuş Seviye 2 artık sınırsız uçuş vermez. R ile **20–35 saniyelik Elytra** göğüs yuvasına takılır ve süre bitince silinir. Göğüs yuvası doluyken çalışmaz.
- Eski 0.3.1 sürümünden kalmış sınırsız `mayfly` yetkisi otomatik temizlenir.
- Warden saldırılarının hasarı, alanı ve olumlu etkilerinin süresi yükseltildi.
- Warden Seviye 4 Karanlık/Gece Görüşü yerine **Sculk Avı** oldu: çevredeki düşmanları parlatır, yavaşlatır, zayıflatır ve hareket edenlere hasar verir.
- Ustalık eşikleri **5 / 15 / 30** kullanımdır: Acemi, Deneyimli, Uzman, Usta.
- Aktif, pasif ve otomatik tetiklenen güçlerin gerçek kullanımları ustalığa yazılır; kademe artınca oyun içi mesaj gösterilir.
- O menüsü yeni başlık, güç kartları, ustalık adları, seçili güç ayrıntı paneli, cooldown ve süre göstergeleriyle yenilendi.
