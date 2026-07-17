# Skin Powers 1.0.8 doğrulama notu

Paket oluşturulurken şu kontroller çalıştırılır:

- Gradle, Fabric, kaynaklar ve GitHub Actions dosyalarının varlığı.
- Bütün JSON dosyalarının ayrıştırılması.
- Sürüm, artifact ve JAR adlarının 1.0.8 olması.
- Beş sınıf, Kadim Ejderha, Uyanış, düello ve mevcut kayıt geçişlerinin korunması.
- PvP bot komutları, beş bot sınıfı, dört zorluk ve savaş paneli veri akışı.
- Anomali kopya kullanım sayısı, Kırık Adım yankıları, hasar depolama ve 404 geliştirmeleri.
- Ateş ve Warden animasyon/güçlendirme kodlarının varlığı.
- Kart PNG dosyalarının 288×512 boyutu ve bütün JSON kaynakları.
- Bütün Java dosyalarında parantez/sözdizimi dengesi.
- Minecraft bağımlılığı olmayan çekirdek sınıfların gerçek `javac` derlemesi ve davranış testi.
- Oluşturulan ZIP'in tekrar açılması ve arşiv bütünlüğü.

Bu çalışma ortamında Java 25 ve Minecraft/Fabric bağımlılıkları birlikte bulunmadığından tam `gradle clean build` burada çalıştırılamaz. Son API/derleme kontrolü GitHub Actions sonucudur.
