#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION = "1.3.0"
MAX_CHECKS = 50
checks: list[str] = []
errors: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def check(name: str, condition: bool, detail: str = "") -> None:
    if len(checks) >= MAX_CHECKS:
        return
    checks.append(name)
    if not condition:
        errors.append(f"{name}: {detail or 'başarısız'}")


def has_all(source: str, *tokens: str) -> bool:
    return all(token in source for token in tokens)


props = read("gradle.properties")
workflow = read(".github/workflows/build.yml")
mod_json = read("src/main/resources/fabric.mod.json")
power_class = read("src/main/java/com/yagiz/skinpowers/PowerClass.java")
catalog = read("src/main/java/com/yagiz/skinpowers/PowerCatalog.java")
power_system = read("src/main/java/com/yagiz/skinpowers/PowerSystem.java")
moon = read("src/main/java/com/yagiz/skinpowers/MoonPowerSystem.java")
anomaly = read("src/main/java/com/yagiz/skinpowers/AnomalySystem.java")
world = read("src/main/java/com/yagiz/skinpowers/WorldEventSystem.java")
commands = read("src/main/java/com/yagiz/skinpowers/SkinPowersCommands.java")
mod = read("src/main/java/com/yagiz/skinpowers/SkinPowersMod.java")
network = read("src/main/java/com/yagiz/skinpowers/ServerNetworking.java")
awakening = read("src/main/java/com/yagiz/skinpowers/AwakeningSystem.java")
charge = read("src/main/java/com/yagiz/skinpowers/AncientChargeSystem.java")
data_store = read("src/main/java/com/yagiz/skinpowers/PlayerDataStore.java")
class_enchantments = read("src/main/java/com/yagiz/skinpowers/ClassEnchantments.java")
enchantment_system = read("src/main/java/com/yagiz/skinpowers/ClassEnchantmentSystem.java")
analyzer = read("src/client/java/com/yagiz/skinpowers/client/SkinAnalyzer.java")
selection = read("src/client/java/com/yagiz/skinpowers/client/SkinSelectionScreen.java")
menu = read("src/client/java/com/yagiz/skinpowers/client/PowerMenuScreen.java")
hud = read("src/client/java/com/yagiz/skinpowers/client/HudOverlay.java")
settings = read("src/client/java/com/yagiz/skinpowers/client/SkinPowersSettingsScreen.java")
tr_text = read("src/main/resources/assets/skinpowers/lang/tr_tr.json")
en_text = read("src/main/resources/assets/skinpowers/lang/en_us.json")
expansion = read("src/main/java/com/yagiz/skinpowers/ExpansionPowerSystem.java")

core = [
    "build.gradle", "gradle.properties", "settings.gradle", ".github/workflows/build.yml",
    "src/main/resources/fabric.mod.json", "src/main/java/com/yagiz/skinpowers/MoonPowerSystem.java",
    "src/main/java/com/yagiz/skinpowers/AnomalySystem.java", "CHANGELOG_1.3.0.md",
]
check("1. Temel proje dosyaları", all((ROOT / p).is_file() for p in core))
check("2. Sürüm numarası", f"mod_version={VERSION}" in props)
check("3. GitHub artifact sürümü", f"skinpowers-{VERSION}-jar" in workflow and f"skinpowers-{VERSION}.jar" in workflow)
check("4. Java 25 ve Gradle 9.5.1", "java-version: '25'" in workflow and "gradle-version: '9.5.1'" in workflow)
check("5. Fabric hedefleri", 'minecraft_version=26.1.2' in props and 'loader_version=0.19.3' in props)
check("6. Ay enumu", 'MOON("Ay")' in power_class and 'NATURE("' not in power_class)
check("7. Eski Doğa kayıt göçü", has_all(data_store, 'equalsIgnoreCase("NATURE")', '"MOON"'))
check("8. Yedi aktif sınıf", all(token in power_class for token in ('WARDEN(', 'FLIGHT(', 'FIRE(', 'MOON(', 'ANOMALY(', 'MAGNETIC(', 'SAND(')))
check("9. Ay altı güç adı", has_all(catalog, "Ay Halkası", "Ay Mührü", "Yerçekimi Baskısı", "Ay Aynası", "Tutulma Hükmü", "Dolunay Canavarı"))
check("10. Ay altı seviye", 'powerClass == PowerClass.MOON' in catalog and '? 6 : 5' in catalog)
check("11. Ay komutu", 'selfClass("ay", PowerClass.MOON)' in commands and 'targetClass("ay", PowerClass.MOON)' in commands)
check("12. Eski Doğa komutu kapalı", 'selfClass("doga"' not in commands and 'targetClass("doga"' not in commands)
check("13. Ay trigger komutları", all(f'triggerLiteral("{name}")' in commands for name in ("moon_crescent", "moon_step", "moon_gravity", "moon_mirror", "moon_eclipse", "moon_beast")))
check("14. Güç sistemine Ay delegasyonu", has_all(power_system, "MoonPowerSystem.tickServer", "case MOON -> MoonPowerSystem.tickPlayer", "case MOON -> MoonPowerSystem.use"))
check("15. Beyaz Ay Halkası gidip dönüyor", has_all(moon, "class CrescentAttack", "attack.returning", "toOwner.normalize()", "drawWhiteLunarRing", "List.of()"))
check("16. Ay Mührü iki aşamalı saldırı", has_all(moon, "Ay Mührü hedefe kilitlendi", "tickLunarSeals", "seal.triggerTick + 16L"))
check("17. Yerçekimi alanı", has_all(moon, "GRAVITY_FIELDS", "tickGravityFields", "motion.y - 0.32"))
check("18. Ay Aynası mermi yansıtma", has_all(moon, "MOON_MIRRORS", "projectile.setOwner(player)", "SoundEvents.SHIELD_BLOCK.value()"))
check("19. Güçlendirilmiş Tutulma Hükmü", has_all(moon, "ECLIPSE_FIELDS", "tickEclipseFields", "MobEffects.WEAKNESS", "drawMoonBeam"))
check("20. Güçlendirilmiş Dolunay Canavarı", has_all(moon, "FULL_MOON_BEASTS", "beastSwipe", "positionBeast", "beast.phase = 4"))
check("21. Ay görünür eşya gövdeleri", has_all(moon, "ItemEntity", "setNeverPickUp", "setUnlimitedLifetime", "moveVisualSmoothly"))
check("22. Ay ölüm temizliği", has_all(moon, "afterDeath", "clearOwner", "handleDisconnect", "clearAll"))
check("23. Ay hasar olay kaydı", "MoonPowerSystem::allowDamage" in mod and "MoonPowerSystem::afterDeath" in mod)
check("24. Ay bağlantı kesme temizliği", "MoonPowerSystem.handleDisconnect" in network)
check("25. Tam Tutulma Uyanışı", 'case MOON' in awakening and '"Tam Tutulma"' in awakening)
check("26. Antik şarj Ay desteği", 'case MOON -> true' in charge and 'case MOON -> ParticleTypes.END_ROD' in charge)
check("27. Kızıl Ay olayı", has_all(world, 'RED_MOON("Kızıl Ay"', 'case RED_MOON', 'spawnMoonPillar'))
check("28. Ay olay komutu", 'case "ay", "moon" -> RED_MOON' in world and 'worldEventLiteral("ay")' in commands)
check("29. Doğa dünya olayı kaldırıldı", 'ANCIENT_BLOOM' not in world and 'case "doga"' not in world)
check("30. Ay olayı geçici blok temizliği", has_all(world, "TemporaryMoonPillar", "clearMoonPillars", "Blocks.AMETHYST_BLOCK"))
check("31. Anomali görünür kopyaları", has_all(anomaly, "spawnGlitchFigure", "ItemEntity", "PendingEcho"))
check("32. REVERSED bildirimi", 'Component.literal("REVERSED")' in anomaly and 'spawnVisibleRing' in anomaly)
check("33. Kopya 10 saniye", has_all(anomaly, "COPIED_EXPIRES", "now + 200L", "10 saniye"))
check("34. Sistem Çökmesi iki kopya", 'setCopiedPowerUses(2)' in anomaly)
check("35. Hasar küpleri", has_all(anomaly, "anomalyStoredDamage", "Items.REDSTONE", "Items.ENDER_EYE"))
check("36. Varlıktan Çıkar görünür beden", "spawnGlitchFigure(level, player.getUUID(), target.position()" in anomaly)
check("37. Görünür 404 gövdesi", has_all(anomaly, "spawn404Body", "404 ALANI: GERÇEKLİK BULUNAMADI", "AnomalyVisual"))
check("38. Anomali Ay güçlerini kopyalar", 'case MOON -> power >= 1 && power <= 6' in anomaly)
check("39. Ay skin paleti", has_all(analyzer, "MOON_COLORS", "double moon", "CLASS_COUNT = 7"))
check("40. Ay seçim kartı", has_all(selection, '"AY"', "PowerClass.MOON", "drawMoon"))
check("41. Ay güç menüsü teması", 'case MOON -> new int[]' in menu and 'powerClass == PowerClass.MOON' in menu)
check("42. Ay HUD ve mavi Uyanış yazısı", 'case MOON -> 0xFFDCE6FF' in hud and 'Ay Aynası' in hud and '0xFF4FA8FF' in hud)
check("43. Ay çevirileri", '"class.skinpowers.moon": "AY"' in tr_text and '"class.skinpowers.moon": "MOON"' in en_text)
check("44. Ay büyü anahtarları", has_all(class_enchantments, "LUNAR_WOUND", "MOON_SIGHT", "MOON_STEP", "MOON_MIRROR"))
check("45. Ay büyüleri ve gerçek Meteor Düşüşü", has_all(enchantment_system, "ClassEnchantments.LUNAR_WOUND", "ClassEnchantments.MOON_SIGHT", "ClassEnchantments.MOON_STEP", "ClassEnchantments.MOON_MIRROR", "PowerSystem.scheduleEnchantmentMeteor"))

enchantment_dir = ROOT / "src/main/resources/data/skinpowers/enchantment"
moon_enchants = {"hilal_yarasi.json", "ay_gozu.json", "ay_adimi.json", "ay_aynasi.json"}
check("46. Yirmi büyü ve dört Ay JSON'u", enchantment_dir.is_dir() and len(list(enchantment_dir.glob("*.json"))) == 20 and moon_enchants <= {p.name for p in enchantment_dir.glob("*.json")})
check("47. Eski Doğa büyüleri silindi", not any((enchantment_dir / name).exists() for name in ("kok_bagi.json", "can_filizi.json", "orman_sicrayisi.json", "dikenli_savunma.json")))
survival = [ROOT / f"src/main/resources/data/minecraft/tags/enchantment/{name}.json" for name in ("tradeable", "treasure", "on_random_loot")]
recipe_dir = ROOT / "src/main/resources/data/skinpowers/recipe"
recipe_files = sorted(recipe_dir.glob("craft_*.json")) if recipe_dir.is_dir() else []
recipe_ok = len(recipe_files) == 20
for recipe_file in recipe_files:
    try:
        recipe = json.loads(recipe_file.read_text(encoding="utf-8"))
        components = recipe.get("result", {}).get("components", {}).get("minecraft:stored_enchantments", {})
        recipe_ok = recipe_ok and recipe.get("type") == "minecraft:crafting_shapeless" and len(components) == 1
    except Exception:
        recipe_ok = False
unlock_advancement = ROOT / "src/main/resources/data/skinpowers/advancement/recipes/survival_buyu_kitaplari.json"
check(
    "48. Survival craft, keşif ve ticaret sistemi",
    all(p.is_file() and "#skinpowers:class_enchantments" in p.read_text(encoding="utf-8") for p in survival)
    and recipe_ok
    and unlock_advancement.is_file()
    and len(json.loads(unlock_advancement.read_text(encoding="utf-8")).get("rewards", {}).get("recipes", [])) == 20,
)
check(
    "49. Kritik düzeltmeler ve havadaki gövdeler",
    has_all(expansion, "MAGNETIC_PULLS", "afterDeath")
    and "Blocks.CHAIN" not in expansion
    and re.search(r"\bItems\.CHAIN\b", expansion) is None
    and has_all(moon, "spawnAirTrail", "ProjectileEscort", "moonFlightItems", "tickProjectileEscorts")
    and has_all(anomaly, "ensureProjectileEscort", "ProjectileEscort", "tickProjectileEscorts"),
    "Manyetik/Kum uyumluluğu veya Ay-Anomali uçan gövdeleri eksik."
)

json_ok = True
for path in ROOT.rglob("*.json"):
    try:
        json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        json_ok = False
        errors.append(f"Bozuk JSON {path.relative_to(ROOT)}: {exc}")
check("50. JSON ve kontrol sınırı", json_ok and MAX_CHECKS == 50 and len(checks) == 49)

if len(checks) != MAX_CHECKS:
    errors.append(f"Kontrol sayısı {len(checks)}; beklenen {MAX_CHECKS}.")

if errors:
    print("SKIN POWERS PROJE DENETİMİ BAŞARISIZ")
    for error in errors:
        print(f"[HATA] {error}")
    print(f"{len(checks)} kontrol çalıştırıldı; en fazla {MAX_CHECKS} kontrol sınırı uygulandı.")
    sys.exit(1)

print("SKIN POWERS PROJE DENETİMİ BAŞARILI")
print(f"{len(checks)} kontrol geçti.")
print("Ay sınıfı, Anomali 2.0, görünür saldırılar ve 20 survival büyü kitabı tarifi doğrulandı.")
