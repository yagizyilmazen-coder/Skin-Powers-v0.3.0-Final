package com.yagiz.skinpowers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vampir sınıfı (eski Kum yerine).
 * Çalınan can max HP'ye eklenir (en fazla +10 kalp = +20 HP). Ölünce sıfırlanır.
 * Kurbandan çalınan her kalp → bulantı + yavaşlık; 5+ kalp → zıplama engeli.
 */
public final class VampirePowerSystem {
    public static final float MAX_STOLEN_HEARTS = 10.0F; // +10 kalp
    private static final float HP_PER_HEART = 2.0F;

    private VampirePowerSystem() {}

    private static final Map<UUID, BloodBond> BONDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> FRENZY_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> BLOOD_ARMOR = new ConcurrentHashMap<>();
    /** Kurban: bu oturumda vampir(ler)den çalınan kalp toplamı (ölünce silinir). */
    private static final Map<UUID, Float> VICTIM_DRAINED_HEARTS = new ConcurrentHashMap<>();

    private record BloodBond(UUID targetId, long expireAt, int stage) {}

    public static void tickServer(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        BONDS.entrySet().removeIf(e -> e.getValue().expireAt <= now);
        FRENZY_UNTIL.entrySet().removeIf(e -> e.getValue() <= now);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            applyVictimJumpLock(player);
            // Kan bağı: periyodik küçük çalma
            BloodBond bond = BONDS.get(player.getUUID());
            if (bond != null && bond.expireAt > now && now % 20L == 0L) {
                var entity = player.level().getEntity(bond.targetId);
                if (entity instanceof LivingEntity living && living.isAlive()) {
                    float steal = 0.25F + bond.stage * 0.05F; // kalp
                    performLifeSteal(player, living, steal, 1.5F + bond.stage * 0.3F, false);
                }
            }
        }
    }

    public static void tickPlayer(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        applyStolenHealthAttribute(player, data);
        Long frenzy = FRENZY_UNTIL.get(player.getUUID());
        if (frenzy != null && frenzy > now) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 25, 0, true, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 25, 0, true, false, false));
        }
        Float armor = BLOOD_ARMOR.get(player.getUUID());
        if (armor != null && armor > 0.05F) {
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.3, 0.4, 0.3, 0.0);
        }
    }

    public static boolean use(ServerPlayer player, PlayerPowerData data, int power, long now, boolean charged) {
        int stage = data.masteryStage(power);
        boolean boosted = charged || data.classAwakeningActive(now);
        ServerLevel level = (ServerLevel) player.level();
        return switch (power) {
            case 1 -> useBite(player, data, level, stage, now, boosted);
            case 2 -> useBloodBond(player, data, level, stage, now, boosted);
            case 3 -> useBloodWave(player, data, level, stage, now, boosted);
            case 4 -> useBloodArmor(player, data, level, stage, now, boosted);
            case 5 -> useFrenzy(player, data, level, stage, now, boosted);
            case 6 -> useLordBite(player, data, level, stage, now, boosted);
            default -> false;
        };
    }

    private static boolean useBite(ServerPlayer player, PlayerPowerData data, ServerLevel level, int stage, long now, boolean boosted) {
        LivingEntity target = findTarget(player, 5.5 + stage * 0.3, 2.2);
        if (target == null) {
            player.sendSystemMessage(Component.literal("§cIsırık için yakın hedef yok."));
            return false;
        }
        float damage = 4.0F + stage * 1.0F + (boosted ? 2.0F : 0.0F);
        float stealHearts = 0.5F + stage * 0.15F + (boosted ? 0.25F : 0.0F);
        if (isFrenzy(player, now)) stealHearts *= 1.5F;
        target.hurtServer(level, level.damageSources().playerAttack(player), damage);
        performLifeSteal(player, target, stealHearts, 0.0F, true);
        bloodFx(level, target.position().add(0, 1, 0), 18);
        setCd(player, data, 1, now, 35 - stage * 3);
        return true;
    }

    private static boolean useBloodBond(ServerPlayer player, PlayerPowerData data, ServerLevel level, int stage, long now, boolean boosted) {
        LivingEntity target = findTarget(player, 16.0 + stage, 2.2);
        if (target == null) {
            player.sendSystemMessage(Component.literal("§cKan bağı için hedef yok."));
            return false;
        }
        long dur = 100L + stage * 30L + (boosted ? 40L : 0L);
        BONDS.put(player.getUUID(), new BloodBond(target.getUUID(), now + dur, stage));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) dur, 0, false, true, true));
        bloodFx(level, target.position().add(0, 1.2, 0), 24);
        player.sendSystemMessage(Component.literal("§4Kan bağı: §f" + target.getName().getString() + " §7(" + (dur / 20) + "s)"));
        setCd(player, data, 2, now, 70 - stage * 5);
        return true;
    }

    private static boolean useBloodWave(ServerPlayer player, PlayerPowerData data, ServerLevel level, int stage, long now, boolean boosted) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        float damage = 3.0F + stage * 0.8F + (boosted ? 1.5F : 0.0F);
        float stealEach = 0.2F + stage * 0.05F;
        int hits = 0;
        for (int i = 1; i <= 6; i++) {
            Vec3 p = eye.add(look.scale(i * 1.1));
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, p.x, p.y, p.z, 4, 0.15, 0.15, 0.15, 0.0);
            AABB box = new AABB(p, p).inflate(1.1);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (e == player || !e.isAlive()) continue;
                e.hurtServer(level, level.damageSources().playerAttack(player), damage);
                performLifeSteal(player, e, stealEach, 0.0F, true);
                hits++;
            }
        }
        if (hits == 0) {
            player.sendSystemMessage(Component.literal("§cKan dalgası kimseye değmedi."));
            return false;
        }
        setCd(player, data, 3, now, 80 - stage * 6);
        return true;
    }

    private static boolean useBloodArmor(ServerPlayer player, PlayerPowerData data, ServerLevel level, int stage, long now, boolean boosted) {
        float stolen = data.vampireStolenHearts();
        if (stolen < 0.5F) {
            player.sendSystemMessage(Component.literal("§cKan zırhı için önce can çalmalısın."));
            return false;
        }
        float armor = Math.min(8.0F, 2.0F + stolen * 0.4F + stage + (boosted ? 2.0F : 0.0F));
        BLOOD_ARMOR.put(player.getUUID(), armor);
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100 + stage * 20, 0, false, true, true));
        bloodFx(level, player.position().add(0, 1, 0), 30);
        player.sendSystemMessage(Component.literal(String.format("§4Kan zırhı: §f%.1f", armor)));
        setCd(player, data, 4, now, 100 - stage * 8);
        return true;
    }

    private static boolean useFrenzy(ServerPlayer player, PlayerPowerData data, ServerLevel level, int stage, long now, boolean boosted) {
        long dur = 80L + stage * 20L + (boosted ? 30L : 0L);
        FRENZY_UNTIL.put(player.getUUID(), now + dur);
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, (int) dur, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, (int) dur, 0, false, true, true));
        // Bitişte yorgunluk için kısa gecikmeli effect
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) dur + 40, 0, false, true, true));
        bloodFx(level, player.position().add(0, 1, 0), 40);
        player.sendSystemMessage(Component.literal("§4Kan çılgınlığı!"));
        setCd(player, data, 5, now, 140 - stage * 10);
        return true;
    }

    private static boolean useLordBite(ServerPlayer player, PlayerPowerData data, ServerLevel level, int stage, long now, boolean boosted) {
        LivingEntity target = findTarget(player, 6.5 + stage * 0.3, 2.4);
        if (target == null) {
            player.sendSystemMessage(Component.literal("§cLord ısırığı için hedef yok."));
            return false;
        }
        float damage = 8.0F + stage * 1.6F + (boosted ? 3.0F : 0.0F);
        float stealHearts = 1.5F + stage * 0.35F + (boosted ? 0.5F : 0.0F);
        target.hurtServer(level, level.damageSources().playerAttack(player), damage);
        performLifeSteal(player, target, stealHearts, 0.0F, true);
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80 + stage * 15, 0, false, true, true));
        bloodFx(level, target.position().add(0, 1, 0), 45);
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.4, 0.5, 0.4, 0.1);
        setCd(player, data, 6, now, 160 - stage * 12);
        return true;
    }

    /**
     * @param stealHearts çalınacak kalp (1 kalp = 2 HP)
     * @param extraDamage ekstra hasar (bağ tick vb.)
     */
    public static void performLifeSteal(ServerPlayer vampire, LivingEntity target, float stealHearts, float extraDamage, boolean showMsg) {
        if (stealHearts <= 0.0F || !target.isAlive()) return;
        PlayerPowerData data = PlayerDataStore.get(vampire.getUUID());
        float current = data.vampireStolenHearts();
        float room = MAX_STOLEN_HEARTS - current;
        float actualHearts = Math.min(stealHearts, Math.max(0.0F, room));

        float hpSteal = actualHearts * HP_PER_HEART;
        if (extraDamage > 0.0F) {
            target.hurtServer((ServerLevel) target.level(), target.level().damageSources().playerAttack(vampire), extraDamage);
        }
        // Hedef canından düş (zaten hurt ile bir kısmı gitti; ek emiş)
        if (hpSteal > 0.0F) {
            float newHealth = Math.max(1.0F, target.getHealth() - hpSteal * 0.5F);
            target.setHealth(newHealth);
        }

        if (actualHearts > 0.0F) {
            data.setVampireStolenHearts(current + actualHearts);
            applyStolenHealthAttribute(vampire, data);
            vampire.heal(actualHearts * HP_PER_HEART);
            PlayerDataStore.markDirty();
            ServerNetworking.sync(vampire);
            if (showMsg) {
                vampire.sendSystemMessage(Component.literal(String.format(
                    "§4+%.1f kalp çalındı §7(toplam %.1f / %.0f)",
                    actualHearts, data.vampireStolenHearts(), MAX_STOLEN_HEARTS
                )));
            }
        }

        // Kurban debuff: çalınan kalp kadar
        applyVictimDrain(target, actualHearts > 0 ? actualHearts : stealHearts * 0.5F);
    }

    private static void applyVictimDrain(LivingEntity target, float heartsStolenThisHit) {
        if (heartsStolenThisHit <= 0.0F) return;
        UUID id = target.getUUID();
        float total = VICTIM_DRAINED_HEARTS.getOrDefault(id, 0.0F) + heartsStolenThisHit;
        VICTIM_DRAINED_HEARTS.put(id, total);

        int ampSlow = Math.min(3, Math.max(0, (int) total - 1));
        int ampNausea = Math.min(1, (int) (total / 2.0F));
        int duration = 60 + (int) (total * 20);
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, ampSlow, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, duration, ampNausea, false, true, true));

        if (total > 5.0F) {
            // Zıplama engeli
            target.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, duration, 128, false, false, true));
            if (target instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.literal("§4Kan kaybı: zıplayamıyorsun!"));
            }
        } else if (target instanceof ServerPlayer sp && heartsStolenThisHit >= 0.5F) {
            sp.sendSystemMessage(Component.literal(String.format("§cKanın çekiliyor… §7(%.1f kalp)", total)));
        }
    }

    private static void applyVictimJumpLock(ServerPlayer player) {
        float drained = VICTIM_DRAINED_HEARTS.getOrDefault(player.getUUID(), 0.0F);
        if (drained > 5.0F) {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 30, 128, false, false, true));
            AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
            if (jump != null && jump.getBaseValue() > 0.01) {
                // geçici: effect yeterli; attribute'a dokunma kalıcı bozmasın
            }
        }
    }

    public static void applyStolenHealthAttribute(ServerPlayer player, PlayerPowerData data) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        float stolen = Math.max(0.0F, Math.min(MAX_STOLEN_HEARTS, data.vampireStolenHearts()));
        double base = data.vampireHealthBase();
        if (base < 1.0) {
            base = Math.max(1.0, attr.getBaseValue() - data.vampireStolenHearts() * HP_PER_HEART);
            data.setVampireHealthBase(base);
        }
        double expected = base + stolen * HP_PER_HEART;
        if (Math.abs(attr.getBaseValue() - expected) > 0.01) {
            attr.setBaseValue(expected);
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    public static void afterDeath(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
        UUID id = entity.getUUID();
        VICTIM_DRAINED_HEARTS.remove(id);
        BONDS.remove(id);
        FRENZY_UNTIL.remove(id);
        BLOOD_ARMOR.remove(id);
        if (entity instanceof ServerPlayer player) {
            PlayerPowerData data = PlayerDataStore.get(player.getUUID());
            if (data.vampireStolenHearts() > 0.0F || data.powerClass() == PowerClass.VAMPIRE) {
                resetStolenHealth(player, data);
                player.sendSystemMessage(Component.literal("§7Çaldığın kan kayboldu."));
            }
        }
    }

    public static void resetStolenHealth(ServerPlayer player, PlayerPowerData data) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        double base = data.vampireHealthBase();
        if (attr != null) {
            if (base < 1.0) base = 20.0;
            attr.setBaseValue(base);
        }
        data.setVampireStolenHearts(0.0F);
        data.setVampireHealthBase(0.0);
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
        PlayerDataStore.markDirty();
        ServerNetworking.sync(player);
    }

    /** Kan zırhı hasar emer. */
    public static float absorbDamage(ServerPlayer player, float amount) {
        Float armor = BLOOD_ARMOR.get(player.getUUID());
        if (armor == null || armor <= 0.0F) return amount;
        float absorbed = Math.min(armor, amount);
        float left = armor - absorbed;
        if (left <= 0.05F) BLOOD_ARMOR.remove(player.getUUID());
        else BLOOD_ARMOR.put(player.getUUID(), left);
        return Math.max(0.0F, amount - absorbed);
    }

    private static boolean isFrenzy(ServerPlayer player, long now) {
        Long until = FRENZY_UNTIL.get(player.getUUID());
        return until != null && until > now;
    }

    private static void setCd(ServerPlayer player, PlayerPowerData data, int power, long now, int ticks) {
        data.setCooldown(power, now, Math.max(20, ticks));
        PlayerDataStore.markDirty();
        ServerNetworking.sync(player);
    }

    private static void bloodFx(ServerLevel level, Vec3 pos, int count) {
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, pos.x, pos.y, pos.z, count, 0.35, 0.45, 0.35, 0.0);
        level.sendParticles(ParticleTypes.CRIMSON_SPORE, pos.x, pos.y, pos.z, Math.max(3, count / 3), 0.25, 0.3, 0.25, 0.02);
    }

    private static LivingEntity findTarget(ServerPlayer player, double range, double width) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(width);
        LivingEntity best = null;
        double bestDist = range + 1.0;
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (e == player || !e.isAlive() || e.isSpectator()) continue;
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            Vec3 center = e.getBoundingBox().getCenter();
            Vec3 to = center.subtract(eye);
            double along = to.dot(look);
            if (along < 0.3 || along > range) continue;
            if (to.subtract(look.scale(along)).length() > width) continue;
            if (along < bestDist) {
                bestDist = along;
                best = e;
            }
        }
        return best;
    }

}
