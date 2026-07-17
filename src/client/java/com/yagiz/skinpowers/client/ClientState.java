package com.yagiz.skinpowers.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yagiz.skinpowers.PowerCatalog;
import com.yagiz.skinpowers.PowerClass;

public final class ClientState {
    private static final Gson GSON = new GsonBuilder().create();
    private static PowerClass powerClass = PowerClass.NONE;
    private static int unlockedLevel;
    private static int selectedPower = 1;
    private static int cooldownTicks;
    private static boolean passiveEnabled;
    private static boolean visionEnabled;
    private static int temporaryElytraTicks;
    private static int wardenHuntTicks;
    private static int natureTreeTicks;
    private static int ancientChargeTicks;
    private static int ancientExhaustionTicks;
    private static boolean ancientChargeAvailable;
    private static boolean comboModeEnabled;
    private static int comboTicks;
    private static String comboName = "";
    private static String comboNextPowerName = "";
    private static int[] masteryUses = new int[6];
    private static int xpLevel;
    private static String powerName = "-";
    private static String copiedPowerName = "";
    private static String copiedPowerDescription = "";
    private static int anomalyStoreTicks;
    private static int anomalyChoiceTicks;
    private static float anomalyStoredDamage;
    private static int anomalyBonusHealthTicks;
    private static double anomalyBonusHealth;
    private static int dragonScalesTicks;
    private static int dragonScaleCharges;
    private static int dragonFormTicks;
    private static float awakeningEnergy;
    private static int classAwakeningTicks;
    private static boolean duelActive;
    private static boolean battlePanelVisible;
    private static String battleMode = "";
    private static String battleOpponentName = "";
    private static String battleOpponentClass = "";
    private static float battleOpponentHealth;
    private static float battleOpponentMaxHealth;
    private static float battleOpponentAwakening;
    private static String battleDetail = "";
    private static boolean receivedState;
    private static int shakeTicks;
    private static float shakeStrength;
    private static int castPulseTicks;
    private static float castPulseStrength;
    private static String castPulseClass = "NONE";

    private ClientState() {}

    public static void updateFromJson(String json) {
        try {
            State state = GSON.fromJson(json, State.class);
            if (state == null) return;
            powerClass = PowerClass.safeValueOf(state.powerClass);
            int maximum = PowerCatalog.maxLevel(powerClass);
            unlockedLevel = Math.max(0, Math.min(maximum, state.unlockedLevel));
            selectedPower = Math.max(1, Math.min(maximum, state.selectedPower));
            cooldownTicks = Math.max(0, state.cooldownTicks);
            passiveEnabled = state.passiveEnabled;
            visionEnabled = state.visionEnabled;
            temporaryElytraTicks = Math.max(0, state.temporaryElytraTicks);
            wardenHuntTicks = Math.max(0, state.wardenHuntTicks);
            natureTreeTicks = Math.max(0, state.natureTreeTicks);
            ancientChargeTicks = Math.max(0, state.ancientChargeTicks);
            ancientExhaustionTicks = Math.max(0, state.ancientExhaustionTicks);
            ancientChargeAvailable = state.ancientChargeAvailable && ancientChargeTicks > 0;
            comboModeEnabled = state.comboModeEnabled;
            comboTicks = Math.max(0, state.comboTicks);
            comboName = state.comboName == null ? "" : state.comboName;
            comboNextPowerName = state.comboNextPowerName == null ? "" : state.comboNextPowerName;
            masteryUses = normalizeMastery(state.masteryUses);
            xpLevel = Math.max(0, state.xpLevel);
            powerName = state.powerName == null ? PowerCatalog.powerName(powerClass, selectedPower) : state.powerName;
            copiedPowerName = state.copiedPowerName == null ? "" : state.copiedPowerName;
            copiedPowerDescription = state.copiedPowerDescription == null ? "" : state.copiedPowerDescription;
            anomalyStoreTicks = Math.max(0, state.anomalyStoreTicks);
            anomalyChoiceTicks = Math.max(0, state.anomalyChoiceTicks);
            anomalyStoredDamage = Math.max(0.0F, state.anomalyStoredDamage);
            anomalyBonusHealthTicks = Math.max(0, state.anomalyBonusHealthTicks);
            anomalyBonusHealth = Math.max(0.0D, state.anomalyBonusHealth);
            dragonScalesTicks = Math.max(0, state.dragonScalesTicks);
            dragonScaleCharges = Math.max(0, state.dragonScaleCharges);
            dragonFormTicks = Math.max(0, state.dragonFormTicks);
            awakeningEnergy = Math.max(0.0F, Math.min(100.0F, state.awakeningEnergy));
            classAwakeningTicks = Math.max(0, state.classAwakeningTicks);
            duelActive = state.duelActive;
            battlePanelVisible = state.battlePanelVisible;
            battleMode = state.battleMode == null ? "" : state.battleMode;
            battleOpponentName = state.battleOpponentName == null ? "" : state.battleOpponentName;
            battleOpponentClass = state.battleOpponentClass == null ? "" : state.battleOpponentClass;
            battleOpponentHealth = Math.max(0.0F, state.battleOpponentHealth);
            battleOpponentMaxHealth = Math.max(0.0F, state.battleOpponentMaxHealth);
            battleOpponentAwakening = Math.max(0.0F, Math.min(100.0F, state.battleOpponentAwakening));
            battleDetail = state.battleDetail == null ? "" : state.battleDetail;
            receivedState = true;
        } catch (RuntimeException ignored) {
            // Bozuk/uyumsuz paket istemciyi düşürmesin.
        }
    }

    private static int[] normalizeMastery(int[] input) {
        int[] result = new int[6];
        if (input != null) System.arraycopy(input, 0, result, 0, Math.min(input.length, result.length));
        return result;
    }

    public static void startShake(float strength, int durationTicks) {
        if (durationTicks <= 0 || strength <= 0.0F) return;
        shakeTicks = Math.max(shakeTicks, durationTicks);
        shakeStrength = Math.min(2.4F, Math.max(strength, shakeStrength * 0.88F + strength * 0.22F));
    }

    public static void startCastPulse(String powerClassName, float strength, int durationTicks) {
        castPulseClass = powerClassName == null ? "NONE" : powerClassName;
        castPulseStrength = Math.max(0.1F, Math.min(2.0F, strength));
        castPulseTicks = Math.max(castPulseTicks, Math.max(1, durationTicks));
    }

    public static void clientTick() {
        if (cooldownTicks > 0) cooldownTicks--;
        if (temporaryElytraTicks > 0) temporaryElytraTicks--;
        if (wardenHuntTicks > 0) wardenHuntTicks--;
        if (natureTreeTicks > 0) natureTreeTicks--;
        if (ancientChargeTicks > 0) ancientChargeTicks--;
        if (ancientExhaustionTicks > 0) ancientExhaustionTicks--;
        if (comboTicks > 0) comboTicks--;
        if (anomalyStoreTicks > 0) anomalyStoreTicks--;
        if (anomalyChoiceTicks > 0) anomalyChoiceTicks--;
        if (anomalyBonusHealthTicks > 0) anomalyBonusHealthTicks--;
        if (dragonScalesTicks > 0) dragonScalesTicks--;
        if (dragonFormTicks > 0) dragonFormTicks--;
        if (classAwakeningTicks > 0) classAwakeningTicks--;
        if (comboTicks <= 0) { comboName = ""; comboNextPowerName = ""; }
        if (ancientChargeTicks <= 0) ancientChargeAvailable = false;
        if (castPulseTicks > 0) {
            castPulseTicks--;
            if (castPulseTicks == 0) { castPulseStrength = 0.0F; castPulseClass = "NONE"; }
        }
        if (shakeTicks > 0) {
            shakeTicks--;
            if (shakeTicks == 0) shakeStrength = 0.0F;
        }
    }

    public static void reset() {
        powerClass = PowerClass.NONE;
        unlockedLevel = 0;
        selectedPower = 1;
        cooldownTicks = 0;
        passiveEnabled = false;
        visionEnabled = false;
        temporaryElytraTicks = 0;
        wardenHuntTicks = 0;
        natureTreeTicks = 0;
        ancientChargeTicks = 0;
        ancientExhaustionTicks = 0;
        ancientChargeAvailable = false;
        comboModeEnabled = false;
        comboTicks = 0;
        comboName = "";
        comboNextPowerName = "";
        masteryUses = new int[6];
        xpLevel = 0;
        powerName = "-";
        copiedPowerName = "";
        copiedPowerDescription = "";
        anomalyStoreTicks = 0;
        anomalyChoiceTicks = 0;
        anomalyStoredDamage = 0.0F;
        anomalyBonusHealthTicks = 0;
        anomalyBonusHealth = 0.0D;
        dragonScalesTicks = 0;
        dragonScaleCharges = 0;
        dragonFormTicks = 0;
        awakeningEnergy = 0.0F;
        classAwakeningTicks = 0;
        duelActive = false;
        battlePanelVisible = false;
        battleMode = "";
        battleOpponentName = "";
        battleOpponentClass = "";
        battleOpponentHealth = 0.0F;
        battleOpponentMaxHealth = 0.0F;
        battleOpponentAwakening = 0.0F;
        battleDetail = "";
        receivedState = false;
        shakeTicks = 0;
        shakeStrength = 0.0F;
        castPulseTicks = 0;
        castPulseStrength = 0.0F;
        castPulseClass = "NONE";
    }

    public static PowerClass powerClass() { return powerClass; }
    public static int unlockedLevel() { return unlockedLevel; }
    public static int selectedPower() { return selectedPower; }
    public static int cooldownTicks() { return cooldownTicks; }
    public static boolean passiveEnabled() { return passiveEnabled; }
    public static boolean visionEnabled() { return visionEnabled; }
    public static int temporaryElytraTicks() { return temporaryElytraTicks; }
    public static int wardenHuntTicks() { return wardenHuntTicks; }
    public static int natureTreeTicks() { return natureTreeTicks; }
    public static int ancientChargeTicks() { return ancientChargeTicks; }
    public static int ancientExhaustionTicks() { return ancientExhaustionTicks; }
    public static boolean ancientChargeAvailable() { return ancientChargeAvailable; }
    public static boolean comboModeEnabled() { return comboModeEnabled; }
    public static int comboTicks() { return comboTicks; }
    public static String comboName() { return comboName; }
    public static String comboNextPowerName() { return comboNextPowerName; }
    public static int xpLevel() { return xpLevel; }
    public static String powerName() { return powerName; }
    public static String copiedPowerName() { return copiedPowerName; }
    public static String copiedPowerDescription() { return copiedPowerDescription; }
    public static int anomalyStoreTicks() { return anomalyStoreTicks; }
    public static int anomalyChoiceTicks() { return anomalyChoiceTicks; }
    public static float anomalyStoredDamage() { return anomalyStoredDamage; }
    public static int anomalyBonusHealthTicks() { return anomalyBonusHealthTicks; }
    public static double anomalyBonusHealth() { return anomalyBonusHealth; }
    public static int dragonScalesTicks() { return dragonScalesTicks; }
    public static int dragonScaleCharges() { return dragonScaleCharges; }
    public static int dragonFormTicks() { return dragonFormTicks; }
    public static float awakeningEnergy() { return awakeningEnergy; }
    public static int classAwakeningTicks() { return classAwakeningTicks; }
    public static boolean duelActive() { return duelActive; }
    public static boolean battlePanelVisible() { return battlePanelVisible; }
    public static String battleMode() { return battleMode; }
    public static String battleOpponentName() { return battleOpponentName; }
    public static String battleOpponentClass() { return battleOpponentClass; }
    public static float battleOpponentHealth() { return battleOpponentHealth; }
    public static float battleOpponentMaxHealth() { return battleOpponentMaxHealth; }
    public static float battleOpponentAwakening() { return battleOpponentAwakening; }
    public static String battleDetail() { return battleDetail; }
    public static boolean receivedState() { return receivedState; }
    public static int shakeTicks() { return shakeTicks; }
    public static float shakeStrength() { return shakeStrength; }
    public static int castPulseTicks() { return castPulseTicks; }
    public static float castPulseStrength() { return castPulseStrength; }
    public static PowerClass castPulseClass() { return PowerClass.safeValueOf(castPulseClass); }

    public static int masteryUses(int level) {
        return masteryUses[Math.max(0, Math.min(5, level - 1))];
    }

    public static int masteryStage(int level) {
        return PowerCatalog.masteryStage(masteryUses(level));
    }

    private static final class State {
        private String powerClass;
        private int unlockedLevel;
        private int selectedPower;
        private int cooldownTicks;
        private boolean passiveEnabled;
        private boolean visionEnabled;
        private int temporaryElytraTicks;
        private int wardenHuntTicks;
        private int natureTreeTicks;
        private int ancientChargeTicks;
        private int ancientExhaustionTicks;
        private boolean ancientChargeAvailable;
        private boolean comboModeEnabled;
        private int comboTicks;
        private String comboName;
        private String comboNextPowerName;
        private int[] masteryUses;
        private int xpLevel;
        private String powerName;
        private String copiedPowerName;
        private String copiedPowerDescription;
        private int anomalyStoreTicks;
        private int anomalyChoiceTicks;
        private float anomalyStoredDamage;
        private int anomalyBonusHealthTicks;
        private double anomalyBonusHealth;
        private int dragonScalesTicks;
        private int dragonScaleCharges;
        private int dragonFormTicks;
        private float awakeningEnergy;
        private int classAwakeningTicks;
        private boolean duelActive;
        private boolean battlePanelVisible;
        private String battleMode;
        private String battleOpponentName;
        private String battleOpponentClass;
        private float battleOpponentHealth;
        private float battleOpponentMaxHealth;
        private float battleOpponentAwakening;
        private String battleDetail;
    }
}
