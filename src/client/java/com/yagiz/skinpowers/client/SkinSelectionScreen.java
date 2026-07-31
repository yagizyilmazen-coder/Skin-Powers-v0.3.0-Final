package com.yagiz.skinpowers.client;

import com.yagiz.skinpowers.PowerClass;
import com.yagiz.skinpowers.network.ClientCommandPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Sınıf seçim ekranı (vitrin düzeni):
 * - Ortada skin'in 1. ve 2. önerisi büyük kartlar (yalnızca bunlar seçilebilir)
 * - Altta kalan 5 sınıf küçük ve kilitli
 * - Skin okunamazsa yine 2 sınıf büyük/seçilebilir; diğerleri kilitli (rastgele sabit çift)
 */
public final class SkinSelectionScreen extends Screen {
    private static final String[] TITLES = {"WARDEN", "KADİM EJDERHA", "ATEŞ", "AY", "ANOMALİ", "MANYETİK", "KUM"};
    private static final String[] SUBTITLES = {
        "Derinliğin gücü", "Mor kıyametin kanatları", "Alevin hâkimiyeti",
        "Tutulmanın hükmü", "Gerçekliğin hatası", "Metalin kutupları", "Çölün şekillenen gücü"
    };
    private static final PowerClass[] CLASSES = {
        PowerClass.WARDEN, PowerClass.FLIGHT, PowerClass.FIRE, PowerClass.MOON,
        PowerClass.ANOMALY, PowerClass.MAGNETIC, PowerClass.SAND
    };
    private static final int[] TOP_COLORS = {0xFF07111C, 0xFF08020F, 0xFF5B0B08, 0xFF060A1C, 0xFF05010B, 0xFF121820, 0xFF4C2F13};
    private static final int[] BOTTOM_COLORS = {0xFF16384B, 0xFF451070, 0xFFFF6B18, 0xFF596B9E, 0xFF291248, 0xFF586875, 0xFFD2A34D};
    private static final int[] ACCENTS = {0xFF35D7D0, 0xFFCE72FF, 0xFFFFC22E, 0xFFD9E4FF, 0xFFB65CFF, 0xFFC5D2DE, 0xFFFFD273};

    private final long openedAt = Util.getMillis();
    private SkinAnalyzer.Result result = SkinAnalyzer.Result.unavailable();
    private boolean analysisFinished;
    private int selectedIndex = -1;
    private long selectedAt;

    /** Seçilebilir iki sınıf (skin önerisi veya yedek çift). */
    private int primaryIndex = 0;
    private int secondaryIndex = 1;
    private boolean choicesResolved;

    private Button primaryButton;
    private Button secondaryButton;
    private ScreenLayout cachedLayout;
    private int cachedLayoutWidth = -1;
    private int cachedLayoutHeight = -1;

    public SkinSelectionScreen() {
        super(Component.translatable("screen.skinpowers.title"));
    }

    @Override
    protected void init() {
        cachedLayout = null;
        ScreenLayout layout = layout();

        primaryButton = Button.builder(Component.literal("SEÇ"), button -> select(primaryIndex))
            .bounds(0, 0, 80, 20)
            .build();
        secondaryButton = Button.builder(Component.literal("SEÇ"), button -> select(secondaryIndex))
            .bounds(0, 0, 80, 20)
            .build();
        primaryButton.active = false;
        secondaryButton.active = false;
        addRenderableWidget(primaryButton);
        addRenderableWidget(secondaryButton);
        positionChoiceButtons(layout);

        if (minecraft != null && minecraft.player != null) {
            SkinAnalyzer.analyzeAsync(minecraft.player.getGameProfile(), true).thenAccept(analyzed ->
                minecraft.execute(() -> {
                    result = analyzed;
                    analysisFinished = true;
                    resolveChoices();
                    positionChoiceButtons(layout());
                })
            );
        } else {
            analysisFinished = true;
            resolveChoices();
        }
    }

    private void resolveChoices() {
        if (result.hasRecommendation()) {
            int best = result.bestIndex();
            int second = result.secondIndex();
            if (best < 0) best = 0;
            if (second < 0 || second == best) {
                second = (best + 1) % CLASSES.length;
            }
            primaryIndex = best;
            secondaryIndex = second;
        } else {
            // Skin yok: bu oturum için sabit, rastgele görünen çift
            int a = (int) Math.floorMod(openedAt, CLASSES.length);
            int b = (int) Math.floorMod(openedAt / 11L, CLASSES.length - 1);
            if (b >= a) b++;
            primaryIndex = a;
            secondaryIndex = b;
        }
        choicesResolved = true;
    }

    private void select(int index) {
        if (selectedIndex >= 0 || !isSelectable(index)) return;
        selectedIndex = index;
        selectedAt = Util.getMillis();
        ClientPlayNetworking.send(new ClientCommandPayload("CHOOSE:" + CLASSES[index].name()));
    }

    private boolean isSelectable(int index) {
        if (!analysisFinished || !choicesResolved || index < 0 || index >= CLASSES.length) return false;
        return index == primaryIndex || index == secondaryIndex;
    }

    private void positionChoiceButtons(ScreenLayout layout) {
        if (primaryButton == null || secondaryButton == null) return;
        int btnH = layout.compact() ? 18 : 20;
        int btnW = Math.max(56, Math.min(100, layout.bigWidth() - 24));
        primaryButton.setWidth(btnW);
        primaryButton.setHeight(btnH);
        secondaryButton.setWidth(btnW);
        secondaryButton.setHeight(btnH);
        primaryButton.setX(layout.bigLeftX() + (layout.bigWidth() - btnW) / 2);
        primaryButton.setY(layout.bigY() + layout.bigHeight() - btnH - 8);
        secondaryButton.setX(layout.bigRightX() + (layout.bigWidth() - btnW) / 2);
        secondaryButton.setY(layout.bigY() + layout.bigHeight() - btnH - 8);
    }

    @Override
    public void tick() {
        super.tick();
        if (selectedIndex < 0 || minecraft == null) return;
        if (ClientState.powerClass() == CLASSES[selectedIndex]) {
            if (Util.getMillis() - selectedAt > 250L) minecraft.setScreen(null);
            return;
        }
        if (Util.getMillis() - selectedAt > 3000L) selectedIndex = -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, width, height, 0xFF03060B, 0xFF101622);

        long now = Util.getMillis();
        ClientConfig config = ClientConfig.get();
        float animationFactor = config.menuAnimations() ? config.cardAnimationSpeedPercent() / 100.0F : 8.0F;
        float totalProgress = clamp01((now - openedAt) / (900.0F / animationFactor));
        ScreenLayout layout = layout();
        positionChoiceButtons(layout);

        // Başlık
        String titleText = fit(title.getString(), Math.max(80, width - 40));
        graphics.text(font, titleText, (width - font.width(titleText)) / 2, layout.compact() ? 8 : 10, 0xFFFFFFFF, true);

        // Durum satırı
        String scanText;
        if (!analysisFinished) {
            scanText = Component.translatable("screen.skinpowers.scan").getString();
        } else if (result.hasRecommendation()) {
            scanText = "Skin tarandı — yalnızca 1. ve 2. öneri seçilebilir";
        } else {
            scanText = "Skin alınamadı — iki rastgele sınıf açıldı, diğerleri kilitli";
        }
        scanText = fit(scanText, Math.max(100, width - 20));
        graphics.text(font, scanText, (width - font.width(scanText)) / 2, layout.compact() ? 20 : 24, analysisFinished ? 0xFF9BEFD9 : 0xFFE7F4FF, false);

        // Küçük skin avatar (sol üst, compact değilse)
        if (!layout.compact() && !config.performanceMode()) {
            drawAvatarCorner(graphics, now, 14, 38);
        }

        // İki büyük kart
        float bigProgress = ClientUiRules.staggeredProgress(totalProgress, 0, 2, 0.18F);
        float bigProgress2 = ClientUiRules.staggeredProgress(totalProgress, 1, 2, 0.18F);
        if (choicesResolved) {
            int shake1 = shakeOffset(primaryIndex, now);
            int shake2 = shakeOffset(secondaryIndex, now);
            int y1 = layout.bigY() + (int) ((1.0F - bigProgress) * 28.0F);
            int y2 = layout.bigY() + (int) ((1.0F - bigProgress2) * 28.0F);
            drawBigCard(graphics, primaryIndex, 1, layout.bigLeftX() + shake1, y1, layout.bigWidth(), layout.bigHeight(), bigProgress, now);
            drawBigCard(graphics, secondaryIndex, 2, layout.bigRightX() + shake2, y2, layout.bigWidth(), layout.bigHeight(), bigProgress2, now);
        } else {
            // Analiz bitene kadar yer tutucu
            drawPlaceholderBig(graphics, layout.bigLeftX(), layout.bigY(), layout.bigWidth(), layout.bigHeight());
            drawPlaceholderBig(graphics, layout.bigRightX(), layout.bigY(), layout.bigWidth(), layout.bigHeight());
        }

        // Butonlar
        if (primaryButton != null) {
            primaryButton.setMessage(Component.literal(analysisFinished && choicesResolved ? "SEÇ" : "..."));
            primaryButton.active = bigProgress >= 0.85F && selectedIndex < 0 && analysisFinished && choicesResolved;
            primaryButton.setY(layout.bigY() + (int) ((1.0F - bigProgress) * 28.0F) + layout.bigHeight() - primaryButton.getHeight() - 8);
        }
        if (secondaryButton != null) {
            secondaryButton.setMessage(Component.literal(analysisFinished && choicesResolved ? "SEÇ" : "..."));
            secondaryButton.active = bigProgress2 >= 0.85F && selectedIndex < 0 && analysisFinished && choicesResolved;
            secondaryButton.setY(layout.bigY() + (int) ((1.0F - bigProgress2) * 28.0F) + layout.bigHeight() - secondaryButton.getHeight() - 8);
        }

        // Altta kilitli 5 küçük kart
        int[] locked = lockedIndices();
        for (int i = 0; i < locked.length; i++) {
            int classIndex = locked[i];
            float p = ClientUiRules.staggeredProgress(totalProgress, i, locked.length, 0.28F);
            int x = layout.smallStartX() + i * (layout.smallWidth() + layout.smallGap());
            int y = layout.smallY() + (int) ((1.0F - p) * 18.0F);
            drawSmallCard(graphics, classIndex, x, y, layout.smallWidth(), layout.smallHeight(), p, now, mouseX, mouseY);
        }

        if (!analysisFinished && config.scanAnimation()) {
            int scanX = (int) ((now - openedAt) % 1400L / 1400.0 * width);
            graphics.fill(scanX - 1, 36, scanX + 2, height - 10, 0x5535D7D0);
        }

        String signature = "Made by Yankalan";
        graphics.text(font, signature, width - font.width(signature) - 7, height - 11, 0xFF85D68A, true);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private int shakeOffset(int classIndex, long now) {
        if (selectedIndex != classIndex) return 0;
        long elapsed = now - selectedAt;
        if (elapsed >= 420L) return 0;
        return (int) Math.round(Math.sin(elapsed * 0.085) * (1.0 - elapsed / 420.0) * 7.0);
    }

    private int[] lockedIndices() {
        if (!choicesResolved) return new int[0];
        int[] locked = new int[CLASSES.length - 2];
        int n = 0;
        for (int i = 0; i < CLASSES.length; i++) {
            if (i != primaryIndex && i != secondaryIndex) locked[n++] = i;
        }
        return locked;
    }

    private void drawPlaceholderBig(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, 0xFF0A1018, 0xFF152030);
        g.outline(x, y, w, h, 0x6635D7D0);
        String t = "...";
        g.text(font, t, x + (w - font.width(t)) / 2, y + h / 2 - 4, 0xFF8AB8C8, false);
    }

    private void drawBigCard(
        GuiGraphicsExtractor graphics,
        int index,
        int rank,
        int x,
        int y,
        int w,
        int h,
        float progress,
        long now
    ) {
        int alpha = (int) (255 * progress);
        graphics.fillGradient(x, y, x + w, y + h, withAlpha(TOP_COLORS[index], alpha), withAlpha(BOTTOM_COLORS[index], alpha));

        float pulse = (float) ((Math.sin(now / 260.0) + 1.0) * 0.5);
        int outlineAlpha = rank == 1 ? 190 + (int) (65 * pulse) : 160 + (int) (50 * pulse);
        graphics.outline(x, y, w, h, withAlpha(ACCENTS[index], outlineAlpha));
        graphics.outline(x - 2, y - 2, w + 4, h + 4, withAlpha(ACCENTS[index], (rank == 1 ? 70 : 45) + (int) (50 * pulse)));

        int btnH = layout().compact() ? 18 : 20;
        int artBottom = Math.max(y + 40, y + h - btnH - 36);
        graphics.enableScissor(x + 2, y + 2, x + w - 2, artBottom);
        drawClassArt(graphics, index, x, y, w, artBottom - y, now);
        graphics.disableScissor();

        // Rozet
        String badge = rank == 1 ? "1. ÖNERİ" : "2. ÖNERİ";
        if (!result.hasRecommendation() && analysisFinished) {
            badge = rank == 1 ? "AÇIK" : "AÇIK";
        }
        int badgeW = font.width(badge) + 10;
        graphics.fill(x + 6, y + 6, x + 6 + badgeW, y + 18, withAlpha(ACCENTS[index], rank == 1 ? 200 : 150));
        graphics.text(font, badge, x + 11, y + 8, 0xFF0A0E14, false);

        // Uyum yüzdesi
        if (result.hasRecommendation()) {
            int score = (int) Math.round(result.score(index) * 100.0);
            String scoreText = "%" + score;
            graphics.text(font, scoreText, x + w - font.width(scoreText) - 8, y + 8, withAlpha(ACCENTS[index], 230), true);
        }

        String titleText = fit(TITLES[index], w - 16);
        graphics.text(font, titleText, x + (w - font.width(titleText)) / 2, artBottom + 4, 0xFFFFFFFF, true);
        if (w >= 100) {
            String sub = fit(SUBTITLES[index], w - 16);
            graphics.text(font, sub, x + (w - font.width(sub)) / 2, artBottom + 16, 0xFFE0E8F0, false);
        }

        // Güç ikonları
        if (progress > 0.55F && w >= 90) {
            int iconSize = Math.min(12, Math.max(8, w / 14));
            int gap = 3;
            int total = iconSize * 6 + gap * 5;
            int iconX = x + Math.max(6, (w - total) / 2);
            int iconY = artBottom + (w >= 100 ? 28 : 18);
            if (iconY + iconSize < y + h - btnH - 10) {
                for (int level = 1; level <= 6; level++) {
                    int iconAccent = withAlpha(PowerIconArt.shade(ACCENTS[index], level), 220);
                    PowerIconArt.draw(graphics, CLASSES[index], level, iconX, iconY, iconSize, iconAccent);
                    iconX += iconSize + gap;
                }
            }
        }
    }

    private void drawSmallCard(
        GuiGraphicsExtractor graphics,
        int index,
        int x,
        int y,
        int w,
        int h,
        float progress,
        long now,
        int mouseX,
        int mouseY
    ) {
        int alpha = (int) (200 * progress);
        graphics.fillGradient(x, y, x + w, y + h, withAlpha(TOP_COLORS[index], alpha), withAlpha(BOTTOM_COLORS[index], alpha));
        graphics.outline(x, y, w, h, withAlpha(0xFF6A7380, 140));

        // Karartma (kilitli)
        graphics.fill(x, y, x + w, y + h, 0x99060A10);

        int artH = Math.max(20, h - 22);
        graphics.enableScissor(x + 1, y + 1, x + w - 1, y + artH);
        drawClassArt(graphics, index, x, y, w, artH, now);
        graphics.disableScissor();
        graphics.fill(x, y, x + w, y + artH, 0x66000000);

        String titleText = fit(TITLES[index], w - 6);
        graphics.text(font, titleText, x + (w - font.width(titleText)) / 2, y + h - 14, 0xFF9AA3AD, false);

        // Kilit simgesi
        String lock = "🔒";
        // Bazı fontlarda emoji yok; metin yedegi
        String lockText = "KİLİT";
        if (w >= 48) {
            graphics.text(font, lockText, x + (w - font.width(lockText)) / 2, y + 6, 0xFFE8C07A, true);
        }

        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h && progress > 0.8F) {
            String tip = "Yalnızca 1. ve 2. öneri seçilebilir";
            int tipW = font.width(tip) + 10;
            int tipX = Math.max(4, Math.min(width - tipW - 4, mouseX + 8));
            int tipY = Math.max(4, mouseY - 16);
            graphics.fill(tipX, tipY, tipX + tipW, tipY + 14, 0xEE0A1018);
            graphics.outline(tipX, tipY, tipW, 14, 0xFF5CE5E5);
            graphics.text(font, tip, tipX + 5, tipY + 3, 0xFFE8F4FF, false);
        }
    }

    private void drawClassArt(GuiGraphicsExtractor graphics, int index, int x, int y, int w, int h, long now) {
        switch (index) {
            case 0 -> drawAncientCity(graphics, x, y, w, h, now);
            case 1 -> drawDragonStorm(graphics, x, y, w, h, now);
            case 2 -> drawLavaCave(graphics, x, y, w, h, now);
            case 3 -> drawMoon(graphics, x, y, w, h, now);
            case 4 -> drawAnomalyGlitch(graphics, x, y, w, h, now);
            case 5 -> drawMagneticForge(graphics, x, y, w, h, now);
            case 6 -> drawDesertTemple(graphics, x, y, w, h, now);
            default -> { }
        }
    }

    private void drawAvatarCorner(GuiGraphicsExtractor graphics, long now, int left, int top) {
        int x = left + 19;
        graphics.fill(x - 20, top + 58, x + 20, top + 62, 0x44000000);
        if (!result.hasSkinImage()) {
            graphics.fill(x - 17, top + 4, x + 17, top + 50, 0xAA101722);
            graphics.outline(x - 17, top + 4, 34, 46, 0xCC63FFF1);
            String waiting = "?";
            graphics.text(font, waiting, x - font.width(waiting) / 2, top + 22, 0xFFFFFFFF, true);
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
            int scanY = top + (int) ((now - openedAt) % 900L / 900.0 * 58.0);
            graphics.fill(x - 18, scanY, x + 18, scanY + 2, 0xAA63FFF1);
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

    private ScreenLayout layout() {
        if (cachedLayout != null && cachedLayoutWidth == width && cachedLayoutHeight == height) {
            return cachedLayout;
        }
        boolean compact = width < 520 || height < 340;
        int side = compact ? 8 : 18;
        int gap = compact ? 8 : 14;
        int available = width - side * 2 - gap;
        int bigWidth = Math.max(120, Math.min(210, available / 2));
        int totalBig = bigWidth * 2 + gap;
        int bigLeftX = Math.max(side, (width - totalBig) / 2);
        int bigRightX = bigLeftX + bigWidth + gap;

        int topReserve = compact ? 34 : 42;
        int bottomReserve = compact ? 58 : 72;
        int bigHeight = Math.max(120, Math.min(compact ? 168 : 210, height - topReserve - bottomReserve - 8));
        int bigY = topReserve;

        int smallCount = 5;
        int smallGap = compact ? 4 : 6;
        int smallHeight = compact ? 44 : 54;
        int smallWidth = Math.max(48, Math.min(90, (width - side * 2 - smallGap * (smallCount - 1)) / smallCount));
        int smallTotal = smallWidth * smallCount + smallGap * (smallCount - 1);
        int smallStartX = Math.max(side, (width - smallTotal) / 2);
        int smallY = height - smallHeight - (compact ? 6 : 10);

        cachedLayoutWidth = width;
        cachedLayoutHeight = height;
        cachedLayout = new ScreenLayout(bigLeftX, bigRightX, bigY, bigWidth, bigHeight, smallStartX, smallY, smallWidth, smallHeight, smallGap, compact);
        return cachedLayout;
    }

    private String fit(String text, int maxWidth) {
        if (text == null) return "";
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

    private record ScreenLayout(
        int bigLeftX,
        int bigRightX,
        int bigY,
        int bigWidth,
        int bigHeight,
        int smallStartX,
        int smallY,
        int smallWidth,
        int smallHeight,
        int smallGap,
        boolean compact
    ) {}
}
