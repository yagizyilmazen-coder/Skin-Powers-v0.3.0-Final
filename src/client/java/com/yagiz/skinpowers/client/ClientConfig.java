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

    // HUD
    private int hudScalePercent = 80;
    private boolean hudRight = false;
    private int hudVerticalOffset = 0;
    private int notificationScalePercent = 85;
    private boolean showAwakeningBar = true;
    private boolean compactHud = false;
    private boolean showBattlePanel = true;

    // Animasyon ve görsel kalite
    private boolean menuAnimations = true;
    private boolean scanAnimation = true;
    private int screenShakePercent = 75;
    private int cardAnimationSpeedPercent = 100;
    private int particleDensityPercent = 100;
    private int glowPercent = 75;
    private boolean animatedBackgrounds = true;
    private boolean cardParallax = true;

    // Erişilebilirlik / performans
    private boolean performanceMode = false;
    private boolean reducedFirstPersonEffects = false;
    private boolean photosensitiveMode = false;

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

    public static ClientConfig get() { return instance; }

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
        hudVerticalOffset = 0;
        notificationScalePercent = 85;
        showAwakeningBar = true;
        compactHud = false;
        showBattlePanel = true;
        menuAnimations = true;
        scanAnimation = true;
        screenShakePercent = 75;
        cardAnimationSpeedPercent = 100;
        particleDensityPercent = 100;
        glowPercent = 75;
        animatedBackgrounds = true;
        cardParallax = true;
        performanceMode = false;
        reducedFirstPersonEffects = false;
        photosensitiveMode = false;
        save();
    }

    private void sanitize() {
        hudScalePercent = clampStep(hudScalePercent, 60, 120, 10);
        hudVerticalOffset = clampStep(hudVerticalOffset, 0, 120, 10);
        notificationScalePercent = clampStep(notificationScalePercent, 60, 120, 10);
        screenShakePercent = clampStep(screenShakePercent, 0, 100, 25);
        cardAnimationSpeedPercent = clampStep(cardAnimationSpeedPercent, 50, 150, 25);
        particleDensityPercent = clampStep(particleDensityPercent, 25, 100, 25);
        glowPercent = clampStep(glowPercent, 0, 100, 25);
        if (photosensitiveMode) {
            screenShakePercent = Math.min(screenShakePercent, 25);
            glowPercent = Math.min(glowPercent, 25);
        }
    }

    private static int clampStep(int value, int min, int max, int step) {
        int clamped = Math.max(min, Math.min(max, value));
        return min + Math.round((clamped - min) / (float) step) * step;
    }

    public int hudScalePercent() { return hudScalePercent; }
    public boolean hudRight() { return hudRight; }
    public int hudVerticalOffset() { return hudVerticalOffset; }
    public int notificationScalePercent() { return notificationScalePercent; }
    public boolean showAwakeningBar() { return showAwakeningBar; }
    public boolean compactHud() { return compactHud; }
    public boolean showBattlePanel() { return showBattlePanel; }
    public boolean menuAnimations() { return menuAnimations; }
    public boolean scanAnimation() { return scanAnimation; }
    public int screenShakePercent() { return screenShakePercent; }
    public int cardAnimationSpeedPercent() { return cardAnimationSpeedPercent; }
    public int particleDensityPercent() { return particleDensityPercent; }
    public int glowPercent() { return glowPercent; }
    public boolean animatedBackgrounds() { return animatedBackgrounds; }
    public boolean cardParallax() { return cardParallax; }
    public boolean performanceMode() { return performanceMode; }
    public boolean reducedFirstPersonEffects() { return reducedFirstPersonEffects; }
    public boolean photosensitiveMode() { return photosensitiveMode; }

    public void cycleHudScale() { hudScalePercent = hudScalePercent >= 120 ? 60 : hudScalePercent + 10; save(); }
    public void toggleHudRight() { hudRight = !hudRight; save(); }
    public void cycleHudVerticalOffset() { hudVerticalOffset = hudVerticalOffset >= 120 ? 0 : hudVerticalOffset + 10; save(); }
    public void cycleNotificationScale() { notificationScalePercent = notificationScalePercent >= 120 ? 60 : notificationScalePercent + 10; save(); }
    public void toggleAwakeningBar() { showAwakeningBar = !showAwakeningBar; save(); }
    public void toggleCompactHud() { compactHud = !compactHud; save(); }
    public void toggleBattlePanel() { showBattlePanel = !showBattlePanel; save(); }
    public void toggleMenuAnimations() { menuAnimations = !menuAnimations; save(); }
    public void toggleScanAnimation() { scanAnimation = !scanAnimation; save(); }
    public void cycleScreenShake() { screenShakePercent = screenShakePercent >= 100 ? 0 : screenShakePercent + 25; save(); }
    public void cycleCardSpeed() { cardAnimationSpeedPercent = cardAnimationSpeedPercent >= 150 ? 50 : cardAnimationSpeedPercent + 25; save(); }
    public void cycleParticleDensity() { particleDensityPercent = particleDensityPercent >= 100 ? 25 : particleDensityPercent + 25; save(); }
    public void cycleGlow() { glowPercent = glowPercent >= 100 ? 0 : glowPercent + 25; save(); }
    public void toggleAnimatedBackgrounds() { animatedBackgrounds = !animatedBackgrounds; save(); }
    public void toggleCardParallax() { cardParallax = !cardParallax; save(); }
    public void togglePerformanceMode() { performanceMode = !performanceMode; save(); }
    public void toggleReducedFirstPersonEffects() { reducedFirstPersonEffects = !reducedFirstPersonEffects; save(); }
    public void togglePhotosensitiveMode() { photosensitiveMode = !photosensitiveMode; sanitize(); save(); }
}
