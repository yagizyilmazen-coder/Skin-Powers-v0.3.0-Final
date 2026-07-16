package com.yagiz.skinpowers.client;

import com.yagiz.skinpowers.PowerCatalog;
import com.yagiz.skinpowers.PowerClass;
import com.yagiz.skinpowers.network.ClientCommandPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class PowerMenuScreen extends Screen {
    private final long openedAt = Util.getMillis();

    public PowerMenuScreen() {
        super(Component.literal("Skin Powers"));
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(430, width - 28);
        int left = (width - panelWidth) / 2;
        int rowTop = 58;
        int rowHeight = Math.max(35, Math.min(47, (height - 105) / 5));
        int next = ClientState.unlockedLevel() + 1;

        if (next <= 5) {
            int cost = PowerCatalog.xpCostForLevel(next);
            addRenderableWidget(Button.builder(Component.literal("Seviye " + next + " Aç — " + cost + " XP"), button -> {
                ClientPlayNetworking.send(new ClientCommandPayload("UNLOCK"));
            }).bounds(left + panelWidth - 145, rowTop + (next - 1) * rowHeight + 7, 133, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Kapat"), button -> onClose())
            .bounds((width - 86) / 2, height - 30, 86, 20)
            .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        PowerClass powerClass = ClientState.powerClass();
        int[] colors = theme(powerClass);
        graphics.fillGradient(0, 0, width, height, colors[0], colors[1]);

        int panelWidth = Math.min(430, width - 28);
        int left = (width - panelWidth) / 2;
        int rowTop = 58;
        int rowHeight = Math.max(35, Math.min(47, (height - 105) / 5));
        float appear = Math.max(0.0F, Math.min(1.0F, (Util.getMillis() - openedAt) / 420.0F));

        graphics.fill(left - 8, 33, left + panelWidth + 8, height - 38, 0xB0080C12);
        graphics.outline(left - 8, 33, panelWidth + 16, height - 71, colors[2]);

        String heading = powerClass.displayName() + " — Güç Gelişimi";
        graphics.text(font, heading, (width - font.width(heading)) / 2, 15, 0xFFFFFFFF, true);
        String xp = "Mevcut XP seviyesi: " + ClientState.xpLevel();
        graphics.text(font, xp, (width - font.width(xp)) / 2, 31, 0xFFD7E5EE, false);

        for (int level = 1; level <= 5; level++) {
            int y = rowTop + (level - 1) * rowHeight + (int) ((1.0F - appear) * 18.0F);
            boolean unlocked = level <= ClientState.unlockedLevel();
            boolean selected = level == ClientState.selectedPower();
            int rowColor = selected ? 0xD0385364 : (unlocked ? 0xB0212B34 : 0x9020262C);
            graphics.fill(left, y, left + panelWidth, y + rowHeight - 5, rowColor);
            graphics.outline(left, y, panelWidth, rowHeight - 5, selected ? colors[2] : 0x557E8D98);

            String line = "Seviye " + level + " — " + PowerCatalog.powerName(powerClass, level);
            graphics.text(font, line, left + 10, y + 7, unlocked ? 0xFFFFFFFF : 0xFF9CA6AD, true);
            int uses = ClientState.masteryUses(level);
            int stage = ClientState.masteryStage(level);
            String status = unlocked ? "AÇIK  |  Ustalık " + stage + "/3  |  " + uses + " kullanım" : "KİLİTLİ  |  " + PowerCatalog.xpCostForLevel(level) + " XP";
            graphics.text(font, status, left + 10, y + 20, unlocked ? 0xFFBFE6D8 : 0xFF8A939A, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int[] theme(PowerClass powerClass) {
        return switch (powerClass) {
            case WARDEN -> new int[]{0xFF03070D, 0xFF102E3E, 0xFF32D4CC};
            case FLIGHT -> new int[]{0xFF4A9ED0, 0xFFEAF8FF, 0xFFFFFFFF};
            case FIRE -> new int[]{0xFF250504, 0xFF9C2309, 0xFFFFB52B};
            default -> new int[]{0xFF080A0E, 0xFF1D2630, 0xFF93A5B2};
        };
    }
}
