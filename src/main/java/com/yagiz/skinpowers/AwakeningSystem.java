package com.yagiz.skinpowers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Hasar alıp verdikçe dolan, sabit bekleme süresi olmayan sınıf Uyanış Formları.
 * Çubuktaki enerji doğrudan süreye çevrilir: %100 yaklaşık 24 saniyedir.
 */
public final class AwakeningSystem {
    private AwakeningSystem() {}

    public static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
        if (amount <= 0.0F) return true;
        if (victim instanceof ServerPlayer hurtPlayer) {
            PlayerPowerData hurtData = PlayerDataStore.get(hurtPlayer.getUUID());
            if (hurtData.powerClass() != PowerClass.NONE && !hurtData.classAwakeningActive(hurtPlayer.level().getGameTime())) {
                // Her tür kaynaktan hasar almak çubuğu doldurur.
                hurtData.addAwakeningEnergy(Math.min(10.0F, amount * 1.15F));
                PlayerDataStore.markDirty();
            }
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer attackingPlayer && attackingPlayer != victim) {
            PlayerPowerData attackData = PlayerDataStore.get(attackingPlayer.getUUID());
            if (attackData.powerClass() != PowerClass.NONE && !attackData.classAwakeningActive(attackingPlayer.level().getGameTime())) {
                // Hasar vermek de doldurur; tek büyük vuruş bütün çubuğu bir anda dolduramaz.
                attackData.addAwakeningEnergy(Math.min(8.0F, amount * 0.85F));
                PlayerDataStore.markDirty();
            }
        }
        return true;
    }

    public static void activate(ServerPlayer player, PlayerPowerData data) {
        long now = player.level().getGameTime();
        if (data.powerClass() == PowerClass.NONE) return;
        if (data.classAwakeningActive(now)) {
            player.sendSystemMessage(Component.literal("Uyanış Formu zaten aktif."));
            return;
        }
        int duration = data.beginClassAwakening(now);
        if (duration <= 0) {
            player.sendSystemMessage(Component.literal("Uyanış için çubuğun en az %20 dolu olmalı."));
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        String name = awakeningName(data.powerClass());
        player.sendSystemMessage(Component.literal(name + " başladı: " + PowerSystem.formatSeconds(duration) + " saniye."));
        level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, awakeningPitch(data.powerClass()));
        emitBurst(level, player.position().add(0.0, 1.0, 0.0), data.powerClass(), 84);
        ServerNetworking.sendCastAnimation(level, player.position(), data.powerClass(), 6);
        ServerNetworking.sendScreenShake(level, player.position(), 28.0, 1.15F, 14);
        PlayerDataStore.markDirty();
        ServerNetworking.sync(player);
    }

    public static void tickPlayer(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        if (data.classAwakeningUntil() == 0L) return;
        if (data.classAwakeningUntil() <= now) {
            finish(player, data, level);
            return;
        }

        switch (data.powerClass()) {
            case WARDEN -> {
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 30, 2, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 1, false, false, true));
                if (now % 8L == 0L) level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.75, 0.85, 0.75, 0.025);
            }
            case FIRE -> {
                player.setRemainingFireTicks(0);
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 30, 0, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 30, 1, false, false, true));
                if (now % 7L == 0L) level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.9, player.getZ(), 12, 0.72, 0.8, 0.72, 0.025);
            }
            case NATURE -> {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 30, 1, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 0, false, false, true));
                if (now % 9L == 0L) level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 0.6, player.getZ(), 9, 0.8, 0.65, 0.8, 0.015);
            }
            case ANOMALY -> {
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 1, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 0, false, false, true));
                if (now % 10L == 0L) data.reduceAllCooldowns(now, 7);
                if (now % 6L == 0L) {
                    level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.85, 0.95, 0.85, 0.03);
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.65, 0.75, 0.65, 0.02);
                }
            }
            case FLIGHT -> {
                if (!player.isCreative() && !player.isSpectator()) {
                    if (!player.getAbilities().mayfly) {
                        player.getAbilities().mayfly = true;
                        player.onUpdateAbilities();
                    }
                }
                player.fallDistance = 0.0F;
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 30, 1, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 1, false, false, true));
                if (now % 5L == 0L) {
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 15, 1.15, 0.9, 1.15, 0.025);
                    level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.1, player.getZ(), 7, 0.85, 0.75, 0.85, 0.02);
                }
            }
            default -> { }
        }
    }

    public static boolean isActive(PlayerPowerData data, long now) {
        return data != null && data.classAwakeningActive(now);
    }

    private static void finish(ServerPlayer player, PlayerPowerData data, ServerLevel level) {
        PowerClass powerClass = data.powerClass();
        data.finishClassAwakening();
        if (powerClass == PowerClass.FLIGHT && data.dragonFormUntil() <= level.getGameTime()
            && !player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        emitFinalPulse(player, data, level, powerClass);
        player.sendSystemMessage(Component.literal(awakeningName(powerClass) + " sona erdi."));
        PlayerDataStore.markDirty();
        ServerNetworking.sync(player);
    }

    private static void emitFinalPulse(ServerPlayer player, PlayerPowerData data, ServerLevel level, PowerClass powerClass) {
        double radius = powerClass == PowerClass.ANOMALY ? 10.0 : 8.0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius))) {
            if (target == player || PowerSystem.isProtectedAlly(player, target)) continue;
            float damage = switch (powerClass) {
                case WARDEN -> 10.0F;
                case FIRE -> 9.0F;
                case NATURE -> 6.0F;
                case ANOMALY -> 8.0F;
                case FLIGHT -> 11.0F;
                default -> 0.0F;
            };
            if (damage > 0.0F) target.hurtServer(level, level.damageSources().playerAttack(player), damage);
            Vec3 push = target.position().subtract(player.position());
            if (push.lengthSqr() > 0.0001) {
                push = push.normalize().scale(powerClass == PowerClass.FLIGHT ? 1.2 : 0.75);
                target.push(push.x, 0.28, push.z);
            }
        }
        emitBurst(level, player.position().add(0.0, 0.8, 0.0), powerClass, 110);
        ServerNetworking.sendScreenShake(level, player.position(), 36.0, 1.55F, 18);
    }

    private static void emitBurst(ServerLevel level, Vec3 center, PowerClass powerClass, int count) {
        var particle = switch (powerClass) {
            case WARDEN -> ParticleTypes.SCULK_SOUL;
            case FIRE -> ParticleTypes.FLAME;
            case NATURE -> ParticleTypes.HAPPY_VILLAGER;
            case ANOMALY -> ParticleTypes.WITCH;
            case FLIGHT -> ParticleTypes.REVERSE_PORTAL;
            default -> ParticleTypes.END_ROD;
        };
        level.sendParticles(particle, center.x, center.y, center.z, count, 1.25, 1.15, 1.25, 0.055);
    }

    public static String awakeningName(PowerClass powerClass) {
        return switch (powerClass) {
            case WARDEN -> "Antik Şehir Uyanışı";
            case FIRE -> "Cehennem Çekirdeği";
            case NATURE -> "Kadim Orman";
            case ANOMALY -> "Sistem Çökmesi";
            case FLIGHT -> "Mor Kıyamet";
            default -> "Uyanış";
        };
    }

    private static float awakeningPitch(PowerClass powerClass) {
        return switch (powerClass) {
            case WARDEN -> 0.65F;
            case FIRE -> 0.82F;
            case NATURE -> 1.15F;
            case ANOMALY -> 0.55F;
            case FLIGHT -> 0.72F;
            default -> 1.0F;
        };
    }
}
