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

import java.util.ArrayList;
import java.util.List;

public final class PowerMenuScreen extends Screen {
    private final long openedAt = Util.getMillis();

    public PowerMenuScreen() {
        super(Component.literal("Skin Powers"));
    }

    @Override
    protected void init() {
        Layout layout = layout();
        int unlocked = ClientState.unlockedLevel();
        int selected = ClientState.selectedPower();
        int next = unlocked + 1;

        for (int level = 1; level <= 5; level++) {
            int y = layout.rowTop() + (level - 1) * (layout.rowHeight() + layout.gap());
            int buttonX = layout.listLeft() + layout.listWidth() - 104;
            if (level <= unlocked) {
                final int selectedLevel = level;
                String label = level == selected ? "SEÇİLİ" : "SEÇ";
                addRenderableWidget(Button.builder(Component.literal(label), button -> {
                    ClientPlayNetworking.send(new ClientCommandPayload("SELECT:" + selectedLevel));
                    onClose();
                }).bounds(buttonX, y + (layout.rowHeight() - 22) / 2, 90, 22).build());
            } else if (level == next) {
                int cost = PowerCatalog.xpCostForLevel(level);
                addRenderableWidget(Button.builder(Component.literal("AÇ  " + cost + " XP"), button -> {
                    ClientPlayNetworking.send(new ClientCommandPayload("UNLOCK"));
                    onClose();
                }).bounds(buttonX, y + (layout.rowHeight() - 22) / 2, 90, 22).build());
            }
        }

    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        PowerClass powerClass = ClientState.powerClass();
        int[] colors = theme(powerClass);
        Layout layout = layout();
        long now = Util.getMillis();
        float appear = smoothStep(clamp01((now - openedAt) / 420.0F));

        graphics.fillGradient(0, 0, width, height, colors[0], colors[1]);
        drawBackgroundDetails(graphics, powerClass, colors[2], now);
        drawHeader(graphics, layout, powerClass, colors);

        int contentBottom = layout.rowTop() + 5 * layout.rowHeight() + 4 * layout.gap();
        graphics.fill(layout.listLeft() - 8, layout.rowTop() - 8, layout.listLeft() + layout.listWidth() + 8, contentBottom + 8, 0xC9070B11);
        graphics.outline(layout.listLeft() - 8, layout.rowTop() - 8, layout.listWidth() + 16, contentBottom - layout.rowTop() + 16, withAlpha(colors[2], 165));

        for (int level = 1; level <= 5; level++) {
            int baseY = layout.rowTop() + (level - 1) * (layout.rowHeight() + layout.gap());
            int y = baseY + (int) ((1.0F - appear) * (20 + level * 4));
            drawPowerRow(graphics, layout, powerClass, colors, level, y, now);
        }

        if (layout.wide()) {
            drawDetailPanel(graphics, layout, powerClass, colors, contentBottom, now);
        } else {
            drawCompactFooter(graphics, layout, powerClass, colors, contentBottom);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, Layout layout, PowerClass powerClass, int[] colors) {
        int top = 10;
        int bottom = 66;
        graphics.fill(layout.totalLeft(), top, layout.totalLeft() + layout.totalWidth(), bottom, 0xD2070B11);
        graphics.fill(layout.totalLeft(), top, layout.totalLeft() + 7, bottom, colors[2]);
        graphics.outline(layout.totalLeft(), top, layout.totalWidth(), bottom - top, withAlpha(colors[2], 190));

        drawClassEmblem(graphics, powerClass, layout.totalLeft() + 20, 21, colors[2]);
        String xp = "XP  " + ClientState.xpLevel();
        int xpWidth = Math.max(70, font.width(xp) + 18);
        int xpX = layout.totalLeft() + layout.totalWidth() - xpWidth - 14;
        int headerTextX = layout.totalLeft() + 62;
        int headerTextWidth = Math.max(90, xpX - headerTextX - 12);
        String heading = fit(powerClass.displayName() + " GÜÇ AĞACI", headerTextWidth);
        graphics.text(font, heading, headerTextX, 18, 0xFFFFFFFF, true);
        graphics.text(font, fit("Gücünü seç, ustalığını geliştir ve R ile kullan.", headerTextWidth), headerTextX, 35, 0xFFBFD0DA, false);
        graphics.text(font, fit("Sol/Sağ: güç değiştir   •   O / ESC: menü", headerTextWidth), headerTextX, 49, 0xFF8799A5, false);

        graphics.fill(xpX, 20, xpX + xpWidth, 45, withAlpha(colors[2], 65));
        graphics.outline(xpX, 20, xpWidth, 25, colors[2]);
        graphics.text(font, xp, xpX + (xpWidth - font.width(xp)) / 2, 29, 0xFFFFFFFF, true);
    }

    private void drawPowerRow(
        GuiGraphicsExtractor graphics,
        Layout layout,
        PowerClass powerClass,
        int[] colors,
        int level,
        int y,
        long now
    ) {
        boolean unlocked = level <= ClientState.unlockedLevel();
        boolean selected = level == ClientState.selectedPower();
        int uses = ClientState.masteryUses(level);
        int stage = ClientState.masteryStage(level);

        int rowColor = selected ? 0xE5283946 : (unlocked ? 0xD3131D25 : 0xC40C1117);
        graphics.fill(layout.listLeft(), y, layout.listLeft() + layout.listWidth(), y + layout.rowHeight(), rowColor);
        graphics.fill(layout.listLeft(), y, layout.listLeft() + 6, y + layout.rowHeight(), selected ? colors[2] : (unlocked ? withAlpha(colors[2], 125) : 0xFF35404A));
        graphics.outline(layout.listLeft(), y, layout.listWidth(), layout.rowHeight(), selected ? colors[2] : 0x66798791);

        int badgeX = layout.listLeft() + 14;
        int badgeY = y + (layout.rowHeight() - 34) / 2;
        graphics.fill(badgeX, badgeY, badgeX + 34, badgeY + 34, unlocked ? withAlpha(colors[2], selected ? 200 : 95) : 0xFF222A31);
        graphics.outline(badgeX, badgeY, 34, 34, unlocked ? colors[2] : 0xFF59636C);
        String roman = roman(level);
        graphics.text(font, roman, badgeX + (34 - font.width(roman)) / 2, badgeY + 12, powerClass == PowerClass.FLIGHT && unlocked ? 0xFF173448 : 0xFFFFFFFF, true);

        int textX = layout.listLeft() + 60;
        int textRight = layout.listLeft() + layout.listWidth() - 118;
        String stageName = PowerCatalog.masteryStageName(stage);
        int chipWidth = font.width(stageName) + 12;
        int chipX = Math.max(textX + 92, textRight - chipWidth);
        String name = fit(PowerCatalog.powerName(powerClass, level), Math.max(50, chipX - textX - 8));
        graphics.text(font, name, textX, y + 8, unlocked ? 0xFFFFFFFF : 0xFF8B969E, true);

        graphics.fill(chipX, y + 6, chipX + chipWidth, y + 19, unlocked ? withAlpha(colors[2], 50 + stage * 28) : 0x332B3238);
        graphics.outline(chipX, y + 6, chipWidth, 13, unlocked ? withAlpha(colors[2], 180) : 0x5559636B);
        graphics.text(font, stageName, chipX + 6, y + 9, unlocked ? 0xFFFFFFFF : 0xFF77828A, false);

        String description = fit(PowerCatalog.powerDescription(powerClass, level), Math.max(100, textRight - textX));
        graphics.text(font, description, textX, y + 24, unlocked ? 0xFFC7D4DC : 0xFF707A82, false);

        int barY = y + layout.rowHeight() - 10;
        int barWidth = Math.max(90, textRight - textX);
        graphics.fill(textX, barY, textX + barWidth, barY + 4, 0xFF202933);
        if (unlocked) {
            int filled = Math.max(2, (int) (barWidth * PowerCatalog.masteryProgress(uses)));
            graphics.fill(textX, barY, textX + Math.min(barWidth, filled), barY + 4, colors[2]);
            String usage = stage >= 3 ? uses + " kullanım • TAM" : uses + "/" + PowerCatalog.nextMasteryTarget(uses) + " kullanım";
            graphics.text(font, usage, textX, barY - 10, 0xFF91A8B5, false);
        } else {
            String locked = level == ClientState.unlockedLevel() + 1
                ? PowerCatalog.xpCostForLevel(level) + " XP ile açılır"
                : "Önceki seviye gerekli";
            graphics.text(font, locked, textX, barY - 10, 0xFFB7786D, false);
        }

        if (selected) {
            float pulse = (float) ((Math.sin(now / 210.0) + 1.0) * 0.5);
            graphics.outline(layout.listLeft() - 2, y - 2, layout.listWidth() + 4, layout.rowHeight() + 4, withAlpha(colors[2], 80 + (int) (90 * pulse)));
        }
    }

    private void drawDetailPanel(
        GuiGraphicsExtractor graphics,
        Layout layout,
        PowerClass powerClass,
        int[] colors,
        int contentBottom,
        long now
    ) {
        int left = layout.detailLeft();
        int top = layout.rowTop() - 8;
        int width = layout.detailWidth();
        int height = contentBottom - layout.rowTop() + 16;
        graphics.fill(left, top, left + width, top + height, 0xDA070B11);
        graphics.outline(left, top, width, height, colors[2]);
        graphics.fill(left, top, left + width, top + 5, colors[2]);

        int selected = ClientState.selectedPower();
        int uses = ClientState.masteryUses(selected);
        int stage = ClientState.masteryStage(selected);
        graphics.text(font, "SEÇİLİ GÜÇ", left + 15, top + 17, colors[2], true);
        graphics.text(font, roman(selected), left + 15, top + 38, 0xFFFFFFFF, true);
        graphics.text(font, PowerCatalog.powerName(powerClass, selected), left + 42, top + 38, 0xFFFFFFFF, true);

        int lineY = top + 62;
        for (String line : wrap(PowerCatalog.powerDescription(powerClass, selected), width - 30, 4)) {
            graphics.text(font, line, left + 15, lineY, 0xFFC4D1D9, false);
            lineY += 12;
        }

        int dividerY = Math.max(top + 120, lineY + 5);
        graphics.fill(left + 15, dividerY, left + width - 15, dividerY + 1, withAlpha(colors[2], 110));

        String control = controlHint(powerClass, selected);
        graphics.text(font, "KONTROL", left + 15, dividerY + 13, 0xFF7F929E, false);
        graphics.text(font, control, left + 15, dividerY + 27, 0xFFFFFFFF, true);

        String cooldown = ClientState.cooldownTicks() <= 0
            ? "HAZIR"
            : String.format(java.util.Locale.ROOT, "%.1f saniye", ClientState.cooldownTicks() / 20.0);
        graphics.text(font, "BEKLEME", left + 15, dividerY + 50, 0xFF7F929E, false);
        graphics.text(font, cooldown, left + 15, dividerY + 64, ClientState.cooldownTicks() <= 0 ? 0xFF88F2A7 : 0xFFFFD27A, true);

        String status = activeStatus(powerClass, selected);
        graphics.text(font, "DURUM", left + 15, dividerY + 87, 0xFF7F929E, false);
        graphics.text(font, status, left + 15, dividerY + 101, 0xFFFFFFFF, true);

        int masteryY = top + height - 68;
        graphics.fill(left + 15, masteryY - 8, left + width - 15, masteryY - 7, withAlpha(colors[2], 100));
        graphics.text(font, "USTALIK", left + 15, masteryY + 3, 0xFF7F929E, false);
        String mastery = PowerCatalog.masteryStageName(stage) + "  •  " + uses + " kullanım";
        graphics.text(font, mastery, left + 15, masteryY + 17, 0xFFFFFFFF, true);
        int barWidth = width - 30;
        graphics.fill(left + 15, masteryY + 36, left + 15 + barWidth, masteryY + 42, 0xFF202933);
        graphics.fill(left + 15, masteryY + 36, left + 15 + Math.max(2, (int) (barWidth * PowerCatalog.masteryProgress(uses))), masteryY + 42, colors[2]);

        float pulse = (float) ((Math.sin(now / 300.0) + 1.0) * 0.5);
        graphics.outline(left + 7, top + 7, width - 14, height - 14, withAlpha(colors[2], 35 + (int) (45 * pulse)));
    }

    private void drawCompactFooter(GuiGraphicsExtractor graphics, Layout layout, PowerClass powerClass, int[] colors, int contentBottom) {
        int y = contentBottom + 12;
        int selected = ClientState.selectedPower();
        graphics.fill(layout.totalLeft(), y, layout.totalLeft() + layout.totalWidth(), y + 28, 0xC9070B11);
        graphics.outline(layout.totalLeft(), y, layout.totalWidth(), 28, withAlpha(colors[2], 150));
        String text = PowerCatalog.powerName(powerClass, selected) + "  •  " + controlHint(powerClass, selected) + "  •  " + activeStatus(powerClass, selected);
        graphics.text(font, fit(text, layout.totalWidth() - 20), layout.totalLeft() + 10, y + 10, 0xFFFFFFFF, false);
    }

    private void drawClassEmblem(GuiGraphicsExtractor graphics, PowerClass powerClass, int x, int y, int accent) {
        if (powerClass == PowerClass.WARDEN) {
            graphics.fill(x + 7, y, x + 29, y + 28, withAlpha(accent, 80));
            graphics.outline(x + 7, y, 22, 28, accent);
            graphics.fill(x, y + 8, x + 8, y + 20, accent);
            graphics.fill(x + 28, y + 8, x + 36, y + 20, accent);
            graphics.fill(x + 12, y + 7, x + 16, y + 11, 0xFFFFFFFF);
            graphics.fill(x + 20, y + 7, x + 24, y + 11, 0xFFFFFFFF);
        } else if (powerClass == PowerClass.FLIGHT) {
            graphics.fill(x + 14, y + 4, x + 22, y + 29, accent);
            graphics.fill(x, y + 5, x + 15, y + 13, withAlpha(accent, 170));
            graphics.fill(x + 21, y + 5, x + 36, y + 13, withAlpha(accent, 170));
            graphics.fill(x + 4, y + 13, x + 15, y + 21, withAlpha(accent, 110));
            graphics.fill(x + 21, y + 13, x + 32, y + 21, withAlpha(accent, 110));
        } else if (powerClass == PowerClass.FIRE) {
            graphics.fill(x + 11, y + 12, x + 27, y + 29, accent);
            graphics.fill(x + 15, y + 5, x + 24, y + 18, withAlpha(accent, 210));
            graphics.fill(x + 18, y, x + 22, y + 10, 0xFFFFFF8A);
        }
    }

    private String controlHint(PowerClass powerClass, int level) {
        if (powerClass == PowerClass.FLIGHT && level == 1) return "R veya Y: aç/kapat";
        if (powerClass == PowerClass.FLIGHT && level == 3) return "R veya çift boşluk";
        if (powerClass == PowerClass.FLIGHT && level == 5) return "Otomatik çarpışma";
        if (powerClass == PowerClass.FIRE && (level == 1 || level == 2)) return "Otomatik";
        return "R: kullan";
    }

    private String activeStatus(PowerClass powerClass, int level) {
        if (powerClass == PowerClass.FLIGHT && level == 1) {
            return ClientState.passiveEnabled() ? "Yavaş Düşüş açık" : "Yavaş Düşüş kapalı";
        }
        if (powerClass == PowerClass.FLIGHT && level == 2 && ClientState.temporaryElytraTicks() > 0) {
            return String.format(java.util.Locale.ROOT, "Elytra %.1f sn", ClientState.temporaryElytraTicks() / 20.0);
        }
        if (powerClass == PowerClass.WARDEN && level == 4 && ClientState.wardenHuntTicks() > 0) {
            return String.format(java.util.Locale.ROOT, "Sculk Avı %.1f sn", ClientState.wardenHuntTicks() / 20.0);
        }
        return ClientState.cooldownTicks() <= 0 ? "Kullanıma hazır" : "Bekleme süresinde";
    }

    private void drawBackgroundDetails(GuiGraphicsExtractor g, PowerClass powerClass, int accent, long now) {
        int drift = (int) ((now / 35L) % Math.max(1, width));
        if (powerClass == PowerClass.FIRE) {
            for (int i = 0; i < 14; i++) {
                int x = (i * 73 + drift) % Math.max(1, width);
                int y = height - 45 - (i % 5) * 18;
                g.fill(x, y, x + 4, y + 11, withAlpha(accent, 55 + (i % 3) * 20));
            }
        } else if (powerClass == PowerClass.FLIGHT) {
            for (int i = -1; i < 7; i++) {
                int x = ((i * 145 + drift) % (width + 180)) - 90;
                int y = 80 + (i % 4) * 78;
                g.fill(x, y, x + 82, y + 12, 0x30FFFFFF);
                g.fill(x + 20, y - 8, x + 58, y + 14, 0x24FFFFFF);
            }
        } else if (powerClass == PowerClass.WARDEN) {
            for (int i = 0; i < 18; i++) {
                int x = (i * 119 + drift / 4) % Math.max(1, width);
                int y = 70 + (i * 43) % Math.max(90, height - 130);
                g.fill(x, y, x + 3, y + 3, withAlpha(accent, 65 + (i % 4) * 22));
            }
        }
    }

    private Layout layout() {
        int totalWidth = Math.min(930, Math.max(430, width - 28));
        boolean wide = totalWidth >= 760 && height >= 520;
        int detailWidth = wide ? 252 : 0;
        int gapToDetail = wide ? 14 : 0;
        int listWidth = totalWidth - detailWidth - gapToDetail;
        int totalLeft = (width - totalWidth) / 2;
        int listLeft = totalLeft;
        int detailLeft = listLeft + listWidth + gapToDetail;
        int rowTop = 78;
        int gap = 7;
        int footerSpace = wide ? 26 : 52;
        int available = Math.max(250, height - rowTop - footerSpace - gap * 4);
        int rowHeight = Math.max(48, Math.min(66, available / 5));
        return new Layout(totalLeft, totalWidth, listLeft, listWidth, detailLeft, detailWidth, rowTop, rowHeight, gap, wide);
    }

    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String suffix = "...";
        int length = text.length();
        while (length > 0 && font.width(text.substring(0, length) + suffix) > maxWidth) length--;
        return text.substring(0, Math.max(0, length)) + suffix;
    }

    private List<String> wrap(String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (font.width(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
            } else {
                if (!current.isEmpty()) lines.add(current.toString());
                current.setLength(0);
                current.append(word);
                if (lines.size() >= maxLines - 1) break;
            }
        }
        if (!current.isEmpty() && lines.size() < maxLines) lines.add(current.toString());
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "V";
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int[] theme(PowerClass powerClass) {
        return switch (powerClass) {
            case WARDEN -> new int[]{0xFF010409, 0xFF0A2430, 0xFF35D7D0};
            case FLIGHT -> new int[]{0xFF2F719D, 0xFFBFE8FF, 0xFFF1FBFF};
            case FIRE -> new int[]{0xFF170201, 0xFF7C1608, 0xFFFFA51F};
            default -> new int[]{0xFF080A0E, 0xFF1D2630, 0xFF93A5B2};
        };
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float smoothStep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private record Layout(
        int totalLeft,
        int totalWidth,
        int listLeft,
        int listWidth,
        int detailLeft,
        int detailWidth,
        int rowTop,
        int rowHeight,
        int gap,
        boolean wide
    ) {}
}
