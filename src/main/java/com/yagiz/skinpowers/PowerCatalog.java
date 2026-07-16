package com.yagiz.skinpowers;

public final class PowerCatalog {
    public static final int[] XP_COSTS = {5, 15, 30, 40, 50};

    private static final String[][] NAMES = {
        {"-", "-", "-", "-", "-"},
        {"Warden Dayanıklılığı", "Yer Sarsıntısı", "Sonik Patlama", "Karanlık Görüş", "Warden Uyanışı"},
        {"Yavaş Düşüş", "Bağlı Kanatlar", "Roketsiz Kalkış", "Hava Patlaması", "Gökyüzü Hâkimiyeti"},
        {"Ateş Bağışıklığı", "Alevli Yakın Dövüş", "Ateş Çemberi", "Ateş Görüşü", "Meteor Yağmuru"}
    };

    private PowerCatalog() {}

    public static String powerName(PowerClass powerClass, int oneBasedLevel) {
        int classIndex = Math.max(0, Math.min(NAMES.length - 1, powerClass.ordinal()));
        int levelIndex = Math.max(0, Math.min(4, oneBasedLevel - 1));
        return NAMES[classIndex][levelIndex];
    }

    public static int xpCostForLevel(int level) {
        if (level < 1 || level > 5) return Integer.MAX_VALUE;
        return XP_COSTS[level - 1];
    }

    public static int masteryStage(int uses) {
        if (uses >= 16) return 3;
        if (uses >= 9) return 2;
        if (uses >= 5) return 1;
        return 0;
    }
}
