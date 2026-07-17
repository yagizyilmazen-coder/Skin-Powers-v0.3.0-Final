# Skin Powers 1.0.4 özellik durumu

## Hazır

- Warden, Uçuş, Ateş ve Doğa sınıfları korunmuştur.
- Zaman sınıfı yerine altı seviyeli Anomali sınıfı eklenmiştir.
- `?` gücü, 30 blok içindeki rakibin son uygun aktif gücünü otomatik yakalar; O menüsündeki ad/açıklama değişir ve kopya tek kullanım sonrası silinir.
- Kopyalanan süreli Warden, Uçuş ve Ateş güçleri kendi süreleri boyunca çalışmaya devam eder.
- Hasar Mevcut Değil alınan hasarı iptal edip depolar; küçük HUD üzerinden V veya X bekler.
- V gerçek kırmızı maksimum can yuvası ekler; yuvalar anında doldurulmaz, 3 dakika sonra güvenli biçimde kaldırılır.
- X hedef yoksa depolanmış hasarı harcamaz.
- Varlıktan Çıkar ve 404 alanı blok yerleştirmez.
- Varlıktan Çıkar sırasında hedef veya Anomali oyuncusu sunucudan ayrılırsa görünmezlik/ölümsüzlük durumu güvenli biçimde geri alınır.
- Kopyalanan Warden VI, oyuncu çömeliyor olsa bile kendine şarj vermek yerine saldırı ışını olarak çalışır.
- Warden şarj bildirimi iki küçük satırdır ve sol üst HUD'dan uzaktadır.
- Komut biçimi `/skinpower degistir <sinif>` şeklindedir.
- Eski TIME/ZAMAN oyuncu kayıtları Anomaliye taşınır.

## Gerçek oyun içinde ayrıca sınanması gerekenler

- Çok oyunculu ortamda farklı sınıflardan bütün kopyalanabilir güçlerin görsel ve hasar davranışı.
- V/X tuşlarının oyuncunun özel tuş ayarlarıyla çakışıp çakışmadığı.
- Farklı GUI ölçeklerinde HUD yerleşimi.
- Sunucu yeniden başlatıldıktan sonra kopyalanmış hamle ve geçici kırmızı kalp süresinin devamı.
