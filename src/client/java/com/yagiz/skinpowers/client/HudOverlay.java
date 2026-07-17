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

        drawPowerPanel(graphics, client, powerClass, x, y, panelWidth, panelHeight, accent);
        int comboHeight = drawComboStatus(graphics, client, x, y + panelHeight + 4, panelWidth, accent);
        drawAncientStatus(graphics, client, screenWidth, screenHeight, x, y, panelWidth, panelHeight + comboHeight + 4);
    }

    private static void drawPowerPanel(
        GuiGraphicsExtractor graphics,
        Minecraft client,
        PowerClass powerClass,
        int x,
        int y,
        int panelWidth,
        int panelHeight,
        int accent
    ) {
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
            case FIRE -> ClientState.unlockedLevel() >= 4 ? "Seviye 4: Cehennem Küresi" : "Ateş bağışıklığı: AÇIK";
            case NATURE -> ClientState.natureTreeTicks() > 0
                ? String.format(java.util.Locale.ROOT, "Yaşam Ağacı: %.1f sn", ClientState.natureTreeTicks() / 20.0)
                : "Doğal Yenilenme: AÇIK";
            default -> "";
        };
        int statusY = Math.min(y + panelHeight - 11, y + 40);
        graphics.text(client.font, fit(client, status, panelWidth - 18), x + 9, statusY, 0xFFB8C8D3, false);
    }

    /** @return HUD'un kapladığı ek yükseklik. */
    private static int drawComboStatus(GuiGraphicsExtractor graphics, Minecraft client, int x, int y, int width, int accent) {
        if (!ClientState.comboModeEnabled()) return 0;
        boolean active = ClientState.comboTicks() > 0;
        int height = active ? 38 : 20;
        graphics.fill(x, y, x + width, y + height, 0xD0080D12);
        graphics.fill(x, y, x + 4, y + height, 0xFFFFD35C);
        graphics.outline(x, y, width, height, active ? 0xFFFFD35C : 0xFF987F45);
        graphics.text(client.font, "KOMBO: AÇIK", x + 9, y + 6, 0xFFFFE49A, true);
        if (active) {
            String name = ClientState.comboName().isBlank() ? "KOMBO HAZIR" : ClientState.comboName();
            String next = String.format(java.util.Locale.ROOT, "%s • %.1f sn", ClientState.comboNextPowerName(), ClientState.comboTicks() / 20.0);
            graphics.text(client.font, fit(client, name, width - 18), x + 9, y + 18, accent, true);
            graphics.text(client.font, fit(client, next, width - 18), x + 9, y + 28, 0xFFFFFFFF, false);
        }
        return height + 4;
    }

    private static void drawAncientStatus(
        GuiGraphicsExtractor graphics,
        Minecraft client,
        int screenWidth,
        int screenHeight,
        int hudX,
        int hudY,
        int hudWidth,
        int hudTotalHeight
    ) {
        boolean charged = ClientState.ancientChargeTicks() > 0;
        boolean exhausted = !charged && ClientState.ancientExhaustionTicks() > 0;
        if (!charged && !exhausted) return;

        int width = charged
            ? Math.min(292, Math.max(202, screenWidth - 24))
            : Math.min(222, Math.max(170, screenWidth - 24));
        int height = charged ? 34 : 23;
        int left = (screenWidth - width) / 2;
        int top = 7;

        boolean horizontalOverlap = left < hudX + hudWidth + 6 && left + width > hudX - 6;
        if (horizontalOverlap) top = hudY + hudTotalHeight + 6;
        if (top + height > screenHeight - 8) top = Math.max(7, screenHeight - height - 8);

        if (charged) {
            graphics.fill(left, top, left + width, top + 34, 0xDD12071D);
            graphics.fill(left, top, left + 5, top + 34, 0xFFB24DFF);
            graphics.outline(left, top, width, 34, 0xFF65E7E0);
            String title = ClientState.ancientChargeAvailable()
                ? "ANTİK ŞEHİR ŞARJI • 1 GÜÇ HAKKI"
                : "ANTİK ŞEHİR ŞARJI • GÜÇ KULLANILDI";
            graphics.text(client.font, fit(client, title, width - 18), left + 10, top + 7, 0xFFFFFFFF, true);
            String timer = ClientState.ancientChargeAvailable()
                ? String.format(java.util.Locale.ROOT, "%.1f sn • bekleme süreleri kapalı", ClientState.ancientChargeTicks() / 20.0)
                : String.format(java.util.Locale.ROOT, "Çöküşe %.1f sn", ClientState.ancientChargeTicks() / 20.0);
            graphics.text(client.font, fit(client, timer, width - 18), left + 10, top + 20, 0xFFCFA8FF, false);
        } else {
            graphics.fill(left, top, left + width, top + 23, 0xD0180C16);
            graphics.outline(left, top, width, 23, 0xFF7C506F);
            String text = String.format(java.util.Locale.ROOT, "ANTİK ÇÖKÜŞ • %.1f sn", ClientState.ancientExhaustionTicks() / 20.0);
            graphics.text(client.font, fit(client, text, width - 12), left + (width - client.font.width(fit(client, text, width - 12))) / 2, top + 8, 0xFFE2B7DA, true);
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
        if (text == null) return "";
        if (client.font.width(text) <= maxWidth) return text;
        String suffix = "...";
        int length = text.length();
        while (length > 0 && client.font.width(text.substring(0, length) + suffix) > maxWidth) length--;
        return text.substring(0, Math.max(0, length)) + suffix;
    }
}
