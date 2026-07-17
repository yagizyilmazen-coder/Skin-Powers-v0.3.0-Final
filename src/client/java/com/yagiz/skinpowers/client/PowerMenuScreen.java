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
    private final Button[] powerButtons = new Button[6];

    public PowerMenuScreen() {
        super(Component.literal("Skin Powers"));
    }

    @Override
    protected void init() {
        Layout layout = layout();
        int unlocked = ClientState.unlockedLevel();
        int selected = ClientState.selectedPower();
        int next = unlocked + 1;
        int maximum = PowerCatalog.maxLevel(ClientState.powerClass());

        for (int level = 1; level <= maximum; level++) {
            int y = layout.rowTop() + (level - 1) * (layout.rowHeight() + layout.gap());
            int buttonWidth = layout.listWidth() < 330 ? 72 : 90;
            int buttonHeight = Math.max(16, Math.min(22, layout.rowHeight() - 4));
            int buttonX = layout.listLeft() + layout.listWidth() - buttonWidth - 14;
            if (level <= unlocked) {
                final int selectedLevel = level;
                String label = level == selected ? "SEÇİLİ" : "SEÇ";
                powerButtons[level - 1] = Button.builder(Component.literal(label), button -> {
                    ClientPlayNetworking.send(new ClientCommandPayload("SELECT:" + selectedLevel));
                    onClose();
                }).bounds(buttonX, y + (layout.rowHeight() - buttonHeight) / 2 + 36, buttonWidth, buttonHeight).build();
                powerButtons[level - 1].active = false;
                addRenderableWidget(powerButtons[level - 1]);
            } else if (level == next) {
                int cost = PowerCatalog.xpCostForLevel(ClientState.powerClass(), level);
                boolean creative = minecraft != null && minecraft.player != null && minecraft.player.isCreative();
                String unlockLabel = creative ? "AÇ  (YARATICI)" : "AÇ  " + cost + " XP";
                powerButtons[level - 1] = Button.builder(Component.literal(unlockLabel), button -> {
                    ClientPlayNetworking.send(new ClientCommandPayload("UNLOCK"));
                    onClose();
                }).bounds(buttonX, y + (layout.rowHeight() - buttonHeight) / 2 + 36, buttonWidth, buttonHeight).build();
                powerButtons[level - 1].active = false;
                addRenderableWidget(powerButtons[level - 1]);
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

        int maximum = PowerCatalog.maxLevel(powerClass);
        int contentBottom = layout.rowTop() + maximum * layout.rowHeight() + (maximum - 1) * layout.gap();
        graphics.fill(layout.listLeft() - 8, layout.rowTop() - 8, layout.listLeft() + layout.listWidth() + 8, contentBottom + 8, 0xC9070B11);
        graphics.outline(layout.listLeft() - 8, layout.rowTop() - 8, layout.listWidth() + 16, contentBottom - layout.rowTop() + 16, withAlpha(colors[2], 165));

        for (int level = 1; level <= maximum; level++) {
            float rowProgress = ClientUiRules.staggeredProgress(appear, level - 1, maximum, 0.30F);
            int baseY = layout.rowTop() + (level - 1) * (layout.rowHeight() + layout.gap());
            int y = baseY + (int) ((1.0F - rowProgress) * (32 + level * 5));
            drawPowerRow(graphics, layout, powerClass, colors, level, y, now);
            Button button = powerButtons[level - 1];
            if (button != null) {
                int buttonHeight = Math.max(16, Math.min(22, layout.rowHeight() - 4));
                button.setY(y + (layout.rowHeight() - buttonHeight) / 2);
                // Altıncı Warden satırı da animasyon bittiğinde kesin olarak aktif olur.
                button.active = rowProgress >= 0.85F;
            }
        }

        if (layout.wide()) {
            drawDetailPanel(graphics, layout, powerClass, colors, contentBottom, now);
        } else if (contentBottom + 54 <= height) {
            // Çok kısa pencerelerde alt bilgi satırı altıncı Warden kartıyla çakışmasın.
            drawCompactFooter(graphics, layout, powerClass, colors, contentBottom);
        }
        String signature = "Made by Yankalan";
        graphics.text(font, signature, width - font.width(signature) - 7, height - 11, 0xFF85D68A, true);

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
        graphics.text(font, fit(powerClass == PowerClass.ANOMALY ? "Sol/Sağ: güç değiştir   •   V/X: hasar seçimi   •   O / ESC: menü" : "Sol/Sağ: güç değiştir   •   K: kombo   •   O / ESC: menü", headerTextWidth), headerTextX, 49, 0xFF8799A5, false);

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
        boolean veryCompact = layout.rowHeight() < 30;

        int rowColor = selected ? 0xE5283946 : (unlocked ? 0xD3131D25 : 0xC40C1117);
        graphics.fill(layout.listLeft(), y, layout.listLeft() + layout.listWidth(), y + layout.rowHeight(), rowColor);
        graphics.fill(layout.listLeft(), y, layout.listLeft() + 5, y + layout.rowHeight(), selected ? colors[2] : (unlocked ? withAlpha(colors[2], 125) : 0xFF35404A));
        graphics.outline(layout.listLeft(), y, layout.listWidth(), layout.rowHeight(), selected ? colors[2] : 0x66798791);

        int badgeSize = Math.max(18, Math.min(34, layout.rowHeight() - 6));
        int badgeX = layout.listLeft() + 10;
        int badgeY = y + (layout.rowHeight() - badgeSize) / 2;
        graphics.fill(badgeX, badgeY, badgeX + badgeSize, badgeY + badgeSize, unlocked ? withAlpha(colors[2], selected ? 200 : 95) : 0xFF222A31);
        graphics.outline(badgeX, badgeY, badgeSize, badgeSize, unlocked ? colors[2] : 0xFF59636C);
        String roman = roman(level);
        graphics.text(font, roman, badgeX + (badgeSize - font.width(roman)) / 2, badgeY + Math.max(4, (badgeSize - 8) / 2), 0xFFFFFFFF, true);

        int buttonReserve = layout.listWidth() < 330 ? 96 : 118;
        int textX = badgeX + badgeSize + 10;
        int textRight = layout.listLeft() + layout.listWidth() - buttonReserve;
        int textWidth = Math.max(42, textRight - textX);
        String name = fit(displayName(powerClass, level), textWidth);
        int nameY = y + (veryCompact ? Math.max(4, (layout.rowHeight() - 8) / 2) : 6);
        graphics.text(font, name, textX, nameY, unlocked ? 0xFFFFFFFF : 0xFF8B969E, true);

        String description = displayDescription(powerClass, level);
        if (!veryCompact) {
            // Ekran yüksekliği azalsa bile bütün sınıflarda güç açıklaması görünür kalır.
            graphics.text(font, fit(description, textWidth), textX, y + 18, unlocked ? 0xFF91A8B5 : 0xFF8B969E, false);
        }

        if (layout.rowHeight() >= 46) {
            String stageName = PowerCatalog.masteryStageName(stage);
            int chipWidth = font.width(stageName) + 10;
            int chipX = Math.max(textX, textRight - chipWidth);
            graphics.fill(chipX, y + 5, chipX + chipWidth, y + 18, unlocked ? withAlpha(colors[2], 50 + stage * 28) : 0x332B3238);
            graphics.outline(chipX, y + 5, chipWidth, 13, unlocked ? withAlpha(colors[2], 180) : 0x5559636B);
            graphics.text(font, stageName, chipX + 5, y + 8, unlocked ? 0xFFFFFFFF : 0xFF77828A, false);
        }

        if (layout.rowHeight() >= 44) {
            int barY = y + layout.rowHeight() - 6;
            graphics.fill(textX, barY, textX + textWidth, barY + 3, 0xFF202933);
            if (unlocked) {
                int filled = Math.max(2, (int) (textWidth * PowerCatalog.masteryProgress(uses)));
                graphics.fill(textX, barY, textX + Math.min(textWidth, filled), barY + 3, colors[2]);
            }
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
        graphics.text(font, fit(displayName(powerClass, selected), width - 58), left + 42, top + 38, 0xFFFFFFFF, true);

        int lineY = top + 62;
        for (String line : wrap(displayDescription(powerClass, selected), width - 30, 4)) {
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
        int comboY = masteryY - 50;
        if (powerClass != PowerClass.ANOMALY && comboY > dividerY + 116) {
            graphics.fill(left + 15, comboY - 7, left + width - 15, comboY - 6, withAlpha(colors[2], 90));
            graphics.text(font, "KOMBİNASYON", left + 15, comboY + 2, 0xFFFFD35C, true);
            graphics.text(font, fit(PowerCatalog.comboName(powerClass), width - 30), left + 15, comboY + 15, 0xFFFFFFFF, true);
            graphics.text(font, fit(PowerCatalog.comboSequence(powerClass), width - 30), left + 15, comboY + 28, 0xFFC7D4DC, false);
            String comboMode = ClientState.comboModeEnabled() ? "K ile kapat" : "K ile aç";
            graphics.text(font, comboMode, left + width - font.width(comboMode) - 15, comboY + 2, ClientState.comboModeEnabled() ? 0xFF8CFFB0 : 0xFFB8C8D3, false);
        }
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
        graphics.fill(layout.totalLeft(), y, layout.totalLeft() + layout.totalWidth(), y + 40, 0xC9070B11);
        graphics.outline(layout.totalLeft(), y, layout.totalWidth(), 40, withAlpha(colors[2], 150));
        String titleLine = displayName(powerClass, selected) + "  •  " + controlHint(powerClass, selected) + (powerClass == PowerClass.ANOMALY ? "" : "  •  K: Kombo " + (ClientState.comboModeEnabled() ? "AÇIK" : "KAPALI"));
        String descriptionLine = displayDescription(powerClass, selected);
        graphics.text(font, fit(titleLine, layout.totalWidth() - 20), layout.totalLeft() + 10, y + 7, 0xFFFFFFFF, false);
        graphics.text(font, fit(descriptionLine, layout.totalWidth() - 20), layout.totalLeft() + 10, y + 22, 0xFFB9C8D1, false);
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
            graphics.fill(x + 14, y + 7, x + 22, y + 29, 0xFF12051B);
            graphics.fill(x + 2, y + 4, x + 15, y + 12, withAlpha(accent, 210));
            graphics.fill(x + 21, y + 4, x + 34, y + 12, withAlpha(accent, 210));
            graphics.fill(x + 6, y + 12, x + 15, y + 22, withAlpha(0xFFE5B6FF, 170));
            graphics.fill(x + 21, y + 12, x + 30, y + 22, withAlpha(0xFFE5B6FF, 170));
            graphics.fill(x + 11, y + 1, x + 15, y + 8, 0xFF5B1A81);
            graphics.fill(x + 21, y + 1, x + 25, y + 8, 0xFF5B1A81);
            graphics.fill(x + 16, y + 11, x + 20, y + 15, 0xFFFFFFFF);
        } else if (powerClass == PowerClass.FIRE) {
            graphics.fill(x + 11, y + 12, x + 27, y + 29, accent);
            graphics.fill(x + 15, y + 5, x + 24, y + 18, withAlpha(accent, 210));
            graphics.fill(x + 18, y, x + 22, y + 10, 0xFFFFFF8A);
        } else if (powerClass == PowerClass.NATURE) {
            graphics.fill(x + 16, y + 11, x + 21, y + 30, 0xFF7A4A25);
            graphics.fill(x + 4, y + 4, x + 18, y + 17, withAlpha(accent, 190));
            graphics.fill(x + 19, y, x + 33, y + 15, withAlpha(accent, 220));
            graphics.fill(x + 12, y - 2, x + 25, y + 11, 0xFF9BE66D);
        } else if (powerClass == PowerClass.ANOMALY) {
            graphics.outline(x + 3, y + 1, 29, 27, accent);
            graphics.fill(x + 7, y + 5, x + 29, y + 8, 0xFF5CE5E5);
            graphics.fill(x + 10, y + 12, x + 25, y + 16, 0xFFB65CFF);
            graphics.fill(x + 5, y + 21, x + 31, y + 24, 0xFFE94B63);
            graphics.text(font, "?", x + 15, y + 10, 0xFFFFFFFF, true);
        }
    }

    private String controlHint(PowerClass powerClass, int level) {
        if (powerClass == PowerClass.FLIGHT && level == 1) return "R: atıl • çift boşluk: hava hamlesi";
        if (powerClass == PowerClass.FLIGHT && level == 4) return "R: yakala • tekrar R: fırlat";
        if (powerClass == PowerClass.FLIGHT && level == 6) return "R: Ejderha Hükümdarı";
        if (powerClass == PowerClass.WARDEN && level == 6) return "R: başka oyuncuya ışın";
        if (powerClass == PowerClass.FIRE && (level == 1 || level == 2)) return "Otomatik";
        if (powerClass == PowerClass.ANOMALY && level == 3) return ClientState.copiedPowerName().isBlank() ? "R: hamle bekle" : "R: çalınan hamleyi kullan";
        if (powerClass == PowerClass.ANOMALY && level == 4) return "R: depola • V: kalp • X: geri gönder";
        return "R: kullan";
    }

    private String activeStatus(PowerClass powerClass, int level) {
        if (powerClass == PowerClass.FLIGHT && level == 3 && ClientState.dragonScalesTicks() > 0) {
            return String.format(java.util.Locale.ROOT, "Kadim Pullar %.1f sn", ClientState.dragonScalesTicks() / 20.0);
        }
        if (powerClass == PowerClass.FLIGHT && level == 6 && ClientState.dragonFormTicks() > 0) {
            return String.format(java.util.Locale.ROOT, "Ejderha Hükümdarı %.1f sn", ClientState.dragonFormTicks() / 20.0);
        }
        if (powerClass == PowerClass.WARDEN && level == 6) {
            return "20 sn • tek güç • 120 sn bekleme";
        }
        if (powerClass == PowerClass.WARDEN && level == 4 && ClientState.wardenHuntTicks() > 0) {
            return String.format(java.util.Locale.ROOT, "Derinlik Pususu %.1f sn", ClientState.wardenHuntTicks() / 20.0);
        }
        if (powerClass == PowerClass.NATURE && level == 4 && ClientState.natureTreeTicks() > 0) {
            return String.format(java.util.Locale.ROOT, "Yaşam Ağacı %.1f sn", ClientState.natureTreeTicks() / 20.0);
        }
        if (powerClass == PowerClass.ANOMALY && level == 3) {
            return ClientState.copiedPowerName().isBlank() ? "Hamle bekleniyor" : "Saklı: " + ClientState.copiedPowerName();
        }
        if (powerClass == PowerClass.ANOMALY && level == 4) {
            if (ClientState.anomalyChoiceTicks() > 0) return String.format(java.util.Locale.ROOT, "%.1f hasar: V veya X", ClientState.anomalyStoredDamage());
            if (ClientState.anomalyStoreTicks() > 0) return String.format(java.util.Locale.ROOT, "Depolama %.1f sn", ClientState.anomalyStoreTicks() / 20.0);
            if (ClientState.anomalyBonusHealthTicks() > 0) return String.format(java.util.Locale.ROOT, "+%.1f kalp %.0f sn", ClientState.anomalyBonusHealth() / 2.0, ClientState.anomalyBonusHealthTicks() / 20.0);
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
            int motion = ClientConfig.get().animatedBackgrounds() ? drift : 0;
            for (int i = -1; i < 7; i++) {
                int x = ((i * 145 + motion) % (width + 180)) - 90;
                int y = 78 + (i % 4) * 72;
                g.fill(x, y, x + 88, y + 11, 0x284D176B);
                g.fill(x + 18, y - 8, x + 61, y + 13, 0x22351452);
                if (i % 2 == 0 && !ClientConfig.get().photosensitiveMode()) {
                    g.fill(x + 44, y + 12, x + 46, y + 32, 0x55DCA1FF);
                    g.fill(x + 38, y + 30, x + 46, y + 33, 0x44B65CFF);
                }
            }
        } else if (powerClass == PowerClass.WARDEN) {
            for (int i = 0; i < 18; i++) {
                int x = (i * 119 + drift / 4) % Math.max(1, width);
                int y = 70 + (i * 43) % Math.max(90, height - 130);
                g.fill(x, y, x + 3, y + 3, withAlpha(accent, 65 + (i % 4) * 22));
            }
        } else if (powerClass == PowerClass.NATURE) {
            for (int i = 0; i < 16; i++) {
                int x = (i * 101 + drift / 3) % Math.max(1, width);
                int y = 75 + (i * 47) % Math.max(90, height - 130);
                g.fill(x, y, x + 7, y + 4, withAlpha(accent, 55 + (i % 4) * 18));
                g.fill(x + 3, y - 5, x + 5, y + 8, 0x557A4A25);
            }
        } else if (powerClass == PowerClass.ANOMALY) {
            for (int i = 0; i < 22; i++) {
                int x = (i * 107 + drift / 3) % Math.max(1, width);
                int y = 72 + (i * 53) % Math.max(90, height - 130);
                int glitch = (i % 3) * 4;
                g.fill(x - glitch, y, x + 10 + glitch, y + 2, withAlpha(i % 2 == 0 ? 0xFFB65CFF : 0xFF5CE5E5, 55 + (i % 4) * 20));
                if (i % 5 == 0) g.fill(x + 3, y - 4, x + 6, y + 8, 0x66E94B63);
            }
        }
    }


    private String displayName(PowerClass powerClass, int level) {
        if (powerClass == PowerClass.ANOMALY && level == 3 && !ClientState.copiedPowerName().isBlank()) {
            return ClientState.copiedPowerName();
        }
        return PowerCatalog.powerName(powerClass, level);
    }

    private String displayDescription(PowerClass powerClass, int level) {
        if (powerClass == PowerClass.ANOMALY && level == 3 && !ClientState.copiedPowerDescription().isBlank()) {
            return ClientState.copiedPowerDescription();
        }
        return PowerCatalog.powerDescription(powerClass, level);
    }

    private Layout layout() {
        // GUI ölçeği büyütüldüğünde ekranın mantıksal genişliği 430 px altına düşebilir.
        // Eski sabit minimum genişlik ve 38 px satır zorlaması Warden'ın 6. satırını ekran dışına itiyordu.
        int totalWidth = Math.max(180, Math.min(930, width - 20));
        boolean wide = totalWidth >= 760 && height >= 520;
        int detailWidth = wide ? 252 : 0;
        int gapToDetail = wide ? 14 : 0;
        int listWidth = totalWidth - detailWidth - gapToDetail;
        int totalLeft = Math.max(5, (width - totalWidth) / 2);
        int listLeft = totalLeft;
        int detailLeft = listLeft + listWidth + gapToDetail;
        int rowTop = height < 300 ? 62 : (height < 430 ? 68 : 78);
        int powerCount = PowerCatalog.maxLevel(ClientState.powerClass());
        int gap = powerCount >= 6 ? (height < 300 ? 1 : (height < 430 ? 3 : 5)) : (height < 300 ? 2 : 7);
        int footerSpace = wide ? 26 : (height < 300 ? 7 : (height < 430 ? 16 : 52));
        int available = Math.max(powerCount * 18, height - rowTop - footerSpace - gap * (powerCount - 1));
        int rowHeight = Math.max(18, Math.min(66, available / powerCount));
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
            case 5 -> "V";
            default -> "VI";
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int[] theme(PowerClass powerClass) {
        return switch (powerClass) {
            case WARDEN -> new int[]{0xFF010409, 0xFF0A2430, 0xFF35D7D0};
            case FLIGHT -> new int[]{0xFF05010B, 0xFF351052, 0xFFCE72FF};
            case FIRE -> new int[]{0xFF170201, 0xFF7C1608, 0xFFFFA51F};
            case NATURE -> new int[]{0xFF071008, 0xFF315A2A, 0xFF72D86A};
            case ANOMALY -> new int[]{0xFF05010B, 0xFF261044, 0xFFB65CFF};
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
