package com.yagiz.skinpowers;

public final class PowerCatalog {
    public static final int[] XP_COSTS = {5, 15, 30, 40, 50};
    public static final int[] NATURE_XP_COSTS = {10, 20, 30, 40, 50};
    public static final int[] TIME_XP_COSTS = {10, 20, 30, 40, 50};
    public static final int WARDEN_ANCIENT_CHARGE_XP = 70;

    private static final String[][] NAMES = {
        {"-", "-", "-", "-", "-", "-"},
        {"Warden Zırhı", "Yer Sarsıntısı", "Sonik Patlama", "Sculk Avı", "Warden Uyanışı", "Şarj Et Beni Antik Şehir"},
        {"Hafif Beden", "Gökyüzü Kanatları", "Gök Mızrağı", "Gökyüzü Bombası", "Göksel Kıyamet", "-"},
        {"Ateş Bağışıklığı", "Alevli Yakın Dövüş", "Ateş Çemberi", "Cehennem Küresi", "Meteor Yağmuru", "-"},
        {"Doğanın Canı", "Dikenli Tohum", "Sarmaşık Kapanı", "Yaşam Ağacı", "Kadim Orman Hükmü", "-"},
        {"Zaman Sezgisi", "Krono Mızrağı", "Geri Sarma", "Zaman Hapishanesi", "Zamanın Sonu", "-"}
    };

    private static final String[][] DESCRIPTIONS = {
        {"", "", "", "", "", ""},
        {
            "Uzun süre Güç, Direnç ve soğurma kazanırsın.",
            "Geniş alandaki düşmanları ezer, savurur ve zayıflatır.",
            "Önündeki hedeflere yüksek hasarlı sonik enerji yollar.",
            "Yakındaki düşmanları işaretler, yavaşlatır ve hareket ettikçe avlar.",
            "Güç, direnç, yenilenme ve hasar aurası kazandırır.",
            "Dört sculk kolu hedefe Antik Şehir enerjisi aktarır; çömelerek kendine uygulayabilirsin."
        },
        {
            "Düşme hasarını azaltır; çömelirken yavaş düşersin.",
            "Göğüs yuvasına süreli Elytra takar ve havada hızlanmanı sağlar.",
            "Havada net görünen uzun bir rüzgâr mızrağı fırlatır.",
            "Kavisle düşen görünür gökyüzü çekirdeği geniş hava patlaması oluşturur.",
            "Altı büyük hava mızrağını hedef alana indirip dev basınç kubbesi oluşturur.",
            ""
        },
        {
            "Ateş ve lav hasarına karşı sürekli koruma.",
            "Yakın dövüş vuruşlarına alev ve ek hasar ekler.",
            "Çevrende yakan ve hasar veren geniş bir halka.",
            "Baktığın yöne ilerleyen görünür büyük bir ateş küresi fırlatır.",
            "Çevrene 10 yuvarlak magma meteoru indirir ve kraterler açar.",
            ""
        },
        {
            "Doğal zeminde iyileşir; kritik cana düşünce uzun beklemeli kurtarma tetiklenir.",
            "Görünür dikenli tohumu fırlatır; hedefi zehirleyip kökler.",
            "Bakılan yerde kalın kökler çıkarır; düşmanları sabitler ve ezer.",
            "Büyük geçici ağaç dostları iyileştirir, ek kalp verir ve mermileri engeller.",
            "Dev kök dalgası ilerler; sonunda Kadim Ağaç yükselip geniş alanı parçalar.",
            ""
        },
        {
            "Yakındaki düşman mermilerini güçlü biçimde yavaşlatır ve düşme hasarını sınırlar.",
            "Güçlü zaman mızrağı hedefe ve çevresine hasar verir; düşmanların zamanını ağırlaştırır.",
            "Seni 5-6,5 saniye önceki konumuna ve canına döndürür; kısa süre direnç ve yenilenme verir.",
            "Uzak hedefi saat halkalarında tamamen sabitler; çıkışta zaman kırılması hasarı verir.",
            "Çok geniş alandaki düşmanları dondurup süre boyunca aşındırır; sonunda büyük zaman patlaması yapar.",
            ""
        }
    };

    private PowerCatalog() {}

    public static int maxLevel(PowerClass powerClass) {
        return powerClass == PowerClass.WARDEN ? 6 : 5;
    }

    public static String powerName(PowerClass powerClass, int oneBasedLevel) {
        int classIndex = Math.max(0, Math.min(NAMES.length - 1, powerClass.ordinal()));
        int levelIndex = Math.max(0, Math.min(5, oneBasedLevel - 1));
        return NAMES[classIndex][levelIndex];
    }

    public static String powerDescription(PowerClass powerClass, int oneBasedLevel) {
        int classIndex = Math.max(0, Math.min(DESCRIPTIONS.length - 1, powerClass.ordinal()));
        int levelIndex = Math.max(0, Math.min(5, oneBasedLevel - 1));
        return DESCRIPTIONS[classIndex][levelIndex];
    }

    public static int xpCostForLevel(int level) {
        return xpCostForLevel(PowerClass.NONE, level);
    }

    public static int xpCostForLevel(PowerClass powerClass, int level) {
        if (level < 1 || level > maxLevel(powerClass)) return Integer.MAX_VALUE;
        if (powerClass == PowerClass.WARDEN && level == 6) return WARDEN_ANCIENT_CHARGE_XP;
        int[] costs = switch (powerClass) {
            case NATURE -> NATURE_XP_COSTS;
            case TIME -> TIME_XP_COSTS;
            default -> XP_COSTS;
        };
        return costs[level - 1];
    }

    public static int comboStarterPower(PowerClass powerClass) {
        return switch (powerClass) {
            case WARDEN -> 2;
            case FLIGHT -> 3;
            case FIRE -> 4;
            case NATURE -> 3;
            case TIME -> 4;
            default -> 0;
        };
    }

    public static int comboFinisherPower(PowerClass powerClass) {
        return switch (powerClass) {
            case WARDEN -> 3;
            case FLIGHT -> 4;
            case FIRE -> 5;
            case NATURE -> 2;
            case TIME -> 5;
            default -> 0;
        };
    }

    public static String comboName(PowerClass powerClass) {
        return switch (powerClass) {
            case WARDEN -> "Sonik Fay";
            case FLIGHT -> "Göksel Bombardıman";
            case FIRE -> "Cehennem Felaketi";
            case NATURE -> "Diken Ormanı";
            case TIME -> "Sonsuz Mahkûmiyet";
            default -> "-";
        };
    }

    public static String comboSequence(PowerClass powerClass) {
        int first = comboStarterPower(powerClass);
        int second = comboFinisherPower(powerClass);
        if (first <= 0 || second <= 0) return "-";
        return powerName(powerClass, first) + " → " + powerName(powerClass, second);
    }

    public static boolean isComboStarter(PowerClass powerClass, int power) {
        return comboStarterPower(powerClass) == power;
    }

    public static boolean isComboFinisher(PowerClass powerClass, int power) {
        return comboFinisherPower(powerClass) == power;
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
