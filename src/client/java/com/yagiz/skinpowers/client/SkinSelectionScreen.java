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
    private long choicesResolvedAt;

    /** Seçilebilir iki sınıf (skin önerisi veya yedek çift). */
    private int primaryIndex = 0;
    private int secondaryIndex = 1;
    private boolean choicesResolved;

    /** 0 = yok, 1 = sol büyük, 2 = sağ büyük, 3+ = alt kilit index+3 */
    private int hoveredSlot = 0;
    private float primaryHover;
    private float secondaryHover;
    private final float[] lockedHover = new float[5];
    private int lockShakeSlot = -1;
    private long lockShakeAt;

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
        choicesResolvedAt = Util.getMillis();
    }

    private void select(int index) {
        if (selectedIndex >= 0 || !isSelectable(index)) return;
        selectedIndex = index;
        selectedAt = Util.getMillis();
        ClientPlayNetworking.send(new ClientCommandPayload("CHOOSE:" + CLASSES[index].name()));
    }

    private static float approach(float current, float target, float speed) {
        if (current < target) return Math.min(target, current + speed);
        if (current > target) return Math.max(target, current - speed);
        return current;
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
        boolean anim = ClientConfig.get().menuAnimations() && !ClientConfig.get().performanceMode();
        float speed = anim ? 0.18F : 1.0F;
        primaryHover = approach(primaryHover, hoveredSlot == 1 ? 1.0F : 0.0F, speed);
        secondaryHover = approach(secondaryHover, hoveredSlot == 2 ? 1.0F : 0.0F, speed);
        for (int i = 0; i < lockedHover.length; i++) {
            lockedHover[i] = approach(lockedHover[i], hoveredSlot == i + 3 ? 1.0F : 0.0F, speed);
        }

        if (selectedIndex < 0 || minecraft == null) return;
        // Seçim onayı animasyonu bitsin diye biraz daha uzun tut
        if (ClientState.powerClass() == CLASSES[selectedIndex]) {
            if (Util.getMillis() - selectedAt > 520L) minecraft.setScreen(null);
            return;
        }
        if (Util.getMillis() - selectedAt > 3000L) selectedIndex = -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && choicesResolved && selectedIndex < 0) {
            ScreenLayout layout = layout();
            int[] locked = lockedIndices();
            for (int i = 0; i < locked.length; i++) {
                int x = layout.smallStartX() + i * (layout.smallWidth() + layout.smallGap());
                int y = layout.smallY();
                if (mouseX >= x && mouseX < x + layout.smallWidth() && mouseY >= y && mouseY < y + layout.smallHeight()) {
                    lockShakeSlot = i;
                    lockShakeAt = Util.getMillis();
                    break;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, width, height, 0xFF03060B, 0xFF101622);

        long now = Util.getMillis();
        ClientConfig config = ClientConfig.get();
        boolean anim = config.menuAnimations() && !config.performanceMode();
        float animationFactor = anim ? config.cardAnimationSpeedPercent() / 100.0F : 8.0F;
        float totalProgress = clamp01((now - openedAt) / (1000.0F / Math.max(0.25F, animationFactor)));
        // Analiz bitince kartlar ikinci bir "reveal" ile otursun
        float reveal = choicesResolved
            ? clamp01((now - choicesResolvedAt) / (anim ? 480.0F : 1.0F))
            : 0.0F;
        float revealSmooth = smoothStep(reveal);
        ScreenLayout layout = layout();
        positionChoiceButtons(layout);
        updateHover(mouseX, mouseY, layout);

        // Başlık — hafif fade
        float titleAlpha = clamp01(totalProgress * 1.4F);
        String titleText = fit(title.getString(), Math.max(80, width - 40));
        graphics.text(font, titleText, (width - font.width(titleText)) / 2, layout.compact() ? 8 : 10,
            withAlpha(0x00FFFFFF, (int) (255 * titleAlpha)), true);

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
        graphics.text(font, scanText, (width - font.width(scanText)) / 2, layout.compact() ? 20 : 24,
            analysisFinished ? 0xFF9BEFD9 : 0xFFE7F4FF, false);

        // Küçük skin avatar (sol üst, compact değilse)
        if (!layout.compact() && !config.performanceMode()) {
            drawAvatarCorner(graphics, now, 14, 38);
        }

        // İki büyük kart
        float bigProgress = ClientUiRules.staggeredProgress(totalProgress, 0, 2, 0.22F) * Math.max(0.35F, revealSmooth);
        float bigProgress2 = ClientUiRules.staggeredProgress(totalProgress, 1, 2, 0.22F) * Math.max(0.35F, revealSmooth);
        if (choicesResolved) {
            int shake1 = selectShake(primaryIndex, now);
            int shake2 = selectShake(secondaryIndex, now);
            int float1 = anim ? (int) Math.round(Math.sin(now / 640.0) * 2.2) : 0;
            int float2 = anim ? (int) Math.round(Math.sin(now / 640.0 + 1.3) * 2.2) : 0;
            int hoverLift1 = Math.round(primaryHover * 5.0F);
            int hoverLift2 = Math.round(secondaryHover * 5.0F);
            int grow1 = Math.round(primaryHover * 4.0F);
            int grow2 = Math.round(secondaryHover * 4.0F);
            // Seçim anında kart büyüyüp parlasın
            float selectBurst1 = selectBurst(primaryIndex, now);
            float selectBurst2 = selectBurst(secondaryIndex, now);
            grow1 += Math.round(selectBurst1 * 8.0F);
            grow2 += Math.round(selectBurst2 * 8.0F);

            int y1 = layout.bigY() + (int) ((1.0F - bigProgress) * 36.0F) - hoverLift1 + float1;
            int y2 = layout.bigY() + (int) ((1.0F - bigProgress2) * 36.0F) - hoverLift2 + float2;
            int x1 = layout.bigLeftX() - grow1 / 2 + shake1;
            int x2 = layout.bigRightX() - grow2 / 2 + shake2;
            int w1 = layout.bigWidth() + grow1;
            int w2 = layout.bigWidth() + grow2;
            int h1 = layout.bigHeight() + hoverLift1 / 2 + Math.round(selectBurst1 * 4);
            int h2 = layout.bigHeight() + hoverLift2 / 2 + Math.round(selectBurst2 * 4);

            drawBigCard(graphics, primaryIndex, 1, x1, y1, w1, h1, bigProgress, now, primaryHover, selectBurst1);
            drawBigCard(graphics, secondaryIndex, 2, x2, y2, w2, h2, bigProgress2, now, secondaryHover, selectBurst2);
        } else {
            drawPlaceholderBig(graphics, layout.bigLeftX(), layout.bigY(), layout.bigWidth(), layout.bigHeight(), now);
            drawPlaceholderBig(graphics, layout.bigRightX(), layout.bigY(), layout.bigWidth(), layout.bigHeight(), now);
        }

        // Butonlar — kartla birlikte kaysın
        if (primaryButton != null) {
            primaryButton.setMessage(Component.literal(analysisFinished && choicesResolved ? "SEÇ" : "..."));
            primaryButton.active = bigProgress >= 0.85F && selectedIndex < 0 && analysisFinished && choicesResolved;
            int hoverLift1 = Math.round(primaryHover * 5.0F);
            int float1 = anim ? (int) Math.round(Math.sin(now / 640.0) * 2.2) : 0;
            primaryButton.setY(layout.bigY() + (int) ((1.0F - bigProgress) * 36.0F) - hoverLift1 + float1
                + layout.bigHeight() - primaryButton.getHeight() - 8);
            primaryButton.setX(layout.bigLeftX() + (layout.bigWidth() - primaryButton.getWidth()) / 2);
        }
        if (secondaryButton != null) {
            secondaryButton.setMessage(Component.literal(analysisFinished && choicesResolved ? "SEÇ" : "..."));
            secondaryButton.active = bigProgress2 >= 0.85F && selectedIndex < 0 && analysisFinished && choicesResolved;
            int hoverLift2 = Math.round(secondaryHover * 5.0F);
            int float2 = anim ? (int) Math.round(Math.sin(now / 640.0 + 1.3) * 2.2) : 0;
            secondaryButton.setY(layout.bigY() + (int) ((1.0F - bigProgress2) * 36.0F) - hoverLift2 + float2
                + layout.bigHeight() - secondaryButton.getHeight() - 8);
            secondaryButton.setX(layout.bigRightX() + (layout.bigWidth() - secondaryButton.getWidth()) / 2);
        }

        // Altta kilitli 5 küçük kart
        int[] locked = lockedIndices();
        for (int i = 0; i < locked.length; i++) {
            int classIndex = locked[i];
            float p = ClientUiRules.staggeredProgress(totalProgress, i, Math.max(1, locked.length), 0.32F) * Math.max(0.4F, revealSmooth);
            int baseX = layout.smallStartX() + i * (layout.smallWidth() + layout.smallGap());
            int lockShake = 0;
            if (lockShakeSlot == i && now - lockShakeAt < 380L) {
                float t = (now - lockShakeAt) / 380.0F;
                lockShake = (int) Math.round(Math.sin(t * Math.PI * 6.0) * (1.0 - t) * 5.0);
            }
            int hoverLift = Math.round(lockedHover[i] * 3.0F);
            int x = baseX + lockShake;
            int y = layout.smallY() + (int) ((1.0F - p) * 22.0F) - hoverLift;
            drawSmallCard(graphics, classIndex, x, y, layout.smallWidth(), layout.smallHeight() + hoverLift / 2,
                p, now, mouseX, mouseY, lockedHover[i], lockShakeSlot == i && now - lockShakeAt < 380L);
        }

        // Seçim flaşı
        if (selectedIndex >= 0 && now - selectedAt < 420L) {
            float flash = 1.0F - (now - selectedAt) / 420.0F;
            int a = Math.round(55 * flash);
            graphics.fill(0, 0, width, height, (a << 24) | (ACCENTS[selectedIndex] & 0x00FFFFFF));
        }

        if (!analysisFinished && config.scanAnimation()) {
            int scanX = (int) ((now - openedAt) % 1400L / 1400.0 * width);
            graphics.fill(scanX - 1, 36, scanX + 2, height - 10, 0x5535D7D0);
        }

        String signature = "Made by Yankalan";
        graphics.text(font, signature, width - font.width(signature) - 7, height - 11, 0xFF85D68A, true);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private int selectShake(int classIndex, long now) {
        if (selectedIndex != classIndex) return 0;
        long elapsed = now - selectedAt;
        if (elapsed >= 480L) return 0;
        return (int) Math.round(Math.sin(elapsed * 0.11) * (1.0 - elapsed / 480.0) * 8.0);
    }

    private float selectBurst(int classIndex, long now) {
        if (selectedIndex != classIndex) return 0.0F;
        long elapsed = now - selectedAt;
        if (elapsed >= 520L) return 0.0F;
        float t = elapsed / 520.0F;
        // Hızlı büyüyüp yavaş sön
        return (float) (Math.sin(t * Math.PI) * (1.0 - t * 0.35));
    }

    private void updateHover(int mouseX, int mouseY, ScreenLayout layout) {
        hoveredSlot = 0;
        if (!choicesResolved || selectedIndex >= 0) return;
        int by = layout.bigY();
        int bh = layout.bigHeight();
        if (mouseY >= by - 6 && mouseY < by + bh + 8) {
            if (mouseX >= layout.bigLeftX() - 4 && mouseX < layout.bigLeftX() + layout.bigWidth() + 4) {
                hoveredSlot = 1;
                return;
            }
            if (mouseX >= layout.bigRightX() - 4 && mouseX < layout.bigRightX() + layout.bigWidth() + 4) {
                hoveredSlot = 2;
                return;
            }
        }
        int[] locked = lockedIndices();
        for (int i = 0; i < locked.length; i++) {
            int x = layout.smallStartX() + i * (layout.smallWidth() + layout.smallGap());
            int y = layout.smallY();
            if (mouseX >= x && mouseX < x + layout.smallWidth() && mouseY >= y && mouseY < y + layout.smallHeight()) {
                hoveredSlot = i + 3;
                return;
            }
        }
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

    private void drawPlaceholderBig(GuiGraphicsExtractor g, int x, int y, int w, int h, long now) {
        float pulse = (float) ((Math.sin(now / 280.0) + 1.0) * 0.5);
        g.fillGradient(x, y, x + w, y + h, 0xFF0A1018, 0xFF152030);
        g.outline(x, y, w, h, withAlpha(0xFF35D7D0, 80 + (int) (70 * pulse)));
        // Tarama çizgisi
        int scanY = y + 8 + (int) ((now % 1200L) / 1200.0 * Math.max(1, h - 16));
        g.fill(x + 4, scanY, x + w - 4, scanY + 2, 0x5535D7D0);
        String t = "...";
        g.text(font, t, x + (w - font.width(t)) / 2, y + h / 2 - 4, withAlpha(0x008AB8C8, 160 + (int) (80 * pulse)), false);
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
        long now,
        float hover,
        float selectBurst
    ) {
        int alpha = (int) (255 * clamp01(progress));
        graphics.fillGradient(x, y, x + w, y + h, withAlpha(TOP_COLORS[index], alpha), withAlpha(BOTTOM_COLORS[index], alpha));

        float pulse = (float) ((Math.sin(now / 260.0) + 1.0) * 0.5);
        int outlineAlpha = rank == 1 ? 190 + (int) (65 * pulse) : 160 + (int) (50 * pulse);
        outlineAlpha = Math.min(255, outlineAlpha + Math.round(hover * 50) + Math.round(selectBurst * 80));
        graphics.outline(x, y, w, h, withAlpha(ACCENTS[index], outlineAlpha));
        graphics.outline(x - 2, y - 2, w + 4, h + 4,
            withAlpha(ACCENTS[index], (rank == 1 ? 70 : 45) + (int) (50 * pulse) + Math.round(hover * 40) + Math.round(selectBurst * 70)));
        if (hover > 0.05F || selectBurst > 0.05F) {
            int glow = Math.round((hover * 28) + (selectBurst * 45));
            graphics.outline(x - 4, y - 4, w + 8, h + 8, withAlpha(ACCENTS[index], glow));
        }

        // Üstten aşağı parlama süpürmesi (idle)
        if (progress > 0.7F && ClientConfig.get().menuAnimations() && !ClientConfig.get().performanceMode()) {
            int sweep = y + (int) ((now / 18L) % Math.max(1, h));
            graphics.fill(x + 3, sweep, x + w - 3, Math.min(y + h - 3, sweep + 3), withAlpha(ACCENTS[index], 18 + Math.round(hover * 20)));
        }

        int btnH = layout().compact() ? 18 : 20;
        int artBottom = Math.max(y + 40, y + h - btnH - 36);
        graphics.enableScissor(x + 2, y + 2, x + w - 2, artBottom);
        drawClassArt(graphics, index, x, y, w, artBottom - y, now);
        graphics.disableScissor();

        // Rozet — nabız
        String badge = (!result.hasRecommendation() && analysisFinished) ? "AÇIK" : (rank == 1 ? "1. ÖNERİ" : "2. ÖNERİ");
        int badgeW = font.width(badge) + 10;
        int badgePulse = rank == 1 ? 200 + (int) (40 * pulse) : 150 + (int) (30 * pulse);
        graphics.fill(x + 6, y + 6, x + 6 + badgeW, y + 18, withAlpha(ACCENTS[index], Math.min(255, badgePulse + Math.round(hover * 30))));
        graphics.text(font, badge, x + 11, y + 8, 0xFF0A0E14, false);

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

        if (progress > 0.55F && w >= 90) {
            int iconSize = Math.min(12, Math.max(8, w / 14));
            int gap = 3;
            int total = iconSize * 6 + gap * 5;
            int iconX = x + Math.max(6, (w - total) / 2);
            int iconY = artBottom + (w >= 100 ? 28 : 18);
            if (iconY + iconSize < y + h - btnH - 10) {
                for (int level = 1; level <= 6; level++) {
                    // İkonlar sırayla belirsin
                    float iconPop = clamp01((progress - 0.55F) / 0.35F);
                    float staggered = ClientUiRules.staggeredProgress(iconPop, level - 1, 6, 0.45F);
                    if (staggered <= 0.05F) continue;
                    int popLift = Math.round((1.0F - staggered) * 4.0F);
                    int iconAccent = withAlpha(PowerIconArt.shade(ACCENTS[index], level), Math.round(220 * staggered));
                    PowerIconArt.draw(graphics, CLASSES[index], level, iconX, iconY - popLift, iconSize, iconAccent);
                    iconX += iconSize + gap;
                }
            }
        }

        // Seçim mühür çemberi
        if (selectBurst > 0.05F) {
            int cx = x + w / 2;
            int cy = y + h / 2;
            int radius = Math.round(12 + selectBurst * Math.min(w, h) * 0.42F);
            graphics.outline(cx - radius, cy - radius, radius * 2, radius * 2, withAlpha(ACCENTS[index], Math.round(180 * selectBurst)));
            graphics.outline(cx - radius - 3, cy - radius - 3, radius * 2 + 6, radius * 2 + 6, withAlpha(0xFFFFFFFF, Math.round(90 * selectBurst)));
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
        int mouseY,
        float hover,
        boolean shaking
    ) {
        int alpha = (int) (255 * clamp01(progress));
        graphics.fillGradient(x, y, x + w, y + h, withAlpha(TOP_COLORS[index], alpha), withAlpha(BOTTOM_COLORS[index], alpha));
        int outlineA = 100 + Math.round(hover * 70) + (shaking ? 60 : 0);
        graphics.outline(x, y, w, h, withAlpha(ACCENTS[index], outlineA));
        if (hover > 0.1F || shaking) {
            graphics.outline(x - 1, y - 1, w + 2, h + 2, withAlpha(ACCENTS[index], Math.round(hover * 50) + (shaking ? 40 : 0)));
        }

        int labelH = 14;
        int artH = Math.max(24, h - labelH);
        graphics.enableScissor(x + 1, y + 1, x + w - 1, y + artH);
        drawClassArt(graphics, index, x, y, w, artH, now);
        graphics.disableScissor();

        graphics.fill(x, y, x + w, y + artH, 0x40081018);

        String lockText = "KİLİT";
        int badgeW = font.width(lockText) + 8;
        int badgeFlash = shaking ? (int) ((Math.sin(now / 40.0) + 1.0) * 40.0) : 0;
        graphics.fill(x + 3, y + 3, x + 3 + badgeW, y + 14, 0xCC1A1208);
        graphics.outline(x + 3, y + 3, badgeW, 11, withAlpha(0xFFC9A15A, 200 + badgeFlash));
        graphics.text(font, lockText, x + 7, y + 5, 0xFFFFD89A, false);

        graphics.fill(x, y + artH, x + w, y + h, 0xDD0A0E14);
        String titleText = fit(TITLES[index], w - 6);
        graphics.text(font, titleText, x + (w - font.width(titleText)) / 2, y + artH + 3, 0xFFD0D8E0, false);

        if (hover > 0.4F && progress > 0.8F) {
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

    /** Kart boyuna göre ölçek: küçük kartlarda da ana silüet okunur. */
    private static float artScale(int w, int h) {
        return Math.max(0.45F, Math.min(1.35F, Math.min(w / 100.0F, h / 70.0F)));
    }

    private void drawAncientCity(GuiGraphicsExtractor g, int x, int y, int w, int h, long now) {
        float s = artScale(w, h);
        int floor = y + h - Math.max(6, Math.round(10 * s));
        g.fillGradient(x, y, x + w, y + h, 0xFF03070B, 0xFF0B2631);
        g.fill(x, floor, x + w, y + h, 0xFF061018);
        int towers = Math.max(3, Math.min(6, w / 18));
        for (int i = 0; i < towers; i++) {
            int towerX = x + 2 + i * Math.max(6, (w - 4) / towers);
            int towerH = Math.round((10 + (i % 3) * 7) * s);
            int tw = Math.max(3, Math.round(5 * s));
            g.fill(towerX, floor - towerH, towerX + tw, floor, 0xFF102733);
            g.fill(towerX + 1, floor - towerH + 2, towerX + tw - 1, floor - towerH + Math.max(4, Math.round(6 * s)), 0xFF22BDB8);
        }
        int cx = x + w / 2;
        int bodyH = Math.max(22, Math.min(h - 8, Math.round(48 * s)));
        int bottom = floor - 1;
        int top = bottom - bodyH;
        int bodyHalf = Math.max(6, Math.min(w / 4, Math.round(14 * s)));
        int horn = Math.max(4, bodyHalf / 2);
        g.fill(cx - bodyHalf, top + Math.round(10 * s), cx + bodyHalf, bottom, 0xFF0B171D);
        g.fill(cx - bodyHalf + 2, top + Math.round(4 * s), cx + bodyHalf - 2, top + Math.round(16 * s), 0xFF101C23);
        g.fill(cx - bodyHalf - horn, top + Math.round(2 * s), cx - bodyHalf + 2, top + Math.round(7 * s), 0xFF176071);
        g.fill(cx + bodyHalf - 2, top + Math.round(2 * s), cx + bodyHalf + horn, top + Math.round(7 * s), 0xFF176071);
        int armW = Math.max(3, Math.round(6 * s));
        g.fill(cx - bodyHalf - armW, top + Math.round(14 * s), cx - bodyHalf, bottom - Math.round(4 * s), 0xFF091216);
        g.fill(cx + bodyHalf, top + Math.round(14 * s), cx + bodyHalf + armW, bottom - Math.round(4 * s), 0xFF091216);
        int glow = 145 + (int) ((Math.sin(now / 220.0) + 1.0) * 45.0);
        int ribs = h < 50 ? 2 : 3;
        for (int rib = 0; rib < ribs; rib++) {
            int ry = top + Math.round(16 * s) + rib * Math.max(4, Math.round(6 * s));
            int rw = Math.max(3, Math.round(6 * s)) + rib;
            g.fill(cx - rw, ry, cx - 1, ry + Math.max(2, Math.round(3 * s)), withAlpha(0xFF43E5DC, glow));
            g.fill(cx + 1, ry, cx + rw, ry + Math.max(2, Math.round(3 * s)), withAlpha(0xFF43E5DC, glow));
        }
        int eye = Math.max(2, Math.round(3 * s));
        g.fill(cx - Math.round(5 * s), top + Math.round(8 * s), cx - Math.round(5 * s) + eye, top + Math.round(8 * s) + eye, 0xFFE7FFFF);
        g.fill(cx + Math.round(3 * s), top + Math.round(8 * s), cx + Math.round(3 * s) + eye, top + Math.round(8 * s) + eye, 0xFFE7FFFF);
    }

    private void drawDragonStorm(GuiGraphicsExtractor g, int x, int y, int w, int h, long now) {
        float s = artScale(w, h);
        g.fillGradient(x, y, x + w, y + h, 0xFF05010B, 0xFF411063);
        int ground = y + h - Math.max(5, Math.round(10 * s));
        g.fill(x, ground, x + w, y + h, 0xFF09050F);
        for (int i = 0; i < 4; i++) {
            int mx = x + i * Math.max(10, w / 4) - 4;
            int mh = Math.round((8 + (i % 3) * 6) * s);
            g.fill(mx, ground - mh, mx + Math.max(12, w / 4), ground, i % 2 == 0 ? 0xFF140A20 : 0xFF21102E);
        }
        int cx = x + w / 2;
        int cy = y + Math.max(Math.round(18 * s), h / 2);
        int flap = ClientConfig.get().menuAnimations() ? (int) Math.round(Math.sin(now / 210.0) * Math.max(2, 3 * s)) : 0;
        int pulse = 150 + (int) ((Math.sin(now / 170.0) + 1.0) * 45.0);
        int bodyW = Math.max(4, Math.round(6 * s));
        int bodyH = Math.max(14, Math.round(28 * s));
        g.fill(cx - bodyW, cy - Math.round(16 * s), cx + bodyW, cy + Math.round(12 * s), 0xE90B0611);
        g.fill(cx - Math.round(10 * s), cy - Math.round(18 * s), cx - Math.round(3 * s), cy - Math.round(12 * s), 0xFF170721);
        g.fill(cx + Math.round(3 * s), cy - Math.round(18 * s), cx + Math.round(10 * s), cy - Math.round(12 * s), 0xFF170721);
        int wing = Math.max(10, Math.round(28 * s));
        int wingH = Math.max(8, Math.round(20 * s));
        g.fill(cx - wing, cy - Math.round(12 * s) + flap, cx - bodyW, cy - Math.round(6 * s) + flap, 0xC14B1370);
        g.fill(cx + bodyW, cy - Math.round(12 * s) + flap, cx + wing, cy - Math.round(6 * s) + flap, 0xC14B1370);
        g.fill(cx - Math.round(wing * 0.75F), cy - Math.round(5 * s) + flap, cx - bodyW, cy + Math.round(4 * s) + flap, 0xD16B1A98);
        g.fill(cx + bodyW, cy - Math.round(5 * s) + flap, cx + Math.round(wing * 0.75F), cy + Math.round(4 * s) + flap, 0xD16B1A98);
        g.outline(cx - wing, cy - Math.round(12 * s) + flap, wing - bodyW, wingH, withAlpha(0xFFDCA1FF, pulse));
        g.outline(cx + bodyW, cy - Math.round(12 * s) + flap, wing - bodyW, wingH, withAlpha(0xFFDCA1FF, pulse));
        int eye = Math.max(2, Math.round(3 * s));
        g.fill(cx - Math.round(4 * s), cy - Math.round(12 * s), cx - Math.round(4 * s) + eye, cy - Math.round(12 * s) + eye, 0xFFFFFFFF);
        g.fill(cx + Math.round(2 * s), cy - Math.round(12 * s), cx + Math.round(2 * s) + eye, cy - Math.round(12 * s) + eye, 0xFFFFFFFF);
        g.fill(cx - Math.round(2 * s), cy - Math.round(2 * s), cx + Math.round(2 * s), cy + Math.round(3 * s), withAlpha(0xFFE9B6FF, pulse));
    }

    private void drawLavaCave(GuiGraphicsExtractor g, int x, int y, int w, int h, long now) {
        float s = artScale(w, h);
        g.fillGradient(x, y, x + w, y + h, 0xFF1A0604, 0xFF3A1008);
        int spikes = Math.max(3, Math.min(7, w / 16));
        for (int i = 0; i < spikes; i++) {
            int px = x + i * Math.max(7, w / spikes);
            int length = Math.round((8 + (i % 3) * 6) * s);
            g.fill(px, y + Math.round(6 * s), px + Math.max(3, Math.round(5 * s)), y + Math.round(6 * s) + length, 0xFF2C0A05);
        }
        int lavaY = y + h - Math.max(8, Math.round(14 * s));
        g.fillGradient(x, lavaY, x + w, y + h, 0xFFFF8A10, 0xFFB31808);
        int wave = (int) ((Math.sin(now / 170.0) + 1.0) * Math.max(1, 2 * s));
        for (int i = 0; i < Math.max(3, w / 20); i++) {
            int bx = x + 4 + i * Math.max(8, (w - 8) / Math.max(3, w / 20));
            g.fill(bx, lavaY - wave - (i % 2) * 2, bx + Math.max(3, Math.round(4 * s)), lavaY + 1, 0xFFFFD24A);
        }
        int orbit = (int) Math.round(Math.sin(now / 260.0) * Math.max(2, w / 18.0));
        int cx = x + w / 2 + orbit;
        int cy = y + Math.max(Math.round(16 * s), h / 2 - Math.round(4 * s));
        int r = Math.max(6, Math.round(10 * s));
        g.fill(cx - r - 4, cy - 2, cx - r + 2, cy + 3, 0x88FF6A00);
        g.fill(cx - r, cy - r, cx + r, cy + r, 0xFFFF6A00);
        g.fill(cx - r / 2, cy - r / 2, cx + r / 2 + 2, cy + r / 2, 0xFFFFC52A);
        g.fill(cx - 1, cy - 2, cx + Math.max(2, r / 3), cy + 2, 0xFFFFFFB0);
    }

    private void drawMoon(GuiGraphicsExtractor g, int x, int y, int w, int h, long now) {
        float s = artScale(w, h);
        g.fillGradient(x, y, x + w, y + h, 0xFF040817, 0xFF34456F);
        int horizon = y + h - Math.max(5, Math.round(10 * s));
        g.fill(x, horizon, x + w, y + h, 0xFF080B16);
        int stars = Math.max(4, Math.min(10, w / 12));
        for (int i = 0; i < stars; i++) {
            int sx = x + 3 + (i * 29) % Math.max(8, w - 8);
            int sy = y + 4 + (i * 17) % Math.max(8, Math.max(1, h - 16));
            int blink = 135 + (int) ((Math.sin(now / 260.0 + i) + 1.0) * 55.0);
            g.fill(sx, sy, sx + 2, sy + 2, withAlpha(0xFFFFFFFF, blink));
        }
        int cx = x + w / 2;
        int cy = y + Math.max(Math.round(16 * s), h / 2);
        int pulse = 160 + (int) ((Math.sin(now / 210.0) + 1.0) * 45.0);
        int radius = Math.max(8, Math.min(Math.round(22 * s), Math.min(w, h) / 3));
        g.fill(cx - radius, cy - radius, cx + radius, cy + radius, withAlpha(0xFFE7EDFF, pulse));
        g.fill(cx - radius / 3, cy - radius - 1, cx + radius + 2, cy + radius + 1, 0xFF10182D);
        g.outline(cx - radius, cy - radius, radius * 2, radius * 2, 0xFFDCE6FF);
        int orbit = ClientConfig.get().menuAnimations() ? (int) Math.round(Math.sin(now / 180.0) * Math.max(2, 4 * s)) : 0;
        int sat = Math.max(3, Math.round(5 * s));
        g.fill(cx - radius - sat - 2 + orbit, cy - 1, cx - radius - 2 + orbit, cy + 2, 0xFFAFC6FF);
        g.fill(cx + radius + 2 - orbit, cy - 1, cx + radius + sat + 2 - orbit, cy + 2, 0xFF9474D6);
        g.fill(cx - 1, horizon - Math.max(6, Math.round(10 * s)), cx + 2, horizon, 0xFF66769F);
    }

    private void drawMagneticForge(GuiGraphicsExtractor g, int x, int y, int w, int h, long now) {
        float s = artScale(w, h);
        g.fillGradient(x, y, x + w, y + h, 0xFF10161D, 0xFF52606B);
        int ground = y + h - Math.max(4, Math.round(8 * s));
        g.fill(x, ground, x + w, y + h, 0xFF161B20);
        // Anvil / forge silhouette
        int cx = x + w / 2;
        int cy = y + Math.max(Math.round(14 * s), h / 2);
        int pulse = 130 + (int) ((Math.sin(now / 180.0) + 1.0) * 52.0);
        int pole = Math.max(5, Math.round(12 * s));
        int poleH = Math.max(6, Math.round(10 * s));
        g.fill(cx - pole - Math.round(4 * s), cy - poleH / 2, cx - Math.round(2 * s), cy + poleH / 2, 0xFFB7C2CB);
        g.fill(cx + Math.round(2 * s), cy - poleH / 2, cx + pole + Math.round(4 * s), cy + poleH / 2, 0xFF707D88);
        g.fill(cx - Math.round(3 * s), cy - Math.round(10 * s), cx + Math.round(3 * s), cy + Math.round(10 * s), withAlpha(0xFFD47E3F, pulse));
        // Anvil base
        int aw = Math.max(14, Math.round(28 * s));
        int ah = Math.max(4, Math.round(7 * s));
        g.fill(cx - aw / 2, cy + Math.round(8 * s), cx + aw / 2, cy + Math.round(8 * s) + ah, 0xFF8A959E);
        g.fill(cx - aw / 3, cy + Math.round(8 * s) + ah, cx + aw / 3, ground, 0xFF5A646E);
        int orbs = h < 45 ? 2 : 4;
        for (int i = 0; i < orbs; i++) {
            double ang = now / 210.0 + i * (Math.PI * 2.0 / orbs);
            int ox = (int) Math.round(Math.sin(ang) * Math.max(6, w / 5.0));
            int oy = (int) Math.round(Math.cos(ang) * Math.max(5, 9 * s));
            int os = Math.max(2, Math.round(3 * s));
            g.fill(cx + ox - os, cy + oy - os, cx + ox + os, cy + oy + os, i % 2 == 0 ? 0xFFC4D0D9 : 0xFFC6793C);
        }
        g.fill(x + 2, y + 4, x + Math.max(3, Math.round(4 * s)), ground, 0x554ED6FF);
        g.fill(x + w - Math.max(3, Math.round(4 * s)), y + 4, x + w - 2, ground, 0x55FF6E4A);
    }

    private void drawDesertTemple(GuiGraphicsExtractor g, int x, int y, int w, int h, long now) {
        float s = artScale(w, h);
        g.fillGradient(x, y, x + w, y + h, 0xFF5A3814, 0xFFE0B55D);
        int ground = y + h - Math.max(4, Math.round(8 * s));
        g.fill(x, ground, x + w, y + h, 0xFFD6AA51);
        int cx = x + w / 2;
        int templeW = Math.max(16, Math.min(w - 8, Math.round(w * 0.55F)));
        int left = cx - templeW / 2;
        int bodyH = Math.max(12, Math.round(22 * s));
        int roofH = Math.max(6, Math.round(10 * s));
        g.fill(left, ground - bodyH, left + templeW, ground, 0xFFE7CF8A);
        g.fill(left + Math.round(3 * s), ground - bodyH - roofH, left + templeW - Math.round(3 * s), ground - bodyH, 0xFFF1DB9B);
        // Door
        int doorW = Math.max(4, Math.round(6 * s));
        g.fill(cx - doorW / 2, ground - Math.round(14 * s), cx + doorW / 2, ground, 0xFF76502A);
        // Pillars
        int pw = Math.max(2, Math.round(3 * s));
        g.fill(left + 2, ground - bodyH, left + 2 + pw, ground, 0xFFC4A86A);
        g.fill(left + templeW - 2 - pw, ground - bodyH, left + templeW - 2, ground, 0xFFC4A86A);
        int drift = ClientConfig.get().animatedBackgrounds() ? (int) ((now / 55L) % Math.max(1, w + 16)) : 0;
        int grains = Math.max(3, Math.min(6, w / 18));
        for (int i = 0; i < grains; i++) {
            int sx = x + ((i * 23 + drift) % Math.max(1, w + 12)) - 6;
            int sy = y + 6 + (i * 11) % Math.max(10, Math.max(1, h - 18));
            g.fill(sx, sy, sx + Math.max(3, Math.round(5 * s)), sy + Math.max(2, Math.round(3 * s)), i % 2 == 0 ? 0xFFDDB96B : 0xFFEAD28C);
        }
        int wave = (int) Math.round(Math.sin(now / 190.0) * Math.max(1, 2 * s));
        g.fill(x + 2, ground - Math.max(3, Math.round(5 * s)) + wave, x + w - 2, ground - 1 + wave, 0x99F3D27C);
    }

    private void drawAnomalyGlitch(GuiGraphicsExtractor g, int x, int y, int w, int h, long now) {
        float s = artScale(w, h);
        g.fillGradient(x, y, x + w, y + h, 0xFF030108, 0xFF291248);
        int shift = (int) ((now / 85L) % Math.max(1, w));
        int lines = Math.max(5, Math.min(12, h / 6));
        for (int i = 0; i < lines; i++) {
            int gy = y + 4 + (i * Math.max(6, h / lines)) % Math.max(6, h - 6);
            int gx = x + ((i * 31 + shift * (i % 3 + 1)) % Math.max(1, w + 16)) - 8;
            int length = Math.max(5, Math.round((6 + (i % 4) * 6) * s));
            int color = switch (i % 3) {
                case 0 -> 0xAA5CE5E5;
                case 1 -> 0xAAB65CFF;
                default -> 0x88E94B63;
            };
            g.fill(gx, gy, Math.min(x + w, gx + length), gy + Math.max(1, Math.round(2 * s)), color);
        }
        int cx = x + w / 2;
        int cy = y + Math.max(Math.round(14 * s), h / 2);
        int jitter = (int) Math.round(Math.sin(now / 90.0) * Math.max(1, 2 * s));
        int boxW = Math.max(12, Math.round(24 * s));
        int boxH = Math.max(14, Math.round(26 * s));
        g.fill(cx - boxW / 2 + jitter, cy - boxH / 2, cx + boxW / 2 + jitter, cy + boxH / 2, 0x88000000);
        g.outline(cx - boxW / 2 - jitter, cy - boxH / 2, boxW, boxH, 0xFFB65CFF);
        g.outline(cx - boxW / 2 + 2 + jitter, cy - boxH / 2 + 2, boxW - 4, boxH - 4, 0xFF5CE5E5);
        g.text(font, "?", cx - font.width("?") / 2 + jitter, cy - 5, 0xFFFFFFFF, true);
        if (h >= 40 && (now / 300L) % 3L == 0L) {
            String error = "404";
            g.text(font, error, cx - font.width(error) / 2 - jitter, cy + Math.max(4, Math.round(6 * s)), 0xFFE94B63, false);
        }
    }

    private ScreenLayout layout() {
        if (cachedLayout != null && cachedLayoutWidth == width && cachedLayoutHeight == height) {
            return cachedLayout;
        }
        boolean compact = width < 520 || height < 360;
        int side = compact ? 8 : 16;
        int gap = compact ? 8 : 12;
        int available = width - side * 2 - gap;
        int bigWidth = Math.max(120, Math.min(220, available / 2));
        int totalBig = bigWidth * 2 + gap;
        int bigLeftX = Math.max(side, (width - totalBig) / 2);
        int bigRightX = bigLeftX + bigWidth + gap;

        int smallCount = 5;
        int smallGap = compact ? 5 : 8;
        int smallHeight = compact ? 64 : 78;
        int smallWidth = Math.max(56, Math.min(110, (width - side * 2 - smallGap * (smallCount - 1)) / smallCount));
        int smallTotal = smallWidth * smallCount + smallGap * (smallCount - 1);
        int smallStartX = Math.max(side, (width - smallTotal) / 2);
        int smallY = height - smallHeight - (compact ? 6 : 10);

        int topReserve = compact ? 32 : 40;
        int bottomReserve = smallHeight + (compact ? 10 : 14);
        int bigHeight = Math.max(110, Math.min(compact ? 160 : 200, height - topReserve - bottomReserve - 6));
        int bigY = topReserve;

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

    private static float smoothStep(float value) {
        float v = clamp01(value);
        return v * v * (3.0F - 2.0F * v);
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
