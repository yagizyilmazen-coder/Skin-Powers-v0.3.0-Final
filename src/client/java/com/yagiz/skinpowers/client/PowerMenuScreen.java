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
        boolean anim = ClientConfig.get().menuAnimations() && !ClientConfig.get().performanceMode();
        float appear = smoothStep(clamp01((now - openedAt) / (anim ? 280.0F : 1.0F)));

        // Koyu zemin + açık kitap (ağır parçacık yok)
        graphics.fillGradient(0, 0, width, height, 0xFF12100C, 0xFF080604);
        drawOpenBook(graphics, layout, colors);
        drawHeader(graphics, layout, powerClass, colors);

        int maximum = PowerCatalog.maxLevel(powerClass);
        int contentBottom = layout.rowTop() + maximum * layout.rowHeight() + (maximum - 1) * layout.gap();
        // Liste çerçevesi — sınıf rengine göre ince kenar
        graphics.fill(layout.listLeft() - 6, layout.rowTop() - 6, layout.listLeft() + layout.listWidth() + 6, contentBottom + 6, 0xD0080A10);
        graphics.outline(layout.listLeft() - 6, layout.rowTop() - 6, layout.listWidth() + 12, contentBottom - layout.rowTop() + 12, withAlpha(colors[2], 140));

        for (int level = 1; level <= maximum; level++) {
            float rowProgress = ClientUiRules.staggeredProgress(appear, level - 1, maximum, anim ? 0.18F : 0.0F);
            int baseY = layout.rowTop() + (level - 1) * (layout.rowHeight() + layout.gap());
            int y = baseY + (int) ((1.0F - rowProgress) * 14.0F);
            drawPowerRow(graphics, layout, powerClass, colors, level, y, now);
            Button button = powerButtons[level - 1];
            if (button != null) {
                int buttonHeight = Math.max(16, Math.min(22, layout.rowHeight() - 4));
                button.setY(y + (layout.rowHeight() - buttonHeight) / 2);
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
        // Parşömen başlık şeridi
        drawParchment(graphics, layout.totalLeft(), top, layout.totalWidth(), bottom - top);
        graphics.fill(layout.totalLeft(), top, layout.totalLeft() + 4, bottom, 0xFF5C3A1E);
        graphics.outline(layout.totalLeft(), top, layout.totalWidth(), bottom - top, 0xFF3D2814);
        graphics.fill(layout.totalLeft() + 5, top + 2, layout.totalLeft() + layout.totalWidth() - 2, top + 4, withAlpha(colors[2], 100));

        drawClassEmblem(graphics, powerClass, layout.totalLeft() + 20, 21, colors[2]);
        String xp = "XP  " + ClientState.xpLevel();
        int xpWidth = Math.max(70, font.width(xp) + 18);
        int xpX = layout.totalLeft() + layout.totalWidth() - xpWidth - 14;
        int headerTextX = layout.totalLeft() + 62;
        int headerTextWidth = Math.max(90, xpX - headerTextX - 12);
        String heading = fit(powerClass.displayName() + " GÜÇ AĞACI", headerTextWidth);
        graphics.text(font, heading, headerTextX, 18, 0xFF1A1008, true);
        graphics.text(font, fit("Gücünü seç, ustalığını geliştir ve R ile kullan.", headerTextWidth), headerTextX, 35, 0xFF5C3D1E, false);
        graphics.text(font, fit(powerClass == PowerClass.ANOMALY ? "Sol/Sağ: güç değiştir   •   V/X: hasar seçimi   •   O / ESC: menü" : "Sol/Sağ: güç değiştir   •   K: kombo   •   O / ESC: menü", headerTextWidth), headerTextX, 49, 0xFF8799A5, false);

        graphics.fill(xpX, 20, xpX + xpWidth, 45, withAlpha(colors[2], 65));
        graphics.outline(xpX, 20, xpWidth, 25, colors[2]);
        graphics.text(font, xp, xpX + (xpWidth - font.width(xp)) / 2, 29, 0xFF1A1008, true);
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
        int powerAccent = PowerIconArt.shade(colors[2], level);

        // Net durum renkleri: seçili / açık / kilitli
        int rowColor;
        if (selected) {
            rowColor = 0xF0C9A878;
        } else if (unlocked) {
            rowColor = 0xE8D8C4A0;
        } else {
            rowColor = 0xC08A7A60;
        }
        graphics.fill(layout.listLeft(), y, layout.listLeft() + layout.listWidth(), y + layout.rowHeight(), rowColor);
        // Sol şerit: seçili = tam accent, açık = yarı, kilitli = koyu
        int strip = selected ? powerAccent : (unlocked ? withAlpha(powerAccent, 170) : 0xFF3A3020);
        graphics.fill(layout.listLeft(), y, layout.listLeft() + 4, y + layout.rowHeight(), strip);
        graphics.outline(layout.listLeft(), y, layout.listWidth(), layout.rowHeight(),
            selected ? powerAccent : (unlocked ? 0xAA6A5040 : 0x66403020));

        int badgeSize = Math.max(18, Math.min(34, layout.rowHeight() - 6));
        int badgeX = layout.listLeft() + 10;
        int badgeY = y + (layout.rowHeight() - badgeSize) / 2;
        graphics.fill(badgeX, badgeY, badgeX + badgeSize, badgeY + badgeSize,
            unlocked ? withAlpha(powerAccent, selected ? 100 : 50) : 0xFF1A1E24);
        graphics.outline(badgeX, badgeY, badgeSize, badgeSize, unlocked ? powerAccent : 0xFF4A5058);
        int iconInset = Math.max(2, badgeSize / 9);
        PowerIconArt.draw(graphics, powerClass, level, badgeX + iconInset, badgeY + iconInset,
            badgeSize - iconInset * 2, unlocked ? powerAccent : 0xFF5A6068);
        if (badgeSize >= 22) {
            String roman = roman(level);
            int tagSize = Math.max(9, badgeSize / 3);
            int tagX = badgeX + badgeSize - tagSize;
            int tagY = badgeY + badgeSize - tagSize;
            graphics.fill(tagX, tagY, badgeX + badgeSize, badgeY + badgeSize, 0xE0060A0F);
            graphics.text(font, roman, tagX + Math.max(1, (tagSize - font.width(roman)) / 2),
                tagY + Math.max(1, (tagSize - 7) / 2), unlocked ? 0xFFE8E0D0 : 0xFF7A8088, true);
        }

        int buttonReserve = layout.listWidth() < 330 ? 96 : 118;
        int textX = badgeX + badgeSize + 10;
        int textRight = layout.listLeft() + layout.listWidth() - buttonReserve;
        int textWidth = Math.max(42, textRight - textX);
        String name = fit(displayName(powerClass, level), textWidth);
        int nameY = y + (veryCompact ? Math.max(4, (layout.rowHeight() - 8) / 2) : 6);
        graphics.text(font, name, textX, nameY, unlocked ? 0xFF1A1008 : 0xFF5A5040, true);

        String description = displayDescription(powerClass, level);
        if (!veryCompact) {
            graphics.text(font, fit(description, textWidth), textX, y + 18,
                unlocked ? 0xFF4A3820 : 0xFF6A6050, false);
        }

        if (layout.rowHeight() >= 46 && unlocked) {
            String stageName = PowerCatalog.masteryStageName(stage);
            String chipText = PowerIconArt.tag(powerClass, level) + "  •  " + stageName;
            int chipWidth = font.width(chipText) + 10;
            int chipX = Math.max(textX, textRight - chipWidth);
            graphics.fill(chipX, y + 5, chipX + chipWidth, y + 18, withAlpha(powerAccent, 45 + stage * 20));
            graphics.outline(chipX, y + 5, chipWidth, 13, withAlpha(powerAccent, 160));
            graphics.text(font, chipText, chipX + 5, y + 8, 0xFF1A1008, false);
        }

        if (layout.rowHeight() >= 44) {
            int barY = y + layout.rowHeight() - 6;
            graphics.fill(textX, barY, textX + textWidth, barY + 3, 0xFF1A1E24);
            if (unlocked) {
                int filled = Math.max(2, (int) (textWidth * PowerCatalog.masteryProgress(uses)));
                graphics.fill(textX, barY, textX + Math.min(textWidth, filled), barY + 3, powerAccent);
            }
        }

        // Seçili satır: sabit (nabızsız) dış çerçeve
        if (selected) {
            graphics.outline(layout.listLeft() - 1, y - 1, layout.listWidth() + 2, layout.rowHeight() + 2, withAlpha(powerAccent, 200));
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
        // Sağ sayfa (parşömen)
        drawParchment(graphics, left, top, width, height);
        graphics.outline(left, top, width, height, 0xFF3D2814);
        graphics.fill(left, top, left + width, top + 3, 0xFF5C3A1E);
        graphics.fill(left, top, left + 3, top + height, 0xFF5C3A1E);

        int selected = ClientState.selectedPower();
        int uses = ClientState.masteryUses(selected);
        int stage = ClientState.masteryStage(selected);
        int powerAccent = PowerIconArt.shade(colors[2], selected);

        int iconSize = 32;
        int iconX = left + width - iconSize - 14;
        int iconY = top + 12;
        graphics.fill(iconX - 4, iconY - 4, iconX + iconSize + 4, iconY + iconSize + 4, withAlpha(powerAccent, 45));
        graphics.outline(iconX - 4, iconY - 4, iconSize + 8, iconSize + 8, withAlpha(powerAccent, 160));
        PowerIconArt.draw(graphics, powerClass, selected, iconX, iconY, iconSize, powerAccent);

        graphics.text(font, "SEÇİLİ GÜÇ", left + 15, top + 17, powerAccent, true);
        graphics.text(font, roman(selected), left + 15, top + 38, 0xFF1A1008, true);
        graphics.text(font, fit(displayName(powerClass, selected), width - iconSize - 74), left + 42, top + 38, 0xFF1A1008, true);

        String tag = PowerIconArt.tag(powerClass, selected);
        int tagWidth = font.width(tag) + 10;
        graphics.fill(left + 15, top + 50, left + 15 + tagWidth, top + 61, withAlpha(powerAccent, 90));
        graphics.outline(left + 15, top + 50, tagWidth, 11, withAlpha(powerAccent, 200));
        graphics.text(font, tag, left + 20, top + 52, 0xFF1A1008, false);
        graphics.text(font, fit(PowerIconArt.flavor(powerClass, selected), width - tagWidth - 40), left + 25 + tagWidth, top + 52, 0xFF3A2A18, false);

        int lineY = top + 68;
        for (String line : wrap(displayDescription(powerClass, selected), width - 30, 4)) {
            graphics.text(font, line, left + 15, lineY, 0xFF2A1C10, false);
            lineY += 12;
        }

        int dividerY = Math.max(top + 120, lineY + 5);
        graphics.fill(left + 15, dividerY, left + width - 15, dividerY + 1, withAlpha(colors[2], 110));

        String control = controlHint(powerClass, selected);
        graphics.text(font, "KONTROL", left + 15, dividerY + 13, 0xFF4A3820, false);
        graphics.text(font, control, left + 15, dividerY + 27, 0xFF1A1008, true);

        String cooldown = ClientState.cooldownTicks() <= 0
            ? "HAZIR"
            : String.format(java.util.Locale.ROOT, "%.1f saniye", ClientState.cooldownTicks() / 20.0);
        graphics.text(font, "BEKLEME", left + 15, dividerY + 50, 0xFF4A3820, false);
        graphics.text(font, cooldown, left + 15, dividerY + 64, ClientState.cooldownTicks() <= 0 ? 0xFF1A6030 : 0xFF6A4010, true);

        String status = activeStatus(powerClass, selected);
        graphics.text(font, "DURUM", left + 15, dividerY + 87, 0xFF4A3820, false);
        graphics.text(font, status, left + 15, dividerY + 101, 0xFF1A1008, true);

        int masteryY = top + height - 68;
        int comboY = masteryY - 50;
        if (powerClass != PowerClass.ANOMALY && comboY > dividerY + 116) {
            graphics.fill(left + 15, comboY - 7, left + width - 15, comboY - 6, withAlpha(colors[2], 90));
            graphics.text(font, "KOMBİNASYON", left + 15, comboY + 2, 0xFFFFD35C, true);
            graphics.text(font, fit(PowerCatalog.comboName(powerClass), width - 30), left + 15, comboY + 15, 0xFF1A1008, true);
            graphics.text(font, fit(PowerCatalog.comboSequence(powerClass), width - 30), left + 15, comboY + 28, 0xFFC7D4DC, false);
            String comboMode = ClientState.comboModeEnabled() ? "K ile kapat" : "K ile aç";
            graphics.text(font, comboMode, left + width - font.width(comboMode) - 15, comboY + 2, ClientState.comboModeEnabled() ? 0xFF8CFFB0 : 0xFF3A2A18, false);
        }
        graphics.fill(left + 15, masteryY - 8, left + width - 15, masteryY - 7, withAlpha(colors[2], 100));
        graphics.text(font, "USTALIK", left + 15, masteryY + 3, 0xFF4A3820, false);
        String mastery = PowerCatalog.masteryStageName(stage) + "  •  " + uses + " kullanım";
        graphics.text(font, mastery, left + 15, masteryY + 17, 0xFF1A1008, true);
        int barWidth = width - 30;
        graphics.fill(left + 15, masteryY + 36, left + 15 + barWidth, masteryY + 42, 0xFF202933);
        graphics.fill(left + 15, masteryY + 36, left + 15 + Math.max(2, (int) (barWidth * PowerCatalog.masteryProgress(uses))), masteryY + 42, colors[2]);
        graphics.outline(left + 7, top + 7, width - 14, height - 14, withAlpha(colors[2], 55));
    }

    private void drawCompactFooter(GuiGraphicsExtractor graphics, Layout layout, PowerClass powerClass, int[] colors, int contentBottom) {
        int y = contentBottom + 12;
        int selected = ClientState.selectedPower();
        graphics.fill(layout.totalLeft(), y, layout.totalLeft() + layout.totalWidth(), y + 40, 0xC9070B11);
        graphics.outline(layout.totalLeft(), y, layout.totalWidth(), 40, withAlpha(colors[2], 150));
        String titleLine = displayName(powerClass, selected) + "  •  " + controlHint(powerClass, selected) + (powerClass == PowerClass.ANOMALY ? "" : "  •  K: Kombo " + (ClientState.comboModeEnabled() ? "AÇIK" : "KAPALI"));
        String descriptionLine = displayDescription(powerClass, selected);
        graphics.text(font, fit(titleLine, layout.totalWidth() - 20), layout.totalLeft() + 10, y + 7, 0xFFFFFFFF, false);
        graphics.text(font, fit(descriptionLine, layout.totalWidth() - 20), layout.totalLeft() + 10, y + 22, 0xFF5C3D1E, false);
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
        } else if (powerClass == PowerClass.MOON) {
            graphics.fill(x + 5, y + 2, x + 31, y + 28, withAlpha(0xFFE5EDFF, 220));
            graphics.fill(x + 15, y, x + 34, y + 30, 0xFF10182D);
            graphics.outline(x + 5, y + 2, 26, 26, accent);
            graphics.fill(x + 1, y + 13, x + 7, y + 16, 0xFFAFC6FF);
            graphics.fill(x + 30, y + 10, x + 35, y + 13, 0xFF9474D6);
        } else if (powerClass == PowerClass.ANOMALY) {
            graphics.outline(x + 3, y + 1, 29, 27, accent);
            graphics.fill(x + 7, y + 5, x + 29, y + 8, 0xFF5CE5E5);
            graphics.fill(x + 10, y + 12, x + 25, y + 16, 0xFFB65CFF);
            graphics.fill(x + 5, y + 21, x + 31, y + 24, 0xFFE94B63);
            graphics.text(font, "?", x + 15, y + 10, 0xFFFFFFFF, true);
        } else if (powerClass == PowerClass.MAGNETIC) {
            graphics.fill(x + 2, y + 5, x + 15, y + 25, 0xFFC5D2DE);
            graphics.fill(x + 21, y + 5, x + 34, y + 25, 0xFF65737F);
            graphics.fill(x + 15, y + 1, x + 21, y + 29, 0xFFC67B42);
            graphics.outline(x + 1, y + 4, 34, 22, accent);
        } else if (powerClass == PowerClass.SAND) {
            graphics.fill(x + 3, y + 17, x + 33, y + 29, 0xFFD0A454);
            graphics.fill(x + 8, y + 8, x + 28, y + 23, 0xFFE7CE88);
            graphics.fill(x + 13, y + 1, x + 23, y + 12, 0xFFF2DC9D);
            graphics.fill(x + 16, y + 17, x + 20, y + 29, 0xFF78522C);
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
        if (powerClass == PowerClass.MOON && level == 2) return "R: hedefe Ay Mührü bırak";
        if (powerClass == PowerClass.MOON && level == 4) return "R: ayna • tekrar R: ay halkası fırlat";
        if (powerClass == PowerClass.MOON && level == 5) return "R: Tutulma Hükmü";
        if (powerClass == PowerClass.MAGNETIC && level == 4) return "R: hazırla • tekrar R: fırlat";
        if (powerClass == PowerClass.SAND && level == 5) return "R: göm • suyla kaçış";
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
        if (powerClass == PowerClass.MOON && level == 5) {
            return ClientState.cooldownTicks() <= 0 ? "Tutulma Hükmü hazır" : "Tutulma Hükmü beklemede";
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
        } else if (powerClass == PowerClass.MOON) {
            for (int i = 0; i < 22; i++) {
                int x = (i * 101 + drift / 5) % Math.max(1, width);
                int y = 65 + (i * 47) % Math.max(90, height - 120);
                int size = i % 5 == 0 ? 3 : 2;
                g.fill(x, y, x + size, y + size, withAlpha(i % 3 == 0 ? 0xFFDCE6FF : accent, 55 + (i % 4) * 22));
            }
            int moonX = width - 68;
            int moonY = 55;
            g.fill(moonX - 18, moonY - 18, moonX + 18, moonY + 18, withAlpha(0xFFE5EDFF, 120));
            g.fill(moonX - 4, moonY - 20, moonX + 22, moonY + 20, 0xAA080D1B);
        } else if (powerClass == PowerClass.ANOMALY) {
            for (int i = 0; i < 22; i++) {
                int x = (i * 107 + drift / 3) % Math.max(1, width);
                int y = 72 + (i * 53) % Math.max(90, height - 130);
                int glitch = (i % 3) * 4;
                g.fill(x - glitch, y, x + 10 + glitch, y + 2, withAlpha(i % 2 == 0 ? 0xFFB65CFF : 0xFF5CE5E5, 55 + (i % 4) * 20));
                if (i % 5 == 0) g.fill(x + 3, y - 4, x + 6, y + 8, 0x66E94B63);
            }
        } else if (powerClass == PowerClass.MAGNETIC) {
            for (int i = 0; i < 18; i++) {
                int x = (i * 97 + drift) % Math.max(1, width);
                int y = 74 + (i * 41) % Math.max(90, height - 130);
                g.fill(x, y, x + 9, y + 5, withAlpha(i % 2 == 0 ? 0xFFC5D2DE : 0xFFC67B42, 58 + (i % 4) * 20));
            }
        } else if (powerClass == PowerClass.SAND) {
            for (int i = 0; i < 24; i++) {
                int x = (i * 83 + drift * 2) % Math.max(1, width);
                int y = 72 + (i * 37) % Math.max(90, height - 130);
                g.fill(x, y, x + 8, y + 5, withAlpha(i % 3 == 0 ? 0xFFF0D58A : 0xFFD0A454, 54 + (i % 4) * 18));
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


    /** Açık kitap: deri kapak + sırt + parşömen sayfalar. */
    private void drawOpenBook(GuiGraphicsExtractor g, Layout layout, int[] colors) {
        int left = Math.max(4, layout.totalLeft() - 10);
        int top = 6;
        int bookW = layout.totalWidth() + 20;
        int bookH = Math.max(120, height - 14);
        // Gölge
        g.fill(left + 3, top + 4, left + bookW + 3, top + bookH + 4, 0x66000000);
        // Deri kapak
        g.fill(left, top, left + bookW, top + bookH, 0xFF3B2414);
        g.fill(left + 3, top + 3, left + bookW - 3, top + bookH - 3, 0xFF4A2E18);
        // Sırt
        int spineX = left + bookW / 2 - 7;
        g.fill(spineX, top + 2, spineX + 14, top + bookH - 2, 0xFF2A180C);
        g.fill(spineX + 4, top + 8, spineX + 10, top + bookH - 8, 0xFF1E1008);
        // Sol sayfa
        int pageL = left + 8;
        int pageR = spineX - 4;
        int pageT = top + 8;
        int pageB = top + bookH - 8;
        if (pageR > pageL + 20 && pageB > pageT + 20) {
            drawParchment(g, pageL, pageT, pageR - pageL, pageB - pageT);
            g.outline(pageL, pageT, pageR - pageL, pageB - pageT, 0xFF5C4030);
        }
        // Sağ sayfa
        int pageL2 = spineX + 18;
        int pageR2 = left + bookW - 8;
        if (pageR2 > pageL2 + 20 && pageB > pageT + 20) {
            drawParchment(g, pageL2, pageT, pageR2 - pageL2, pageB - pageT);
            g.outline(pageL2, pageT, pageR2 - pageL2, pageB - pageT, 0xFF5C4030);
        }
        // Köşe süs
        g.fill(left + 6, top + 6, left + 16, top + 9, withAlpha(colors[2], 180));
        g.fill(left + bookW - 16, top + 6, left + bookW - 6, top + 9, withAlpha(colors[2], 180));
    }

    /** Parşömen kağıdı — sade gradient, nokta gürültüsü yok. */
    private static void drawParchment(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, 0xFFE4D4AA, 0xFFC8B48C);
        g.fill(x, y, x + w, y + 1, 0x33A08050);
        g.fill(x, y + h - 1, x + w, y + h, 0x44806040);
    }

    private static int[] theme(PowerClass powerClass) {
        return switch (powerClass) {
            case WARDEN -> new int[]{0xFF010409, 0xFF0A2430, 0xFF35D7D0};
            case FLIGHT -> new int[]{0xFF05010B, 0xFF351052, 0xFFCE72FF};
            case FIRE -> new int[]{0xFF170201, 0xFF7C1608, 0xFFFFA51F};
            case MOON -> new int[]{0xFF030714, 0xFF27385E, 0xFFDCE6FF};
            case ANOMALY -> new int[]{0xFF05010B, 0xFF261044, 0xFFB65CFF};
            case MAGNETIC -> new int[]{0xFF080D12, 0xFF3B4954, 0xFFC5D2DE};
            case SAND -> new int[]{0xFF2E1C0C, 0xFF9B6D30, 0xFFFFD273};
            case ICE -> new int[]{0xFF0A1A28, 0xFF4A90B8, 0xFF8FD4FF};
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
