package com.yagiz.skinpowers.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Üç sekmeli, küçük ekranlarda da taşmayan ayrıntılı Mod Menu ayar ekranı. */
public final class SkinPowersSettingsScreen extends Screen {
    private final Screen parent;
    private int page;

    public SkinPowersSettingsScreen(Screen parent) {
        this(parent, 0);
    }

    private SkinPowersSettingsScreen(Screen parent, int page) {
        super(Component.literal("Skin Powers Ayarları"));
        this.parent = parent;
        this.page = Math.max(0, Math.min(2, page));
    }

    @Override
    protected void init() {
        rebuildButtons();
    }

    private void rebuildButtons() {
        ClientConfig config = ClientConfig.get();
        int tabGap = 4;
        int tabWidth = Math.max(74, Math.min(116, (width - 24 - tabGap * 2) / 3));
        int tabsTotal = tabWidth * 3 + tabGap * 2;
        int tabX = (width - tabsTotal) / 2;
        String[] tabs = {"HUD", "ANİMASYON", "ERİŞİLEBİLİRLİK"};
        for (int i = 0; i < 3; i++) {
            final int target = i;
            String label = (page == i ? "▶ " : "") + tabs[i];
            addRenderableWidget(Button.builder(Component.literal(label), button -> {
                if (minecraft != null) minecraft.setScreen(new SkinPowersSettingsScreen(parent, target));
            }).bounds(tabX + i * (tabWidth + tabGap), 43, tabWidth, 20).build());
        }

        boolean twoColumns = width >= 360;
        int gap = 8;
        int buttonWidth = twoColumns ? Math.max(138, Math.min(238, (width - 34 - gap) / 2)) : Math.max(166, Math.min(260, width - 28));
        int columnsWidth = twoColumns ? buttonWidth * 2 + gap : buttonWidth;
        int startX = (width - columnsWidth) / 2;
        int top = 72;
        int rowGap = height < 330 ? 26 : 30;

        if (page == 0) {
            addSettingButton(0, buttonWidth, startX, top, rowGap, twoColumns, "HUD Boyutu: %" + config.hudScalePercent(), config::cycleHudScale);
            addSettingButton(1, buttonWidth, startX, top, rowGap, twoColumns, "HUD Konumu: " + (config.hudRight() ? "SAĞ" : "SOL"), config::toggleHudRight);
            addSettingButton(2, buttonWidth, startX, top, rowGap, twoColumns, "Dikey Kaydırma: " + config.hudVerticalOffset(), config::cycleHudVerticalOffset);
            addSettingButton(3, buttonWidth, startX, top, rowGap, twoColumns, "Bildirim Boyutu: %" + config.notificationScalePercent(), config::cycleNotificationScale);
            addSettingButton(4, buttonWidth, startX, top, rowGap, twoColumns, "Uyanış Çubuğu: " + onOff(config.showAwakeningBar()), config::toggleAwakeningBar);
            addSettingButton(5, buttonWidth, startX, top, rowGap, twoColumns, "Kompakt HUD: " + onOff(config.compactHud()), config::toggleCompactHud);
        } else if (page == 1) {
            addSettingButton(0, buttonWidth, startX, top, rowGap, twoColumns, "Menü Animasyonları: " + onOff(config.menuAnimations()), config::toggleMenuAnimations);
            addSettingButton(1, buttonWidth, startX, top, rowGap, twoColumns, "Kart Hızı: %" + config.cardAnimationSpeedPercent(), config::cycleCardSpeed);
            addSettingButton(2, buttonWidth, startX, top, rowGap, twoColumns, "Parçacık Yoğunluğu: %" + config.particleDensityPercent(), config::cycleParticleDensity);
            addSettingButton(3, buttonWidth, startX, top, rowGap, twoColumns, "Parlama: %" + config.glowPercent(), config::cycleGlow);
            addSettingButton(4, buttonWidth, startX, top, rowGap, twoColumns, "Hareketli Arka Plan: " + onOff(config.animatedBackgrounds()), config::toggleAnimatedBackgrounds);
            addSettingButton(5, buttonWidth, startX, top, rowGap, twoColumns, "Kart Derinliği: " + onOff(config.cardParallax()), config::toggleCardParallax);
            addSettingButton(6, buttonWidth, startX, top, rowGap, twoColumns, "Skin Tarama Çizgisi: " + onOff(config.scanAnimation()), config::toggleScanAnimation);
            addSettingButton(7, buttonWidth, startX, top, rowGap, twoColumns, "Ekran Sarsıntısı: %" + config.screenShakePercent(), config::cycleScreenShake);
        } else {
            addSettingButton(0, buttonWidth, startX, top, rowGap, twoColumns, "Performans Modu: " + onOff(config.performanceMode()), config::togglePerformanceMode);
            addSettingButton(1, buttonWidth, startX, top, rowGap, twoColumns, "1. Şahıs Efekt Azaltma: " + onOff(config.reducedFirstPersonEffects()), config::toggleReducedFirstPersonEffects);
            addSettingButton(2, buttonWidth, startX, top, rowGap, twoColumns, "Foto-Hassasiyet Modu: " + onOff(config.photosensitiveMode()), config::togglePhotosensitiveMode);
            addSettingButton(3, buttonWidth, startX, top, rowGap, twoColumns, "Varsayılanlara Dön", config::resetDefaults);
        }

        int doneY = height - 42;
        addRenderableWidget(Button.builder(Component.literal("BİTTİ"), button -> onClose())
            .bounds(width / 2 - 72, doneY, 144, 22).build());
    }

    private void addSettingButton(int index, int buttonWidth, int startX, int top, int rowGap, boolean twoColumns, String label, Runnable action) {
        int column = twoColumns ? index % 2 : 0;
        int row = twoColumns ? index / 2 : index;
        int x = startX + column * (buttonWidth + 8);
        int y = top + row * rowGap;
        if (y > height - 72) return;
        addRenderableWidget(Button.builder(Component.literal(label), button -> {
            action.run();
            if (minecraft != null) minecraft.setScreen(new SkinPowersSettingsScreen(parent, page));
        }).bounds(x, y, buttonWidth, 22).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, width, height, 0xFF06040D, 0xFF261044);
        int panelLeft = Math.max(8, width / 2 - Math.min(330, width / 2 - 12));
        int panelRight = width - panelLeft;
        graphics.fill(panelLeft, 12, panelRight, height - 12, 0xD0070610);
        graphics.outline(panelLeft, 12, panelRight - panelLeft, height - 24, 0xFFB65CFF);
        graphics.text(font, title, (width - font.width(title)) / 2, 18, 0xFFFFFFFF, true);
        String hint = page == 0 ? "HUD ve bildirim yerleşimi" : page == 1 ? "Kartlar, efektler ve animasyon kalitesi" : "Performans ve rahatsız edici efektleri azaltma";
        graphics.text(font, hint, (width - font.width(hint)) / 2, 31, 0xFFD7C8F2, false);
        String signature = "Skin Powers 1.0.7 • Made by Yankalan";
        graphics.text(font, signature, (width - font.width(signature)) / 2, height - 17, 0xFFB98DFF, true);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }
    private static String onOff(boolean value) { return value ? "AÇIK" : "KAPALI"; }
}
