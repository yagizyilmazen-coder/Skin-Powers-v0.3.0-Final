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
        if (client.player == null) return;

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        ClientConfig config = ClientConfig.get();
        drawSandScreenOverlay(graphics, screenWidth, screenHeight);
        if (ClientState.powerClass() == PowerClass.NONE) return;
        PowerClass powerClass = ClientState.powerClass();

        drawShakeOverlay(graphics, screenWidth, screenHeight, config);
        drawCastOverlay(graphics, screenWidth, screenHeight, config);

        if (powerClass == PowerClass.WARDEN && ClientState.wardenHuntTicks() > 0) {
            graphics.fill(0, 0, screenWidth, 4, 0xAA35D7D0);
            graphics.fill(0, screenHeight - 4, screenWidth, screenHeight, 0xAA35D7D0);
            graphics.fill(0, 4, 4, screenHeight - 4, 0xAA35D7D0);
            graphics.fill(screenWidth - 4, 4, screenWidth, screenHeight - 4, 0xAA35D7D0);
        }

        float scale = config.hudScalePercent() / 100.0F;
        int panelWidth = Math.max(146, Math.round(228 * scale));
        int panelHeight = config.compactHud() ? Math.max(42, Math.round(54 * scale)) : Math.max(47, Math.round(64 * scale));
        int x = config.hudRight() ? screenWidth - panelWidth - 6 : 6;
        int y = 6 + config.hudVerticalOffset();
        int accent = switch (powerClass) {
            case WARDEN -> 0xFF35D7D0;
            case FLIGHT -> 0xFFB65CFF;
            case FIRE -> 0xFFFFA826;
            case MOON -> 0xFFDCE6FF;
            case ANOMALY -> 0xFFB65CFF;
            case MAGNETIC -> 0xFFB8C5D1;
            case SAND -> 0xFFE0B85A;
            default -> 0xFFBFC9D2;
        };

        int powerAccent = PowerIconArt.shade(accent, ClientState.selectedPower());
        drawPowerPanel(graphics, client, powerClass, x, y, panelWidth, panelHeight, powerAccent);
        int awakeningHeight = drawAwakeningStatus(graphics, client, powerClass, x, y + panelHeight + 4, panelWidth, accent, config);
        int comboHeight = drawComboStatus(graphics, client, x, y + panelHeight + awakeningHeight + 4, panelWidth, accent);
        drawAncientStatus(graphics, client, screenWidth, screenHeight, x, y, panelWidth, panelHeight + awakeningHeight + comboHeight + 4);
        drawAnomalyChoice(graphics, client, screenWidth, screenHeight);
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

        int iconSize = Math.min(14, panelHeight - 8);
        int iconX = x + 7;
        int iconY = y + 4;
        PowerIconArt.draw(graphics, powerClass, ClientState.selectedPower(), iconX, iconY, iconSize, accent);
        int textStartX = iconX + iconSize + 5;

        int stage = ClientState.masteryStage(ClientState.selectedPower());
        String mastery = PowerCatalog.masteryStageName(stage);
        String title = powerClass.displayName() + "  S" + ClientState.selectedPower();
        int titleWidth = panelWidth - (textStartX - x) - 11 - client.font.width(mastery);
        graphics.text(client.font, fit(client, title, titleWidth), textStartX, y + 5, 0xFFFFFFFF, true);
        graphics.text(client.font, mastery, x + panelWidth - client.font.width(mastery) - 7, y + 5, accent, true);
        String powerLine = ClientState.powerName() + "  •  " + PowerIconArt.tag(powerClass, ClientState.selectedPower());
        graphics.text(client.font, fit(client, powerLine, panelWidth - 18), x + 9, y + 16, 0xFFDCE7ED, false);

        int cooldown = ClientState.cooldownTicks();
        String ready = cooldown <= 0 ? "R: HAZIR" : String.format(java.util.Locale.ROOT, "R: %.1f sn", cooldown / 20.0);
        graphics.text(client.font, ready, x + 9, y + 29, cooldown <= 0 ? 0xFF8CFFB0 : 0xFFFFD27A, true);

        String status = switch (powerClass) {
            case FLIGHT -> ClientState.dragonFormTicks() > 0
                ? String.format(java.util.Locale.ROOT, "Ejderha Hükümdarı: %.1f sn", ClientState.dragonFormTicks() / 20.0)
                : (ClientState.dragonScalesTicks() > 0
                    ? "Kadim Pullar: " + ClientState.dragonScaleCharges() + " pul"
                    : "Ejderha Kanı: AÇIK");
            case WARDEN -> ClientState.wardenHuntTicks() > 0
                ? String.format(java.util.Locale.ROOT, "Derinlik Pususu: %.1f sn", ClientState.wardenHuntTicks() / 20.0)
                : "Derinlik Pususu: R ile kullan";
            case FIRE -> ClientState.unlockedLevel() >= 4 ? "Seviye 4: Cehennem Küresi" : "Ateş bağışıklığı: AÇIK";
            case MOON -> ClientState.selectedPower() == 4
                ? "Ay Aynası: R ile aç/fırlat"
                : (ClientState.selectedPower() == 5 ? "Tutulma Hükmü hazır" : "Ay ışığı pasifi: gece açık");
            case ANOMALY -> {
                if (ClientState.anomalyChoiceTicks() > 0) {
                    yield String.format(java.util.Locale.ROOT, "Depolanan: %.1f", ClientState.anomalyStoredDamage());
                }
                if (ClientState.anomalyStoreTicks() > 0) {
                    yield String.format(java.util.Locale.ROOT, "Hasar depolanıyor: %.1f sn", ClientState.anomalyStoreTicks() / 20.0);
                }
                if (ClientState.anomalyBonusHealthTicks() > 0 && ClientState.anomalyBonusHealth() > 0.0D) {
                    yield String.format(java.util.Locale.ROOT, "+%.1f kalp • %.0f sn", ClientState.anomalyBonusHealth() / 2.0D, ClientState.anomalyBonusHealthTicks() / 20.0D);
                }
                yield ClientState.copiedPowerName().isBlank() ? "?: Hamle bekleniyor" : "?: " + ClientState.copiedPowerName();
            }
            case MAGNETIC -> ClientState.selectedPower() == 4 ? "Metal Fırtınası: R ile hazırla/fırlat" : "Metal alanı dengede";
            case SAND -> ClientState.sandScreenTicks() > 0 ? "Görüşte kum: suya girerek temizle" : "Çöl akışı hazır";
            default -> "";
        };
        int statusY = Math.min(y + panelHeight - 11, y + 40);
        graphics.text(client.font, fit(client, status, panelWidth - 18), x + 9, statusY, 0xFFB8C8D3, false);
    }

    private static int drawAwakeningStatus(GuiGraphicsExtractor graphics, Minecraft client, PowerClass powerClass, int x, int y, int width, int accent, ClientConfig config) {
        if (!config.showAwakeningBar()) return 0;
        int height = ClientState.classAwakeningTicks() > 0 ? 27 : 18;
        graphics.fill(x, y, x + width, y + height, 0xC5080710);
        graphics.outline(x, y, width, height, accent);
        int barLeft = x + 7;
        int barRight = x + width - 7;
        int barTop = y + 7;
        int barBottom = barTop + 5;
        graphics.fill(barLeft, barTop, barRight, barBottom, 0xFF191824);
        float ratio;
        String label;
        if (ClientState.classAwakeningTicks() > 0) {
            ratio = Math.max(0.0F, Math.min(1.0F, ClientState.classAwakeningTicks() / 480.0F));
            label = String.format(java.util.Locale.ROOT, "UYANIŞ AKTİF • %.1f sn", ClientState.classAwakeningTicks() / 20.0);
        } else {
            ratio = ClientState.awakeningEnergy() / 100.0F;
            label = ClientState.awakeningEnergy() >= 20.0F
                ? String.format(java.util.Locale.ROOT, "G: UYANIŞ • %%%.0f", ClientState.awakeningEnergy())
                : String.format(java.util.Locale.ROOT, "UYANIŞ • %%%.0f", ClientState.awakeningEnergy());
        }
        int fill = Math.round((barRight - barLeft) * ratio);
        if (fill > 0) graphics.fill(barLeft, barTop, barLeft + fill, barBottom, accent);
        int labelColor = powerClass == PowerClass.MOON ? 0xFF4FA8FF : 0xFFF2E9FF;
        graphics.text(client.font, fit(client, label, width - 14), x + 7, y + (height > 20 ? 16 : 5), labelColor, false);
        return height + 4;
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
        boolean exhausted = ClientState.ancientExhaustionTicks() > 0;
        if (!charged && !exhausted) return;

        if (charged) {
            float nScale = ClientConfig.get().notificationScalePercent() / 100.0F;
            int width = Math.min(screenWidth - 20, Math.max(150, Math.round(218 * nScale)));
            int height = Math.max(24, Math.round(28 * nScale));
            int left = (screenWidth - width) / 2;
            int top = Math.max(8, screenHeight - 92);
            graphics.fill(left, top, left + width, top + height, 0xC8110719);
            graphics.fill(left, top, left + 3, top + height, 0xFFB24DFF);
            graphics.outline(left, top, width, height, 0xFF65E7E0);
            String first = fit(client, "Antik Şehir Seni Şarj etti.", width - 12);
            String second = fit(client, "Vücudun bunu kaldırabilecek Mi?", width - 12);
            graphics.text(client.font, first, left + (width - client.font.width(first)) / 2, top + 5, 0xFFE9D8FF, false);
            graphics.text(client.font, second, left + (width - client.font.width(second)) / 2, top + 15, 0xFFC9EFFF, false);
            return;
        }

        int width = 126;
        int height = 16;
        int left = screenWidth - width - 7;
        int top = Math.max(7, screenHeight - 63);
        graphics.fill(left, top, left + width, top + height, 0xB5190B16);
        graphics.outline(left, top, width, height, 0xFF9E557D);
        graphics.text(client.font, "Antik Şehir çöküşü", left + 7, top + 4, 0xFFE0AFCF, false);
    }

    private static void drawAnomalyChoice(
        GuiGraphicsExtractor graphics,
        Minecraft client,
        int screenWidth,
        int screenHeight
    ) {
        if (ClientState.powerClass() != PowerClass.ANOMALY || ClientState.anomalyChoiceTicks() <= 0) return;
        String line = String.format(
            java.util.Locale.ROOT,
            "Depolanan %.1f   [V] Kalp   [X] Geri gönder",
            ClientState.anomalyStoredDamage()
        );
        float nScale = ClientConfig.get().notificationScalePercent() / 100.0F;
        int width = Math.min(screenWidth - 16, Math.max(150, Math.round((client.font.width(line) + 18) * nScale)));
        int height = Math.max(16, Math.round(18 * nScale));
        int left = (screenWidth - width) / 2;
        int top = Math.max(8, screenHeight - 58);
        graphics.fill(left, top, left + width, top + height, 0xC3080710);
        graphics.fill(left, top, left + 3, top + height, 0xFFB65CFF);
        graphics.outline(left, top, width, height, 0xFF5CE5E5);
        String fitted = fit(client, line, width - 12);
        graphics.text(client.font, fitted, left + (width - client.font.width(fitted)) / 2, top + 5, 0xFFF0E8FF, false);
    }

    private static void drawSandScreenOverlay(GuiGraphicsExtractor graphics, int width, int height) {
        int ticks = ClientState.sandScreenTicks();
        if (ticks <= 0) return;
        float fade = ticks < 20 ? ticks / 20.0F : 1.0F;
        int baseAlpha = Math.max(18, Math.round(72.0F * fade));
        graphics.fill(0, 0, width, height, (baseAlpha << 24) | 0x00C99745);
        int grains = Math.max(24, Math.min(90, (width * height) / 4200));
        int seed = ticks * 1103515245 + width * 31 + height;
        for (int i = 0; i < grains; i++) {
            seed = seed * 1664525 + 1013904223;
            int x = Math.floorMod(seed, Math.max(1, width));
            seed = seed * 1664525 + 1013904223;
            int y = Math.floorMod(seed, Math.max(1, height));
            int size = 2 + Math.floorMod(seed >>> 8, 7);
            int alpha = Math.max(28, Math.round((70 + Math.floorMod(seed >>> 16, 70)) * fade));
            int rgb = (i % 3 == 0) ? 0x00E6C36C : (i % 3 == 1 ? 0x00C49343 : 0x00F2D886);
            graphics.fill(x, y, Math.min(width, x + size + 3), Math.min(height, y + size), (alpha << 24) | rgb);
        }
        int edgeAlpha = Math.max(30, Math.round(145.0F * fade));
        int edge = Math.max(10, Math.min(30, width / 18));
        int edgeColor = (edgeAlpha << 24) | 0x00B17B32;
        graphics.fill(0, 0, width, edge, edgeColor);
        graphics.fill(0, height - edge, width, height, edgeColor);
        graphics.fill(0, edge, edge, height - edge, edgeColor);
        graphics.fill(width - edge, edge, width, height - edge, edgeColor);
    }

    private static void drawCastOverlay(GuiGraphicsExtractor graphics, int width, int height, ClientConfig config) {
        if (ClientState.castPulseTicks() <= 0 || config.glowPercent() <= 0 || config.performanceMode()) return;
        PowerClass powerClass = ClientState.castPulseClass();
        int rgb = switch (powerClass) {
            case WARDEN -> 0x0035D7D0;
            case FLIGHT -> 0x00B65CFF;
            case FIRE -> 0x00FF8A18;
            case MOON -> 0x00DCE6FF;
            case ANOMALY -> 0x005CE5E5;
            case MAGNETIC -> 0x00B8C5D1;
            case SAND -> 0x00E0B85A;
            default -> 0x00FFFFFF;
        };
        float fade = Math.min(1.0F, ClientState.castPulseTicks() / 8.0F);
        int maxAlpha = config.photosensitiveMode() ? 28 : 72;
        int alpha = Math.max(8, Math.min(maxAlpha, Math.round(ClientState.castPulseStrength() * fade * maxAlpha * config.glowPercent() / 100.0F)));
        int edge = config.reducedFirstPersonEffects() ? 2 : 4;
        int color = (alpha << 24) | rgb;
        graphics.fill(0, 0, width, edge, color);
        graphics.fill(0, height - edge, width, height, color);
        graphics.fill(0, edge, edge, height - edge, color);
        graphics.fill(width - edge, edge, width, height - edge, color);
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
