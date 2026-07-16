package com.yagiz.skinpowers;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Ortak Antik Şehir Şarjı, mutasyon, ışın ve cooldown dondurma sistemi. */
public final class AncientChargeSystem {
    public static final int MAX_CHARGE_TICKS = 20 * 20;
    public static final int EXHAUSTION_TICKS = 30 * 20;
    private static final List<PendingBeam> BEAMS = new ArrayList<>();

    private AncientChargeSystem() {}

    public static void clearPendingBeams() {
        BEAMS.clear();
    }

    public static void tick(MinecraftServer server) {
        tickBeams();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerPowerData data = PlayerDataStore.get(player.getUUID());
            tickPlayer(player, data);
        }
    }

    private static void tickPlayer(ServerPlayer player, PlayerPowerData data) {
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();

        if (data.ancientChargeActive(now)) {
            int remaining = (int) Math.max(0L, data.ancientChargeUntil() - now);
            int elapsed = (int) Math.max(0L, now - data.ancientChargeStartedAt());
            int phase = Math.min(3, elapsed / 100);

            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, phase >= 3 ? 1 : 0, false, false, true));
            drawMutation(level, player, phase, now);

            if (remaining == 200 || remaining == 100) {
                player.sendSystemMessage(Component.literal("Antik Şehir Şarjı: " + Math.max(1, remaining / 20) + " saniye • 1 güç hakkı"));
            }
        } else if (data.ancientChargeAvailable()) {
            data.expireAncientCharge(now);
            applyExhaustion(player, data, now, "Antik Şehir enerjisi kullanılmadan dağıldı.");
        }

        if (data.ancientExhausted(now)) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 4, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 30, 0, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 30, 0, false, true, true));
            if (now % 6L == 0L) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.8, player.getZ(), 3, 0.35, 0.55, 0.35, 0.01);
                level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 0.9, player.getZ(), 2, 0.25, 0.4, 0.25, 0.005);
            }
        }
    }

    public static boolean isUsableCharge(PlayerPowerData data, long now, int power) {
        if (!data.ancientChargeActive(now) || power == 6) return false;
        return switch (data.powerClass()) {
            case FLIGHT, FIRE, NATURE -> power != 1;
            case WARDEN -> true;
            default -> false;
        };
    }

    public static boolean grant(ServerPlayer player, int requestedTicks, boolean force) {
        PlayerPowerData data = PlayerDataStore.get(player.getUUID());
        long now = player.level().getGameTime();
        int duration = Math.max(1, Math.min(MAX_CHARGE_TICKS, requestedTicks));

        if (!force && data.ancientExhausted(now)) {
            player.sendSystemMessage(Component.literal("Hedef hâlâ Antik Şehir çöküşünde."));
            return false;
        }
        if (!force && data.ancientChargeActive(now)) {
            player.sendSystemMessage(Component.literal("Hedef zaten Antik Şehir Şarjı taşıyor."));
            return false;
        }

        if (data.ancientChargeAvailable()) data.expireAncientCharge(now);
        if (force) data.clearAncientExhaustion();
        data.beginAncientCharge(now, duration);
        PlayerDataStore.markDirty();
        ServerNetworking.sync(player);

        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.1F, 1.45F);
        level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 65, 1.0, 1.2, 1.0, 0.04);
        level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0, player.getZ(), 42, 0.8, 1.0, 0.8, 0.05);
        player.sendSystemMessage(Component.literal("ANTİK ŞEHİR ŞARJI • " + (duration / 20.0F) + " saniye • 1 güç hakkı"));
        return true;
    }

    public static void clear(ServerPlayer player) {
        PlayerPowerData data = PlayerDataStore.get(player.getUUID());
        long now = player.level().getGameTime();
        if (data.ancientChargeAvailable()) data.expireAncientCharge(now);
        data.clearAncientExhaustion();
        PlayerDataStore.markDirty();
        ServerNetworking.sync(player);
        player.sendSystemMessage(Component.literal("Antik Şehir Şarjı ve çöküş etkisi temizlendi."));
    }

    public static void consume(ServerPlayer player, PlayerPowerData data, int usedPower, long now) {
        if (!isUsableCharge(data, now, usedPower)) return;
        data.consumeAncientCharge(now, usedPower);
        emitChargedBurst((ServerLevel) player.level(), player.position().add(0.0, 1.0, 0.0), data.powerClass(), 1.8);
        applyExhaustion(player, data, now, "Antik Şehir gücü boşaldı. Bedenin çökmeye başladı.");
    }

    private static void applyExhaustion(ServerPlayer player, PlayerPowerData data, long now, String message) {
        data.setAncientExhaustionUntil(now + EXHAUSTION_TICKS);
        PlayerDataStore.markDirty();
        player.sendSystemMessage(Component.literal(message));
        ServerNetworking.sync(player);
    }

    public static boolean beginBeam(ServerPlayer caster, PlayerPowerData data, long now) {
        ServerPlayer target = findBeamTarget(caster, 22.0);
        if (target == null) {
            caster.sendSystemMessage(Component.literal("Işının önünde şarj edilebilecek başka bir oyuncu yok."));
            return false;
        }
        PlayerPowerData targetData = PlayerDataStore.get(target.getUUID());
        if (targetData.ancientChargeActive(now) || targetData.ancientExhausted(now)) {
            caster.sendSystemMessage(Component.literal("Hedef şu anda yeni bir Antik Şehir Şarjı alamaz."));
            return false;
        }

        BEAMS.add(new PendingBeam((ServerLevel) caster.level(), caster.getUUID(), target.getUUID(), now, now + 18L));
        caster.sendSystemMessage(Component.literal(target.getScoreboardName() + " Antik Şehir ışınıyla şarj ediliyor."));
        return true;
    }

    private static ServerPlayer findBeamTarget(ServerPlayer caster, double range) {
        Vec3 origin = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().normalize();
        ServerPlayer best = null;
        double bestForward = range + 1.0;
        for (ServerPlayer candidate : ((ServerLevel) caster.level()).players()) {
            if (candidate == caster || candidate.isSpectator()) continue;
            Vec3 to = candidate.getEyePosition().subtract(origin);
            double forward = to.dot(look);
            if (forward <= 0.6 || forward > range) continue;
            double side = to.subtract(look.scale(forward)).length();
            if (side > 1.35 || !caster.hasLineOfSight(candidate)) continue;
            if (forward < bestForward) {
                best = candidate;
                bestForward = forward;
            }
        }
        return best;
    }

    private static void tickBeams() {
        Iterator<PendingBeam> iterator = BEAMS.iterator();
        while (iterator.hasNext()) {
            PendingBeam beam = iterator.next();
            ServerPlayer caster = beam.level.getServer().getPlayerList().getPlayer(beam.caster);
            ServerPlayer target = beam.level.getServer().getPlayerList().getPlayer(beam.target);
            long now = beam.level.getGameTime();
            if (caster == null || target == null || caster.level() != beam.level || target.level() != beam.level) {
                iterator.remove();
                continue;
            }

            drawBeamAnimation(beam.level, caster, target, now - beam.startTick);
            if (now < beam.finishTick) continue;

            if (caster.distanceToSqr(target) <= 25.0 * 25.0 && caster.hasLineOfSight(target)) {
                if (grant(target, MAX_CHARGE_TICKS, false)) {
                    beam.level.playSound(null, target.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.4F, 1.18F);
                } else {
                    caster.sendSystemMessage(Component.literal("Hedef ışın tamamlanmadan başka bir şarj veya çöküş etkisi aldı."));
                }
            } else {
                caster.sendSystemMessage(Component.literal("Antik Şehir ışını hedef bağlantısını kaybetti."));
            }
            iterator.remove();
        }
    }

    private static void drawBeamAnimation(ServerLevel level, ServerPlayer caster, ServerPlayer target, long age) {
        Vec3 look = caster.getLookAngle().normalize();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() < 0.0001) horizontal = new Vec3(0.0, 0.0, 1.0);
        horizontal = horizontal.normalize();
        Vec3 right = new Vec3(-horizontal.z, 0.0, horizontal.x);
        Vec3 body = caster.position().add(0.0, 1.05, 0.0);
        Vec3 back = body.subtract(horizontal.scale(0.62));
        Vec3 convergence = caster.getEyePosition().add(look.scale(1.35));
        double growth = Math.min(1.0, (age + 1.0) / 8.0);

        Vec3[] roots = {
            back.add(right.scale(0.62)).add(0.0, 0.58, 0.0),
            back.subtract(right.scale(0.62)).add(0.0, 0.58, 0.0),
            back.add(right.scale(0.72)).add(0.0, -0.18, 0.0),
            back.subtract(right.scale(0.72)).add(0.0, -0.18, 0.0)
        };
        for (int i = 0; i < roots.length; i++) {
            double side = i % 2 == 0 ? 1.0 : -1.0;
            double height = i < 2 ? 0.35 : -0.20;
            Vec3 bendFull = roots[i].add(right.scale(side * 0.78)).add(horizontal.scale(0.48)).add(0.0, height, 0.0);
            Vec3 bend = roots[i].lerp(bendFull, growth);
            Vec3 end = roots[i].lerp(convergence, growth);
            drawParticleLine(level, roots[i], bend, ParticleTypes.SCULK_SOUL, 7);
            drawParticleLine(level, bend, end, ParticleTypes.WITCH, 8);
        }

        if (age >= 6L) {
            Vec3 targetPoint = target.getEyePosition();
            drawParticleLine(level, convergence, targetPoint, ParticleTypes.WITCH, 26);
            drawParticleLine(level, convergence, targetPoint, ParticleTypes.SCULK_SOUL, 18);
            if (age % 3L == 0L) {
                level.sendParticles(ParticleTypes.SONIC_BOOM, convergence.x, convergence.y, convergence.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private static void drawMutation(ServerLevel level, ServerPlayer player, int phase, long now) {
        if (now % 2L != 0L) return;
        double radius = 0.50 + phase * 0.14;
        int count = 5 + phase * 3;
        double y = player.getY() + 0.25 + (now % 20L) / 20.0 * 1.45;
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count + now * 0.08;
            level.sendParticles(ParticleTypes.WITCH,
                player.getX() + Math.cos(angle) * radius, y, player.getZ() + Math.sin(angle) * radius,
                1, 0.0, 0.02, 0.0, 0.0);
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 2 + phase, 0.45 + phase * 0.1, 0.75, 0.45 + phase * 0.1, 0.01);

        // Mutasyon ilerledikçe omuz ve sırttan çıkan sivri enerji uzantıları büyür.
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() < 0.0001) horizontal = new Vec3(0.0, 0.0, 1.0);
        horizontal = horizontal.normalize();
        Vec3 right = new Vec3(-horizontal.z, 0.0, horizontal.x);
        Vec3 back = player.position().add(0.0, 1.05, 0.0).subtract(horizontal.scale(0.45));
        double length = 0.55 + phase * 0.28;
        drawParticleLine(level, back.add(right.scale(0.35)).add(0.0, 0.35, 0.0), back.add(right.scale(0.75)).add(horizontal.scale(-length)).add(0.0, 0.65, 0.0), ParticleTypes.WITCH, 6 + phase * 2);
        drawParticleLine(level, back.subtract(right.scale(0.35)).add(0.0, 0.35, 0.0), back.subtract(right.scale(0.75)).add(horizontal.scale(-length)).add(0.0, 0.65, 0.0), ParticleTypes.WITCH, 6 + phase * 2);
        if (phase >= 2) {
            drawParticleLine(level, back.add(right.scale(0.32)).add(0.0, -0.20, 0.0), back.add(right.scale(0.72)).add(horizontal.scale(-length * 0.85)).add(0.0, -0.35, 0.0), ParticleTypes.SCULK_SOUL, 7);
            drawParticleLine(level, back.subtract(right.scale(0.32)).add(0.0, -0.20, 0.0), back.subtract(right.scale(0.72)).add(horizontal.scale(-length * 0.85)).add(0.0, -0.35, 0.0), ParticleTypes.SCULK_SOUL, 7);
        }

        ParticleOptions classParticle = switch (PlayerDataStore.get(player.getUUID()).powerClass()) {
            case FIRE -> ParticleTypes.FLAME;
            case FLIGHT -> ParticleTypes.CLOUD;
            case NATURE -> ParticleTypes.HAPPY_VILLAGER;
            default -> ParticleTypes.SCULK_SOUL;
        };
        level.sendParticles(classParticle, player.getX(), player.getY() + 0.9, player.getZ(), 1 + phase, 0.35, 0.6, 0.35, 0.01);
    }

    public static float damage(float base, boolean charged) {
        return charged ? base * 2.75F : base;
    }

    public static double radius(double base, boolean charged) {
        return charged ? base * 1.45 : base;
    }

    public static int duration(int baseTicks, boolean charged) {
        return charged ? Math.max(baseTicks + 20, (int) Math.round(baseTicks * 1.65)) : baseTicks;
    }

    public static double knockback(double base, boolean charged) {
        return charged ? base * 1.85 : base;
    }

    public static void emitChargedBurst(ServerLevel level, Vec3 center, PowerClass powerClass, double spread) {
        level.sendParticles(ParticleTypes.WITCH, center.x, center.y, center.z, 38, spread, spread * 0.8, spread, 0.08);
        level.sendParticles(ParticleTypes.SCULK_SOUL, center.x, center.y, center.z, 30, spread * 0.8, spread * 0.65, spread * 0.8, 0.04);
        ParticleOptions original = switch (powerClass) {
            case FIRE -> ParticleTypes.FLAME;
            case FLIGHT -> ParticleTypes.CLOUD;
            case NATURE -> ParticleTypes.HAPPY_VILLAGER;
            default -> ParticleTypes.SONIC_BOOM;
        };
        level.sendParticles(original, center.x, center.y, center.z, powerClass == PowerClass.WARDEN ? 2 : 16, spread * 0.55, spread * 0.45, spread * 0.55, 0.03);
    }

    private static void drawParticleLine(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions particle, int points) {
        int safePoints = Math.max(2, points);
        for (int i = 0; i <= safePoints; i++) {
            Vec3 point = from.lerp(to, i / (double) safePoints);
            level.sendParticles(particle, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private record PendingBeam(ServerLevel level, UUID caster, UUID target, long startTick, long finishTick) {}
}
