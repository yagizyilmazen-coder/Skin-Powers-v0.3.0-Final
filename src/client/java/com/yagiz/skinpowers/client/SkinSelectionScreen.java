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
    private static final String[] TITLES = {"WARDEN", "UÇUŞ", "ATEŞ", "DOĞA", "ZAMAN"};
    private static final String[] SUBTITLES = {"Derinliğin gücü", "Gökyüzünün özgürlüğü", "Alevin hâkimiyeti", "Ormanın yaşamı", "Anların hâkimiyeti"};
    private static final PowerClass[] CLASSES = {PowerClass.WARDEN, PowerClass.FLIGHT, PowerClass.FIRE, PowerClass.NATURE, PowerClass.TIME};
    private static final int[] TOP_COLORS = {0xFF07111C, 0xFF74BDE8, 0xFF5B0B08, 0xFF102B13, 0xFF09142C};
    private static final int[] BOTTOM_COLORS = {0xFF16384B, 0xFFEAF8FF, 0xFFFF6B18, 0xFF4C8B3C, 0xFFD6AF42};
    private static final int[] ACCENTS = {0xFF35D7D0, 0xFFFFFFFF, 0xFFFFC22E, 0xFF74E36D, 0xFFFFD86A};

    private final long openedAt = Util.getMillis();
    private SkinAnalyzer.Result result = SkinAnalyzer.Result.unavailable();
    private boolean analysisFinished;
    private int selectedIndex = -1;
    private long selectedAt;
    private final Button[] selectButtons = new Button[5];
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
            SkinAnalyzer.analyzeAsync(minecraft.player.getGameProfile()).thenAccept(analyzed ->
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
                // Böylece ikinci öneri Zaman olsa bile tıklanabilir kalır.
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
            case 1 -> drawClouds(graphics, x, y, w, artBottom - y, now);
            case 2 -> drawLavaCave(graphics, x, y, w, artBottom - y, now);
            case 3 -> drawForest(graphics, x, y, w, artBottom - y, now);
            case 4 -> drawTimeTemple(graphics, x, y, w, artBottom - y, now);
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
            graphics.text(font, recommendedText, x + 9, y + 8, index == 1 || index == 4 ? 0xFF183348 : 0xFFFFFFFF, true);
        }
    }

    private void drawAvatar(GuiGraphicsExtractor graphics, long now) {
        int centerX = width / 2;
        int top = 43;
        int sway = (int) Math.round(Math.sin(now / 520.0) * 2.0);
        int x = centerX + sway;

        graphics.fill(x - 22, top + 64, x + 22, top + 68, 0x44000000);
        if (!result.hasSkinImage()) {
            graphics.fill(x - 8, top, x + 8, top + 16, 0xFF34404D);
            graphics.outline(x - 8, top, 16, 16, 0xAAFFFFFF);
            graphics.fill(x - 8, top + 17, x + 8, top + 41, 0xFF516270);
            graphics.fill(x - 16, top + 18, x - 9, top + 41, 0xFF3B4854);
            graphics.fill(x + 9, top + 18, x + 16, top + 41, 0xFF3B4854);
            graphics.fill(x - 8, top + 42, x - 1, top + 66, 0xFF28333D);
            graphics.fill(x + 1, top + 42, x + 8, top + 66, 0xFF28333D);
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

    private void drawClouds(GuiGraphicsExtractor g, int x, int y, int w, int h, long now) {
        int drift = (int) ((now / 45L) % Math.max(1, w));
        for (int i = -1; i < 4; i++) {
            int cx = x + ((i * 43 + drift) % (w + 50)) - 25;
            int cy = y + 20 + (i & 1) * 27;
            g.fill(cx, cy, cx + 38, cy + 9, 0xBFFFFFFF);
            g.fill(cx + 9, cy - 7, cx + 27, cy + 11, 0xD8FFFFFF);
        }

        // Kartın merkezinde belirgin Elytra kanatları.
        int cx = x + w / 2;
        int cy = y + h / 2 + 7;
        int flap = (int) Math.round(Math.sin(now / 230.0) * 3.0);
        g.fill(cx - 4, cy - 20, cx + 4, cy + 20, 0xFF3E596A);
        g.fill(cx - 29, cy - 20 + flap, cx - 5, cy - 12 + flap, 0xFF6E8594);
        g.fill(cx + 5, cy - 20 + flap, cx + 29, cy - 12 + flap, 0xFF6E8594);
        g.fill(cx - 25, cy - 11 + flap, cx - 5, cy + 1 + flap, 0xFF8CA2AE);
        g.fill(cx + 5, cy - 11 + flap, cx + 25, cy + 1 + flap, 0xFF8CA2AE);
        g.fill(cx - 19, cy, cx - 5, cy + 14, 0xFFB9CBD4);
        g.fill(cx + 5, cy, cx + 19, cy + 14, 0xFFB9CBD4);
        g.outline(cx - 29, cy - 20 + flap, 24, 34, 0xDFFFFFFF);
        g.outline(cx + 5, cy - 20 + flap, 24, 34, 0xDFFFFFFF);
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

    private void drawForest(GuiGraphicsExtractor g, int x, int y, int w, int h, long now) {
        g.fillGradient(x, y, x + w, y + h, 0xFF132A15, 0xFF477A35);
        int ground = y + h - 18;
        g.fill(x, ground, x + w, y + h, 0xFF3B2A19);
        for (int i = 0; i < 5; i++) {
            int tx = x + 8 + i * Math.max(13, (w - 16) / 5);
            int th = 26 + (i % 3) * 8;
            g.fill(tx, ground - th, tx + 6, ground, 0xFF6A4425);
            g.fill(tx - 8, ground - th - 10, tx + 14, ground - th + 6, 0xFF2F7335);
            g.fill(tx - 4, ground - th - 17, tx + 10, ground - th - 3, 0xFF4B9848);
        }
        int cx = x + w / 2;
        int cy = y + Math.max(35, h / 2);
        int bob = (int) Math.round(Math.sin(now / 240.0) * 3.0);
        // Dikenli Tohum görseli: havada net görünen çekirdek ve dikenler.
        g.fill(cx - 8, cy - 8 + bob, cx + 8, cy + 8 + bob, 0xFF6F4C26);
        g.fill(cx - 5, cy - 5 + bob, cx + 5, cy + 5 + bob, 0xFF8BC34A);
        g.fill(cx - 14, cy - 2 + bob, cx - 7, cy + 2 + bob, 0xFFB5E56A);
        g.fill(cx + 7, cy - 2 + bob, cx + 14, cy + 2 + bob, 0xFFB5E56A);
        g.fill(cx - 2, cy - 14 + bob, cx + 2, cy - 7 + bob, 0xFFB5E56A);
        g.fill(cx - 2, cy + 7 + bob, cx + 2, cy + 14 + bob, 0xFFB5E56A);
    }

    private void drawTimeTemple(GuiGraphicsExtractor g, int x, int y, int w, int h, long now) {
        g.fillGradient(x, y, x + w, y + h, 0xFF071127, 0xFFB78C2E);
        int floor = y + h - 16;
        g.fill(x, floor, x + w, y + h, 0xFF111A35);
        int cx = x + w / 2;
        int cy = y + Math.max(32, h / 2);
        int radius = Math.max(13, Math.min(27, w / 4));
        for (int i = 0; i < 32; i++) {
            double angle = Math.PI * 2.0 * i / 32.0;
            int px = cx + (int) Math.round(Math.cos(angle) * radius);
            int py = cy + (int) Math.round(Math.sin(angle) * radius);
            g.fill(px - 1, py - 1, px + 2, py + 2, i % 4 == 0 ? 0xFFFFE28A : 0xFF5ED9E5);
        }
        double hand = now / 480.0;
        int hx = cx + (int) Math.round(Math.cos(hand) * (radius - 4));
        int hy = cy + (int) Math.round(Math.sin(hand) * (radius - 4));
        int steps = Math.max(1, radius);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int px = (int) Math.round(cx + (hx - cx) * t);
            int py = (int) Math.round(cy + (hy - cy) * t);
            g.fill(px, py, px + 2, py + 2, 0xFFFFD861);
        }
        int spearBob = (int) Math.round(Math.sin(now / 230.0) * 3.0);
        g.fill(cx - 3, cy - radius - 15 + spearBob, cx + 3, cy - radius + 10 + spearBob, 0xFFFFD861);
        g.fill(cx - 7, cy - radius - 14 + spearBob, cx + 7, cy - radius - 8 + spearBob, 0xFF4FD6E3);
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
