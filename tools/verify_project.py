#!/usr/bin/env python3
from pathlib import Path
import json
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = "1.0.3"
REQUIRED = [
    "build.gradle",
    "settings.gradle",
    "gradle.properties",
    ".github/workflows/build.yml",
    "README.md",
    f"CHANGELOG_{VERSION}.md",
    "LICENSE",
    "src/main/resources/fabric.mod.json",
    "src/main/resources/assets/skinpowers/icon.png",
    "src/main/resources/assets/skinpowers/lang/tr_tr.json",
    "src/main/resources/assets/skinpowers/lang/en_us.json",
    "src/main/java/com/yagiz/skinpowers/SkinPowersMod.java",
    "src/main/java/com/yagiz/skinpowers/PowerSystem.java",
    "src/main/java/com/yagiz/skinpowers/AncientChargeSystem.java",
    "src/main/java/com/yagiz/skinpowers/SkinPowersCommands.java",
    "src/client/java/com/yagiz/skinpowers/client/SkinPowersClient.java",
    "src/client/java/com/yagiz/skinpowers/client/PowerMenuScreen.java",
    "src/client/java/com/yagiz/skinpowers/client/HudOverlay.java",
    "src/client/java/com/yagiz/skinpowers/client/SkinPowersSettingsScreen.java",
    "src/client/java/com/yagiz/skinpowers/client/SkinPowersModMenu.java",
]
EXPECTED_PROPERTIES = {
    "minecraft_version": "26.1.2",
    "loader_version": "0.19.3",
    "loom_version": "1.17-SNAPSHOT",
    "fabric_api_version": "0.154.2+26.1.2",
    "modmenu_version": "18.0.0",
    "mod_version": VERSION,
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
    "player.hurtTime": "erişilemeyen oyuncu alanı kullanılmamalı",
    "applyExhaustion(player, data, now, \"Antik Şehir gücü boşaldı": "Çöküş güç kullanıldığı anda başlamamalı",
}
REQUIRED_SOURCE_SNIPPETS = {
    '"Şarj Et Beni Antik Şehir"': "Warden altıncı güç adı",
    "WARDEN_ANCIENT_CHARGE_XP = 70": "70 XP bedeli",
    "public static final int MAX_CHARGE_TICKS = 20 * 20": "20 saniye üst sınırı",
    "public static final int EXHAUSTION_TICKS = 30 * 20": "30 saniye çöküş",
    "SELF_CHARGE_ANIMATION_TICKS = 40": "iki saniyelik kendi kendine şarj animasyonu",
    "SELF_CHARGE_HEALTH_COST = 6.0F": "üç kalplik kendi kendine şarj bedeli",
    "caster.isShiftKeyDown()": "çömelerek kendi kendine şarj",
    "drawPurpleHeart": "göğüs önündeki mor Antik Kalp",
    "grantMob": "moblara Antik Şehir gücü aktarımı",
    "ancientChargeCyclePresent": "güç kullanılsa da 20 saniyelik sayaç",
    "startSacrificedHeartRecovery": "feda edilen kalplerin yavaş geri dönüşü",
    "int count = charged ? 20 : 10;": "şarjlı 20 / normal 10 meteor",
    "AncientChargeSystem.consume": "tek kullanım hakkı",
    "beginAncientCharge": "cooldown dondurma",
    "frozenCooldownTicks": "cooldown saklama",
    "ParticleTypes.WITCH": "mor şarj görselleri",
    "clearPendingBeams": "sunucu kapanış temizliği",
    "Made by Yankalan": "yapımcı imzası",
    "SkinPowersModMenu": "Mod Menu entegrasyonu",
    "COMBO_TOGGLE": "K tuşu kombo komutu",
    "SONİK FAY": "Warden Sonik Fay kombosu",
    "CEHENNEM FELAKETİ": "Ateş Cehennem Felaketi kombosu",
    "DİKEN ORMANI": "Doğa Diken Ormanı kombosu",
    "GÖKSEL BOMBARDIMAN": "Uçuş Göksel Bombardıman kombosu",
    "Zamanın Sonu": "Zaman sınıfı nihai gücü",
    "Krono Mızrağı": "Zaman sınıfı görünür mızrağı",
    "case FLIGHT, FIRE, NATURE, TIME": "Antik Şehir Şarjının beş sınıf altyapısı",
    "HTTP_EXECUTOR": "skin ağ istekleri için ayrı iş parçacığı havuzu",
    "api.mojang.com/users/profiles/minecraft/": "oyuncu adından skin UUID yedek çözümü",
    "secondIndex()": "ikinci skin önerisi",
    "selectButton.setY": "ilk seçim ekranında düğmelerin kartlarla birlikte hareketi",
    "Commands.literal(\"skinpower\")": "tek /skinpower komut kökü",
    "data.changeClass(powerClass)": "komutla sınıf değiştirme",
    "height < 300 ? 62": "Warden altıncı satırı için kısa ekran yerleşimi",
    "target.setPos(prison.anchor.x": "Zaman Hapishanesinin ekran efektsiz sabitlemesi",
    "boolean ancientBoost = data.ancientChargeActive(now)": "pasif güçlerin Antik Şehir süresince güçlenmesi",
    "ClientUiRules.classChoiceAllowed": "birinci ve ikinci öneri seçim kuralı",
    "ClientUiRules.staggeredProgress": "son GUI öğelerinin de tamamlanan animasyonu",
    "button.active = rowProgress >= 0.85F": "Warden VI düğmesinin etkinleşmesi",
    "String description = PowerCatalog.powerDescription": "O menüsünde her güç satırında açıklama",
    "double burstRadius = AncientChargeSystem.radius(2.4": "Krono Mızrağı alan hasarı",
    "float rewindBonus = 2.0F": "Geri Sarma güçlendirmesi",
    "float releaseDamage = AncientChargeSystem.damage(8.0F": "Zaman Hapishanesi çıkış hasarı",
    "float baseDamage = AncientChargeSystem.damage(24.0F": "Zamanın Sonu final hasarı",
}

errors: list[str] = []

for rel in REQUIRED:
    if not (ROOT / rel).is_file():
        errors.append(f"Eksik zorunlu dosya: {rel}")

for path in ROOT.rglob("*.json"):
    try:
        json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"Geçersiz JSON: {path.relative_to(ROOT)} -> {exc}")

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
        errors.append(f"Eski/riskli davranış kalıntısı bulundu: {snippet} — {explanation}")
for snippet, explanation in REQUIRED_SOURCE_SNIPPETS.items():
    if snippet not in all_source:
        errors.append(f"Beklenen özellik kodu bulunamadı: {explanation} ({snippet})")

power_system_path = ROOT / "src/main/java/com/yagiz/skinpowers/PowerSystem.java"
if power_system_path.exists():
    power_source = power_system_path.read_text(encoding="utf-8")
    prison_start = power_source.find("private static void tickTimePrisons()")
    prison_end = power_source.find("private static void createTimeField", prison_start)
    prison_section = power_source[prison_start:prison_end] if prison_start >= 0 and prison_end > prison_start else ""
    if not prison_section:
        errors.append("Zaman Hapishanesi tick bölümü bulunamadı")
    elif "MobEffects.SLOWNESS" in prison_section or "MobEffects.NAUSEA" in prison_section:
        errors.append("Zaman Hapishanesi ekran/potion efekti kullanmamalı; hedef doğrudan sabitlenmeli")

fabric = ROOT / "src/main/resources/fabric.mod.json"
if fabric.exists():
    try:
        data = json.loads(fabric.read_text(encoding="utf-8"))
        if data.get("id") != "skinpowers": errors.append("fabric.mod.json id değeri skinpowers değil")
        if data.get("environment") != "*": errors.append("fabric.mod.json environment değeri '*' değil")
        entrypoints = data.get("entrypoints", {})
        for key in ("main", "client", "modmenu"):
            if key not in entrypoints: errors.append(f"fabric.mod.json {key} entrypoint eksik")
        depends = data.get("depends", {})
        if depends.get("minecraft") != "~26.1.2": errors.append("fabric.mod.json Minecraft bağımlılığı ~26.1.2 değil")
        if depends.get("java") != ">=25": errors.append("fabric.mod.json Java bağımlılığı >=25 değil")
        if data.get("authors") != ["Yankalan"]: errors.append("fabric.mod.json yazar bilgisi Yankalan değil")
    except Exception:
        pass

properties_path = ROOT / "gradle.properties"
if properties_path.exists():
    properties: dict[str, str] = {}
    for raw in properties_path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line: continue
        key, value = line.split("=", 1)
        properties[key.strip()] = value.strip()
    for key, expected in EXPECTED_PROPERTIES.items():
        if properties.get(key) != expected:
            errors.append(f"gradle.properties {key}: beklenen {expected}, bulunan {properties.get(key)!r}")

workflow = ROOT / ".github/workflows/build.yml"
if workflow.exists():
    workflow_text = workflow.read_text(encoding="utf-8")
    for required_text in [
        "java-version: '25'",
        "gradle-version: '9.5.1'",
        "gradle clean build",
        "tools/verify_project.py",
        f"skinpowers-{VERSION}-jar",
        f"build/libs/skinpowers-{VERSION}.jar",
    ]:
        if required_text not in workflow_text:
            errors.append(f"GitHub Actions içinde eksik ifade: {required_text}")

icon = ROOT / "src/main/resources/assets/skinpowers/icon.png"
if icon.exists() and icon.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
    errors.append("Mod ikonu geçerli PNG imzasına sahip değil")
for name in ["warden", "flight", "fire", "nature", "time"]:
    card = ROOT / f"src/main/resources/assets/skinpowers/textures/gui/cards/{name}.png"
    if not card.is_file():
        errors.append(f"Eksik sınıf kartı görseli: {card.relative_to(ROOT)}")
    elif card.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
        errors.append(f"Geçersiz PNG kartı: {card.relative_to(ROOT)}")

for suffix in [".class", ".pyc", ".bak", ".bakwork", ".tmp"]:
    for path in ROOT.rglob(f"*{suffix}"):
        errors.append(f"Paketlenmemesi gereken dosya: {path.relative_to(ROOT)}")
for unwanted_dir in ["build", ".gradle", "run", "out", "__pycache__"]:
    if (ROOT / unwanted_dir).exists():
        errors.append(f"Paketlenmemesi gereken klasör mevcut: {unwanted_dir}")
for obsolete in [
    "DESIGN.md", "FEATURE_STATUS.md", "FILE_MANIFEST.txt", "VALIDATION.md",
    "KURULUM_0.3.3.txt", "CHANGELOG_0.3.3.md", "CHANGELOG_0.3.6.md", "CHANGELOG_0.3.7.md", "CHANGELOG_4.0.0.md", "CHANGELOG_4.1.0.md",
]:
    if (ROOT / obsolete).exists():
        errors.append(f"Eski/gereksiz kök dosyası kalmış: {obsolete}")


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
        ROOT / "src/client/java/com/yagiz/skinpowers/client/ClientUiRules.java",
    ]
    test_source = r'''
package com.yagiz.skinpowers;
import com.yagiz.skinpowers.client.ClientUiRules;
public final class CoreLogicSmokeTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(PowerCatalog.maxLevel(PowerClass.WARDEN) == 6, "warden level cap");
        check(PowerCatalog.maxLevel(PowerClass.TIME) == 5, "time level cap");
        check("Zamanın Sonu".equals(PowerCatalog.powerName(PowerClass.TIME, 5)), "time ultimate name");
        check(PowerCatalog.xpCostForLevel(PowerClass.WARDEN, 6) == 70, "warden sixth xp");
        check(PowerCatalog.comboStarterPower(PowerClass.FIRE) == 4, "fire combo starter");
        check(PowerCatalog.comboFinisherPower(PowerClass.FIRE) == 5, "fire combo finisher");
        check("Cehennem Felaketi".equals(PowerCatalog.comboName(PowerClass.FIRE)), "fire combo name");
        check("Şarj Et Beni Antik Şehir".equals(PowerCatalog.powerName(PowerClass.WARDEN, 6)), "sixth name");
        for (int level = 1; level <= 5; level++) {
            check(!PowerCatalog.powerDescription(PowerClass.TIME, level).isBlank(), "time description " + level);
        }
        check(ClientUiRules.classChoiceAllowed(true, true, 4, 5, 1, 4), "second recommendation selectable");
        check(!ClientUiRules.classChoiceAllowed(true, true, 3, 5, 1, 4), "non-recommended class locked");
        check(ClientUiRules.staggeredProgress(1.0F, 4, 5, 0.34F) == 1.0F, "last class card completes");
        check(ClientUiRules.staggeredProgress(1.0F, 5, 6, 0.30F) == 1.0F, "warden sixth row completes");

        PlayerPowerData data = new PlayerPowerData();
        data.chooseClass(PowerClass.TIME);
        data.unlockNextLevel();
        check(data.changeClass(PowerClass.WARDEN), "command class change");
        check(data.powerClass() == PowerClass.WARDEN && data.unlockedLevel() == 0, "class change resets progression");
        for (int i = 0; i < 8; i++) data.unlockNextLevel();
        check(data.toggleComboMode(), "combo mode enabled");
        data.beginCombo(2, 100L, 80, 1.0, 2.0, 3.0, true);
        check(data.comboActive(120L), "combo active");
        check(data.comboTargetValid() && data.comboTargetX() == 1.0, "combo target");
        data.clearCombo();
        check(!data.comboActive(120L), "combo cleared");
        data.setCooldown(1, 100L, 50);
        data.setCooldown(5, 100L, 70);
        data.setCooldown(6, 100L, 90);
        data.beginAncientCharge(110L, 999, true);
        check(data.ancientChargeUntil() == 510L, "charge max 20 seconds");
        check(data.ancientChargeReady(110L), "charge right ready");
        check(data.selfSacrificeActive(), "self sacrifice flag");
        check(data.cooldownRemaining(1, 110L) == 0, "regular cooldown disabled");
        check(data.cooldownRemaining(6, 110L) == 80, "sixth cooldown protected");

        data.setCooldown(5, 110L, 200);
        data.consumeAncientCharge(120L, 5);
        check(data.ancientChargeActive(120L), "20 second window continues after use");
        check(!data.ancientChargeReady(120L), "single use right consumed");
        check(data.ancientChargeUsedPower() == 5, "used power remembered");
        check(data.cooldownRemaining(1, 120L) == 40, "old cooldown restored after use");
        check(data.cooldownRemaining(5, 120L) == 190, "used power new cooldown kept");
        check(data.cooldownRemaining(6, 120L) == 70, "sixth cooldown continued");

        data.finishAncientCharge(510L);
        check(!data.ancientChargeCyclePresent(), "charge cycle finished at timer end");
        data.startSacrificedHeartRecovery(510L);
        check(data.sacrificedHealthPointsToRecover() == 6, "three hearts recover in six half-heart steps");
        data.advanceSacrificedHeartRecovery(630L);
        check(data.sacrificedHealthPointsToRecover() == 5, "heart recovery advances");

        PlayerPowerData expiry = new PlayerPowerData();
        expiry.chooseClass(PowerClass.FIRE);
        for (int i = 0; i < 5; i++) expiry.unlockNextLevel();
        expiry.setCooldown(4, 200L, 60);
        expiry.beginAncientCharge(210L, 200);
        check(expiry.cooldownRemaining(4, 210L) == 0, "expiry charge bypass");
        expiry.finishAncientCharge(410L);
        check(expiry.cooldownRemaining(4, 410L) == 50, "unused expiry restores frozen cooldown");

        for (int i = 0; i < 30; i++) data.addMasteryUse(6);
        check(data.masteryStage(6) == 3, "sixth mastery");
        data.reset();
        check(data.powerClass() == PowerClass.NONE && data.unlockedLevel() == 0, "reset");
    }
}
'''
    with tempfile.TemporaryDirectory(prefix="skinpowers-core-") as temp_dir:
        temp = Path(temp_dir)
        test_path = temp / "CoreLogicSmokeTest.java"
        test_path.write_text(test_source, encoding="utf-8")
        compiled = subprocess.run(
            [javac, "-encoding", "UTF-8", "-d", str(temp), *(str(p) for p in core_sources), str(test_path)],
            capture_output=True, text=True,
        )
        if compiled.returncode != 0:
            errors.append("Çekirdek Java derleme testi başarısız:\n" + compiled.stderr.strip())
            return
        executed = subprocess.run(
            [java, "-cp", str(temp), "com.yagiz.skinpowers.CoreLogicSmokeTest"],
            capture_output=True, text=True,
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
    "Warden 6. güç, hedef olmasa da ışın, mob şarjı, iki saniyelik Antik Kalp animasyonu, "
    "üç kalp bedeli, 20 saniye sonunda çöküş, yavaş kalp dönüşü, cooldown dondurma, "
    "20 mor meteor, K kombo modu, beş kombinasyon, Zaman sınıfı, skin öneri sıralaması, dinamik HUD yerleşimi, komutlar ve çekirdek davranış testleri kontrol edildi."
)
