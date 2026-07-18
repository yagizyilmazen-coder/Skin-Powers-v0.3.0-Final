# Skin Powers 1.1.1 Doğrulama

1. `python3 tools/verify_project.py` çalıştırılır.
2. Bütün JSON dosyaları ve 20 büyü tanımı ayrıştırılır.
3. Büyülerin sınıf, eşya yuvası, uygun eşya ve özel etki bağlantıları denetlenir.
4. Tek sınıf büyüsü sınırını sağlayan `class_enchantments` etiketi doğrulanır.
5. `/skinpower buyu kitap <büyü>` test komutları doğrulanır.
6. Normal örs için gerçek `Enchantment` ve `Enchanted Book` yapısının kullanıldığı doğrulanır.
7. Bot ve düello kaynaklarının/komutlarının bulunmadığı doğrulanır.
8. Dünya olayı komutları ve önceki sınıf sistemleri korunur.
9. GitHub Actions Java 25 ve Gradle 9.5.1 ile gerçek Fabric JAR derlemesini yapar.

- Skin yeniden deneme/fallback akışı doğrulandı.
- Survival büyü etiketleri doğrulandı.
- Görünür büyü gövdeleri, güçlendirilmiş Antik Çöküş ve Kor Birikimi doğrulandı.
