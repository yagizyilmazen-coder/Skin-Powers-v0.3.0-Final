package com.yagiz.skinpowers;

public final class PowerCatalog {
    public static final int[] XP_COSTS = {5, 15, 30, 40, 50};

    private static final String[][] NAMES = {
        {"-", "-", "-", "-", "-"},
        {"Warden Zırhı", "Yer Sarsıntısı", "Sonik Patlama", "Sculk Avı", "Warden Uyanışı"},
        {"Yavaş Düşüş", "Süreli Elytra", "Roketsiz Kalkış", "Hava Patlaması", "Gökyüzü Hâkimiyeti"},
        {"Ateş Bağışıklığı", "Alevli Yakın Dövüş", "Ateş Çemberi", "Cehennem Küresi", "Meteor Yağmuru"}
    };

    private static final String[][] DESCRIPTIONS = {
        {"", "", "", "", ""},
        {
            "Uzun süre Güç ve Direnç kazanırsın.",
            "Geniş alandaki düşmanları ezer, savurur ve zayıflatır.",
            "Önündeki hedeflere yüksek hasarlı sonik enerji yollar.",
            "Yakındaki düşmanları duvar arkasından parlatır ve avlar.",
            "Güç, direnç, yenilenme ve hasar aurası kazandırır."
        },
        {
            "Düşüş hasarını engeller; Y ile açıp kapatılır.",
            "R ile göğüs yuvasına süreli Elytra takar; süre bitince silinir.",
            "Geçici Elytra açıkken çift zıplamayla havalanırsın.",
            "Öndeki canlıları ve mermileri güçlü biçimde savurur.",
            "Süreli Elytra ile uçuş yolundaki hedeflere çarpar; darbeyi yumuşatıp hasar verir."
        },
        {
            "Ateş ve lav hasarına karşı sürekli koruma.",
            "Yakın dövüş vuruşlarına alev ve ek hasar ekler.",
            "Çevrende yakan ve hasar veren geniş bir halka.",
            "Baktığın yöne ilerleyen büyük bir ateş küresi fırlatır; çarpınca patlar.",
            "Çevrene görünür meteorlar indirir ve büyük kraterler açar."
        }
    };

    private PowerCatalog() {}

    public static String powerName(PowerClass powerClass, int oneBasedLevel) {
        int classIndex = Math.max(0, Math.min(NAMES.length - 1, powerClass.ordinal()));
        int levelIndex = Math.max(0, Math.min(4, oneBasedLevel - 1));
        return NAMES[classIndex][levelIndex];
    }

    public static String powerDescription(PowerClass powerClass, int oneBasedLevel) {
        int classIndex = Math.max(0, Math.min(DESCRIPTIONS.length - 1, powerClass.ordinal()));
        int levelIndex = Math.max(0, Math.min(4, oneBasedLevel - 1));
        return DESCRIPTIONS[classIndex][levelIndex];
    }

    public static int xpCostForLevel(int level) {
        if (level < 1 || level > 5) return Integer.MAX_VALUE;
        return XP_COSTS[level - 1];
    }

    public static int masteryStage(int uses) {
        if (uses >= 30) return 3;
        if (uses >= 15) return 2;
        if (uses >= 5) return 1;
        return 0;
    }

    public static String masteryStageName(int stage) {
        return switch (Math.max(0, Math.min(3, stage))) {
            case 0 -> "ACEMİ";
            case 1 -> "DENEYİMLİ";
            case 2 -> "UZMAN";
            default -> "USTA";
        };
    }

    public static int nextMasteryTarget(int uses) {
        if (uses < 5) return 5;
        if (uses < 15) return 15;
        if (uses < 30) return 30;
        return 30;
    }

    public static float masteryProgress(int uses) {
        if (uses >= 30) return 1.0F;
        int start = uses < 5 ? 0 : (uses < 15 ? 5 : 15);
        int end = uses < 5 ? 5 : (uses < 15 ? 15 : 30);
        return Math.max(0.0F, Math.min(1.0F, (uses - start) / (float) (end - start)));
    }
}
