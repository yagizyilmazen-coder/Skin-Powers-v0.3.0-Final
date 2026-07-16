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
        ClientConfig config = ClientConfig.get();

        drawShakeOverlay(graphics, screenWidth, screenHeight, config);
        drawAncientStatus(graphics, client, screenWidth);

        if (powerClass == PowerClass.WARDEN && ClientState.wardenHuntTicks() > 0) {
            graphics.fill(0, 0, screenWidth, 4, 0xAA35D7D0);
            graphics.fill(0, screenHeight - 4, screenWidth, screenHeight, 0xAA35D7D0);
            graphics.fill(0, 4, 4, screenHeight - 4, 0xAA35D7D0);
            graphics.fill(screenWidth - 4, 4, screenWidth, screenHeight - 4, 0xAA35D7D0);
        }

        float scale = config.hudScalePercent() / 100.0F;
        int panelWidth = Math.max(146, Math.round(228 * scale));
        int panelHeight = Math.max(47, Math.round(64 * scale));
        int x = config.hudRight() ? screenWidth - panelWidth - 6 : 6;
        int y = 6;
        int accent = switch (powerClass) {
            case WARDEN -> 0xFF35D7D0;
            case FLIGHT -> 0xFFEAF8FF;
            case FIRE -> 0xFFFFA826;
            case NATURE -> 0xFF67D96E;
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
            case NATURE -> ClientState.natureTreeTicks() > 0
                ? String.format(java.util.Locale.ROOT, "Yaşam Ağacı: %.1f sn", ClientState.natureTreeTicks() / 20.0)
                : "Doğal Yenilenme: AÇIK";
            default -> "";
        };
        int statusY = Math.min(y + panelHeight - 11, y + 40);
        graphics.text(client.font, fit(client, status, panelWidth - 18), x + 9, statusY, 0xFFB8C8D3, false);
    }


    private static void drawAncientStatus(GuiGraphicsExtractor graphics, Minecraft client, int screenWidth) {
        if (ClientState.ancientChargeAvailable() && ClientState.ancientChargeTicks() > 0) {
            int width = Math.min(268, Math.max(190, screenWidth - 24));
            int left = (screenWidth - width) / 2;
            int top = 7;
            graphics.fill(left, top, left + width, top + 34, 0xDD12071D);
            graphics.fill(left, top, left + 5, top + 34, 0xFFB24DFF);
            graphics.outline(left, top, width, 34, 0xFF65E7E0);
            String title = "ANTİK ŞEHİR ŞARJI • 1 GÜÇ HAKKI";
            graphics.text(client.font, fit(client, title, width - 18), left + 10, top + 7, 0xFFFFFFFF, true);
            String timer = String.format(java.util.Locale.ROOT, "%.1f sn • bekleme süreleri kapalı", ClientState.ancientChargeTicks() / 20.0);
            graphics.text(client.font, fit(client, timer, width - 18), left + 10, top + 20, 0xFFCFA8FF, false);
        } else if (ClientState.ancientExhaustionTicks() > 0) {
            int width = Math.min(222, Math.max(170, screenWidth - 24));
            int left = (screenWidth - width) / 2;
            graphics.fill(left, 7, left + width, 7 + 23, 0xD0180C16);
            graphics.outline(left, 7, width, 23, 0xFF7C506F);
            String text = String.format(java.util.Locale.ROOT, "ANTİK ÇÖKÜŞ • %.1f sn", ClientState.ancientExhaustionTicks() / 20.0);
            graphics.text(client.font, text, left + (width - client.font.width(text)) / 2, 15, 0xFFE2B7DA, true);
        }
    }

    private static void drawShakeOverlay(GuiGraphicsExtractor graphics, int width, int height, ClientConfig config) {
        if (ClientState.shakeTicks() <= 0 || config.screenShakePercent() <= 0) return;
        float scaledStrength = ClientState.shakeStrength() * config.screenShakePercent() / 100.0F;
        int alpha = Math.max(18, Math.min(86, Math.round(22.0F + scaledStrength * 32.0F)));
        int edge = Math.max(2, Math.min(7, Math.round(2.0F + scaledStrength * 1.8F)));
        int jitter = (ClientState.shakeTicks() % 4) - 2;
        int color = (alpha << 24) | 0x00150B08;
        graphics.fill(0, Math.max(0, jitter), width, Math.max(edge, edge + jitter), color);
        graphics.fill(0, height - edge + Math.min(0, jitter), width, height, color);
        graphics.fill(Math.max(0, jitter), 0, Math.max(edge, edge + jitter), height, color);
        graphics.fill(width - edge + Math.min(0, jitter), 0, width, height, color);
    }

    private static String fit(Minecraft client, String text, int maxWidth) {
        if (client.font.width(text) <= maxWidth) return text;
        String suffix = "...";
        int length = text.length();
        while (length > 0 && client.font.width(text.substring(0, length) + suffix) > maxWidth) length--;
        return text.substring(0, Math.max(0, length)) + suffix;
    }
}
