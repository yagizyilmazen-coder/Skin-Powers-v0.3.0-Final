package com.yagiz.skinpowers.client;

import com.yagiz.skinpowers.PowerClass;
import com.yagiz.skinpowers.network.ClientCommandPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class SkinSelectionScreen extends Screen {
    private static final String[] TITLES = {"WARDEN", "KADİM EJDERHA", "ATEŞ", "AY", "ANOMALİ", "MANYETİK", "KUM"};
    private static final String[] SUBTITLES = {"Derinliğin gücü", "Mor kıyametin kanatları", "Alevin hâkimiyeti", "Tutulmanın hükmü", "Gerçekliğin hatası", "Metalin kutupları", "Çölün şekillenen gücü"};
    private static final PowerClass[] CLASSES = {PowerClass.WARDEN, PowerClass.FLIGHT, PowerClass.FIRE, PowerClass.MOON, PowerClass.ANOMALY, PowerClass.MAGNETIC, PowerClass.SAND};
    private static final int[] TOP_COLORS = {0xFF07111C, 0xFF08020F, 0xFF5B0B08, 0xFF060A1C, 0xFF05010B, 0xFF121820, 0xFF4C2F13};
    private static final int[] BOTTOM_COLORS = {0xFF16384B, 0xFF451070, 0xFFFF6B18, 0xFF596B9E, 0xFF291248, 0xFF586875, 0xFFD2A34D};
    private static final int[] ACCENTS = {0xFF35D7D0, 0xFFCE72FF, 0xFFFFC22E, 0xFFD9E4FF, 0xFFB65CFF, 0xFFC5D2DE, 0xFFFFD273};

    private final long openedAt = Util.getMillis();
    private SkinAnalyzer.Result result = SkinAnalyzer.Result.unavailable();
    private boolean analysisFinished;
    private int selectedIndex = -1;
    private long selectedAt;
    private final Button[] selectButtons = new Button[CLASSES.length];
    private CardLayout cachedLayout;
    private int cachedLayoutWidth = -1;
    private int cachedLayoutHeight = -1;

    public SkinSelectionScreen() {
        super(Component.translatable("screen.skinpowers.title"));
    }

    @Override
    protected void init() {
        CardLayout layout = layout();
        for (int i = 0; i < CLASSES.length; i++) {
            final int index = i;
            int buttonWidth = Math.max(28, layout.cardWidth() - (layout.compact() ? 6 : 24));
            int x = layout.startX() + i * (layout.cardWidth() + layout.gap()) + (layout.cardWidth() - buttonWidth) / 2;
            int buttonHeight = layout.compact() ? 18 : 20;
            int y = layout.cardY() + layout.cardHeight() - buttonHeight - 7;
            selectButtons[i] = Button.builder(Component.translatable("screen.skinpowers.select"), button -> select(index))
                .bounds(x, y + 35, buttonWidth, buttonHeight)
                .build();
            selectButtons[i].active = false;
            addRenderableWidget(selectButtons[i]);
        }

        if (minecraft != null && minecraft.player != null) {
            SkinAnalyzer.analyzeAsync(minecraft.player.getGameProfile(), true).thenAccept(analyzed ->
                minecraft.execute(() -> {
                    result = analyzed;
                    analysisFinished = true;
                })
            );
        } else {
            analysisFinished = true;
        }
    }

    private void select(int index) {
        if (selectedIndex >= 0 || !isSelectable(index)) return;
        selectedIndex = index;
        selectedAt = Util.getMillis();
        ClientPlayNetworking.send(new ClientCommandPayload("CHOOSE:" + CLASSES[index].name()));
    }

    private boolean isSelectable(int index) {
        if (!analysisFinished || index < 0 || index >= CLASSES.length) return false;
        // Gerçek skin analizi başarılıysa yalnızca en yüksek ve ikinci en yüksek puanlı sınıf seçilebilir.
        // Skin alınamadığında oyuncuyu kilitlememek için bütün sınıflar geçici olarak seçilebilir.
        return ClientUiRules.classChoiceAllowed(
            analysisFinished,
            result.hasRecommendation(),
            index,
            CLASSES.length,
            result.bestIndex(),
            result.secondIndex()
        );
    }

    @Override
    public void tick() {
        super.tick();
        if (selectedIndex < 0 || minecraft == null) return;

        // Ekran yalnızca sunucu seçimi gerçekten onayladıktan sonra kapanır.
        if (ClientState.powerClass() == CLASSES[selectedIndex]) {
            if (Util.getMillis() - selectedAt > 250L) minecraft.setScreen(null);
            return;
        }

        // Paket reddedilir veya bağlantı gecikirse seçim düğmelerini tekrar kullanılabilir yap.
        if (Util.getMillis() - selectedAt > 3000L) selectedIndex = -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, width, height, 0xFF03060B, 0xFF101622);

        long now = Util.getMillis();
        ClientConfig config = ClientConfig.get();
        float animationFactor = config.menuAnimations() ? config.cardAnimationSpeedPercent() / 100.0F : 8.0F;
        float totalProgress = clamp01((now - openedAt) / (900.0F / animationFactor));
        CardLayout layout = layout();
        String titleText = fit(title.getString(), Math.max(80, width - (layout.compact() ? 150 : 250)));
        int titleAreaWidth = layout.compact() ? width - 78 : width;
        graphics.text(font, titleText, Math.max(7, (titleAreaWidth - font.width(titleText)) / 2), layout.compact() ? 9 : 12, 0xFFFFFFFF, true);

        String scanText;
        if (!analysisFinished) {
            scanText = Component.translatable("screen.skinpowers.scan").getString();
        } else if (!result.fromSkin()) {
            scanText = "Skin alınamadı — puan uydurulmadı, sınıfı kendin seç";
        } else if (result.hasRecommendation()) {
            scanText = "Skin tarandı — yalnızca 1. ve 2. öneri seçilebilir";
        } else {
            scanText = "Skin tarandı — belirgin bir sınıf rengi bulunamadı";
        }
        scanText = fit(scanText, Math.max(100, width - 20));
        graphics.text(font, scanText, (width - font.width(scanText)) / 2, layout.compact() ? 26 : 28, analysisFinished ? 0xFF9BEFD9 : 0xFFE7F4FF, false);

        if (!layout.compact() && !ClientConfig.get().performanceMode()) drawAvatar(graphics, now);

        for (int i = 0; i < CLASSES.length; i++) {
            float cardProgress = ClientUiRules.staggeredProgress(totalProgress, i, CLASSES.length, 0.34F);
            int baseX = layout.startX() + i * (layout.cardWidth() + layout.gap());
            int shake = 0;
            if (selectedIndex == i) {
                long elapsed = now - selectedAt;
                if (elapsed < 420L) shake = (int) Math.round(Math.sin(elapsed * 0.085) * (1.0 - elapsed / 420.0) * 7.0);
            }
            int y = layout.cardY() + (int) ((1.0F - cardProgress) * 35.0F);
            drawCard(graphics, i, baseX + shake, y, layout.cardWidth(), layout.cardHeight(), cardProgress, now);

            // Seçim düğmesi karttan ayrı kalmaz; kartla aynı animasyon grubunda yükselir.
            Button selectButton = selectButtons[i];
            if (selectButton != null) {
                int buttonHeight = layout.compact() ? 18 : 20;
                selectButton.setY(y + layout.cardHeight() - buttonHeight - 7);
                boolean selectable = isSelectable(i);
                String buttonText = selectable ? "SEÇ" : (analysisFinished ? (layout.cardWidth() < 68 ? "KİLİT" : "KİLİTLİ") : "...");
                selectButton.setMessage(Component.literal(buttonText));
                // Düğme, kartın son animasyon karesine matematiksel olarak bağlı değildir.
                // Böylece ikinci öneri Anomali olsa bile tıklanabilir kalır.
                selectButton.active = cardProgress >= 0.85F && selectedIndex < 0 && selectable;
            }
        }

        if (!analysisFinished && ClientConfig.get().scanAnimation()) {
            int scanX = (int) ((now - openedAt) % 1400L / 1400.0 * width);
            graphics.fill(scanX - 1, 42, scanX + 2, height - 12, 0x5535D7D0);
        }
        String signature = "Made by Yankalan";
        graphics.text(font, signature, width - font.width(signature) - 7, height - 11, 0xFF85D68A, true);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void drawCard(GuiGraphicsExtractor graphics, int index, int x, int y, int w, int h, float progress, long now) {
        int alpha = (int) (255 * progress);
        int top = withAlpha(TOP_COLORS[index], alpha);
        int bottom = withAlpha(BOTTOM_COLORS[index], alpha);
        graphics.fillGradient(x, y, x + w, y + h, top, bottom);

        boolean recommended = analysisFinished && result.bestIndex() == index;
        boolean secondRecommended = analysisFinished && result.secondIndex() == index;
        float pulse = (float) ((Math.sin(now / 260.0) + 1.0) * 0.5);
        int outlineAlpha = recommended ? 180 + (int) (75 * pulse) : (secondRecommended ? 155 + (int) (55 * pulse) : 110);
        graphics.outline(x, y, w, h, withAlpha(ACCENTS[index], outlineAlpha));
        if (recommended || secondRecommended) {
            graphics.outline(x - 2, y - 2, w + 4, h + 4, withAlpha(ACCENTS[index], (recommended ? 80 : 48) + (int) ((recommended ? 80 : 45) * pulse)));
        }

        int buttonHeight = layout().compact() ? 18 : 20;
        int buttonTop = y + h - buttonHeight - 7;
        boolean showSubtitle = !layout().compact() && w >= 96;
        int textReserve = showSubtitle ? 48 : 34;
        int artBottom = Math.max(y + 34, buttonTop - textReserve);
        graphics.enableScissor(x + 2, y + 2, x + w - 2, artBottom);
        switch (index) {
            case 0 -> drawAncientCity(graphics, x, y, w, artBottom - y, now);
            case 1 -> drawDragonStorm(graphics, x, y, w, artBottom - y, now);
            case 2 -> drawLavaCave(graphics, x, y, w, artBottom - y, now);
            case 3 -> drawMoon(graphics, x, y, w, artBottom - y, now);
            case 4 -> drawAnomalyGlitch(graphics, x, y, w, artBottom - y, now);
            case 5 -> drawMagneticForge(graphics, x, y, w, artBottom - y, now);
            case 6 -> drawDesertTemple(graphics, x, y, w, artBottom - y, now);
            default -> { }
        }
        graphics.disableScissor();

        int score = (int) Math.round(result.score(index) * 100.0);
        String titleText = fit(TITLES[index], w - 10);
        String subtitleText = fit(SUBTITLES[index], w - 10);
        graphics.text(font, titleText, x + (w - font.width(titleText)) / 2, artBottom + 5, 0xFFFFFFFF, true);
        int scoreY;
        if (showSubtitle) {
            graphics.text(font, subtitleText, x + (w - font.width(subtitleText)) / 2, artBottom + 18, 0xFFE8EEF4, false);
            scoreY = artBottom + 32;
        } else {
            scoreY = artBottom + 18;
        }
        String scoreText = !analysisFinished ? (w < 82 ? "..." : "Taranıyor...")
            : (result.fromSkin() ? (w < 82 ? "%" + score : "Eşleşme: %" + score) : "Eşleşme: —");
        scoreText = fit(scoreText, w - 8);
        graphics.text(font, scoreText, x + (w - font.width(scoreText)) / 2, scoreY, recommended ? ACCENTS[index] : 0xFFC8D0D8, false);
        if (recommended || secondRecommended) {
            String recommendedText = recommended ? "1. ÖNERİ" : "2. ÖNERİ";
            graphics.fill(x + 5, y + 6, x + 5 + font.width(recommendedText) + 7, y + 19, withAlpha(ACCENTS[index], recommended ? 180 : 125));
            graphics.text(font, recommendedText, x + 9, y + 8, 0xFFFFFFFF, true);
        }

        int iconRowY = scoreY + 11;
        int iconRowSpace = buttonTop - 3 - iconRowY;
        if (w >= 70 && iconRowSpace >= 8) {
            int count = 6;
            int gap2 = 2;
            int iconSize = Math.max(6, Math.min(12, (w - 12 - gap2 * (count - 1)) / count));
            int rowWidth = iconSize * count + gap2 * (count - 1);
            int iconX = x + (w - rowWidth) / 2;
            int iconY = iconRowY + Math.max(0, (iconRowSpace - iconSize) / 2);
            PowerClass previewClass = CLASSES[index];
            for (int level = 1; level <= count; level++) {
                int iconAccent = withAlpha(PowerIconArt.shade(ACCENTS[index], level), (recommended || secondRecommended) ? 235 : 190);
                PowerIconArt.draw(graphics, previewClass, level, iconX, iconY, iconSize, iconAccent);
                iconX += iconSize + gap2;
            }
        }
    }

    private void drawAvatar(GuiGraphicsExtractor graphics, long now) {
        int centerX = width / 2;
        int top = 43;
        int sway = (int) Math.round(Math.sin(now / 520.0) * 2.0);
        int x = centerX + sway;

        graphics.fill(x - 22, top + 64, x + 22, top + 68, 0x44000000);
        if (!result.hasSkinImage()) {
            // Skin yüklenmediyse Steve benzeri sahte bir karakter çizilmez.
            graphics.fill(x - 19, top + 7, x + 19, top + 55, 0xAA101722);
            graphics.outline(x - 19, top + 7, 38, 48, 0xCC63FFF1);
            String waiting = "?";
            graphics.text(font, waiting, x - font.width(waiting) / 2, top + 25, 0xFFFFFFFF, true);
            String label = "SKIN BEKLENİYOR";
            graphics.text(font, label, x - font.width(label) / 2, top + 58, 0xFF9BEFD9, false);
        } else {
            int armSwing = (int) Math.round(Math.sin(now / 260.0) * 1.5);
            boolean modern = result.skinHeight() >= 64;

            drawSkinPart(graphics, 8, 8, 8, 8, x - 8, top, 2);
            drawSkinPart(graphics, 40, 8, 8, 8, x - 8, top, 2);

            drawSkinPart(graphics, 20, 20, 8, 12, x - 8, top + 16, 2);
            if (modern) drawSkinPart(graphics, 20, 36, 8, 12, x - 8, top + 16, 2);

            drawSkinPart(graphics, 44, 20, 4, 12, x - 16, top + 16 + armSwing, 2);
            if (modern) drawSkinPart(graphics, 44, 36, 4, 12, x - 16, top + 16 + armSwing, 2);

            int leftArmX = modern ? 36 : 44;
            int leftArmY = modern ? 52 : 20;
            drawSkinPart(graphics, leftArmX, leftArmY, 4, 12, x + 8, top + 16 - armSwing, 2);
            if (modern) drawSkinPart(graphics, 52, 52, 4, 12, x + 8, top + 16 - armSwing, 2);

            drawSkinPart(graphics, 4, 20, 4, 12, x - 8, top + 40, 2);
            if (modern) drawSkinPart(graphics, 4, 36, 4, 12, x - 8, top + 40, 2);

            int leftLegX = modern ? 20 : 4;
            int leftLegY = modern ? 52 : 20;
            drawSkinPart(graphics, leftLegX, leftLegY, 4, 12, x, top + 40, 2);
            if (modern) drawSkinPart(graphics, 4, 52, 4, 12, x, top + 40, 2);
        }

        if (ClientConfig.get().scanAnimation()) {
            int scanY = top + (int) ((now - openedAt) % 900L / 900.0 * 66.0);
            graphics.fill(x - 21, scanY, x + 21, scanY + 2, 0xAA63FFF1);
        }
    }

    private void drawSkinPart(
        GuiGraphicsExtractor graphics,
        int sourceX,
        int sourceY,
        int sourceWidth,
        int sourceHeight,
        int targetX,
        int targetY,
        int scale
    ) {
        for (int py = 0; py < sourceHeight; py++) {
            for (int px = 0; px < sourceWidth; px++) {
                int argb = result.argbAt(sourceX + px, sourceY + py);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha < 24) continue;
                graphics.fill(
                    targetX + px * scale,
                    targetY + py * scale,
                    targetX + (px + 1) * scale,
                    targetY + (py + 1) * scale,
                    argb
                );
            }
        }
    }

    private void drawAncientCity(GuiGraphicsExtractor g, int x, int y, int w, int h, long now) {
        int floor = y + h - 16;
        g.fillGradient(x, y, x + w, y + h, 0xFF03070B, 0xFF0B2631);
        g.fill(x, floor, x + w, y + h, 0xFF061018);
        for (int i = 0; i < 6; i++) {
            int towerX = x + 3 + i * Math.max(8, (w - 7) / 6);
            int towerH = 15 + (i % 3) * 9;
            g.fill(towerX, floor - towerH, towerX + 5, floor, 0xFF102733);
            g.fill(towerX + 1, floor - towerH + 3, towerX + 4, floor - towerH + 7, 0xFF22BDB8);
        }

        // Oyun içindeki Warden'ın geniş gövde, uzun kol, boynuz ve parlayan göğüs yapısına yakın piksel çizimi.
        int cx = x + w / 2;
        int bottom = floor - 2;
        int top = Math.max(y + 12, bottom - Math.min(72, h - 19));
        int bodyHalf = Math.max(10, Math.min(18, w / 6));
        int horn = Math.max(7, bodyHalf / 2);
        g.fill(cx - bodyHalf, top + 16, cx + bodyHalf, bottom, 0xFF0B171D);
        g.fill(cx - bodyHalf + 3, top + 7, cx + bodyHalf - 3, top + 23, 0xFF101C23);
        g.fill(cx - bodyHalf - horn, top + 5, cx - bodyHalf + 3, top + 10, 0xFF176071);
        g.fill(cx + bodyHalf - 3, top + 5, cx + bodyHalf + horn, top + 10, 0xFF176071);
        g.fill(cx - bodyHalf - 7, top + 21, cx - bodyHalf, bottom - 5, 0xFF091216);
        g.fill(cx + bodyHalf, top + 21, cx + bodyHalf + 7, bottom - 5, 0xFF091216);
        g.fill(cx - bodyHalf + 2, bottom - 3, cx - 3, bottom + 5, 0xFF071014);
        g.fill(cx + 3, bottom - 3, cx + bodyHalf - 2, bottom + 5, 0xFF071014);
        int glow = 145 + (int) ((Math.sin(now / 220.0) + 1.0) * 45.0);
        for (int rib = 0; rib < 3; rib++) {
            int ry = top + 25 + rib * 7;
            g.fill(cx - 8 - rib, ry, cx - 2, ry + 4, withAlpha(0xFF43E5DC, glow));
            g.fill(cx + 2, ry, cx + 8 + rib, ry + 4, withAlpha(0xFF43E5DC, glow));
        }
        g.fill(cx - 7, top + 12, cx - 3, top + 16, 0xFFE7FFFF);
        g.fill(cx + 3, top + 12, cx + 7, top + 16, 0xFFE7FFFF);
    }

    private void drawDragonStorm(GuiGraphicsExtractor g, int x, int y, int w, int h, long now) {
        g.fillGradient(x, y, x + w, y + h, 0xFF05010B, 0xFF411063);
        int drift = ClientConfig.get().animatedBackgrounds() ? (int) ((now / 34L) % Math.max(1, w + 60)) : 0;
        // Mor fırtına bulutları ve uzaktaki dağlar.
        for (int i = -1; i < 5; i++) {
            int cx = x + ((i * 47 + drift) % (w + 70)) - 35;
            int cy = y + 13 + (i & 1) * 18;
            g.fill(cx, cy, cx + 43, cy + 8, 0x493D175A);
            g.fill(cx + 9, cy - 6, cx + 31, cy + 10, 0x603F1B60);
        }
        int ground = y + h - 15;
        g.fill(x, ground, x + w, y + h, 0xFF09050F);
        for (int i = 0; i < 5; i++) {
            int mx = x + i * Math.max(12, w / 4) - 6;
            int mh = 12 + (i % 3) * 8;
            g.fill(mx, ground - mh, mx + Math.max(16, w / 3), ground, i % 2 == 0 ? 0xFF140A20 : 0xFF21102E);
        }

        int cx = x + w / 2;
        int cy = y + Math.max(37, h / 2 + 2);
        int flap = ClientConfig.get().menuAnimations() ? (int) Math.round(Math.sin(now / 210.0) * 4.0) : 0;
        int pulse = 150 + (int) ((Math.sin(now / 170.0) + 1.0) * 45.0);

        // Büyük ejderha gölgesi, boynuzlar, baş ve kuyruk.
        g.fill(cx - 7, cy - 23, cx + 7, cy + 20, 0xE90B0611);
        g.fill(cx - 13, cy - 25, cx - 4, cy - 18, 0xFF170721);
        g.fill(cx + 4, cy - 25, cx + 13, cy - 18, 0xFF170721);
        g.fill(cx - 4, cy + 16, cx + 4, cy + 34, 0xD713071D);

        // Katmanlı mor enerji kanatları.
        g.fill(cx - 38, cy - 18 + flap, cx - 7, cy - 10 + flap, 0xC14B1370);
        g.fill(cx + 7, cy - 18 + flap, cx + 38, cy - 10 + flap, 0xC14B1370);
        g.fill(cx - 32, cy - 9 + flap, cx - 7, cy + 3 + flap, 0xD16B1A98);
        g.fill(cx + 7, cy - 9 + flap, cx + 32, cy + 3 + flap, 0xD16B1A98);
        g.fill(cx - 24, cy + 2 + flap, cx - 7, cy + 16 + flap, 0xE68D37C1);
        g.fill(cx + 7, cy + 2 + flap, cx + 24, cy + 16 + flap, 0xE68D37C1);
        g.outline(cx - 38, cy - 18 + flap, 31, 34, withAlpha(0xFFDCA1FF, pulse));
        g.outline(cx + 7, cy - 18 + flap, 31, 34, withAlpha(0xFFDCA1FF, pulse));

        // Parlayan gözler ve göğüs çekirdeği.
        g.fill(cx - 6, cy - 18, cx - 2, cy - 14, 0xFFFFFFFF);
        g.fill(cx + 2, cy - 18, cx + 6, cy - 14, 0xFFFFFFFF);
        g.fill(cx - 3, cy - 4, cx + 3, cy + 4, withAlpha(0xFFE9B6FF, pulse));

        // İnce mor yıldırım çizgileri; foto-hassasiyet modunda sabit ve daha soluk.
        if (!ClientConfig.get().photosensitiveMode()) {
            int bolt = (int) ((now / 120L) % Math.max(1, w));
            g.fill(x + bolt, y + 6, x + bolt + 2, y + 21, 0x8FDFA6FF);
            g.fill(x + bolt - 5, y + 20, x + bolt + 2, y + 23, 0x7FC56CFF);
        }
    }

    private void drawLavaCave(GuiGraphicsExtractor g, int x, int y, int w, int h, long now) {
        g.fill(x, y, x + w, y + 15, 0xFF190604);
        for (int i = 0; i < 7; i++) {
            int px = x + i * Math.max(9, w / 7);
            int length = 12 + (i % 3) * 9;
            g.fill(px, y + 10, px + 7, y + 10 + length, 0xFF2C0A05);
        }
        int lavaY = y + h - 20;
        g.fillGradient(x, lavaY, x + w, y + h, 0xFFFF8A10, 0xFFB31808);
        int wave = (int) ((Math.sin(now / 170.0) + 1.0) * 3.0);
        for (int i = 0; i < 6; i++) {
            int bx = x + 8 + i * Math.max(12, (w - 16) / 6);
            g.fill(bx, lavaY - wave - (i % 2) * 4, bx + 5, lavaY + 2, 0xFFFFD24A);
        }

        // Görünür Cehennem Küresi simgesi.
        int orbit = (int) Math.round(Math.sin(now / 260.0) * Math.max(3, w / 16.0));
        int cx = x + w / 2 + orbit;
        int cy = y + Math.max(38, h / 2 - 5);
        g.fill(cx - 15, cy - 3, cx - 6, cy + 4, 0x88FF6A00);
        g.fill(cx - 10, cy - 8, cx - 2, cy + 9, 0xBBFF8B12);
        g.fill(cx - 7, cy - 11, cx + 8, cy + 11, 0xFFFF6A00);
        g.fill(cx - 3, cy - 7, cx + 9, cy + 7, 0xFFFFC52A);
        g.fill(cx, cy - 4, cx + 6, cy + 4, 0xFFFFFFB0);
    }

    private void drawMoon(GuiGraphicsExtractor g, int x, int y, int w, int h, long now) {
        g.fillGradient(x, y, x + w, y + h, 0xFF040817, 0xFF34456F);
        int horizon = y + h - 18;
        g.fill(x, horizon, x + w, y + h, 0xFF080B16);
        for (int i = 0; i < 8; i++) {
            int sx = x + 6 + (i * 29) % Math.max(12, w - 12);
            int sy = y + 7 + (i * 17) % Math.max(12, h - 34);
            int blink = 135 + (int) ((Math.sin(now / 260.0 + i) + 1.0) * 55.0);
            g.fill(sx, sy, sx + 2, sy + 2, withAlpha(0xFFFFFFFF, blink));
        }
        int cx = x + w / 2;
        int cy = y + Math.max(35, h / 2);
        int pulse = 160 + (int) ((Math.sin(now / 210.0) + 1.0) * 45.0);
        int radius = Math.max(15, Math.min(27, w / 5));
        g.fill(cx - radius, cy - radius, cx + radius, cy + radius, withAlpha(0xFFE7EDFF, pulse));
        g.fill(cx - radius / 3, cy - radius - 2, cx + radius + 4, cy + radius + 2, 0xFF10182D);
        g.outline(cx - radius, cy - radius, radius * 2, radius * 2, 0xFFDCE6FF);
        int orbit = ClientConfig.get().menuAnimations() ? (int) Math.round(Math.sin(now / 180.0) * 5.0) : 0;
        g.fill(cx - radius - 13 + orbit, cy - 2, cx - radius - 4 + orbit, cy + 3, 0xFFAFC6FF);
        g.fill(cx + radius + 4 - orbit, cy - 2, cx + radius + 13 - orbit, cy + 3, 0xFF9474D6);
        g.fill(cx - 2, horizon - 15, cx + 2, horizon, 0xFF66769F);
    }

    private void drawMagneticForge(GuiGraphicsExtractor g, int x, int y, int w, int h, long now) {
        g.fillGradient(x, y, x + w, y + h, 0xFF10161D, 0xFF52606B);
        int ground = y + h - 15;
        g.fill(x, ground, x + w, y + h, 0xFF161B20);
        int cx = x + w / 2;
        int cy = y + Math.max(32, h / 2);
        int pulse = 130 + (int) ((Math.sin(now / 180.0) + 1.0) * 52.0);
        // İki kutuplu gerçek metal çekirdek ve bakır sargılar.
        g.fill(cx - 20, cy - 9, cx - 4, cy + 9, 0xFFB7C2CB);
        g.fill(cx + 4, cy - 9, cx + 20, cy + 9, 0xFF707D88);
        g.fill(cx - 4, cy - 13, cx + 4, cy + 13, withAlpha(0xFFD47E3F, pulse));
        for (int i = 0; i < 4; i++) {
            int orbit = (int) Math.round(Math.sin(now / 210.0 + i * 1.57) * Math.max(7, w / 5.5));
            int oy = cy + (int) Math.round(Math.cos(now / 210.0 + i * 1.57) * 12.0);
            g.fill(cx + orbit - 3, oy - 3, cx + orbit + 3, oy + 3, i % 2 == 0 ? 0xFFC4D0D9 : 0xFFC6793C);
        }
        g.fill(x + 5, y + 8, x + 8, ground, 0x554ED6FF);
        g.fill(x + w - 8, y + 8, x + w - 5, ground, 0x55FF6E4A);
    }

    private void drawDesertTemple(GuiGraphicsExtractor g, int x, int y, int w, int h, long now) {
        g.fillGradient(x, y, x + w, y + h, 0xFF6C431B, 0xFFE0B55D);
        int ground = y + h - 14;
        g.fill(x, ground, x + w, y + h, 0xFFD6AA51);
        // Kumtaşı tapınak: Minecraft kum/kumtaşı bloklarını çağrıştıran kare gövdeler.
        int cx = x + w / 2;
        int templeW = Math.max(24, w / 2);
        int left = cx - templeW / 2;
        g.fill(left, ground - 28, left + templeW, ground, 0xFFE7CF8A);
        g.fill(left + 5, ground - 39, left + templeW - 5, ground - 28, 0xFFF1DB9B);
        g.fill(cx - 5, ground - 23, cx + 5, ground, 0xFF76502A);
        int drift = ClientConfig.get().animatedBackgrounds() ? (int) ((now / 55L) % Math.max(1, w + 22)) : 0;
        for (int i = 0; i < 7; i++) {
            int sx = x + ((i * 23 + drift) % (w + 18)) - 9;
            int sy = y + 12 + (i * 13) % Math.max(18, h - 34);
            g.fill(sx, sy, sx + 7, sy + 5, i % 2 == 0 ? 0xFFDDB96B : 0xFFEAD28C);
        }
        int wave = (int) Math.round(Math.sin(now / 190.0) * 3.0);
        g.fill(x + 4, ground - 7 + wave, x + w - 4, ground - 2 + wave, 0x99F3D27C);
    }

    private void drawAnomalyGlitch(GuiGraphicsExtractor g, int x, int y, int w, int h, long now) {
        g.fillGradient(x, y, x + w, y + h, 0xFF030108, 0xFF291248);
        int shift = (int) ((now / 85L) % Math.max(1, w));
        for (int i = 0; i < 14; i++) {
            int gy = y + 8 + (i * 17) % Math.max(18, h - 12);
            int gx = x + ((i * 31 + shift * (i % 3 + 1)) % Math.max(1, w + 24)) - 12;
            int length = 8 + (i % 4) * 9;
            int color = switch (i % 3) {
                case 0 -> 0xAA5CE5E5;
                case 1 -> 0xAAB65CFF;
                default -> 0x88E94B63;
            };
            g.fill(gx, gy, Math.min(x + w, gx + length), gy + 2, color);
        }
        int cx = x + w / 2;
        int cy = y + Math.max(30, h / 2);
        int jitter = (int) Math.round(Math.sin(now / 90.0) * 3.0);
        g.fill(cx - 18 + jitter, cy - 20, cx + 18 + jitter, cy + 20, 0x66000000);
        g.outline(cx - 18 - jitter, cy - 20, 36, 40, 0xFFB65CFF);
        g.outline(cx - 15 + jitter, cy - 17, 30, 34, 0xFF5CE5E5);
        g.text(font, "?", cx - font.width("?") / 2 + jitter, cy - 5, 0xFFFFFFFF, true);
        if ((now / 300L) % 3L == 0L) {
            String error = "404";
            g.text(font, error, cx - font.width(error) / 2 - jitter, cy + 11, 0xFFE94B63, false);
        }
    }

    private CardLayout layout() {
        if (cachedLayout != null && cachedLayoutWidth == width && cachedLayoutHeight == height) return cachedLayout;
        int count = CLASSES.length;
        boolean compact = width < 520 || height < 330;
        int gap = compact ? 3 : Math.max(4, Math.min(8, width / 125));
        int sideMargin = compact ? 5 : 14;
        int availableWidth = Math.max(count * 30, width - sideMargin * 2 - gap * (count - 1));
        int cardWidth = Math.max(30, Math.min(132, availableWidth / count));
        int cardY = compact ? 44 : 112;
        int maxHeight = Math.max(112, height - cardY - 20);
        int preferredHeight = compact ? Math.max(142, (int) (cardWidth * 2.15)) : Math.max(178, (int) (cardWidth * 1.62));
        int cardHeight = Math.min(maxHeight, preferredHeight);
        int total = cardWidth * count + gap * (count - 1);
        int startX = Math.max(4, (width - total) / 2);
        if (!compact) cardY = Math.max(112, height - cardHeight - 20);
        cachedLayoutWidth = width;
        cachedLayoutHeight = height;
        cachedLayout = new CardLayout(startX, cardY, cardWidth, cardHeight, gap, compact);
        return cachedLayout;
    }

    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String suffix = "...";
        int length = text.length();
        while (length > 0 && font.width(text.substring(0, length) + suffix) > maxWidth) length--;
        return text.substring(0, Math.max(0, length)) + suffix;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return selectedIndex >= 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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

    private record CardLayout(int startX, int cardY, int cardWidth, int cardHeight, int gap, boolean compact) {}
}
