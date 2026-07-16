# Skin Powers 0.3.7

Minecraft Java Edition 26.1.2 için Fabric modudur. İlk sınıf seçiminde oyuncunun skini renklerine göre analiz edilir ve dört sınıf önerilir:

- Warden
- Uçuş
- Ateş
- Doğa

## Temel kontroller

- `R`: seçili aktif gücü kullan
- `Y`: uygun yardımcı özelliği kullan
- `Sol/Sağ`: açık güçler arasında geçiş yap
- `O`: güç menüsünü aç/kapat
- `ESC`: açık menüyü kapat

## 0.3.7 — Antik Şehir Şarjı

Warden sınıfına **70 XP** gerektiren altıncı güç eklendi:

**Şarj Et Beni Antik Şehir**

- Warden oyuncusunun arkasında dört sculk enerji kolu animasyonla açılır.
- Dört ışın, oyuncunun yaklaşık 1–1,5 blok önünde birleşerek tek ışın hâlinde hedef oyuncuya gider.
- Işın yalnızca başka bir oyuncuya şarj verir; kullanan oyuncu kendini bu güçle hedefleyemez.
- Hedef oyuncu en fazla 20 saniye boyunca Antik Şehir mutasyonu taşır.
- Şarj sırasında bütün uygun aktif güç cooldownları geçici olarak kapanır.
- Oyuncunun yalnızca **bir güçlendirilmiş aktif güç kullanma hakkı** vardır.
- Güç kullanılırsa veya 20 saniye kullanılmadan dolarsa 30 saniyelik Yavaşlık V, Bulantı ve Madencilik Yorgunluğu başlar.
- Warden'ın altıncı gücü kendi şarjıyla güçlendirilemez ve cooldownu temizlenmez.
- Şarjlı saldırılar mor/camgöbeği sculk görünümü kazanır.
- Şarjlı Meteor Yağmuru 10 yerine **20 daha büyük morumsu meteor** oluşturur.

Gerçek değerler güvenli sınırlarla artırılır: hasar yaklaşık 2,75 kat, etki alanı 1,45 kat, geri savurma 1,85 kat ve süre yaklaşık 1,65 kat artar. Böylece ekranda güçlü hissettirirken oyunun tamamen bozulması önlenir.

## Tek oyunculu test komutları

Hileler açıkken veya yönetici yetkisiyle:

```text
/skinpowers charge give @s 20
```

Süre 20 saniyeden büyük yazılsa bile otomatik olarak 20 saniyeyle sınırlandırılır.

```text
/skinpowers charge clear @s
```

Türkçe karşılıkları:

```text
/skingucu sarj ver @s 20
/skingucu sarj temizle @s
```

## Gereksinimler

- Minecraft Java Edition 26.1.2
- Fabric Loader 0.19.3 veya üzeri
- Fabric API 0.154.2+26.1.2
- Java 25
- Mod Menu 18.0.0 isteğe bağlıdır; yalnızca ayar düğmesini gösterir.

## GitHub Actions ile JAR oluşturma

1. Bu klasörün içeriğini GitHub deposunun ana dizinine kopyalayın.
2. `Actions` sekmesindeki **Fabric JAR Derle** iş akışını çalıştırın.
3. Yeşil tikten sonra `skinpowers-0.3.7-jar` artifact'ini indirin.
4. ZIP içindeki `skinpowers-0.3.7.jar` dosyasını Minecraft `mods` klasörüne koyun.
5. Eski Skin Powers JAR dosyalarını `mods` klasöründen kaldırın.

## Yapımcı

**Made by Yankalan**
