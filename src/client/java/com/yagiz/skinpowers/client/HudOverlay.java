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
        boolean compact = config.compactHud();
        int panelWidth = Math.max(148, Math.round(204 * scale));
        // Slot satırı için biraz daha yüksek
        int panelHeight = compact ? Math.max(52, Math.round(58 * scale)) : Math.max(68, Math.round(76 * scale));
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
        drawPowerPanel(graphics, client, powerClass, x, y, panelWidth, panelHeight, powerAccent, accent, compact);
        int awakeningHeight = drawAwakeningStatus(graphics, client, powerClass, x, y + panelHeight + 3, panelWidth, accent, config);
        int comboHeight = drawComboStatus(graphics, client, x, y + panelHeight + awakeningHeight + 3, panelWidth, accent);
        drawAncientStatus(graphics, client, screenWidth, screenHeight, x, y, panelWidth, panelHeight + awakeningHeight + comboHeight + 3);
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
        int accent,
        int classAccent,
        boolean compact
    ) {
        drawPanel(graphics, x, y, panelWidth, panelHeight, classAccent);

        // Üst ince gradient şerit (sınıf rengi)
        graphics.fill(x + 3, y + 1, x + panelWidth - 1, y + 2, withAlpha(classAccent, 90));

        int iconSize = compact ? 14 : 18;
        int iconBox = iconSize + 6;
        int iconX = x + 8;
        int iconY = y + 6;
        graphics.fill(iconX, iconY, iconX + iconBox, iconY + iconBox, 0xEE06080E);
        graphics.outline(iconX, iconY, iconBox, iconBox, withAlpha(accent, 210));
        graphics.fill(iconX + 1, iconY + 1, iconX + iconBox - 1, iconY + 2, withAlpha(accent, 70));
        PowerIconArt.draw(graphics, powerClass, ClientState.selectedPower(),
            iconX + 3, iconY + 3, iconSize, accent);

        int textX = iconX + iconBox + 6;
        int selected = ClientState.selectedPower();
        int stage = ClientState.masteryStage(selected);
        String mastery = PowerCatalog.masteryStageName(stage);

        // Sağ üst: ustalık rozeti
        int masteryW = client.font.width(mastery) + 8;
        int mx = x + panelWidth - masteryW - 5;
        graphics.fill(mx, y + 5, mx + masteryW, y + 16, 0xDD06080E);
        graphics.outline(mx, y + 5, masteryW, 11, withAlpha(classAccent, 190));
        graphics.text(client.font, mastery, mx + 4, y + 7, classAccent, false);

        // Satır 1: sınıf · seviye
        String title = powerClass.displayName() + "  S" + selected;
        int titleMax = Math.max(24, mx - textX - 4);
        graphics.text(client.font, fit(client, title, titleMax), textX, y + 6, 0xFFF4F0E8, true);

        // Satır 2: güç adı
        String powerLine = ClientState.powerName();
        graphics.text(client.font, fit(client, powerLine, panelWidth - (textX - x) - 8), textX, y + 17, 0xFFD0C8B8, false);

        // Etiket chip
        String tag = PowerIconArt.tag(powerClass, selected);
        int tagW = client.font.width(tag) + 6;
        int tagY = y + 28;
        graphics.fill(textX, tagY, textX + tagW, tagY + 10, withAlpha(accent, 55));
        graphics.outline(textX, tagY, tagW, 10, withAlpha(accent, 150));
        graphics.text(client.font, tag, textX + 3, tagY + 1, 0xFFE8E0D0, false);

        // R / cooldown
        int cooldown = ClientState.cooldownTicks();
        boolean ready = cooldown <= 0;
        String readyText = ready ? "R HAZIR" : String.format(java.util.Locale.ROOT, "R %.1fs", cooldown / 20.0);
        int readyColor = ready ? 0xFF7DFFB0 : 0xFFFFC86A;
        int readyW = client.font.width(readyText) + 8;
        int readyX = textX + tagW + 4;
        graphics.fill(readyX, tagY, readyX + readyW, tagY + 10, 0xDD06080E);
        graphics.outline(readyX, tagY, readyW, 10, withAlpha(readyColor, 180));
        graphics.text(client.font, readyText, readyX + 4, tagY + 1, readyColor, false);

        // Durum metni (chip'lerin sağı veya altı)
        String status = statusLine(powerClass);
        int statusX = readyX + readyW + 5;
        int statusMax = panelWidth - (statusX - x) - 6;
        if (statusMax >= 36) {
            graphics.text(client.font, fit(client, status, statusMax), statusX, tagY + 1, 0xFF9A9288, false);
        }

        // Güç slotları: 1..max — açık / seçili / kilitli
        if (!compact) {
            int max = PowerCatalog.maxLevel(powerClass);
            int unlocked = ClientState.unlockedLevel();
            int slotSize = 7;
            int gap = 3;
            int totalSlotsW = max * slotSize + (max - 1) * gap;
            int slotX = x + 8;
            int slotY = y + panelHeight - slotSize - 6;
            // Sol: "GÜÇ" etiketi
            graphics.text(client.font, "GÜÇ", slotX, slotY - 1, 0xFF6A655C, false);
            slotX += client.font.width("GÜÇ") + 5;
            for (int i = 1; i <= max; i++) {
                boolean isUnlocked = i <= unlocked;
                boolean isSelected = i == selected;
                int sx = slotX + (i - 1) * (slotSize + gap);
                int fill;
                if (isSelected) fill = accent;
                else if (isUnlocked) fill = withAlpha(classAccent, 120);
                else fill = 0xFF2A2E34;
                graphics.fill(sx, slotY, sx + slotSize, slotY + slotSize, fill);
                if (isSelected) {
                    graphics.outline(sx - 1, slotY - 1, slotSize + 2, slotSize + 2, withAlpha(0xFFFFFFFF, 180));
                } else {
                    graphics.outline(sx, slotY, slotSize, slotSize, isUnlocked ? withAlpha(classAccent, 160) : 0xFF1A1E24);
                }
            }

            // Cooldown ince bar (slotların sağında kalan alan)
            int barLeft = slotX + totalSlotsW + 6;
            int barRight = x + panelWidth - 8;
            if (barRight - barLeft > 20) {
                graphics.fill(barLeft, slotY + 1, barRight, slotY + slotSize - 1, 0xFF0A0C10);
                if (!ready) {
                    // Yaklaşık kalan oran: 0..1 ters (süre bilmiyoruz; dolu=beklemede hissi)
                    float pulse = Math.min(1.0F, cooldown / 200.0F);
                    int fillW = Math.max(2, Math.round((barRight - barLeft) * pulse));
                    graphics.fill(barLeft, slotY + 1, barLeft + fillW, slotY + slotSize - 1, withAlpha(readyColor, 160));
                } else {
                    graphics.fill(barLeft, slotY + 1, barRight, slotY + slotSize - 1, withAlpha(0xFF7DFFB0, 90));
                }
            }
        }
    }

    private static String statusLine(PowerClass powerClass) {
        return switch (powerClass) {
            case FLIGHT -> ClientState.dragonFormTicks() > 0
                ? String.format(java.util.Locale.ROOT, "Ejderha: %.1fs", ClientState.dragonFormTicks() / 20.0)
                : (ClientState.dragonScalesTicks() > 0
                    ? "Pullar: " + ClientState.dragonScaleCharges()
                    : "Ejderha Kanı");
            case WARDEN -> ClientState.wardenHuntTicks() > 0
                ? String.format(java.util.Locale.ROOT, "Pusu: %.1fs", ClientState.wardenHuntTicks() / 20.0)
                : "Pusu: R ile kullan";
            case FIRE -> ClientState.unlockedLevel() >= 4 ? "Cehennem Küresi" : "Ateş bağışıklığı";
            case MOON -> ClientState.selectedPower() == 4 ? "Ay Aynası" : (ClientState.selectedPower() == 5 ? "Tutulma" : "Ay ışığı");
            case ANOMALY -> {
                if (ClientState.anomalyChoiceTicks() > 0)
                    yield String.format(java.util.Locale.ROOT, "Depo: %.1f", ClientState.anomalyStoredDamage());
                if (ClientState.anomalyStoreTicks() > 0)
                    yield String.format(java.util.Locale.ROOT, "Depolanıyor: %.1fs", ClientState.anomalyStoreTicks() / 20.0);
                if (ClientState.anomalyBonusHealthTicks() > 0 && ClientState.anomalyBonusHealth() > 0.0D)
                    yield String.format(java.util.Locale.ROOT, "+%.1f kalp", ClientState.anomalyBonusHealth() / 2.0D);
                yield ClientState.copiedPowerName().isBlank() ? "?: Bekleniyor" : "?: " + ClientState.copiedPowerName();
            }
            case MAGNETIC -> ClientState.selectedPower() == 4 ? "Metal Fırtınası" : "Metal alanı";
            case SAND -> ClientState.sandScreenTicks() > 0 ? "Kum görüşü" : "Çöl akışı";
            default -> "";
        };
    }

    private static int drawAwakeningStatus(GuiGraphicsExtractor graphics, Minecraft client, PowerClass powerClass, int x, int y, int width, int accent, ClientConfig config) {
        if (!config.showAwakeningBar()) return 0;
        boolean active = ClientState.classAwakeningTicks() > 0;
        int height = 18;
        drawPanel(graphics, x, y, width, height, accent);

        float ratio;
        String label;
        if (active) {
            ratio = Math.max(0.0F, Math.min(1.0F, ClientState.classAwakeningTicks() / 480.0F));
            label = String.format(java.util.Locale.ROOT, "UYANIŞ AKTİF · %.1fs", ClientState.classAwakeningTicks() / 20.0);
        } else {
            ratio = ClientState.awakeningEnergy() / 100.0F;
            label = ClientState.awakeningEnergy() >= 20.0F
                ? String.format(java.util.Locale.ROOT, "G UYANIŞ · %%%.0f", ClientState.awakeningEnergy())
                : String.format(java.util.Locale.ROOT, "UYANIŞ · %%%.0f", ClientState.awakeningEnergy());
        }
        graphics.text(client.font, fit(client, label, width - 16), x + 8, y + 3, 0xFFF0E8D8, false);
        int barLeft = x + 8;
        int barRight = x + width - 8;
        int barTop = y + 12;
        graphics.fill(barLeft, barTop, barRight, barTop + 3, 0xFF0A0A0A);
        int fill = Math.round((barRight - barLeft) * ratio);
        if (fill > 0) graphics.fill(barLeft, barTop, barLeft + fill, barTop + 3, accent);
        return height + 4;
    }

    private static int drawComboStatus(GuiGraphicsExtractor graphics, Minecraft client, int x, int y, int width, int accent) {
        if (!ClientState.comboModeEnabled()) return 0;
        boolean active = ClientState.comboTicks() > 0;
        int height = active ? 38 : 20;
        int gold = active ? 0xFFFFD35C : 0xFFB8964A;
        drawPanel(graphics, x, y, width, height, gold);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 3, withAlpha(gold, 140));
        graphics.text(client.font, "KOMBO", x + 10, y + 6, gold, true);
        if (active) {
            String name = ClientState.comboName().isBlank() ? "KOMBO HAZIR" : ClientState.comboName();
            String next = String.format(java.util.Locale.ROOT, "%s  ·  %.1fs", ClientState.comboNextPowerName(), ClientState.comboTicks() / 20.0);
            graphics.text(client.font, fit(client, name, width - 18), x + 10, y + 18, accent, true);
            graphics.text(client.font, fit(client, next, width - 18), x + 10, y + 28, 0xFFE8E0D0, false);
        }
        return height + 4;
    }

    /** Sade koyu panel + sol accent şerit. Cobble tile loop yok → her kare ucuz. */
    private static void drawPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent) {
        g.fill(x, y, x + w, y + h, 0xE00C0E14);
        g.fill(x, y, x + 3, y + h, withAlpha(accent, 220));
        g.outline(x, y, w, h, withAlpha(accent, 160));
        g.outline(x + 1, y + 1, w - 2, h - 2, 0x44000000);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
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
