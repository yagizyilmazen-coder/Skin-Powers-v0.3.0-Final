package com.yagiz.skinpowers.client;

import com.yagiz.skinpowers.PowerClass;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Güçlere özel piksel-sanat simge, tür etiketi ve kısa vurgu metni kataloğu.
 * Sadece istemci GUI/HUD çizimini zenginleştirir; sunucu mantığına dokunmaz.
 */
public final class PowerIconArt {
    private PowerIconArt() {}

    public enum Archetype {
        ARMOR, SHOCKWAVE, BEAM, TENTACLES, AURA, PILLAR,
        WHIRL, SHIELD_STACK, CLAW, CREATURE,
        BLADE, RING, ORB, RAIN,
        SEAL, WAVE, MIRROR, ECLIPSE,
        GLITCH_STEP, QUESTION, ERROR, VOID_RIFT, ERROR_BLOCK,
        CHAIN, FIST, STORM, CAGE, PROJECTILE, STATUE
    }

    // Sıra: WARDEN, FLIGHT (Kadim Ejderha), FIRE, MOON, ANOMALY, MAGNETIC, SAND — PowerClass ordinal() - 1 ile eşleşir.
    private static final Archetype[][] ARCHETYPES = {
        {Archetype.ARMOR, Archetype.SHOCKWAVE, Archetype.BEAM, Archetype.TENTACLES, Archetype.AURA, Archetype.PILLAR},
        {Archetype.WHIRL, Archetype.BEAM, Archetype.SHIELD_STACK, Archetype.CLAW, Archetype.AURA, Archetype.CREATURE},
        {Archetype.ARMOR, Archetype.BLADE, Archetype.RING, Archetype.ORB, Archetype.RAIN, Archetype.BEAM},
        {Archetype.RING, Archetype.SEAL, Archetype.WAVE, Archetype.MIRROR, Archetype.ECLIPSE, Archetype.CREATURE},
        {Archetype.GLITCH_STEP, Archetype.MIRROR, Archetype.QUESTION, Archetype.ERROR, Archetype.VOID_RIFT, Archetype.ERROR_BLOCK},
        {Archetype.CHAIN, Archetype.RING, Archetype.FIST, Archetype.STORM, Archetype.BEAM, Archetype.CAGE},
        {Archetype.PROJECTILE, Archetype.WAVE, Archetype.STATUE, Archetype.ARMOR, Archetype.CAGE, Archetype.CREATURE}
    };

    private static final String[][] TAGS = {
        {"SAVUNMA", "ALAN", "SALDIRI", "GİZLİLİK", "GÜÇLENME", "ULTRA"},
        {"ALAN", "SALDIRI", "SAVUNMA", "YAKALAMA", "ALAN", "ULTRA"},
        {"SAVUNMA", "YAKIN DÖVÜŞ", "ALAN", "MERMİ", "ALAN", "ULTRA"},
        {"SALDIRI", "TUZAK", "ALAN", "SAVUNMA", "ALAN", "ULTRA"},
        {"HAREKET", "KONTROL", "ÇALMA", "DEPOLAMA", "SÜRGÜN", "ULTRA"},
        {"ÇEKME", "İTME", "SALDIRI", "MERMİ", "SALDIRI", "TUZAK"},
        {"MERMİ", "ALAN", "SAVUNMA", "SAVUNMA", "TUZAK", "ULTRA"}
    };

    private static final String[][] FLAVOR = {
        {
            "Derinliklerin zırhı bedenini sarar.",
            "Zemin çöker, düşman diz çöker.",
            "Karanlığın sesi hedefi delip geçer.",
            "Görünmez avcı gölgede bekler.",
            "Kadim güç damarlarında uyanır.",
            "Antik Şehir'in tüm gücü sende."
        },
        {
            "Mor kasırga her şeyi savurur.",
            "Nefesin alevden değil, güçten.",
            "Pullar her darbeyi geri çevirir.",
            "Pençe hedefi asla bırakmaz.",
            "Kükreme rakip güçleri susturur.",
            "Ejderha formunda gökler senindir."
        },
        {
            "Alevler artık sana zarar veremez.",
            "Yumruklarında cehennem ateşi yanar.",
            "Etrafın yanan bir kaleye dönüşür.",
            "Ateş küresi yolundaki her şeyi yakar.",
            "Gökten magma yağmuru iner.",
            "Cehennemin ışını hiçbir şeyi esirgemez."
        },
        {
            "Beyaz hilal sessizce geri döner.",
            "Ay mührü rakibi merkeze kilitler.",
            "Yerçekimi düşmanı toprağa çiviler.",
            "Ayna mermiyi de, ışığı da yansıtır.",
            "Tutulma altında sen, gölgede onlar.",
            "Dolunayın canavarı gökten iner."
        },
        {
            "Gerçeklik kırılır, sen ileri sıçrarsın.",
            "Kader tersine döner, hasar geri gelir.",
            "Bilinmeyen güç sessizce çalınır.",
            "Hasar birikir; ne zaman patlayacağına sen karar verirsin.",
            "Var olmak seçime bağlı hale gelir.",
            "Sistem çöker, 404: gerçeklik bulunamadı."
        },
        {
            "Zincir hedefi sana doğru çeker.",
            "Kutup gücü herkesi iter.",
            "Demir yumruk yolunda durulmaz.",
            "Metal fırtınası havada hazır bekler.",
            "Ray topu düşman sırasını deler.",
            "Kafes kapanır, kaçış yok."
        },
        {
            "Kum taneleri hedefi gözünden vurur.",
            "Çölün dalgası her şeyi sürükler.",
            "Heykeller senin yerine darbe alır.",
            "Kum zırhı darbe aldıkça kırılır.",
            "Kumtaşı duvarları hedefi gömer.",
            "Devler seni yere çakar."
        }
    };

    public static Archetype archetype(PowerClass powerClass, int oneBasedLevel) {
        return ARCHETYPES[classIndex(powerClass)][levelIndex(oneBasedLevel)];
    }

    public static String tag(PowerClass powerClass, int oneBasedLevel) {
        return TAGS[classIndex(powerClass)][levelIndex(oneBasedLevel)];
    }

    public static String flavor(PowerClass powerClass, int oneBasedLevel) {
        return FLAVOR[classIndex(powerClass)][levelIndex(oneBasedLevel)];
    }

    private static int classIndex(PowerClass powerClass) {
        int idx = powerClass.ordinal() - 1;
        return Math.max(0, Math.min(ARCHETYPES.length - 1, idx));
    }

    private static int levelIndex(int oneBasedLevel) {
        return Math.max(0, Math.min(5, oneBasedLevel - 1));
    }

    /** Sınıf rengini seviyeye göre hafifçe aydınlatarak her güce kendine has bir ton verir. */
    public static int shade(int base, int oneBasedLevel) {
        float t = levelIndex(oneBasedLevel) / 5.0F;
        int a = (base >>> 24) & 0xFF;
        int r = mixToward((base >> 16) & 0xFF, 255, t * 0.38F);
        int g = mixToward((base >> 8) & 0xFF, 255, t * 0.30F);
        int b = mixToward(base & 0xFF, 255, t * 0.22F);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int mixToward(int channel, int target, float amount) {
        int mixed = Math.round(channel + (target - channel) * amount);
        return Math.max(0, Math.min(255, mixed));
    }

    private static int mixBlack(int color, float amount) {
        int r = mixToward((color >> 16) & 0xFF, 0, amount);
        int g = mixToward((color >> 8) & 0xFF, 0, amount);
        int b = mixToward(color & 0xFF, 0, amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    /** Güce özgü küçük piksel-sanat simgeyi (x,y) noktasından başlayarak size x size kutuya çizer. */
    public static void draw(GuiGraphicsExtractor g, PowerClass powerClass, int oneBasedLevel, int x, int y, int size, int accent) {
        Archetype archetype = archetype(powerClass, oneBasedLevel);
        float s = size / 20.0F;
        int dark = mixBlack(accent, 0.55F);
        int light = withAlpha(0xFFFFFFFF, 235);
        switch (archetype) {
            case ARMOR -> {
                r(g, x, y, s, 5, 3, 10, 15, withAlpha(accent, 210));
                r(g, x, y, s, 5, 3, 10, 4, withAlpha(accent, 255));
                r(g, x, y, s, 8, 7, 4, 8, dark);
            }
            case SHOCKWAVE -> {
                r(g, x, y, s, 8, 15, 4, 3, dark);
                r(g, x, y, s, 2, 12, 16, 2, withAlpha(accent, 220));
                r(g, x, y, s, 5, 9, 10, 2, withAlpha(accent, 165));
                r(g, x, y, s, 8, 6, 4, 2, withAlpha(accent, 110));
            }
            case BEAM -> {
                r(g, x, y, s, 1, 9, 18, 3, withAlpha(accent, 235));
                r(g, x, y, s, 1, 8, 5, 5, light);
                r(g, x, y, s, 14, 8, 5, 5, withAlpha(accent, 180));
            }
            case TENTACLES -> {
                r(g, x, y, s, 9, 2, 2, 16, dark);
                r(g, x, y, s, 2, 4, 3, 12, withAlpha(accent, 210));
                r(g, x, y, s, 15, 4, 3, 12, withAlpha(accent, 210));
                r(g, x, y, s, 5, 14, 3, 4, withAlpha(accent, 210));
                r(g, x, y, s, 12, 14, 3, 4, withAlpha(accent, 210));
            }
            case AURA -> {
                r(g, x, y, s, 7, 7, 6, 6, light);
                r(g, x, y, s, 2, 9, 3, 2, withAlpha(accent, 220));
                r(g, x, y, s, 15, 9, 3, 2, withAlpha(accent, 220));
                r(g, x, y, s, 9, 2, 2, 3, withAlpha(accent, 220));
                r(g, x, y, s, 9, 15, 2, 3, withAlpha(accent, 220));
                r(g, x, y, s, 4, 4, 2, 2, withAlpha(accent, 180));
                r(g, x, y, s, 14, 4, 2, 2, withAlpha(accent, 180));
                r(g, x, y, s, 4, 14, 2, 2, withAlpha(accent, 180));
                r(g, x, y, s, 14, 14, 2, 2, withAlpha(accent, 180));
            }
            case PILLAR -> {
                r(g, x, y, s, 8, 2, 4, 16, withAlpha(accent, 235));
                r(g, x, y, s, 5, 16, 10, 3, dark);
                r(g, x, y, s, 7, 0, 6, 4, light);
            }
            case WHIRL -> {
                r(g, x, y, s, 2, 9, 16, 2, withAlpha(accent, 220));
                r(g, x, y, s, 4, 4, 2, 12, withAlpha(accent, 150));
                r(g, x, y, s, 14, 4, 2, 12, withAlpha(accent, 150));
                r(g, x, y, s, 8, 8, 4, 4, light);
            }
            case SHIELD_STACK -> {
                r(g, x, y, s, 4, 3, 12, 6, withAlpha(accent, 235));
                r(g, x, y, s, 4, 10, 12, 6, withAlpha(accent, 170));
                r(g, x, y, s, 8, 5, 4, 2, light);
                r(g, x, y, s, 8, 12, 4, 2, light);
            }
            case CLAW -> {
                r(g, x, y, s, 3, 2, 3, 12, withAlpha(accent, 230));
                r(g, x, y, s, 9, 1, 3, 13, withAlpha(accent, 230));
                r(g, x, y, s, 15, 2, 3, 12, withAlpha(accent, 230));
                r(g, x, y, s, 2, 13, 17, 3, dark);
            }
            case CREATURE -> {
                r(g, x, y, s, 4, 8, 12, 8, withAlpha(accent, 230));
                r(g, x, y, s, 0, 5, 5, 6, withAlpha(accent, 190));
                r(g, x, y, s, 15, 5, 5, 6, withAlpha(accent, 190));
                r(g, x, y, s, 7, 3, 6, 6, dark);
                r(g, x, y, s, 8, 5, 2, 2, light);
                r(g, x, y, s, 11, 5, 2, 2, light);
            }
            case BLADE -> {
                r(g, x, y, s, 9, 1, 2, 12, light);
                r(g, x, y, s, 6, 13, 8, 3, withAlpha(accent, 235));
                r(g, x, y, s, 8, 16, 4, 3, dark);
            }
            case RING -> {
                r(g, x, y, s, 3, 8, 14, 4, withAlpha(accent, 230));
                r(g, x, y, s, 6, 5, 8, 3, withAlpha(accent, 150));
                r(g, x, y, s, 6, 12, 8, 3, withAlpha(accent, 150));
                r(g, x, y, s, 8, 9, 4, 2, dark);
            }
            case ORB -> {
                r(g, x, y, s, 5, 5, 10, 10, withAlpha(accent, 235));
                r(g, x, y, s, 7, 7, 4, 4, light);
                r(g, x, y, s, 0, 9, 3, 2, withAlpha(accent, 150));
                r(g, x, y, s, 17, 9, 3, 2, withAlpha(accent, 150));
            }
            case RAIN -> {
                r(g, x, y, s, 3, 2, 2, 6, withAlpha(accent, 230));
                r(g, x, y, s, 9, 0, 2, 8, withAlpha(accent, 230));
                r(g, x, y, s, 15, 3, 2, 6, withAlpha(accent, 230));
                r(g, x, y, s, 1, 14, 5, 4, dark);
                r(g, x, y, s, 8, 15, 5, 4, dark);
                r(g, x, y, s, 14, 14, 5, 4, dark);
            }
            case SEAL -> {
                r(g, x, y, s, 4, 4, 12, 12, withAlpha(accent, 60));
                r(g, x, y, s, 4, 4, 12, 2, withAlpha(accent, 230));
                r(g, x, y, s, 4, 14, 12, 2, withAlpha(accent, 230));
                r(g, x, y, s, 4, 4, 2, 12, withAlpha(accent, 230));
                r(g, x, y, s, 14, 4, 2, 12, withAlpha(accent, 230));
                r(g, x, y, s, 9, 8, 2, 4, light);
            }
            case WAVE -> {
                r(g, x, y, s, 1, 12, 4, 4, withAlpha(accent, 210));
                r(g, x, y, s, 5, 9, 4, 4, withAlpha(accent, 230));
                r(g, x, y, s, 9, 12, 4, 4, withAlpha(accent, 210));
                r(g, x, y, s, 13, 9, 4, 4, withAlpha(accent, 230));
                r(g, x, y, s, 17, 12, 3, 4, withAlpha(accent, 210));
            }
            case MIRROR -> {
                r(g, x, y, s, 6, 2, 8, 16, withAlpha(accent, 100));
                r(g, x, y, s, 7, 3, 3, 14, light);
                r(g, x, y, s, 10, 3, 3, 14, withAlpha(accent, 220));
                r(g, x, y, s, 6, 2, 8, 2, dark);
            }
            case ECLIPSE -> {
                r(g, x, y, s, 3, 3, 12, 12, light);
                r(g, x, y, s, 7, 3, 12, 12, dark);
            }
            case GLITCH_STEP -> {
                r(g, x, y, s, 3, 4, 6, 12, withAlpha(accent, 235));
                r(g, x, y, s, 9, 2, 6, 12, withAlpha(accent, 110));
                r(g, x, y, s, 5, 15, 4, 2, dark);
            }
            case QUESTION -> {
                r(g, x, y, s, 6, 3, 8, 3, withAlpha(accent, 235));
                r(g, x, y, s, 12, 6, 3, 4, withAlpha(accent, 235));
                r(g, x, y, s, 8, 10, 3, 3, withAlpha(accent, 235));
                r(g, x, y, s, 8, 15, 3, 3, light);
            }
            case ERROR -> {
                r(g, x, y, s, 4, 4, 3, 12, withAlpha(0xFFE94B63, 235));
                r(g, x, y, s, 13, 4, 3, 12, withAlpha(0xFFE94B63, 235));
                r(g, x, y, s, 8, 8, 4, 4, dark);
            }
            case VOID_RIFT -> {
                r(g, x, y, s, 5, 5, 10, 10, 0xFF0A0210);
                r(g, x, y, s, 5, 5, 10, 2, withAlpha(accent, 220));
                r(g, x, y, s, 5, 13, 10, 2, withAlpha(accent, 220));
                r(g, x, y, s, 9, 9, 2, 2, light);
            }
            case ERROR_BLOCK -> {
                r(g, x, y, s, 2, 5, 16, 10, dark);
                r(g, x, y, s, 3, 6, 5, 3, withAlpha(accent, 235));
                r(g, x, y, s, 9, 6, 3, 3, withAlpha(accent, 235));
                r(g, x, y, s, 13, 6, 4, 3, withAlpha(accent, 235));
                r(g, x, y, s, 3, 11, 14, 2, withAlpha(0xFFE94B63, 200));
            }
            case CHAIN -> {
                r(g, x, y, s, 2, 2, 5, 5, withAlpha(accent, 220));
                r(g, x, y, s, 8, 8, 5, 5, withAlpha(accent, 220));
                r(g, x, y, s, 14, 14, 5, 5, withAlpha(accent, 235));
                r(g, x, y, s, 6, 6, 3, 3, dark);
                r(g, x, y, s, 12, 12, 3, 3, dark);
            }
            case FIST -> {
                r(g, x, y, s, 4, 6, 12, 10, withAlpha(accent, 235));
                r(g, x, y, s, 4, 2, 4, 6, withAlpha(accent, 200));
                r(g, x, y, s, 9, 1, 4, 7, withAlpha(accent, 200));
                r(g, x, y, s, 14, 3, 4, 6, withAlpha(accent, 200));
            }
            case STORM -> {
                r(g, x, y, s, 8, 8, 4, 4, light);
                r(g, x, y, s, 9, 1, 3, 3, withAlpha(accent, 220));
                r(g, x, y, s, 16, 9, 3, 3, withAlpha(accent, 220));
                r(g, x, y, s, 9, 16, 3, 3, withAlpha(accent, 220));
                r(g, x, y, s, 1, 9, 3, 3, withAlpha(accent, 220));
            }
            case CAGE -> {
                r(g, x, y, s, 2, 3, 2, 14, withAlpha(accent, 225));
                r(g, x, y, s, 6.5F, 3, 2, 14, withAlpha(accent, 225));
                r(g, x, y, s, 11, 3, 2, 14, withAlpha(accent, 225));
                r(g, x, y, s, 15.5F, 3, 2, 14, withAlpha(accent, 225));
                r(g, x, y, s, 2, 3, 16, 2, dark);
                r(g, x, y, s, 2, 15, 16, 2, dark);
            }
            case PROJECTILE -> {
                r(g, x, y, s, 7, 8, 8, 4, withAlpha(accent, 235));
                r(g, x, y, s, 3, 9, 4, 2, light);
                r(g, x, y, s, 15, 6, 2, 2, withAlpha(accent, 150));
                r(g, x, y, s, 15, 12, 2, 2, withAlpha(accent, 150));
            }
            case STATUE -> {
                r(g, x, y, s, 5, 2, 4, 14, withAlpha(accent, 210));
                r(g, x, y, s, 11, 2, 4, 14, withAlpha(accent, 210));
                r(g, x, y, s, 3, 16, 14, 3, dark);
                r(g, x, y, s, 6, 4, 2, 2, light);
                r(g, x, y, s, 12, 4, 2, 2, light);
            }
        }
    }

    private static void r(GuiGraphicsExtractor g, int left, int top, float s, float gx, float gy, float gw, float gh, int color) {
        int x1 = left + Math.round(gx * s);
        int y1 = top + Math.round(gy * s);
        int x2 = left + Math.round((gx + gw) * s);
        int y2 = top + Math.round((gy + gh) * s);
        if (x2 <= x1) x2 = x1 + 1;
        if (y2 <= y1) y2 = y1 + 1;
        g.fill(x1, y1, x2, y2, color);
    }
}
