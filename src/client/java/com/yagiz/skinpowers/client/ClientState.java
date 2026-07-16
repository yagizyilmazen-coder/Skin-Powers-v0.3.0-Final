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
    private static int[] masteryUses = new int[6];
    private static int xpLevel;
    private static String powerName = "-";
    private static boolean receivedState;
    private static int shakeTicks;
    private static float shakeStrength;

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
            masteryUses = normalizeMastery(state.masteryUses);
            xpLevel = Math.max(0, state.xpLevel);
            powerName = state.powerName == null ? PowerCatalog.powerName(powerClass, selectedPower) : state.powerName;
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

    public static void clientTick() {
        if (cooldownTicks > 0) cooldownTicks--;
        if (temporaryElytraTicks > 0) temporaryElytraTicks--;
        if (wardenHuntTicks > 0) wardenHuntTicks--;
        if (natureTreeTicks > 0) natureTreeTicks--;
        if (ancientChargeTicks > 0) ancientChargeTicks--;
        if (ancientExhaustionTicks > 0) ancientExhaustionTicks--;
        if (ancientChargeTicks <= 0) ancientChargeAvailable = false;
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
        masteryUses = new int[6];
        xpLevel = 0;
        powerName = "-";
        receivedState = false;
        shakeTicks = 0;
        shakeStrength = 0.0F;
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
    public static int xpLevel() { return xpLevel; }
    public static String powerName() { return powerName; }
    public static boolean receivedState() { return receivedState; }
    public static int shakeTicks() { return shakeTicks; }
    public static float shakeStrength() { return shakeStrength; }

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
        private int[] masteryUses;
        private int xpLevel;
        private String powerName;
    }
}
