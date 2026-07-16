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

    // Antik Şehir Şarjı ortak durumu.
    private long ancientChargeStartedAt = 0L;
    private long ancientChargeUntil = 0L;
    private long ancientExhaustionUntil = 0L;
    private boolean ancientChargeAvailable = false;

    // Şarjla başlatılan süreli güçlerin mor/kuvvetli hâli şarj bittikten sonra da sürer.
    private boolean chargedAwakening = false;
    private boolean chargedFireRing = false;
    private boolean chargedTemporaryElytra = false;
    private boolean chargedWardenHunt = false;

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
    public long ancientChargeStartedAt() { return ancientChargeStartedAt; }
    public long ancientChargeUntil() { return ancientChargeUntil; }
    public long ancientExhaustionUntil() { return ancientExhaustionUntil; }
    public boolean ancientChargeAvailable() { return ancientChargeAvailable; }
    public boolean chargedAwakening() { return chargedAwakening; }
    public boolean chargedFireRing() { return chargedFireRing; }
    public boolean chargedTemporaryElytra() { return chargedTemporaryElytra; }
    public boolean chargedWardenHunt() { return chargedWardenHunt; }

    public void chooseClass(PowerClass value) {
        if (powerClass() != PowerClass.NONE || value == null || value == PowerClass.NONE) return;
        powerClass = value;
        selectedPower = 1;
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
        ancientChargeStartedAt = 0L;
        ancientChargeUntil = 0L;
        ancientExhaustionUntil = 0L;
        ancientChargeAvailable = false;
        chargedAwakening = false;
        chargedFireRing = false;
        chargedTemporaryElytra = false;
        chargedWardenHunt = false;
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
        if (ancientChargeActive(gameTime) && level != 6) return 0;
        return (int) Math.max(0L, cooldownUntil(level) - gameTime);
    }

    public void setCooldown(int level, long gameTime, int ticks) {
        ensureArrays();
        cooldownUntil[index(level)] = gameTime + Math.max(0, ticks);
    }

    public boolean ancientChargeActive(long gameTime) {
        return ancientChargeAvailable && ancientChargeUntil > gameTime;
    }

    public boolean ancientExhausted(long gameTime) {
        return ancientExhaustionUntil > gameTime;
    }

    /** Şarj verildiğinde mevcut bekleme sürelerini saklar ve geçici olarak sıfırlar. */
    public void beginAncientCharge(long gameTime, int durationTicks) {
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
    }

    /** Kullanılan güç yeni cooldown'unu korur; diğer güçlerin eski cooldown'ları kaldığı yerden devam eder. */
    public void consumeAncientCharge(long gameTime, int usedPower) {
        ensureArrays();
        int usedIndex = index(usedPower);
        long usedCooldown = cooldownUntil[usedIndex];
        for (int i = 0; i < STORAGE_SIZE; i++) {
            if (i == 5 && powerClass() == PowerClass.WARDEN) {
                frozenCooldownTicks[i] = 0;
                continue;
            }
            cooldownUntil[i] = gameTime + Math.max(0, frozenCooldownTicks[i]);
            frozenCooldownTicks[i] = 0;
        }
        cooldownUntil[usedIndex] = Math.max(gameTime, usedCooldown);
        ancientChargeStartedAt = 0L;
        ancientChargeUntil = 0L;
        ancientChargeAvailable = false;
    }

    /** Şarj kullanılmadan biterse tüm eski cooldown'lar geri döner. */
    public void expireAncientCharge(long gameTime) {
        ensureArrays();
        for (int i = 0; i < STORAGE_SIZE; i++) {
            if (i == 5 && powerClass() == PowerClass.WARDEN) {
                frozenCooldownTicks[i] = 0;
                continue;
            }
            cooldownUntil[i] = gameTime + Math.max(0, frozenCooldownTicks[i]);
            frozenCooldownTicks[i] = 0;
        }
        ancientChargeStartedAt = 0L;
        ancientChargeUntil = 0L;
        ancientChargeAvailable = false;
    }

    public void setAncientExhaustionUntil(long value) { ancientExhaustionUntil = value; }
    public void clearAncientExhaustion() { ancientExhaustionUntil = 0L; }

    public void togglePassive() { passiveEnabled = !passiveEnabled; }
    public void toggleVision() { visionEnabled = !visionEnabled; }
    public void setVisionEnabled(boolean value) { visionEnabled = value; }
    public void setAwakeningUntil(long value) { awakeningUntil = value; }
    public void setFireRingUntil(long value) { fireRingUntil = value; }
    public void setSkyImpactSlowUntil(long value) { skyImpactSlowUntil = value; }
    public void setTemporaryElytraUntil(long value) { temporaryElytraUntil = value; }
    public void setWardenHuntUntil(long value) { wardenHuntUntil = value; }
    public void setNatureTreeUntil(long value) { natureTreeUntil = value; }
    public void setChargedAwakening(boolean value) { chargedAwakening = value; }
    public void setChargedFireRing(boolean value) { chargedFireRing = value; }
    public void setChargedTemporaryElytra(boolean value) { chargedTemporaryElytra = value; }
    public void setChargedWardenHunt(boolean value) { chargedWardenHunt = value; }

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
