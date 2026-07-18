package com.yagiz.skinpowers;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ClassEnchantments {
    public static final ResourceKey<Enchantment> ECHO_STRIKE = key("yanki_darbesi");
    public static final ResourceKey<Enchantment> DEPTH_STEP = key("derinlik_adimi");
    public static final ResourceKey<Enchantment> SCULK_ARMOR = key("sculk_zirhi");
    public static final ResourceKey<Enchantment> ANCIENT_COLLAPSE = key("antik_cokus");

    public static final ResourceKey<Enchantment> EMBER_BUILDUP = key("kor_birikimi");
    public static final ResourceKey<Enchantment> ASH_WALK = key("kul_yuruyusu");
    public static final ResourceKey<Enchantment> HELL_CORE = key("cehennem_cekirdegi");
    public static final ResourceKey<Enchantment> METEOR_FALL = key("meteor_dususu");

    public static final ResourceKey<Enchantment> ROOT_BIND = key("kok_bagi");
    public static final ResourceKey<Enchantment> LIFE_SPROUT = key("can_filizi");
    public static final ResourceKey<Enchantment> FOREST_LEAP = key("orman_sicrayisi");
    public static final ResourceKey<Enchantment> THORNY_DEFENSE = key("dikenli_savunma");

    public static final ResourceKey<Enchantment> DELAYED_STRIKE = key("gecikmis_darbe");
    public static final ResourceKey<Enchantment> PHASE_SHIFT = key("faz_kaymasi");
    public static final ResourceKey<Enchantment> ERROR_MARGIN = key("hata_payi");
    public static final ResourceKey<Enchantment> BROKEN_TRAJECTORY = key("bozuk_yorunge");

    public static final ResourceKey<Enchantment> DRAGON_CLAW = key("ejderha_pencesi");
    public static final ResourceKey<Enchantment> PURPLE_WING = key("mor_kanat");
    public static final ResourceKey<Enchantment> ANCIENT_SCALES = key("kadim_pullar");
    public static final ResourceKey<Enchantment> PURPLE_BREATH = key("mor_nefes");

    private static final Map<String, ResourceKey<Enchantment>> BY_COMMAND = new LinkedHashMap<>();

    static {
        add("yanki_darbesi", ECHO_STRIKE);
        add("derinlik_adimi", DEPTH_STEP);
        add("sculk_zirhi", SCULK_ARMOR);
        add("antik_cokus", ANCIENT_COLLAPSE);
        add("kor_birikimi", EMBER_BUILDUP);
        add("kul_yuruyusu", ASH_WALK);
        add("cehennem_cekirdegi", HELL_CORE);
        add("meteor_dususu", METEOR_FALL);
        add("kok_bagi", ROOT_BIND);
        add("can_filizi", LIFE_SPROUT);
        add("orman_sicrayisi", FOREST_LEAP);
        add("dikenli_savunma", THORNY_DEFENSE);
        add("gecikmis_darbe", DELAYED_STRIKE);
        add("faz_kaymasi", PHASE_SHIFT);
        add("hata_payi", ERROR_MARGIN);
        add("bozuk_yorunge", BROKEN_TRAJECTORY);
        add("ejderha_pencesi", DRAGON_CLAW);
        add("mor_kanat", PURPLE_WING);
        add("kadim_pullar", ANCIENT_SCALES);
        add("mor_nefes", PURPLE_BREATH);
    }

    private ClassEnchantments() {}

    private static ResourceKey<Enchantment> key(String path) {
        return ResourceKey.create(Registries.ENCHANTMENT, SkinPowersMod.id(path));
    }

    private static void add(String name, ResourceKey<Enchantment> key) {
        BY_COMMAND.put(name, key);
    }

    public static Map<String, ResourceKey<Enchantment>> commandEntries() {
        return Map.copyOf(BY_COMMAND);
    }

    public static ResourceKey<Enchantment> byCommand(String name) {
        if (name == null) return null;
        return BY_COMMAND.get(name.trim().toLowerCase(Locale.ROOT));
    }

    public static Holder<Enchantment> holder(RegistryAccess access, ResourceKey<Enchantment> key) {
        return access.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    public static boolean has(RegistryAccess access, ItemStack stack, ResourceKey<Enchantment> key) {
        if (stack == null || stack.isEmpty()) return false;
        return EnchantmentHelper.getItemEnchantmentLevel(holder(access, key), stack) > 0;
    }

    public static ItemStack createBook(RegistryAccess access, ResourceKey<Enchantment> key) {
        return EnchantmentHelper.createBook(new EnchantmentInstance(holder(access, key), 1));
    }
}
