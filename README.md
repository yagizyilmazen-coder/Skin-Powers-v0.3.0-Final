# Skin Powers 1.0.8

Skin Powers, Minecraft skinindeki renkleri analiz ederek Warden, Kadim Ejderha, Ateş, Doğa ve Anomali sınıflarını öneren Fabric modudur. İlk seçim ekranında bütün sınıflar görünür; skin analizi başarılıysa en yüksek ve ikinci en yüksek öneri seçilebilir.

## 1.0.8 ana değişiklikleri

- **Sınıf Ustası PvP botları:** Beş sınıf için Kolay, Normal, Zor ve Kâbus rakipler.
- **Anomali yenilemesi:** Gecikmeli Kırık Adım yankıları, iki kullanımlık Uyanış kopyası, güçlendirilmiş Varlıktan Çıkar ve 404.
- **Ateş yenilemesi:** Alan alevli yakın dövüş, dönen Ateş Çemberi çekirdekleri ve daha güçlü Cehennem Çekirdeği.
- **Warden yenilemesi:** Sculk göğüs çekirdeği, katmanlı Yer Sarsıntısı, gelişmiş sonic izleri ve güçlü Antik Şehir Uyanışı.
- **Savaş paneli:** Düello ve bot rakibinin canı, sınıfı, Uyanışı ve zorluğu üst-orta panelde gösterilir.
- Kadim Ejderha, Uyanış Formları, güç çarpışmaları, dünya olayları ve trigger komutları korunur.

## Sınıflar

### Warden
Warden Zırhı, Yer Sarsıntısı, Sonik Patlama, Sculk Avı, Warden Uyanışı ve Antik Şehir Şarjı.

**Uyanış:** Antik Şehir Uyanışı

### Kadim Ejderha
Kuyruk Kasırgası, yönlendirilebilir Ejderha Nefesi, saldırı emen Kadim Pullar, Avcı Pençesi, güçlü Kadim Kükreme ve Ejderha Hükümdarı.

**Uyanış:** Mor Kıyamet

### Ateş
Ateş Bağışıklığı, alan etkili Alevli Yakın Dövüş, dönen çekirdekli Ateş Çemberi, Cehennem Küresi ve görünür Meteor Yağmuru.

**Uyanış:** Cehennem Çekirdeği

### Doğa
Doğanın Canı, Dikenli Tohum, Sarmaşık Kapanı, Yaşam Ağacı ve Kadim Orman Hükmü.

**Uyanış:** Kadim Orman

### Anomali
1. **Kırık Adım:** İleri yarılır ve arkasında gecikmeli patlayan bozuk görüntüler bırakır.
2. **Tersine Çevir:** Hedefin hareketini, mermilerini ve hasarının bir bölümünü tersine çevirir.
3. **?:** Rakibin son uygun aktif gücünü saklar; Sistem Çökmesi sırasında iki kullanım kazanır.
4. **Hasar Mevcut Değil:** Mob, oyuncu, mermi ve patlama hasarını depolar. `V` geçici kırmızı kalbe, `X` tam geri saldırıya çevirir.
5. **Varlıktan Çıkar:** Hedefi savaş dışına alır; geri dönüşte alanı içeri çökerterek hasar ve savurma oluşturur.
6. **404: Gerçeklik Bulunamadı:** Mermileri ve saldırıları reddeder, hasarı geri yollar, beklemeleri hızlandırır ve ölümü bir kez iptal eder.

**Uyanış:** Sistem Çökmesi

## PvP botları

```text
/skinpower bot cagir warden normal
/skinpower bot cagir ates zor
/skinpower bot cagir doga kolay
/skinpower bot cagir anomali kabus
/skinpower bot cagir ejderha zor

/skinpower bot durdur
/skinpower bot devam
/skinpower bot temizle
```

Botlar sınıfına göre mesafe seçer, saldırılardan kaçmaya çalışır, aktif güçlerini sırayla değil duruma göre kullanır ve savaşarak Uyanış enerjisi toplar. Bunlar sahte çevrim içi hesaplar değil, sunucunun yönettiği özel sınıf ustası savaş varlıklarıdır.

## Düello

```text
/skinpower duello <oyuncu>
/skinpower duello kabul
/skinpower duello reddet
/skinpower duello bitir
```

Düello ve bot rakibinin can/Uyanış bilgisi küçük üst-orta panelde görünür. Panel Mod Menu içindeki HUD sekmesinden kapatılabilir.

## Uyanış Formları

- Hasar aldıkça ve geçerli hedeflere hasar verdikçe enerji dolar.
- En az `%20` enerjiyle `G` tuşuna basılarak açılır.
- Bar ne kadar doluysa form o kadar uzun sürer; `%100` yaklaşık 24 saniyedir.
- Sabit üç dakikalık bekleme yoktur; form bitince tekrar savaşarak doldurulur.

## Tuşlar

- `R`: Seçili gücü kullan
- `Sol / Sağ Ok`: Güç değiştir
- `O`: Güç menüsü
- `Y`: Sınıfa özel yardımcı işlev
- `K`: Kombo
- `G`: Uyanış Formu
- `V / X`: Anomali depolanan hasar seçimi
- `Z`: Avcı Pençesi'nden kaçma

## Sınıf değiştirme ve trigger

```text
/skinpower degistir warden
/skinpower degistir ejderha
/skinpower degistir ates
/skinpower degistir doga
/skinpower degistir anomali

/skinpower trigger meteor
/skinpower trigger meteor_charged
/skinpower trigger dragon_breath
/skinpower trigger reality_404
```

Trigger komutları moderatör/operatör yetkisi gerektirir.

## Gereksinimler

- Minecraft Java Edition 26.1.2
- Fabric Loader 0.19.3
- Fabric API 0.154.2+26.1.2
- Java 25
- Mod Menu isteğe bağlıdır

## GitHub Actions ile JAR

1. Proje içeriğini GitHub deposunun ana dizinine kopyalayın.
2. Commit ve `Push origin` yapın.
3. **Actions → Fabric JAR Derle** çalışmasını açın.
4. Başarılı olunca `skinpowers-1.0.8-jar` artifact'ini indirin.
5. İçindeki `skinpowers-1.0.8.jar` dosyasını `mods` klasörüne koyun.

Eski Skin Powers JAR'larını aynı anda `mods` klasöründe bırakmayın.
