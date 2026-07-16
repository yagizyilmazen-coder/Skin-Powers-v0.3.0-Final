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
    private static final String[] TITLES = {"WARDEN", "UÇUŞ", "ATEŞ", "SU"};
    private static final String[] SUBTITLES = {"Derinliğin gücü", "Gökyüzünün özgürlüğü", "Alevin hâkimiyeti", "Okyanusun kuvveti"};
    private static final PowerClass[] CLASSES = {PowerClass.WARDEN, PowerClass.FLIGHT, PowerClass.FIRE, PowerClass.WATER};
    private static final int[] TOP_COLORS = {0xFF07111C, 0xFF74BDE8, 0xFF5B0B08, 0xFF06354A};
    private static final int[] BOTTOM_COLORS = {0xFF16384B, 0xFFEAF8FF, 0xFFFF6B18, 0xFF18B8C8};
    private static final int[] ACCENTS = {0xFF35D7D0, 0xFFFFFFFF, 0xFFFFC22E, 0xFF63FFF1};

    private final long openedAt = Util.getMillis();
    private SkinAnalyzer.Result result = SkinAnalyzer.Result.unavailable();
    private boolean analysisFinished;
    private int selectedIndex = -1;
    private long selectedAt;
    private Button[] selectButtons = new Button[4];

    public SkinSelectionScreen() {
        super(Component.translatable("screen.skinpowers.title"));
    }

    @Override
    protected void init() {
        CardLayout layout = layout();
        for (int i = 0; i < CLASSES.length; i++) {
            final int index = i;
            int buttonWidth = Math.max(48, layout.cardWidth() - (layout.compact() ? 8 : 24));
            int x = layout.startX() + i * (layout.cardWidth() + layout.gap()) + (layout.cardWidth() - buttonWidth) / 2;
            int buttonHeight = layout.compact() ? 18 : 20;
            int y = layout.cardY() + layout.cardHeight() - buttonHeight - 7;
            selectButtons[i] = Button.builder(Component.translatable("screen.skinpowers.select"), button -> select(index))
                .bounds(x, y, buttonWidth, buttonHeight)
                .build();
            addRenderableWidget(selectButtons[i]);
        }

        int skipWidth = width < 520 ? 70 : 108;
        int skipHeight = width < 520 ? 18 : 20;
        addRenderableWidget(Button.builder(Component.translatable("screen.skinpowers.skip"), button -> analysisFinished = true)
            .bounds(Math.max(6, width - skipWidth - 7), 7, skipWidth, skipHeight)
            .build());

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
        if (selectedIndex >= 0) return;
        selectedIndex = index;
        selectedAt = Util.getMillis();
        ClientPlayNetworking.send(new ClientCommandPayload("CHOOSE:" + CLASSES[index].name()));
    }

    @Override
    public void tick() {
        super.tick();
        if (selectedIndex >= 0 && Util.getMillis() - selectedAt > 850L && minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, width, height, 0xFF03060B, 0xFF101622);

        long now = Util.getMillis();
        float totalProgress = clamp01((now - openedAt) / 900.0F);
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
            scanText = "Skin tarandı — renk puanları gerçek piksellerden hesaplandı";
        } else {
            scanText = "Skin tarandı — belirgin bir sınıf rengi bulunamadı";
        }
        scanText = fit(scanText, Math.max(100, width - 20));
        graphics.text(font, scanText, (width - font.width(scanText)) / 2, layout.compact() ? 26 : 28, analysisFinished ? 0xFF9BEFD9 : 0xFFE7F4FF, false);

        if (!layout.compact()) drawAvatar(graphics, now);

        for (int i = 0; i < CLASSES.length; i++) {
            float cardProgress = smoothStep(clamp01((totalProgress * 1.4F) - i * 0.15F));
            int baseX = layout.startX() + i * (layout.cardWidth() + layout.gap());
            int shake = 0;
            if (selectedIndex == i) {
                long elapsed = now - selectedAt;
                if (elapsed < 420L) shake = (int) Math.round(Math.sin(elapsed * 0.085) * (1.0 - elapsed / 420.0) * 7.0);
            }
            int y = layout.cardY() + (int) ((1.0F - cardProgress) * 35.0F);
            drawCard(graphics, i, baseX + shake, y, layout.cardWidth(), layout.cardHeight(), cardProgress, now);
        }

        if (!analysisFinished) {
            int scanX = (int) ((now - openedAt) % 1400L / 1400.0 * width);
            graphics.fill(scanX - 1, 42, scanX + 2, height - 12, 0x5535D7D0);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void drawCard(GuiGraphicsExtractor graphics, int index, int x, int y, int w, int h, float progress, long now) {
        int alpha = (int) (255 * progress);
        int top = withAlpha(TOP_COLORS[index], alpha);
        int bottom = withAlpha(BOTTOM_COLORS[index], alpha);
        graphics.fillGradient(x, y, x + w, y + h, top, bottom);

        boolean recommended = analysisFinished && result.bestIndex() == index;
        float pulse = (float) ((Math.sin(now / 260.0) + 1.0) * 0.5);
        int outlineAlpha = recommended ? 180 + (int) (75 * pulse) : 110;
        graphics.outline(x, y, w, h, withAlpha(ACCENTS[index], outlineAlpha));
        if (recommended) {
            graphics.outline(x - 2, y - 2, w + 4, h + 4, withAlpha(ACCENTS[index], 80 + (int) (80 * pulse)));
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
            case 3 -> drawOcean(graphics, x, y, w, artBottom - y, now);
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
        if (recommended) {
            String recommendedText = "ÖNERİLEN";
            graphics.fill(x + 8, y + 8, x + 8 + font.width(recommendedText) + 8, y + 21, withAlpha(ACCENTS[index], 155));
            graphics.text(font, recommendedText, x + 12, y + 10, index == 1 ? 0xFF183348 : 0xFFFFFFFF, true);
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

        int scanY = top + (int) ((now - openedAt) % 900L / 900.0 * 66.0);
        graphics.fill(x - 21, scanY, x + 21, scanY + 2, 0xAA63FFF1);
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
        int floor = y + h - 18;
        g.fill(x, floor, x + w, y + h, 0xFF071018);
        for (int i = 0; i < 5; i++) {
            int towerX = x + 5 + i * Math.max(10, (w - 10) / 5);
            int towerH = 19 + (i % 3) * 10;
            g.fill(towerX, floor - towerH, towerX + 6, floor, 0xFF102733);
            g.fill(towerX + 1, floor - towerH + 4, towerX + 5, floor - towerH + 8, 0xFF28BFB8);
        }

        // Kartta sınıfı açıkça anlatan Warden silüeti.
        int cx = x + w / 2;
        int bodyBottom = floor - 3;
        int bodyTop = Math.max(y + 22, bodyBottom - Math.min(63, h - 28));
        int bodyHalf = Math.max(9, Math.min(17, w / 7));
        g.fill(cx - bodyHalf, bodyTop + 15, cx + bodyHalf, bodyBottom, 0xFF101C25);
        g.fill(cx - bodyHalf - 7, bodyTop + 19, cx - bodyHalf, bodyBottom - 8, 0xFF0A141C);
        g.fill(cx + bodyHalf, bodyTop + 19, cx + bodyHalf + 7, bodyBottom - 8, 0xFF0A141C);
        g.fill(cx - bodyHalf + 2, bodyTop, cx + bodyHalf - 2, bodyTop + 19, 0xFF111A22);
        g.fill(cx - bodyHalf - 9, bodyTop + 3, cx - bodyHalf + 2, bodyTop + 8, 0xFF173847);
        g.fill(cx + bodyHalf - 2, bodyTop + 3, cx + bodyHalf + 9, bodyTop + 8, 0xFF173847);
        int glow = 135 + (int) ((Math.sin(now / 250.0) + 1) * 48);
        g.fill(cx - 6, bodyTop + 22, cx - 2, bodyTop + 35, withAlpha(0xFF39E2D6, glow));
        g.fill(cx + 2, bodyTop + 22, cx + 6, bodyTop + 35, withAlpha(0xFF39E2D6, glow));
        g.fill(cx - 8, bodyTop + 7, cx - 4, bodyTop + 11, 0xFFEFFFFF);
        g.fill(cx + 4, bodyTop + 7, cx + 8, bodyTop + 11, 0xFFEFFFFF);
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

    private void drawOcean(GuiGraphicsExtractor g, int x, int y, int w, int h, long now) {
        g.fillGradient(x, y, x + w, y + h, 0xFF073E59, 0xFF0FB7C5);
        int seaY = y + h - 20;
        g.fill(x, seaY, x + w, y + h, 0xFF075A79);
        int motion = (int) ((now / 55L) % Math.max(1, w + 35));
        for (int i = -1; i < 5; i++) {
            int waveX = x + ((i * 39 + motion) % (w + 35)) - 18;
            int waveY = seaY - 8 - (i & 1) * 5;
            g.fill(waveX, waveY, waveX + 30, waveY + 6, 0xAA83FFF5);
            g.fill(waveX + 8, waveY - 5, waveX + 22, waveY + 7, 0x88D8FFFF);
        }

        // Büyük Tsunami: yüksek ve geniş, ileri kıvrılan su duvarı.
        int crestX = x + w / 2 - 7;
        int crestY = y + Math.max(24, h / 4);
        int waveWidth = Math.max(34, Math.min(70, w - 18));
        int waveHeight = Math.max(45, Math.min(82, h - 30));
        g.fill(crestX - waveWidth / 2, crestY + waveHeight / 3, crestX + waveWidth / 2, crestY + waveHeight, 0xB91193B2);
        g.fill(crestX - waveWidth / 2, crestY + waveHeight / 5, crestX + waveWidth / 5, crestY + waveHeight / 2, 0xD825C9D2);
        g.fill(crestX - waveWidth / 2 + 7, crestY + 4, crestX + 8, crestY + waveHeight / 3, 0xE253E8E0);
        g.fill(crestX - waveWidth / 2 + 13, crestY, crestX + 18, crestY + 8, 0xEEEAFFFF);
        g.fill(crestX + 7, crestY + 8, crestX + 23, crestY + 15, 0xD7D7FFFF);
    }

    private CardLayout layout() {
        int count = CLASSES.length;
        boolean compact = width < 520 || height < 330;
        int gap = compact ? 4 : Math.max(5, Math.min(10, width / 105));
        int sideMargin = compact ? 8 : 20;
        int availableWidth = Math.max(200, width - sideMargin - gap * (count - 1));
        int cardWidth = Math.max(48, Math.min(145, availableWidth / count));
        int cardY = compact ? 44 : 112;
        int maxHeight = Math.max(118, height - cardY - 8);
        int preferredHeight = compact ? Math.max(142, (int) (cardWidth * 2.15)) : Math.max(178, (int) (cardWidth * 1.62));
        int cardHeight = Math.min(maxHeight, preferredHeight);
        int total = cardWidth * count + gap * (count - 1);
        int startX = (width - total) / 2;
        if (!compact) cardY = Math.max(112, height - cardHeight - 8);
        return new CardLayout(startX, cardY, cardWidth, cardHeight, gap, compact);
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
