package com.yagiz.skinpowers;

import java.util.Arrays;

public final class PlayerPowerData {
    private PowerClass powerClass = PowerClass.NONE;
    private int unlockedLevel = 0;
    private int selectedPower = 1;
    private int[] masteryUses = new int[5];
    private long[] cooldownUntil = new long[5];
    private boolean passiveEnabled = false;
    private boolean visionEnabled = false;
    private long awakeningUntil = 0L;
    private long fireRingUntil = 0L;
    private long skyImpactSlowUntil = 0L;

    public PowerClass powerClass() { return powerClass == null ? PowerClass.NONE : powerClass; }
    public int unlockedLevel() { return Math.max(0, Math.min(5, unlockedLevel)); }
    public int selectedPower() { return Math.max(1, Math.min(5, selectedPower)); }
    public boolean passiveEnabled() { return passiveEnabled; }
    public boolean visionEnabled() { return visionEnabled; }
    public long awakeningUntil() { return awakeningUntil; }
    public long fireRingUntil() { return fireRingUntil; }
    public long skyImpactSlowUntil() { return skyImpactSlowUntil; }

    public void chooseClass(PowerClass value) {
        if (powerClass() != PowerClass.NONE || value == null || value == PowerClass.NONE) return;
        powerClass = value;
        selectedPower = 1;
    }

    public void reset() {
        powerClass = PowerClass.NONE;
        unlockedLevel = 0;
        selectedPower = 1;
        masteryUses = new int[5];
        cooldownUntil = new long[5];
        passiveEnabled = false;
        visionEnabled = false;
        awakeningUntil = 0L;
        fireRingUntil = 0L;
        skyImpactSlowUntil = 0L;
    }

    public void unlockNextLevel() {
        if (unlockedLevel < 5) {
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
        return masteryUses[Math.max(0, Math.min(4, level - 1))];
    }

    public int masteryStage(int level) {
        return PowerCatalog.masteryStage(masteryUses(level));
    }

    public void addMasteryUse(int level) {
        ensureArrays();
        int index = Math.max(0, Math.min(4, level - 1));
        masteryUses[index] = Math.min(9999, masteryUses[index] + 1);
    }

    public long cooldownUntil(int level) {
        ensureArrays();
        return cooldownUntil[Math.max(0, Math.min(4, level - 1))];
    }

    public int cooldownRemaining(int level, long gameTime) {
        return (int) Math.max(0L, cooldownUntil(level) - gameTime);
    }

    public void setCooldown(int level, long gameTime, int ticks) {
        ensureArrays();
        cooldownUntil[Math.max(0, Math.min(4, level - 1))] = gameTime + Math.max(0, ticks);
    }

    public void togglePassive() { passiveEnabled = !passiveEnabled; }
    public void toggleVision() { visionEnabled = !visionEnabled; }
    public void setAwakeningUntil(long value) { awakeningUntil = value; }
    public void setFireRingUntil(long value) { fireRingUntil = value; }
    public void setSkyImpactSlowUntil(long value) { skyImpactSlowUntil = value; }

    public int[] masteryCopy() {
        ensureArrays();
        return Arrays.copyOf(masteryUses, masteryUses.length);
    }

    private void ensureArrays() {
        if (masteryUses == null || masteryUses.length != 5) masteryUses = new int[5];
        if (cooldownUntil == null || cooldownUntil.length != 5) cooldownUntil = new long[5];
    }
}
