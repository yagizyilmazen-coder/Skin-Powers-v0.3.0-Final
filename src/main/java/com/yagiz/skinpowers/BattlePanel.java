package com.yagiz.skinpowers;

/** İstemcide küçük düello/bot paneli göstermek için ortak, salt okunur durum. */
public record BattlePanel(
    boolean visible,
    String mode,
    String opponentName,
    String opponentClass,
    float health,
    float maxHealth,
    float awakening,
    String detail
) {
    public static BattlePanel hidden() {
        return new BattlePanel(false, "", "", "", 0.0F, 0.0F, 0.0F, "");
    }
}
