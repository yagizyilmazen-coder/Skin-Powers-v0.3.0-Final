package com.yagiz.skinpowers.client;

import com.yagiz.skinpowers.PowerCatalog;
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

        if (powerClass == PowerClass.WARDEN && ClientState.wardenHuntTicks() > 0) {
            graphics.fill(0, 0, screenWidth, 4, 0xAA35D7D0);
            graphics.fill(0, screenHeight - 4, screenWidth, screenHeight, 0xAA35D7D0);
            graphics.fill(0, 4, 4, screenHeight - 4, 0xAA35D7D0);
            graphics.fill(screenWidth - 4, 4, screenWidth, screenHeight - 4, 0xAA35D7D0);
            for (int radius = 28; radius <= 84; radius += 28) {
                int alpha = Math.max(15, 52 - radius / 3);
                graphics.outline(screenWidth / 2 - radius, screenHeight / 2 - radius / 2, radius * 2, radius, (alpha << 24) | 0x35D7D0);
            }
        }

        // Önceki 228x64 panelin yaklaşık %80 boyutu.
        int x = 6;
        int y = 6;
        int panelWidth = 182;
        int panelHeight = 51;
        int accent = switch (powerClass) {
            case WARDEN -> 0xFF35D7D0;
            case FLIGHT -> 0xFFEAF8FF;
            case FIRE -> 0xFFFFA826;
            case WATER -> 0xFF63FFF1;
            default -> 0xFFBFC9D2;
        };

        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xC005080D);
        graphics.fill(x, y, x + 4, y + panelHeight, accent);
        graphics.outline(x, y, panelWidth, panelHeight, accent);

        int stage = ClientState.masteryStage(ClientState.selectedPower());
        String mastery = PowerCatalog.masteryStageName(stage);
        String title = powerClass.displayName() + "  S" + ClientState.selectedPower();
        int titleWidth = panelWidth - 20 - client.font.width(mastery);
        graphics.text(client.font, fit(client, title, titleWidth), x + 9, y + 5, 0xFFFFFFFF, true);
        graphics.text(client.font, mastery, x + panelWidth - client.font.width(mastery) - 7, y + 5, accent, true);
        graphics.text(client.font, fit(client, ClientState.powerName(), panelWidth - 18), x + 9, y + 16, 0xFFDCE7ED, false);

        int cooldown = ClientState.cooldownTicks();
        String ready = cooldown <= 0 ? "R: HAZIR" : String.format(java.util.Locale.ROOT, "R: %.1f sn", cooldown / 20.0);
        graphics.text(client.font, ready, x + 9, y + 29, cooldown <= 0 ? 0xFF8CFFB0 : 0xFFFFD27A, true);

        String status = switch (powerClass) {
            case FLIGHT -> ClientState.temporaryElytraTicks() > 0
                ? String.format(java.util.Locale.ROOT, "Elytra: %.1f sn", ClientState.temporaryElytraTicks() / 20.0)
                : "Yavaş Düşüş: " + (ClientState.passiveEnabled() ? "AÇIK" : "KAPALI");
            case WARDEN -> ClientState.wardenHuntTicks() > 0
                ? String.format(java.util.Locale.ROOT, "Sculk Avı: %.1f sn", ClientState.wardenHuntTicks() / 20.0)
                : "Sculk Avı: R ile kullan";
            case FIRE -> ClientState.unlockedLevel() >= 4 ? "Seviye 4: Ateş Küresi" : "Ateş bağışıklığı: AÇIK";
            case WATER -> ClientState.waterArmorTicks() > 0
                ? String.format(java.util.Locale.ROOT, "Su Zırhı: %.1f sn", ClientState.waterArmorTicks() / 20.0)
                : "Suda Yaşam: AÇIK";
            default -> "";
        };
        graphics.text(client.font, fit(client, status, panelWidth - 18), x + 9, y + 40, 0xFFB8C8D3, false);
    }

    private static String fit(Minecraft client, String text, int maxWidth) {
        if (client.font.width(text) <= maxWidth) return text;
        String suffix = "...";
        int length = text.length();
        while (length > 0 && client.font.width(text.substring(0, length) + suffix) > maxWidth) length--;
        return text.substring(0, Math.max(0, length)) + suffix;
    }
}
