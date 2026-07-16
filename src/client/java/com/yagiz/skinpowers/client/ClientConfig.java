package com.yagiz.skinpowers.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("skinpowers-client.json");
    private static ClientConfig instance = new ClientConfig();

    private int hudScalePercent = 80;
    private boolean hudRight = false;
    private boolean menuAnimations = true;
    private boolean scanAnimation = true;
    private int screenShakePercent = 100;
    private boolean performanceMode = false;
    private int cardAnimationSpeedPercent = 100;

    private ClientConfig() {}

    public static void load() {
        if (!Files.isRegularFile(PATH)) return;
        try (Reader reader = Files.newBufferedReader(PATH)) {
            ClientConfig loaded = GSON.fromJson(reader, ClientConfig.class);
            if (loaded != null) instance = loaded;
            instance.sanitize();
        } catch (IOException | RuntimeException ignored) {
            instance = new ClientConfig();
        }
    }

    public static ClientConfig get() {
        return instance;
    }

    public void save() {
        sanitize();
        try {
            Files.createDirectories(PATH.getParent());
            Path temp = PATH.resolveSibling(PATH.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp)) {
                GSON.toJson(this, writer);
            }
            try {
                Files.move(temp, PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(temp, PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // Ayar dosyası yazılamazsa oyun çalışmaya devam eder.
        }
    }

    public void resetDefaults() {
        hudScalePercent = 80;
        hudRight = false;
        menuAnimations = true;
        scanAnimation = true;
        screenShakePercent = 100;
        performanceMode = false;
        cardAnimationSpeedPercent = 100;
        save();
    }

    private void sanitize() {
        hudScalePercent = clampStep(hudScalePercent, 60, 120, 10);
        screenShakePercent = clampStep(screenShakePercent, 0, 100, 25);
        cardAnimationSpeedPercent = clampStep(cardAnimationSpeedPercent, 50, 150, 25);
    }

    private static int clampStep(int value, int min, int max, int step) {
        int clamped = Math.max(min, Math.min(max, value));
        return min + Math.round((clamped - min) / (float) step) * step;
    }

    public int hudScalePercent() { return hudScalePercent; }
    public boolean hudRight() { return hudRight; }
    public boolean menuAnimations() { return menuAnimations; }
    public boolean scanAnimation() { return scanAnimation; }
    public int screenShakePercent() { return screenShakePercent; }
    public boolean performanceMode() { return performanceMode; }
    public int cardAnimationSpeedPercent() { return cardAnimationSpeedPercent; }

    public void cycleHudScale() {
        hudScalePercent += 10;
        if (hudScalePercent > 120) hudScalePercent = 60;
        save();
    }

    public void toggleHudRight() { hudRight = !hudRight; save(); }
    public void toggleMenuAnimations() { menuAnimations = !menuAnimations; save(); }
    public void toggleScanAnimation() { scanAnimation = !scanAnimation; save(); }
    public void cycleScreenShake() {
        screenShakePercent += 25;
        if (screenShakePercent > 100) screenShakePercent = 0;
        save();
    }
    public void togglePerformanceMode() { performanceMode = !performanceMode; save(); }
    public void cycleCardSpeed() {
        cardAnimationSpeedPercent += 25;
        if (cardAnimationSpeedPercent > 150) cardAnimationSpeedPercent = 50;
        save();
    }
}
