#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION = "1.0.4"
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


# Temel proje dosyaları
for file_name in [
    "build.gradle", "gradle.properties", "settings.gradle", "README.md", "VALIDATION.md", "FEATURE_STATUS.md",
    ".github/workflows/build.yml", "src/main/resources/fabric.mod.json",
    "src/main/resources/assets/skinpowers/lang/tr_tr.json",
    "src/main/resources/assets/skinpowers/lang/en_us.json",
    "src/main/resources/assets/skinpowers/textures/gui/cards/anomaly.png",
]:
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
    fail("gradle.properties sürümü 1.0.4 değil")
if f"skinpowers-{VERSION}-jar" not in workflow or f"skinpowers-{VERSION}.jar" not in workflow:
    fail("GitHub Actions artifact/JAR adı 1.0.4 değil")
if "rm -f src/main/resources/assets/skinpowers/textures/gui/cards/time.png" not in workflow:
    fail("GitHub Actions eski time.png temizliğini içermiyor")
if "Anomali" not in mod_json:
    fail("fabric.mod.json Anomali açıklamasını içermiyor")

power_class = text("src/main/java/com/yagiz/skinpowers/PowerClass.java")
catalog = text("src/main/java/com/yagiz/skinpowers/PowerCatalog.java")
data = text("src/main/java/com/yagiz/skinpowers/PlayerPowerData.java")
anomaly = text("src/main/java/com/yagiz/skinpowers/AnomalySystem.java")
power_system = text("src/main/java/com/yagiz/skinpowers/PowerSystem.java")
network = text("src/main/java/com/yagiz/skinpowers/ServerNetworking.java")
client = text("src/client/java/com/yagiz/skinpowers/client/SkinPowersClient.java")
client_state = text("src/client/java/com/yagiz/skinpowers/client/ClientState.java")
hud = text("src/client/java/com/yagiz/skinpowers/client/HudOverlay.java")
menu = text("src/client/java/com/yagiz/skinpowers/client/PowerMenuScreen.java")
selection = text("src/client/java/com/yagiz/skinpowers/client/SkinSelectionScreen.java")
analyzer = text("src/client/java/com/yagiz/skinpowers/client/SkinAnalyzer.java")
commands = text("src/main/java/com/yagiz/skinpowers/SkinPowersCommands.java")
mod = text("src/main/java/com/yagiz/skinpowers/SkinPowersMod.java")
store = text("src/main/java/com/yagiz/skinpowers/PlayerDataStore.java")

required_tokens = {
    power_class: ["ANOMALY(\"Anomali\")", 'normalized.equals("TIME")', "return ANOMALY"],
    catalog: ["Kırık Adım", "Tersine Çevir", '"?"', "Hasar Mevcut Değil", "Varlıktan Çıkar", "404: Gerçeklik Bulunamadı", "ANOMALY_XP_COSTS"],
    data: ["copiedPowerClass", "anomalyStoredDamage", "anomalyBonusHealthUntil", "anomalyHealthBaseBeforeBonus", "anomalyRealityReviveAvailable"],
    anomaly: ["recordPowerUse", "chooseStoredDamage", "setBaseValue", "3600L", "allowDamage", "VOIDED", "REVERSED", "handleDisconnect", "restoreVoidedTarget"],
    power_system: ["tickBorrowedClassEffects", "tickActiveFireRing", "executeCopiedPower", "clearBorrowedClassEffects", "beginCopiedBeam"],
    network: ["ANOMALY_HEALTH", "ANOMALY_RETURN", "copiedPowerName", "anomalyChoiceTicks", "ServerPlayConnectionEvents.DISCONNECT.register"],
    client: ["GLFW.GLFW_KEY_V", "GLFW.GLFW_KEY_X", 'send("ANOMALY_HEALTH")', 'send("ANOMALY_RETURN")'],
    client_state: ["copiedPowerName", "anomalyStoredDamage", "anomalyBonusHealthTicks"],
    hud: ["Antik Şehir Seni Şarj etti.", "Vücudun bunu kaldırabilecek Mi?", "[V] Kalp", "[X] Geri gönder"],
    menu: ["displayName(powerClass, level)", "copiedPowerDescription", "R: depola • V: kalp • X: geri gönder"],
    selection: ['"ANOMALİ"', "drawAnomalyGlitch"],
    analyzer: ["ANOMALY_COLORS", "diversityBonus", "SkinPowers/1.0.4"],
    commands: ['Commands.literal("degistir")', 'selfClass("anomali"', 'Commands.literal("admin")'],
    mod: ["ServerLivingEntityEvents.ALLOW_DAMAGE.register(AnomalySystem::allowDamage)", "ServerLivingEntityEvents.ALLOW_DEATH.register(AnomalySystem::allowDeath)", "Skin Powers 1.0.4 yüklendi"],
    store: ["migrateLegacyClassNames", 'object.addProperty("powerClass", "ANOMALY")', "JsonParser.parseReader"],
}
for source, tokens in required_tokens.items():
    for token in tokens:
        if token not in source:
            fail(f"Gerekli kod bulunamadı: {token}")

# Kök komutta doğrudan sınıf değişimi bulunmamalı.
root_prefix = commands.split('root.then(Commands.literal("degistir")', 1)[0]
for forbidden in ['selfClass("warden"', 'selfClass("ucus"', 'selfClass("ates"', 'selfClass("doga"', 'selfClass("anomali"']:
    if forbidden in root_prefix:
        fail(f"Sınıf kök komutta doğrudan görünüyor: {forbidden}")

# Eski Zaman kartı kullanılmamalı.
if (ROOT / "src/main/resources/assets/skinpowers/textures/gui/cards/time.png").exists():
    fail("Eski time.png hâlâ mevcut")
if "drawTimeTemple" in selection or '"ZAMAN"' in selection:
    fail("Seçim ekranında eski Zaman kartı kaldı")

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
            if c == '\n': in_line = False
        elif in_block:
            if c == '*' and n == '/': in_block = False; i += 1
        elif in_string:
            if escaped: escaped = False
            elif c == '\\': escaped = True
            elif c == '"': in_string = False
        elif in_char:
            if escaped: escaped = False
            elif c == '\\': escaped = True
            elif c == "'": in_char = False
        elif c == '/' and n == '/': in_line = True; i += 1
        elif c == '/' and n == '*': in_block = True; i += 1
        elif c == '"': in_string = True
        elif c == "'": in_char = True
        elif c in opens: stack.append(c)
        elif c in pairs:
            if not stack or stack.pop() != pairs[c]:
                fail(f"Java parantez hatası: {name}")
                return
        i += 1
    if stack or in_string or in_char or in_block:
        fail(f"Java kapanış hatası: {name}")
    else:
        ok(f"Java yapı: {name}")

for java_path in list((ROOT / "src/main/java").rglob("*.java")) + list((ROOT / "src/client/java").rglob("*.java")):
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
    check(PowerCatalog.maxLevel(PowerClass.ANOMALY) == 6, "anomaly max level");
    check("?".equals(PowerCatalog.powerName(PowerClass.ANOMALY, 3)), "copy power name");
    check(PowerCatalog.xpCostForLevel(PowerClass.ANOMALY, 6) == 70, "anomaly VI cost");
    for (int i = 1; i <= 6; i++) {
      check(!PowerCatalog.powerDescription(PowerClass.ANOMALY, i).isBlank(), "anomaly description " + i);
    }
    PlayerPowerData data = new PlayerPowerData();
    data.chooseClass(PowerClass.ANOMALY);
    for (int i = 0; i < 6; i++) data.unlockNextLevel();
    data.setCopiedPower(PowerClass.FIRE, 5);
    check(data.hasCopiedPower() && data.copiedPowerClass() == PowerClass.FIRE && data.copiedPowerLevel() == 5, "copy persistence");
    data.beginAnomalyDamageStore(100L);
    data.addAnomalyStoredDamage(24.0F);
    data.finishAnomalyDamageStore(300L);
    check(data.anomalyStoredDamage() == 24.0F && data.anomalyChoiceUntil() == 300L, "damage storage");
    data.setAnomalyBonusHealth(20.0, 3600L, 20.0);
    check(data.anomalyBonusHealth() == 20.0 && data.anomalyBonusHealthUntil() == 3600L
      && data.anomalyHealthBaseBeforeBonus() == 20.0, "bonus hearts");
    data.beginAnomalyReality(500L, 1, 2, 3);
    check(data.anomalyRealityReviveAvailable(), "404 revive");
    data.reset();
    check(data.powerClass() == PowerClass.NONE && !data.hasCopiedPower() && data.anomalyStoredDamage() == 0.0F, "reset");
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
        command = [javac, "-encoding", "UTF-8", "-d", str(tmp_path / "classes"), *map(str, sources)]
        result = subprocess.run(command, capture_output=True, text=True)
        if result.returncode != 0:
            fail("Çekirdek javac derlemesi başarısız:\n" + result.stderr)
        else:
            run = subprocess.run([java, "-cp", str(tmp_path / "classes"), "CoreTest"], capture_output=True, text=True)
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
print("Anomali, V/X hasar seçimi, tek kullanımlık güç kopyası, eski kayıt geçişi, küçük Warden HUD'u ve degistir komut yapısı doğrulandı.")
