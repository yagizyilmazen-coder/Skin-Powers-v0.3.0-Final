# Skin Powers 1.3.0

Minecraft 26.1.2 için Fabric güç modu. Skin renklerini inceleyerek Warden, Kadim Ejderha, Ateş, Ay, Anomali, Manyetik ve Kum sınıflarını önerir.

## 1.3.0 — Ay ve Anomali 2.0

### Ay sınıfı

1. **Hilal Kesik:** Gidip geri dönen görünür hilal.
2. **Ay Adımı:** Güvenli noktaya sıçrama ve geride patlayan ay görüntüsü.
3. **Yerçekimi Baskısı:** Düşmanları yere bastıran ve mermileri aşağı büken alan.
4. **Ay Aynası:** Mermileri yansıtan disk; ikinci kullanımda hilal olarak fırlatılır.
5. **Tutulma Alanı:** Ay oyuncusunu güçlendirip rakipleri zayıflatan alan.
6. **Dolunay Canavarı:** İki pençe darbesi ve final yere çarpması yapan büyük görünür yaratık.

**Uyanış:** Tam Tutulma.

### Anomali 2.0

- Kırık Adım gerçek görünür bozuk kopyalar bırakır.
- Tersine Çevir hedefte görünür hata halkası ve `REVERSED` bildirimi oluşturur.
- `?`, yakınında kullanılan uygun gücü 10 saniye saklar.
- Hasar Mevcut Değil depolanan hasarı görünür kırmızı hata parçalarıyla gösterir.
- Varlıktan Çıkar hedefin bulunduğu yerde görünür bozulma bedeni bırakır.
- 404 alanının merkezinde büyük, gerçek eşya gövdeli `404` işareti bulunur.

### Dünya olayı

```text
/skinpower olay ay
```

**Kızıl Ay** sırasında yerçekimi ağırlaşır, geçici ay sütunları yükselir ve Ay sınıfı güçlenir. Etkinlik yapıları süre sonunda temizlenir.

## Yedi sınıf

- Warden
- Kadim Ejderha
- Ateş
- Ay
- Anomali
- Manyetik
- Kum

## Ay sınıf büyüleri

- `Hilal Yarası` — kılıç veya balta
- `Ay Gözü` — miğfer
- `Ay Adımı` — bot
- `Ay Aynası` — göğüslük

Büyülü kitap test komutları:

```text
/skinpower buyu kitap hilal_yarasi
/skinpower buyu kitap ay_gozu
/skinpower buyu kitap ay_adimi
/skinpower buyu kitap ay_aynasi
```

Kitaplar survival sandık ganimetlerinde ve kütüphaneci takaslarında bulunabilir; normal örste uygun eşyaya basılır.

## Temel komutlar

```text
/skinpower degistir <warden|ejderha|ates|ay|anomali|manyetik|kum>
/skinpower olay <sculk|meteor|gok|ay|anomali|rastgele>
/skinpower olay durum
/skinpower olay durdur
```

Eski kayıtlardaki Doğa sınıfı veri kaybetmeden Ay sınıfına dönüştürülür. PvP botu ve düello sistemi bulunmaz.
