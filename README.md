# Skin Powers 1.2.1

Minecraft 26.1.2 için Fabric güç modu. Oyuncunun skin renklerini inceleyerek Warden, Kadim Ejderha, Ateş, Doğa ve Anomali sınıflarını önerir.

## 1.2.1 — Manyetik ve Kum

Yeni sınıflar:

- **Manyetik:** çekme-itme, metal yumruk, metal fırtınası, ray topu ve manyetik kafes.
- **Kum:** gerçek Minecraft kum/kumtaşı gövdeli mermi, dalga, aynalar, zırh, mezar ve dev kollar.
- Kumla vurulan oyuncunun ekranı 4 saniye kumla kaplanır; suya girince hemen temizlenir.
- Warden Derinlik Pususu artık zırhı ve eldeki eşyaları da güvenli biçimde gizler.

Sınıf test komutları:

```text
/skinpower degistir manyetik
/skinpower degistir kum
```


## Temel özellikler
- Beş sınıf ve her sınıf için altı güç seviyesi.
- Ustalık, XP, kombo ve sınıfa özel Uyanış sistemi.
- Warden için Derinlik Pususu ve 30 blok titreşim görüşü.
- Otomatik veya yönetici komutuyla başlatılabilen dünya olayları.
- Her sınıfa özel dört büyü; toplam 20 sınıf büyüsü.
- Büyülü kitaplar normal Minecraft örsünde uygun eşyaya basılır.
- Normal Minecraft büyüleri aynı eşyada kalabilir.
- Bir eşyada en fazla bir Skin Powers sınıf büyüsü bulunabilir.
- Özel etki yalnızca büyünün ait olduğu sınıf seçiliyken çalışır.
- PvP botu ve düello sistemi bulunmaz.

## Sınıf büyüleri

### Warden
- `Yankı Darbesi` — kılıç veya balta
- `Derinlik Adımı` — bot
- `Sculk Zırhı` — göğüslük
- `Antik Çöküş` — gürz

### Ateş
- `Kor Birikimi` — kılıç veya balta
- `Kül Yürüyüşü` — bot
- `Cehennem Çekirdeği` — göğüslük
- `Meteor Düşüşü` — gürz

### Doğa
- `Kök Bağı` — kılıç veya balta
- `Can Filizi` — miğfer
- `Orman Sıçrayışı` — bot
- `Dikenli Savunma` — göğüslük

### Anomali
- `Gecikmiş Darbe` — kılıç veya balta
- `Faz Kayması` — yay, arbalet veya üçlü mızrak
- `Hata Payı` — göğüslük
- `Bozuk Yörünge` — yay veya arbalet

### Kadim Ejderha
- `Ejderha Pençesi` — kılıç veya balta
- `Mor Kanat` — bot
- `Kadim Pullar` — göğüslük
- `Mor Nefes` — yay veya arbalet

## Büyülü kitap testi

Büyüler gerçek Minecraft büyüleri olarak veri paketinde kayıtlıdır. Yönetici/test komutu gerçek bir büyülü kitap verir:

```text
/skinpower buyu kitap yanki_darbesi
/skinpower buyu kitap derinlik_adimi
/skinpower buyu kitap sculk_zirhi
/skinpower buyu kitap antik_cokus
/skinpower buyu kitap kor_birikimi
/skinpower buyu kitap kul_yuruyusu
/skinpower buyu kitap cehennem_cekirdegi
/skinpower buyu kitap meteor_dususu
/skinpower buyu kitap kok_bagi
/skinpower buyu kitap can_filizi
/skinpower buyu kitap orman_sicrayisi
/skinpower buyu kitap dikenli_savunma
/skinpower buyu kitap gecikmis_darbe
/skinpower buyu kitap faz_kaymasi
/skinpower buyu kitap hata_payi
/skinpower buyu kitap bozuk_yorunge
/skinpower buyu kitap ejderha_pencesi
/skinpower buyu kitap mor_kanat
/skinpower buyu kitap kadim_pullar
/skinpower buyu kitap mor_nefes
```

Kitabı aldıktan sonra normal örs kullanılır:

```text
Uygun eşya + Sınıf Büyülü Kitabı = Sınıf büyülü eşya
```

## Diğer temel komutlar
```text
/skinpower degistir <warden|ejderha|ates|doga|anomali>
/skinpower olay <sculk|meteor|gok|doga|anomali|rastgele>
/skinpower olay durum
/skinpower olay durdur
```

Dünya olayı, büyü kitabı ve yönetim komutları moderatör yetkisi gerektirir.

## 1.1.1 düzeltmeleri

- Skin tarama üç kez dener; UUID sorgusu başarısız olsa bile oyuncu adıyla devam eder.
- Eski başarılı skin sonucu kalıcı tutulmaz; sınıf ekranı her açıldığında gerçek skin yeniden istenir.
- Skin alınamazsa sahte Steve çizilmez, "Skin bekleniyor" göstergesi görünür.
- Sınıf büyülü kitapları survival sandık ganimetlerinde ve kütüphaneci takaslarında bulunabilir.
- Meteor ve etkin büyü saldırıları gerçek eşya/blok gövdeleriyle görünür.
- Antik Çöküş ve Kor Birikimi güçlendirildi ve daha belirgin hâle getirildi.
