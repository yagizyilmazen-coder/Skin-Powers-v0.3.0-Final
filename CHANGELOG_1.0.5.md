# Skin Powers 1.0.5

## Anomali düzeltmeleri
- Kırık Adım koşarken de çalışır, daha uzağa gider, eğimli zeminde güvenli iniş noktası arar ve hareket hızını korur.
- Hasar Mevcut Değil; oyuncu, mob, mermi, patlama ve mod güçlerinden gelen hasarı depolar.
- 404 alanı yakın saldırıları ve ışın hasarını saldırana geri yollar.
- Oklar ve gerçek Minecraft mermileri havada donar; alan kapanınca sahibine geri döner.
- Meteor, cehennem küresi, doğa tohumu, kök dalgası, uçuş mızrağı ve gök bombası alan içinde durur; alan kapanınca saldırıyı yapan kişiye yönelir.
- Anomali, on dakikada bir ölümcül sonucu 1 canla reddeden küçük Hata Payı pasifi kazandı.

## Yeni sistemler
- Büyük güçler kısa süre içinde yakın mesafede kullanıldığında güç çarpışması oluşur; zayıf saldırının devam eden parçaları temizlenir.
- `/skinpower duello <oyuncu>`, `kabul`, `reddet` ve `bitir` komutlarıyla güvenli sınıf düelloları eklendi.
- Düelloda gerçek ölüm ve eşya kaybı olmaz; dış oyuncular, moblar ve çevresel hasar düelloya karışamaz.
- Beş dünya olayı eklendi: Sculk Uyanışı, Meteor Fırtınası, Gökyüzü Yarığı, Kadim Çiçeklenme ve Gerçeklik Çatlağı.
- Dünya olayları bloklara kalıcı hasar vermez ve yönetici komutuyla test edilebilir.
- Sınıf pasifleri tamamlandı: Warden hareket titreşimlerini hisseder; Uçuş yavaş düşer; Ateş yanmaz; Doğa doğal zeminde toparlanır; Anomali sonucu reddedebilir.

## Yönetici dünya olayı komutları
- `/skinpower admin olay baslat sculk`
- `/skinpower admin olay baslat meteor`
- `/skinpower admin olay baslat gok`
- `/skinpower admin olay baslat doga`
- `/skinpower admin olay baslat anomali`
- `/skinpower admin olay baslat rastgele`
- `/skinpower admin olay durdur`

## Trigger saldırı komutları
- Yetkili oyuncular saldırıları sınıf, seviye ve cooldown değiştirmeden `/skinpower trigger <atak>` ile test edebilir.
- `_charged` son eki saldırının Antik Şehir ile güçlendirilmiş sürümünü çağırır.
- Örnekler: `/skinpower trigger meteor` ve `/skinpower trigger meteor_charged`.
- Komut yalnızca moderatör/operatör yetkisine açıktır; normal oyuncular sınıf güçlerini yine oyun içi tuşlarla kullanır.
