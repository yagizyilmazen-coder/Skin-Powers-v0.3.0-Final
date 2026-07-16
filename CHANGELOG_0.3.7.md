# Skin Powers 0.3.7

## Eklendi

- Warden için 70 XP değerinde altıncı güç: **Şarj Et Beni Antik Şehir**
- Dört sculk kolunun birleşen ışın animasyonu
- 20 saniyelik tek kullanımlık Antik Şehir Şarjı ve büyüyen mutasyon görünümü
- Şarj bitiminde 30 saniyelik Yavaşlık V, Bulantı ve Madencilik Yorgunluğu
- `/skinpowers charge give <oyuncu> <1-20>` ve `/skinpowers charge clear <oyuncu>` test komutları
- Türkçe `/skingucu sarj ...` komutları
- Şarj durumunu ve çöküş süresini gösteren HUD paneli

## Değiştirildi

- Warden sınıfı altı güç gösterecek şekilde veri, menü, ustalık ve XP sistemi genişletildi.
- Antik Şehir Şarjı sırasında uygun aktif güçlerin bekleme süreleri geçici olarak devre dışı bırakılır.
- İlk kullanılan aktif güç şarjı tüketir; eski cooldownlar geri döner ve kullanılan gücün yeni cooldownu korunur.
- Warden, Uçuş, Ateş ve Doğa sınıflarının bütün aktif güçleri için ortak şarj altyapısı eklendi.
- Şarjlı güçler mor/camgöbeği görünüm, daha yüksek hasar, alan, süre ve savurma kazanır.
- Şarjlı Meteor Yağmuru 20 büyük morumsu meteor oluşturur.
- Kısa ekranlarda altıncı Warden kartının yazılarla çakışmasını önleyen menü yerleşimi eklendi.

## Korundu

- Normal güçlerin şarjsız çalışma şekli ve 0.3.6 sınıf dengeleri korunmuştur.
- Warden'ın altıncı gücü kendini veya başka bir altıncı gücü şarj zinciriyle güçlendiremez.
