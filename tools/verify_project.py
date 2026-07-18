#!/usr/bin/env python3
from __future__ import annotations

import json
import shutil
import struct
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION = "1.1.1"
errors: list[str] = []
checks: list[str] = []


def ok(message: str) -> None:
    checks.append(message)


def fail(message: str) -> None:
    errors.append(message)


def require_file(relative: str) -> Path:
    path = ROOT / relative
    if not path.is_file():
        fail(f"Eksik dosya: {relative}")
    else:
        ok(f"Dosya: {relative}")
    return path


def text(relative: str) -> str:
    path = require_file(relative)
    return path.read_text(encoding="utf-8") if path.is_file() else ""


required_files = [
    "build.gradle", "gradle.properties", "settings.gradle", "README.md", "VALIDATION.md", "FEATURE_STATUS.md",
    "CHANGELOG_1.1.1.md", ".github/workflows/build.yml", "src/main/resources/fabric.mod.json",
    "src/main/resources/assets/skinpowers/lang/tr_tr.json",
    "src/main/resources/assets/skinpowers/lang/en_us.json",
    "src/main/java/com/yagiz/skinpowers/AwakeningSystem.java",
    "src/main/java/com/yagiz/skinpowers/PowerCollisionSystem.java",
    "src/main/java/com/yagiz/skinpowers/WorldEventSystem.java",
    "src/main/java/com/yagiz/skinpowers/ClassEnchantments.java",
    "src/main/java/com/yagiz/skinpowers/ClassEnchantmentSystem.java",
    "src/main/resources/data/skinpowers/tags/enchantment/class_enchantments.json",
    "src/main/resources/data/minecraft/tags/enchantment/tradeable.json",
    "src/main/resources/data/minecraft/tags/enchantment/treasure.json",
    "src/main/resources/data/minecraft/tags/enchantment/on_random_loot.json",
    "src/client/java/com/yagiz/skinpowers/client/SkinPowersSettingsScreen.java",
]
for file_name in required_files:
    require_file(file_name)

# JSON doğrulaması
for path in ROOT.rglob("*.json"):
    try:
        json.loads(path.read_text(encoding="utf-8"))
        ok(f"JSON: {path.relative_to(ROOT)}")
    except Exception as exc:
        fail(f"Bozuk JSON {path.relative_to(ROOT)}: {exc}")

props = text("gradle.properties")
workflow = text(".github/workflows/build.yml")
mod_json = text("src/main/resources/fabric.mod.json")
if f"mod_version={VERSION}" not in props:
    fail(f"gradle.properties sürümü {VERSION} değil")
if f"skinpowers-{VERSION}-jar" not in workflow or f"skinpowers-{VERSION}.jar" not in workflow:
    fail(f"GitHub Actions artifact/JAR adı {VERSION} değil")
if "java-version: '25'" not in workflow or "gradle-version: '9.5.1'" not in workflow:
    fail("GitHub Actions Java 25 / Gradle 9.5.1 ayarı eksik")
if "20 sınıfa özel büyü" not in mod_json or "normal örste" not in mod_json:
    fail("fabric.mod.json 1.1.1 büyü açıklamasını içermiyor")

power_class = text("src/main/java/com/yagiz/skinpowers/PowerClass.java")
catalog = text("src/main/java/com/yagiz/skinpowers/PowerCatalog.java")
data = text("src/main/java/com/yagiz/skinpowers/PlayerPowerData.java")
anomaly = text("src/main/java/com/yagiz/skinpowers/AnomalySystem.java")
awakening = text("src/main/java/com/yagiz/skinpowers/AwakeningSystem.java")
power_system = text("src/main/java/com/yagiz/skinpowers/PowerSystem.java")
network = text("src/main/java/com/yagiz/skinpowers/ServerNetworking.java")
client = text("src/client/java/com/yagiz/skinpowers/client/SkinPowersClient.java")
client_state = text("src/client/java/com/yagiz/skinpowers/client/ClientState.java")
client_config = text("src/client/java/com/yagiz/skinpowers/client/ClientConfig.java")
settings = text("src/client/java/com/yagiz/skinpowers/client/SkinPowersSettingsScreen.java")
hud = text("src/client/java/com/yagiz/skinpowers/client/HudOverlay.java")
menu = text("src/client/java/com/yagiz/skinpowers/client/PowerMenuScreen.java")
selection = text("src/client/java/com/yagiz/skinpowers/client/SkinSelectionScreen.java")
analyzer = text("src/client/java/com/yagiz/skinpowers/client/SkinAnalyzer.java")
commands = text("src/main/java/com/yagiz/skinpowers/SkinPowersCommands.java")
mod = text("src/main/java/com/yagiz/skinpowers/SkinPowersMod.java")
store = text("src/main/java/com/yagiz/skinpowers/PlayerDataStore.java")
collision = text("src/main/java/com/yagiz/skinpowers/PowerCollisionSystem.java")
world_event = text("src/main/java/com/yagiz/skinpowers/WorldEventSystem.java")
class_enchantments = text("src/main/java/com/yagiz/skinpowers/ClassEnchantments.java")
class_enchantment_system = text("src/main/java/com/yagiz/skinpowers/ClassEnchantmentSystem.java")

required_tokens = {
    power_class: [
        'FLIGHT("Kadim Ejderha")', 'normalized.equals("EJDERHA")', 'normalized.equals("TIME")', "return ANOMALY"
    ],
    catalog: [
        "Kuyruk Kasırgası", "Ejderha Nefesi", "Kadim Pullar", "Avcı Pençesi", "Kadim Kükreme",
        "Ejderha Hükümdarı", "DRAGON_XP_COSTS", "Mor Ejderha Fırtınası",
        "Kırık Adım", '"?"', "404: Gerçeklik Bulunamadı", "gecikmeli patlayan bozuk kopyalar", "Derinlik Pususu"
    ],
    data: [
        "dragonScalesUntil", "dragonScaleCharges", "dragonFormUntil", "awakeningEnergy", "classAwakeningUntil",
        "beginClassAwakening", "Math.round(energy * 4.8F)", "copiedPowerClass", "copiedPowerUses", "anomalyStoredDamage"
    ],
    awakening: [
        "allowDamage", "beginClassAwakening", "Antik Şehir Uyanışı", "Cehennem Çekirdeği",
        "Kadim Orman", "Sistem Çökmesi", "Mor Kıyamet", "emitFinalPulse"
    ],
    anomaly: [
        "recordPowerUse", "chooseStoredDamage", "allowDamage", "VOIDED", "REVERSED",
        "FROZEN_PROJECTILES", "ECHOES", "PendingEcho", "copiedPowerUses", "SONUÇ REDDEDİLDİ", "handleDisconnect"
    ],
    power_system: [
        "tickFlight", "Kadim Ejderha pasifi", "DRAGON_CLAW_TARGET", "DRAGON_CLAW_ESCAPE_PRESSES", "DRAGON_BREATHS", "DRAGON_SILENCE_UNTIL",
        "data.setDragonScalesUntil", "data.setDragonScaleCharges", "data.setDragonFormUntil", "dragon_dash", "dragon_breath",
        "dragon_roar", "dragon_form", "ServerNetworking.sendCastAnimation", "PowerCollisionSystem.registerCast",
        "WorldEventSystem.tick", "data.beginCombo(2, now, 80)",
        "WARDEN_AMBUSHES", "spawnWardenArm", "nearbyLiving(player, 30.0)", "WardenArmSegment"
    ],
    network: [
        'command.equals("AWAKEN")', "dragonScalesTicks", "dragonScaleCharges", "awakeningEnergy", "classAwakeningTicks",
        "sendCastAnimation"
    ],
    client: [
        "GLFW.GLFW_KEY_G", 'send("AWAKEN")', "GLFW.GLFW_KEY_V", "GLFW.GLFW_KEY_X", "GLFW.GLFW_KEY_Z", 'send("DRAGON_ESCAPE")', "CAST_"
    ],
    client_state: [
        "dragonScalesTicks", "dragonFormTicks", "awakeningEnergy", "classAwakeningTicks",
        "castPulseTicks"
    ],
    client_config: [
        "hudScalePercent", "notificationScalePercent", "showAwakeningBar", "cardAnimationSpeedPercent",
        "particleDensityPercent", "glowPercent", "animatedBackgrounds", "performanceMode", "photosensitiveMode"
    ],
    settings: [
        '"HUD"', '"ANİMASYON"', '"ERİŞİLEBİLİRLİK"', "Uyanış Çubuğu", "Parçacık Yoğunluğu",
        "Foto-Hassasiyet Modu"
    ],
    hud: [
        "drawAwakeningStatus", "G: UYANIŞ", "UYANIŞ AKTİF", "Ejderha Hükümdarı",
        "Antik Şehir Seni Şarj etti.", "Vücudun bunu kaldırabilecek Mi?", "drawCastOverlay"
    ],
    menu: [
        "Ejderha Hükümdarı", "Kadim Pullar", "drawClassEmblem", "displayName(powerClass, level)"
    ],
    selection: [
        '"KADİM EJDERHA"', "drawDragonStorm", "drawAncientCity", "drawLavaCave", "drawForest", "drawAnomalyGlitch",
        "cardProgress >= 0.85F"
    ],
    analyzer: ["FLIGHT_COLORS", "ANOMALY_COLORS", "SkinPowers/1.1.1", "analyzeAsync(profile, false)", "CACHE_TTL_MILLIS", "response.statusCode() == 429"],
    commands: [
        'Commands.literal("degistir")', 'selfClass("ejderha"', 'Commands.literal("olay")',
        'worldEventLiteral("meteor")', 'Commands.literal("durdur")',
        'Commands.literal("durum")', 'Commands.literal("buyu")', 'Commands.literal("kitap")',
        'giveEnchantmentBook', 'Commands.literal("trigger")', 'triggerLiteral("dragon_breath")',
        'triggerLiteral("dragon_form")'
    ],
    mod: [
        "ServerLivingEntityEvents.ALLOW_DAMAGE.register(AwakeningSystem::allowDamage)",
        "PowerSystem::allowWardenAmbushDamage",
        "ClassEnchantmentSystem::tickServer", "ClassEnchantmentSystem::onAttackEntity",
        "ClassEnchantmentSystem::allowDamage", "ClassEnchantmentSystem::allowDeath",
        "Skin Powers 1.1.1 yüklendi"
    ],
    store: ["migrateLegacyClassNames", "JsonParser.parseReader"],
    collision: ["registerCast", "cancelActiveOffense", "GÜÇ ÇARPIŞMASI"],
    world_event: ["Sculk Uyanışı", "Meteor Fırtınası", "Gökyüzü Yarığı", "Kadim Çiçeklenme", "Gerçeklik Çatlağı"],
    class_enchantments: [
        "ECHO_STRIKE", "DEPTH_STEP", "SCULK_ARMOR", "ANCIENT_COLLAPSE",
        "EMBER_BUILDUP", "ASH_WALK", "HELL_CORE", "METEOR_FALL",
        "ROOT_BIND", "LIFE_SPROUT", "FOREST_LEAP", "THORNY_DEFENSE",
        "DELAYED_STRIKE", "PHASE_SHIFT", "ERROR_MARGIN", "BROKEN_TRAJECTORY",
        "DRAGON_CLAW", "PURPLE_WING", "ANCIENT_SCALES", "PURPLE_BREATH",
        "EnchantmentHelper.createBook"
    ],
    class_enchantment_system: [
        "tryDragonJump", "tickDepthStep", "tickAshWalk", "tickForestLeap",
        "echoStrike", "ancientCollapse", "emberHit", "rootBind", "dragonClaw",
        "hellCore", "tickOwnedProjectiles", "tickMeteors", "tickVisibleEffects", "spawnVisibleRing", "allowDeath",
        "Items.SCULK_CATALYST", "5.5F", "inflate(5.0)", "6.5F", "List<UUID> visualIds"
    ],
}
for source, tokens in required_tokens.items():
    for token in tokens:
        if token not in source:
            fail(f"Gerekli kod bulunamadı: {token}")

# Kök komutta doğrudan sınıf değişimi bulunmamalı.
root_prefix = commands.split('root.then(Commands.literal("degistir")', 1)[0]
if 'Commands.literal("baslat")' in commands:
    fail("Dünya olayı komutunda gereksiz baslat katmanı kaldı")

for forbidden in [
    'selfClass("warden"', 'selfClass("ejderha"', 'selfClass("ucus"', 'selfClass("ates"',
    'selfClass("doga"', 'selfClass("anomali"'
]:
    if forbidden in root_prefix:
        fail(f"Sınıf kök komutta doğrudan görünüyor: {forbidden}")

# 20 sınıf büyüsü ve normal örs veri yapısı.
enchantment_ids = [
    "yanki_darbesi", "derinlik_adimi", "sculk_zirhi", "antik_cokus",
    "kor_birikimi", "kul_yuruyusu", "cehennem_cekirdegi", "meteor_dususu",
    "kok_bagi", "can_filizi", "orman_sicrayisi", "dikenli_savunma",
    "gecikmis_darbe", "faz_kaymasi", "hata_payi", "bozuk_yorunge",
    "ejderha_pencesi", "mor_kanat", "kadim_pullar", "mor_nefes",
]
enchantment_dir = ROOT / "src/main/resources/data/skinpowers/enchantment"
found_enchantments = sorted(path.stem for path in enchantment_dir.glob("*.json")) if enchantment_dir.is_dir() else []
if sorted(enchantment_ids) != found_enchantments:
    fail(f"Büyü JSON listesi yanlış: {found_enchantments}")
else:
    ok("20 büyü JSON'u")

for enchantment_id in enchantment_ids:
    path = enchantment_dir / f"{enchantment_id}.json"
    if not path.is_file():
        continue
    definition = json.loads(path.read_text(encoding="utf-8"))
    if definition.get("max_level") != 1:
        fail(f"Büyü tek seviyeli değil: {enchantment_id}")
    if definition.get("exclusive_set") != "#skinpowers:class_enchantments":
        fail(f"Tek sınıf büyüsü sınırı eksik: {enchantment_id}")
    if "supported_items" not in definition or "slots" not in definition:
        fail(f"Büyü eşya/yuva tanımı eksik: {enchantment_id}")

exclusive_tag_path = ROOT / "src/main/resources/data/skinpowers/tags/enchantment/class_enchantments.json"
if exclusive_tag_path.is_file():
    exclusive_values = json.loads(exclusive_tag_path.read_text(encoding="utf-8")).get("values", [])
    expected_values = [f"skinpowers:{entry}" for entry in enchantment_ids]
    if sorted(exclusive_values) != sorted(expected_values):
        fail("class_enchantments etiketi 20 büyünün tamamını içermiyor")
    else:
        ok("Tek sınıf büyüsü etiketi")

for survival_tag in ["tradeable", "treasure", "on_random_loot"]:
    survival_path = ROOT / f"src/main/resources/data/minecraft/tags/enchantment/{survival_tag}.json"
    if survival_path.is_file():
        values = json.loads(survival_path.read_text(encoding="utf-8")).get("values", [])
        if "#skinpowers:class_enchantments" not in values:
            fail(f"Survival büyü etiketi sınıf büyülerini içermiyor: {survival_tag}")
        else:
            ok(f"Survival büyü etiketi: {survival_tag}")

if "SkinAnalyzer.analyzeAsync(minecraft.player.getGameProfile(), true)" not in selection:
    fail("Skin ekranı zorunlu yenileme ile tarama başlatmıyor")
if "SKIN BEKLENİYOR" not in selection or "Steve benzeri sahte bir karakter" not in selection:
    fail("Skin alınamadığında sahte Steve kaldırma göstergesi eksik")

tr_lang = json.loads(text("src/main/resources/assets/skinpowers/lang/tr_tr.json") or "{}")
en_lang = json.loads(text("src/main/resources/assets/skinpowers/lang/en_us.json") or "{}")
for enchantment_id in enchantment_ids:
    key = f"enchantment.skinpowers.{enchantment_id}"
    if key not in tr_lang or key not in en_lang:
        fail(f"Büyü çevirisi eksik: {key}")

if 'command.equals("ENCHANT_JUMP")' not in network or 'send("ENCHANT_JUMP")' not in client:
    fail("Mor Kanat ikinci zıplama ağı eksik")
else:
    ok("Mor Kanat ağ doğrulaması")

# Bot ve düello sistemleri tamamen kaldırılmış olmalı.
for removed in [
    "src/main/java/com/yagiz/skinpowers/DuelSystem.java",
    "src/main/java/com/yagiz/skinpowers/PvpBotSystem.java",
    "src/main/java/com/yagiz/skinpowers/BattlePanel.java",
]:
    if (ROOT / removed).exists():
        fail(f"Kaldırılması gereken kaynak hâlâ mevcut: {removed}")

combined_sources = "\n".join([power_system, network, commands, mod, world_event, hud, settings, client_state, client_config])
for forbidden in ["DuelSystem", "PvpBotSystem", "BattlePanel", 'Commands.literal("duello")', 'Commands.literal("bot")', "Düello/Bot Paneli", "duelActive", "battlePanel", "showBattlePanel", "battleOpponent"]:
    if forbidden in combined_sources:
        fail(f"Kaldırılan sistemden kalan kod bulundu: {forbidden}")

# Eski sınıf kartları / metinleri aktif seçim arayüzünde kalmamalı.
if (ROOT / "src/main/resources/assets/skinpowers/textures/gui/cards/time.png").exists():
    fail("Eski time.png hâlâ mevcut")
if "drawTimeTemple" in selection or '"ZAMAN"' in selection:
    fail("Seçim ekranında eski Zaman kartı kaldı")
if '"UÇUŞ"' in selection or "drawCloudSky" in selection:
    fail("Seçim ekranında eski Uçuş kartı kaldı")

# Sınıf kartlarının PNG imzasını ve boyutunu Pillow gerektirmeden kontrol et.
def png_size(path: Path) -> tuple[int, int] | None:
    try:
        raw = path.read_bytes()
        if raw[:8] != b"\x89PNG\r\n\x1a\n" or raw[12:16] != b"IHDR":
            return None
        return struct.unpack(">II", raw[16:24])
    except Exception:
        return None

for card in ["warden", "flight", "dragon", "fire", "nature", "anomaly"]:
    relative = f"src/main/resources/assets/skinpowers/textures/gui/cards/{card}.png"
    path = require_file(relative)
    if path.is_file():
        size = png_size(path)
        if size != (288, 512):
            fail(f"Kart boyutu yanlış: {relative} -> {size}")
        else:
            ok(f"PNG 288x512: {card}")

# Basit Java parantez dengesi; yorumları ve dizeleri kaba biçimde atlar.
def balanced_java(source: str, name: str) -> None:
    stack: list[str] = []
    pairs = {')': '(', ']': '[', '}': '{'}
    opens = set(pairs.values())
    i = 0
    in_string = in_char = in_line = in_block = False
    escaped = False
    while i < len(source):
        c = source[i]
        n = source[i + 1] if i + 1 < len(source) else ''
        if in_line:
            if c == '\n':
                in_line = False
        elif in_block:
            if c == '*' and n == '/':
                in_block = False
                i += 1
        elif in_string:
            if escaped:
                escaped = False
            elif c == '\\':
                escaped = True
            elif c == '"':
                in_string = False
        elif in_char:
            if escaped:
                escaped = False
            elif c == '\\':
                escaped = True
            elif c == "'":
                in_char = False
        elif c == '/' and n == '/':
            in_line = True
            i += 1
        elif c == '/' and n == '*':
            in_block = True
            i += 1
        elif c == '"':
            in_string = True
        elif c == "'":
            in_char = True
        elif c in opens:
            stack.append(c)
        elif c in pairs:
            if not stack or stack.pop() != pairs[c]:
                fail(f"Java parantez hatası: {name}")
                return
        i += 1
    if stack or in_string or in_char or in_block:
        fail(f"Java kapanış hatası: {name}")
    else:
        ok(f"Java yapı: {name}")


java_paths = list((ROOT / "src/main/java").rglob("*.java")) + list((ROOT / "src/client/java").rglob("*.java"))
for java_path in java_paths:
    balanced_java(java_path.read_text(encoding="utf-8"), str(java_path.relative_to(ROOT)))

# Minecraft bağımlılığı olmayan çekirdek sınıfları gerçek javac ile derle ve davranış testi yap.
javac = shutil.which("javac")
java = shutil.which("java")
if not javac or not java:
    fail("javac/java bulunamadı")
else:
    with tempfile.TemporaryDirectory(prefix="skinpowers_verify_") as tmp:
        tmp_path = Path(tmp)
        test_file = tmp_path / "CoreTest.java"
        test_file.write_text(r'''
import com.yagiz.skinpowers.*;
public final class CoreTest {
  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
  public static void main(String[] args) {
    check(PowerClass.safeValueOf("TIME") == PowerClass.ANOMALY, "old TIME migration");
    check(PowerClass.safeValueOf("EJDERHA") == PowerClass.FLIGHT, "dragon command mapping");
    check("Kadim Ejderha".equals(PowerClass.FLIGHT.displayName()), "dragon display name");
    check(PowerCatalog.maxLevel(PowerClass.FLIGHT) == 6, "dragon max level");
    check(PowerCatalog.xpCostForLevel(PowerClass.FLIGHT, 6) == 70, "dragon VI cost");
    for (int i = 1; i <= 6; i++) {
      check(!PowerCatalog.powerName(PowerClass.FLIGHT, i).isBlank(), "dragon name " + i);
      check(!PowerCatalog.powerDescription(PowerClass.FLIGHT, i).isBlank(), "dragon description " + i);
      check(!PowerCatalog.powerDescription(PowerClass.ANOMALY, i).isBlank(), "anomaly description " + i);
    }
    check("Derinlik Pususu".equals(PowerCatalog.powerName(PowerClass.WARDEN, 4)), "warden ambush name");
    check(PowerCatalog.powerDescription(PowerClass.WARDEN, 4).contains("3B sculk"), "warden ambush description");
    check(PowerCatalog.comboStarterPower(PowerClass.FLIGHT) == 2, "dragon combo starter");
    check(PowerCatalog.comboFinisherPower(PowerClass.FLIGHT) == 5, "dragon combo finisher");

    PlayerPowerData data = new PlayerPowerData();
    data.chooseClass(PowerClass.FLIGHT);
    for (int i = 0; i < 6; i++) data.unlockNextLevel();
    data.setAwakeningEnergy(50.0F);
    int duration = data.beginClassAwakening(100L);
    check(duration == 240, "50 percent awakening duration");
    check(data.classAwakeningUntil() == 340L && data.awakeningEnergy() == 0.0F, "awakening consumes bar");
    data.finishClassAwakening();
    data.setAwakeningEnergy(19.0F);
    check(data.beginClassAwakening(500L) == 0, "minimum awakening threshold");
    data.setDragonScalesUntil(800L);
    data.setDragonFormUntil(900L);
    check(data.dragonScalesUntil() == 800L && data.dragonFormUntil() == 900L, "dragon timers");

    data.changeClass(PowerClass.ANOMALY);
    data.setCopiedPower(PowerClass.FIRE, 5);
    check(data.hasCopiedPower() && data.copiedPowerClass() == PowerClass.FIRE, "copy persistence");
    data.setCopiedPowerUses(2);
    check(data.copiedPowerUses() == 2, "awakening copy has two uses");
    check(!data.consumeCopiedPowerUse() && data.copiedPowerUses() == 1, "first copy use remains");
    check(data.consumeCopiedPowerUse() && !data.hasCopiedPower(), "second copy use exhausts");
    data.setCopiedPower(PowerClass.FIRE, 5);
    data.beginAnomalyDamageStore(100L);
    data.addAnomalyStoredDamage(24.0F);
    data.finishAnomalyDamageStore(300L);
    check(data.anomalyStoredDamage() == 24.0F && data.anomalyChoiceUntil() == 300L, "damage storage");

    check(com.yagiz.skinpowers.client.ClientUiRules.classChoiceAllowed(true, true, 1, 5, 0, 1), "second suggestion selectable");
    check(com.yagiz.skinpowers.client.ClientUiRules.staggeredProgress(1.0F, 5, 6, 0.30F) >= 0.99F, "sixth row animation reaches end");
    System.out.println("CORE_TEST_OK");
  }
}
''', encoding="utf-8")
        sources = [
            ROOT / "src/main/java/com/yagiz/skinpowers/PowerClass.java",
            ROOT / "src/main/java/com/yagiz/skinpowers/PowerCatalog.java",
            ROOT / "src/main/java/com/yagiz/skinpowers/PlayerPowerData.java",
            ROOT / "src/client/java/com/yagiz/skinpowers/client/ClientUiRules.java",
            test_file,
        ]
        classes = tmp_path / "classes"
        classes.mkdir()
        result = subprocess.run(
            [javac, "-encoding", "UTF-8", "-d", str(classes), *map(str, sources)],
            capture_output=True, text=True
        )
        if result.returncode != 0:
            fail("Çekirdek javac derlemesi başarısız:\n" + result.stderr)
        else:
            run = subprocess.run([java, "-cp", str(classes), "CoreTest"], capture_output=True, text=True)
            if run.returncode != 0 or "CORE_TEST_OK" not in run.stdout:
                fail("Çekirdek davranış testi başarısız:\n" + run.stdout + run.stderr)
            else:
                ok("Çekirdek javac ve davranış testleri")

if errors:
    print("SKIN POWERS PROJE DENETİMİ BAŞARISIZ")
    for item in errors:
        print("[HATA]", item)
    sys.exit(1)

print("SKIN POWERS PROJE DENETİMİ BAŞARILI")
print(f"{len(checks)} kontrol geçti.")
print("Skin yenileme, survival büyü kitapları, görünür büyü gövdeleri ve güçlendirilmiş büyüler doğrulandı.")
