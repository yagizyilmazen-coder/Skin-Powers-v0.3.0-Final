# Skin Powers 1.0.5 doğrulama notu

## Otomatik geçen kontroller

- Bütün JSON kaynakları ayrıştırıldı.
- Ana ve istemci Java kaynaklarının parantez/sınıf yapısı denetlendi.
- Minecraft bağımlılığı olmayan çekirdek sınıflar gerçek `javac` ile derlendi.
- Anomali VI, güç açıklamaları, kopya kaydı, hasar deposu, üç dakikalık ek kalpler, Hata Payı süresi ve düello cooldown sıfırlaması test edildi.
- İkinci skin önerisinin seçilebildiği ve altıncı menü satırı animasyonunun yüzde 100'e ulaştığı test edildi.
- Eski `TIME/ZAMAN` kayıtlarını `ANOMALY` değerine dönüştüren geçiş kodu kontrol edildi.
- Güç çarpışması, düello, dünya olayı ve 404 iç saldırı geri çevirme kodlarının gerekli giriş noktaları tarandı.
- `/skinpower` kökünde doğrudan sınıf seçimi bulunmadığı kontrol edildi.
- ZIP oluşturulduktan sonra arşiv bütünlüğü ayrıca denetlenecektir.

## Son derleme sınırı

Bu çalışma ortamında Java 25 ile Minecraft/Fabric bağımlılıklarını kullanan tam `gradle clean build` çalıştırılamadı. Tam derleme, paketteki GitHub Actions iş akışında Java 25 ve Gradle 9.5.1 ile yapılır. Kırmızı çarpı oluşursa Actions günlük ZIP'i gerçek derleyici hatasını gösterir.
