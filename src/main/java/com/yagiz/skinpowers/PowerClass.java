package com.yagiz.skinpowers;

public enum PowerClass {
    NONE("Sınıf seçilmedi"),
    WARDEN("Warden"),
    FLIGHT("Kadim Ejderha"),
    FIRE("Ateş"),
    MOON("Ay"),
    ANOMALY("Anomali"),
    MAGNETIC("Manyetik"),
    SAND("Kum");

    private final String displayName;

    PowerClass(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static PowerClass safeValueOf(String value) {
        if (value == null) return NONE;
        String normalized = value.toUpperCase(java.util.Locale.ROOT);
        // 1.0.3 ve önceki kayıtlardaki Zaman sınıfı, 1.0.4'te Anomaliye taşınır.
        if (normalized.equals("TIME") || normalized.equals("ZAMAN")) return ANOMALY;
        if (normalized.equals("DRAGON") || normalized.equals("EJDERHA") || normalized.equals("KADIM_EJDERHA")) return FLIGHT;
        if (normalized.equals("NATURE") || normalized.equals("DOGA") || normalized.equals("DOĞA") || normalized.equals("AY") || normalized.equals("MOON")) return MOON;
        if (normalized.equals("MAGNET") || normalized.equals("MANYETIK") || normalized.equals("MANYETİK")) return MAGNETIC;
        if (normalized.equals("KUM") || normalized.equals("SAND") || normalized.equals("COL")) return SAND;
        try {
            return PowerClass.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
