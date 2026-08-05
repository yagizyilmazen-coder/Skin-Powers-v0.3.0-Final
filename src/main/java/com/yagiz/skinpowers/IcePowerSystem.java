package com.yagiz.skinpowers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Buz sınıfı: dondurma, yavaşlatma, buz kalkanı.
 * Warden ligi CD/hasar bandı; rol = kontrol + savunma (burst düşük).
 */
public final class IcePowerSystem {
    private static final List<IceSpear> SPEARS = new ArrayList<>();
    private static final List<IceCage> CAGES = new ArrayList<>();
    private static final List<BlizzardField> BLIZZARDS = new ArrayList<>();
    private static final List<FrozenRoot> FROZEN_ROOTS = new ArrayList<>();

    private IcePowerSystem() {}

    public static void tickServer(MinecraftServer server) {
        tickSpears();
        tickCages();
        tickBlizzards();
        tickFrozenRoots();
    }

    public static void tickPlayer(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        // Pasif: soğukta / buzda hafif direnç hissi
        if (data.unlockedLevel() >= 1 && now % 40L == 0L) {
            BlockPos below = player.blockPosition().below();
            var state = level.getBlockState(below);
            if (state.is(net.minecraft.world.level.block.Blocks.ICE)
                || state.is(net.minecraft.world.level.block.Blocks.PACKED_ICE)
                || state.is(net.minecraft.world.level.block.Blocks.BLUE_ICE)
                || state.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)
                || state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) {
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 50, 0, false, false, true));
            }
        }
    }

    public static boolean use(ServerPlayer player, PlayerPowerData data, int power, long now, boolean charged) {
        ServerLevel level = (ServerLevel) player.level();
        int stage = data.masteryStage(power);
        return switch (power) {
            case 1 -> iceArmor(player, data, level, now, stage, charged);
            case 2 -> freezeWave(player, data, level, now, stage, charged);
            case 3 -> iceSpear(player, data, level, now, stage, charged);
            case 4 -> iceCage(player, data, level, now, stage, charged);
            case 5 -> blizzard(player, data, level, now, stage, charged);
            case 6 -> absoluteZero(player, data, level, now, stage, charged);
            default -> false;
        };
    }

    public static void tickAwakening(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        // Uyanış: Buzul Hükmü — yakındakileri yavaşlat, kendine soğuk direnci
        if (now % 15L != 0L) return;
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 1, false, false, true));
        for (LivingEntity target : nearby(player, 8.0)) {
            if (target == player || PowerSystem.isProtectedAlly(player, target)) continue;
            applyFreeze(target, 40, 1);
            target.hurtServer(level, level.damageSources().playerAttack(player), 3.5F);
        }
        if (now % 10L == 0L) {
            level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1.0, player.getZ(), 18, 1.2, 0.8, 1.2, 0.02);
        }
    }

    private static boolean iceArmor(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now, int stage, boolean charged) {
        int duration = 300 + stage * 40 + (charged ? 80 : 0);
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, duration, charged ? 2 : (stage >= 2 ? 1 : 0), false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, charged ? 2 : (stage >= 1 ? 1 : 0), false, true, true));
        level.playSound(null, player.blockPosition(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.0F, 1.4F);
        level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.6, 0.8, 0.6, 0.04);
        data.setCooldown(1, now, Math.max(520, 780 - stage * 55));
        return true;
    }

    private static boolean freezeWave(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now, int stage, boolean charged) {
        double radius = 6.5 + stage * 0.7 + (charged ? 2.0 : 0.0);
        float damage = 9.0F + stage * 1.6F + (charged ? 4.0F : 0.0F);
        for (LivingEntity target : nearby(player, radius)) {
            if (target == player || PowerSystem.isProtectedAlly(player, target)) continue;
            target.hurtServer(level, level.damageSources().playerAttack(player), damage);
            applyFreeze(target, 70 + stage * 12, stage >= 2 ? 2 : 1);
            Vec3 push = target.position().subtract(player.position());
            if (push.lengthSqr() > 0.001) {
                push = push.normalize().scale(0.55);
                target.push(push.x, 0.25, push.z);
            }
        }
        PowerSystem.drawExternalRing(level, player.position().add(0.0, 0.15, 0.0), radius, ParticleTypes.SNOWFLAKE, 64);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.2F, 0.7F);
        ServerNetworking.sendScreenShake(level, player.position(), 22.0, 0.7F, 8);
        data.setCooldown(2, now, Math.max(280, 400 - stage * 28));
        return true;
    }

    private static boolean iceSpear(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now, int stage, boolean charged) {
        Vec3 dir = player.getLookAngle().normalize();
        Vec3 start = player.getEyePosition().add(dir.scale(0.8));
        float damage = 11.0F + stage * 1.8F + (charged ? 5.0F : 0.0F);
        SPEARS.add(new IceSpear(level, player.getUUID(), start, dir, now, now + 28L, damage, stage, charged));
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 1.6F);
        level.sendParticles(ParticleTypes.SNOWFLAKE, start.x, start.y, start.z, 12, 0.15, 0.15, 0.15, 0.02);
        data.setCooldown(3, now, Math.max(200, 320 - stage * 22));
        return true;
    }

    private static boolean iceCage(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now, int stage, boolean charged) {
        LivingEntity target = findLookTarget(player, 16.0 + stage);
        if (target == null) {
            player.sendSystemMessage(Component.literal("Buz Kafesi için nişangâhında bir hedef olmalı."));
            return false;
        }
        if (PowerSystem.isProtectedAlly(player, target)) {
            player.sendSystemMessage(Component.literal("Dost hedefe Buz Kafesi uygulanamaz."));
            return false;
        }
        long until = now + 80L + stage * 12L + (charged ? 30L : 0L);
        CAGES.add(new IceCage(level, player.getUUID(), target.getUUID(), now, until, stage, charged));
        applyFreeze(target, (int) (until - now), 3);
        target.hurtServer(level, level.damageSources().playerAttack(player), 8.0F + stage * 1.4F);
        level.playSound(null, target.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.1F, 0.55F);
        level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + 1.0, target.getZ(), 50, 0.7, 1.0, 0.7, 0.05);
        player.sendSystemMessage(Component.literal("Buz Kafesi kilitlendi."));
        data.setCooldown(4, now, Math.max(420, 600 - stage * 40));
        return true;
    }

    private static boolean blizzard(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now, int stage, boolean charged) {
        Vec3 center = player.position();
        double radius = 9.0 + stage * 0.8 + (charged ? 2.5 : 0.0);
        long until = now + 140L + stage * 16L + (charged ? 40L : 0L);
        BLIZZARDS.add(new BlizzardField(level, player.getUUID(), center, now, until, radius, stage, charged));
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.4F, 0.5F);
        ServerNetworking.sendScreenShake(level, center, 26.0, 0.85F, 10);
        data.setCooldown(5, now, Math.max(700, 960 - stage * 50));
        return true;
    }

    private static boolean absoluteZero(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now, int stage, boolean charged) {
        double radius = 12.0 + stage * 0.9 + (charged ? 3.0 : 0.0);
        float damage = 18.0F + stage * 2.2F + (charged ? 6.0F : 0.0F);
        for (LivingEntity target : nearby(player, radius)) {
            if (target == player || PowerSystem.isProtectedAlly(player, target)) continue;
            target.hurtServer(level, level.damageSources().playerAttack(player), damage);
            applyFreeze(target, 100 + stage * 15, 4);
            try {
                target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 40, target.getTicksFrozen() + 140));
            } catch (Throwable ignored) {
                // Sürüm farkı: setTicksFrozen yoksa sadece efekt yeterli
            }
            Vec3 push = target.position().subtract(player.position());
            if (push.lengthSqr() > 0.001) {
                push = push.normalize().scale(1.1);
                target.push(push.x, 0.55, push.z);
            }
        }
        for (int i = 0; i < 4; i++) {
            PowerSystem.drawExternalRing(level, player.position().add(0.0, 0.2 + i * 0.35, 0.0), radius * (0.4 + i * 0.2), ParticleTypes.SNOWFLAKE, 48);
        }
        level.sendParticles(ParticleTypes.ITEM_SNOWBALL, player.getX(), player.getY() + 1.0, player.getZ(), 80, 3.0, 1.5, 3.0, 0.08);
        level.playSound(null, player.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 1.2F, 1.8F);
        ServerNetworking.sendScreenShake(level, player.position(), 34.0, 1.4F, 16);
        ServerNetworking.sendCastAnimation(level, player.position(), PowerClass.ICE, 6);
        data.setCooldown(6, now, Math.max(1400, 1800 - stage * 80));
        return true;
    }

    /**
     * Gerçek dondurma: yavaşlık efekti YOK.
     * Hareket/zıplama sıfırlanır, oyuncuya buz ekranı gider, etrafta buz parçacığı.
     */
    private static void applyFreeze(LivingEntity target, int ticks, int amp) {
        if (target == null || !target.isAlive() || ticks <= 0) return;
        int duration = Math.max(20, ticks);
        // Eski yavaşlık/mining efektlerini temizle (önceki vuruşlardan kalmasın)
        target.removeEffect(MobEffects.SLOWNESS);
        target.removeEffect(MobEffects.MINING_FATIGUE);
        try {
            target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 60, 200));
        } catch (Throwable ignored) {
        }
        // Anında kilitle
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        try { target.setJumping(false); } catch (Throwable ignored) {}
        target.hasImpulse = true;

        long now = target.level().getGameTime();
        long until = now + duration;
        // Aynı hedefte süreyi uzat
        boolean found = false;
        for (FrozenRoot root : FROZEN_ROOTS) {
            if (root.targetId.equals(target.getUUID())) {
                root.endsAt = Math.max(root.endsAt, until);
                root.amp = Math.max(root.amp, amp);
                found = true;
                break;
            }
        }
        if (!found) {
            ServerLevel level = (ServerLevel) target.level();
            FROZEN_ROOTS.add(new FrozenRoot(level, target.getUUID(), now, until, amp));
        }

        // Hedef oyuncuysa buz ekranı
        if (target instanceof ServerPlayer victim) {
            ServerNetworking.sendIceScreen(victim, duration);
        }
        // Görsel buz aurası
        ServerLevel level = (ServerLevel) target.level();
        level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + 1.0, target.getZ(),
            28 + amp * 6, 0.45, 0.9, 0.45, 0.02);
        level.sendParticles(ParticleTypes.ITEM_SNOWBALL, target.getX(), target.getY() + 0.4, target.getZ(),
            12, 0.35, 0.5, 0.35, 0.01);
        level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 0.9F, 0.65F);
    }

    private static void tickFrozenRoots() {
        Iterator<FrozenRoot> it = FROZEN_ROOTS.iterator();
        while (it.hasNext()) {
            FrozenRoot root = it.next();
            long now = root.level.getGameTime();
            if (now > root.endsAt) {
                it.remove();
                continue;
            }
            Entity entity = root.level.getEntity(root.targetId);
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                it.remove();
                continue;
            }
            // Tam kilit: hız yok, zıplama yok
            living.setDeltaMovement(Vec3.ZERO);
            living.hurtMarked = true;
            try { living.setJumping(false); } catch (Throwable ignored) {}
            living.fallDistance = 0.0F;
            // Oyuncuysa istemciyi de bastır
            if (living instanceof ServerPlayer player) {
                player.setDeltaMovement(Vec3.ZERO);
                // Her 10 tikte ekranı yenile (süre uzadıysa)
                if (now % 10L == 0L) {
                    int remain = (int) Math.max(1, root.endsAt - now);
                    ServerNetworking.sendIceScreen(player, remain);
                }
            }
            // Etrafında buz görünümü
            if (now % 5L == 0L) {
                root.level.sendParticles(ParticleTypes.SNOWFLAKE,
                    living.getX(), living.getY() + 1.0, living.getZ(),
                    10 + root.amp * 2, 0.5, 0.85, 0.5, 0.015);
                root.level.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                    living.getX(), living.getY() + 0.3, living.getZ(),
                    4, 0.4, 0.45, 0.4, 0.008);
            }
        }
    }

    private static void tickSpears() {
        Iterator<IceSpear> it = SPEARS.iterator();
        while (it.hasNext()) {
            IceSpear spear = it.next();
            long now = spear.level.getGameTime();
            if (now > spear.endsAt) {
                it.remove();
                continue;
            }
            double t = (now - spear.startedAt) / Math.max(1.0, spear.endsAt - spear.startedAt);
            Vec3 pos = spear.start.add(spear.dir.scale(t * (14.0 + spear.stage)));
            spear.level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y, pos.z, 4, 0.08, 0.08, 0.08, 0.01);
            AABB box = new AABB(pos, pos).inflate(0.9);
            ServerPlayer owner = spear.level.getServer().getPlayerList().getPlayer(spear.owner);
            for (LivingEntity target : spear.level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (owner != null && (target == owner || PowerSystem.isProtectedAlly(owner, target))) continue;
                if (spear.hit.contains(target.getUUID())) continue;
                spear.hit.add(target.getUUID());
                if (owner != null) {
                    target.hurtServer(spear.level, spear.level.damageSources().playerAttack(owner), spear.damage);
                } else {
                    target.hurtServer(spear.level, spear.level.damageSources().generic(), spear.damage);
                }
                applyFreeze(target, 50 + spear.stage * 8, 2);
            }
        }
    }

    private static void tickCages() {
        Iterator<IceCage> it = CAGES.iterator();
        while (it.hasNext()) {
            IceCage cage = it.next();
            long now = cage.level.getGameTime();
            if (now > cage.endsAt) {
                it.remove();
                continue;
            }
            Entity entity = cage.level.getEntity(cage.target);
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                it.remove();
                continue;
            }
            living.setDeltaMovement(living.getDeltaMovement().scale(0.15));
            living.hurtMarked = true;
            if (now % 8L == 0L) {
                cage.level.sendParticles(ParticleTypes.SNOWFLAKE, living.getX(), living.getY() + 1.0, living.getZ(), 10, 0.4, 0.6, 0.4, 0.02);
                // Kilit FrozenRoot ile sürer; her tik spam yok
            }
        }
    }

    private static void tickBlizzards() {
        Iterator<BlizzardField> it = BLIZZARDS.iterator();
        while (it.hasNext()) {
            BlizzardField field = it.next();
            long now = field.level.getGameTime();
            if (now > field.endsAt) {
                it.remove();
                continue;
            }
            if (now % 10L == 0L) {
                PowerSystem.drawExternalRing(field.level, field.center.add(0.0, 0.2, 0.0), field.radius, ParticleTypes.SNOWFLAKE, 40);
            }
            if (now % 12L != 0L) continue;
            ServerPlayer owner = field.level.getServer().getPlayerList().getPlayer(field.owner);
            AABB area = new AABB(field.center, field.center).inflate(field.radius, 6.0, field.radius);
            for (LivingEntity target : field.level.getEntitiesOfClass(LivingEntity.class, area)) {
                if (owner != null && (target == owner || PowerSystem.isProtectedAlly(owner, target))) continue;
                applyFreeze(target, 25, 1);
                float dmg = 4.0F + field.stage * 0.7F + (field.charged ? 1.5F : 0.0F);
                if (owner != null) {
                    target.hurtServer(field.level, field.level.damageSources().playerAttack(owner), dmg);
                } else {
                    target.hurtServer(field.level, field.level.damageSources().generic(), dmg);
                }
            }
        }
    }

    private static List<LivingEntity> nearby(ServerPlayer player, double radius) {
        return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius));
    }

    private static LivingEntity findLookTarget(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        LivingEntity best = null;
        double bestScore = 0.0;
        for (LivingEntity e : nearby(player, range)) {
            if (e == player || !e.isAlive()) continue;
            Vec3 to = e.getEyePosition().subtract(eye);
            double dist = to.length();
            if (dist < 0.5 || dist > range) continue;
            double dot = look.dot(to.normalize());
            if (dot < 0.88) continue;
            double score = dot * 2.0 - dist * 0.03;
            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    public static void clearOwner(UUID owner) {
        SPEARS.removeIf(s -> s.owner.equals(owner));
        CAGES.removeIf(c -> c.owner.equals(owner));
        BLIZZARDS.removeIf(b -> b.owner.equals(owner));
    }

    public static void handleDisconnect(ServerPlayer player) {
        clearOwner(player.getUUID());
    }

    public static void clearAll() {
        SPEARS.clear();
        CAGES.clear();
        BLIZZARDS.clear();
        FROZEN_ROOTS.clear();
    }

    public static void afterDeath(LivingEntity entity, DamageSource source) {
        if (entity instanceof ServerPlayer player) clearOwner(player.getUUID());
        if (entity != null) {
            FROZEN_ROOTS.removeIf(r -> r.targetId.equals(entity.getUUID()));
        }
    }

    private static final class IceSpear {
        private final ServerLevel level;
        private final UUID owner;
        private final Vec3 start;
        private final Vec3 dir;
        private final long startedAt;
        private final long endsAt;
        private final float damage;
        private final int stage;
        private final boolean charged;
        private final java.util.HashSet<UUID> hit = new java.util.HashSet<>();

        private IceSpear(ServerLevel level, UUID owner, Vec3 start, Vec3 dir, long startedAt, long endsAt, float damage, int stage, boolean charged) {
            this.level = level;
            this.owner = owner;
            this.start = start;
            this.dir = dir;
            this.startedAt = startedAt;
            this.endsAt = endsAt;
            this.damage = damage;
            this.stage = stage;
            this.charged = charged;
        }
    }

    private static final class IceCage {
        private final ServerLevel level;
        private final UUID owner;
        private final UUID target;
        private final long startedAt;
        private final long endsAt;
        private final int stage;
        private final boolean charged;

        private IceCage(ServerLevel level, UUID owner, UUID target, long startedAt, long endsAt, int stage, boolean charged) {
            this.level = level;
            this.owner = owner;
            this.target = target;
            this.startedAt = startedAt;
            this.endsAt = endsAt;
            this.stage = stage;
            this.charged = charged;
        }
    }

    private static final class FrozenRoot {
        private final ServerLevel level;
        private final UUID targetId;
        private final long startedAt;
        private long endsAt;
        private int amp;

        private FrozenRoot(ServerLevel level, UUID targetId, long startedAt, long endsAt, int amp) {
            this.level = level;
            this.targetId = targetId;
            this.startedAt = startedAt;
            this.endsAt = endsAt;
            this.amp = amp;
        }
    }

    private static final class BlizzardField {
        private final ServerLevel level;
        private final UUID owner;
        private final Vec3 center;
        private final long startedAt;
        private final long endsAt;
        private final double radius;
        private final int stage;
        private final boolean charged;

        private BlizzardField(ServerLevel level, UUID owner, Vec3 center, long startedAt, long endsAt, double radius, int stage, boolean charged) {
            this.level = level;
            this.owner = owner;
            this.center = center;
            this.startedAt = startedAt;
            this.endsAt = endsAt;
            this.radius = radius;
            this.stage = stage;
            this.charged = charged;
        }
    }
}
