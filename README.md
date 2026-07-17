# Skin Powers 4.1.0

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

## Warden 6. güç — Şarj Et Beni Antik Şehir

Bu güç 70 XP gerektirir.

### Normal kullanım

- Dört sculk kolu oyuncunun arkasından çıkar ve ışınlarını 1–1,5 blok önde birleştirir.
- Birleşen kalın ışın, karşıda hedef bulunmasa da mutlaka ileri ateşlenir.
- İlk uygun oyuncuya veya canlı moba çarparsa Antik Şehir enerjisini aktarır.
- Oyuncular 20 saniye boyunca bir adet güçlendirilmiş aktif güç kullanabilir.
- Moblar 20 saniye boyunca mor/camgöbeği Antik Mutasyon, güç, hız ve direnç kazanır.

### Kendi kendine kullanım

6. güç seçiliyken çömelip `R` tuşuna bas:

1. Dört sculk kolu animasyonla çıkar.
2. Kollar göğsün önünde atan mor/camgöbeği Antik Kalp oluşturur.
3. Kalp göğse itilip içeri girdikten sonra üç kalp feda edilir.
4. Yalnızca kalp tamamen yerleştiği anda 20 saniyelik şarj sayacı ve cooldown serbestliği başlar.
5. 20 saniye sonunda kalpler yarım kalplik adımlarla yavaşça geri dolar.

Kendi kendine şarj başlatmak için üç kalpten fazla can gerekir.

## Şarj zamanlaması

- Şarj geldiği anda uygun aktif güçlerin mevcut bekleme süreleri geçici olarak kapanır.
- Yalnızca bir güçlendirilmiş aktif güç kullanılabilir.
- Güç hakkı kullanıldığında diğer cooldownlar geri gelir; fakat 20 saniyelik aşırı yük sayacı devam eder.
- Yavaşlık V, Bulantı ve Madencilik Yorgunluğu güç kullanıldığı anda değil, ilk şarjın başlamasından 20 saniye sonra uygulanır.
- Güç hiç kullanılmasa da 20 saniye sonunda aynı çöküş başlar.
- Warden'ın 6. gücü kendi şarjıyla güçlendirilemez ve cooldownu temizlenmez.
- Şarjlı saldırılar mor/camgöbeği sculk görünümü kazanır.
- Şarjlı Meteor Yağmuru 10 yerine 20 daha büyük morumsu meteor oluşturur.

## Tek oyunculu test komutları

Hileler açıkken veya yönetici yetkisiyle:

```text
/skinpowers charge give @s 20
/skinpowers charge clear @s
```

Türkçe karşılıkları:

```text
/skingucu sarj ver @s 20
/skingucu sarj temizle @s
```

Süre 20 saniyeden büyük yazılsa bile 20 saniyeyle sınırlandırılır. Komutla verilen şarj, kendi kendine kullanımın üç kalplik bedelini uygulamaz; test amacı taşır.

## Gereksinimler

- Minecraft Java Edition 26.1.2
- Fabric Loader 0.19.3 veya üzeri
- Fabric API 0.154.2+26.1.2
- Java 25
- Mod Menu 18.0.0 isteğe bağlıdır; yalnızca ayar düğmesini gösterir.

## GitHub Actions ile JAR oluşturma

1. Bu klasörün içeriğini GitHub deposunun ana dizinine kopyalayın.
2. Eski sürümden kalan `CHANGELOG_0.3.x.md` gibi dosyaları silin; `.git` klasörünü silmeyin.
3. `Actions` sekmesindeki **Fabric JAR Derle** iş akışını çalıştırın.
4. Yeşil tikten sonra `skinpowers-4.1.0-jar` artifact'ini indirin.
5. ZIP içindeki `skinpowers-4.1.0.jar` dosyasını Minecraft `mods` klasörüne koyun.
6. Eski Skin Powers JAR dosyalarını `mods` klasöründen kaldırın.

## Yapımcı

**Made by Yankalan**


## 4.1 Güç kombinasyonları

`K` ile Kombo Modu açılır/kapatılır. Mod açıkken doğru iki güç kısa süre içinde kullanılırsa özel saldırı oluşur:

- Warden: Yer Sarsıntısı → Sonik Patlama = Sonik Fay
- Uçuş: Süreli Elytra → Gökyüzü Hâkimiyeti = Gök Dalışı
- Ateş: Cehennem Küresi → Meteor Yağmuru = Cehennem Felaketi
- Doğa: Sarmaşık Kapanı → Dikenli Tohum = Diken Ormanı

Antik Şehir Şarjı varsa hazırlık gücü hakkı tüketmez; birleşik saldırı mor-camgöbeği güçlendirilmiş biçimde hakkı tüketir.
