package com.yagiz.skinpowers.client;

/** Saf GUI kuralları: Minecraft sınıflarına bağlı değildir ve doğrudan test edilebilir. */
public final class ClientUiRules {
    private ClientUiRules() {}

    public static boolean classChoiceAllowed(
        boolean analysisFinished,
        boolean hasRecommendation,
        int index,
        int classCount,
        int bestIndex,
        int secondIndex
    ) {
        if (!analysisFinished || index < 0 || index >= classCount) return false;
        return !hasRecommendation || index == bestIndex || index == secondIndex;
    }

    /**
     * Gecikmeli animasyonun son öğede bile mutlaka 1.0'a ulaşmasını sağlar.
     * Eski formül beşinci kartnı %89,6'da, Warden VI satırını %93,9'da bırakıyordu.
     */
    public static float staggeredProgress(float globalProgress, int index, int count, float maximumDelay) {
        float progress = clamp01(globalProgress);
        int safeCount = Math.max(1, count);
        int safeIndex = Math.max(0, Math.min(safeCount - 1, index));
        float delayLimit = Math.max(0.0F, Math.min(0.85F, maximumDelay));
        float delay = safeCount <= 1 ? 0.0F : delayLimit * safeIndex / (float) (safeCount - 1);
        if (progress <= delay) return 0.0F;
        float local = clamp01((progress - delay) / Math.max(0.0001F, 1.0F - delay));
        return local * local * (3.0F - 2.0F * local);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
