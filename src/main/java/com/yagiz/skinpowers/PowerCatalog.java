package com.yagiz.skinpowers;

public final class PowerCatalog {
    public static final int[] XP_COSTS = {5, 15, 30, 40, 50};
    public static final int[] NATURE_XP_COSTS = {10, 20, 30, 40, 50};
    public static final int[] DRAGON_XP_COSTS = {10, 20, 30, 40, 55, 70};
    public static final int[] ANOMALY_XP_COSTS = {10, 20, 30, 40, 50, 70};
    public static final int WARDEN_ANCIENT_CHARGE_XP = 70;

    private static final String[][] NAMES = {
        {"-", "-", "-", "-", "-", "-"},
        {"Warden Zırhı", "Yer Sarsıntısı", "Sonik Patlama", "Sculk Avı", "Warden Uyanışı", "Şarj Et Beni Antik Şehir"},
        {"Ejderha Atılışı", "Ejderha Nefesi", "Kadim Pullar", "Avcı Pençesi", "Kadim Kükreme", "Ejderha Hükümdarı"},
        {"Ateş Bağışıklığı", "Alevli Yakın Dövüş", "Ateş Çemberi", "Cehennem Küresi", "Meteor Yağmuru", "-"},
        {"Doğanın Canı", "Dikenli Tohum", "Sarmaşık Kapanı", "Yaşam Ağacı", "Kadim Orman Hükmü", "-"},
        {"Kırık Adım", "Tersine Çevir", "?", "Hasar Mevcut Değil", "Varlıktan Çıkar", "404: Gerçeklik Bulunamadı"}
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
            "Yerde veya havada 12-15 blok ileri atılır; yolundakileri mor kanat enerjisiyle savurur.",
            "Geniş bir konide blok yakmayan mor ejderha enerjisi üfler ve hedefleri zayıflatır.",
            "Kadim mor pullar hasarı azaltır, savrulmayı önler ve yakındaki mermileri geri sektirir.",
            "Hedefi mor enerji pençesiyle yakalar; ikinci kullanımda baktığın yöne fırlatır.",
            "Geniş kükreme dalgası düşmanları savurur, mermileri bozar ve oyuncuların güçlerini kısa süre susturur.",
            "Mor enerji kanatları açar; uçuş, güç, hız ve güçlendirilmiş Ejderha saldırıları kazandırır."
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
            "Baktığın yöne kırılarak sıçrar; yolundaki düşmanların içinden geçip hasar verir.",
            "Nişangâhtaki hedefin hareketini ve saldırı yönünü kısa süre tersine çevirir.",
            "Yakındaki rakibin son kopyalanabilir gücünü bir kez kullanmak üzere saklar.",
            "5 saniye boyunca alınan hasarı yok sayıp depolar; sonra V ile kalbe, X ile saldırıya dönüştürür.",
            "Nişangâhtaki hedefi kısa süre gerçeklikten siler; geri geldiğinde savunması kırılır.",
            "Geniş bir 404 alanı açar; düşmanları bozar, mermileri ters çevirir ve seni bir kez ölümden döndürür."
        }
    };

    private PowerCatalog() {}

    public static int maxLevel(PowerClass powerClass) {
        return powerClass == PowerClass.WARDEN || powerClass == PowerClass.FLIGHT || powerClass == PowerClass.ANOMALY ? 6 : 5;
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

    public static int xpCostForLevel(int level) { return xpCostForLevel(PowerClass.NONE, level); }

    public static int xpCostForLevel(PowerClass powerClass, int level) {
        if (level < 1 || level > maxLevel(powerClass)) return Integer.MAX_VALUE;
        if (powerClass == PowerClass.WARDEN && level == 6) return WARDEN_ANCIENT_CHARGE_XP;
        if (powerClass == PowerClass.ANOMALY) return ANOMALY_XP_COSTS[level - 1];
        if (powerClass == PowerClass.FLIGHT) return DRAGON_XP_COSTS[level - 1];
        int[] costs = powerClass == PowerClass.NATURE ? NATURE_XP_COSTS : XP_COSTS;
        return costs[level - 1];
    }

    public static int comboStarterPower(PowerClass powerClass) {
        return switch (powerClass) {
            case WARDEN -> 2;
            case FLIGHT -> 2;
            case FIRE -> 4;
            case NATURE -> 3;
            default -> 0;
        };
    }

    public static int comboFinisherPower(PowerClass powerClass) {
        return switch (powerClass) {
            case WARDEN -> 3;
            case FLIGHT -> 5;
            case FIRE -> 5;
            case NATURE -> 2;
            default -> 0;
        };
    }

    public static String comboName(PowerClass powerClass) {
        return switch (powerClass) {
            case WARDEN -> "Sonik Fay";
            case FLIGHT -> "Mor Ejderha Fırtınası";
            case FIRE -> "Cehennem Felaketi";
            case NATURE -> "Diken Ormanı";
            default -> "-";
        };
    }

    public static String comboSequence(PowerClass powerClass) {
        int first = comboStarterPower(powerClass), second = comboFinisherPower(powerClass);
        if (first <= 0 || second <= 0) return "-";
        return powerName(powerClass, first) + " → " + powerName(powerClass, second);
    }

    public static boolean isComboStarter(PowerClass powerClass, int power) { return comboStarterPower(powerClass) == power; }
    public static boolean isComboFinisher(PowerClass powerClass, int power) { return comboFinisherPower(powerClass) == power; }

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
        return 30;
    }

    public static float masteryProgress(int uses) {
        if (uses >= 30) return 1.0F;
        int start = uses < 5 ? 0 : (uses < 15 ? 5 : 15);
        int end = uses < 5 ? 5 : (uses < 15 ? 15 : 30);
        return Math.max(0.0F, Math.min(1.0F, (uses - start) / (float) (end - start)));
    }
}
