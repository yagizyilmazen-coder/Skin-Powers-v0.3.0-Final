package com.yagiz.skinpowers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PowerSystem {
    private static final List<PendingMeteor> METEORS = new ArrayList<>();
    private static final Map<UUID, Long> LAST_FIRE_BONUS = new HashMap<>();
    private static final Map<UUID, Long> LAST_SKY_IMPACT = new HashMap<>();
    private static long lastAutosaveTick;

    private PowerSystem() {}

    public static InteractionResult onAttackEntity(
        net.minecraft.world.entity.player.Player player,
        Level level,
        InteractionHand hand,
        Entity entity,
        EntityHitResult hitResult
    ) {
        if (!(level instanceof ServerLevel serverLevel)
            || !(player instanceof ServerPlayer serverPlayer)
            || !(entity instanceof LivingEntity target)
            || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        PlayerPowerData data = PlayerDataStore.get(serverPlayer.getUUID());
        if (data.powerClass() != PowerClass.FIRE || data.unlockedLevel() < 2) {
            return InteractionResult.PASS;
        }

        long now = serverLevel.getGameTime();
        long last = LAST_FIRE_BONUS.getOrDefault(target.getUUID(), Long.MIN_VALUE / 2);
        if (now - last >= 160L) {
            LAST_FIRE_BONUS.put(target.getUUID(), now);
            target.hurtServer(serverLevel, serverLevel.damageSources().playerAttack(serverPlayer), 4.0F);
            target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 60));
            serverLevel.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 1.0, target.getZ(), 12, 0.35, 0.45, 0.35, 0.02);
        }
        return InteractionResult.PASS;
    }

    public static void tickServer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player);
        }
        tickMeteors();

        long gameTime = server.overworld().getGameTime();
        if (gameTime - lastAutosaveTick >= 1200L) {
            PlayerDataStore.save();
            lastAutosaveTick = gameTime;
        }
    }

    private static void tickPlayer(ServerPlayer player) {
        PlayerPowerData data = PlayerDataStore.get(player.getUUID());
        if (data.powerClass() == PowerClass.NONE) return;

        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();

        switch (data.powerClass()) {
            case WARDEN -> tickWarden(player, data, level, now);
            case FLIGHT -> tickFlight(player, data, level, now);
            case FIRE -> tickFire(player, data, level, now);
            default -> { }
        }

        if (now % 10L == 0L) ServerNetworking.sync(player);
    }

    private static void tickWarden(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        if (data.unlockedLevel() >= 4 && data.visionEnabled()) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 30, 0, false, false, false));
            if (now % 8L == 0L) {
                for (LivingEntity living : nearbyLiving(player, 16.0)) {
                    if (living == player || protectedAlly(player, living)) continue;
                    if (living.getDeltaMovement().horizontalDistanceSqr() > 0.002) {
                        level.sendParticles(ParticleTypes.SCULK_SOUL, living.getX(), living.getY() + 0.8, living.getZ(), 3, 0.25, 0.35, 0.25, 0.01);
                    }
                }
            }
        }

        if (data.awakeningUntil() > now) {
            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 30, 2, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 1, false, false, true));
            if (now % 4L == 0L) {
                level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 7, 0.65, 0.9, 0.65, 0.015);
            }
        } else if (data.awakeningUntil() != 0L && data.awakeningUntil() <= now) {
            data.setAwakeningUntil(0L);
            player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 140, 1, false, true, true));
            PlayerDataStore.markDirty();
        }
    }

    private static void tickFlight(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        boolean chestAllowed = isChestArmorAllowed(player.getItemBySlot(EquipmentSlot.CHEST), data.unlockedLevel());
        boolean classFlight = data.unlockedLevel() >= 2 && chestAllowed;

        if (classFlight && !player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        } else if (!classFlight && player.getAbilities().mayfly && !player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }

        if (data.unlockedLevel() >= 1 && data.passiveEnabled()) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false, true));
            player.fallDistance = 0.0F;
        }

        if (player.getAbilities().flying && now % 2L == 0L) {
            Vec3 back = player.getLookAngle().scale(-0.75);
            level.sendParticles(ParticleTypes.CLOUD,
                player.getX() + back.x, player.getY() + 0.9, player.getZ() + back.z,
                3, 0.25, 0.22, 0.25, 0.01);
        }

        if (data.skyImpactSlowUntil() > now) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 3, false, false, true));
        }

        if (data.unlockedLevel() >= 5 && player.getAbilities().flying && player.getDeltaMovement().lengthSqr() > 1.20) {
            long lastImpact = LAST_SKY_IMPACT.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
            if (now - lastImpact >= 80L) {
                for (LivingEntity target : nearbyLiving(player, 1.6)) {
                    if (target == player || protectedAlly(player, target)) continue;
                    target.hurtServer(level, level.damageSources().playerAttack(player), 10.0F);
                    Vec3 push = target.position().subtract(player.position()).normalize().scale(1.2);
                    target.push(push.x, 0.35, push.z);
                    player.setDeltaMovement(player.getDeltaMovement().scale(0.25));
                    data.setSkyImpactSlowUntil(now + 80L);
                    LAST_SKY_IMPACT.put(player.getUUID(), now);
                    level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 0.7, target.getZ(), 24, 0.7, 0.7, 0.7, 0.08);
                    level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 1.5F);
                    break;
                }
            }
        }
    }

    private static void tickFire(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        if (data.unlockedLevel() >= 1) {
            player.setRemainingFireTicks(0);
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, true));
        }

        if (data.fireRingUntil() > now) {
            if (now % 20L == 0L) {
                for (LivingEntity target : nearbyLiving(player, 10.0)) {
                    if (target == player) continue;
                    target.hurtServer(level, level.damageSources().playerAttack(player), 2.0F);
                    target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 60));
                }
            }
            if (now % 3L == 0L) {
                drawRing(level, player.position(), 10.0, ParticleTypes.FLAME, 42);
                igniteSparseGround(level, player.blockPosition(), 10, now);
            }
        } else if (data.fireRingUntil() != 0L) {
            data.setFireRingUntil(0L);
            PlayerDataStore.markDirty();
        }

        if (data.unlockedLevel() >= 4 && data.visionEnabled() && now % 8L == 0L) {
            for (LivingEntity target : nearbyLiving(player, 22.0)) {
                if (target.isOnFire()) {
                    level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 1.0, target.getZ(), 2, 0.25, 0.5, 0.25, 0.01);
                }
            }
        }
    }

    public static void useSelectedPower(ServerPlayer player, PlayerPowerData data) {
        if (data.powerClass() == PowerClass.NONE || data.unlockedLevel() == 0) {
            player.displayClientMessage(Component.literal("Önce O ekranından bir seviye açmalısın."), true);
            return;
        }
        int power = data.selectedPower();
        if (power > data.unlockedLevel()) return;

        long now = player.level().getGameTime();
        int remaining = data.cooldownRemaining(power, now);
        if (remaining > 0) {
            player.displayClientMessage(Component.literal("Güç " + formatSeconds(remaining) + " saniye sonra hazır."), true);
            return;
        }

        boolean used = switch (data.powerClass()) {
            case WARDEN -> useWarden(player, data, power, now);
            case FLIGHT -> useFlight(player, data, power, now);
            case FIRE -> useFire(player, data, power, now);
            default -> false;
        };

        if (used) {
            data.addMasteryUse(power);
            PlayerDataStore.markDirty();
            ServerNetworking.sync(player);
        }
    }

    public static void toggleSelectedFeature(ServerPlayer player, PlayerPowerData data) {
        boolean changed = false;
        if (data.powerClass() == PowerClass.FLIGHT && data.unlockedLevel() >= 1) {
            data.togglePassive();
            changed = true;
            player.displayClientMessage(Component.literal("Yavaş Düşüş: " + (data.passiveEnabled() ? "AÇIK" : "KAPALI")), true);
        } else if ((data.powerClass() == PowerClass.WARDEN || data.powerClass() == PowerClass.FIRE) && data.unlockedLevel() >= 4) {
            data.toggleVision();
            changed = true;
            player.displayClientMessage(Component.literal("Görüş modu: " + (data.visionEnabled() ? "AÇIK" : "KAPALI")), true);
        }
        if (changed) {
            PlayerDataStore.markDirty();
            ServerNetworking.sync(player);
        }
    }

    public static void tryRocketlessLaunch(ServerPlayer player, PlayerPowerData data) {
        if (data.powerClass() != PowerClass.FLIGHT || data.unlockedLevel() < 3) return;
        long now = player.level().getGameTime();
        int remaining = data.cooldownRemaining(3, now);
        if (remaining > 0) return;

        int stage = data.masteryStage(3);
        Vec3 look = player.getLookAngle();
        player.setDeltaMovement(look.x * (0.9 + stage * 0.12), 1.15 + stage * 0.12, look.z * (0.9 + stage * 0.12));
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        data.setCooldown(3, now, Math.max(80, 160 - stage * 20));
        data.addMasteryUse(3);
        player.serverLevel().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 25, 0.5, 0.2, 0.5, 0.08);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.8F, 1.25F);
        PlayerDataStore.markDirty();
        ServerNetworking.sync(player);
    }

    private static boolean useWarden(ServerPlayer player, PlayerPowerData data, int power, long now) {
        ServerLevel level = player.serverLevel();
        int stage = data.masteryStage(power);
        switch (power) {
            case 1 -> {
                int duration = 200 + stage * 60;
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, duration, stage >= 3 ? 1 : 0, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, duration, 0, false, true, true));
                data.setCooldown(1, now, Math.max(300, 600 - stage * 80));
                level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 1.0F);
                return true;
            }
            case 2 -> {
                double radius = 5.0 + stage * 1.5;
                for (LivingEntity target : nearbyLiving(player, radius)) {
                    if (target == player || protectedAlly(player, target)) continue;
                    target.hurtServer(level, level.damageSources().playerAttack(player), 6.0F + stage * 1.5F);
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 45, 5, false, true, true));
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 45, 5, false, true, true));
                    target.push(0.0, -0.25, 0.0);
                }
                drawRing(level, player.position(), radius, ParticleTypes.SCULK_SOUL, 52);
                level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1.2F, 0.8F);
                data.setCooldown(2, now, Math.max(420, 600 - stage * 50));
                return true;
            }
            case 3 -> {
                sonicBlast(player, data, stage);
                data.setCooldown(3, now, Math.max(240, 400 - stage * 40));
                return true;
            }
            case 4 -> {
                data.toggleVision();
                player.displayClientMessage(Component.literal("Karanlık Görüş: " + (data.visionEnabled() ? "AÇIK" : "KAPALI")), true);
                return true;
            }
            case 5 -> {
                data.setAwakeningUntil(now + 300L);
                data.setCooldown(5, now, Math.max(1800, 2400 - stage * 120));
                level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.4F, 0.75F);
                level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 55, 1.0, 1.1, 1.0, 0.04);
                return true;
            }
            default -> { return false; }
        }
    }

    private static boolean useFlight(ServerPlayer player, PlayerPowerData data, int power, long now) {
        if (power == 1) {
            data.togglePassive();
            player.displayClientMessage(Component.literal("Yavaş Düşüş: " + (data.passiveEnabled() ? "AÇIK" : "KAPALI")), true);
            return true;
        }
        if (power == 2) {
            player.displayClientMessage(Component.literal("Bağlı kanatlar pasif olarak çalışıyor; uçmak için zıpla ve uçuş tuşunu kullan."), true);
            return false;
        }
        if (power == 3) {
            tryRocketlessLaunch(player, data);
            return false;
        }
        if (power == 4) {
            int stage = data.masteryStage(4);
            airBlast(player, stage);
            data.setCooldown(4, now, Math.max(180, 300 - stage * 30));
            return true;
        }
        if (power == 5) {
            player.displayClientMessage(Component.literal("Gökyüzü Hâkimiyeti uçarken yüksek hızlı çarpışmalarda otomatik çalışır."), true);
            return false;
        }
        return false;
    }

    private static boolean useFire(ServerPlayer player, PlayerPowerData data, int power, long now) {
        ServerLevel level = player.serverLevel();
        int stage = data.masteryStage(power);
        switch (power) {
            case 1 -> {
                player.displayClientMessage(Component.literal("Ateş bağışıklığı sürekli aktif."), true);
                return false;
            }
            case 2 -> {
                player.displayClientMessage(Component.literal("Alevli yakın dövüş, saldırdığında otomatik çalışır."), true);
                return false;
            }
            case 3 -> {
                data.setFireRingUntil(now + 100L + stage * 10L);
                for (LivingEntity target : nearbyLiving(player, 10.0 + stage * 0.5)) {
                    if (target == player) continue;
                    target.hurtServer(level, level.damageSources().playerAttack(player), 6.0F + stage);
                    target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 80));
                }
                drawRing(level, player.position(), 10.0, ParticleTypes.FLAME, 64);
                level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.8F);
                data.setCooldown(3, now, Math.max(420, 600 - stage * 50));
                return true;
            }
            case 4 -> {
                data.toggleVision();
                player.displayClientMessage(Component.literal("Ateş Görüşü: " + (data.visionEnabled() ? "AÇIK" : "KAPALI")), true);
                return true;
            }
            case 5 -> {
                scheduleMeteors(player, data, stage);
                data.setCooldown(5, now, Math.max(1800, 2400 - stage * 120));
                return true;
            }
            default -> { return false; }
        }
    }

    private static void sonicBlast(ServerPlayer player, PlayerPowerData data, int stage) {
        ServerLevel level = player.serverLevel();
        Vec3 origin = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        double range = 14.0 + stage * 2.0;
        List<LivingEntity> candidates = nearbyLiving(player, range);
        List<LivingEntity> lineTargets = new ArrayList<>();

        for (LivingEntity target : candidates) {
            if (target == player || protectedAlly(player, target)) continue;
            Vec3 to = target.getEyePosition().subtract(origin);
            double forward = to.dot(look);
            if (forward <= 0.0 || forward > range) continue;
            double side = to.subtract(look.scale(forward)).length();
            if (side <= 1.35 + stage * 0.25) lineTargets.add(target);
        }

        if (stage == 0 && !lineTargets.isEmpty()) {
            LivingEntity nearest = lineTargets.stream().min((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player))).orElse(null);
            lineTargets.clear();
            if (nearest != null) lineTargets.add(nearest);
        } else if (stage >= 2) {
            lineTargets.clear();
            for (LivingEntity target : candidates) {
                if (target != player && !protectedAlly(player, target) && target.distanceToSqr(player) <= 100.0) lineTargets.add(target);
            }
        }

        for (LivingEntity target : lineTargets) {
            target.hurtServer(level, level.damageSources().playerAttack(player), 8.0F + stage * 2.0F);
            Vec3 push = target.position().subtract(player.position()).normalize().scale(0.9);
            target.push(push.x, 0.18, push.z);
        }

        for (int i = 1; i <= (int) range; i++) {
            Vec3 point = origin.add(look.scale(i));
            level.sendParticles(ParticleTypes.SONIC_BOOM, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.4F, 1.0F);
    }

    private static void airBlast(ServerPlayer player, int stage) {
        ServerLevel level = player.serverLevel();
        Vec3 origin = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        double range = 10.0 + stage * 2.0;
        double minDot = 0.76 - stage * 0.04;

        AABB box = player.getBoundingBox().inflate(range);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (target == player || protectedAlly(player, target)) continue;
            Vec3 to = target.getEyePosition().subtract(origin);
            if (to.lengthSqr() > range * range) continue;
            Vec3 direction = to.normalize();
            if (direction.dot(look) < minDot) continue;
            target.hurtServer(level, level.damageSources().playerAttack(player), 8.0F + stage * 1.5F);
            Vec3 push = look.scale(1.3 + stage * 0.18);
            target.push(push.x, 0.35, push.z);
        }

        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, box)) {
            Vec3 to = projectile.position().subtract(origin);
            if (to.lengthSqr() > range * range || to.normalize().dot(look) < minDot) continue;
            Vec3 velocity = projectile.getDeltaMovement();
            projectile.setDeltaMovement(look.scale(Math.max(1.0, velocity.length() + 0.4)));
            projectile.setOwner(player);
        }

        for (int i = 1; i <= (int) range; i++) {
            Vec3 point = origin.add(look.scale(i));
            double spread = 0.08 * i;
            level.sendParticles(ParticleTypes.CLOUD, point.x, point.y, point.z, 4, spread, spread, spread, 0.03);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1.0F, 1.4F);
    }

    private static void scheduleMeteors(ServerPlayer player, PlayerPowerData data, int stage) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Vec3 center = player.position().add(player.getLookAngle().normalize().scale(20.0));
        RandomSource random = level.getRandom();
        int count = 5 + stage;

        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 10, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 10, false, true, true));
        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.8F, 1.4F);

        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(random.nextDouble()) * (7.0 + stage * 1.5);
            int x = (int) Math.floor(center.x + Math.cos(angle) * distance);
            int z = (int) Math.floor(center.z + Math.sin(angle) * distance);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            Vec3 impact = new Vec3(x + 0.5, y, z + 0.5);
            METEORS.add(new PendingMeteor(level, player.getUUID(), impact, now + 40L + i * 5L, 2 + stage / 2, 16.0F + stage * 2.0F));
        }
    }

    private static void tickMeteors() {
        Iterator<PendingMeteor> iterator = METEORS.iterator();
        while (iterator.hasNext()) {
            PendingMeteor meteor = iterator.next();
            ServerLevel level = meteor.level();
            long now = level.getGameTime();
            long remaining = meteor.impactTick() - now;
            if (remaining > 0L) {
                double progress = Math.max(0.0, Math.min(1.0, remaining / 40.0));
                double y = meteor.impact().y + 2.0 + progress * 30.0;
                level.sendParticles(ParticleTypes.FLAME, meteor.impact().x, y, meteor.impact().z, 7, 0.4, 0.7, 0.4, 0.03);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, meteor.impact().x, y + 0.4, meteor.impact().z, 4, 0.5, 0.6, 0.5, 0.02);
                continue;
            }

            impactMeteor(meteor);
            iterator.remove();
        }
    }

    private static void impactMeteor(PendingMeteor meteor) {
        ServerLevel level = meteor.level();
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(meteor.owner());
        Vec3 impact = meteor.impact();
        int radius = meteor.radius();

        level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y + 0.5, impact.z, 8, 1.1, 0.8, 1.1, 0.05);
        level.sendParticles(ParticleTypes.FLAME, impact.x, impact.y + 0.5, impact.z, 70, 2.0, 1.3, 2.0, 0.12);
        level.playSound(null, BlockPos.containing(impact), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.8F, 0.7F);

        AABB area = new AABB(impact, impact).inflate(5.0 + radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (owner != null && target == owner) continue;
            double distance = Math.sqrt(target.distanceToSqr(impact));
            if (distance > 5.0 + radius) continue;
            float scaledDamage = (float) Math.max(4.0, meteor.damage() * (1.0 - distance / (8.0 + radius)));
            if (owner != null) {
                target.hurtServer(level, level.damageSources().playerAttack(owner), scaledDamage);
            } else {
                target.hurtServer(level, level.damageSources().generic(), scaledDamage);
            }
            Vec3 push = target.position().subtract(impact).normalize().scale(1.4);
            target.push(push.x, 0.65, push.z);
            target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 80));
        }

        if (PlayerDataStore.config().meteorBlockDamage()) {
            carveCrater(level, BlockPos.containing(impact), radius, owner);
        }
    }

    private static void carveCrater(ServerLevel level, BlockPos center, int radius, ServerPlayer owner) {
        int vertical = Math.max(2, radius - 1);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 1; dy >= -vertical; dy--) {
                    double normalized = (dx * dx + dz * dz) / (double) (radius * radius) + (dy * dy) / (double) (vertical * vertical + 1);
                    if (normalized > 1.25) continue;
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.is(Blocks.BEDROCK) || state.getDestroySpeed(level, pos) < 0.0F) continue;
                    level.destroyBlock(pos, false, owner);
                }
            }
        }
    }

    private static void igniteSparseGround(ServerLevel level, BlockPos center, int radius, long now) {
        RandomSource random = level.getRandom();
        if (now % 6L != 0L) return;
        for (int i = 0; i < 5; i++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            if (dx * dx + dz * dz > radius * radius) continue;
            BlockPos base = center.offset(dx, -1, dz);
            BlockPos above = base.above();
            if (!level.getBlockState(base).isAir() && level.getBlockState(above).isAir()) {
                level.setBlockAndUpdate(above, Blocks.FIRE.defaultBlockState());
            }
        }
    }

    private static void drawRing(ServerLevel level, Vec3 center, double radius, net.minecraft.core.particles.ParticleOptions particle, int count) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            level.sendParticles(particle, x, center.y + 0.15, z, 1, 0.0, 0.05, 0.0, 0.0);
        }
    }

    private static List<LivingEntity> nearbyLiving(ServerPlayer player, double radius) {
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius));
    }

    private static boolean protectedAlly(ServerPlayer source, LivingEntity target) {
        if (source.isAlliedTo(target)) return true;
        return target instanceof TamableAnimal tamable
            && tamable.getOwnerUUID() != null
            && tamable.getOwnerUUID().equals(source.getUUID());
    }

    private static boolean isChestArmorAllowed(ItemStack stack, int level) {
        if (stack.isEmpty()) return true;
        if (level < 3) return false;
        if (stack.is(Items.LEATHER_CHESTPLATE) || stack.is(Items.GOLDEN_CHESTPLATE) || stack.is(Items.CHAINMAIL_CHESTPLATE)) return true;
        if (level >= 4 && (stack.is(Items.IRON_CHESTPLATE) || stack.is(Items.DIAMOND_CHESTPLATE))) return true;
        return level >= 5 && stack.is(Items.NETHERITE_CHESTPLATE);
    }

    private static String formatSeconds(int ticks) {
        return String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0);
    }

    private record PendingMeteor(
        ServerLevel level,
        UUID owner,
        Vec3 impact,
        long impactTick,
        int radius,
        float damage
    ) {}
}
