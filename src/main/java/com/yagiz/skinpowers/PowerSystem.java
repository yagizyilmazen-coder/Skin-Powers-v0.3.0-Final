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
    private static final Map<UUID, long[]> LAST_MASTERY_CREDIT = new HashMap<>();
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
            creditMastery(serverPlayer, data, 2, now, 20L);
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

        ServerLevel level = (ServerLevel) player.level();
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
        if (data.wardenHuntUntil() > now) {
            int stage = data.masteryStage(4);
            double radius = 20.0 + stage * 2.0;
            if (now % 5L == 0L) {
                for (LivingEntity living : nearbyLiving(player, radius)) {
                    if (living == player || protectedAlly(player, living)) continue;
                    living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 35, 0, false, false, true));
                    living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 35, stage >= 2 ? 2 : 1, false, false, true));
                    living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 35, stage >= 3 ? 1 : 0, false, false, true));
                    if (now % 20L == 0L && living.getDeltaMovement().horizontalDistanceSqr() > 0.001) {
                        living.hurtServer(level, level.damageSources().playerAttack(player), 2.0F + stage);
                        level.sendParticles(ParticleTypes.SCULK_SOUL, living.getX(), living.getY() + 0.8, living.getZ(), 8, 0.35, 0.45, 0.35, 0.025);
                    }
                }
                drawRing(level, player.position(), Math.min(9.0, radius * 0.42), ParticleTypes.SCULK_SOUL, 34);
            }
        } else if (data.wardenHuntUntil() != 0L || data.visionEnabled()) {
            data.setWardenHuntUntil(0L);
            data.setVisionEnabled(false);
            player.sendSystemMessage(Component.literal("Sculk Avı sona erdi."));
            PlayerDataStore.markDirty();
        }

        if (data.awakeningUntil() > now) {
            int stage = data.masteryStage(5);
            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 35, stage >= 2 ? 3 : 2, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 35, 1, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 35, stage >= 3 ? 1 : 0, false, false, true));
            if (now % 4L == 0L) {
                level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.85, 1.0, 0.85, 0.025);
            }
            if (now % 20L == 0L) {
                double auraRadius = 5.0 + stage * 0.7;
                for (LivingEntity target : nearbyLiving(player, auraRadius)) {
                    if (target == player || protectedAlly(player, target)) continue;
                    target.hurtServer(level, level.damageSources().playerAttack(player), 4.0F + stage * 1.5F);
                    Vec3 push = target.position().subtract(player.position());
                    if (push.lengthSqr() > 0.0001) {
                        push = push.normalize().scale(0.45 + stage * 0.08);
                        target.push(push.x, 0.12, push.z);
                    }
                }
                drawRing(level, player.position(), auraRadius, ParticleTypes.SCULK_SOUL, 42);
            }
        } else if (data.awakeningUntil() != 0L) {
            data.setAwakeningUntil(0L);
            player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 100, 0, false, true, true));
            PlayerDataStore.markDirty();
        }
    }

    private static void tickFlight(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        // 0.3.1 sürümünden kalmış sınırsız mayfly yetkisini temizle.
        if (player.getAbilities().mayfly && !player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }

        if (data.unlockedLevel() >= 1 && data.passiveEnabled()) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false, true));
            player.fallDistance = 0.0F;
        }

        boolean temporaryFlight = data.temporaryElytraUntil() > now;
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (temporaryFlight) {
            if (!chest.is(Items.ELYTRA)) {
                data.setTemporaryElytraUntil(0L);
                player.sendSystemMessage(Component.literal("Süreli Elytra çıkarıldığı için uçuş sona erdi."));
                PlayerDataStore.markDirty();
                temporaryFlight = false;
            } else if (player.fallDistance > 0.0F && now % 2L == 0L) {
                Vec3 back = player.getLookAngle().scale(-0.75);
                level.sendParticles(ParticleTypes.CLOUD,
                    player.getX() + back.x, player.getY() + 0.9, player.getZ() + back.z,
                    4, 0.30, 0.24, 0.30, 0.015);
            }
        } else if (data.temporaryElytraUntil() != 0L) {
            removeTemporaryElytra(player, data);
            player.sendSystemMessage(Component.literal("Süreli Elytra kayboldu."));
            PlayerDataStore.markDirty();
        }

        if (data.skyImpactSlowUntil() > now) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 3, false, false, true));
        }

        if (data.unlockedLevel() >= 5 && temporaryFlight && player.fallDistance > 0.0F
            && player.getDeltaMovement().lengthSqr() > 1.05) {
            long lastImpact = LAST_SKY_IMPACT.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
            if (now - lastImpact >= 70L) {
                int stage = data.masteryStage(5);
                for (LivingEntity target : nearbyLiving(player, 1.9 + stage * 0.15)) {
                    if (target == player || protectedAlly(player, target)) continue;
                    target.hurtServer(level, level.damageSources().playerAttack(player), 12.0F + stage * 2.0F);
                    Vec3 push = target.position().subtract(player.position());
                    if (push.lengthSqr() > 0.0001) {
                        push = push.normalize().scale(1.35 + stage * 0.15);
                        target.push(push.x, 0.45, push.z);
                    }
                    player.setDeltaMovement(player.getDeltaMovement().scale(0.28));
                    data.setSkyImpactSlowUntil(now + 60L);
                    LAST_SKY_IMPACT.put(player.getUUID(), now);
                    level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 0.7, target.getZ(), 32, 0.8, 0.8, 0.8, 0.10);
                    level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9F, 1.45F);
                    creditMastery(player, data, 5, now, 60L);
                    break;
                }
            }
        }
    }

    private static void tickFire(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        if (data.unlockedLevel() >= 1) {
            boolean preventedFire = player.getRemainingFireTicks() > 0;
            player.setRemainingFireTicks(0);
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, true));
            if (preventedFire) creditMastery(player, data, 1, now, 200L);
        }

        if (data.fireRingUntil() > now) {
            int stage = data.masteryStage(3);
            double radius = 10.0 + stage * 0.6;
            if (now % 20L == 0L) {
                for (LivingEntity target : nearbyLiving(player, radius)) {
                    if (target == player || protectedAlly(player, target)) continue;
                    target.hurtServer(level, level.damageSources().playerAttack(player), 2.5F + stage * 0.5F);
                    target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 70));
                }
            }
            if (now % 3L == 0L) {
                drawRing(level, player.position(), radius, ParticleTypes.FLAME, 48);
                igniteSparseGround(level, player.blockPosition(), (int) Math.ceil(radius), now);
            }
        } else if (data.fireRingUntil() != 0L) {
            data.setFireRingUntil(0L);
            PlayerDataStore.markDirty();
        }
    }

    public static void useSelectedPower(ServerPlayer player, PlayerPowerData data) {
        if (data.powerClass() == PowerClass.NONE || data.unlockedLevel() == 0) {
            player.sendSystemMessage(Component.literal("Önce O ekranından bir seviye açmalısın."));
            return;
        }
        int power = data.selectedPower();
        if (power > data.unlockedLevel()) return;

        long now = player.level().getGameTime();
        int remaining = data.cooldownRemaining(power, now);
        if (remaining > 0) {
            player.sendSystemMessage(Component.literal("Güç " + formatSeconds(remaining) + " saniye sonra hazır."));
            return;
        }

        boolean used = switch (data.powerClass()) {
            case WARDEN -> useWarden(player, data, power, now);
            case FLIGHT -> useFlight(player, data, power, now);
            case FIRE -> useFire(player, data, power, now);
            default -> false;
        };

        if (used) {
            recordMasteryUse(player, data, power);
            ServerNetworking.sync(player);
        }
    }

    public static void toggleSelectedFeature(ServerPlayer player, PlayerPowerData data) {
        boolean changed = false;
        long now = player.level().getGameTime();
        if (data.powerClass() == PowerClass.FLIGHT && data.unlockedLevel() >= 1) {
            int remaining = data.cooldownRemaining(1, now);
            if (remaining > 0) {
                player.sendSystemMessage(Component.literal("Yavaş Düşüş " + formatSeconds(remaining) + " saniye sonra değiştirilebilir."));
                return;
            }
            data.togglePassive();
            data.setCooldown(1, now, 40);
            recordMasteryUse(player, data, 1);
            changed = true;
            player.sendSystemMessage(Component.literal("Yavaş Düşüş: " + (data.passiveEnabled() ? "AÇIK" : "KAPALI")));
        } else if (data.powerClass() == PowerClass.WARDEN && data.unlockedLevel() >= 4) {
            player.sendSystemMessage(Component.literal("Sculk Avı aç/kapat değildir; 4. gücü seçip R ile kullan."));
        } else if (data.powerClass() == PowerClass.FIRE) {
            player.sendSystemMessage(Component.literal("Ateş sınıfındaki güçler R ile veya otomatik olarak çalışır."));
        }
        if (changed) {
            PlayerDataStore.markDirty();
            ServerNetworking.sync(player);
        }
    }

    public static void tryRocketlessLaunch(ServerPlayer player, PlayerPowerData data) {
        long now = player.level().getGameTime();
        if (performRocketlessLaunch(player, data, now)) {
            recordMasteryUse(player, data, 3);
            ServerNetworking.sync(player);
        }
    }

    private static boolean performRocketlessLaunch(ServerPlayer player, PlayerPowerData data, long now) {
        if (data.powerClass() != PowerClass.FLIGHT || data.unlockedLevel() < 3) return false;
        if (data.temporaryElytraUntil() <= now || !player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            player.sendSystemMessage(Component.literal("Önce Süreli Elytra gücünü açmalısın."));
            return false;
        }
        int remaining = data.cooldownRemaining(3, now);
        if (remaining > 0) return false;

        int stage = data.masteryStage(3);
        Vec3 look = player.getLookAngle();
        player.setDeltaMovement(look.x * (1.0 + stage * 0.14), 1.25 + stage * 0.14, look.z * (1.0 + stage * 0.14));
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        data.setCooldown(3, now, Math.max(70, 150 - stage * 20));
        ((ServerLevel) player.level()).sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 30, 0.6, 0.25, 0.6, 0.10);
        ((ServerLevel) player.level()).playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.9F, 1.2F);
        return true;
    }

    private static void removeTemporaryElytra(ServerPlayer player, PlayerPowerData data) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.is(Items.ELYTRA)) player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        data.setTemporaryElytraUntil(0L);
    }

    private static boolean useWarden(ServerPlayer player, PlayerPowerData data, int power, long now) {
        ServerLevel level = (ServerLevel) player.level();
        int stage = data.masteryStage(power);
        switch (power) {
            case 1 -> {
                int duration = 400 + stage * 100;
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, duration, stage >= 2 ? 2 : 1, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, duration, stage >= 3 ? 2 : 1, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, stage >= 2 ? 1 : 0, false, true, true));
                data.setCooldown(1, now, Math.max(600, 900 - stage * 100));
                level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 0.9F);
                level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 28, 0.7, 0.8, 0.7, 0.025);
                return true;
            }
            case 2 -> {
                double radius = 7.0 + stage * 1.2;
                for (LivingEntity target : nearbyLiving(player, radius)) {
                    if (target == player || protectedAlly(player, target)) continue;
                    target.hurtServer(level, level.damageSources().playerAttack(player), 10.0F + stage * 2.0F);
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100 + stage * 20, stage >= 2 ? 3 : 2, false, true, true));
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100 + stage * 20, stage >= 3 ? 2 : 1, false, true, true));
                    Vec3 push = target.position().subtract(player.position());
                    if (push.lengthSqr() > 0.0001) {
                        push = push.normalize().scale(0.9 + stage * 0.12);
                        target.push(push.x, 0.45, push.z);
                    }
                }
                drawRing(level, player.position(), radius, ParticleTypes.SCULK_SOUL, 68);
                level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1.5F, 0.72F);
                data.setCooldown(2, now, Math.max(360, 600 - stage * 60));
                return true;
            }
            case 3 -> {
                sonicBlast(player, data, stage);
                data.setCooldown(3, now, Math.max(220, 380 - stage * 40));
                return true;
            }
            case 4 -> {
                int duration = 400 + stage * 100;
                data.setWardenHuntUntil(now + duration);
                data.setVisionEnabled(true);
                data.setCooldown(4, now, Math.max(600, 900 - stage * 90));
                player.sendSystemMessage(Component.literal("Sculk Avı başladı: " + formatSeconds(duration) + " saniye."));
                level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.9F, 1.35F);
                level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 46, 1.0, 1.0, 1.0, 0.035);
                return true;
            }
            case 5 -> {
                int duration = 600 + stage * 100;
                data.setAwakeningUntil(now + duration);
                data.setCooldown(5, now, Math.max(1500, 2400 - stage * 180));
                level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.6F, 0.68F);
                level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 85, 1.3, 1.3, 1.3, 0.055);
                return true;
            }
            default -> { return false; }
        }
    }

    private static boolean useFlight(ServerPlayer player, PlayerPowerData data, int power, long now) {
        int stage = data.masteryStage(power);
        if (power == 1) {
            data.togglePassive();
            data.setCooldown(1, now, 40);
            player.sendSystemMessage(Component.literal("Yavaş Düşüş: " + (data.passiveEnabled() ? "AÇIK" : "KAPALI")));
            return true;
        }
        if (power == 2) {
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            if (!chest.isEmpty()) {
                player.sendSystemMessage(Component.literal("Süreli Elytra için göğüs zırhı yuvasını boşaltmalısın."));
                return false;
            }
            int duration = 400 + stage * 100;
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
            data.setTemporaryElytraUntil(now + duration);
            data.setCooldown(2, now, Math.max(700, 1000 - stage * 100));
            ((ServerLevel) player.level()).sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 36, 0.8, 0.8, 0.8, 0.05);
            ((ServerLevel) player.level()).playSound(null, player.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1.0F, 1.1F);
            player.sendSystemMessage(Component.literal("Süreli Elytra takıldı: " + formatSeconds(duration) + " saniye."));
            return true;
        }
        if (power == 3) {
            return performRocketlessLaunch(player, data, now);
        }
        if (power == 4) {
            airBlast(player, stage);
            data.setCooldown(4, now, Math.max(160, 300 - stage * 35));
            return true;
        }
        if (power == 5) {
            player.sendSystemMessage(Component.literal("Gökyüzü Hâkimiyeti, süreli Elytra ile hızlı çarpışmada otomatik çalışır."));
            return false;
        }
        return false;
    }

    private static boolean useFire(ServerPlayer player, PlayerPowerData data, int power, long now) {
        ServerLevel level = (ServerLevel) player.level();
        int stage = data.masteryStage(power);
        switch (power) {
            case 1 -> {
                player.sendSystemMessage(Component.literal("Ateş bağışıklığı sürekli aktif."));
                return false;
            }
            case 2 -> {
                player.sendSystemMessage(Component.literal("Alevli yakın dövüş, saldırdığında otomatik çalışır."));
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
                hellfireBeam(player, stage);
                data.setCooldown(4, now, Math.max(240, 360 - stage * 40));
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
        ServerLevel level = (ServerLevel) player.level();
        Vec3 origin = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        double range = 18.0 + stage * 2.5;
        List<LivingEntity> candidates = nearbyLiving(player, range);
        List<LivingEntity> lineTargets = new ArrayList<>();

        for (LivingEntity target : candidates) {
            if (target == player || protectedAlly(player, target)) continue;
            Vec3 to = target.getEyePosition().subtract(origin);
            double forward = to.dot(look);
            if (forward <= 0.0 || forward > range) continue;
            double side = to.subtract(look.scale(forward)).length();
            if (side <= 1.7 + stage * 0.3) lineTargets.add(target);
        }

        if (stage == 0 && lineTargets.size() > 2) {
            lineTargets.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
            lineTargets = new ArrayList<>(lineTargets.subList(0, 2));
        }

        for (LivingEntity target : lineTargets) {
            target.hurtServer(level, level.damageSources().playerAttack(player), 14.0F + stage * 3.0F);
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80 + stage * 20, 0, false, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 70 + stage * 15, stage >= 2 ? 2 : 1, false, true, true));
            Vec3 push = target.position().subtract(player.position());
            if (push.lengthSqr() > 0.0001) {
                push = push.normalize().scale(1.15 + stage * 0.1);
                target.push(push.x, 0.28, push.z);
            }
        }

        for (double distance = 1.0; distance <= range; distance += 1.25) {
            Vec3 point = origin.add(look.scale(distance));
            level.sendParticles(ParticleTypes.SONIC_BOOM, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6F, 0.92F);
    }

    private static void hellfireBeam(ServerPlayer player, int stage) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 origin = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        double range = 25.0 + stage * 3.0;
        double travelled = range;
        Vec3 impact = origin.add(look.scale(range));

        for (double distance = 0.5; distance <= range; distance += 0.5) {
            Vec3 point = origin.add(look.scale(distance));
            BlockState state = level.getBlockState(BlockPos.containing(point));
            if (!state.isAir() && !state.is(Blocks.FIRE)) {
                impact = point.subtract(look.scale(0.35));
                travelled = distance;
                break;
            }
            impact = point;
            if (((int) (distance * 2.0)) % 2 == 0) {
                level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 3, 0.10, 0.10, 0.10, 0.01);
                level.sendParticles(ParticleTypes.LAVA, point.x, point.y, point.z, 1, 0.06, 0.06, 0.06, 0.0);
            }
        }

        double beamRadius = 1.25 + stage * 0.18;
        AABB search = player.getBoundingBox().inflate(range + 2.0);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, search)) {
            if (target == player || protectedAlly(player, target)) continue;
            Vec3 toTarget = target.getEyePosition().subtract(origin);
            double forward = toTarget.dot(look);
            if (forward <= 0.0 || forward > travelled + 0.8) continue;
            double sideDistance = toTarget.subtract(look.scale(forward)).length();
            if (sideDistance > beamRadius) continue;

            target.hurtServer(level, level.damageSources().playerAttack(player), 12.0F + stage * 2.0F);
            target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 160 + stage * 40));
            Vec3 push = look.scale(0.55 + stage * 0.08);
            target.push(push.x, 0.12, push.z);
            level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 0.9, target.getZ(), 22, 0.45, 0.55, 0.45, 0.05);
        }

        double blastRadius = 3.2 + stage * 0.45;
        AABB blastArea = new AABB(impact, impact).inflate(blastRadius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, blastArea)) {
            if (target == player || protectedAlly(player, target)) continue;
            double distance = Math.sqrt(target.distanceToSqr(impact));
            if (distance > blastRadius) continue;
            float damage = (float) Math.max(4.0, (9.0 + stage * 1.5) * (1.0 - distance / (blastRadius + 1.0)));
            target.hurtServer(level, level.damageSources().playerAttack(player), damage);
            target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 120));
            Vec3 push = target.position().subtract(impact);
            if (push.lengthSqr() > 0.0001) {
                push = push.normalize().scale(0.85);
                target.push(push.x, 0.35, push.z);
            }
        }

        level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 4, 0.45, 0.45, 0.45, 0.0);
        level.sendParticles(ParticleTypes.FLAME, impact.x, impact.y, impact.z, 55, 1.1, 1.1, 1.1, 0.10);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.4F, 0.65F);
        level.playSound(null, BlockPos.containing(impact), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.1F, 1.1F);
    }

    private static void airBlast(ServerPlayer player, int stage) {
        ServerLevel level = (ServerLevel) player.level();
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
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        Vec3 center = player.position();
        RandomSource random = level.getRandom();
        int count = 7 + stage;

        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 36, 6, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 36, 4, false, true, true));
        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.9F, 1.35F);

        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 3.0 + Math.sqrt(random.nextDouble()) * (9.0 + stage);
            int x = (int) Math.floor(center.x + Math.cos(angle) * distance);
            int z = (int) Math.floor(center.z + Math.sin(angle) * distance);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            Vec3 impact = new Vec3(x + 0.5, y, z + 0.5);

            double approachAngle = angle + Math.PI + (random.nextDouble() - 0.5) * 0.8;
            double horizontalOffset = 8.0 + random.nextDouble() * 7.0;
            Vec3 startPosition = new Vec3(
                impact.x + Math.cos(approachAngle) * horizontalOffset,
                impact.y + 36.0 + random.nextInt(10),
                impact.z + Math.sin(approachAngle) * horizontalOffset
            );

            long spawnTick = now + i * 3L;
            long impactTick = spawnTick + 48L + random.nextInt(10);
            int craterRadius = 4 + stage / 2;
            float damage = 20.0F + stage * 3.0F;
            METEORS.add(new PendingMeteor(level, player.getUUID(), startPosition, impact, spawnTick, impactTick, craterRadius, damage));
        }
    }

    private static void tickMeteors() {
        Iterator<PendingMeteor> iterator = METEORS.iterator();
        while (iterator.hasNext()) {
            PendingMeteor meteor = iterator.next();
            ServerLevel level = meteor.level;
            long now = level.getGameTime();

            clearMeteorVisual(meteor);
            if (now < meteor.spawnTick) continue;

            long remaining = meteor.impactTick - now;
            if (remaining > 0L) {
                double duration = Math.max(1.0, meteor.impactTick - meteor.spawnTick);
                double progress = Math.max(0.0, Math.min(1.0, (now - meteor.spawnTick) / duration));
                double eased = progress * progress * (3.0 - 2.0 * progress);
                Vec3 target = meteor.impact.add(0.0, 1.0, 0.0);
                Vec3 position = new Vec3(
                    meteor.start.x + (target.x - meteor.start.x) * eased,
                    meteor.start.y + (target.y - meteor.start.y) * eased,
                    meteor.start.z + (target.z - meteor.start.z) * eased
                );

                placeMeteorVisual(meteor, position);
                level.sendParticles(ParticleTypes.FLAME, position.x, position.y, position.z, 13, 0.55, 0.55, 0.55, 0.05);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, position.x, position.y + 0.4, position.z, 8, 0.7, 0.7, 0.7, 0.035);
                level.sendParticles(ParticleTypes.LAVA, position.x, position.y, position.z, 3, 0.35, 0.35, 0.35, 0.0);

                if (now % 4L == 0L) {
                    drawRing(level, meteor.impact.add(0.0, 0.15, 0.0), 1.8 + meteor.radius * 0.18, ParticleTypes.FLAME, 20);
                }
                continue;
            }

            impactMeteor(meteor);
            iterator.remove();
        }
    }

    private static void placeMeteorVisual(PendingMeteor meteor, Vec3 position) {
        BlockPos center = BlockPos.containing(position);
        BlockPos[] shape = {
            center,
            center.east(), center.west(), center.north(), center.south(),
            center.above()
        };
        for (BlockPos pos : shape) {
            if (!meteor.level.getBlockState(pos).isAir()) continue;
            meteor.level.setBlockAndUpdate(pos, Blocks.MAGMA_BLOCK.defaultBlockState());
            meteor.visualBlocks.add(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    private static void clearMeteorVisual(PendingMeteor meteor) {
        for (BlockPos pos : meteor.visualBlocks) {
            if (meteor.level.getBlockState(pos).is(Blocks.MAGMA_BLOCK)) {
                meteor.level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
        meteor.visualBlocks.clear();
    }

    public static void clearAllMeteorVisuals() {
        for (PendingMeteor meteor : METEORS) clearMeteorVisual(meteor);
        METEORS.clear();
    }

    private static void impactMeteor(PendingMeteor meteor) {
        clearMeteorVisual(meteor);
        ServerLevel level = meteor.level;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(meteor.owner);
        Vec3 impact = meteor.impact;
        int radius = meteor.radius;

        level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y + 0.6, impact.z, 14, 1.9, 1.2, 1.9, 0.08);
        level.sendParticles(ParticleTypes.FLAME, impact.x, impact.y + 0.6, impact.z, 130, 3.2, 1.8, 3.2, 0.16);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, impact.x, impact.y + 1.0, impact.z, 55, 2.7, 2.1, 2.7, 0.08);
        level.playSound(null, BlockPos.containing(impact), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.2F, 0.62F);

        AABB area = new AABB(impact, impact).inflate(5.0 + radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (owner != null && target == owner) continue;
            double distance = Math.sqrt(target.distanceToSqr(impact));
            if (distance > 5.0 + radius) continue;
            float scaledDamage = (float) Math.max(6.0, meteor.damage * (1.0 - distance / (8.0 + radius)));
            if (owner != null) {
                target.hurtServer(level, level.damageSources().playerAttack(owner), scaledDamage);
            } else {
                target.hurtServer(level, level.damageSources().generic(), scaledDamage);
            }
            Vec3 push = target.position().subtract(impact);
            if (push.lengthSqr() > 0.0001) {
                push = push.normalize().scale(1.7);
                target.push(push.x, 0.8, push.z);
            }
            target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 120));
        }

        if (PlayerDataStore.config().meteorBlockDamage()) {
            carveCrater(level, BlockPos.containing(impact), radius, owner);
        }
        igniteMeteorGround(level, BlockPos.containing(impact), radius + 2);
    }

    private static void igniteMeteorGround(ServerLevel level, BlockPos center, int radius) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < 18; i++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            if (dx * dx + dz * dz > radius * radius) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX() + dx, center.getZ() + dz);
            BlockPos firePos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
            BlockPos below = firePos.below();
            if (level.getBlockState(firePos).isAir() && !level.getBlockState(below).isAir()) {
                level.setBlockAndUpdate(firePos, Blocks.FIRE.defaultBlockState());
            }
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
        return ((ServerLevel) player.level()).getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius));
    }

    private static boolean protectedAlly(ServerPlayer source, LivingEntity target) {
        if (source.isAlliedTo(target)) return true;
        return target instanceof TamableAnimal tamable
            && tamable.getOwner() == source;
    }

    private static boolean isChestArmorAllowed(ItemStack stack, int level) {
        if (stack.isEmpty()) return true;
        if (level < 3) return false;
        if (stack.is(Items.LEATHER_CHESTPLATE) || stack.is(Items.GOLDEN_CHESTPLATE) || stack.is(Items.CHAINMAIL_CHESTPLATE)) return true;
        if (level >= 4 && (stack.is(Items.IRON_CHESTPLATE) || stack.is(Items.DIAMOND_CHESTPLATE))) return true;
        return level >= 5 && stack.is(Items.NETHERITE_CHESTPLATE);
    }

    private static boolean creditMastery(ServerPlayer player, PlayerPowerData data, int power, long now, long minimumInterval) {
        long[] credits = LAST_MASTERY_CREDIT.computeIfAbsent(player.getUUID(), ignored -> new long[5]);
        int index = Math.max(0, Math.min(4, power - 1));
        if (now - credits[index] < minimumInterval) return false;
        credits[index] = now;
        recordMasteryUse(player, data, power);
        return true;
    }

    private static void recordMasteryUse(ServerPlayer player, PlayerPowerData data, int power) {
        int previousStage = data.masteryStage(power);
        data.addMasteryUse(power);
        int newStage = data.masteryStage(power);
        if (newStage > previousStage) {
            player.sendSystemMessage(Component.literal(
                PowerCatalog.powerName(data.powerClass(), power) + " ustalığı: " + PowerCatalog.masteryStageName(newStage)
            ));
        }
        PlayerDataStore.markDirty();
    }

    private static String formatSeconds(int ticks) {
        return String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0);
    }

    private static final class PendingMeteor {
        private final ServerLevel level;
        private final UUID owner;
        private final Vec3 start;
        private final Vec3 impact;
        private final long spawnTick;
        private final long impactTick;
        private final int radius;
        private final float damage;
        private final List<BlockPos> visualBlocks = new ArrayList<>();

        private PendingMeteor(
            ServerLevel level,
            UUID owner,
            Vec3 start,
            Vec3 impact,
            long spawnTick,
            long impactTick,
            int radius,
            float damage
        ) {
            this.level = level;
            this.owner = owner;
            this.start = start;
            this.impact = impact;
            this.spawnTick = spawnTick;
            this.impactTick = impactTick;
            this.radius = radius;
            this.damage = damage;
        }
    }
}
