# Skin Powers 1.0.7 doğrulama notu

Bu paket için aşağıdaki kontroller otomatik yapılır:

- Temel Gradle, Fabric, kaynak ve GitHub Actions dosyalarının varlığı.
- Bütün JSON dosyalarının ayrıştırılması.
- Sürüm, artifact ve JAR adlarının 1.0.7 olması.
- Kadim Ejderha'nın altı adı, açıklaması, komutları ve eski FLIGHT kayıt uyumluluğu.
- Uyanış enerjisi, yüzdeye bağlı süre, G tuşu, HUD senkronizasyonu ve beş form adı.
- Düello filtresi, komutları ve Uyanış başlangıç sıfırlaması.
- Anomali, güç çarpışması, dünya olayı ve trigger sistemlerinin korunması.
- Sınıf kart PNG dosyalarının varlığı, 288×512 boyutu ve dosya bütünlüğü.
- Tüm Java dosyalarında kaba sözdizimi/parantez dengesi.
- Minecraft bağımlılığı olmayan çekirdek sınıfların gerçek `javac` derlemesi ve davranış testi.
- ZIP arşiv bütünlüğü paket oluşturulduktan sonra ayrıca kontrol edilir.

Bu çalışma ortamında Java 25, Gradle 9.5.1 ve Minecraft/Fabric bağımlılıkları birlikte bulunmadığından tam `gradle clean build` burada çalıştırılamaz. Son API ve tam derleme kontrolü GitHub Actions sonucudur; bu yüzden hiçbir hata çıkmayacağı kesin olarak garanti edilmez.
