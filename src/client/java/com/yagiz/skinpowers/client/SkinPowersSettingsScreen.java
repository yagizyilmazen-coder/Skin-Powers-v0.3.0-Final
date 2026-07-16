package com.yagiz.skinpowers.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SkinPowersSettingsScreen extends Screen {
    private final Screen parent;

    public SkinPowersSettingsScreen(Screen parent) {
        super(Component.literal("Skin Powers Ayarları"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuildButtons();
    }

    private void rebuildButtons() {
        ClientConfig config = ClientConfig.get();
        boolean twoColumns = width >= 320;
        int gap = 8;
        int buttonWidth = twoColumns
            ? Math.max(132, Math.min(230, (width - 34 - gap) / 2))
            : Math.max(150, Math.min(250, width - 28));
        int columnsWidth = twoColumns ? buttonWidth * 2 + gap : buttonWidth;
        int startX = (width - columnsWidth) / 2;
        int top = height < 300 ? 48 : 58;
        int rowGap = height < 300 ? 27 : 32;
        boolean shortLabels = buttonWidth < 185;

        addSettingButton(0, buttonWidth, startX, top, rowGap, twoColumns,
            shortLabels ? "HUD: %" + config.hudScalePercent() : "HUD Boyutu: %" + config.hudScalePercent(),
            () -> config.cycleHudScale());
        addSettingButton(1, buttonWidth, startX, top, rowGap, twoColumns,
            shortLabels ? "Konum: " + (config.hudRight() ? "SAĞ" : "SOL") : "HUD Konumu: " + (config.hudRight() ? "SAĞ" : "SOL"),
            config::toggleHudRight);
        addSettingButton(2, buttonWidth, startX, top, rowGap, twoColumns,
            shortLabels ? "Menü Anim.: " + onOff(config.menuAnimations()) : "Menü Animasyonları: " + onOff(config.menuAnimations()),
            config::toggleMenuAnimations);
        addSettingButton(3, buttonWidth, startX, top, rowGap, twoColumns,
            shortLabels ? "Tarama: " + onOff(config.scanAnimation()) : "Skin Tarama Çizgisi: " + onOff(config.scanAnimation()),
            config::toggleScanAnimation);
        addSettingButton(4, buttonWidth, startX, top, rowGap, twoColumns,
            shortLabels ? "Sarsıntı: %" + config.screenShakePercent() : "Ekran Sarsıntısı: %" + config.screenShakePercent(),
            config::cycleScreenShake);
        addSettingButton(5, buttonWidth, startX, top, rowGap, twoColumns,
            shortLabels ? "Performans: " + onOff(config.performanceMode()) : "Performans Modu: " + onOff(config.performanceMode()),
            config::togglePerformanceMode);
        addSettingButton(6, buttonWidth, startX, top, rowGap, twoColumns,
            shortLabels ? "Kart Hızı: %" + config.cardAnimationSpeedPercent() : "Kart Animasyon Hızı: %" + config.cardAnimationSpeedPercent(),
            config::cycleCardSpeed);
        addSettingButton(7, buttonWidth, startX, top, rowGap, twoColumns,
            shortLabels ? "Varsayılan" : "Varsayılanlara Dön",
            config::resetDefaults);

        int usedRows = twoColumns ? 4 : 8;
        int doneY = Math.min(height - 50, top + usedRows * rowGap + 10);
        addRenderableWidget(Button.builder(Component.literal("BİTTİ"), button -> onClose())
            .bounds(width / 2 - 72, doneY, 144, 22).build());
    }

    private void addSettingButton(
        int index,
        int buttonWidth,
        int startX,
        int top,
        int rowGap,
        boolean twoColumns,
        String label,
        Runnable action
    ) {
        int column = twoColumns ? index % 2 : 0;
        int row = twoColumns ? index / 2 : index;
        int x = startX + column * (buttonWidth + 8);
        int y = top + row * rowGap;
        addRenderableWidget(Button.builder(Component.literal(label), button -> {
            action.run();
            if (minecraft != null) minecraft.setScreen(new SkinPowersSettingsScreen(parent));
        }).bounds(x, y, buttonWidth, 22).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, width, height, 0xFF071009, 0xFF183820);
        int panelLeft = Math.max(8, width / 2 - Math.min(300, width / 2 - 12));
        int panelRight = width - panelLeft;
        graphics.fill(panelLeft, 14, panelRight, height - 14, 0xCE07100A);
        graphics.outline(panelLeft, 14, panelRight - panelLeft, height - 28, 0xFF65D56E);
        graphics.text(font, title, (width - font.width(title)) / 2, 22, 0xFFFFFFFF, true);
        String hint = width < 400 ? "Ayarlar bu bilgisayarda saklanır." : "Ayarlar yalnızca bu bilgisayarda saklanır.";
        graphics.text(font, hint, (width - font.width(hint)) / 2, 35, 0xFFB6C9B7, false);
        String signature = "Made by Yankalan";
        graphics.text(font, signature, (width - font.width(signature)) / 2, height - 25, 0xFF83D58A, true);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String onOff(boolean value) {
        return value ? "AÇIK" : "KAPALI";
    }
}
