# Skin Powers 1.0.5

Minecraft Java Edition 26.1.2 için Fabric güç modu.

## 1.0.5 ana değişiklikleri

- **Kırık Adım** koşarken çalışır, daha uzağa gider ve eğimli zeminde güvenli iniş noktası arar.
- **Hasar Mevcut Değil** mob, oyuncu, mermi, patlama ve mod güçlerinden gelen hasarı depolar.
- **404**; Warden saldırıları dâhil alan içindeki hasarı saldırana geri yollar. Minecraft mermileri ile Meteor, Cehennem Küresi, Doğa Tohumu, Kök Dalgası, Uçuş Mızrağı ve Gök Bombası havada durur; alan kapanınca saldırıyı yapan kişiye geri döner.
- Büyük güçler yakın zamanda ve yakın mesafede kullanılırsa **güç çarpışması** oluşur. Zayıf saldırının devam eden parçaları temizlenir.
- Güvenli **sınıf düelloları** eklendi. Düelloda gerçek ölüm ve eşya kaybı olmaz; dışarıdaki oyuncular ve moblar karışamaz.
- Beş **dünya olayı** eklendi: Sculk Uyanışı, Meteor Fırtınası, Gökyüzü Yarığı, Kadim Çiçeklenme ve Gerçeklik Çatlağı.
- Küçük sınıf pasifleri tamamlandı. Warden hareket titreşimlerini hisseder; Uçuş yavaş düşer; Ateş yanmaz; Doğa doğal zeminde toparlanır; Anomali on dakikada bir ölümcül sonucu reddedebilir.
- Eski `TIME/ZAMAN` oyuncu kayıtları Anomaliye taşınmaya devam eder.
- Sınıf değiştirme yalnızca `/skinpower degistir <sinif>` üzerinden yapılır.

## Anomali güçleri

1. **Kırık Adım — 10 XP:** baktığın yöne kırılarak sıçrar, yolundaki düşmanlara hasar verir.
2. **Tersine Çevir — 20 XP:** hedefin hareketini, mermilerini ve saldırısının bir bölümünü tersine çevirir.
3. **? — 30 XP:** 30 blok içindeki rakibin son kopyalanabilir aktif gücünü yakalar. Hamle bir kez kullanılınca tekrar `?` olur.
4. **Hasar Mevcut Değil — 40 XP:** 5 saniyelik hasarı depolar. Sonra küçük HUD seçimi çıkar:
   - `V`: hasarın yarısını en fazla 5 yeni kırmızı kalp kapasitesine dönüştürür. Toplam sınır 10 ek kalptir; süre 3 dakikadır. Yeni kalp yuvaları boş gelir ve normal kırmızı kalpler gibi yemek/iyileşme ile dolar.
   - `X`: depolanan hasarın tamamını nişangâhtaki hedefe geri gönderir.
5. **Varlıktan Çıkar — 50 XP:** hedefi kısa süre savaştan çıkarır; döndüğünde savunması kırılır.
6. **404: Gerçeklik Bulunamadı — 70 XP:** geniş gerçeklik alanı açar; düşmanları bozar, mermileri ters çevirir ve oyuncuyu bir kez ölümden döndürür.

Anomali güçleri blok yerleştirmez veya dünyayı güç görseli için değiştirmez.

## Tuşlar

- `R`: seçili aktif gücü kullan
- `Sol / Sağ Ok`: güç değiştir
- `O`: güç menüsü
- `Y`: sınıfa bağlı yardımcı özellik
- `K`: Warden, Uçuş, Ateş ve Doğa kombo modu
- `V`: Anomali depolanmış hasarını kalbe çevir
- `X`: Anomali depolanmış hasarını geri gönder

## Komutlar

Oyuncunun kendi sınıfını değiştirmesi:

```text
/skinpower degistir warden
/skinpower degistir ucus
/skinpower degistir ates
/skinpower degistir alev
/skinpower degistir doga
/skinpower degistir anomali
```

Sınıf adları `/skinpower` kökünde doğrudan görünmez; önce `degistir` seçilmelidir.

Düello komutları:

```text
/skinpower duello <oyuncu>
/skinpower duello kabul
/skinpower duello reddet
/skinpower duello bitir
```

Dünya olayı durumunu görmek:

```text
/skinpower olay
```

Yönetici komutları:

```text
/skinpower admin degistir <oyuncu> <sinif>
/skinpower admin reset <oyuncu>
/skinpower admin meteor blokhasari <true|false>
/skinpower admin olay baslat <sculk|meteor|gok|doga|anomali|rastgele>
/skinpower admin olay durdur
/skinpower admin sarj ver <oyuncu> <saniye>
/skinpower admin sarj temizle <oyuncu>
```

### Yönetici saldırı tetikleme komutu

Sınıfı değiştirmeden ve cooldown beklemeden saldırı test etmek için:

```text
/skinpower trigger meteor
/skinpower trigger meteor_charged
/skinpower trigger sonic
/skinpower trigger sky_bomb_charged
```

`_charged` son eki saldırının Antik Şehir ile güçlendirilmiş sürümünü çağırır. `trigger` dalı yalnızca operatör/moderatör yetkisine açıktır.

## GitHub Actions ile JAR

1. ZIP'i **Tümünü Ayıkla** ile açın.
2. İçindeki dosyaları GitHub Desktop'taki proje ana klasörüne kopyalayın.
3. Değişiklikleri commit edip **Push origin** yapın.
4. GitHub'da **Actions → Fabric JAR Derle** işleminin yeşil tamamlanmasını bekleyin.
5. `skinpowers-1.0.5-jar` artifact'ini indirin.
6. İçindeki `skinpowers-1.0.5.jar` dosyasını Minecraft `mods` klasörüne koyun.

## Sürüm hedefleri

- Minecraft Java Edition `26.1.2`
- Fabric Loader `0.19.3`
- Fabric API `0.154.2+26.1.2`
- Java `25`
- Gradle `9.5.1`
