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
    private static final String[] TITLES = {"WARDEN", "UÇUŞ", "ATEŞ"};
    private static final String[] SUBTITLES = {"Derinliğin gücü", "Gökyüzünün özgürlüğü", "Alevin hâkimiyeti"};
    private static final PowerClass[] CLASSES = {PowerClass.WARDEN, PowerClass.FLIGHT, PowerClass.FIRE};
    private static final int[] TOP_COLORS = {0xFF07111C, 0xFF74BDE8, 0xFF5B0B08};
    private static final int[] BOTTOM_COLORS = {0xFF16384B, 0xFFEAF8FF, 0xFFFF6B18};
    private static final int[] ACCENTS = {0xFF35D7D0, 0xFFFFFFFF, 0xFFFFC22E};

    private final long openedAt = Util.getMillis();
    private SkinAnalyzer.Result result = SkinAnalyzer.Result.fallback();
    private boolean analysisFinished;
    private int selectedIndex = -1;
    private long selectedAt;
    private Button[] selectButtons = new Button[3];

    public SkinSelectionScreen() {
        super(Component.translatable("screen.skinpowers.title"));
    }

    @Override
    protected void init() {
        CardLayout layout = layout();
        for (int i = 0; i < 3; i++) {
            final int index = i;
            int buttonWidth = Math.max(54, layout.cardWidth() - 24);
            int x = layout.startX() + i * (layout.cardWidth() + layout.gap()) + (layout.cardWidth() - buttonWidth) / 2;
            int y = layout.cardY() + layout.cardHeight() - 31;
            selectButtons[i] = Button.builder(Component.translatable("screen.skinpowers.select"), button -> select(index))
                .bounds(x, y, buttonWidth, 20)
                .build();
            addRenderableWidget(selectButtons[i]);
        }

        addRenderableWidget(Button.builder(Component.translatable("screen.skinpowers.skip"), button -> analysisFinished = true)
            .bounds(Math.max(8, width - 118), 10, 108, 20)
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
        int titleWidth = font.width(title);
        graphics.text(font, title, (width - titleWidth) / 2, 12, 0xFFFFFFFF, true);

        String scanText = analysisFinished
            ? (result.fromSkin() ? "Skin taraması tamamlandı" : "Skin alınamadı — dengeli öneri kullanılıyor")
            : Component.translatable("screen.skinpowers.scan").getString();
        graphics.text(font, scanText, (width - font.width(scanText)) / 2, 28, analysisFinished ? 0xFF9BEFD9 : 0xFFE7F4FF, false);

        drawAvatar(graphics, now);

        CardLayout layout = layout();
        for (int i = 0; i < 3; i++) {
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

        int artBottom = y + Math.max(82, h - 78);
        graphics.enableScissor(x + 2, y + 2, x + w - 2, artBottom);
        switch (index) {
            case 0 -> drawAncientCity(graphics, x, y, w, artBottom - y, now);
            case 1 -> drawClouds(graphics, x, y, w, artBottom - y, now);
            case 2 -> drawLavaCave(graphics, x, y, w, artBottom - y, now);
            default -> { }
        }
        graphics.disableScissor();

        int score = (int) Math.round(result.score(index) * 100.0);
        graphics.text(font, TITLES[index], x + (w - font.width(TITLES[index])) / 2, artBottom + 7, 0xFFFFFFFF, true);
        graphics.text(font, SUBTITLES[index], x + (w - font.width(SUBTITLES[index])) / 2, artBottom + 20, 0xFFE8EEF4, false);
        String scoreText = analysisFinished ? "Skin uyumu: %" + score : "Taranıyor...";
        graphics.text(font, scoreText, x + (w - font.width(scoreText)) / 2, artBottom + 34, recommended ? ACCENTS[index] : 0xFFC8D0D8, false);
        if (recommended) {
            String recommendedText = "ÖNERİLEN";
            graphics.fill(x + 8, y + 8, x + 8 + font.width(recommendedText) + 8, y + 21, withAlpha(ACCENTS[index], 155));
            graphics.text(font, recommendedText, x + 12, y + 10, index == 1 ? 0xFF183348 : 0xFFFFFFFF, true);
        }
    }

    private void drawAvatar(GuiGraphicsExtractor graphics, long now) {
        int centerX = width / 2;
        int top = 43;
        int[] colors = result.dominantColors();
        int primary = 0xFF000000 | colors[0];
        int secondary = 0xFF000000 | colors[1];
        int accent = 0xFF000000 | colors[2];
        int sway = (int) Math.round(Math.sin(now / 520.0) * 5.0);
        int x = centerX + sway;

        graphics.fill(x - 10, top, x + 10, top + 18, primary);
        graphics.outline(x - 10, top, 20, 18, 0xAAFFFFFF);
        graphics.fill(x - 11, top + 19, x + 11, top + 48, secondary);
        graphics.fill(x - 18, top + 20, x - 12, top + 47, primary);
        graphics.fill(x + 12, top + 20, x + 18, top + 47, accent);
        graphics.fill(x - 10, top + 49, x - 2, top + 73, primary);
        graphics.fill(x + 2, top + 49, x + 10, top + 73, secondary);

        int scanY = top + (int) ((now - openedAt) % 900L / 900.0 * 74.0);
        graphics.fill(x - 22, scanY, x + 22, scanY + 2, 0xAA63FFF1);
    }

    private void drawAncientCity(GuiGraphicsExtractor g, int x, int y, int w, int h, long now) {
        int floor = y + h - 18;
        g.fill(x, floor, x + w, y + h, 0xFF071018);
        for (int i = 0; i < 5; i++) {
            int towerX = x + 8 + i * Math.max(12, (w - 16) / 5);
            int towerH = 24 + (i % 3) * 13;
            g.fill(towerX, floor - towerH, towerX + 8, floor, 0xFF102733);
            g.fill(towerX + 2, floor - towerH + 5, towerX + 6, floor - towerH + 10, 0xFF28BFB8);
        }
        int glow = 100 + (int) ((Math.sin(now / 280.0) + 1) * 45);
        g.fill(x + w / 2 - 3, floor - 34, x + w / 2 + 3, floor - 15, withAlpha(0xFF39E2D6, glow));
    }

    private void drawClouds(GuiGraphicsExtractor g, int x, int y, int w, int h, long now) {
        int drift = (int) ((now / 45L) % Math.max(1, w));
        for (int i = -1; i < 4; i++) {
            int cx = x + ((i * 43 + drift) % (w + 50)) - 25;
            int cy = y + 25 + (i & 1) * 28;
            g.fill(cx, cy, cx + 38, cy + 10, 0xCCFFFFFF);
            g.fill(cx + 9, cy - 7, cx + 27, cy + 12, 0xDDFFFFFF);
        }
        g.fill(x + w / 2 - 23, y + h - 35, x + w / 2 + 23, y + h - 27, 0xFF7896A8);
        g.fill(x + w / 2 - 15, y + h - 43, x + w / 2 + 15, y + h - 34, 0xFFA9D482);
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
    }

    private CardLayout layout() {
        int gap = Math.max(7, Math.min(14, width / 70));
        int availableWidth = Math.max(180, width - 28 - gap * 2);
        int maxHeight = Math.max(170, height - 128);
        int cardWidth = Math.min(170, availableWidth / 3);
        int cardHeight = Math.min(maxHeight, Math.max(205, (int) (cardWidth * 16.0 / 9.0)));
        if (cardHeight > maxHeight) {
            cardHeight = maxHeight;
            cardWidth = Math.max(58, (int) (cardHeight * 9.0 / 16.0));
        }
        int total = cardWidth * 3 + gap * 2;
        int startX = (width - total) / 2;
        int cardY = Math.max(122, height - cardHeight - 10);
        return new CardLayout(startX, cardY, cardWidth, cardHeight, gap);
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

    private record CardLayout(int startX, int cardY, int cardWidth, int cardHeight, int gap) {}
}
