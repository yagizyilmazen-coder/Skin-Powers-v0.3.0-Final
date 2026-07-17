# Skin Powers 1.0.7 özellik durumu

## Hazır sistemler
- Warden, Kadim Ejderha, Ateş, Doğa ve Anomali sınıfları.
- Skin renk analizi ve yalnızca ilk iki öneriyi seçme kuralı.
- Altı seviyeye kadar sınıf güçleri, XP, ustalık ve cooldown kayıtları.
- Kadim Ejderha'nın altı gücü ve mor temalı pasifi.
- Beş sınıf için enerjiye bağlı Uyanış Formları.
- Animasyonlu sınıf kartları, sınıf temalı menüler ve istemci efektleri.
- Ayrıntılı Mod Menu istemci ayarları.
- Güvenli düellolar, güç çarpışmaları, dünya olayları ve trigger komutları.
- Anomali `?`, Hasar Mevcut Değil, V/X seçimi ve 404 sistemi.
- Eski `TIME/ZAMAN` kayıtlarının Anomaliye, eski `FLIGHT` kayıtlarının Kadim Ejderhaya uyumlu yüklenmesi.

## Teknik not
- Kod içindeki `FLIGHT` enum adı eski dünya kayıtlarını kırmamak için korunur; oyuncuya gösterilen ad Kadim Ejderha'dır.
- Mod Menu görüntü ayarları istemciye özeldir. Sunucu tarafı meteor blok hasarı yönetici komutuyla kontrol edilir.
- Gerçek son Minecraft/Fabric derlemesi GitHub Actions'ta Java 25 ve Gradle 9.5.1 ile yapılır.
