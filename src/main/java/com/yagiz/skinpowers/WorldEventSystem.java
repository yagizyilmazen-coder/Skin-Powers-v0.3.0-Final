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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Sunucuda arada bir oluşan sınıf temalı dünya olayları. Meteor blok hasarı sunucu ayarına bağlıdır. */
public final class WorldEventSystem {
    private static final long EVENT_DURATION = 2400L; // 2 dakika
    private static final double RADIUS = 34.0;
    private static ActiveEvent active;
    private static long nextAutomaticEvent;
    private static final List<PendingStrike> STRIKES = new ArrayList<>();
    private static final List<TemporaryMoonPillar> MOON_PILLARS = new ArrayList<>();

    private WorldEventSystem() {}

    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        long now = overworld.getGameTime();
        if (nextAutomaticEvent == 0L) nextAutomaticEvent = now + 12000L;

        if (active == null && now >= nextAutomaticEvent) {
            List<ServerPlayer> candidates = new ArrayList<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                candidates.add(player);
            }
            if (!candidates.isEmpty()) {
                ServerPlayer anchor = candidates.get(overworld.getRandom().nextInt(candidates.size()));
                start(server, EventType.random(overworld.getRandom()), (ServerLevel) anchor.level(), anchor.position(), false);
            } else {
                nextAutomaticEvent = now + 2400L;
            }
        }

        tickStrikes();
        tickMoonPillars();
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
            player.sendSystemMessage(Component.literal("Bilinmeyen olay. sculk, meteor, gok, ay, anomali veya rastgele kullan."));
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
        for (PendingStrike strike : STRIKES) clearStrikeVisual(strike);
        STRIKES.clear();
        clearMoonPillars();
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
                    entity.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 35, 0, false, false, true));
                    if (now % 40L == 0L) {
                        Vec3 motion = entity.getDeltaMovement();
                        entity.setDeltaMovement(motion.x, Math.max(motion.y, 0.45), motion.z);
                        if (entity instanceof ServerPlayer player) player.hurtMarked = true;
                    }
                }
                if (now % 5L == 0L) level.sendParticles(ParticleTypes.CLOUD, event.center.x, event.center.y + 5.0, event.center.z, 28, 15.0, 5.0, 15.0, 0.08);
            }
            case RED_MOON -> {
                if (now % 20L == 0L) {
                    for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
                        if (entity instanceof ServerPlayer player && PlayerDataStore.get(player.getUUID()).powerClass() == PowerClass.MOON) {
                            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 45, 1, false, false, true));
                            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 45, 0, false, false, true));
                        } else {
                            Vec3 motion = entity.getDeltaMovement();
                            entity.setDeltaMovement(motion.x * 0.72, Math.min(motion.y - 0.32, -0.25), motion.z * 0.72);
                            entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 45, 1, false, false, true));
                            if (entity.getHealth() <= entity.getMaxHealth() * 0.40F) {
                                entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 45, 0, false, false, true));
                            }
                        }
                    }
                }
                for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, area)) {
                    Vec3 motion = projectile.getDeltaMovement();
                    projectile.setDeltaMovement(motion.x * 0.92, motion.y - 0.08, motion.z * 0.92);
                }
                if (now % 80L == 0L) spawnMoonPillar(event, now);
                if (now % 5L == 0L) {
                    level.sendParticles(ParticleTypes.END_ROD, event.center.x, event.center.y + 6.0, event.center.z, 34, 15.0, 6.0, 15.0, 0.035);
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, event.center.x, event.center.y + 1.0, event.center.z, 20, 15.0, 2.0, 15.0, 0.025);
                }
            }
            case REALITY_TEAR -> {
                for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, area)) {
                    if (now % 8L == 0L) projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-0.82));
                }
                if (now % 20L == 0L) {
                    for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
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
        STRIKES.add(new PendingStrike(event.level, target, now, now + 52L));
        event.level.sendParticles(ParticleTypes.FLAME, target.x, target.y + 0.2, target.z, 35, 1.4, 0.1, 1.4, 0.02);
    }

    private static void tickStrikes() {
        Iterator<PendingStrike> iterator = STRIKES.iterator();
        while (iterator.hasNext()) {
            PendingStrike strike = iterator.next();
            long now = strike.level.getGameTime();
            if (now < strike.impactAt) {
                double total = Math.max(1.0, strike.impactAt - strike.startedAt);
                double progress = Math.max(0.0, Math.min(1.0, (now - strike.startedAt) / total));
                Vec3 position = strike.target.add(0.0, 30.0 * (1.0 - progress), 0.0);
                placeStrikeVisual(strike, position);
                strike.level.sendParticles(ParticleTypes.FLAME, position.x, position.y, position.z, 15, 0.75, 0.75, 0.75, 0.055);
                strike.level.sendParticles(ParticleTypes.LARGE_SMOKE, position.x, position.y + 0.5, position.z, 10, 0.85, 0.85, 0.85, 0.035);
                if (now % 4L == 0L) PowerSystem.drawExternalRing(strike.level, strike.target.add(0.0, 0.12, 0.0), 3.6, ParticleTypes.FLAME, 32);
                continue;
            }
            clearStrikeVisual(strike);
            AABB blast = new AABB(strike.target, strike.target).inflate(6.0);
            for (LivingEntity entity : strike.level.getEntitiesOfClass(LivingEntity.class, blast)) {
                double distance = Math.sqrt(entity.distanceToSqr(strike.target));
                if (distance > 6.0) continue;
                entity.hurtServer(strike.level, strike.level.damageSources().generic(), (float) Math.max(5.0, 16.0 - distance * 1.8));
                Vec3 push = entity.position().subtract(strike.target);
                if (push.lengthSqr() > 0.001) {
                    push = push.normalize().scale(2.05);
                    entity.push(push.x, 0.95, push.z);
                }
                entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), 100));
            }
            strike.level.sendParticles(ParticleTypes.EXPLOSION, strike.target.x, strike.target.y + 0.5, strike.target.z, 14, 1.6, 1.0, 1.6, 0.08);
            strike.level.sendParticles(ParticleTypes.FLAME, strike.target.x, strike.target.y + 0.5, strike.target.z, 120, 3.2, 1.8, 3.2, 0.16);
            strike.level.sendParticles(ParticleTypes.LARGE_SMOKE, strike.target.x, strike.target.y + 1.0, strike.target.z, 55, 2.8, 2.0, 2.8, 0.08);
            strike.level.playSound(null, BlockPos.containing(strike.target), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 2.1F, 0.68F);
            ServerNetworking.sendScreenShake(strike.level, strike.target, 34.0, 1.65F, 18);
            if (PlayerDataStore.config().meteorBlockDamage()) carveEventCrater(strike.level, BlockPos.containing(strike.target), 4);
            iterator.remove();
        }
    }

    private static void placeStrikeVisual(PendingStrike strike, Vec3 position) {
        BlockPos center = BlockPos.containing(position);
        if (center.equals(strike.visualCenter)) return;
        clearStrikeVisual(strike);
        int[][] offsets = {{0,0,0},{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1},{1,1,0},{-1,1,0}};
        for (int i = 0; i < offsets.length; i++) {
            BlockPos pos = center.offset(offsets[i][0], offsets[i][1], offsets[i][2]);
            if (!strike.level.getBlockState(pos).isAir()) continue;
            strike.level.setBlockAndUpdate(pos, (i == 0 || i == 7) ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState());
            strike.visualBlocks.add(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
        }
        strike.visualCenter = new BlockPos(center.getX(), center.getY(), center.getZ());
    }

    private static void clearStrikeVisual(PendingStrike strike) {
        for (BlockPos pos : strike.visualBlocks) {
            BlockState state = strike.level.getBlockState(pos);
            if (state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CRYING_OBSIDIAN)) strike.level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        strike.visualBlocks.clear();
        strike.visualCenter = null;
    }

    private static void carveEventCrater(ServerLevel level, BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 1; dy >= -3; dy--) {
                    double shape = (dx * dx + dz * dz) / (double) (radius * radius) + (dy * dy) / 12.0;
                    if (shape > 1.2) continue;
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.is(Blocks.BEDROCK) || state.getDestroySpeed(level, pos) < 0.0F) continue;
                    level.destroyBlock(pos, false, null);
                }
            }
        }
    }

    private static void spawnMoonPillar(ActiveEvent event, long now) {
        RandomSource random = event.level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = 4.0 + random.nextDouble() * (RADIUS - 6.0);
        int x = (int) Math.floor(event.center.x + Math.cos(angle) * distance);
        int z = (int) Math.floor(event.center.z + Math.sin(angle) * distance);
        int y = event.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos base = new BlockPos(x, y, z);
        List<BlockPos> placed = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            BlockPos pos = base.above(i);
            if (!event.level.getBlockState(pos).isAir()) break;
            BlockState state = switch (i % 3) {
                case 0 -> Blocks.AMETHYST_BLOCK.defaultBlockState();
                case 1 -> Blocks.CALCITE.defaultBlockState();
                default -> Blocks.PACKED_ICE.defaultBlockState();
            };
            event.level.setBlockAndUpdate(pos, state);
            placed.add(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
        }
        if (!placed.isEmpty()) {
            MOON_PILLARS.add(new TemporaryMoonPillar(event.level, placed, now + 70L));
            event.level.sendParticles(ParticleTypes.END_ROD, x + 0.5, y + 2.0, z + 0.5, 45, 0.8, 2.0, 0.8, 0.05);
            event.level.playSound(null, base, SoundEvents.END_PORTAL_SPAWN, SoundSource.AMBIENT, 1.0F, 0.75F);
        }
    }

    private static void tickMoonPillars() {
        Iterator<TemporaryMoonPillar> iterator = MOON_PILLARS.iterator();
        while (iterator.hasNext()) {
            TemporaryMoonPillar pillar = iterator.next();
            if (pillar.level.getGameTime() < pillar.expireTick) continue;
            for (BlockPos pos : pillar.blocks) {
                BlockState state = pillar.level.getBlockState(pos);
                if (state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.CALCITE) || state.is(Blocks.PACKED_ICE)) {
                    pillar.level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }
            }
            iterator.remove();
        }
    }

    private static void clearMoonPillars() {
        for (TemporaryMoonPillar pillar : MOON_PILLARS) {
            for (BlockPos pos : pillar.blocks) {
                BlockState state = pillar.level.getBlockState(pos);
                if (state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.CALCITE) || state.is(Blocks.PACKED_ICE)) {
                    pillar.level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }
            }
        }
        MOON_PILLARS.clear();
    }

    public static void clearAll() {
        active = null;
        for (PendingStrike strike : STRIKES) clearStrikeVisual(strike);
        STRIKES.clear();
        clearMoonPillars();
        nextAutomaticEvent = 0L;
    }

    private enum EventType {
        SCULK_SURGE("Sculk Uyanışı", "Titreşimler güçleniyor; yaratıklar saldırganlaşıyor.", ParticleTypes.SCULK_SOUL, 0.65F),
        METEOR_STORM("Meteor Fırtınası", "Görünür meteorlar gökyüzünden düşüyor ve çarpınca krater açıyor.", ParticleTypes.FLAME, 0.75F),
        SKY_RIFT("Gökyüzü Yarığı", "Yerçekimi zayıflıyor ve güçlü rüzgârlar yükseliyor.", ParticleTypes.CLOUD, 1.35F),
        RED_MOON("Kızıl Ay", "Ay parçaları düşüyor; yerçekimi ağırlaşıyor ve Ay sınıfı güçleniyor.", ParticleTypes.END_ROD, 0.82F),
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
                case "ay", "moon" -> RED_MOON;
                case "anomali", "404" -> REALITY_TEAR;
                case "rastgele" -> random(random);
                default -> null;
            };
        }
    }

    private record ActiveEvent(EventType type, ServerLevel level, Vec3 center, long startedAt, long endsAt) {}
    private record TemporaryMoonPillar(ServerLevel level, List<BlockPos> blocks, long expireTick) {}
    private static final class PendingStrike {
        private final ServerLevel level;
        private final Vec3 target;
        private final long startedAt;
        private final long impactAt;
        private final List<BlockPos> visualBlocks = new ArrayList<>();
        private BlockPos visualCenter;

        private PendingStrike(ServerLevel level, Vec3 target, long startedAt, long impactAt) {
            this.level = level;
            this.target = target;
            this.startedAt = startedAt;
            this.impactAt = impactAt;
        }
    }
}
