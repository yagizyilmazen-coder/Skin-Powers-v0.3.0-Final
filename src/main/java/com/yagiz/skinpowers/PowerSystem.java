package com.yagiz.skinpowers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
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
    private static final List<PendingHellfireOrb> HELLFIRE_ORBS = new ArrayList<>();
    private static final List<PendingWaterOrb> WATER_ORBS = new ArrayList<>();
    private static final List<PendingWhirlpool> WHIRLPOOLS = new ArrayList<>();
    private static final List<PendingTsunami> TSUNAMIS = new ArrayList<>();
    private static final Map<UUID, Long> LAST_SKY_IMPACT = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_FLIGHT_POSITION = new HashMap<>();
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
        target.hurtServer(serverLevel, serverLevel.damageSources().playerAttack(serverPlayer), 4.0F);
        target.setRemainingFireTicks(80);
        serverLevel.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 1.0, target.getZ(), 12, 0.35, 0.45, 0.35, 0.02);
        creditMastery(serverPlayer, data, 2, now, 20L);
        return InteractionResult.PASS;
    }

    public static void tickServer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player);
        }
        tickMeteors();
        tickHellfireOrbs();
        tickWaterOrbs();
        tickWhirlpools();
        tickTsunamis();

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
            case WATER -> tickWater(player, data, level, now);
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
            } else if (player.getDeltaMovement().lengthSqr() > 0.08 && now % 2L == 0L) {
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

        Vec3 currentPosition = player.position();
        Vec3 previousPosition = LAST_FLIGHT_POSITION.put(player.getUUID(), currentPosition);
        if (!temporaryFlight) {
            LAST_FLIGHT_POSITION.remove(player.getUUID());
            return;
        }

        if (data.unlockedLevel() >= 5 && player.getDeltaMovement().lengthSqr() > 0.42) {
            // Gökyüzü Hâkimiyeti yüksek hızlı çarpma hasarını büyük ölçüde emer.
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 6, 2, false, false, true));
            player.fallDistance = 0.0F;
        }

        if (data.unlockedLevel() >= 5 && previousPosition != null
            && previousPosition.distanceToSqr(currentPosition) <= 36.0
            && player.getDeltaMovement().lengthSqr() > 0.42) {
            long lastImpact = LAST_SKY_IMPACT.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
            if (now - lastImpact >= 24L) {
                int stage = data.masteryStage(5);
                double hitRadius = 2.25 + stage * 0.20;
                AABB sweptArea = new AABB(previousPosition, currentPosition).inflate(hitRadius);
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, sweptArea)) {
                    if (target == player || protectedAlly(player, target)) continue;
                    Vec3 targetCenter = target.getEyePosition();
                    if (distanceToSegmentSqr(targetCenter, previousPosition, currentPosition) > hitRadius * hitRadius) continue;

                    target.hurtServer(level, level.damageSources().playerAttack(player), 12.0F + stage * 2.0F);
                    Vec3 push = player.getDeltaMovement();
                    if (push.lengthSqr() < 0.0001) push = player.getLookAngle();
                    push = push.normalize().scale(1.25 + stage * 0.14);
                    target.push(push.x, 0.42, push.z);

                    // Darbenin oyuncuya geri dönmesini azalt: hız yumuşatılır ve düşüş birikimi sıfırlanır.
                    player.setDeltaMovement(player.getDeltaMovement().scale(0.58));
                    player.fallDistance = 0.0F;
                    player.hurtMarked = true;
                    LAST_SKY_IMPACT.put(player.getUUID(), now);
                    level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 0.7, target.getZ(), 36, 0.9, 0.9, 0.9, 0.11);
                    creditMastery(player, data, 5, now, 24L);
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

    private static void tickWater(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        if (data.unlockedLevel() >= 1 && isEyesInWater(player, level)) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 40, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, false, true));
            if (player.getDeltaMovement().lengthSqr() > 0.015) {
                creditMastery(player, data, 1, now, 600L);
            }
        }

        if (data.waterArmorUntil() > now) {
            int stage = data.masteryStage(4);
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 35, stage >= 3 ? 2 : 1, false, false, true));
            player.setRemainingFireTicks(0);
            player.fallDistance = 0.0F;

            if (now % 2L == 0L) {
                drawWaterArmorVisual(level, player.position(), 1.65 + stage * 0.12, now);
            }

            AABB shield = player.getBoundingBox().inflate(3.3 + stage * 0.2);
            for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, shield)) {
                if (projectile.getOwner() == player) continue;
                Vec3 away = projectile.position().subtract(player.position());
                if (away.lengthSqr() < 0.0001) away = player.getLookAngle().scale(-1.0);
                away = away.normalize();
                double speed = Math.max(0.85, projectile.getDeltaMovement().length() + 0.25);
                projectile.setDeltaMovement(away.scale(speed).add(0.0, 0.10, 0.0));
                projectile.setOwner(player);
            }

            if (now % 10L == 0L) {
                for (LivingEntity target : nearbyLiving(player, 2.7 + stage * 0.2)) {
                    if (target == player || protectedAlly(player, target)) continue;
                    Vec3 away = target.position().subtract(player.position());
                    if (away.lengthSqr() > 0.0001) {
                        away = away.normalize().scale(0.22 + stage * 0.03);
                        target.push(away.x, 0.05, away.z);
                    }
                }
            }
        } else if (data.waterArmorUntil() != 0L) {
            data.setWaterArmorUntil(0L);
            player.sendSystemMessage(Component.literal("Okyanus Zırhı sona erdi."));
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
            case WATER -> useWater(player, data, power, now);
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
        } else if (data.powerClass() == PowerClass.WATER) {
            player.sendSystemMessage(Component.literal("Suda Yaşam otomatik; diğer Su güçleri R ile çalışır."));
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

    private static boolean useWater(ServerPlayer player, PlayerPowerData data, int power, long now) {
        ServerLevel level = (ServerLevel) player.level();
        int stage = data.masteryStage(power);
        switch (power) {
            case 1 -> {
                player.sendSystemMessage(Component.literal("Suda Yaşam, suya girdiğinde otomatik çalışır."));
                return false;
            }
            case 2 -> {
                launchWaterOrb(player, stage);
                data.setCooldown(2, now, Math.max(80, 120 - stage * 10));
                return true;
            }
            case 3 -> {
                Vec3 look = horizontalDirection(player.getLookAngle());
                Vec3 center = player.position().add(look.scale(7.0 + stage));
                long duration = 100L + stage * 15L;
                WHIRLPOOLS.add(new PendingWhirlpool(level, player.getUUID(), center, now + duration, stage));
                level.sendParticles(ParticleTypes.SPLASH, center.x, center.y + 0.55, center.z, 150, 2.8, 0.75, 2.8, 0.10);
                level.sendParticles(ParticleTypes.BUBBLE_POP, center.x, center.y + 0.45, center.z, 70, 2.2, 0.65, 2.2, 0.06);
                level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.65, center.z, 30, 2.4, 0.35, 2.4, 0.025);
                data.setCooldown(3, now, Math.max(220, 280 - stage * 20));
                return true;
            }
            case 4 -> {
                int duration = 240 + stage * 30;
                data.setWaterArmorUntil(now + duration);
                level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1.0, player.getZ(), 70, 1.2, 1.2, 1.2, 0.10);
                level.sendParticles(ParticleTypes.BUBBLE_POP, player.getX(), player.getY() + 1.0, player.getZ(), 32, 0.9, 1.0, 0.9, 0.04);
                data.setCooldown(4, now, Math.max(380, 480 - stage * 30));
                player.sendSystemMessage(Component.literal("Okyanus Zırhı: " + formatSeconds(duration) + " saniye."));
                return true;
            }
            case 5 -> {
                launchTsunami(player, stage);
                data.setCooldown(5, now, Math.max(560, 700 - stage * 45));
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
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 start = player.getEyePosition().add(direction.scale(1.15));
        Vec3 velocity = direction.scale(1.05 + stage * 0.10);
        long now = level.getGameTime();
        HELLFIRE_ORBS.add(new PendingHellfireOrb(
            level,
            player.getUUID(),
            start,
            velocity,
            now + 34L + stage * 4L,
            stage
        ));
        level.sendParticles(ParticleTypes.FLAME, start.x, start.y, start.z, 24, 0.35, 0.35, 0.35, 0.05);
        level.sendParticles(ParticleTypes.LAVA, start.x, start.y, start.z, 5, 0.20, 0.20, 0.20, 0.0);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.3F, 0.72F);
    }

    private static void tickHellfireOrbs() {
        Iterator<PendingHellfireOrb> iterator = HELLFIRE_ORBS.iterator();
        while (iterator.hasNext()) {
            PendingHellfireOrb orb = iterator.next();
            ServerLevel level = orb.level;
            long now = level.getGameTime();
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(orb.owner);
            clearHellfireVisual(orb);

            Vec3 from = orb.position;
            Vec3 to = from.add(orb.velocity);
            Vec3 impact = null;
            LivingEntity directTarget = null;
            int steps = Math.max(3, (int) Math.ceil(Math.sqrt(orb.velocity.lengthSqr()) / 0.24));

            for (int step = 1; step <= steps; step++) {
                double fraction = step / (double) steps;
                Vec3 point = from.add(orb.velocity.scale(fraction));
                BlockState state = level.getBlockState(BlockPos.containing(point));
                if (!state.isAir() && !state.is(Blocks.FIRE)) {
                    impact = point.subtract(orb.velocity.normalize().scale(0.18));
                    break;
                }

                double contactRadius = 0.95 + orb.stage * 0.08;
                AABB contact = new AABB(point, point).inflate(contactRadius);
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, contact)) {
                    if (owner != null && (target == owner || protectedAlly(owner, target))) continue;
                    directTarget = target;
                    impact = target.getEyePosition();
                    break;
                }
                if (impact != null) break;
            }

            if (impact != null || now >= orb.expireTick) {
                clearHellfireVisual(orb);
                impactHellfireOrb(orb, impact == null ? to : impact, directTarget, owner);
                iterator.remove();
                continue;
            }

            orb.position = to;
            placeHellfireVisual(orb, to);
            level.sendParticles(ParticleTypes.FLAME, to.x, to.y, to.z, 24, 0.50, 0.50, 0.50, 0.03);
            level.sendParticles(ParticleTypes.LAVA, to.x, to.y, to.z, 3, 0.24, 0.24, 0.24, 0.0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z, 3, 0.20, 0.20, 0.20, 0.015);
        }
    }

    private static void placeHellfireVisual(PendingHellfireOrb orb, Vec3 position) {
        BlockPos center = BlockPos.containing(position);
        BlockPos[] shape = {center, center.above()};
        for (BlockPos pos : shape) {
            if (!orb.level.getBlockState(pos).isAir()) continue;
            orb.level.setBlockAndUpdate(pos, Blocks.MAGMA_BLOCK.defaultBlockState());
            orb.visualBlocks.add(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    private static void clearHellfireVisual(PendingHellfireOrb orb) {
        for (BlockPos pos : orb.visualBlocks) {
            if (orb.level.getBlockState(pos).is(Blocks.MAGMA_BLOCK)) {
                orb.level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
        orb.visualBlocks.clear();
    }

    private static void impactHellfireOrb(
        PendingHellfireOrb orb,
        Vec3 impact,
        LivingEntity directTarget,
        ServerPlayer owner
    ) {
        ServerLevel level = orb.level;
        if (directTarget != null) {
            float directDamage = 13.0F + orb.stage * 2.5F;
            directTarget.hurtServer(level,
                owner == null ? level.damageSources().generic() : level.damageSources().playerAttack(owner),
                directDamage);
            directTarget.setRemainingFireTicks(180 + orb.stage * 35);
        }

        double blastRadius = 3.2 + orb.stage * 0.45;
        AABB blastArea = new AABB(impact, impact).inflate(blastRadius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, blastArea)) {
            if (target == directTarget) continue;
            if (owner != null && (target == owner || protectedAlly(owner, target))) continue;
            double distance = Math.sqrt(target.distanceToSqr(impact));
            if (distance > blastRadius) continue;
            float damage = (float) Math.max(4.0, (9.0 + orb.stage * 1.8) * (1.0 - distance / (blastRadius + 1.0)));
            target.hurtServer(level,
                owner == null ? level.damageSources().generic() : level.damageSources().playerAttack(owner),
                damage);
            target.setRemainingFireTicks(130 + orb.stage * 25);
            Vec3 push = target.position().subtract(impact);
            if (push.lengthSqr() > 0.0001) {
                push = push.normalize().scale(0.90 + orb.stage * 0.08);
                target.push(push.x, 0.36, push.z);
            }
        }

        level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 5, 0.55, 0.55, 0.55, 0.0);
        level.sendParticles(ParticleTypes.FLAME, impact.x, impact.y, impact.z, 70, 1.25, 1.25, 1.25, 0.11);
        level.sendParticles(ParticleTypes.LAVA, impact.x, impact.y, impact.z, 15, 0.85, 0.85, 0.85, 0.0);
        level.playSound(null, BlockPos.containing(impact), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.15F, 1.05F);
    }

    private static void launchWaterOrb(ServerPlayer player, int stage) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 start = player.getEyePosition().add(direction.scale(1.1));
        Vec3 velocity = direction.scale(0.88 + stage * 0.08);
        long now = level.getGameTime();
        WATER_ORBS.add(new PendingWaterOrb(level, player.getUUID(), start, velocity, now + 32L + stage * 3L, stage));
        level.sendParticles(ParticleTypes.SPLASH, start.x, start.y, start.z, 95, 0.72, 0.72, 0.72, 0.09);
        level.sendParticles(ParticleTypes.BUBBLE_POP, start.x, start.y, start.z, 40, 0.58, 0.58, 0.58, 0.045);
        level.sendParticles(ParticleTypes.CLOUD, start.x, start.y, start.z, 16, 0.50, 0.50, 0.50, 0.02);
    }

    private static void tickWaterOrbs() {
        Iterator<PendingWaterOrb> iterator = WATER_ORBS.iterator();
        while (iterator.hasNext()) {
            PendingWaterOrb orb = iterator.next();
            ServerLevel level = orb.level;
            long now = level.getGameTime();
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(orb.owner);
            Vec3 from = orb.position;
            Vec3 to = from.add(orb.velocity);
            Vec3 impact = null;
            LivingEntity directTarget = null;
            int steps = Math.max(3, (int) Math.ceil(orb.velocity.length() / 0.22));

            for (int step = 1; step <= steps; step++) {
                Vec3 point = from.add(orb.velocity.scale(step / (double) steps));
                BlockPos blockPos = BlockPos.containing(point);
                BlockState state = level.getBlockState(blockPos);
                boolean water = level.getFluidState(blockPos).is(FluidTags.WATER);
                if (!state.isAir() && !water) {
                    impact = point.subtract(orb.velocity.normalize().scale(0.16));
                    break;
                }
                AABB contact = new AABB(point, point).inflate(0.78 + orb.stage * 0.06);
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, contact)) {
                    if (owner != null && (target == owner || protectedAlly(owner, target))) continue;
                    directTarget = target;
                    impact = target.getEyePosition();
                    break;
                }
                if (impact != null) break;
            }

            if (impact != null || now >= orb.expireTick) {
                impactWaterOrb(orb, impact == null ? to : impact, directTarget, owner);
                iterator.remove();
                continue;
            }

            orb.position = to;
            drawWaterOrbVisual(level, to, 0.82 + orb.stage * 0.07, now);
            level.sendParticles(ParticleTypes.SPLASH, from.x, from.y, from.z, 16, 0.26, 0.26, 0.26, 0.025);
            level.sendParticles(ParticleTypes.BUBBLE_POP, from.x, from.y, from.z, 10, 0.24, 0.24, 0.24, 0.018);
        }
    }

    private static void impactWaterOrb(PendingWaterOrb orb, Vec3 impact, LivingEntity directTarget, ServerPlayer owner) {
        ServerLevel level = orb.level;
        Vec3 pushDirection = horizontalDirection(orb.velocity);
        if (directTarget != null) {
            directTarget.hurtServer(level,
                owner == null ? level.damageSources().generic() : level.damageSources().playerAttack(owner),
                5.0F + orb.stage * 1.3F);
            directTarget.setRemainingFireTicks(0);
            Vec3 push = pushDirection.scale(1.25 + orb.stage * 0.14);
            directTarget.push(push.x, 0.30, push.z);
            directTarget.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1, false, true, true));
        }

        double radius = 2.4 + orb.stage * 0.25;
        AABB area = new AABB(impact, impact).inflate(radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (target == directTarget) continue;
            if (owner != null && (target == owner || protectedAlly(owner, target))) continue;
            if (target.distanceToSqr(impact) > radius * radius) continue;
            target.hurtServer(level,
                owner == null ? level.damageSources().generic() : level.damageSources().playerAttack(owner),
                2.0F + orb.stage * 0.65F);
            target.setRemainingFireTicks(0);
            Vec3 push = pushDirection.scale(0.72 + orb.stage * 0.08);
            target.push(push.x, 0.18, push.z);
        }
        level.sendParticles(ParticleTypes.SPLASH, impact.x, impact.y, impact.z, 180, 1.75, 1.25, 1.75, 0.16);
        level.sendParticles(ParticleTypes.BUBBLE_POP, impact.x, impact.y, impact.z, 75, 1.35, 1.0, 1.35, 0.08);
        level.sendParticles(ParticleTypes.CLOUD, impact.x, impact.y + 0.35, impact.z, 30, 1.20, 0.65, 1.20, 0.05);
    }

    private static void tickWhirlpools() {
        Iterator<PendingWhirlpool> iterator = WHIRLPOOLS.iterator();
        while (iterator.hasNext()) {
            PendingWhirlpool whirlpool = iterator.next();
            ServerLevel level = whirlpool.level;
            long now = level.getGameTime();
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(whirlpool.owner);
            double radius = 5.0 + whirlpool.stage * 0.55;

            if (now >= whirlpool.expireTick) {
                AABB finishArea = new AABB(whirlpool.center, whirlpool.center).inflate(radius, 2.5, radius);
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, finishArea)) {
                    if (owner != null && (target == owner || protectedAlly(owner, target))) continue;
                    Vec3 away = target.position().subtract(whirlpool.center);
                    if (away.lengthSqr() > 0.0001) {
                        away = away.normalize().scale(0.85 + whirlpool.stage * 0.08);
                        target.push(away.x, 0.40, away.z);
                    }
                }
                level.sendParticles(ParticleTypes.SPLASH, whirlpool.center.x, whirlpool.center.y + 0.7, whirlpool.center.z, 210, radius * 0.72, 1.4, radius * 0.72, 0.15);
                level.sendParticles(ParticleTypes.BUBBLE_POP, whirlpool.center.x, whirlpool.center.y + 0.6, whirlpool.center.z, 90, radius * 0.58, 1.1, radius * 0.58, 0.08);
                level.sendParticles(ParticleTypes.CLOUD, whirlpool.center.x, whirlpool.center.y + 0.7, whirlpool.center.z, 45, radius * 0.62, 0.55, radius * 0.62, 0.04);
                iterator.remove();
                continue;
            }

            if (now % 2L == 0L) {
                drawWhirlpoolVisual(level, whirlpool.center, radius, now, whirlpool.stage);
            }

            AABB area = new AABB(whirlpool.center, whirlpool.center).inflate(radius, 3.0, radius);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
                if (owner != null && (target == owner || protectedAlly(owner, target))) continue;
                Vec3 toward = whirlpool.center.subtract(target.position());
                double distance = Math.sqrt(toward.x * toward.x + toward.z * toward.z);
                if (distance > radius || distance < 0.05) continue;
                Vec3 pull = new Vec3(toward.x, 0.0, toward.z).normalize().scale(0.16 + (radius - distance) * 0.035 + whirlpool.stage * 0.015);
                target.push(pull.x, -0.015, pull.z);
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, whirlpool.stage >= 2 ? 2 : 1, false, true, true));
                target.setRemainingFireTicks(0);
                if (now % 20L == 0L) {
                    target.hurtServer(level,
                        owner == null ? level.damageSources().generic() : level.damageSources().playerAttack(owner),
                        2.0F + whirlpool.stage * 0.6F);
                }
            }
        }
    }

    private static void launchTsunami(ServerPlayer player, int stage) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 direction = horizontalDirection(player.getLookAngle());
        Vec3 start = player.position().add(direction.scale(2.2));
        long now = level.getGameTime();
        int lifetime = 38 + stage * 4;
        TSUNAMIS.add(new PendingTsunami(level, player.getUUID(), start, direction, now, now + lifetime, stage));
        level.sendParticles(ParticleTypes.SPLASH, start.x, start.y + 2.2, start.z, 320, 4.5, 2.8, 2.0, 0.18);
        level.sendParticles(ParticleTypes.BUBBLE_POP, start.x, start.y + 2.0, start.z, 120, 4.0, 2.4, 1.8, 0.09);
        level.sendParticles(ParticleTypes.CLOUD, start.x, start.y + 4.3, start.z, 70, 4.3, 0.6, 1.8, 0.05);
    }

    private static void tickTsunamis() {
        Iterator<PendingTsunami> iterator = TSUNAMIS.iterator();
        while (iterator.hasNext()) {
            PendingTsunami tsunami = iterator.next();
            ServerLevel level = tsunami.level;
            long now = level.getGameTime();
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(tsunami.owner);
            if (now >= tsunami.expireTick) {
                level.sendParticles(ParticleTypes.SPLASH, tsunami.position.x, tsunami.position.y + 1.4, tsunami.position.z, 260, 3.8, 2.2, 3.8, 0.16);
                level.sendParticles(ParticleTypes.BUBBLE_POP, tsunami.position.x, tsunami.position.y + 1.2, tsunami.position.z, 95, 3.1, 1.7, 3.1, 0.08);
                level.sendParticles(ParticleTypes.CLOUD, tsunami.position.x, tsunami.position.y + 1.8, tsunami.position.z, 50, 3.2, 0.8, 3.2, 0.05);
                iterator.remove();
                continue;
            }

            tsunami.position = tsunami.position.add(tsunami.direction.scale(0.72 + tsunami.stage * 0.035));
            double total = Math.max(1.0, tsunami.expireTick - tsunami.startTick);
            double progress = Math.max(0.0, Math.min(1.0, (now - tsunami.startTick) / total));
            double width = (8.0 + tsunami.stage) * (1.0 - progress * 0.42);
            double height = (4.5 + tsunami.stage * 0.28) * (1.0 - progress * 0.48);
            double thickness = 1.7 + tsunami.stage * 0.08;
            Vec3 right = new Vec3(-tsunami.direction.z, 0.0, tsunami.direction.x);

            if (now % 2L == 0L) {
                drawTsunamiVisual(level, tsunami.position, tsunami.direction, right, width, height, thickness, now);
            } else {
                level.sendParticles(ParticleTypes.SPLASH,
                    tsunami.position.x, tsunami.position.y + height * 0.52, tsunami.position.z,
                    80, width * 0.38, height * 0.40, thickness * 0.48, 0.03);
                level.sendParticles(ParticleTypes.CLOUD,
                    tsunami.position.x + tsunami.direction.x * 0.7,
                    tsunami.position.y + height,
                    tsunami.position.z + tsunami.direction.z * 0.7,
                    24, width * 0.40, 0.20, thickness * 0.52, 0.03);
            }

            double boxRadius = Math.max(width / 2.0, thickness) + 1.0;
            AABB area = new AABB(tsunami.position, tsunami.position).inflate(boxRadius, height + 1.0, boxRadius);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
                if (owner != null && (target == owner || protectedAlly(owner, target))) continue;
                Vec3 relative = target.position().subtract(tsunami.position);
                double lateral = relative.dot(right);
                double forward = relative.dot(tsunami.direction);
                if (Math.abs(lateral) > width / 2.0 + 0.8 || Math.abs(forward) > thickness + 0.8) continue;
                if (relative.y < -1.5 || relative.y > height + 1.0) continue;

                Vec3 push = tsunami.direction.scale(0.82 + tsunami.stage * 0.10);
                target.push(push.x, 0.18, push.z);
                target.setRemainingFireTicks(0);
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 55, tsunami.stage >= 2 ? 2 : 1, false, true, true));

                long lastDamage = tsunami.lastDamage.getOrDefault(target.getUUID(), Long.MIN_VALUE / 2);
                if (now - lastDamage >= 20L) {
                    float damage = lastDamage < -1000L ? 8.0F + tsunami.stage * 1.4F : 3.0F + tsunami.stage * 0.55F;
                    target.hurtServer(level,
                        owner == null ? level.damageSources().generic() : level.damageSources().playerAttack(owner),
                        damage);
                    tsunami.lastDamage.put(target.getUUID(), now);
                }
            }
        }
    }


    private static void drawWaterOrbVisual(ServerLevel level, Vec3 center, double radius, long now) {
        double spin = now * 0.34;
        level.sendParticles(ParticleTypes.SPLASH, center.x, center.y, center.z,
            58, radius * 0.52, radius * 0.52, radius * 0.52, 0.025);
        level.sendParticles(ParticleTypes.BUBBLE_POP, center.x, center.y, center.z,
            22, radius * 0.42, radius * 0.42, radius * 0.42, 0.018);

        int points = 14;
        for (int ring = -1; ring <= 1; ring++) {
            double y = center.y + ring * radius * 0.48;
            double ringRadius = radius * (ring == 0 ? 1.0 : 0.72);
            for (int i = 0; i < points; i++) {
                double angle = spin + Math.PI * 2.0 * i / points + ring * 0.45;
                double x = center.x + Math.cos(angle) * ringRadius;
                double z = center.z + Math.sin(angle) * ringRadius;
                level.sendParticles(ParticleTypes.SPLASH, x, y, z, 2, 0.035, 0.035, 0.035, 0.012);
            }
        }
        level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + radius * 0.72, center.z,
            5, radius * 0.42, 0.08, radius * 0.42, 0.015);
    }

    private static void drawWhirlpoolVisual(ServerLevel level, Vec3 center, double radius, long now, int stage) {
        double spin = now * (0.24 + stage * 0.018);
        int layers = 5;
        for (int layer = 0; layer < layers; layer++) {
            double fraction = layer / (double) (layers - 1);
            double layerRadius = radius * (1.0 - fraction * 0.72);
            double y = center.y + 0.18 + fraction * (1.9 + stage * 0.12);
            int points = Math.max(14, 30 - layer * 3);
            for (int i = 0; i < points; i++) {
                double angle = spin * (1.0 + fraction * 0.55) + Math.PI * 2.0 * i / points + layer * 0.55;
                double wobble = Math.sin(angle * 3.0 + now * 0.12) * 0.12;
                double x = center.x + Math.cos(angle) * (layerRadius + wobble);
                double z = center.z + Math.sin(angle) * (layerRadius + wobble);
                level.sendParticles(ParticleTypes.SPLASH, x, y, z, 2, 0.07, 0.10, 0.07, 0.02);
                if ((i + layer) % 3 == 0) {
                    level.sendParticles(ParticleTypes.BUBBLE_POP, x, y + 0.08, z, 1, 0.04, 0.06, 0.04, 0.012);
                }
            }
        }

        level.sendParticles(ParticleTypes.SPLASH, center.x, center.y + 0.9, center.z,
            90, radius * 0.48, 1.15, radius * 0.48, 0.065);
        level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.38, center.z,
            18, radius * 0.72, 0.16, radius * 0.72, 0.025);
    }

    private static void drawWaterArmorVisual(ServerLevel level, Vec3 center, double radius, long now) {
        double spin = now * 0.28;
        for (int band = 0; band < 3; band++) {
            double y = center.y + 0.35 + band * 0.58;
            double bandRadius = radius * (1.0 - band * 0.08);
            int points = 18;
            for (int i = 0; i < points; i++) {
                double angle = spin * (band % 2 == 0 ? 1.0 : -1.0) + Math.PI * 2.0 * i / points + band * 0.7;
                double x = center.x + Math.cos(angle) * bandRadius;
                double z = center.z + Math.sin(angle) * bandRadius;
                level.sendParticles(ParticleTypes.SPLASH, x, y, z, 2, 0.045, 0.08, 0.045, 0.015);
                if ((i + band) % 4 == 0) {
                    level.sendParticles(ParticleTypes.BUBBLE_POP, x, y, z, 1, 0.03, 0.05, 0.03, 0.01);
                }
            }
        }
        level.sendParticles(ParticleTypes.SPLASH, center.x, center.y + 1.0, center.z,
            30, radius * 0.52, 0.92, radius * 0.52, 0.035);
    }

    private static void drawTsunamiVisual(
        ServerLevel level,
        Vec3 center,
        Vec3 direction,
        Vec3 right,
        double width,
        double height,
        double thickness,
        long now
    ) {
        int columns = Math.max(11, (int) Math.ceil(width * 1.55));
        double crestForward = 0.72 + Math.sin(now * 0.22) * 0.10;

        // Her sütun çok sayıda su parçacığı üretir; böylece seyrek noktalar yerine dolu bir su duvarı görünür.
        for (int column = 0; column <= columns; column++) {
            double lateral = -width / 2.0 + width * column / columns;
            Vec3 columnBase = center.add(right.scale(lateral));
            int waterCount = Math.max(18, (int) Math.round(height * 7.0));
            level.sendParticles(
                ParticleTypes.SPLASH,
                columnBase.x,
                columnBase.y + height * 0.50,
                columnBase.z,
                waterCount,
                Math.max(0.12, width / columns * 0.48),
                height * 0.48,
                thickness * 0.46,
                0.028
            );
            level.sendParticles(
                ParticleTypes.BUBBLE_POP,
                columnBase.x,
                columnBase.y + height * 0.46,
                columnBase.z,
                Math.max(6, waterCount / 4),
                Math.max(0.10, width / columns * 0.40),
                height * 0.40,
                thickness * 0.38,
                0.018
            );

            Vec3 crest = columnBase.add(direction.scale(crestForward)).add(0.0, height, 0.0);
            level.sendParticles(ParticleTypes.CLOUD, crest.x, crest.y, crest.z,
                7, Math.max(0.12, width / columns * 0.65), 0.20, thickness * 0.62, 0.025);
            level.sendParticles(ParticleTypes.SPLASH, crest.x, crest.y - 0.10, crest.z,
                11, Math.max(0.12, width / columns * 0.55), 0.28, thickness * 0.58, 0.045);

            Vec3 baseSpray = columnBase.add(direction.scale(-thickness * 0.28)).add(0.0, 0.22, 0.0);
            level.sendParticles(ParticleTypes.SPLASH, baseSpray.x, baseSpray.y, baseSpray.z,
                8, Math.max(0.10, width / columns * 0.45), 0.18, thickness * 0.72, 0.055);
        }

        // Dalganın gövdesini dolduran ek hacim; uzaktan bakıldığında da büyük bir su kütlesi olarak seçilir.
        level.sendParticles(ParticleTypes.SPLASH, center.x, center.y + height * 0.52, center.z,
            Math.max(90, (int) Math.round(width * height * 3.2)),
            width * 0.42, height * 0.42, thickness * 0.50, 0.022);
        level.sendParticles(ParticleTypes.CLOUD,
            center.x + direction.x * crestForward,
            center.y + height,
            center.z + direction.z * crestForward,
            Math.max(28, (int) Math.round(width * 4.0)),
            width * 0.44, 0.22, thickness * 0.58, 0.032);
    }

    private static Vec3 horizontalDirection(Vec3 direction) {
        Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z);
        if (horizontal.lengthSqr() < 0.0001) return new Vec3(0.0, 0.0, 1.0);
        return horizontal.normalize();
    }

    private static boolean isEyesInWater(ServerPlayer player, ServerLevel level) {
        return level.getFluidState(BlockPos.containing(player.getEyePosition())).is(FluidTags.WATER);
    }

    private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-7) return point.distanceToSqr(start);
        double projection = point.subtract(start).dot(segment) / lengthSqr;
        projection = Math.max(0.0, Math.min(1.0, projection));
        return point.distanceToSqr(start.add(segment.scale(projection)));
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
    }

    private static void scheduleMeteors(ServerPlayer player, PlayerPowerData data, int stage) {
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        Vec3 center = player.position();
        RandomSource random = level.getRandom();
        int count = 8 + stage;

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
        for (PendingHellfireOrb orb : HELLFIRE_ORBS) clearHellfireVisual(orb);
        METEORS.clear();
        HELLFIRE_ORBS.clear();
        WATER_ORBS.clear();
        WHIRLPOOLS.clear();
        TSUNAMIS.clear();
        LAST_FLIGHT_POSITION.clear();
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


    private static final class PendingHellfireOrb {
        private final ServerLevel level;
        private final UUID owner;
        private Vec3 position;
        private final Vec3 velocity;
        private final long expireTick;
        private final int stage;
        private final List<BlockPos> visualBlocks = new ArrayList<>();

        private PendingHellfireOrb(
            ServerLevel level,
            UUID owner,
            Vec3 position,
            Vec3 velocity,
            long expireTick,
            int stage
        ) {
            this.level = level;
            this.owner = owner;
            this.position = position;
            this.velocity = velocity;
            this.expireTick = expireTick;
            this.stage = stage;
        }
    }

    private static final class PendingWaterOrb {
        private final ServerLevel level;
        private final UUID owner;
        private Vec3 position;
        private final Vec3 velocity;
        private final long expireTick;
        private final int stage;

        private PendingWaterOrb(ServerLevel level, UUID owner, Vec3 position, Vec3 velocity, long expireTick, int stage) {
            this.level = level;
            this.owner = owner;
            this.position = position;
            this.velocity = velocity;
            this.expireTick = expireTick;
            this.stage = stage;
        }
    }

    private static final class PendingWhirlpool {
        private final ServerLevel level;
        private final UUID owner;
        private final Vec3 center;
        private final long expireTick;
        private final int stage;

        private PendingWhirlpool(ServerLevel level, UUID owner, Vec3 center, long expireTick, int stage) {
            this.level = level;
            this.owner = owner;
            this.center = center;
            this.expireTick = expireTick;
            this.stage = stage;
        }
    }

    private static final class PendingTsunami {
        private final ServerLevel level;
        private final UUID owner;
        private Vec3 position;
        private final Vec3 direction;
        private final long startTick;
        private final long expireTick;
        private final int stage;
        private final Map<UUID, Long> lastDamage = new HashMap<>();

        private PendingTsunami(ServerLevel level, UUID owner, Vec3 position, Vec3 direction, long startTick, long expireTick, int stage) {
            this.level = level;
            this.owner = owner;
            this.position = position;
            this.direction = direction;
            this.startTick = startTick;
            this.expireTick = expireTick;
            this.stage = stage;
        }
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
