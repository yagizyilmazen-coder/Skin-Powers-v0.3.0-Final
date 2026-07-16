#!/usr/bin/env python3
from pathlib import Path
import json
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = [
    "build.gradle", "settings.gradle", "gradle.properties",
    "src/main/resources/fabric.mod.json",
    "src/main/resources/assets/skinpowers/icon.png",
    "src/main/resources/assets/skinpowers/lang/tr_tr.json",
    "src/main/resources/assets/skinpowers/lang/en_us.json",
    "src/main/java/com/yagiz/skinpowers/SkinPowersMod.java",
    "src/client/java/com/yagiz/skinpowers/client/SkinPowersClient.java",
    ".github/workflows/build.yml",
    "README.md", "DESIGN.md", "VALIDATION.md",
]
EXPECTED_PROPERTIES = {
    "minecraft_version": "26.1.2",
    "loader_version": "0.19.3",
    "loom_version": "1.17-SNAPSHOT",
    "fabric_api_version": "0.154.2+26.1.2",
    "mod_version": "0.3.5",
    "maven_group": "com.yagiz",
    "archives_base_name": "skinpowers",
}
FORBIDDEN_SOURCE_SNIPPETS = {
    "MobEffects.MOVEMENT_SLOWDOWN": "26.1 adı MobEffects.SLOWNESS olmalı",
    "MobEffects.DAMAGE_BOOST": "26.1 adı MobEffects.STRENGTH olmalı",
    "MobEffects.DAMAGE_RESISTANCE": "26.1 adı MobEffects.RESISTANCE olmalı",
    "MobEffects.DIG_SLOWDOWN": "26.1 adı MobEffects.MINING_FATIGUE olmalı",
    ".clearFire()": "26.1 için setRemainingFireTicks(0) kullanılıyor",
    "hasPermissionLevel(": "26.1 permission API kullanılmalı",
    "currentScreen()": "26.1 alanı currentScreen kullanılmalı",
}

errors: list[str] = []

for rel in REQUIRED:
    if not (ROOT / rel).is_file():
        errors.append(f"Eksik zorunlu dosya: {rel}")

# JSON validity
for path in ROOT.rglob("*.json"):
    try:
        json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"Geçersiz JSON: {path.relative_to(ROOT)} -> {exc}")

# Java package paths and delimiter balance
java_files = sorted(ROOT.rglob("*.java"))
all_source = "\n".join(path.read_text(encoding="utf-8") for path in java_files)
for path in java_files:
    text = path.read_text(encoding="utf-8")
    package_match = re.search(r"^package\s+([\w.]+);", text, re.M)
    if not package_match:
        errors.append(f"Package satırı yok: {path.relative_to(ROOT)}")
    else:
        package_path = Path(*package_match.group(1).split("."))
        if not str(path.parent).replace("\\", "/").endswith(str(package_path).replace("\\", "/")):
            errors.append(f"Package/yol uyuşmuyor: {path.relative_to(ROOT)}")

    stripped = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)
    stripped = re.sub(r"'(?:\\.|[^'\\])'", "''", stripped)
    stripped = re.sub(r"//.*?$|/\*.*?\*/", "", stripped, flags=re.M | re.S)
    for opening, closing, name in [("{", "}", "süslü"), ("(", ")", "normal"), ("[", "]", "köşeli")]:
        if stripped.count(opening) != stripped.count(closing):
            errors.append(f"{name} parantez dengesi bozuk: {path.relative_to(ROOT)}")

for snippet, explanation in FORBIDDEN_SOURCE_SNIPPETS.items():
    if snippet in all_source:
        errors.append(f"Eski/riskli API kalıntısı bulundu: {snippet} — {explanation}")

# fabric.mod.json checks
fabric = ROOT / "src/main/resources/fabric.mod.json"
if fabric.exists():
    try:
        data = json.loads(fabric.read_text(encoding="utf-8"))
        if data.get("id") != "skinpowers":
            errors.append("fabric.mod.json id değeri skinpowers değil")
        if data.get("environment") != "*":
            errors.append("fabric.mod.json environment değeri '*' değil")
        entrypoints = data.get("entrypoints", {})
        if "main" not in entrypoints or "client" not in entrypoints:
            errors.append("fabric.mod.json main/client entrypoint eksik")
        depends = data.get("depends", {})
        if depends.get("minecraft") != "~26.1.2":
            errors.append("fabric.mod.json Minecraft bağımlılığı ~26.1.2 değil")
        if depends.get("java") != ">=25":
            errors.append("fabric.mod.json Java bağımlılığı >=25 değil")
    except Exception:
        pass

# gradle.properties exact versions
properties_path = ROOT / "gradle.properties"
if properties_path.exists():
    properties: dict[str, str] = {}
    for raw in properties_path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        properties[key.strip()] = value.strip()
    for key, expected in EXPECTED_PROPERTIES.items():
        actual = properties.get(key)
        if actual != expected:
            errors.append(f"gradle.properties {key}: beklenen {expected}, bulunan {actual!r}")

# Workflow should be independent from user's local Java/Gradle installation
workflow = ROOT / ".github/workflows/build.yml"
if workflow.exists():
    workflow_text = workflow.read_text(encoding="utf-8")
    for required_text in ["java-version: '25'", "gradle-version: '9.5.1'", "gradle clean build", "tools/verify_project.py"]:
        if required_text not in workflow_text:
            errors.append(f"GitHub Actions içinde eksik ifade: {required_text}")

# Asset checks
icon = ROOT / "src/main/resources/assets/skinpowers/icon.png"
if icon.exists() and icon.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
    errors.append("Mod ikonu geçerli PNG imzasına sahip değil")
for name in ["warden", "flight", "fire", "water"]:
    card = ROOT / f"src/main/resources/assets/skinpowers/textures/gui/cards/{name}.png"
    if not card.is_file():
        errors.append(f"Eksik sınıf kartı görseli: {card.relative_to(ROOT)}")
    elif card.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
        errors.append(f"Geçersiz PNG kartı: {card.relative_to(ROOT)}")

# Dense water visuals must remain present.
power_system = ROOT / "src/main/java/com/yagiz/skinpowers/PowerSystem.java"
if power_system.exists():
    water_visual_source = power_system.read_text(encoding="utf-8")
    for required_text in [
        "drawWaterOrbVisual",
        "drawWhirlpoolVisual",
        "drawWaterArmorVisual",
        "drawTsunamiVisual",
        "waterCount = Math.max",
        "ParticleTypes.CLOUD",
    ]:
        if required_text not in water_visual_source:
            errors.append(f"Yoğun Su görsel sisteminde eksik ifade: {required_text}")

# Four-card selection screen and compact-layout collision guards
selection_path = ROOT / "src/client/java/com/yagiz/skinpowers/client/SkinSelectionScreen.java"
if selection_path.exists():
    selection_text = selection_path.read_text(encoding="utf-8")
    for required_text in [
        '"WARDEN", "UÇUŞ", "ATEŞ", "SU"',
        'PowerClass.WATER',
        'drawAncientCity',
        'drawClouds',
        'drawLavaCave',
        'drawOcean',
        'boolean compact = width < 520 || height < 330',
    ]:
        if required_text not in selection_text:
            errors.append(f"Dört kartlı seçim ekranında eksik ifade: {required_text}")

# Mirror the layout math on small and large GUI sizes. This catches card/button/text overlap regressions.
for test_width, test_height in [(320, 240), (400, 300), (520, 300), (640, 360), (854, 480), (1280, 720)]:
    count = 4
    compact = test_width < 520 or test_height < 330
    gap = 4 if compact else max(5, min(10, test_width // 105))
    side_margin = 8 if compact else 20
    available_width = max(200, test_width - side_margin - gap * (count - 1))
    card_width = max(48, min(145, available_width // count))
    card_y = 44 if compact else 112
    max_height = max(118, test_height - card_y - 8)
    preferred_height = max(142, int(card_width * 2.15)) if compact else max(178, int(card_width * 1.62))
    card_height = min(max_height, preferred_height)
    total = card_width * count + gap * (count - 1)
    start_x = (test_width - total) // 2
    if not compact:
        card_y = max(112, test_height - card_height - 8)
    button_height = 18 if compact else 20
    button_top = card_y + card_height - button_height - 7
    show_subtitle = (not compact) and card_width >= 96
    text_reserve = 48 if show_subtitle else 34
    art_bottom = max(card_y + 34, button_top - text_reserve)
    score_y = art_bottom + (32 if show_subtitle else 18)
    if start_x < 0 or start_x + total > test_width:
        errors.append(f"Seçim kartları yatay sınırı aşıyor: {test_width}x{test_height}")
    if card_y < 40 or card_y + card_height > test_height - 7:
        errors.append(f"Seçim kartları dikey sınırı aşıyor: {test_width}x{test_height}")
    if score_y + 9 > button_top:
        errors.append(f"Seçim ekranında yazı/düğme çakışması: {test_width}x{test_height}")

for lang_name in ["tr_tr.json", "en_us.json"]:
    lang_path = ROOT / f"src/main/resources/assets/skinpowers/lang/{lang_name}"
    if lang_path.exists():
        lang = json.loads(lang_path.read_text(encoding="utf-8"))
        if "class.skinpowers.water" not in lang:
            errors.append(f"Dil dosyasında Su sınıfı eksik: {lang_name}")

# Do not ship generated garbage
for unwanted in [".class", ".pyc"]:
    for path in ROOT.rglob(f"*{unwanted}"):
        errors.append(f"Paketlenmemesi gereken üretilmiş dosya: {path.relative_to(ROOT)}")
for unwanted_dir in ["build", ".gradle", "run", "out", "__pycache__"]:
    if (ROOT / unwanted_dir).exists():
        errors.append(f"Paketlenmemesi gereken klasör mevcut: {unwanted_dir}")

# Compile and run the Minecraft-independent core logic as a smoke test.
def run_core_smoke_test() -> None:
    javac = shutil.which("javac")
    java = shutil.which("java")
    if not javac or not java:
        errors.append("Çekirdek test için javac/java bulunamadı")
        return

    core_sources = [
        ROOT / "src/main/java/com/yagiz/skinpowers/PowerClass.java",
        ROOT / "src/main/java/com/yagiz/skinpowers/PowerCatalog.java",
        ROOT / "src/main/java/com/yagiz/skinpowers/PlayerPowerData.java",
        ROOT / "src/main/java/com/yagiz/skinpowers/ModConfig.java",
    ]
    test_source = r'''
package com.yagiz.skinpowers;
public final class CoreLogicSmokeTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(PowerClass.safeValueOf("warden") == PowerClass.WARDEN, "class parse");
        check(PowerClass.safeValueOf("invalid") == PowerClass.NONE, "safe invalid class");
        check(PowerClass.safeValueOf("water") == PowerClass.WATER, "water class parse");
        check(PowerCatalog.xpCostForLevel(1) == 5, "level 1 xp");
        check(PowerCatalog.xpCostForLevel(PowerClass.WATER, 1) == 10, "water level 1 xp");
        check(PowerCatalog.xpCostForLevel(PowerClass.WATER, 2) == 20, "water level 2 xp");
        check(PowerCatalog.xpCostForLevel(5) == 50, "level 5 xp");
        check(PowerCatalog.masteryStage(4) == 0, "mastery 4");
        check(PowerCatalog.masteryStage(5) == 1, "mastery 5");
        check(PowerCatalog.masteryStage(14) == 1, "mastery 14");
        check(PowerCatalog.masteryStage(15) == 2, "mastery 15");
        check(PowerCatalog.masteryStage(30) == 3, "mastery 30");

        PlayerPowerData data = new PlayerPowerData();
        data.chooseClass(PowerClass.FIRE);
        data.chooseClass(PowerClass.WARDEN);
        check(data.powerClass() == PowerClass.FIRE, "class must be immutable after first choice");
        for (int i = 0; i < 7; i++) data.unlockNextLevel();
        check(data.unlockedLevel() == 5, "level cap");
        data.setSelectedPower(5);
        data.selectRelative(1);
        check(data.selectedPower() == 1, "selection wrap next");
        data.selectRelative(-1);
        check(data.selectedPower() == 5, "selection wrap previous");
        for (int i = 0; i < 30; i++) data.addMasteryUse(3);
        check(data.masteryStage(3) == 3, "mastery progression");
        data.setCooldown(3, 100L, 40);
        check(data.cooldownRemaining(3, 120L) == 20, "cooldown remaining");
        check(data.cooldownRemaining(3, 200L) == 0, "cooldown floor");
        data.togglePassive();
        data.toggleVision();
        data.setTemporaryElytraUntil(500L);
        data.setWardenHuntUntil(600L);
        data.setWaterArmorUntil(700L);
        check(data.passiveEnabled() && data.visionEnabled(), "toggles");
        check(data.temporaryElytraUntil() == 500L && data.wardenHuntUntil() == 600L, "timed powers");
        check(data.waterArmorUntil() == 700L, "water armor timer");
        data.reset();
        check(data.powerClass() == PowerClass.NONE && data.unlockedLevel() == 0, "reset");

        ModConfig config = new ModConfig();
        config.setMeteorBlockDamage(false);
        check(!config.meteorBlockDamage(), "config toggle");
    }
}
'''
    with tempfile.TemporaryDirectory(prefix="skinpowers-core-") as temp_dir:
        temp = Path(temp_dir)
        test_path = temp / "CoreLogicSmokeTest.java"
        test_path.write_text(test_source, encoding="utf-8")
        compile_cmd = [javac, "-encoding", "UTF-8", "-d", str(temp), *(str(p) for p in core_sources), str(test_path)]
        compiled = subprocess.run(compile_cmd, capture_output=True, text=True)
        if compiled.returncode != 0:
            errors.append("Çekirdek Java derleme testi başarısız:\n" + compiled.stderr.strip())
            return
        executed = subprocess.run([java, "-cp", str(temp), "com.yagiz.skinpowers.CoreLogicSmokeTest"], capture_output=True, text=True)
        if executed.returncode != 0:
            errors.append("Çekirdek Java çalışma testi başarısız:\n" + (executed.stderr or executed.stdout).strip())

run_core_smoke_test()

if errors:
    print("PROJE DENETİMİ BAŞARISIZ")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print(
    f"PROJE DENETİMİ BAŞARILI — {len(java_files)} Java dosyası; JSON, PNG, sürüm, "
    "GitHub Actions, eski API kalıntıları ve çekirdek davranış testleri kontrol edildi."
)
