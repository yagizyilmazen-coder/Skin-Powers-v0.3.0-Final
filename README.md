# Skin Powers 0.3.0 — Fabric 26.1.2

Skin Powers, oyuncunun Minecraft skinindeki renkleri analiz ederek **Warden**, **Uçuş** ve **Ateş** sınıflarını öneren; seçimi UUID ile saklayan ve beş seviyeli güç sistemi sunan bir Fabric modudur.

## Hedef sürüm

- Minecraft Java Edition: **26.1.2**
- Fabric Loader: **0.19.3**
- Fabric API: **0.154.2+26.1.2**
- Java: **25**
- Mod sürümü: **0.3.0**

## GitHub'da JAR üretme

1. ZIP'i açın.
2. ZIP'in içindeki tüm dosyaları GitHub deposunun köküne yükleyin. `build.gradle` doğrudan ana sayfada görünmelidir.
3. GitHub'da **Actions → Fabric JAR Derle → Run workflow** yolunu açın.
4. İşlem yeşil tik olduğunda **Artifacts → skinpowers-0.3.0-jar** dosyasını indirin.
5. Artifact ZIP'inin içindeki `skinpowers-0.3.0.jar` dosyasını Minecraft `mods` klasörüne koyun.
6. Fabric API'nin 26.1.2 sürümünü de `mods` klasörüne koyun.

> Proje, GitHub Actions içinde Java 25 ve Gradle 9.5.1'i kendisi kurar. Bilgisayarınızdaki eski Java 8 bu derlemeyi etkilemez.

## Kontroller

- **R:** Seçili aktif gücü kullanır.
- **Y:** Sınıfın pasif/görüş özelliğini açar veya kapatır.
- **Sol / Sağ ok:** Güçler arasında geçer.
- **O:** Güç ve seviye ekranını açar.
- **Uçuş sınıfı Seviye 3+:** Boşluk tuşuna hızlıca iki kez basınca roketsiz kalkış.

## XP maliyetleri

| Açılan sınıf seviyesi | Gerekli Minecraft XP seviyesi |
|---:|---:|
| 1 | 5 |
| 2 | 15 |
| 3 | 30 |
| 4 | 40 |
| 5 | 50 |

Seviye açılırken XP seviyesi oyuncudan düşülür. Bir sonraki seviye, `O` ekranındaki düğmeyle açılır.

## Yönetici komutları

```text
/skingucu reset OyuncuAdı
/skingucu meteor blokhasari true
/skingucu meteor blokhasari false
```

## Uygulanan sistemler

- Skin PNG'sini Mojang profilindeki texture adresinden asenkron indirip saydam olmayan pikselleri analiz etme
- Ana ve ikinci skin katmanlarını birlikte değerlendirme
- Bir pikselin renk uzaklığına göre birden fazla sınıfa farklı puan vermesi
- İlk girişte otomatik ve atlanabilir tarama/seçim ekranı
- Warden=antik şehir, Uçuş=bulutlar, Ateş=lav mağarası temalı üç kart
- Yumuşak kart açılma, parıltı, tarama çizgisi, seçme sarsıntısı ve geçiş animasyonları
- UUID tabanlı kalıcı kayıt
- Beş seviye, XP ücretleri, kullanım ustalığı ve cooldown gelişimi
- HUD, güç seçimi, cooldown ve pasif durum göstergesi
- Warden: dayanıklılık, yer sarsıntısı, duvar içinden sonik saldırı, titreşim görüşü, uyanış
- Uçuş: yavaş düşüş, bağlı uçuş yeteneği, çift boşluk kalkış, hava patlaması/mermi çevirme, hız çarpması
- Ateş: bağışıklık, alevli yakın dövüş, ateş çemberi, ateş görüşü, animasyonlu meteor yağmuru
- Meteor blok hasarını yönetici tarafından açma/kapatma
- Takım arkadaşı ve evcil hayvan filtreleri gereken güçlerde

## Önemli görsel kapsam notu

Oynanış, kayıt, XP, cooldown ve üç sınıfın güç sistemi kaynak kodda bulunur. Gerçek 3B dönen oyuncu renderı ile özel giyilebilir kanat modeli, bu sürümde daha güvenli istemci görsellerine uyarlanmıştır. Ayrıntılı ve açık durum tablosu için `FEATURE_STATUS.md`; yapılan kontroller için `VALIDATION.md` dosyasına bakın.

## Kayıt dosyaları

Her dünya/sunucu kayıt klasöründe:

```text
skinpowers/players.json
skinpowers/config.json
```

oluşturulur. Oyuncunun sınıfı, seviye ve ustalığı kullanıcı adına değil UUID'ye bağlıdır.

## Proje denetimi

GitHub derlemesinden önce otomatik çalışan denetim:

```text
python tools/verify_project.py
```

Bu denetim JSON dosyalarını, zorunlu proje dosyalarını, Java paket yollarını ve kaynaklardaki temel parantez dengesini kontrol eder.

## Teknik not

Minecraft 26.1.x ile resmi Minecraft adları kullanılan yeni Fabric kaynak yapısına geçildiği için proje `src/main` ve `src/client` olarak ayrılmıştır. Böylece sunucu, yalnızca istemcide bulunan ekran/render sınıflarını yüklemeye çalışmaz.
