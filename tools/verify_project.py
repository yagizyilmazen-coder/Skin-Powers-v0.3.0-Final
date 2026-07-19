#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION = "1.2.0"
MAX_CHECKS = 50
checks: list[str] = []
errors: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        return ""
    return path.read_text(encoding="utf-8")


def check(name: str, condition: bool, detail: str = "") -> None:
    if len(checks) >= MAX_CHECKS:
        return
    checks.append(name)
    if not condition:
        errors.append(f"{name}: {detail or 'başarısız'}")


def contains_all(source: str, *tokens: str) -> bool:
    return all(token in source for token in tokens)


props = read("gradle.properties")
workflow = read(".github/workflows/build.yml")
mod_json_text = read("src/main/resources/fabric.mod.json")
power_class = read("src/main/java/com/yagiz/skinpowers/PowerClass.java")
catalog = read("src/main/java/com/yagiz/skinpowers/PowerCatalog.java")
power_system = read("src/main/java/com/yagiz/skinpowers/PowerSystem.java")
expansion = read("src/main/java/com/yagiz/skinpowers/ExpansionPowerSystem.java")
network = read("src/main/java/com/yagiz/skinpowers/ServerNetworking.java")
mod = read("src/main/java/com/yagiz/skinpowers/SkinPowersMod.java")
awakening = read("src/main/java/com/yagiz/skinpowers/AwakeningSystem.java")
anomaly = read("src/main/java/com/yagiz/skinpowers/AnomalySystem.java")
commands = read("src/main/java/com/yagiz/skinpowers/SkinPowersCommands.java")
client_state = read("src/client/java/com/yagiz/skinpowers/client/ClientState.java")
client = read("src/client/java/com/yagiz/skinpowers/client/SkinPowersClient.java")
hud = read("src/client/java/com/yagiz/skinpowers/client/HudOverlay.java")
menu = read("src/client/java/com/yagiz/skinpowers/client/PowerMenuScreen.java")
selection = read("src/client/java/com/yagiz/skinpowers/client/SkinSelectionScreen.java")
analyzer = read("src/client/java/com/yagiz/skinpowers/client/SkinAnalyzer.java")
settings = read("src/client/java/com/yagiz/skinpowers/client/SkinPowersSettingsScreen.java")
tr_text = read("src/main/resources/assets/skinpowers/lang/tr_tr.json")
en_text = read("src/main/resources/assets/skinpowers/lang/en_us.json")
class_enchantment = read("src/main/java/com/yagiz/skinpowers/ClassEnchantmentSystem.java")

core_files = [
    "build.gradle", "gradle.properties", "settings.gradle", ".github/workflows/build.yml",
    "src/main/resources/fabric.mod.json", "src/main/java/com/yagiz/skinpowers/ExpansionPowerSystem.java",
    "src/client/java/com/yagiz/skinpowers/client/SkinSelectionScreen.java", "CHANGELOG_1.2.0.md",
]
check("1. Temel proje dosyaları", all((ROOT / p).is_file() for p in core_files))
check("2. Gradle sürüm numarası", f"mod_version={VERSION}" in props)
check("3. GitHub Actions artifact", f"skinpowers-{VERSION}-jar" in workflow and f"skinpowers-{VERSION}.jar" in workflow)
check("4. Java ve Gradle CI", "java-version: '25'" in workflow and "gradle-version: '9.5.1'" in workflow)
check("5. Fabric açıklaması", "yedi güç sınıfı" in mod_json_text and "20 sınıfa özel büyü" in mod_json_text)
check("6. Yeni sınıf enumları", contains_all(power_class, 'MAGNETIC("Manyetik")', 'SAND("Kum")'))
check("7. Yeni sınıf takma adları", contains_all(power_class, 'normalized.equals("MANYETIK")', 'normalized.equals("KUM")'))
check("8. Manyetik güç adları", contains_all(catalog, "Manyetik Çekim", "Kutup İtişi", "Demir Yumruk", "Metal Fırtınası", "Ray Topu", "Manyetik Kafes"))
check("9. Kum güç adları", contains_all(catalog, "Kum Mermisi", "Kum Dalgası", "Çöl Aynası", "Kum Zırhı", "Kum Mezarı", "Kum Devleri"))
check("10. Yeni sınıflar altı seviye", "powerClass == PowerClass.MAGNETIC || powerClass == PowerClass.SAND" in catalog and "? 6 : 5" in catalog)
check("11. Oyuncu sınıf komutları", 'selfClass("manyetik"' in commands and 'selfClass("kum"' in commands)
check("12. Yönetici sınıf komutları", 'targetClass("manyetik"' in commands and 'targetClass("kum"' in commands)
check("13. Güç sistemi delegasyonu", contains_all(power_system, "ExpansionPowerSystem.tickServer", "ExpansionPowerSystem.useMagnetic", "ExpansionPowerSystem.useSand"))
check("14. Manyetik gerçek gövdeler", contains_all(expansion, "Items.IRON_BLOCK", "Items.COPPER_BLOCK", "Items.ANVIL", "Items.IRON_BARS", "ItemEntity") and "Blocks.CHAIN" not in expansion and re.search(r"\\bItems\\.CHAIN\\b", expansion) is None)
check("15. Kum gerçek gövdeler", contains_all(expansion, "Items.SAND", "Items.SANDSTONE", "Items.CUT_SANDSTONE", "Items.CHISELED_SANDSTONE"))
check("16. Kum parçacığı yalnızca darbe vurgusu", "Ana gövde her zaman kum/kumtaşı ItemEntity'dir" in expansion)
check("17. Manyetik altı aktif güç", "public static boolean useMagnetic" in expansion and all(f"case {i} ->" in expansion for i in range(1, 7)))
check("18. Kum altı aktif güç", "public static boolean useSand" in expansion and expansion.count("case 6 ->") >= 2)
check("19. Manyetik combo", "Kutup Kıyameti" in catalog and "KUTUP KIYAMETİ" in power_system)
check("20. Kum combo", "Çöl Ezicisi" in catalog and "ÇÖL EZİCİSİ" in power_system)
check("21. Kum ekran paketi", 'new ClientEffectPayload("SAND_SCREEN"' in network)
check("22. Kum ekran istemci alıcısı", '"SAND_SCREEN".equalsIgnoreCase' in client and "startSandScreen" in client_state)
check("23. Kum ekranı dört saniye", "sendSandScreen" in expansion and ", 80)" in expansion)
check("24. Suya girince ekran temizleme", "client.player.isInWater()" in client and "clearSandScreen" in client)
check("25. Görsel kum ekranı", contains_all(hud, "drawSandScreenOverlay", "sandScreenTicks", "0x00C99745"))
check("26. Kum mezarı suyla kaçış", "target.isInWater()" in expansion and "tickSandGraves" in expansion)
check("27. Warden zırh gizleme", contains_all(power_system, "hideAmbushEquipment", "EquipmentSlot.HEAD", "EquipmentSlot.MAINHAND"))
check("28. Warden gizli eşya güvenliği", contains_all(power_system, "hiddenExtraItems", "restoreAmbushEquipment", "player.getInventory().add"))
check("29. Warden bağlantı kesme geri yükleme", "public static void handleDisconnect" in power_system and "PowerSystem.handleDisconnect" in network)
check("30. Yeni hasar sistemi kaydı", "ExpansionPowerSystem::allowDamage" in mod)
check("31. Sunucu kapanış temizliği", "ExpansionPowerSystem.clearAll" in mod and "public static void clearAll()" in expansion)
check("32. Manyetik Uyanış", "Manyetik Çekirdek" in awakening and "tickAwakening" in expansion)
check("33. Kum Uyanış", "Çölün Kalbi" in awakening and "finishAwakening" in expansion)
check("34. Anomali yeni güçleri kopyalar", "case MAGNETIC, SAND -> power >= 1 && power <= 6" in anomaly)
check("35. Skin analizinde yedi sınıf", "CLASS_COUNT = 7" in analyzer)
check("36. Skin analizinde Manyetik paleti", contains_all(analyzer, "MAGNETIC_COLORS", "metallicPixels"))
check("37. Skin analizinde Kum paleti", contains_all(analyzer, "SAND_COLORS", "sandPixels"))
check("38. Seçim ekranında yedi kart", "PowerClass.MAGNETIC, PowerClass.SAND" in selection and "new Button[CLASSES.length]" in selection)
check("39. Manyetik kart görseli", "drawMagneticForge" in selection)
check("40. Kum kart görseli", "drawDesertTemple" in selection)
check("41. Güç menüsü yeni temalar", "case MAGNETIC -> new int[]" in menu and "case SAND -> new int[]" in menu)
check("42. HUD yeni renk ve durumları", "case MAGNETIC -> 0xFFB8C5D1" in hud and "case SAND -> 0xFFE0B85A" in hud)
check("43. Türkçe sınıf çevirileri", '"class.skinpowers.magnetic": "MANYETİK"' in tr_text and '"class.skinpowers.sand": "KUM"' in tr_text)
check("44. İngilizce sınıf çevirileri", '"class.skinpowers.magnetic": "MAGNETIC"' in en_text and '"class.skinpowers.sand": "SAND"' in en_text)

enchantment_dir = ROOT / "src/main/resources/data/skinpowers/enchantment"
check("45. Yirmi büyü JSON'u", enchantment_dir.is_dir() and len(list(enchantment_dir.glob("*.json"))) == 20)
survival_tags = [ROOT / f"src/main/resources/data/minecraft/tags/enchantment/{name}.json" for name in ("tradeable", "treasure", "on_random_loot")]
check("46. Survival büyü etiketleri", all(p.is_file() and "#skinpowers:class_enchantments" in p.read_text(encoding="utf-8") for p in survival_tags))
check("47. Skin yeniden deneme ve ad fallback", contains_all(analyzer, "for (int attempt = 0; attempt < 3", "extractProfileName", "SkinPowers/1.2.0"))
check("48. Eski API hataları yok", "ParticleTypes.DRAGON_BREATH" not in class_enchantment and "SoundEvents.SHIELD_BLOCK," not in class_enchantment and "SoundEvents.GENERIC_EXPLODE," not in class_enchantment)

json_ok = True
for path in ROOT.rglob("*.json"):
    try:
        json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        json_ok = False
        errors.append(f"Bozuk JSON {path.relative_to(ROOT)}: {exc}")
check("49. Bütün JSON dosyaları", json_ok)
check("50. Kontrol sınırı", len(checks) == 49 and MAX_CHECKS == 50)

if errors:
    print("SKIN POWERS PROJE DENETİMİ BAŞARISIZ")
    for error in errors:
        print(f"[HATA] {error}")
    print(f"{len(checks)} kontrol çalıştırıldı; en fazla {MAX_CHECKS} kontrol sınırı uygulandı.")
    sys.exit(1)

print("SKIN POWERS PROJE DENETİMİ BAŞARILI")
print(f"{len(checks)} kontrol geçti.")
print("Manyetik ve Kum sınıfları, kum ekranı ve Warden ekipman gizleme düzeltmesi doğrulandı.")
