# Skin Powers 1.0.7

Skin Powers, oyuncunun Minecraft skinindeki renkleri analiz ederek uygun güç sınıfları öneren Fabric modudur. İlk seçimde bütün sınıflar görünür; gerçek skin analizi başarılıysa yalnızca en yüksek ve ikinci en yüksek öneri seçilebilir.

## 1.0.7 ana değişiklikleri

- Eski **Uçuş/Kuş sınıfı**, kayıtlar bozulmadan **Kadim Ejderha** sınıfına dönüştürüldü.
- Beş sınıf için hasar alıp verdikçe dolan **Uyanış çubuğu** eklendi. `G` ile etkinleşir; çubuk ne kadar doluysa form o kadar uzun sürer. Sabit üç dakikalık bekleme yoktur.
- Başlangıç sınıf kartları, sınıf renkleri, arka planları ve giriş animasyonları yenilendi.
- Güç kullanımlarına hazırlık/çarpışma hissi veren sınıfa özel parçacıklar, ekran kenarı parlaması ve ayarlanabilir sarsıntılar eklendi.
- Mod Menu ayar ekranı HUD, Animasyon ve Erişilebilirlik sekmelerine ayrıldı.
- Düello sistemi korunup Uyanış sistemiyle uyumlu hâle getirildi.
- Anomali, güç çarpışmaları, dünya olayları ve trigger saldırıları korunmuştur.

## Güç sınıfları

### Warden
Warden Zırhı, Yer Sarsıntısı, Sonik Patlama, Sculk Avı, Warden Uyanışı ve Antik Şehir Şarjı.

**Sınıf Uyanışı:** Antik Şehir Uyanışı

### Kadim Ejderha

1. **Kuyruk Kasırgası:** Çevrende dönen mor kuyruk dalgası; yakındaki düşmanlara hasar verir ve uzağa savurur.
2. **Ejderha Nefesi:** Blok yakmayan geniş mor enerji konisi.
3. **Kadim Pullar:** Hasar azaltma ve yakındaki mermileri geri sektirme.
4. **Avcı Pençesi:** Hedefi yakalama; ikinci kullanımda baktığın yöne fırlatma.
5. **Kadim Kükreme:** Alan hasarı, savurma, mermi bozma ve kısa güç susturma.
6. **Ejderha Hükümdarı:** Mor enerji kanatları, serbest uçuş, güç ve hız.

**Pasif:** Düşme hasarını engeller ve ateşe dayanıklılık verir.  
**Sınıf Uyanışı:** Mor Kıyamet

Eski kayıtlardaki `FLIGHT` değeri korunur; oyuncunun XP, seviyesi ve ustalığı silinmez. Komut adı artık `ejderha`dır.

### Ateş
Ateş bağışıklığı, alevli yakın dövüş, Ateş Çemberi, Cehennem Küresi ve Meteor Yağmuru.

**Sınıf Uyanışı:** Cehennem Çekirdeği

### Doğa
Doğanın Canı, Dikenli Tohum, Sarmaşık Kapanı, Yaşam Ağacı ve Kadim Orman Hükmü.

**Sınıf Uyanışı:** Kadim Orman

### Anomali
Kırık Adım, Tersine Çevir, `?`, Hasar Mevcut Değil, Varlıktan Çıkar ve 404: Gerçeklik Bulunamadı.

- `?`, yakındaki rakibin son uygun aktif gücünü tek kullanımlık olarak saklar.
- Hasar Mevcut Değil aktifken alınan hasar moblar, oyuncular ve uygun çevresel kaynaklardan depolanır.
- `V`, depolanan hasarın yarısını üç dakikalık gerçek kırmızı maksimum kalplere çevirir.
- `X`, depolanan hasarı baktığın hedefe geri gönderir.

**Sınıf Uyanışı:** Sistem Çökmesi

## Uyanış Formları

Uyanış enerjisi:

- Oyuncu herhangi bir geçerli kaynaktan hasar aldığında dolar.
- Oyuncu moblara veya başka oyunculara hasar verdiğinde dolar.
- Tek büyük vuruş çubuğu anında tamamen dolduramaz.
- Form etkinken yeni enerji birikmez.
- Çubuk en az `%20` doluyken `G` ile kullanılabilir.
- `%100` enerji yaklaşık 24 saniyelik forma karşılık gelir; daha az enerji daha kısa süre verir.
- Sabit tekrar bekleme süresi yoktur. Form bittikten sonra yeniden savaşarak doldurulur.

## Düello

```text
/skinpower duello <oyuncu>
/skinpower duello kabul
/skinpower duello reddet
/skinpower duello bitir
```

Düello başladığında can, açlık ve güç bekleme süreleri hazırlanır; Uyanış çubukları boş başlar ve dövüş sırasında dolar. Üçüncü kişiler düellodakilere zarar veremez, güçler düello dışındaki hedeflere taşmaz, eşyalar düşmez ve bitince oyuncular başlangıç yerlerine döner.

## Tuşlar

- `R`: Seçili aktif gücü kullan
- `Sol / Sağ Ok`: Güç değiştir
- `O`: Güç menüsü
- `Y`: Sınıfa özel yardımcı işlev
- `K`: Kombo modu
- `G`: Uyanış Formu
- `V`: Anomali depolanmış hasarını kalbe dönüştür
- `X`: Anomali depolanmış hasarını geri gönder
- `Çift Boşluk`: Kadim Ejderha hava atılışı

## Mod Menu ayarları

### HUD
HUD ölçeği ve tarafı, dikey konum, bildirim ölçeği, Uyanış çubuğu ve kompakt görünüm.

### Animasyon
Menü animasyonları, kart hızı, parçacık yoğunluğu, parlama, hareketli arka plan, kart derinliği, skin tarama çizgisi ve ekran sarsıntısı.

### Erişilebilirlik
Performans modu, birinci şahıs efekt azaltma ve foto-hassasiyet modu.

Ayarlar `config/skinpowers-client.json` dosyasında saklanır.

## Komut örnekleri

```text
/skinpower degistir warden
/skinpower degistir ejderha
/skinpower degistir ates
/skinpower degistir doga
/skinpower degistir anomali

/skinpower trigger meteor
/skinpower trigger meteor_charged
/skinpower trigger dragon_breath
/skinpower trigger dragon_roar
/skinpower trigger dragon_form
```

Sınıf adları doğrudan `/skinpower warden` biçiminde çalışmaz; önce `degistir` yazılmalıdır. Trigger komutları moderatör/operatör yetkisi gerektirir.

## Gereksinimler

- Minecraft Java Edition 26.1.2
- Fabric Loader 0.19.3
- Fabric API 0.154.2+26.1.2
- Java 25
- Mod Menu isteğe bağlıdır; ayar ekranını açmayı kolaylaştırır.

## GitHub Actions ile JAR

1. Proje dosyalarını deponun ana dizinine kopyalayın.
2. GitHub Desktop'ta commit ve `Push origin` yapın.
3. GitHub'da **Actions → Fabric JAR Derle** çalışmasını açın.
4. Yeşil tamamlanınca `skinpowers-1.0.7-jar` artifact'ini indirin.
5. İçindeki `skinpowers-1.0.7.jar` dosyasını Minecraft `mods` klasörüne koyun.

Eski Skin Powers JAR'larını aynı anda `mods` klasöründe bırakmayın.
