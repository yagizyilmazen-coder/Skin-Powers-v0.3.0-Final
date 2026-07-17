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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Sunucuda arada bir oluşan, bloklara kalıcı zarar vermeyen sınıf temalı dünya olayları. */
public final class WorldEventSystem {
    private static final long EVENT_DURATION = 2400L; // 2 dakika
    private static final double RADIUS = 34.0;
    private static ActiveEvent active;
    private static long nextAutomaticEvent;
    private static final List<PendingStrike> STRIKES = new ArrayList<>();

    private WorldEventSystem() {}

    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        long now = overworld.getGameTime();
        if (nextAutomaticEvent == 0L) nextAutomaticEvent = now + 12000L;

        if (active == null && now >= nextAutomaticEvent) {
            List<ServerPlayer> candidates = new ArrayList<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!DuelSystem.isInDuel(player.getUUID())) candidates.add(player);
            }
            if (!candidates.isEmpty()) {
                ServerPlayer anchor = candidates.get(overworld.getRandom().nextInt(candidates.size()));
                start(server, EventType.random(overworld.getRandom()), (ServerLevel) anchor.level(), anchor.position(), false);
            } else {
                nextAutomaticEvent = now + 2400L;
            }
        }

        tickStrikes();
        if (active == null) return;
        long levelNow = active.level.getGameTime();
        if (levelNow >= active.endsAt) {
            finish(server);
            return;
        }
        tickActive(active, levelNow);
    }

    public static boolean startNearPlayer(ServerPlayer player, String requested) {
        EventType type = EventType.fromCommand(requested, ((ServerLevel) player.level()).getRandom());
        if (type == null) {
            player.sendSystemMessage(Component.literal("Bilinmeyen olay. sculk, meteor, gok, doga, anomali veya rastgele kullan."));
            return false;
        }
        return start(player.level().getServer(), type, (ServerLevel) player.level(), player.position(), true);
    }

    public static boolean stop(MinecraftServer server) {
        if (active == null) return false;
        finish(server);
        return true;
    }

    public static String status() {
        if (active == null) return "Aktif dünya olayı yok.";
        long remaining = Math.max(0L, active.endsAt - active.level.getGameTime());
        return active.type.display + " • " + String.format(java.util.Locale.ROOT, "%.0f", remaining / 20.0) + " saniye";
    }

    private static boolean start(MinecraftServer server, EventType type, ServerLevel level, Vec3 center, boolean forced) {
        if (active != null) return false;
        long now = level.getGameTime();
        active = new ActiveEvent(type, level, center, now, now + EVENT_DURATION);
        nextAutomaticEvent = server.overworld().getGameTime() + 21600L + server.overworld().getRandom().nextInt(14401);
        for (ServerPlayer online : server.getPlayerList().getPlayers()) online.sendSystemMessage(Component.literal("DÜNYA OLAYI: " + type.display + " • " + type.description));
        level.playSound(null, BlockPos.containing(center), SoundEvents.END_PORTAL_SPAWN, SoundSource.AMBIENT, 1.2F, type.pitch);
        level.sendParticles(type.particle, center.x, center.y + 1.0, center.z, 160, 4.0, 2.0, 4.0, 0.12);
        return true;
    }

    private static void finish(MinecraftServer server) {
        if (active == null) return;
        for (ServerPlayer online : server.getPlayerList().getPlayers()) online.sendSystemMessage(Component.literal("Dünya olayı sona erdi: " + active.type.display));
        active = null;
        STRIKES.clear();
    }

    private static void tickActive(ActiveEvent event, long now) {
        ServerLevel level = event.level;
        AABB area = new AABB(event.center, event.center).inflate(RADIUS, 18.0, RADIUS);
        if (now % 10L == 0L) PowerSystem.drawExternalRing(level, event.center, RADIUS, event.type.particle, 96);

        switch (event.type) {
            case SCULK_SURGE -> {
                if (now % 20L == 0L) {
                    for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
                        if (entity instanceof ServerPlayer player) {
                            if (DuelSystem.isInDuel(player.getUUID())) continue;
                            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 45, 0, false, false, true));
                            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 45, 0, false, false, true));
                        } else {
                            entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 45, 0, false, false, true));
                            entity.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 45, 1, false, false, true));
                        }
                    }
                    level.sendParticles(ParticleTypes.SCULK_SOUL, event.center.x, event.center.y + 1.0, event.center.z, 45, 14.0, 2.0, 14.0, 0.04);
                }
            }
            case METEOR_STORM -> {
                if (now % 30L == 0L) scheduleStrike(event, now);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, event.center.x, event.center.y + 12.0, event.center.z, 10, 13.0, 4.0, 13.0, 0.02);
            }
            case SKY_RIFT -> {
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
                    if (entity instanceof ServerPlayer player && DuelSystem.isInDuel(player.getUUID())) continue;
                    entity.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 35, 0, false, false, true));
                    if (now % 40L == 0L) {
                        Vec3 motion = entity.getDeltaMovement();
                        entity.setDeltaMovement(motion.x, Math.max(motion.y, 0.45), motion.z);
                        if (entity instanceof ServerPlayer player) player.hurtMarked = true;
                    }
                }
                if (now % 5L == 0L) level.sendParticles(ParticleTypes.CLOUD, event.center.x, event.center.y + 5.0, event.center.z, 28, 15.0, 5.0, 15.0, 0.08);
            }
            case ANCIENT_BLOOM -> {
                if (now % 20L == 0L) {
                    for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
                        if (entity instanceof ServerPlayer player) {
                            if (DuelSystem.isInDuel(player.getUUID())) continue;
                            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 45, 0, false, false, true));
                        } else {
                            entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 45, 1, false, false, true));
                            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 45, 0, false, false, true));
                        }
                    }
                }
                if (now % 5L == 0L) level.sendParticles(ParticleTypes.HAPPY_VILLAGER, event.center.x, event.center.y + 1.0, event.center.z, 22, 15.0, 2.5, 15.0, 0.04);
            }
            case REALITY_TEAR -> {
                for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, area)) {
                    if (now % 8L == 0L) projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-0.82));
                }
                if (now % 20L == 0L) {
                    for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
                        if (entity instanceof ServerPlayer player && DuelSystem.isInDuel(player.getUUID())) continue;
                        entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 35, 1, false, false, true));
                    }
                }
                if (now % 4L == 0L) level.sendParticles(ParticleTypes.WITCH, event.center.x, event.center.y + 1.0, event.center.z, 35, 16.0, 3.5, 16.0, 0.08);
            }
        }
    }

    private static void scheduleStrike(ActiveEvent event, long now) {
        RandomSource random = event.level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = 5.0 + random.nextDouble() * (RADIUS - 7.0);
        Vec3 target = event.center.add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
        int y = event.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (int) Math.floor(target.x), (int) Math.floor(target.z));
        target = new Vec3(target.x, y, target.z);
        STRIKES.add(new PendingStrike(event.level, target, now + 24L));
        event.level.sendParticles(ParticleTypes.FLAME, target.x, target.y + 0.2, target.z, 35, 1.4, 0.1, 1.4, 0.02);
    }

    private static void tickStrikes() {
        Iterator<PendingStrike> iterator = STRIKES.iterator();
        while (iterator.hasNext()) {
            PendingStrike strike = iterator.next();
            long now = strike.level.getGameTime();
            if (now < strike.impactAt) {
                Vec3 high = strike.target.add(0.0, Math.max(3.0, (strike.impactAt - now) * 1.2), 0.0);
                strike.level.sendParticles(ParticleTypes.FLAME, high.x, high.y, high.z, 6, 0.4, 0.5, 0.4, 0.02);
                continue;
            }
            AABB blast = new AABB(strike.target, strike.target).inflate(4.5);
            for (LivingEntity entity : strike.level.getEntitiesOfClass(LivingEntity.class, blast)) {
                if (entity instanceof ServerPlayer player && DuelSystem.isInDuel(player.getUUID())) continue;
                double distance = Math.sqrt(entity.distanceToSqr(strike.target));
                if (distance > 4.5) continue;
                entity.hurtServer(strike.level, strike.level.damageSources().generic(), (float) Math.max(3.0, 10.0 - distance * 1.5));
                Vec3 push = entity.position().subtract(strike.target);
                if (push.lengthSqr() > 0.001) {
                    push = push.normalize().scale(1.1);
                    entity.push(push.x, 0.55, push.z);
                }
            }
            strike.level.sendParticles(ParticleTypes.EXPLOSION, strike.target.x, strike.target.y + 0.5, strike.target.z, 5, 0.7, 0.5, 0.7, 0.0);
            strike.level.sendParticles(ParticleTypes.FLAME, strike.target.x, strike.target.y + 0.5, strike.target.z, 70, 2.0, 1.0, 2.0, 0.10);
            strike.level.playSound(null, BlockPos.containing(strike.target), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 1.3F, 0.85F);
            iterator.remove();
        }
    }

    public static void clearAll() {
        active = null;
        STRIKES.clear();
        nextAutomaticEvent = 0L;
    }

    private enum EventType {
        SCULK_SURGE("Sculk Uyanışı", "Titreşimler güçleniyor; yaratıklar saldırganlaşıyor.", ParticleTypes.SCULK_SOUL, 0.65F),
        METEOR_STORM("Meteor Fırtınası", "Gökyüzünden blok kırmayan meteorlar düşüyor.", ParticleTypes.FLAME, 0.75F),
        SKY_RIFT("Gökyüzü Yarığı", "Yerçekimi zayıflıyor ve güçlü rüzgârlar yükseliyor.", ParticleTypes.CLOUD, 1.35F),
        ANCIENT_BLOOM("Kadim Çiçeklenme", "Doğa oyuncuları iyileştirirken yaratıkları zayıflatıyor.", ParticleTypes.HAPPY_VILLAGER, 1.15F),
        REALITY_TEAR("Gerçeklik Çatlağı", "Mermiler ve hareketler kararsızlaşıyor.", ParticleTypes.WITCH, 0.50F);

        private final String display;
        private final String description;
        private final net.minecraft.core.particles.ParticleOptions particle;
        private final float pitch;

        EventType(String display, String description, net.minecraft.core.particles.ParticleOptions particle, float pitch) {
            this.display = display;
            this.description = description;
            this.particle = particle;
            this.pitch = pitch;
        }

        private static EventType random(RandomSource random) {
            EventType[] values = values();
            return values[random.nextInt(values.length)];
        }

        private static EventType fromCommand(String command, RandomSource random) {
            if (command == null) return null;
            return switch (command.toLowerCase(java.util.Locale.ROOT)) {
                case "sculk", "warden" -> SCULK_SURGE;
                case "meteor", "ates" -> METEOR_STORM;
                case "gok", "ucus" -> SKY_RIFT;
                case "doga" -> ANCIENT_BLOOM;
                case "anomali", "404" -> REALITY_TEAR;
                case "rastgele" -> random(random);
                default -> null;
            };
        }
    }

    private record ActiveEvent(EventType type, ServerLevel level, Vec3 center, long startedAt, long endsAt) {}
    private record PendingStrike(ServerLevel level, Vec3 target, long impactAt) {}
}
