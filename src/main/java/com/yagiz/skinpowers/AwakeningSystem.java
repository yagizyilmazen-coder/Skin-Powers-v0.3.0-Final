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
        if (data.powerClass() == PowerClass.ANOMALY && data.hasCopiedPower()) data.setCopiedPowerUses(2);
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

        // Uyanış sırasında bütün aktif güçler PowerSystem içinde güçlendirilmiş sürüm olarak çalışır.
        if (now % 5L == 0L) data.reduceAllCooldowns(now, 10);
        switch (data.powerClass()) {
            case WARDEN -> {
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 30, 3, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 2, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 30, 1, false, false, true));
                if (now % 6L == 0L) {
                    level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 30, 1.35, 1.25, 1.35, 0.055);
                    PowerSystem.drawExternalRing(level, player.position(), 4.0 + (now % 20L) * 0.18, ParticleTypes.SCULK_SOUL, 40);
                }
                if (now % 16L == 0L) pulseEnemies(player, level, 10.0, 10.0F, 1.25, 0.62, ParticleTypes.SCULK_SOUL);
            }
            case FIRE -> {
                player.setRemainingFireTicks(0);
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 30, 0, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 30, 2, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 1, false, false, true));
                if (now % 2L == 0L) {
                    double angle = now * 0.22;
                    for (int i = 0; i < 4; i++) {
                        double a = angle + Math.PI * 0.5 * i;
                        double x = player.getX() + Math.cos(a) * 1.8;
                        double z = player.getZ() + Math.sin(a) * 1.8;
                        level.sendParticles(i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.LAVA, x, player.getY() + 1.0, z, 5, 0.18, 0.30, 0.18, 0.025);
                    }
                }
                if (now % 15L == 0L) {
                    pulseEnemies(player, level, 9.5, 9.0F, 0.95, 0.48, ParticleTypes.FLAME);
                    for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(9.5))) {
                        if (target != player && !PowerSystem.isProtectedAlly(player, target)) target.setRemainingFireTicks(Math.max(100, target.getRemainingFireTicks()));
                    }
                }
            }
            case MOON -> {
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 2, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 2, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false, true));
                if (now % 4L == 0L) {
                    level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 24, 1.35, 1.15, 1.35, 0.045);
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 12, 1.0, 0.9, 1.0, 0.025);
                }
                if (now % 18L == 0L) pulseEnemies(player, level, 10.5, 8.0F, 0.85, -0.10, ParticleTypes.END_ROD);
            }
            case ANOMALY -> {
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 3, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 2, false, false, true));
                if (now % 5L == 0L) data.reduceAllCooldowns(now, 18);
                for (net.minecraft.world.entity.projectile.Projectile projectile : level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class, player.getBoundingBox().inflate(8.0))) {
                    if (projectile.getOwner() == player) continue;
                    Vec3 velocity = projectile.getDeltaMovement();
                    if (velocity.lengthSqr() > 0.001) projectile.setDeltaMovement(velocity.scale(-1.08));
                    projectile.setOwner(player);
                }
                if (now % 4L == 0L) {
                    level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0, player.getZ(), 26, 1.4, 1.35, 1.4, 0.065);
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 18, 1.1, 1.1, 1.1, 0.045);
                }
                if (now % 18L == 0L) pulseEnemies(player, level, 9.0, 7.0F, 0.8, 0.35, ParticleTypes.WITCH);
            }
            case MAGNETIC, SAND -> ExpansionPowerSystem.tickAwakening(player, data, level, now);
            case FLIGHT -> {
                if (!player.isCreative() && !player.isSpectator() && !player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();
                }
                player.fallDistance = 0.0F;
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 30, 3, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 2, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 1, false, false, true));
                if (now % 5L == 0L) {
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 28, 1.45, 1.15, 1.45, 0.055);
                    level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.1, player.getZ(), 14, 1.0, 0.95, 1.0, 0.04);
                }
                if (now % 20L == 0L) pulseEnemies(player, level, 9.5, 9.0F, 1.45, 0.85, ParticleTypes.REVERSE_PORTAL);
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
        if (powerClass == PowerClass.MAGNETIC || powerClass == PowerClass.SAND) {
            ExpansionPowerSystem.finishAwakening(player, level, powerClass);
            return;
        }
        double radius = powerClass == PowerClass.ANOMALY || powerClass == PowerClass.FLIGHT ? 13.0 : 11.0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius))) {
            if (target == player || PowerSystem.isProtectedAlly(player, target)) continue;
            float damage = switch (powerClass) {
                case WARDEN -> 20.0F;
                case FIRE -> 19.0F;
                case MOON -> 20.0F;
                case ANOMALY -> 18.0F;
                case FLIGHT -> 18.0F;
                default -> 0.0F;
            };
            if (damage > 0.0F) target.hurtServer(level, level.damageSources().playerAttack(player), damage);
            Vec3 push = target.position().subtract(player.position());
            if (push.lengthSqr() > 0.0001) {
                push = push.normalize().scale(powerClass == PowerClass.FLIGHT ? 2.35 : 1.45);
                target.push(push.x, powerClass == PowerClass.FLIGHT ? 1.15 : 0.72, push.z);
            }
        }
        emitBurst(level, player.position().add(0.0, 0.8, 0.0), powerClass, 110);
        ServerNetworking.sendScreenShake(level, player.position(), 36.0, 1.55F, 18);
    }

    private static void pulseEnemies(ServerPlayer player, ServerLevel level, double radius, float damage,
                                     double horizontalPush, double verticalPush,
                                     net.minecraft.core.particles.ParticleOptions particle) {
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius))) {
            if (target == player || PowerSystem.isProtectedAlly(player, target)) continue;
            target.hurtServer(level, level.damageSources().playerAttack(player), damage);
            Vec3 push = target.position().subtract(player.position());
            if (push.lengthSqr() > 0.0001) {
                push = push.normalize().scale(horizontalPush);
                target.push(push.x, verticalPush, push.z);
            }
        }
        PowerSystem.drawExternalRing(level, player.position(), radius, particle, 84);
    }

    private static void emitBurst(ServerLevel level, Vec3 center, PowerClass powerClass, int count) {
        var particle = switch (powerClass) {
            case WARDEN -> ParticleTypes.SCULK_SOUL;
            case FIRE -> ParticleTypes.FLAME;
            case MOON -> ParticleTypes.END_ROD;
            case ANOMALY -> ParticleTypes.WITCH;
            case FLIGHT -> ParticleTypes.REVERSE_PORTAL;
            case MAGNETIC -> ParticleTypes.CRIT;
            case SAND -> ParticleTypes.POOF;
            default -> ParticleTypes.END_ROD;
        };
        level.sendParticles(particle, center.x, center.y, center.z, count, 1.25, 1.15, 1.25, 0.055);
    }

    public static String awakeningName(PowerClass powerClass) {
        return switch (powerClass) {
            case WARDEN -> "Antik Şehir Uyanışı";
            case FIRE -> "Cehennem Çekirdeği";
            case MOON -> "Tam Tutulma";
            case ANOMALY -> "Sistem Çökmesi";
            case FLIGHT -> "Mor Kıyamet";
            case MAGNETIC -> "Manyetik Çekirdek";
            case SAND -> "Çölün Kalbi";
            default -> "Uyanış";
        };
    }

    private static float awakeningPitch(PowerClass powerClass) {
        return switch (powerClass) {
            case WARDEN -> 0.65F;
            case FIRE -> 0.82F;
            case MOON -> 1.35F;
            case ANOMALY -> 0.55F;
            case FLIGHT -> 0.72F;
            case MAGNETIC -> 0.48F;
            case SAND -> 0.88F;
            default -> 1.0F;
        };
    }
}
