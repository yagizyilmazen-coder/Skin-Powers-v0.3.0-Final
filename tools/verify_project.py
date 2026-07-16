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
    "build.gradle",
    "settings.gradle",
    "gradle.properties",
    ".github/workflows/build.yml",
    "README.md",
    "CHANGELOG_0.3.6.md",
    "LICENSE",
    "src/main/resources/fabric.mod.json",
    "src/main/resources/assets/skinpowers/icon.png",
    "src/main/resources/assets/skinpowers/lang/tr_tr.json",
    "src/main/resources/assets/skinpowers/lang/en_us.json",
    "src/main/java/com/yagiz/skinpowers/SkinPowersMod.java",
    "src/main/java/com/yagiz/skinpowers/PowerSystem.java",
    "src/client/java/com/yagiz/skinpowers/client/SkinPowersClient.java",
    "src/client/java/com/yagiz/skinpowers/client/SkinPowersSettingsScreen.java",
    "src/client/java/com/yagiz/skinpowers/client/SkinPowersModMenu.java",
]
EXPECTED_PROPERTIES = {
    "minecraft_version": "26.1.2",
    "loader_version": "0.19.3",
    "loom_version": "1.17-SNAPSHOT",
    "fabric_api_version": "0.154.2+26.1.2",
    "modmenu_version": "18.0.0",
    "mod_version": "0.3.6",
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
    "PowerClass.WATER": "Kaldırılan Su sınıfı kodda kalmamalı",
    "clearWidgets(": "26.1 ekranında riskli eski widget temizleme çağrısı kullanılmamalı",
    "player.hurtTime": "Doğa pasifinde erişilemeyen oyuncu alanı kullanılmamalı",
}
REQUIRED_SOURCE_SNIPPETS = {
    "PowerClass.NATURE": "Doğa sınıfı bağlantısı",
    "int count = 10;": "10 meteor",
    "ServerNetworking.sendScreenShake": "sunucu sarsıntı gönderimi",
    "adjustPreviousRotation": "görünür kamera sarsıntısı",
    "Made by Yankalan": "yapımcı imzası",
    "SkinPowersModMenu": "Mod Menu entegrasyonu",
    "Dikenli Tohum": "Doğa güçleri",
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
for snippet, explanation in REQUIRED_SOURCE_SNIPPETS.items():
    if snippet not in all_source:
        errors.append(f"Beklenen özellik kodu bulunamadı: {explanation} ({snippet})")

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
        for required_entrypoint in ("main", "client", "modmenu"):
            if required_entrypoint not in entrypoints:
                errors.append(f"fabric.mod.json {required_entrypoint} entrypoint eksik")
        depends = data.get("depends", {})
        if depends.get("minecraft") != "~26.1.2":
            errors.append("fabric.mod.json Minecraft bağımlılığı ~26.1.2 değil")
        if depends.get("java") != ">=25":
            errors.append("fabric.mod.json Java bağımlılığı >=25 değil")
        if data.get("authors") != ["Yankalan"]:
            errors.append("fabric.mod.json yazar bilgisi Yankalan değil")
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
    for required_text in [
        "java-version: '25'",
        "gradle-version: '9.5.1'",
        "gradle clean build",
        "tools/verify_project.py",
        "skinpowers-0.3.6-jar",
        "build/libs/skinpowers-0.3.6.jar",
    ]:
        if required_text not in workflow_text:
            errors.append(f"GitHub Actions içinde eksik ifade: {required_text}")

# Asset checks
icon = ROOT / "src/main/resources/assets/skinpowers/icon.png"
if icon.exists() and icon.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
    errors.append("Mod ikonu geçerli PNG imzasına sahip değil")
for name in ["warden", "flight", "fire", "nature"]:
    card = ROOT / f"src/main/resources/assets/skinpowers/textures/gui/cards/{name}.png"
    if not card.is_file():
        errors.append(f"Eksik sınıf kartı görseli: {card.relative_to(ROOT)}")
    elif card.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
        errors.append(f"Geçersiz PNG kartı: {card.relative_to(ROOT)}")

# Do not ship generated or obsolete garbage
for suffix in [".class", ".pyc", ".bak", ".tmp"]:
    for path in ROOT.rglob(f"*{suffix}"):
        errors.append(f"Paketlenmemesi gereken dosya: {path.relative_to(ROOT)}")
for unwanted_dir in ["build", ".gradle", "run", "out", "__pycache__"]:
    if (ROOT / unwanted_dir).exists():
        errors.append(f"Paketlenmemesi gereken klasör mevcut: {unwanted_dir}")
for obsolete in ["DESIGN.md", "FEATURE_STATUS.md", "FILE_MANIFEST.txt", "VALIDATION.md", "KURULUM_0.3.3.txt", "CHANGELOG_0.3.3.md"]:
    if (ROOT / obsolete).exists():
        errors.append(f"Eski/gereksiz kök dosyası kalmış: {obsolete}")

# Compile and run Minecraft-independent core logic.
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
        check(PowerClass.safeValueOf("nature") == PowerClass.NATURE, "nature class parse");
        check(PowerClass.safeValueOf("water") == PowerClass.NONE, "removed class fallback");
        check(PowerCatalog.xpCostForLevel(PowerClass.NATURE, 1) == 10, "nature level 1 xp");
        check(PowerCatalog.xpCostForLevel(PowerClass.FIRE, 1) == 5, "regular level 1 xp");
        check(PowerCatalog.xpCostForLevel(PowerClass.NATURE, 5) == 50, "nature level 5 xp");
        check(PowerCatalog.masteryStage(4) == 0, "mastery 4");
        check(PowerCatalog.masteryStage(5) == 1, "mastery 5");
        check(PowerCatalog.masteryStage(15) == 2, "mastery 15");
        check(PowerCatalog.masteryStage(30) == 3, "mastery 30");

        PlayerPowerData data = new PlayerPowerData();
        data.chooseClass(PowerClass.NATURE);
        data.chooseClass(PowerClass.FIRE);
        check(data.powerClass() == PowerClass.NATURE, "class must stay immutable");
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
        data.setNatureTreeUntil(500L);
        check(data.natureTreeUntil() == 500L, "nature timer");
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
        compiled = subprocess.run(
            [javac, "-encoding", "UTF-8", "-d", str(temp), *(str(p) for p in core_sources), str(test_path)],
            capture_output=True,
            text=True,
        )
        if compiled.returncode != 0:
            errors.append("Çekirdek Java derleme testi başarısız:\n" + compiled.stderr.strip())
            return
        executed = subprocess.run(
            [java, "-cp", str(temp), "com.yagiz.skinpowers.CoreLogicSmokeTest"],
            capture_output=True,
            text=True,
        )
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
    "Doğa sınıfı, 10 meteor, ekran sarsıntısı, Mod Menu ve çekirdek davranış testleri kontrol edildi."
)
