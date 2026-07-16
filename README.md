# Skin Powers 0.3.4 — Fabric 26.1.2

Minecraft skinindeki gerçek piksel renklerini analiz ederek Warden, Uçuş, Ateş ve Su sınıflarını öneren beş seviyeli Fabric modudur. Skin alınamazsa mod puan veya öneri uydurmaz; oyuncu dört sınıftan birini kendisi seçer.

## Sürüm ve derleme

- Minecraft: **26.1.2**
- Fabric Loader: **0.19.3**
- Fabric API: **0.154.2+26.1.2**
- Java: **25**
- Mod: **0.3.4**

GitHub Actions başarılı olduğunda `skinpowers-0.3.4-jar` artifact’ini indirin ve içindeki `skinpowers-0.3.4.jar` dosyasını `mods` klasörüne koyun.

## Kontroller

- **O:** Güç ağacını açar; açıkken tekrar O ile kapatır.
- **ESC:** Güç ağacını kapatır.
- **R:** Seçili aktif gücü kullanır.
- **Y:** Uçuş sınıfında Yavaş Düşüşü açar/kapatır.
- **Sol/Sağ:** Seçili gücü değiştirir.
- **Çift boşluk:** Süreli Elytra açıkken Roketsiz Kalkış.

## Su sınıfı

1. **Suda Yaşam — 10 XP:** Su altında nefes, daha net görüş ve hızlı yüzme.
2. **Basınçlı Su Küresi — 20 XP:** Hasar verir, ateşi söndürür ve hedefleri kuvvetle savurur.
3. **Derin Girdap — 30 XP:** Düşmanları merkeze çeker, yavaşlatır ve aralıklı hasar verir.
4. **Okyanus Zırhı — 40 XP:** Hasarı azaltır, mermileri saptırır ve oyuncuyu söndürür.
5. **Büyük Tsunami — 50 XP:** Geniş su duvarı ileri gider; canlıları sürükler, hasar verir ve giderek küçülür.

## 0.3.4 değişiklikleri

- İlk seçim ekranı dört kart için yeniden ölçeklendirildi; dar ekranlarda yazı ve düğme çarpışmasını azaltan dinamik yerleşim eklendi.
- Warden kartında Warden, Uçuş kartında Elytra, Ateş kartında görünür ateş küresi ve Su kartında tsunami görseli bulunur.
- Skin analizine turkuaz, camgöbeği ve deniz yeşili odaklı Su puanlaması eklendi.
- Birkaç beyaz pikselin Uçuş sınıfına yüksek ve yapay puan vermemesi için puanlar toplam piksel sayısına göre hesaplanır.
- Su sınıfının beş gücü ve sınıfa özel O menüsü/HUD teması eklendi.
- Cehennem Küresi hareket ederken geçici magma çekirdeğiyle görünür hâle getirildi.
- Warden oynanış değerleri değiştirilmedi.
