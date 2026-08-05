package com.yagiz.skinpowers;

import java.util.Arrays;

public final class PlayerPowerData {
    private static final int STORAGE_SIZE = 6;

    private PowerClass powerClass = PowerClass.NONE;
    private int unlockedLevel = 0;
    private int selectedPower = 1;
    private int[] masteryUses = new int[STORAGE_SIZE];
    private long[] cooldownUntil = new long[STORAGE_SIZE];
    private int[] frozenCooldownTicks = new int[STORAGE_SIZE];
    private boolean passiveEnabled = false;
    private boolean visionEnabled = false;
    private long awakeningUntil = 0L;
    private long fireRingUntil = 0L;
    private long skyImpactSlowUntil = 0L;
    private long temporaryElytraUntil = 0L;
    private long wardenHuntUntil = 0L;
    private long natureTreeUntil = 0L;

    // 1.0.6: Kadim Ejderha ve tüm sınıfların enerjiye bağlı Uyanış Formu.
    private long dragonScalesUntil = 0L;
    private int dragonScaleCharges = 0;
    private long dragonFormUntil = 0L;
    private float awakeningEnergy = 0.0F;
    private long classAwakeningUntil = 0L;

    // 4.1 Kombo modu ve kısa süreli kombinasyon penceresi.
    private boolean comboModeEnabled = false;
    private int comboStarterPower = 0;
    private long comboExpiresAt = 0L;
    private double comboTargetX = 0.0;
    private double comboTargetY = 0.0;
    private double comboTargetZ = 0.0;
    private boolean comboTargetValid = false;

    // Antik Şehir Şarjı ortak durumu.
    // Sayaç 20 saniye boyunca sürer; tek güç hakkı daha erken kullanılsa bile çöküş sayaç bitince başlar.
    private long ancientChargeStartedAt = 0L;
    private long ancientChargeUntil = 0L;
    private long ancientExhaustionUntil = 0L;
    private boolean ancientChargeAvailable = false;
    private int ancientChargeUsedPower = 0;

    // Kendi kendine şarjın üç kalplik bedeli ve yavaş geri kazanımı.
    private boolean selfSacrificeActive = false;
    private int sacrificedHealthPointsToRecover = 0;
    private long nextSacrificedHeartRecoveryTick = 0L;

    // Şarjla başlatılan süreli güçlerin mor/kuvvetli hâli şarj bittikten sonra da sürer.
    private boolean chargedAwakening = false;
    private boolean chargedFireRing = false;
    private boolean chargedTemporaryElytra = false;
    private boolean chargedWardenHunt = false;

    // Anomali sınıfı: tek kullanımlık kopya, hasar deposu ve geçici gerçek kırmızı kalpler.
    private String copiedPowerClass = "NONE";
    private int copiedPowerLevel = 0;
    private int copiedPowerUses = 0;
    private long anomalyDamageStoreUntil = 0L;
    private float anomalyStoredDamage = 0.0F;
    private long anomalyChoiceUntil = 0L;
    private double anomalyBonusHealth = 0.0;
    private long anomalyBonusHealthUntil = 0L;

    private double anomalyHealthBaseBeforeBonus = -1.0;
    private long anomalyRealityUntil = 0L;
    private boolean anomalyRealityReviveAvailable = false;
    private double anomalyRealityX = 0.0;
    private double anomalyRealityY = 0.0;
    private double anomalyRealityZ = 0.0;
    private long anomalyErrorCooldownUntil = 0L;

    public PowerClass powerClass() { return powerClass == null ? PowerClass.NONE : powerClass; }
    public int maxPowerLevel() { return PowerCatalog.maxLevel(powerClass()); }
    public int unlockedLevel() { return Math.max(0, Math.min(maxPowerLevel(), unlockedLevel)); }
    public int selectedPower() { return Math.max(1, Math.min(Math.max(1, maxPowerLevel()), selectedPower)); }
    public boolean passiveEnabled() { return passiveEnabled; }
    public boolean visionEnabled() { return visionEnabled; }
    public long awakeningUntil() { return awakeningUntil; }
    public long fireRingUntil() { return fireRingUntil; }
    public long skyImpactSlowUntil() { return skyImpactSlowUntil; }
    public long temporaryElytraUntil() { return temporaryElytraUntil; }
    public long wardenHuntUntil() { return wardenHuntUntil; }
    public long natureTreeUntil() { return natureTreeUntil; }
    public long dragonScalesUntil() { return dragonScalesUntil; }
    public int dragonScaleCharges() { return Math.max(0, dragonScaleCharges); }
    public long dragonFormUntil() { return dragonFormUntil; }
    public float awakeningEnergy() { return Math.max(0.0F, Math.min(100.0F, awakeningEnergy)); }
    public long classAwakeningUntil() { return classAwakeningUntil; }
    public boolean classAwakeningActive(long gameTime) { return classAwakeningUntil > gameTime; }
    public boolean comboModeEnabled() { return comboModeEnabled; }
    public int comboStarterPower() { return comboStarterPower; }
    public long comboExpiresAt() { return comboExpiresAt; }
    public boolean comboTargetValid() { return comboTargetValid; }
    public double comboTargetX() { return comboTargetX; }
    public double comboTargetY() { return comboTargetY; }
    public double comboTargetZ() { return comboTargetZ; }
    public long ancientChargeStartedAt() { return ancientChargeStartedAt; }
    public long ancientChargeUntil() { return ancientChargeUntil; }
    public long ancientExhaustionUntil() { return ancientExhaustionUntil; }
    public boolean ancientChargeAvailable() { return ancientChargeAvailable; }
    public int ancientChargeUsedPower() { return ancientChargeUsedPower; }
    public boolean selfSacrificeActive() { return selfSacrificeActive; }
    public int sacrificedHealthPointsToRecover() { return sacrificedHealthPointsToRecover; }
    public long nextSacrificedHeartRecoveryTick() { return nextSacrificedHeartRecoveryTick; }
    public boolean chargedAwakening() { return chargedAwakening; }
    public boolean chargedFireRing() { return chargedFireRing; }
    public boolean chargedTemporaryElytra() { return chargedTemporaryElytra; }
    public boolean chargedWardenHunt() { return chargedWardenHunt; }
    public PowerClass copiedPowerClass() { return PowerClass.safeValueOf(copiedPowerClass); }
    public int copiedPowerLevel() { return Math.max(0, Math.min(6, copiedPowerLevel)); }
    public int copiedPowerUses() { return hasCopiedPower() ? Math.max(1, Math.min(2, copiedPowerUses <= 0 ? 1 : copiedPowerUses)) : 0; }
    public boolean hasCopiedPower() { return copiedPowerClass() != PowerClass.NONE && copiedPowerLevel() > 0; }
    public long anomalyDamageStoreUntil() { return anomalyDamageStoreUntil; }
    public float anomalyStoredDamage() { return Math.max(0.0F, anomalyStoredDamage); }
    public long anomalyChoiceUntil() { return anomalyChoiceUntil; }
    public double anomalyBonusHealth() { return Math.max(0.0, anomalyBonusHealth); }

    public long anomalyBonusHealthUntil() { return anomalyBonusHealthUntil; }
    public double anomalyHealthBaseBeforeBonus() { return anomalyHealthBaseBeforeBonus; }
    public long anomalyRealityUntil() { return anomalyRealityUntil; }
    public boolean anomalyRealityReviveAvailable() { return anomalyRealityReviveAvailable; }
    public double anomalyRealityX() { return anomalyRealityX; }
    public double anomalyRealityY() { return anomalyRealityY; }
    public double anomalyRealityZ() { return anomalyRealityZ; }
    public long anomalyErrorCooldownUntil() { return anomalyErrorCooldownUntil; }

    public void chooseClass(PowerClass value) {
        if (powerClass() != PowerClass.NONE || value == null || value == PowerClass.NONE) return;
        powerClass = value;
        selectedPower = 1;
    }

    /** Komutla sınıf değiştirirken eski sınıfa ait seviye, ustalık ve süreli etkileri temizler. */
    public boolean changeClass(PowerClass value) {
        if (value == null || value == PowerClass.NONE) return false;
        if (powerClass() == value) return false;
        reset();
        powerClass = value;
        selectedPower = 1;
        return true;
    }

    public void reset() {
        powerClass = PowerClass.NONE;
        unlockedLevel = 0;
        selectedPower = 1;
        masteryUses = new int[STORAGE_SIZE];
        cooldownUntil = new long[STORAGE_SIZE];
        frozenCooldownTicks = new int[STORAGE_SIZE];
        passiveEnabled = false;
        visionEnabled = false;
        awakeningUntil = 0L;
        fireRingUntil = 0L;
        skyImpactSlowUntil = 0L;
        temporaryElytraUntil = 0L;
        wardenHuntUntil = 0L;
        natureTreeUntil = 0L;
        dragonScalesUntil = 0L;
        dragonScaleCharges = 0;
        dragonFormUntil = 0L;
        awakeningEnergy = 0.0F;
        classAwakeningUntil = 0L;
        comboModeEnabled = false;
        clearCombo();
        ancientChargeStartedAt = 0L;
        ancientChargeUntil = 0L;
        ancientExhaustionUntil = 0L;
        ancientChargeAvailable = false;
        ancientChargeUsedPower = 0;
        selfSacrificeActive = false;
        sacrificedHealthPointsToRecover = 0;
        nextSacrificedHeartRecoveryTick = 0L;
        chargedAwakening = false;
        chargedFireRing = false;
        chargedTemporaryElytra = false;
        chargedWardenHunt = false;
        copiedPowerClass = "NONE";
        copiedPowerLevel = 0;
        copiedPowerUses = 0;
        anomalyDamageStoreUntil = 0L;
        anomalyStoredDamage = 0.0F;
        anomalyChoiceUntil = 0L;
        anomalyBonusHealth = 0.0;
        anomalyBonusHealthUntil = 0L;
        anomalyHealthBaseBeforeBonus = -1.0;
        anomalyRealityUntil = 0L;
        anomalyRealityReviveAvailable = false;
        anomalyRealityX = anomalyRealityY = anomalyRealityZ = 0.0;
        anomalyErrorCooldownUntil = 0L;
    }

    public void unlockNextLevel() {
        int maximum = maxPowerLevel();
        if (unlockedLevel < maximum) {
            unlockedLevel++;
            if (selectedPower > unlockedLevel) selectedPower = unlockedLevel;
        }
    }

    public void setSelectedPower(int level) {
        selectedPower = Math.max(1, Math.min(Math.max(1, unlockedLevel()), level));
    }

    public void selectRelative(int delta) {
        int max = Math.max(1, unlockedLevel());
        int next = selectedPower() + delta;
        if (next < 1) next = max;
        if (next > max) next = 1;
        selectedPower = next;
    }

    public int masteryUses(int level) {
        ensureArrays();
        return masteryUses[index(level)];
    }

    public int masteryStage(int level) {
        return PowerCatalog.masteryStage(masteryUses(level));
    }

    public void addMasteryUse(int level) {
        ensureArrays();
        int index = index(level);
        masteryUses[index] = Math.min(9999, masteryUses[index] + 1);
    }

    public long cooldownUntil(int level) {
        ensureArrays();
        return cooldownUntil[index(level)];
    }

    public int cooldownRemaining(int level, long gameTime) {
        if (ancientChargeReady(gameTime) && level != 6) return 0;
        return (int) Math.max(0L, cooldownUntil(level) - gameTime);
    }

    public void setCooldown(int level, long gameTime, int ticks) {
        ensureArrays();
        cooldownUntil[index(level)] = gameTime + Math.max(0, ticks);
    }

    /** 20 saniyelik şarj penceresi; tek kullanım hakkı harcansa bile sayaç sonuna kadar true kalır. */
    public boolean ancientChargeActive(long gameTime) {
        return ancientChargeUntil > gameTime;
    }

    /** Güçlendirilmiş tek kullanım hakkı hâlâ mevcut mu? */
    public boolean ancientChargeReady(long gameTime) {
        return ancientChargeAvailable && ancientChargeActive(gameTime);
    }

    /** Süre bitmiş olsa bile henüz çöküşe çevrilmemiş bir şarj döngüsü var mı? */
    public boolean ancientChargeCyclePresent() {
        return ancientChargeUntil > 0L || ancientChargeStartedAt > 0L;
    }

    public boolean ancientExhausted(long gameTime) {
        return ancientExhaustionUntil > gameTime;
    }

    /** Şarj verildiğinde mevcut bekleme sürelerini saklar ve tek kullanım hakkı için geçici olarak sıfırlar. */
    public void beginAncientCharge(long gameTime, int durationTicks, boolean selfSacrifice) {
        ensureArrays();
        for (int i = 0; i < STORAGE_SIZE; i++) {
            // Warden'ın 6. gücü şarjdan yararlanamaz ve bekleme süresi temizlenmez.
            if (i == 5 && powerClass() == PowerClass.WARDEN) {
                frozenCooldownTicks[i] = 0;
                continue;
            }
            frozenCooldownTicks[i] = (int) Math.max(0L, cooldownUntil[i] - gameTime);
            cooldownUntil[i] = gameTime;
        }
        ancientChargeStartedAt = gameTime;
        ancientChargeUntil = gameTime + Math.max(1, Math.min(400, durationTicks));
        ancientChargeAvailable = true;
        ancientChargeUsedPower = 0;
        selfSacrificeActive = selfSacrifice;
    }

    public void beginAncientCharge(long gameTime, int durationTicks) {
        beginAncientCharge(gameTime, durationTicks, false);
    }

    /** Kullanılan güç yeni cooldown'unu korur; diğer güçlerin eski cooldown'ları geri döner.
     *  20 saniyelik şarj penceresi kapanmaz; çöküş ancak pencere bitince başlar. */
    public void consumeAncientCharge(long gameTime, int usedPower) {
        if (!ancientChargeReady(gameTime)) return;
        restoreFrozenCooldowns(gameTime, usedPower);
        ancientChargeAvailable = false;
        ancientChargeUsedPower = Math.max(1, Math.min(STORAGE_SIZE, usedPower));
    }

    /** Kombinasyonda hem hazırlık hem bitiriş gücünün yeni cooldown'unu korur. */
    public void consumeAncientChargeForCombo(long gameTime, int starterPower, int finisherPower) {
        if (!ancientChargeReady(gameTime)) return;
        ensureArrays();
        int starterIndex = index(starterPower);
        long starterCooldown = cooldownUntil[starterIndex];
        restoreFrozenCooldowns(gameTime, finisherPower);
        cooldownUntil[starterIndex] = Math.max(gameTime, starterCooldown);
        ancientChargeAvailable = false;
        ancientChargeUsedPower = Math.max(1, Math.min(STORAGE_SIZE, finisherPower));
    }

    /** Sayaç bittiğinde cooldown'ları geri yükler ve şarj döngüsünü kapatır. */
    public void finishAncientCharge(long gameTime) {
        if (ancientChargeAvailable) restoreFrozenCooldowns(gameTime, 0);
        ancientChargeStartedAt = 0L;
        ancientChargeUntil = 0L;
        ancientChargeAvailable = false;
        ancientChargeUsedPower = 0;
    }

    /** Komutla temizleme veya zorla yeni şarj verme için cezasız iptal. */
    public void cancelAncientCharge(long gameTime) {
        if (ancientChargeAvailable) restoreFrozenCooldowns(gameTime, 0);
        ancientChargeStartedAt = 0L;
        ancientChargeUntil = 0L;
        ancientChargeAvailable = false;
        ancientChargeUsedPower = 0;
        selfSacrificeActive = false;
    }

    /** Eski çağrılarla uyumluluk: kullanılmadan biterse şarj döngüsünü kapatır. */
    public void expireAncientCharge(long gameTime) {
        finishAncientCharge(gameTime);
    }

    private void restoreFrozenCooldowns(long gameTime, int usedPower) {
        ensureArrays();
        int usedIndex = usedPower <= 0 ? -1 : index(usedPower);
        long usedCooldown = usedIndex >= 0 ? cooldownUntil[usedIndex] : gameTime;
        for (int i = 0; i < STORAGE_SIZE; i++) {
            if (i == 5 && powerClass() == PowerClass.WARDEN) {
                frozenCooldownTicks[i] = 0;
                continue;
            }
            cooldownUntil[i] = gameTime + Math.max(0, frozenCooldownTicks[i]);
            frozenCooldownTicks[i] = 0;
        }
        if (usedIndex >= 0) cooldownUntil[usedIndex] = Math.max(gameTime, usedCooldown);
    }

    public void startSacrificedHeartRecovery(long gameTime) {
        if (!selfSacrificeActive) return;
        selfSacrificeActive = false;
        sacrificedHealthPointsToRecover = 6;
        nextSacrificedHeartRecoveryTick = gameTime + 60L;
    }

    public void advanceSacrificedHeartRecovery(long nextTick) {
        if (sacrificedHealthPointsToRecover > 0) sacrificedHealthPointsToRecover--;
        nextSacrificedHeartRecoveryTick = sacrificedHealthPointsToRecover > 0 ? nextTick : 0L;
    }

    public void clearSacrificedHeartRecovery() {
        selfSacrificeActive = false;
        sacrificedHealthPointsToRecover = 0;
        nextSacrificedHeartRecoveryTick = 0L;
    }

    public void setAncientExhaustionUntil(long value) { ancientExhaustionUntil = value; }
    public void clearAncientExhaustion() { ancientExhaustionUntil = 0L; }


    public boolean toggleComboMode() {
        comboModeEnabled = !comboModeEnabled;
        if (!comboModeEnabled) clearCombo();
        return comboModeEnabled;
    }

    public void setComboModeEnabled(boolean value) {
        comboModeEnabled = value;
        if (!value) clearCombo();
    }

    public boolean comboActive(long gameTime) {
        if (!comboModeEnabled || comboStarterPower <= 0 || comboExpiresAt <= gameTime) {
            if (comboStarterPower > 0 && comboExpiresAt <= gameTime) clearCombo();
            return false;
        }
        return true;
    }

    public void beginCombo(int starterPower, long gameTime, int durationTicks) {
        beginCombo(starterPower, gameTime, durationTicks, 0.0, 0.0, 0.0, false);
    }

    public void beginCombo(int starterPower, long gameTime, int durationTicks, double x, double y, double z, boolean hasTarget) {
        if (!comboModeEnabled) return;
        comboStarterPower = Math.max(1, Math.min(STORAGE_SIZE, starterPower));
        comboExpiresAt = gameTime + Math.max(1, durationTicks);
        comboTargetX = x;
        comboTargetY = y;
        comboTargetZ = z;
        comboTargetValid = hasTarget;
    }

    public void clearCombo() {
        comboStarterPower = 0;
        comboExpiresAt = 0L;
        comboTargetX = 0.0;
        comboTargetY = 0.0;
        comboTargetZ = 0.0;
        comboTargetValid = false;
    }

    public void togglePassive() { passiveEnabled = !passiveEnabled; }
    public void toggleVision() { visionEnabled = !visionEnabled; }
    public void setVisionEnabled(boolean value) { visionEnabled = value; }
    public void setAwakeningUntil(long value) { awakeningUntil = value; }
    public void setFireRingUntil(long value) { fireRingUntil = value; }
    public void setSkyImpactSlowUntil(long value) { skyImpactSlowUntil = value; }
    public void setTemporaryElytraUntil(long value) { temporaryElytraUntil = value; }
    public void setWardenHuntUntil(long value) { wardenHuntUntil = value; }
    public void setNatureTreeUntil(long value) { natureTreeUntil = value; }
    public void setDragonScalesUntil(long value) {
        dragonScalesUntil = Math.max(0L, value);
        if (dragonScalesUntil == 0L) dragonScaleCharges = 0;
    }
    public void setDragonScaleCharges(int value) { dragonScaleCharges = Math.max(0, Math.min(9, value)); }
    public boolean consumeDragonScaleCharge() {
        if (dragonScaleCharges <= 0) return false;
        dragonScaleCharges--;
        if (dragonScaleCharges == 0) dragonScalesUntil = 0L;
        return true;
    }
    public void setDragonFormUntil(long value) { dragonFormUntil = Math.max(0L, value); }
    public void setClassAwakeningUntil(long value) { classAwakeningUntil = Math.max(0L, value); }
    public void setAwakeningEnergy(float value) { awakeningEnergy = Math.max(0.0F, Math.min(100.0F, value)); }
    public void addAwakeningEnergy(float value) {
        if (classAwakeningUntil > 0L || value <= 0.0F) return;
        awakeningEnergy = Math.max(0.0F, Math.min(100.0F, awakeningEnergy + value));
    }
    /** Çubuktaki enerjinin tamamını süreye dönüştürür. %100 = 24 saniye. */
    public int beginClassAwakening(long gameTime) {
        float energy = awakeningEnergy();
        if (energy < 20.0F || classAwakeningActive(gameTime)) return 0;
        int durationTicks = Math.max(96, Math.round(energy * 4.8F));
        awakeningEnergy = 0.0F;
        classAwakeningUntil = gameTime + durationTicks;
        return durationTicks;
    }
    public void finishClassAwakening() { classAwakeningUntil = 0L; }
    public void setChargedAwakening(boolean value) { chargedAwakening = value; }
    public void setChargedFireRing(boolean value) { chargedFireRing = value; }
    public void setChargedTemporaryElytra(boolean value) { chargedTemporaryElytra = value; }
    public void setChargedWardenHunt(boolean value) { chargedWardenHunt = value; }

    public void setCopiedPower(PowerClass powerClass, int level) {
        if (powerClass == null || powerClass == PowerClass.NONE || powerClass == PowerClass.ANOMALY || level <= 0) return;
        copiedPowerClass = powerClass.name();
        copiedPowerLevel = Math.max(1, Math.min(PowerCatalog.maxLevel(powerClass), level));
        copiedPowerUses = 1;
    }

    public void setCopiedPowerUses(int value) {
        if (!hasCopiedPower()) { copiedPowerUses = 0; return; }
        copiedPowerUses = Math.max(1, Math.min(2, value));
    }

    /** @return kullanımdan sonra kopya tamamen tükendiyse true. */
    public boolean consumeCopiedPowerUse() {
        if (!hasCopiedPower()) return true;
        copiedPowerUses = Math.max(0, copiedPowerUses() - 1);
        if (copiedPowerUses <= 0) {
            clearCopiedPower();
            return true;
        }
        return false;
    }

    public void clearCopiedPower() { copiedPowerClass = "NONE"; copiedPowerLevel = 0; copiedPowerUses = 0; }

    public void beginAnomalyDamageStore(long untilTick) {
        anomalyDamageStoreUntil = untilTick;
        anomalyStoredDamage = 0.0F;
        anomalyChoiceUntil = 0L;
    }

    public void addAnomalyStoredDamage(float amount) {
        anomalyStoredDamage = Math.min(200.0F, anomalyStoredDamage + Math.max(0.0F, amount));
    }

    public void finishAnomalyDamageStore(long choiceUntil) {
        anomalyDamageStoreUntil = 0L;
        anomalyChoiceUntil = anomalyStoredDamage > 0.0F ? choiceUntil : 0L;
    }

    public void clearAnomalyStoredDamage() {
        anomalyDamageStoreUntil = 0L;
        anomalyStoredDamage = 0.0F;
        anomalyChoiceUntil = 0L;
    }

    public void setAnomalyBonusHealth(double amount, long untilTick, double baseBeforeBonus) {
        anomalyBonusHealth = Math.max(0.0, Math.min(20.0, amount));
        anomalyBonusHealthUntil = anomalyBonusHealth > 0.0 ? untilTick : 0L;
        anomalyHealthBaseBeforeBonus = anomalyBonusHealth > 0.0 ? Math.max(1.0, baseBeforeBonus) : -1.0;
    }

    public void clearAnomalyBonusHealth() {
        anomalyBonusHealth = 0.0;
        anomalyBonusHealthUntil = 0L;
        anomalyHealthBaseBeforeBonus = -1.0;
    }

    public void beginAnomalyReality(long untilTick, double x, double y, double z) {
        anomalyRealityUntil = untilTick;
        anomalyRealityReviveAvailable = true;
        anomalyRealityX = x; anomalyRealityY = y; anomalyRealityZ = z;
    }

    public void consumeAnomalyRealityRevive() { anomalyRealityReviveAvailable = false; }
    public void clearAnomalyReality() { anomalyRealityUntil = 0L; anomalyRealityReviveAvailable = false; }
    public void setAnomalyErrorCooldownUntil(long value) { anomalyErrorCooldownUntil = Math.max(0L, value); }

    public void resetAllCooldowns(long gameTime) {
        ensureArrays();
        for (int i = 0; i < cooldownUntil.length; i++) cooldownUntil[i] = gameTime;
        for (int i = 0; i < frozenCooldownTicks.length; i++) frozenCooldownTicks[i] = 0;
        clearCombo();
    }

    public void clearCooldown(int level, long gameTime) {
        ensureArrays();
        cooldownUntil[index(level)] = gameTime;
    }

    public void reduceAllCooldowns(long gameTime, int ticks) {
        ensureArrays();
        int reduction = Math.max(0, ticks);
        for (int i = 0; i < cooldownUntil.length; i++) {
            if (cooldownUntil[i] > gameTime) cooldownUntil[i] = Math.max(gameTime, cooldownUntil[i] - reduction);
        }
    }

    public int[] masteryCopy() {
        ensureArrays();
        return Arrays.copyOf(masteryUses, masteryUses.length);
    }

    private int index(int oneBasedLevel) {
        return Math.max(0, Math.min(STORAGE_SIZE - 1, oneBasedLevel - 1));
    }

    private void ensureArrays() {
        if (masteryUses == null || masteryUses.length != STORAGE_SIZE) {
            masteryUses = copyToSize(masteryUses, STORAGE_SIZE);
        }
        if (cooldownUntil == null || cooldownUntil.length != STORAGE_SIZE) {
            cooldownUntil = copyToSize(cooldownUntil, STORAGE_SIZE);
        }
        if (frozenCooldownTicks == null || frozenCooldownTicks.length != STORAGE_SIZE) {
            frozenCooldownTicks = copyToSize(frozenCooldownTicks, STORAGE_SIZE);
        }
    }

    private static int[] copyToSize(int[] source, int size) {
        int[] output = new int[size];
        if (source != null) System.arraycopy(source, 0, output, 0, Math.min(source.length, size));
        return output;
    }

    private static long[] copyToSize(long[] source, int size) {
        long[] output = new long[size];
        if (source != null) System.arraycopy(source, 0, output, 0, Math.min(source.length, size));
        return output;
    }
}
