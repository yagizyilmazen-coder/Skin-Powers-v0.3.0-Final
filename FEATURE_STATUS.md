# Skin Powers 1.0.5 özellik durumu

## Hazır

- Warden, Uçuş, Ateş, Doğa ve Anomali sınıfları korunmuştur.
- Kırık Adım koşu yönünü kullanır, daha uzağa gider, eğimli zeminde güvenli konum arar ve çıkış momentumunu korur.
- Hasar Mevcut Değil; mob, oyuncu, mermi, patlama, ateş ve mod güçlerinin normal hasarını depolar.
- 404 alanı yakın/ışın hasarını saldırana geri yollar; gerçek Minecraft mermilerini dondurup sahibine geri gönderir.
- Meteor, Cehennem Küresi, Doğa Tohumu, Kök Dalgası, Uçuş Mızrağı ve Gök Bombası 404 alanında durur ve kapanışta eski saldırgana yönelir.
- Güç çarpışmaları büyük güçleri kısa bir zaman penceresinde karşılaştırır; eşitse iki devam eden saldırıyı, değilse zayıf olanı temizler.
- Güvenli düello isteği, kabul, reddetme ve teslim olma komutları vardır.
- Düelloda dış hasar engellenir; gerçek ölüm/eşya kaybı olmaz ve süre sonunda oyuncular başlangıç konumuna döner.
- Beş blok-kırmayan dünya olayı vardır ve yönetici komutuyla başlatılabilir.
- Warden titreşim algısı ve Anomali Hata Payı dâhil sınıf pasifleri uygulanmıştır.
- Warden şarj bildirimi iki küçük satırdır ve sol üst HUD'dan uzaktadır.
- Komut biçimi `/skinpower degistir <sinif>` şeklindedir.
- Eski TIME/ZAMAN oyuncu kayıtları Anomaliye taşınır.

## Gerçek oyun içinde özellikle sınanması gerekenler

- İki oyuncunun aynı anda seviye 3–6 güç kullanarak güç çarpışması oluşturması.
- Düelloda ölümün iptal edilmesi, eşya düşmemesi ve dışarıdaki oyuncuların zarar verememesi.
- 404 alanında vanilla Warden yakın saldırısı/sonik saldırısı ile bütün hareketli mod saldırılarının geri dönüşü.
- Dünya olaylarının Overworld, Nether ve çok oyunculu sunucudaki etkileri.
- V/X tuşlarının oyuncunun özel tuş ayarlarıyla çakışıp çakışmadığı.
- Farklı GUI ölçeklerinde HUD yerleşimi.
