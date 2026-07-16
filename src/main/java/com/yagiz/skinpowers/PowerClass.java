package com.yagiz.skinpowers;

public enum PowerClass {
    NONE("Sınıf seçilmedi"),
    WARDEN("Warden"),
    FLIGHT("Uçuş"),
    FIRE("Ateş"),
    WATER("Su");

    private final String displayName;

    PowerClass(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static PowerClass safeValueOf(String value) {
        if (value == null) return NONE;
        try {
            return PowerClass.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
