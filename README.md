# Skin Powers 1.0.1

Minecraft Java Edition 26.1.2 için Fabric güç modudur. Oyuncunun gerçek skin piksellerini analiz eder, en güçlü ve ikinci en yakın sınıf önerisini gösterir; oyuncu yalnızca en yüksek puanlı ve ona en yakın ikinci sınıftan birini seçebilir.

## Sınıflar

- Warden
- Uçuş
- Ateş
- Doğa
- Zaman

## Kontroller

- `R`: seçili aktif gücü kullan
- `Y`: uygun yardımcı/pasif özelliği değiştir
- `Sol/Sağ`: açık güçler arasında geç
- `O`: güç menüsünü aç/kapat
- `K`: Kombo Modunu aç/kapat
- `ESC`: menüyü kapat

## Zaman sınıfı

1. **Zaman Sezgisi — 10 XP:** yakındaki mermileri yavaşlatır.
2. **Krono Mızrağı — 20 XP:** havada görünür altın-lacivert mızrak fırlatır.
3. **Geri Sarma — 30 XP:** oyuncuyu yaklaşık beş saniye önceki konum ve canına döndürür.
4. **Zaman Hapishanesi — 40 XP:** hedefi ekran efekti uygulamadan görünür saat halkaları içinde sabitler.
5. **Zamanın Sonu — 50 XP:** geniş alandaki hedefleri durdurur ve süre sonunda büyük zaman patlaması oluşturur.

## Skin analizi

- Skin analizi ve skin indirme ayrı iş parçacıklarında çalışır; ilk ekran ağ isteği yüzünden kilitlenmez.
- Profil dokusu bulunamazsa UUID, ardından oyuncu adı üzerinden Mojang skin adresi yeniden çözülür ve HTTPS ile alınır.
- Ana katman ve ikinci katmanlar analiz edilir; şeffaf pikseller sayılmaz.
- Baş/ten bölgesi düşük, gövde ve kıyafet katmanları daha yüksek ağırlık alır.
- En yüksek ve ikinci en yüksek sonuç ayrı öneri olarak gösterilir ve normal seçimde yalnızca bu iki kart açılır.
- Skin alınamazsa uydurma yüzdeler gösterilmez; manuel sınıf seçimi açık kalır.

## Antik Şehir Şarjı

Warden'ın 6. gücü 70 XP gerektirir. Hedef oyuncuya veya moba ışın atar; çömelerek kullanılırsa oyuncu üç kalp feda ederek kendini şarj eder.

- Şarj en fazla 20 saniye sürer ve bir güçlendirilmiş aktif güç hakkı verir.
- Uygun güçlerin bekleme süreleri geçici olarak açılır.
- Hasar, alan, süre, savurma ve görünür saldırı boyutu güç türüne göre artar. Uçuş, Doğa ve Zaman pasifleri de 20 saniyelik taşıma süresince geçici olarak güçlenir.
- Şarjlı Meteor Yağmuru tam 20 büyük morumsu meteor üretir.
- Yoğun parçacık duvarı kaldırılmıştır; ana görünürlük parlama dış çizgisi ve geçici sculk kol modellerinden gelir.
- Çöküş etkileri 20 saniye tamamlandıktan sonra başlar.

## Komutlar

Tek komut kökü `/skinpower` olarak düzenlenmiştir.

```text
/skinpower warden
/skinpower ucus
/skinpower ates
/skinpower doga
/skinpower zaman
```

Bu komutlar oyuncunun sınıfını değiştirir; eski sınıfa ait güç seviyeleri ve ustalık ilerlemesi temizlenir. Yetkili test komutları:

```text
/skinpower sarj ver @s 20
/skinpower sarj temizle @s
/skinpower reset @s
```

## Gereksinimler

- Minecraft Java Edition 26.1.2
- Fabric Loader 0.19.3+
- Fabric API 0.154.2+26.1.2
- Java 25
- Mod Menu 18.0.0 isteğe bağlı

## GitHub Actions ile derleme

1. Mevcut GitHub proje klasöründe gizli `.git` klasörünü koruyun.
2. Eski proje dosyalarını temizleyip bu paketin içeriğini ana klasöre kopyalayın.
3. `Commit to main` ve `Push origin` yapın.
4. GitHub `Actions` sekmesinde **Fabric JAR Derle** işinin yeşil tamamlanmasını bekleyin.
5. `skinpowers-1.0.1-jar` artifact'ini indirip içindeki `skinpowers-1.0.1.jar` dosyasını `mods` klasörüne koyun.

**Made by Yankalan**
