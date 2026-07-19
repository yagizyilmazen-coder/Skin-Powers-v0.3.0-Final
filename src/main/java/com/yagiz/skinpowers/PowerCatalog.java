package com.yagiz.skinpowers;

public final class PowerCatalog {
    public static final int[] XP_COSTS = {5, 15, 30, 40, 50};
    public static final int[] DRAGON_XP_COSTS = {10, 20, 30, 40, 55, 70};
    public static final int[] ANOMALY_XP_COSTS = {10, 20, 30, 40, 50, 70};
    public static final int[] EXPANSION_XP_COSTS = {10, 20, 30, 40, 55, 70};
    public static final int WARDEN_ANCIENT_CHARGE_XP = 70;

    private static final String[][] NAMES = {
        {"-", "-", "-", "-", "-", "-"},
        {"Warden Zırhı", "Yer Sarsıntısı", "Sonik Patlama", "Derinlik Pususu", "Warden Uyanışı", "Şarj Et Beni Antik Şehir"},
        {"Kuyruk Kasırgası", "Ejderha Nefesi", "Kadim Pullar", "Avcı Pençesi", "Kadim Kükreme", "Ejderha Hükümdarı"},
        {"Ateş Bağışıklığı", "Alevli Yakın Dövüş", "Ateş Çemberi", "Cehennem Küresi", "Meteor Yağmuru", "-"},
        {"Hilal Kesik", "Ay Adımı", "Yerçekimi Baskısı", "Ay Aynası", "Tutulma Alanı", "Dolunay Canavarı"},
        {"Kırık Adım", "Tersine Çevir", "?", "Hasar Mevcut Değil", "Varlıktan Çıkar", "404: Gerçeklik Bulunamadı"},
        {"Manyetik Çekim", "Kutup İtişi", "Demir Yumruk", "Metal Fırtınası", "Ray Topu", "Manyetik Kafes"},
        {"Kum Mermisi", "Kum Dalgası", "Çöl Aynası", "Kum Zırhı", "Kum Mezarı", "Kum Devleri"}
    };

    private static final String[][] DESCRIPTIONS = {
        {"", "", "", "", "", ""},
        {
            "Uzun süre Güç, Direnç ve soğurma kazanırsın.",
            "Geniş alandaki düşmanları ezer, savurur ve zayıflatır.",
            "Önündeki hedeflere yüksek hasarlı sonik enerji yollar.",
            "Yer altına çekilirsin; karakterin, zırhın ve eldeki eşyaların tamamen kaybolur. Tekrar kullandığında dört gerçek 3B sculk koluyla yüzeye saldırırsın.",
            "Güç, direnç, yenilenme ve hasar aurası kazandırır.",
            "Dört sculk kolu hedefe Antik Şehir enerjisi aktarır; çömelerek kendine uygulayabilirsin."
        },
        {
            "Etrafında tam tur dönen dev mor kuyruk oluşturur; yakındaki düşmanları ezer ve uzağa savurur.",
            "Birkaç saniye boyunca baktığın yöne yönlendirilebilen mor ejderha nefesi püskürtür.",
            "Kadim pul yükleri kazanırsın; her pul bir saldırıyı tamamen engeller ve saldırganı geri vurur.",
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
            "Gidip geri dönen büyük, görünür bir hilal fırlatır; dönüşte yeniden vurabilir.",
            "Güvenli bir noktaya ay ışığıyla sıçrar; geride patlayan bir ay görüntüsü bırakır.",
            "Bir alanın yerçekimini artırır; düşmanları yere bastırır ve mermileri aşağı büker.",
            "Dönen ay diski mermileri yansıtır; tekrar kullanılırsa güçlü hilal olarak fırlatılır.",
            "Geçici tutulma alanında sen hızlanır, rakipler zayıflar ve Ay güçleri büyür.",
            "Büyük görünür ay yaratığı iki pençe darbesi ve güçlü bir yere çarpma saldırısı yapar."
        },
        {
            "Koşarken veya havada gerçekliği yararak ileri sıçrar; yolundakilere vurur ve gecikmeli patlayan bozuk kopyalar bırakır.",
            "Nişangâhtaki hedefin hareketini, mermilerini ve verdiği hasarın bir bölümünü kısa süre kendi üzerine çevirir.",
            "Yakındaki rakibin son uygun aktif gücünü saklar; Sistem Çökmesi sırasında kopya iki kez kullanılabilir.",
            "Moblar, oyuncular, mermiler ve patlamalar dâhil gelen hasarı depolar; V ile geçici kırmızı kalbe, X ile tam hasara dönüştürür.",
            "Hedefi kısa süre gerçeklikten siler; geri döndüğünde merkezde çöken bir bozulma patlaması oluşturur.",
            "Saldırıları ve mermileri reddeden geniş bir alan açar; hasarı geri yollar, cooldownları hızlandırır ve ölümü bir kez iptal eder."
        },
        {
            "Nişangâhtaki hedefi demir ve bakırdan oluşan görünür manyetik zincirle kendine çeker; metal zırh daha güçlü etkilenir.",
            "Gerçek demir blok halkaları dışarı açılır; düşmanları iter ve mermileri sahibine geri çevirir.",
            "Demir blok ve örsten oluşan dev bir yumruk ileri uçar, hedefi ezer ve çevresini savurur.",
            "Etrafında dönen gerçek metal parçaları hazırlar; tekrar R ile nişangâha toplu hâlde fırlatılır.",
            "Demir çekirdekli yüksek hızlı bir ray mermisi birden fazla hedefi delerek geçer.",
            "Hedefi hareket eden demir parmaklık ve zincir halkalarına kapatır; süre sonunda kafes çöker."
        },
        {
            "Gerçek kum ve kumtaşı parçalarından oluşan sıkıştırılmış mermi fırlatır; patlayınca hedefin ekranını 4 saniye kum kaplar.",
            "Minecraft kum ve kumtaşı blok gövdelerinden oluşan geniş dalga ilerler; hedefleri sürükler ve görüşlerini kumla kaplar.",
            "İki gerçek kumtaşı heykeli oluşturur; gelen ilk saldırıları heykellere aktararak kaçış sağlar.",
            "Oyuncunun çevresinde dönen gerçek kum ve kumtaşı kabuğu darbe aldıkça parça parça kırılır.",
            "Hedefin çevresinde kumtaşı duvarları yükselir; hedef suya girene veya süre bitene kadar gömülür.",
            "Kum ve kumtaşından oluşan iki dev kol hedefi yakalar, havaya kaldırır ve yere çarpar."
        }
    };

    private PowerCatalog() {}

    public static int maxLevel(PowerClass powerClass) {
        return powerClass == PowerClass.WARDEN || powerClass == PowerClass.FLIGHT || powerClass == PowerClass.MOON
            || powerClass == PowerClass.ANOMALY || powerClass == PowerClass.MAGNETIC || powerClass == PowerClass.SAND ? 6 : 5;
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
        if (powerClass == PowerClass.MAGNETIC || powerClass == PowerClass.SAND) return EXPANSION_XP_COSTS[level - 1];
        if (powerClass == PowerClass.MOON) return EXPANSION_XP_COSTS[level - 1];
        return XP_COSTS[level - 1];
    }

    public static int comboStarterPower(PowerClass powerClass) {
        return switch (powerClass) {
            case WARDEN -> 2;
            case FLIGHT -> 2;
            case FIRE -> 4;
            case MOON -> 3;
            case MAGNETIC -> 4;
            case SAND -> 2;
            default -> 0;
        };
    }

    public static int comboFinisherPower(PowerClass powerClass) {
        return switch (powerClass) {
            case WARDEN -> 3;
            case FLIGHT -> 5;
            case FIRE -> 5;
            case MOON -> 1;
            case MAGNETIC -> 5;
            case SAND -> 6;
            default -> 0;
        };
    }

    public static String comboName(PowerClass powerClass) {
        return switch (powerClass) {
            case WARDEN -> "Sonik Fay";
            case FLIGHT -> "Mor Ejderha Fırtınası";
            case FIRE -> "Cehennem Felaketi";
            case MOON -> "Tutulma Hükmü";
            case MAGNETIC -> "Kutup Kıyameti";
            case SAND -> "Çöl Ezicisi";
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
