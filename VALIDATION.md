# Skin Powers 1.0.4 doğrulama notu

## Otomatik geçen kontroller

- Bütün JSON kaynakları ayrıştırıldı.
- 24 Java kaynak dosyasının parantez/sınıf yapısı denetlendi.
- Minecraft bağımlılığı olmayan çekirdek sınıflar gerçek `javac` ile derlendi.
- Anomali VI seviyesi, güç açıklamaları, kopya kaydı, hasar deposu, üç dakikalık ek kalpler ve sıfırlama davranışı test edildi.
- İkinci skin önerisinin seçilebildiği ve altıncı menü satırı animasyonunun yüzde 100'e ulaştığı test edildi.
- Eski `TIME/ZAMAN` kayıtlarını yükleme sırasında `ANOMALY` değerine dönüştüren geçiş kodu kontrol edildi.
- `/skinpower` kökünde doğrudan sınıf seçimi bulunmadığı; sınıfların yalnızca `degistir` dalından sonra geldiği kontrol edildi.
- Varlıktan Çıkar bağlantı-kesilme temizliği ve kopyalanan Warden VI saldırı ışını yolu kod denetimine eklendi.
- ZIP oluşturulduktan sonra arşiv bütünlüğü ayrıca denetlenecektir.

## Son derleme sınırı

Bu çalışma ortamında Java 25 ile Minecraft/Fabric bağımlılıklarını kullanan tam `gradle clean build` çalıştırılamadı. Tam derleme, paketteki GitHub Actions iş akışında Java 25 ve Gradle 9.5.1 ile yapılır. Kırmızı çarpı oluşursa Actions günlük ZIP'i gerçek derleyici hatasını gösterir.
