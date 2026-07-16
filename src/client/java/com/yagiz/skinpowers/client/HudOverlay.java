package com.yagiz.skinpowers.client;

import com.yagiz.skinpowers.PowerClass;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HudOverlay {
    private HudOverlay() {}

    public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || ClientState.powerClass() == PowerClass.NONE) return;

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        PowerClass powerClass = ClientState.powerClass();

        if (ClientState.visionEnabled()) {
            if (powerClass == PowerClass.WARDEN) {
                graphics.fill(0, 0, screenWidth, screenHeight, 0x66101827);
                for (int radius = 28; radius <= 112; radius += 28) {
                    int alpha = Math.max(12, 54 - radius / 3);
                    graphics.outline(screenWidth / 2 - radius, screenHeight / 2 - radius / 2, radius * 2, radius, (alpha << 24) | 0x35D7D0);
                }
            } else if (powerClass == PowerClass.FIRE) {
                graphics.fill(0, 0, screenWidth, screenHeight, 0x44D23A00);
            }
        }

        int x = 8;
        int y = 8;
        int panelWidth = 210;
        int accent = switch (powerClass) {
            case WARDEN -> 0xFF35D7D0;
            case FLIGHT -> 0xFFFFFFFF;
            case FIRE -> 0xFFFFA826;
            default -> 0xFFBFC9D2;
        };

        graphics.fill(x, y, x + panelWidth, y + 57, 0xB005080D);
        graphics.outline(x, y, panelWidth, 57, accent);
        graphics.text(client.font, powerClass.displayName() + "  |  Seviye " + ClientState.selectedPower(), x + 8, y + 7, 0xFFFFFFFF, true);
        graphics.text(client.font, ClientState.powerName(), x + 8, y + 20, 0xFFDCE7ED, false);

        int cooldown = ClientState.cooldownTicks();
        String ready = cooldown <= 0 ? "R: HAZIR" : String.format(java.util.Locale.ROOT, "R: %.1f sn", cooldown / 20.0);
        graphics.text(client.font, ready, x + 8, y + 33, cooldown <= 0 ? 0xFF8CFFB0 : 0xFFFFD27A, true);

        String passive = switch (powerClass) {
            case FLIGHT -> "Yavaş Düşüş: " + (ClientState.passiveEnabled() ? "AÇIK" : "KAPALI");
            case WARDEN -> ClientState.unlockedLevel() >= 4 ? "Karanlık Görüş: " + (ClientState.visionEnabled() ? "AÇIK" : "KAPALI") : "Y: Seviye 4'te açılır";
            case FIRE -> ClientState.unlockedLevel() >= 4 ? "Ateş Görüşü: " + (ClientState.visionEnabled() ? "AÇIK" : "KAPALI") : "Ateş bağışıklığı: AÇIK";
            default -> "";
        };
        graphics.text(client.font, passive, x + 8, y + 45, 0xFFB8C8D3, false);

        int barX = x + 135;
        int barY = y + 35;
        int barWidth = 66;
        graphics.fill(barX, barY, barX + barWidth, barY + 7, 0xFF202831);
        float fraction = cooldown <= 0 ? 1.0F : Math.max(0.0F, Math.min(1.0F, 1.0F - cooldown / 2400.0F));
        graphics.fill(barX, barY, barX + Math.max(1, (int) (barWidth * fraction)), barY + 7, accent);
    }
}
