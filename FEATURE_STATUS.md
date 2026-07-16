# Özellik Durumu

## Doğrudan uygulanan ana sistemler

- Skin PNG'sindeki saydam olmayan piksellerin renk yakınlığıyla Warden/Uçuş/Ateş puanlanması
- İlk girişte otomatik sınıf seçim ekranı ve UUID tabanlı kalıcı kayıt
- 5/15/30/40/50 XP seviyeleriyle beş güç seviyesi
- Güç kullanımıyla 5, 9 ve 16 kullanım eşiklerinde ustalık gelişimi
- `R`, `Y`, sol/sağ ok, `O` ve çift boşluk kontrolleri
- Cooldown, HUD, seviye ekranı ve yönetici sıfırlama/meteor ayarı
- Üç sınıfın beşer seviyelik sunucu güç mantığı
- Warden/antik şehir, Uçuş/bulut ve Ateş/lav temalı; parıltı, açılma, tarama ve sarsıntı animasyonlu kartlar

## Minecraft motoruna uyarlanarak uygulanan görseller

- İstenen gerçek üç boyutlu oyuncu modeli yerine, skinin baskın renklerinden üretilen yavaş dönen blok karakter önizlemesi kullanılır.
- Bağlı kanatlar özel bir giyilebilir 3B model ve ayrı dayanıklılık eşyası değildir; zırh seviyelerini gözeten sunucu uçuş yeteneği ve kanat izi parçacıklarıdır.
- Warden titreşim görüşü bütün oyun seslerini yakalayan özel bir ses motoru değildir; karanlık filtre, hareket eden canlıların sculk işaretleri ve duvar arkasından görülebilen parçacık yaklaşımıdır.
- Ateş görüşü kırmızı-turuncu filtre ve yanan canlı işaretlemesidir; her ateş/lav bloğuna özel duvar arkası kontur çizmez.

Bu uyarlamalar, 26.1.2'nin yeni render sistemi içinde derleme riskini azaltmak ve sunucu/istemci ayrımını korumak için seçilmiştir.
