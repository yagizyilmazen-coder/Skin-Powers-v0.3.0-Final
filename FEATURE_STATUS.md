# Skin Powers 1.0.8 özellik durumu

## Hazır sistemler
- Warden, Kadim Ejderha, Ateş, Doğa ve Anomali sınıfları.
- Skin renk analizi ve yalnızca ilk iki öneriyi seçme kuralı.
- XP, ustalık, altı seviyeye kadar güçler ve kalıcı oyuncu kayıtları.
- Enerjiye bağlı beş Uyanış Formu.
- Güvenli oyuncu düelloları ve üst-orta düello paneli.
- Beş sınıflı PvP bot sistemi; Kolay, Normal, Zor ve Kâbus zorlukları.
- Bot canı, sınıfı, zorluğu ve Uyanışını gösteren panel.
- Yenilenmiş Anomali Kırık Adım yankıları, güç kopyalama, hasar depolama, Varlıktan Çıkar ve 404.
- Ateş ve Warden için geliştirilmiş savaş animasyonları ve Uyanış etkileri.
- Kadim Ejderha'nın altı gücü, Avcı Pençesi Z kaçışı ve mor görselleri.
- Güç çarpışmaları, dünya olayları, yönetici trigger komutları ve Mod Menu ayarları.

## Teknik notlar
- Kod içindeki `FLIGHT` enum adı eski Uçuş kayıtlarını bozmamak için korunur; oyunda Kadim Ejderha olarak görünür.
- PvP botları sahte kullanıcı hesabı değil, sunucunun yönettiği özel sınıf ustası savaş varlıklarıdır.
- Tam Minecraft/Fabric derlemesi GitHub Actions'ta Java 25 ile yapılır.
