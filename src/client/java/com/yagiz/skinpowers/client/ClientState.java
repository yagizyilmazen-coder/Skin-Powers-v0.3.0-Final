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
    private static int waterArmorTicks;
    private static int[] masteryUses = new int[5];
    private static int xpLevel;
    private static String powerName = "-";
    private static boolean receivedState;

    private ClientState() {}

    public static void updateFromJson(String json) {
        try {
            State state = GSON.fromJson(json, State.class);
            if (state == null) return;
            powerClass = PowerClass.safeValueOf(state.powerClass);
            unlockedLevel = Math.max(0, Math.min(5, state.unlockedLevel));
            selectedPower = Math.max(1, Math.min(5, state.selectedPower));
            cooldownTicks = Math.max(0, state.cooldownTicks);
            passiveEnabled = state.passiveEnabled;
            visionEnabled = state.visionEnabled;
            temporaryElytraTicks = Math.max(0, state.temporaryElytraTicks);
            wardenHuntTicks = Math.max(0, state.wardenHuntTicks);
            waterArmorTicks = Math.max(0, state.waterArmorTicks);
            masteryUses = state.masteryUses == null || state.masteryUses.length != 5 ? new int[5] : state.masteryUses;
            xpLevel = Math.max(0, state.xpLevel);
            powerName = state.powerName == null ? PowerCatalog.powerName(powerClass, selectedPower) : state.powerName;
            receivedState = true;
        } catch (RuntimeException ignored) {
            // Bozuk/uyumsuz paket istemciyi düşürmesin.
        }
    }

    public static void clientTick() {
        if (cooldownTicks > 0) cooldownTicks--;
        if (temporaryElytraTicks > 0) temporaryElytraTicks--;
        if (wardenHuntTicks > 0) wardenHuntTicks--;
        if (waterArmorTicks > 0) waterArmorTicks--;
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
        waterArmorTicks = 0;
        masteryUses = new int[5];
        xpLevel = 0;
        powerName = "-";
        receivedState = false;
    }

    public static PowerClass powerClass() { return powerClass; }
    public static int unlockedLevel() { return unlockedLevel; }
    public static int selectedPower() { return selectedPower; }
    public static int cooldownTicks() { return cooldownTicks; }
    public static boolean passiveEnabled() { return passiveEnabled; }
    public static boolean visionEnabled() { return visionEnabled; }
    public static int temporaryElytraTicks() { return temporaryElytraTicks; }
    public static int wardenHuntTicks() { return wardenHuntTicks; }
    public static int waterArmorTicks() { return waterArmorTicks; }
    public static int xpLevel() { return xpLevel; }
    public static String powerName() { return powerName; }
    public static boolean receivedState() { return receivedState; }

    public static int masteryUses(int level) {
        return masteryUses[Math.max(0, Math.min(4, level - 1))];
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
        private int waterArmorTicks;
        private int[] masteryUses;
        private int xpLevel;
        private String powerName;
    }
}
