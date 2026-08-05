package com.yagiz.skinpowers;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Ortak Antik Şehir Şarjı, mutasyon, ışın, mob güçlendirme ve cooldown dondurma sistemi. */
public final class AncientChargeSystem {
    public static final int MAX_CHARGE_TICKS = 20 * 20;
    public static final int EXHAUSTION_TICKS = 30 * 20;
    public static final int SELF_CHARGE_ANIMATION_TICKS = 40;
    public static final float SELF_CHARGE_HEALTH_COST = 6.0F;

    private static final List<PendingBeam> BEAMS = new ArrayList<>();
    private static final List<PendingSelfCharge> SELF_CHARGES = new ArrayList<>();
    private static final List<ChargedMob> MOB_CHARGES = new ArrayList<>();

    private AncientChargeSystem() {}

    public static void clearPendingBeams() {
        for (PendingBeam beam : BEAMS) clearArmBlocks(beam.level, beam.visualBlocks);
        for (PendingSelfCharge pending : SELF_CHARGES) clearArmBlocks(pending.level, pending.visualBlocks);
        BEAMS.clear();
        SELF_CHARGES.clear();
        MOB_CHARGES.clear();
    }

    public static void tick(MinecraftServer server) {
        tickBeams();
        tickSelfCharges();
        tickChargedMobs();
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
            boolean ready = data.ancientChargeReady(now);

            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, ready ? 1 : 0, false, false, true));
            if (ready) player.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, phase >= 3 ? 1 : 0, false, false, true));
            drawMutation(level, player, phase, now, ready);

            // Kendi kendine şarj sırasında feda edilen üç kalp 20 saniye boyunca geri doldurulamaz.
            if (data.selfSacrificeActive()) {
                float sacrificedCap = Math.max(1.0F, player.getMaxHealth() - SELF_CHARGE_HEALTH_COST);
                if (player.getHealth() > sacrificedCap) player.setHealth(sacrificedCap);
            }

        } else if (data.ancientChargeCyclePresent()) {
            boolean unused = data.ancientChargeAvailable();
            boolean selfSacrifice = data.selfSacrificeActive();
            data.finishAncientCharge(now);
            if (selfSacrifice) data.startSacrificedHeartRecovery(now);
            applyExhaustion(
                player,
                data,
                now,
                unused
                    ? "Antik Şehir enerjisi kullanılmadan dağıldı. Bedenin çökmeye başladı."
                    : "Antik Şehir enerjisinin 20 saniyelik yükü sona erdi. Bedenin çökmeye başladı."
            );
        }

        if (data.ancientExhausted(now)) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 4, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 30, 0, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 30, 0, false, true, true));
            if (now % 6L == 0L) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.8, player.getZ(), 4, 0.38, 0.58, 0.38, 0.012);
                level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 0.9, player.getZ(), 3, 0.28, 0.45, 0.28, 0.006);
            }
        }

        tickSacrificedHeartRecovery(player, data, now);
    }

    private static void tickSacrificedHeartRecovery(ServerPlayer player, PlayerPowerData data, long now) {
        if (data.sacrificedHealthPointsToRecover() <= 0) return;
        if (now < data.nextSacrificedHeartRecoveryTick()) return;

        player.heal(1.0F); // Her üç saniyede yarım kalp.
        data.advanceSacrificedHeartRecovery(now + 60L);
        PlayerDataStore.markDirty();
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.1, player.getZ(), 3, 0.28, 0.35, 0.28, 0.01);
        level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 0.9, player.getZ(), 2, 0.22, 0.25, 0.22, 0.005);
        ServerNetworking.sync(player);
    }

    public static boolean isUsableCharge(PlayerPowerData data, long now, int power) {
        if (!data.ancientChargeReady(now) || power == 6) return false;
        return switch (data.powerClass()) {
            case FLIGHT -> true;
            case FIRE -> power != 1;
            case MOON -> true;
            case ICE -> true;
            case WARDEN, ANOMALY -> true;
            default -> false;
        };
    }

    public static boolean grant(ServerPlayer player, int requestedTicks, boolean force) {
        return grant(player, requestedTicks, force, false);
    }

    private static boolean grant(ServerPlayer player, int requestedTicks, boolean force, boolean selfSacrifice) {
        PlayerPowerData data = PlayerDataStore.get(player.getUUID());
        long now = player.level().getGameTime();
        int duration = Math.max(1, Math.min(MAX_CHARGE_TICKS, requestedTicks));

        if (!force && data.ancientExhausted(now)) {
            player.sendSystemMessage(Component.literal("Hedef hâlâ Antik Şehir çöküşünde."));
            return false;
        }
        if (!force && data.ancientChargeCyclePresent()) {
            player.sendSystemMessage(Component.literal("Hedef zaten Antik Şehir enerjisi taşıyor."));
            return false;
        }

        if (force) {
            data.cancelAncientCharge(now);
            data.clearAncientExhaustion();
            SELF_CHARGES.removeIf(pending -> pending.caster.equals(player.getUUID()));
        }
        data.beginAncientCharge(now, duration, selfSacrifice);
        PlayerDataStore.markDirty();
        ServerNetworking.sync(player);

        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.35F, 1.32F);
        // Ana görünürlük artık oyuncu dış çizgisi ve model kollarından gelir; parçacıklar yalnızca hafif vurgudur.
        level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 0.55, player.getZ(), 6, 0.42, 0.28, 0.42, 0.012);
        level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 0.35, player.getZ(), 4, 0.38, 0.22, 0.38, 0.018);
        drawRing(level, player.position().add(0.0, 0.06, 0.0), 0.86, ParticleTypes.WITCH, 10);
        return true;
    }

    public static void clear(ServerPlayer player) {
        clear(player, true);
    }

    public static void clearSilently(ServerPlayer player) {
        clear(player, false);
    }

    private static void clear(ServerPlayer player, boolean notify) {
        PlayerPowerData data = PlayerDataStore.get(player.getUUID());
        long now = player.level().getGameTime();
        int recovery = data.sacrificedHealthPointsToRecover() + (data.selfSacrificeActive() ? 6 : 0);
        data.cancelAncientCharge(now);
        data.clearAncientExhaustion();
        data.clearSacrificedHeartRecovery();
        if (recovery > 0) player.heal(recovery);
        SELF_CHARGES.removeIf(pending -> pending.caster.equals(player.getUUID()));
        BEAMS.removeIf(beam -> beam.caster.equals(player.getUUID()));
        PlayerDataStore.markDirty();
        ServerNetworking.sync(player);
        if (notify) player.sendSystemMessage(Component.literal("Antik Şehir Şarjı, çöküş ve kalp bedeli temizlendi."));
    }

    public static void consume(ServerPlayer player, PlayerPowerData data, int usedPower, long now) {
        if (!isUsableCharge(data, now, usedPower)) return;
        data.consumeAncientCharge(now, usedPower);
        emitChargedBurst((ServerLevel) player.level(), player.position().add(0.0, 1.0, 0.0), data.powerClass(), 2.15);
        player.sendSystemMessage(Component.literal("Antik Şehir enerjisi gücü dönüştürdü. Bedel, yirmi saniyelik yük tamamlandığında başlayacak."));
        PlayerDataStore.markDirty();
        ServerNetworking.sync(player);
    }

    private static void applyExhaustion(ServerPlayer player, PlayerPowerData data, long now, String message) {
        data.setAncientExhaustionUntil(now + EXHAUSTION_TICKS);
        PlayerDataStore.markDirty();
        player.sendSystemMessage(Component.literal(message));
        ServerNetworking.sync(player);
    }

    /** Çömelerek kullanılırsa kalp yerleştirme animasyonu; normal kullanımda hedef olsun veya olmasın ışın mutlaka ateşlenir. */
    public static boolean beginBeam(ServerPlayer caster, PlayerPowerData data, long now) {
        if (caster.isShiftKeyDown()) return beginSelfCharge(caster, data, now);
        return beginAttackBeam(caster, now);
    }

    /** Anomali kopyası çömelme durumunu devralmaz; çalınan Warden VI her zaman saldırı ışınıdır. */
    public static boolean beginCopiedBeam(ServerPlayer caster, long now) {
        return beginAttackBeam(caster, now);
    }

    private static boolean beginAttackBeam(ServerPlayer caster, long now) {
        LivingEntity target = findBeamTarget(caster, 22.0);
        Vec3 endpoint = target == null
            ? caster.getEyePosition().add(caster.getLookAngle().normalize().scale(22.0))
            : target.getEyePosition();
        BEAMS.add(new PendingBeam((ServerLevel) caster.level(), caster.getUUID(), target, endpoint, now, now + 22L));
        caster.sendSystemMessage(Component.literal(target == null
            ? "Antik Şehir ışını boşluğa ateşlendi."
            : target.getScoreboardName() + " Antik Şehir ışınıyla hedeflendi."));
        return true;
    }

    private static boolean beginSelfCharge(ServerPlayer caster, PlayerPowerData data, long now) {
        if (data.ancientExhausted(now) || data.ancientChargeCyclePresent()) {
            caster.sendSystemMessage(Component.literal("Kendine yeni şarj vermeden önce mevcut Antik Şehir etkisinin bitmesi gerekiyor."));
            return false;
        }
        if (caster.getHealth() <= SELF_CHARGE_HEALTH_COST) {
            caster.sendSystemMessage(Component.literal("Kendi kendine şarj için üç kalpten fazla canın olmalı."));
            return false;
        }
        boolean alreadyAnimating = SELF_CHARGES.stream().anyMatch(pending -> pending.caster.equals(caster.getUUID()));
        if (alreadyAnimating) return false;

        SELF_CHARGES.add(new PendingSelfCharge((ServerLevel) caster.level(), caster.getUUID(), now, now + SELF_CHARGE_ANIMATION_TICKS));
        caster.sendSystemMessage(Component.literal("Dört sculk kolu Antik Kalbi oluşturmaya başladı..."));
        return true;
    }

    private static LivingEntity findBeamTarget(ServerPlayer caster, double range) {
        Vec3 origin = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().normalize();
        Vec3 end = origin.add(look.scale(range));
        AABB search = new AABB(origin, end).inflate(1.7);
        LivingEntity best = null;
        double bestForward = range + 1.0;
        for (LivingEntity candidate : ((ServerLevel) caster.level()).getEntitiesOfClass(LivingEntity.class, search)) {
            if (candidate == caster || candidate.isSpectator() || !candidate.isAlive()) continue;
            Vec3 to = candidate.getEyePosition().subtract(origin);
            double forward = to.dot(look);
            if (forward <= 0.6 || forward > range) continue;
            double side = to.subtract(look.scale(forward)).length();
            double allowance = 1.55;
            if (side > allowance || !caster.hasLineOfSight(candidate)) continue;
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
            long now = beam.level.getGameTime();
            if (caster == null || caster.level() != beam.level) {
                clearArmBlocks(beam.level, beam.visualBlocks);
                iterator.remove();
                continue;
            }

            LivingEntity target = beam.target;
            Vec3 targetPoint = target != null && target.isAlive() && target.level() == beam.level
                ? target.getEyePosition()
                : beam.endpoint;
            drawBeamAnimation(beam.level, caster, targetPoint, now - beam.startTick, beam.visualBlocks);
            if (now < beam.finishTick) continue;

            boolean transferred = false;
            if (target != null && target.isAlive() && target.level() == beam.level
                && caster.distanceToSqr(target) <= 25.0 * 25.0 && caster.hasLineOfSight(target)) {
                if (target instanceof ServerPlayer targetPlayer) {
                    transferred = grant(targetPlayer, MAX_CHARGE_TICKS, false, false);
                } else {
                    transferred = grantMob(target, MAX_CHARGE_TICKS);
                }
            }

            if (transferred) {
                beam.level.playSound(null, target.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5F, 1.15F);
            } else {
                beam.level.sendParticles(ParticleTypes.WITCH, targetPoint.x, targetPoint.y, targetPoint.z, 18, 0.48, 0.48, 0.48, 0.045);
                beam.level.sendParticles(ParticleTypes.SCULK_SOUL, targetPoint.x, targetPoint.y, targetPoint.z, 10, 0.36, 0.36, 0.36, 0.022);
                beam.level.playSound(null, caster.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0F, 1.38F);
            }
            clearArmBlocks(beam.level, beam.visualBlocks);
            iterator.remove();
        }
    }

    private static void tickSelfCharges() {
        Iterator<PendingSelfCharge> iterator = SELF_CHARGES.iterator();
        while (iterator.hasNext()) {
            PendingSelfCharge pending = iterator.next();
            ServerPlayer caster = pending.level.getServer().getPlayerList().getPlayer(pending.caster);
            long now = pending.level.getGameTime();
            if (caster == null || !caster.isAlive() || caster.level() != pending.level) {
                clearArmBlocks(pending.level, pending.visualBlocks);
                iterator.remove();
                continue;
            }

            long age = now - pending.startTick;
            drawSelfChargeAnimation(pending.level, caster, age, pending.finishTick - pending.startTick, pending.visualBlocks);
            if (age % 8L == 0L) {
                pending.level.playSound(null, caster.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.15F, 0.78F + age * 0.006F);
            }
            if (now < pending.finishTick) continue;

            PlayerPowerData data = PlayerDataStore.get(caster.getUUID());
            if (caster.getHealth() <= SELF_CHARGE_HEALTH_COST || data.ancientChargeCyclePresent() || data.ancientExhausted(now)) {
                caster.sendSystemMessage(Component.literal("Antik Kalp göğsüne yerleşemedi; şarj iptal edildi."));
                clearArmBlocks(pending.level, pending.visualBlocks);
                iterator.remove();
                continue;
            }

            caster.setHealth(Math.max(1.0F, caster.getHealth() - SELF_CHARGE_HEALTH_COST));
            if (grant(caster, MAX_CHARGE_TICKS, false, true)) {
                pending.level.sendParticles(ParticleTypes.WITCH, caster.getX(), caster.getY() + 0.55, caster.getZ(), 8, 0.45, 0.35, 0.45, 0.022);
                pending.level.sendParticles(ParticleTypes.SCULK_SOUL, caster.getX(), caster.getY() + 0.45, caster.getZ(), 6, 0.36, 0.30, 0.36, 0.014);
            } else {
                caster.heal(SELF_CHARGE_HEALTH_COST);
            }
            clearArmBlocks(pending.level, pending.visualBlocks);
            iterator.remove();
        }
    }

    private static boolean grantMob(LivingEntity target, int durationTicks) {
        if (!target.isAlive()) return false;
        long now = target.level().getGameTime();
        MOB_CHARGES.removeIf(charge -> charge.target == target);
        MOB_CHARGES.add(new ChargedMob((ServerLevel) target.level(), target, now + Math.min(MAX_CHARGE_TICKS, Math.max(1, durationTicks))));
        ServerLevel level = (ServerLevel) target.level();
        level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getEyePosition().y - 0.30, target.getZ(), 18, 0.55, 0.65, 0.55, 0.035);
        level.sendParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getEyePosition().y - 0.30, target.getZ(), 12, 0.45, 0.55, 0.45, 0.025);
        return true;
    }

    private static void tickChargedMobs() {
        Iterator<ChargedMob> iterator = MOB_CHARGES.iterator();
        while (iterator.hasNext()) {
            ChargedMob charge = iterator.next();
            LivingEntity target = charge.target;
            long now = charge.level.getGameTime();
            if (!target.isAlive() || target.level() != charge.level) {
                iterator.remove();
                continue;
            }
            if (now >= charge.untilTick) {
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 4, false, true, true));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1, false, true, true));
                charge.level.sendParticles(ParticleTypes.LARGE_SMOKE, target.getX(), target.getEyePosition().y - 0.35, target.getZ(), 32, 0.7, 0.8, 0.7, 0.025);
                iterator.remove();
                continue;
            }

            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 30, 2, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 1, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 0, false, false, true));
            if (now % 3L == 0L) {
                double y = target.getY() + 0.2 + (now % 18L) / 18.0 * 1.45;
                drawRing(charge.level, new Vec3(target.getX(), y, target.getZ()), 0.78, ParticleTypes.WITCH, 18);
                charge.level.sendParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getEyePosition().y - 0.30, target.getZ(), 5, 0.4, 0.6, 0.4, 0.012);
            }
        }
    }

    private static void drawBeamAnimation(ServerLevel level, ServerPlayer caster, Vec3 targetPoint, long age, List<PlacedArmBlock> visualBlocks) {
        Vec3 look = caster.getLookAngle().normalize();
        Vec3 horizontal = horizontalDirection(look);
        Vec3 right = new Vec3(-horizontal.z, 0.0, horizontal.x);
        Vec3 body = caster.position().add(0.0, 1.05, 0.0);
        Vec3 back = body.subtract(horizontal.scale(0.62));
        Vec3 convergence = caster.getEyePosition().add(look.scale(1.35));
        double growth = Math.min(1.0, (age + 1.0) / 8.0);

        Vec3[] roots = armRoots(back, right);
        if (age % 2L == 0L) clearArmBlocks(level, visualBlocks);
        for (int i = 0; i < roots.length; i++) {
            double side = i % 2 == 0 ? 1.0 : -1.0;
            double height = i < 2 ? 0.35 : -0.20;
            Vec3 bendFull = roots[i].add(right.scale(side * 0.82)).add(horizontal.scale(0.50)).add(0.0, height, 0.0);
            Vec3 bend = roots[i].lerp(bendFull, growth);
            Vec3 end = roots[i].lerp(convergence, growth);
            if (age % 2L == 0L) {
                // Katı model kollar oyuncunun arka ve yan tarafında kalır; görüş çizgisine ve gövdesine blok yerleştirilmez.
                Vec3 visibleTip = bend.lerp(end, 0.32);
                placeArmLine(level, visualBlocks, roots[i], bend, Blocks.SCULK.defaultBlockState(), 4);
                placeArmLine(level, visualBlocks, bend, visibleTip,
                    i < 2 ? Blocks.CYAN_STAINED_GLASS.defaultBlockState() : Blocks.SCULK.defaultBlockState(), 2);
            }
            if (age % 4L == 0L) drawParticleLine(level, bend, end, ParticleTypes.SCULK_SOUL, 4);
        }

        if (age >= 6L) {
            drawThickBeam(level, convergence, targetPoint, 0.10);
            if (age % 3L == 0L) {
                level.sendParticles(ParticleTypes.SONIC_BOOM, convergence.x, convergence.y, convergence.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private static void drawSelfChargeAnimation(ServerLevel level, ServerPlayer caster, long age, long duration, List<PlacedArmBlock> visualBlocks) {
        Vec3 look = caster.getLookAngle().normalize();
        Vec3 horizontal = horizontalDirection(look);
        Vec3 right = new Vec3(-horizontal.z, 0.0, horizontal.x);
        Vec3 body = caster.position().add(0.0, 1.05, 0.0);
        Vec3 back = body.subtract(horizontal.scale(0.62));
        Vec3 chest = body.add(horizontal.scale(0.18));
        Vec3 front = chest.add(horizontal.scale(1.05));
        double progress = Math.max(0.0, Math.min(1.0, age / (double) Math.max(1L, duration)));
        double armGrowth = Math.min(1.0, progress / 0.42);
        double insertion = progress <= 0.58 ? 0.0 : (progress - 0.58) / 0.42;
        insertion = insertion * insertion * (3.0 - 2.0 * insertion);
        Vec3 heartCenter = front.lerp(chest, insertion);

        Vec3[] roots = armRoots(back, right);
        if (age % 2L == 0L) clearArmBlocks(level, visualBlocks);
        Vec3[] aroundHeart = {
            heartCenter.add(right.scale(0.48)).add(0.0, 0.38, 0.0),
            heartCenter.subtract(right.scale(0.48)).add(0.0, 0.38, 0.0),
            heartCenter.add(right.scale(0.42)).add(0.0, -0.34, 0.0),
            heartCenter.subtract(right.scale(0.42)).add(0.0, -0.34, 0.0)
        };
        for (int i = 0; i < roots.length; i++) {
            Vec3 elbowFull = roots[i].lerp(aroundHeart[i], 0.52).subtract(horizontal.scale(0.32));
            Vec3 elbow = roots[i].lerp(elbowFull, armGrowth);
            Vec3 end = roots[i].lerp(aroundHeart[i], armGrowth);
            if (age % 2L == 0L) {
                // Gerçek kol gövdesi arkada kalır; kalbe uzanan son bölüm ışın olarak çizilir ve oyuncuyu sıkıştırmaz.
                Vec3 visibleTip = elbow.lerp(end, 0.28);
                placeArmLine(level, visualBlocks, roots[i], elbow, Blocks.SCULK.defaultBlockState(), 4);
                placeArmLine(level, visualBlocks, elbow, visibleTip, Blocks.CYAN_STAINED_GLASS.defaultBlockState(), 2);
            }
        }

        if (progress >= 0.28) {
            double pulse = 0.92 + Math.sin(age * 0.55) * 0.12;
            drawPurpleHeart(level, heartCenter, right, pulse);
            level.sendParticles(ParticleTypes.SCULK_SOUL, heartCenter.x, heartCenter.y, heartCenter.z, 2, 0.16, 0.18, 0.16, 0.005);
        }
        if (insertion > 0.05) {
            drawParticleLine(level, front, chest, ParticleTypes.WITCH, 7);
            drawRing(level, chest, 0.38 + insertion * 0.20, ParticleTypes.SCULK_SOUL, 12);
        }
    }

    private static void drawPurpleHeart(ServerLevel level, Vec3 center, Vec3 right, double scale) {
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        int points = 22;
        for (int i = 0; i < points; i++) {
            double t = Math.PI * 2.0 * i / points;
            double x = 16.0 * Math.pow(Math.sin(t), 3.0);
            double y = 13.0 * Math.cos(t) - 5.0 * Math.cos(2.0 * t) - 2.0 * Math.cos(3.0 * t) - Math.cos(4.0 * t);
            Vec3 point = center.add(right.scale(x * 0.022 * scale)).add(up.scale(y * 0.022 * scale));
            level.sendParticles(i % 3 == 0 ? ParticleTypes.SCULK_SOUL : ParticleTypes.WITCH, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void drawMutation(ServerLevel level, ServerPlayer player, int phase, long now, boolean ready) {
        // Görüşü kapatan parçacık duvarı kaldırıldı. Ana aura, oyunun görünür parlama dış çizgisidir.
        // Az sayıdaki kıvılcım oyuncunun göz hizasından uzakta, ayak ve sırt tarafında kalır.
        if (now % 8L != 0L) return;
        Vec3 horizontal = horizontalDirection(player.getLookAngle());
        Vec3 back = player.position().add(0.0, 0.85, 0.0).subtract(horizontal.scale(0.72));
        double spread = 0.28 + phase * 0.08;
        level.sendParticles(ParticleTypes.SCULK_SOUL, back.x, back.y, back.z, ready ? 4 : 2, spread, 0.42, spread, 0.008);
        if (now % 16L == 0L) {
            drawRing(level, player.position().add(0.0, 0.08, 0.0), 0.78 + phase * 0.10, ParticleTypes.WITCH, 12 + phase * 2);
        }
        ParticleOptions classParticle = switch (PlayerDataStore.get(player.getUUID()).powerClass()) {
            case FIRE -> ParticleTypes.FLAME;
            case FLIGHT -> ParticleTypes.REVERSE_PORTAL;
            case MOON -> ParticleTypes.END_ROD;
            case ICE -> ParticleTypes.SNOWFLAKE;
            case ANOMALY -> ParticleTypes.END_ROD;
            default -> ParticleTypes.SCULK_SOUL;
        };
        level.sendParticles(classParticle, back.x, back.y + 0.18, back.z, ready ? 2 : 1, 0.22, 0.30, 0.22, 0.005);
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
        level.sendParticles(ParticleTypes.WITCH, center.x, center.y, center.z, 22, spread * 0.75, spread * 0.65, spread * 0.75, 0.055);
        level.sendParticles(ParticleTypes.SCULK_SOUL, center.x, center.y, center.z, 16, spread * 0.62, spread * 0.55, spread * 0.62, 0.035);
        drawRing(level, center, Math.max(0.8, spread * 0.78), ParticleTypes.WITCH, 20);
        ParticleOptions original = switch (powerClass) {
            case FIRE -> ParticleTypes.FLAME;
            case FLIGHT -> ParticleTypes.REVERSE_PORTAL;
            case MOON -> ParticleTypes.END_ROD;
            case ICE -> ParticleTypes.SNOWFLAKE;
            case ANOMALY -> ParticleTypes.END_ROD;
            default -> ParticleTypes.SONIC_BOOM;
        };
        level.sendParticles(original, center.x, center.y, center.z, powerClass == PowerClass.WARDEN ? 2 : 8, spread * 0.58, spread * 0.5, spread * 0.58, 0.04);
    }

    private static void placeArmLine(ServerLevel level, List<PlacedArmBlock> list, Vec3 from, Vec3 to, BlockState state, int points) {
        int safePoints = Math.max(2, points);
        for (int i = 0; i <= safePoints; i++) {
            Vec3 point = from.lerp(to, i / (double) safePoints);
            BlockPos pos = BlockPos.containing(point);
            if (!level.getBlockState(pos).isAir()) continue;
            level.setBlockAndUpdate(pos, state);
            list.add(new PlacedArmBlock(pos, state));
        }
    }

    private static void clearArmBlocks(ServerLevel level, List<PlacedArmBlock> list) {
        for (PlacedArmBlock placed : list) {
            if (level.getBlockState(placed.pos).is(placed.state.getBlock())) {
                level.setBlockAndUpdate(placed.pos, Blocks.AIR.defaultBlockState());
            }
        }
        list.clear();
    }

    private static Vec3 horizontalDirection(Vec3 look) {
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() < 0.0001) horizontal = new Vec3(0.0, 0.0, 1.0);
        return horizontal.normalize();
    }

    private static Vec3[] armRoots(Vec3 back, Vec3 right) {
        return new Vec3[] {
            back.add(right.scale(0.62)).add(0.0, 0.58, 0.0),
            back.subtract(right.scale(0.62)).add(0.0, 0.58, 0.0),
            back.add(right.scale(0.72)).add(0.0, -0.18, 0.0),
            back.subtract(right.scale(0.72)).add(0.0, -0.18, 0.0)
        };
    }

    private static void drawThickBeam(ServerLevel level, Vec3 from, Vec3 to, double thickness) {
        Vec3 direction = to.subtract(from);
        Vec3 horizontal = horizontalDirection(direction);
        Vec3 right = new Vec3(-horizontal.z, 0.0, horizontal.x).scale(thickness);
        Vec3 up = new Vec3(0.0, thickness, 0.0);
        drawParticleLine(level, from, to, ParticleTypes.WITCH, 15);
        drawParticleLine(level, from.add(right), to.add(right), ParticleTypes.SCULK_SOUL, 10);
        drawParticleLine(level, from.subtract(right), to.subtract(right), ParticleTypes.SCULK_SOUL, 10);
        drawParticleLine(level, from.add(up), to.add(up), ParticleTypes.WITCH, 9);
        drawParticleLine(level, from.subtract(up), to.subtract(up), ParticleTypes.WITCH, 9);
    }

    private static void drawRing(ServerLevel level, Vec3 center, double radius, ParticleOptions particle, int count) {
        int safeCount = Math.max(8, count);
        for (int i = 0; i < safeCount; i++) {
            double angle = Math.PI * 2.0 * i / safeCount;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            level.sendParticles(particle, x, center.y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void drawParticleLine(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions particle, int points) {
        int safePoints = Math.max(2, points);
        for (int i = 0; i <= safePoints; i++) {
            Vec3 point = from.lerp(to, i / (double) safePoints);
            level.sendParticles(particle, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static final class PendingBeam {
        private final ServerLevel level;
        private final UUID caster;
        private final LivingEntity target;
        private final Vec3 endpoint;
        private final long startTick;
        private final long finishTick;
        private final List<PlacedArmBlock> visualBlocks = new ArrayList<>();
        private PendingBeam(ServerLevel level, UUID caster, LivingEntity target, Vec3 endpoint, long startTick, long finishTick) {
            this.level = level; this.caster = caster; this.target = target; this.endpoint = endpoint; this.startTick = startTick; this.finishTick = finishTick;
        }
    }
    private static final class PendingSelfCharge {
        private final ServerLevel level;
        private final UUID caster;
        private final long startTick;
        private final long finishTick;
        private final List<PlacedArmBlock> visualBlocks = new ArrayList<>();
        private PendingSelfCharge(ServerLevel level, UUID caster, long startTick, long finishTick) {
            this.level = level; this.caster = caster; this.startTick = startTick; this.finishTick = finishTick;
        }
    }
    private record PlacedArmBlock(BlockPos pos, BlockState state) {}
    private record ChargedMob(ServerLevel level, LivingEntity target, long untilTick) {}
}
