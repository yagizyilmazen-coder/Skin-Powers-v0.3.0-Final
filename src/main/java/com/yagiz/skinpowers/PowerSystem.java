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
import net.minecraft.world.entity.item.ItemEntity;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PowerSystem {
    private static final List<PendingMeteor> METEORS = new ArrayList<>();
    private static final List<PendingHellfireOrb> HELLFIRE_ORBS = new ArrayList<>();
    private static final List<PendingNatureSeed> NATURE_SEEDS = new ArrayList<>();
    private static final List<PendingVineTrap> VINE_TRAPS = new ArrayList<>();
    private static final List<PendingLifeTree> LIFE_TREES = new ArrayList<>();
    private static final List<PendingRootWave> ROOT_WAVES = new ArrayList<>();
    private static final List<TemporaryNatureShape> NATURE_SHAPES = new ArrayList<>();
    private static final List<PendingFlightSpear> FLIGHT_SPEARS = new ArrayList<>();
    private static final List<PendingSkyBomb> SKY_BOMBS = new ArrayList<>();
    private static final List<PendingTimeSpear> TIME_SPEARS = new ArrayList<>();
    private static final List<PendingTimePrison> TIME_PRISONS = new ArrayList<>();
    private static final List<PendingTimeField> TIME_FIELDS = new ArrayList<>();
    private static final List<TemporaryTimeShape> TIME_SHAPES = new ArrayList<>();
    private static final Map<UUID, java.util.ArrayDeque<TimeSnapshot>> TIME_HISTORY = new HashMap<>();
    private static final Map<UUID, Long> NATURE_CRITICAL_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Long> LAST_SKY_IMPACT = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_FLIGHT_POSITION = new HashMap<>();
    private static final Map<UUID, UUID> DRAGON_CLAW_TARGET = new HashMap<>();
    private static final Map<UUID, Long> DRAGON_CLAW_UNTIL = new HashMap<>();
    private static final Map<UUID, Integer> DRAGON_CLAW_ESCAPE_PRESSES = new HashMap<>();
    private static final Map<UUID, DragonBreathState> DRAGON_BREATHS = new HashMap<>();
    private static final Map<UUID, Long> DRAGON_SILENCE_UNTIL = new HashMap<>();
    private static final Map<UUID, WardenAmbushState> WARDEN_AMBUSHES = new HashMap<>();
    private static final List<WardenArmSegment> WARDEN_ARM_SEGMENTS = new ArrayList<>();
    private static final List<WardenArmStrike> WARDEN_ARM_STRIKES = new ArrayList<>();
    private static final Map<UUID, long[]> LAST_MASTERY_CREDIT = new HashMap<>();
    private static long lastAutosaveTick;
    private static boolean reflectingDragonScaleDamage;

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

        if (AnomalySystem.isVoided(serverPlayer.getUUID())) {
            return InteractionResult.FAIL;
        }

        PlayerPowerData data = PlayerDataStore.get(serverPlayer.getUUID());
        if (data.powerClass() != PowerClass.FIRE || data.unlockedLevel() < 2) {
            return InteractionResult.PASS;
        }

        long now = serverLevel.getGameTime();
        boolean charged = data.selectedPower() == 2 && AncientChargeSystem.isUsableCharge(data, now, 2);
        int stage = data.masteryStage(2);
        float damage = AncientChargeSystem.damage(4.0F + stage * 1.2F, charged || data.classAwakeningActive(now));
        target.hurtServer(serverLevel, serverLevel.damageSources().playerAttack(serverPlayer), damage);
        target.setRemainingFireTicks(charged ? 220 : 90 + stage * 20);
        drawRing(serverLevel, target.position().add(0.0, 0.8, 0.0), 1.1 + stage * 0.12, charged ? ParticleTypes.WITCH : ParticleTypes.FLAME, charged ? 42 : 24);
        serverLevel.sendParticles(ParticleTypes.LAVA, target.getX(), target.getY() + 1.0, target.getZ(), charged ? 18 : 7, 0.35, 0.45, 0.35, 0.0);
        if (stage >= 2 || data.classAwakeningActive(now)) {
            for (LivingEntity nearby : serverLevel.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(2.8 + stage * 0.2))) {
                if (nearby == target || nearby == serverPlayer || protectedAlly(serverPlayer, nearby)) continue;
                nearby.hurtServer(serverLevel, serverLevel.damageSources().playerAttack(serverPlayer), damage * 0.42F);
                nearby.setRemainingFireTicks(Math.max(nearby.getRemainingFireTicks(), 60));
            }
        }
        ServerNetworking.sendScreenShake(serverLevel, target.position(), 10.0, charged ? 0.75F : 0.38F, 6);
        if (charged) {
            serverLevel.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 1.0, target.getZ(), 35, 0.55, 0.65, 0.55, 0.08);
            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getY() + 1.0, target.getZ(), 18, 0.45, 0.55, 0.45, 0.04);
            AncientChargeSystem.consume(serverPlayer, data, 2, now);
        }
        creditMastery(serverPlayer, data, 2, now, 20L);
        return InteractionResult.PASS;
    }

    public static void tickServer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player);
        }
        tickMeteors();
        tickHellfireOrbs();
        tickNatureSeeds();
        tickVineTraps();
        tickLifeTrees();
        tickRootWaves();
        tickNatureShapes();
        tickFlightSpears();
        tickSkyBombs();
        tickTimeSpears();
        tickTimePrisons();
        tickTimeFields();
        tickTimeShapes();
        tickWardenAmbushes(server);
        tickWardenArmSegments();
        tickWardenArmStrikes();
        MoonPowerSystem.tickServer(server);
        ExpansionPowerSystem.tickServer(server);
        AnomalySystem.tickServer(server);
        AncientChargeSystem.tick(server);
        PowerCollisionSystem.tick(server);
        WorldEventSystem.tick(server);

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

        AwakeningSystem.tickPlayer(player, data, level, now);

        switch (data.powerClass()) {
            case WARDEN -> tickWarden(player, data, level, now);
            case FLIGHT -> tickFlight(player, data, level, now);
            case FIRE -> tickFire(player, data, level, now);
            case MOON -> MoonPowerSystem.tickPlayer(player, data, level, now);
            case MAGNETIC, SAND -> ExpansionPowerSystem.tickPlayer(player, data, level, now);
            case ANOMALY -> {
                AnomalySystem.tickPlayer(player, data, level, now);
                tickBorrowedClassEffects(player, data, level, now);
            }
            default -> { }
        }

        if (now % 10L == 0L) ServerNetworking.sync(player);
    }

    private static void tickWarden(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        // Pasif Warden göğüs aurası kaldırıldı. Parçacıklar yalnızca aktif güçler ve Uyanış sırasında görünür.
        // Warden görüşü: mob ve oyuncuları 30 blok içinde vurgular.
        if (data.unlockedLevel() >= 1 && now % 10L == 0L) {
            for (LivingEntity living : nearbyLiving(player, 30.0)) {
                if (living == player || protectedAlly(player, living)) continue;
                living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 26, 0, false, false, true));
                if (living.getDeltaMovement().lengthSqr() > 0.006 && now % 20L == 0L) {
                    level.sendParticles(ParticleTypes.SCULK_SOUL, living.getX(), living.getY() + 0.35, living.getZ(),
                        3, 0.18, 0.12, 0.18, 0.01);
                }
            }
        }
        // Eski sürümden kalmış Sculk Avı durumunu sessizce temizle.
        if ((data.wardenHuntUntil() != 0L || data.visionEnabled())
            && !WARDEN_AMBUSHES.containsKey(player.getUUID())) {
            data.setWardenHuntUntil(0L);
            data.setChargedWardenHunt(false);
            data.setVisionEnabled(false);
            PlayerDataStore.markDirty();
        }

        if (data.awakeningUntil() > now) {
            int stage = data.masteryStage(5);
            boolean boosted = data.chargedAwakening();
            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 35, boosted ? 4 : (stage >= 2 ? 3 : 2), false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 35, boosted ? 2 : 1, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 35, stage >= 3 ? 1 : 0, false, false, true));
            if (now % 4L == 0L) {
                level.sendParticles(boosted ? ParticleTypes.WITCH : ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), boosted ? 22 : 10, 0.85, 1.0, 0.85, 0.025);
            }
            if (now % 20L == 0L) {
                double auraRadius = AncientChargeSystem.radius(5.0 + stage * 0.7, boosted);
                for (LivingEntity target : nearbyLiving(player, auraRadius)) {
                    if (target == player || protectedAlly(player, target)) continue;
                    target.hurtServer(level, level.damageSources().playerAttack(player), AncientChargeSystem.damage(4.0F + stage * 1.5F, boosted));
                    Vec3 push = target.position().subtract(player.position());
                    if (push.lengthSqr() > 0.0001) {
                        push = push.normalize().scale(AncientChargeSystem.knockback(0.45 + stage * 0.08, boosted));
                        target.push(push.x, 0.12, push.z);
                    }
                }
                drawRing(level, player.position(), auraRadius, boosted ? ParticleTypes.WITCH : ParticleTypes.SCULK_SOUL, boosted ? 72 : 42);
            }
        } else if (data.awakeningUntil() != 0L) {
            data.setAwakeningUntil(0L);
            data.setChargedAwakening(false);
            player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 100, 0, false, true, true));
            PlayerDataStore.markDirty();
        }
    }

    private static void tickFlight(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        // Uçuş sınıfının 1.0.6 karşılığı Kadim Ejderhadır. Eski kayıtlar FLIGHT enumunu kullanmaya devam eder.
        boolean formActive = data.dragonFormUntil() > now;
        boolean awakeningActive = data.classAwakeningActive(now);

        // Kadim Ejderha pasifi: düşme hasarı yok, ateşe kısmi dayanıklılık.
        if (data.unlockedLevel() >= 1) {
            player.fallDistance = 0.0F;
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 30, 0, false, false, true));
        }

        if (data.dragonScalesUntil() > now && data.dragonScaleCharges() > 0) {
            int charges = data.dragonScaleCharges();
            if (now % 3L == 0L) {
                for (int i = 0; i < charges; i++) {
                    double angle = now * 0.12 + Math.PI * 2.0 * i / Math.max(1, charges);
                    double x = player.getX() + Math.cos(angle) * 1.25;
                    double z = player.getZ() + Math.sin(angle) * 1.25;
                    level.sendParticles(i % 2 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.WITCH,
                        x, player.getY() + 1.05 + Math.sin(angle * 1.7) * 0.28, z, 3, 0.08, 0.14, 0.08, 0.01);
                }
            }
            for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(3.2))) {
                if (projectile.getOwner() == player) continue;
                Vec3 velocity = projectile.getDeltaMovement();
                if (velocity.lengthSqr() < 0.001) continue;
                projectile.setDeltaMovement(velocity.scale(-1.35).add(0.0, 0.08, 0.0));
                projectile.setOwner(player);
            }
        } else if (data.dragonScalesUntil() != 0L || data.dragonScaleCharges() != 0) {
            data.setDragonScalesUntil(0L);
            data.setDragonScaleCharges(0);
            PlayerDataStore.markDirty();
        }

        DragonBreathState breath = DRAGON_BREATHS.get(player.getUUID());
        if (breath != null) {
            if (breath.untilTick <= now || !player.isAlive()) DRAGON_BREATHS.remove(player.getUUID());
            else tickDragonBreath(player, breath, level, now);
        }

        if (formActive || awakeningActive) {
            if (!player.isCreative() && !player.isSpectator() && !player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            }
            player.fallDistance = 0.0F;
            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 30, formActive ? 2 : 1, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 1, false, false, true));
            if (now % 5L == 0L) {
                Vec3 side = horizontalDirection(player.getLookAngle()).cross(new Vec3(0.0, 1.0, 0.0)).normalize();
                Vec3 back = player.position().add(player.getLookAngle().scale(-0.8)).add(0.0, 1.1, 0.0);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, back.x + side.x * 0.7, back.y, back.z + side.z * 0.7, 8, 0.28, 0.42, 0.28, 0.025);
                level.sendParticles(ParticleTypes.WITCH, back.x - side.x * 0.7, back.y, back.z - side.z * 0.7, 6, 0.24, 0.38, 0.24, 0.02);
            }
        } else {
            if (data.dragonFormUntil() != 0L) {
                data.setDragonFormUntil(0L);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 55, 0.9, 1.0, 0.9, 0.045);
                PlayerDataStore.markDirty();
            }
            if (!player.isCreative() && !player.isSpectator() && player.getAbilities().mayfly) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }

        UUID grabbed = DRAGON_CLAW_TARGET.get(player.getUUID());
        long grabUntil = DRAGON_CLAW_UNTIL.getOrDefault(player.getUUID(), 0L);
        if (grabbed != null && grabUntil > now) {
            Entity entity = level.getEntity(grabbed);
            if (entity instanceof LivingEntity target && target.isAlive()) {
                Vec3 anchor = player.position().add(player.getLookAngle().normalize().scale(2.2)).add(0.0, 1.0, 0.0);
                target.setPos(anchor.x, anchor.y, anchor.z);
                target.setDeltaMovement(Vec3.ZERO);
                if (now % 20L == 0L) target.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
                level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 0.8, target.getZ(), 5, 0.35, 0.5, 0.35, 0.015);
            } else {
                clearDragonClaw(player.getUUID());
            }
        } else if (grabbed != null) {
            clearDragonClaw(player.getUUID());
        }
    }

    private static void tickFire(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        if (data.unlockedLevel() >= 1) {
            boolean preventedFire = player.getRemainingFireTicks() > 0;
            player.setRemainingFireTicks(0);
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, true));
            if (preventedFire) creditMastery(player, data, 1, now, 200L);
        }

        tickActiveFireRing(player, data, level, now);
    }

    /** Kopyalanmış süreli güçlerin Anomali sınıfında da tam süre çalışmasını sağlar. */
    private static void tickBorrowedClassEffects(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        if (data.wardenHuntUntil() != 0L || data.awakeningUntil() != 0L) {
            tickWarden(player, data, level, now);
        }
        if (data.temporaryElytraUntil() != 0L || data.dragonScalesUntil() != 0L || data.dragonFormUntil() != 0L) {
            tickFlight(player, data, level, now);
        }
        if (data.fireRingUntil() != 0L) {
            tickActiveFireRing(player, data, level, now);
        }
    }

    private static void tickActiveFireRing(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        if (data.fireRingUntil() > now) {
            int stage = data.masteryStage(3);
            boolean boosted = data.chargedFireRing();
            double radius = AncientChargeSystem.radius(10.0 + stage * 0.6, boosted);
            if (now % 2L == 0L) {
                double baseAngle = now * 0.16;
                int cores = boosted ? 6 : 4;
                for (int i = 0; i < cores; i++) {
                    double angle = baseAngle + Math.PI * 2.0 * i / cores;
                    double orbit = 2.4 + Math.sin(now * 0.08 + i) * 0.35;
                    double x = player.getX() + Math.cos(angle) * orbit;
                    double z = player.getZ() + Math.sin(angle) * orbit;
                    double y = player.getY() + 0.8 + Math.sin(angle * 2.0) * 0.45;
                    level.sendParticles(i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.LAVA, x, y, z,
                        boosted ? 8 : 5, 0.16, 0.22, 0.16, 0.02);
                }
            }
            if (now % 20L == 0L) {
                for (LivingEntity target : nearbyLiving(player, radius)) {
                    if (target == player || protectedAlly(player, target)) continue;
                    target.hurtServer(level, level.damageSources().playerAttack(player), AncientChargeSystem.damage(2.5F + stage * 0.5F, boosted));
                    target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 70));
                }
            }
            if (now % 3L == 0L) {
                drawRing(level, player.position(), radius, boosted ? ParticleTypes.WITCH : ParticleTypes.FLAME, boosted ? 82 : 48);
                igniteSparseGround(level, player.blockPosition(), (int) Math.ceil(radius), now);
            }
        } else if (data.fireRingUntil() != 0L) {
            data.setFireRingUntil(0L);
            data.setChargedFireRing(false);
            PlayerDataStore.markDirty();
        }
    }

    private static void tickNature(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        if (data.unlockedLevel() < 1) return;
        BlockState below = level.getBlockState(player.blockPosition().below());
        boolean naturalGround = below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.DIRT)
            || below.is(Blocks.PODZOL) || below.is(Blocks.MOSS_BLOCK)
            || below.is(Blocks.OAK_LEAVES) || below.is(Blocks.MANGROVE_ROOTS);
        boolean ancientBoost = data.ancientChargeActive(now);
        long healingInterval = ancientBoost ? 50L : 100L;
        if (naturalGround && player.getHealth() < player.getMaxHealth() && now % healingInterval == 0L) {
            int stage = data.masteryStage(1);
            player.heal((1.3F + stage * 0.45F) * (ancientBoost ? 2.0F : 1.0F));
            level.sendParticles(ancientBoost ? ParticleTypes.WITCH : ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 0.3, player.getZ(), ancientBoost ? 8 : 5, 0.35, 0.20, 0.35, 0.01);
            creditMastery(player, data, 1, now, 500L);
        }
        long rescueReady = NATURE_CRITICAL_COOLDOWN.getOrDefault(player.getUUID(), 0L);
        if (naturalGround && player.getHealth() <= Math.max(4.0F, player.getMaxHealth() * 0.25F) && now >= rescueReady) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ancientBoost ? 180 : 120, ancientBoost ? 3 : 2, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ancientBoost ? 300 : 200, ancientBoost ? 3 : 1, false, true, true));
            NATURE_CRITICAL_COOLDOWN.put(player.getUUID(), now + 2400L);
            level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.25F);
        }
        if (data.natureTreeUntil() != 0L && data.natureTreeUntil() <= now) {
            data.setNatureTreeUntil(0L);
        data.setDragonScalesUntil(0L);
        data.setDragonScaleCharges(0);
        data.setDragonFormUntil(0L);
        DRAGON_BREATHS.remove(player.getUUID());
        clearDragonClaw(player.getUUID());
            PlayerDataStore.markDirty();
        }
    }

    private static void tickTime(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        java.util.ArrayDeque<TimeSnapshot> history = TIME_HISTORY.computeIfAbsent(player.getUUID(), ignored -> new java.util.ArrayDeque<>());
        history.addLast(new TimeSnapshot(now, player.position(), player.getHealth(), player.getYRot(), player.getXRot()));
        while (!history.isEmpty() && now - history.peekFirst().tick > 160L) history.removeFirst();

        if (data.unlockedLevel() >= 1 && data.passiveEnabled()) {
            boolean ancientBoost = data.ancientChargeActive(now);
            int stage = data.masteryStage(1);
            float fallCap = ancientBoost ? 1.0F : Math.max(2.0F, 3.5F - stage * 0.4F);
            if (player.fallDistance > fallCap) player.fallDistance = fallCap;
            if (now % (ancientBoost ? 1L : 2L) == 0L) {
                double radius = (ancientBoost ? 11.0 : 7.0) + stage * 1.25;
                AABB area = player.getBoundingBox().inflate(radius);
                for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, area)) {
                    if (projectile.getOwner() == player) continue;
                    projectile.setDeltaMovement(projectile.getDeltaMovement().scale(ancientBoost ? 0.35 : 0.58));
                }
            }
        }
    }

    public static void useSelectedPower(ServerPlayer player, PlayerPowerData data) {
        if (AnomalySystem.isVoided(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Varlıktan çıkarılmışken güç kullanamazsın."));
            return;
        }
        if (data.powerClass() == PowerClass.NONE || data.unlockedLevel() == 0) {
            player.sendSystemMessage(Component.literal("Önce O ekranından bir seviye açmalısın."));
            return;
        }
        int power = data.selectedPower();
        if (power > data.unlockedLevel()) return;
        if (WARDEN_AMBUSHES.containsKey(player.getUUID())
            && !(data.powerClass() == PowerClass.WARDEN && power == 4)) {
            player.sendSystemMessage(Component.literal("Derinlik Pususu aktif. Önce 4. gücü tekrar kullanıp yüzeye çık."));
            return;
        }

        long now = player.level().getGameTime();
        data.comboActive(now); // Süresi dolmuş kombo penceresini temizle.
        if (isDragonSilenced(player, now)) {
            player.sendSystemMessage(Component.literal("Kadim Kükreme gücünü kısa süreliğine susturdu."));
            return;
        }
        if (power == 6 && data.ancientChargeActive(now)) {
            player.sendSystemMessage(Component.literal("Antik Şehir Şarjı taşırken 6. güç kullanılamaz."));
            return;
        }
        int remaining = data.cooldownRemaining(power, now);
        if (remaining > 0) {
            player.sendSystemMessage(Component.literal("Güç " + formatSeconds(remaining) + " saniye sonra hazır."));
            return;
        }

        boolean pendingCombo = data.comboActive(now);
        boolean expectedFinisher = pendingCombo
            && data.comboStarterPower() == PowerCatalog.comboStarterPower(data.powerClass())
            && power == PowerCatalog.comboFinisherPower(data.powerClass());
        boolean charged = AncientChargeSystem.isUsableCharge(data, now, power);
        boolean awakened = data.classAwakeningActive(now);

        if (expectedFinisher) {
            boolean comboUsed = useComboFinisher(player, data, power, now, charged || awakened);
            if (comboUsed) {
                recordMasteryUse(player, data, power);
                AnomalySystem.recordPowerUse(player, data.powerClass(), power);
                if (charged) {
                    data.consumeAncientChargeForCombo(now, PowerCatalog.comboStarterPower(data.powerClass()), power);
                    AncientChargeSystem.emitChargedBurst((ServerLevel) player.level(), player.position().add(0.0, 1.0, 0.0), data.powerClass(), 1.5);
                }
                data.clearCombo();
                PlayerDataStore.markDirty();
                ServerNetworking.sync(player);
            }
            return;
        }

        // Yanlış ikinci güç normal çalışır ama bekleyen kombinasyonu iptal eder.
        if (pendingCombo && !expectedFinisher) data.clearCombo();

        boolean comboStarter = data.comboModeEnabled()
            && data.unlockedLevel() >= PowerCatalog.comboFinisherPower(data.powerClass())
            && PowerCatalog.isComboStarter(data.powerClass(), power);
        // Antik Şehir hakkı ilk hazırlık gücünde harcanmaz; yalnızca birleşik saldırıyı güçlendirir.
        boolean normalCharged = awakened || (charged && !comboStarter);
        boolean used = switch (data.powerClass()) {
            case WARDEN -> useWarden(player, data, power, now, normalCharged);
            case FLIGHT -> useFlight(player, data, power, now, normalCharged);
            case FIRE -> useFire(player, data, power, now, normalCharged, comboStarter);
            case MOON -> MoonPowerSystem.use(player, data, power, now, normalCharged);
            case MAGNETIC -> ExpansionPowerSystem.useMagnetic(player, data, power, now, normalCharged);
            case SAND -> ExpansionPowerSystem.useSand(player, data, power, now, normalCharged);
            case ANOMALY -> AnomalySystem.use(player, data, power, now, normalCharged);
            default -> false;
        };

        if (used) {
            ServerNetworking.sendCastAnimation((ServerLevel) player.level(), player.position().add(0.0, 1.0, 0.0), data.powerClass(), power);
            if (comboStarter) beginImmediateComboIfNeeded(player, data, power, now);
            PowerCollisionSystem.registerCast(player, data, power, now, normalCharged);
            recordMasteryUse(player, data, power);
            AnomalySystem.recordPowerUse(player, data.powerClass(), power);
            if (charged) AncientChargeSystem.consume(player, data, power, now);
            PlayerDataStore.markDirty();
            ServerNetworking.sync(player);
        }
    }

    /**
     * Yönetici/test komutundan bir saldırıyı doğrudan çağırır.
     * Sınıfı değiştirmez, XP/ustalık kazandırmaz ve komutun oluşturduğu cooldown'u temizler.
     * "_charged" son eki aynı saldırının Antik Şehir ile güçlendirilmiş hâlini çağırır.
     */
    public static boolean triggerAttack(ServerPlayer player, String attackId) {
        if (player == null || attackId == null || attackId.isBlank()) return false;

        String normalized = attackId.trim().toLowerCase(java.util.Locale.ROOT);
        boolean charged = normalized.endsWith("_charged");
        String base = charged ? normalized.substring(0, normalized.length() - "_charged".length()) : normalized;
        PlayerPowerData data = PlayerDataStore.get(player.getUUID());
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        int stage = 3; // Trigger saldırıları yayın/test amacıyla tam ustalık görünümünde çalışır.
        int cooldownSlot = 0;

        boolean used = switch (base) {
            case "earthquake" -> {
                double radius = AncientChargeSystem.radius(7.0 + stage * 1.2, charged);
                for (LivingEntity target : nearbyLiving(player, radius)) {
                    if (target == player || protectedAlly(player, target)) continue;
                    target.hurtServer(level, level.damageSources().playerAttack(player), AncientChargeSystem.damage(10.0F + stage * 2.0F, charged));
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 160, 3, false, true, true));
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 2, false, true, true));
                    Vec3 push = target.position().subtract(player.position());
                    if (push.lengthSqr() > 0.0001) {
                        push = push.normalize().scale(AncientChargeSystem.knockback(1.25, charged));
                        target.push(push.x, 0.52, push.z);
                    }
                }
                drawRing(level, player.position(), radius, charged ? ParticleTypes.WITCH : ParticleTypes.SCULK_SOUL, charged ? 104 : 76);
                level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.5F, 0.72F);
                yield true;
            }
            case "sonic" -> {
                sonicBlast(player, data, stage, charged);
                yield true;
            }
            case "sonic_fault" -> {
                sonicFault(player, stage, charged);
                yield true;
            }
            case "sky_spear" -> {
                launchFlightSpear(player, stage, charged, false);
                yield true;
            }
            case "sky_bomb" -> {
                launchSkyBomb(player, stage, charged, false, null);
                yield true;
            }
            case "sky_cataclysm" -> {
                launchSkyCataclysm(player, stage, charged);
                yield true;
            }
            case "dragon_dash" -> { cooldownSlot = 1; yield useFlight(player, data, 1, now, charged); }
            case "dragon_breath" -> { cooldownSlot = 2; yield useFlight(player, data, 2, now, charged); }
            case "dragon_scales" -> { cooldownSlot = 3; yield useFlight(player, data, 3, now, charged); }
            case "dragon_claw" -> { cooldownSlot = 4; yield useFlight(player, data, 4, now, charged); }
            case "dragon_roar" -> { cooldownSlot = 5; yield useFlight(player, data, 5, now, charged); }
            case "dragon_form" -> { cooldownSlot = 6; yield useFlight(player, data, 6, now, charged); }
            case "fire_ring" -> {
                cooldownSlot = 3;
                yield useFire(player, data, 3, now, charged, false);
            }
            case "hellfire" -> {
                hellfireBeam(player, stage, charged, false);
                yield true;
            }
            case "meteor" -> {
                scheduleMeteors(player, data, stage, charged);
                yield true;
            }
            case "moon_crescent" -> { cooldownSlot = 1; yield MoonPowerSystem.use(player, data, 1, now, charged); }
            case "moon_step" -> { cooldownSlot = 2; yield MoonPowerSystem.use(player, data, 2, now, charged); }
            case "moon_gravity" -> { cooldownSlot = 3; yield MoonPowerSystem.use(player, data, 3, now, charged); }
            case "moon_mirror" -> { cooldownSlot = 4; yield MoonPowerSystem.use(player, data, 4, now, charged); }
            case "moon_eclipse" -> { cooldownSlot = 5; yield MoonPowerSystem.use(player, data, 5, now, charged); }
            case "moon_beast" -> { cooldownSlot = 6; yield MoonPowerSystem.use(player, data, 6, now, charged); }
            case "broken_step" -> {
                cooldownSlot = 1;
                yield AnomalySystem.use(player, data, 1, now, charged);
            }
            case "reverse" -> {
                cooldownSlot = 2;
                yield AnomalySystem.use(player, data, 2, now, charged);
            }
            case "void_out" -> {
                cooldownSlot = 5;
                yield AnomalySystem.use(player, data, 5, now, charged);
            }
            case "reality_404" -> {
                if (data.powerClass() != PowerClass.ANOMALY) {
                    player.sendSystemMessage(Component.literal("reality_404 yalnızca Anomali sınıfındayken çağrılabilir."));
                    yield false;
                }
                cooldownSlot = 6;
                yield AnomalySystem.use(player, data, 6, now, charged);
            }
            default -> false;
        };

        if (!used) return false;
        ServerNetworking.sendCastAnimation(level, player.position().add(0.0, 1.0, 0.0), data.powerClass(), Math.max(1, cooldownSlot));
        if (cooldownSlot > 0) data.clearCooldown(cooldownSlot, now);
        PlayerDataStore.markDirty();
        ServerNetworking.sync(player);
        return true;
    }

    public static void toggleSelectedFeature(ServerPlayer player, PlayerPowerData data) {
        boolean changed = false;
        long now = player.level().getGameTime();
        if (data.powerClass() == PowerClass.FLIGHT) {
            player.sendSystemMessage(Component.literal("Kadim Ejderha güçlerini R ile kullan; düşme ve ateş direnci pasifi sürekli aktiftir."));
        } else if (data.powerClass() == PowerClass.WARDEN && data.unlockedLevel() >= 4) {
            player.sendSystemMessage(Component.literal("Derinlik Pususu: 4. gücü R ile başlat; hareket ettikten sonra R ile yüzeye saldır."));
        } else if (data.powerClass() == PowerClass.FIRE) {
            player.sendSystemMessage(Component.literal("Ateş sınıfındaki güçler R ile veya otomatik olarak çalışır."));
        } else if (data.powerClass() == PowerClass.MOON) {
            player.sendSystemMessage(Component.literal("Ay güçlerini R ile kullan. Ay Aynasını ikinci R basışıyla beyaz ay halkası olarak fırlat."));
        } else if (data.powerClass() == PowerClass.ANOMALY) {
            player.sendSystemMessage(Component.literal("Anomali: güçleri R ile kullan. Hasar seçimi hazırken V kalbe, X hedefe dönüştürür."));
        } else if (data.powerClass() == PowerClass.MAGNETIC) {
            player.sendSystemMessage(Component.literal("Manyetik güçleri R ile kullan. Metal Fırtınasını ikinci R basışıyla fırlat."));
        } else if (data.powerClass() == PowerClass.SAND) {
            player.sendSystemMessage(Component.literal("Kum güçleri R ile kullan. Kum görüş etkisi 4 saniye veya suya girene kadar sürer."));
        }
        if (changed) {
            PlayerDataStore.markDirty();
            ServerNetworking.sync(player);
        }
    }

    public static void toggleComboMode(ServerPlayer player, PlayerPowerData data) {
        if (data.powerClass() == PowerClass.ANOMALY) {
            player.sendSystemMessage(Component.literal("Anomali güçleri kombo yerine ? ile hamle kopyalar."));
            return;
        }
        boolean enabled = data.toggleComboMode();
        PlayerDataStore.markDirty();
        player.sendSystemMessage(Component.literal("Kombo Modu: " + (enabled ? "AÇIK" : "KAPALI")));
        ServerNetworking.sync(player);
    }

    public static void tryRocketlessLaunch(ServerPlayer player, PlayerPowerData data) {
        long now = player.level().getGameTime();
        if (data.powerClass() != PowerClass.FLIGHT || data.unlockedLevel() < 1) return;
        long last = LAST_SKY_IMPACT.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
        if (now - last < 24L) return;
        Vec3 look = player.getLookAngle().normalize();
        player.setDeltaMovement(look.scale(data.dragonFormUntil() > now ? 1.65 : 1.18).add(0.0, 0.28, 0.0));
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        LAST_SKY_IMPACT.put(player.getUUID(), now);
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 0.7, player.getZ(), 22, 0.48, 0.35, 0.48, 0.06);
    }

    private static void removeTemporaryElytra(ServerPlayer player, PlayerPowerData data) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.is(Items.ELYTRA)) player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        data.setTemporaryElytraUntil(0L);
        data.setChargedTemporaryElytra(false);
    }

    private static boolean useWarden(ServerPlayer player, PlayerPowerData data, int power, long now, boolean charged) {
        ServerLevel level = (ServerLevel) player.level();
        int stage = data.masteryStage(power);
        switch (power) {
            case 1 -> {
                int duration = AncientChargeSystem.duration(400 + stage * 100, charged);
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, duration, charged ? 4 : (stage >= 2 ? 2 : 1), false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, duration, charged ? 3 : (stage >= 3 ? 2 : 1), false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, charged ? 3 : (stage >= 2 ? 1 : 0), false, true, true));
                data.setCooldown(1, now, Math.max(600, 900 - stage * 100));
                level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 0.9F);
                level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), charged ? 58 : 28, 0.7, 0.8, 0.7, 0.025);
                if (charged) AncientChargeSystem.emitChargedBurst(level, player.position().add(0.0, 1.0, 0.0), PowerClass.WARDEN, 1.25);
                return true;
            }
            case 2 -> {
                double radius = AncientChargeSystem.radius(7.0 + stage * 1.2, charged);
                for (LivingEntity target : nearbyLiving(player, radius)) {
                    if (target == player || protectedAlly(player, target)) continue;
                    target.hurtServer(level, level.damageSources().playerAttack(player), AncientChargeSystem.damage(10.0F + stage * 2.0F, charged));
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100 + stage * 20, stage >= 2 ? 3 : 2, false, true, true));
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100 + stage * 20, stage >= 3 ? 2 : 1, false, true, true));
                    Vec3 push = target.position().subtract(player.position());
                    if (push.lengthSqr() > 0.0001) {
                        push = push.normalize().scale(AncientChargeSystem.knockback(0.9 + stage * 0.12, charged));
                        target.push(push.x, 0.45, push.z);
                    }
                }
                for (int wave = 0; wave < 3; wave++) {
                    drawRing(level, player.position().add(0.0, 0.08 * wave, 0.0), radius * (0.45 + wave * 0.27),
                        wave == 2 && charged ? ParticleTypes.WITCH : ParticleTypes.SCULK_SOUL, charged ? 72 : 48);
                }
                for (int i = 0; i < 24; i++) {
                    double angle = Math.PI * 2.0 * i / 24.0;
                    double distance = radius * (0.25 + (i % 4) * 0.18);
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        player.getX() + Math.cos(angle) * distance, player.getY() + 0.18,
                        player.getZ() + Math.sin(angle) * distance, 2, 0.08, 0.10, 0.08, 0.01);
                }
                level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.5F, 0.76F);
                ServerNetworking.sendScreenShake(level, player.position(), 30.0, charged ? 1.55F : 1.05F, 14);
                data.setCooldown(2, now, Math.max(360, 600 - stage * 60));
                return true;
            }
            case 3 -> {
                sonicBlast(player, data, stage, charged);
                data.setCooldown(3, now, Math.max(220, 380 - stage * 40));
                return true;
            }
            case 4 -> {
                WardenAmbushState active = WARDEN_AMBUSHES.get(player.getUUID());
                if (active != null) {
                    finishWardenAmbush(player, data, active, now, false);
                    return false; // İlk basışta ustalık kaydı yapıldı; çıkışta ikinci kez sayma.
                }
                beginWardenAmbush(player, data, level, now, stage, charged);
                data.setCooldown(4, now, 1); // Bir sonraki tikten itibaren R ile yüzeye çıkılabilir.
                return true;
            }
            case 5 -> {
                int duration = AncientChargeSystem.duration(600 + stage * 100, charged);
                data.setAwakeningUntil(now + duration);
                data.setChargedAwakening(charged);
                data.setCooldown(5, now, Math.max(1500, 2400 - stage * 180));
                level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.6F, 0.68F);
                level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), charged ? 120 : 85, 1.3, 1.3, 1.3, 0.055);
                if (charged) AncientChargeSystem.emitChargedBurst(level, player.position().add(0.0, 1.0, 0.0), PowerClass.WARDEN, 1.6);
                return true;
            }
            case 6 -> {
                if (!AncientChargeSystem.beginBeam(player, data, now)) return false;
                data.setCooldown(6, now, 2400);
                level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5F, 0.62F);
                return true;
            }
            default -> { return false; }
        }
    }


    private static void beginWardenAmbush(ServerPlayer player, PlayerPowerData data, ServerLevel level,
                                          long now, int stage, boolean charged) {
        long duration = charged ? 110L : 80L;
        Map<EquipmentSlot, ItemStack> hiddenEquipment = hideAmbushEquipment(player);
        WARDEN_AMBUSHES.put(player.getUUID(), new WardenAmbushState(
            level, player.getUUID(), player.position(), now, now + duration, stage, charged,
            player.isInvisible(), hiddenEquipment, new ArrayList<>()));
        data.setWardenHuntUntil(now + duration);
        player.setInvisible(true);
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, (int) duration + 10, 3, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, (int) duration + 10, charged ? 3 : 2, false, false, true));
        player.fallDistance = 0.0F;
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.1F, 0.68F);
        drawRing(level, player.position().add(0.0, 0.08, 0.0), charged ? 3.4 : 2.6,
            charged ? ParticleTypes.WITCH : ParticleTypes.SCULK_SOUL, charged ? 72 : 48);
        spawnBurrowCore(level, player, now, duration);
        player.sendSystemMessage(Component.literal("Derinlik Pususu: hareket et; R ile yüzeye saldır."));
    }

    private static void tickWardenAmbushes(MinecraftServer server) {
        for (WardenAmbushState state : new ArrayList<>(WARDEN_AMBUSHES.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(state.playerId);
            if (player == null || !player.isAlive() || player.level() != state.level) {
                if (player != null) restoreAfterAmbush(player, state);
                WARDEN_AMBUSHES.remove(state.playerId);
                continue;
            }
            long now = state.level.getGameTime();
            PlayerPowerData data = PlayerDataStore.get(player.getUUID());
            if ((data.powerClass() != PowerClass.WARDEN && data.powerClass() != PowerClass.ANOMALY) || now >= state.endTick) {
                finishWardenAmbush(player, data, state, now, true);
                continue;
            }
            player.setInvisible(true);
            keepAmbushEquipmentHidden(player, state);
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 8, 3, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 8, state.charged ? 3 : 2, false, false, true));
            player.fallDistance = 0.0F;
            Vec3 offset = horizontalDirection(player.position().subtract(state.origin));
            double distance = new Vec3(player.getX() - state.origin.x, 0.0, player.getZ() - state.origin.z).length();
            double maxDistance = 13.0 + state.stage * 1.5 + (state.charged ? 4.0 : 0.0);
            if (distance > maxDistance) {
                Vec3 limited = state.origin.add(offset.scale(maxDistance));
                player.setPos(limited.x, player.getY(), limited.z);
            }
            if (now % 4L == 0L) {
                Vec3 ground = findGroundPoint(state.level, player.position());
                drawRing(state.level, ground.add(0.0, 0.06, 0.0), state.charged ? 1.8 : 1.35,
                    state.charged ? ParticleTypes.WITCH : ParticleTypes.SCULK_SOUL, state.charged ? 24 : 16);
            }
        }
    }

    private static void finishWardenAmbush(ServerPlayer player, PlayerPowerData data, WardenAmbushState state,
                                            long now, boolean automatic) {
        if (WARDEN_AMBUSHES.remove(player.getUUID()) == null) return;
        restoreAfterAmbush(player, state);
        Vec3 exit = findGroundPoint(state.level, player.position());
        player.setPos(exit.x, exit.y, exit.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;

        double radius = 9.0 + state.stage * 1.1 + (state.charged ? 3.0 : 0.0);
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity living : state.level.getEntitiesOfClass(LivingEntity.class,
            new AABB(exit.x - radius, exit.y - 4.0, exit.z - radius, exit.x + radius, exit.y + 6.0, exit.z + radius))) {
            if (living == player || protectedAlly(player, living) || !living.isAlive()) continue;
            targets.add(living);
        }
        targets.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));

        state.level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.5F, 0.72F);
        ServerNetworking.sendScreenShake(state.level, exit, 34.0, state.charged ? 1.65F : 1.15F, 15);
        drawRing(state.level, exit.add(0.0, 0.10, 0.0), radius * 0.72,
            state.charged ? ParticleTypes.WITCH : ParticleTypes.SCULK_SOUL, state.charged ? 100 : 72);

        if (targets.isEmpty()) {
            for (LivingEntity living : nearbyLiving(player, Math.min(5.5, radius))) {
                if (living == player || protectedAlly(player, living)) continue;
                living.hurtServer(state.level, state.level.damageSources().playerAttack(player),
                    AncientChargeSystem.damage(7.0F + state.stage, state.charged));
                Vec3 push = horizontalDirection(living.position().subtract(exit)).scale(
                    AncientChargeSystem.knockback(1.35, state.charged));
                living.push(push.x, 0.65, push.z);
            }
        } else {
            Vec3 back = horizontalDirection(player.getLookAngle()).scale(-0.55);
            Vec3 baseStart = player.position().add(back).add(0.0, 1.15, 0.0);
            for (int arm = 0; arm < 4; arm++) {
                LivingEntity target = targets.get(arm % Math.min(4, targets.size()));
                long startTick = now + arm * 5L;
                long impactTick = startTick + (state.charged ? 12L : 16L);
                double side = (arm - 1.5) * 0.48;
                Vec3 right = horizontalDirection(player.getLookAngle()).cross(new Vec3(0.0, 1.0, 0.0));
                Vec3 armStart = baseStart.add(right.scale(side)).add(0.0, (arm % 2) * 0.32, 0.0);
                spawnWardenArm(state.level, armStart, target, startTick, impactTick, arm, state.charged);
                WARDEN_ARM_STRIKES.add(new WardenArmStrike(state.level, player.getUUID(), target.getUUID(),
                    impactTick, arm, state.stage, state.charged));
            }
        }
        data.setWardenHuntUntil(0L);
        data.setCooldown(4, now, Math.max(520, 820 - state.stage * 70));
        if (automatic) creditMastery(player, data, 4, now, 20L);
        PlayerDataStore.markDirty();
        ServerNetworking.sendCastAnimation(state.level, exit.add(0.0, 1.0, 0.0), PowerClass.WARDEN, 4);
        ServerNetworking.sync(player);
    }

    private static void restoreAfterAmbush(ServerPlayer player, WardenAmbushState state) {
        restoreAmbushEquipment(player, state.hiddenEquipment, state.hiddenExtraItems);
        player.setInvisible(state.wasInvisible);
        player.removeEffect(MobEffects.SPEED);
        player.removeEffect(MobEffects.RESISTANCE);
        player.fallDistance = 0.0F;
    }

    private static Map<EquipmentSlot, ItemStack> hideAmbushEquipment(ServerPlayer player) {
        Map<EquipmentSlot, ItemStack> saved = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : new EquipmentSlot[]{
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
        }) {
            ItemStack stack = player.getItemBySlot(slot);
            saved.put(slot, stack.copy());
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        return saved;
    }

    private static void keepAmbushEquipmentHidden(ServerPlayer player, WardenAmbushState state) {
        for (EquipmentSlot slot : new EquipmentSlot[]{
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
        }) {
            ItemStack visible = player.getItemBySlot(slot);
            if (visible.isEmpty()) continue;
            state.hiddenExtraItems.add(visible.copy());
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    private static void restoreAmbushEquipment(ServerPlayer player, Map<EquipmentSlot, ItemStack> saved,
                                                List<ItemStack> hiddenExtraItems) {
        if (saved == null || saved.isEmpty()) return;
        for (Map.Entry<EquipmentSlot, ItemStack> entry : saved.entrySet()) {
            ItemStack stack = entry.getValue();
            if (stack == null || stack.isEmpty()) continue;
            EquipmentSlot slot = entry.getKey();
            if (player.getItemBySlot(slot).isEmpty()) {
                player.setItemSlot(slot, stack.copy());
            } else {
                ItemStack copy = stack.copy();
                if (!player.getInventory().add(copy) && !copy.isEmpty()) player.drop(copy, false, false);
            }
        }
        if (hiddenExtraItems != null) {
            for (ItemStack extra : hiddenExtraItems) {
                if (extra == null || extra.isEmpty()) continue;
                ItemStack copy = extra.copy();
                if (!player.getInventory().add(copy) && !copy.isEmpty()) player.drop(copy, false, false);
            }
            hiddenExtraItems.clear();
        }
    }

    public static void handleDisconnect(ServerPlayer player) {
        WardenAmbushState state = WARDEN_AMBUSHES.remove(player.getUUID());
        if (state != null) restoreAfterAmbush(player, state);
        ExpansionPowerSystem.handleDisconnect(player);
    }

    private static void spawnBurrowCore(ServerLevel level, ServerPlayer player, long now, long duration) {
        Vec3 start = player.position().add(0.0, 0.45, 0.0);
        for (int i = 0; i < 5; i++) {
            ItemStack stack = new ItemStack(i == 0 ? Items.SCULK_CATALYST : Items.ECHO_SHARD);
            ItemEntity body = new ItemEntity(level, start.x, start.y, start.z, stack);
            body.setNoGravity(true);
            body.setNeverPickUp();
            body.setUnlimitedLifetime();
            body.setInvulnerable(true);
            body.setGlowingTag(true);
            if (!level.addFreshEntity(body)) continue;
            WARDEN_ARM_SEGMENTS.add(new WardenArmSegment(level, body.getUUID(), null, start,
                start.add(0.0, -1.4, 0.0), now + i, now + Math.min(20L, duration), i, 5,
                (i - 2) * 0.10, 0.15));
        }
    }

    private static void spawnWardenArm(ServerLevel level, Vec3 start, LivingEntity target, long startTick,
                                       long endTick, int armIndex, boolean charged) {
        int segments = charged ? 9 : 7;
        for (int segment = 0; segment < segments; segment++) {
            ItemStack stack = new ItemStack(segment == segments - 1 ? Items.SCULK_CATALYST
                : (segment % 3 == 0 ? Items.SCULK_SENSOR : Items.ECHO_SHARD));
            ItemEntity body = new ItemEntity(level, start.x, start.y, start.z, stack);
            body.setNoGravity(true);
            body.setNeverPickUp();
            body.setUnlimitedLifetime();
            body.setInvulnerable(true);
            body.setGlowingTag(true);
            if (!level.addFreshEntity(body)) continue;
            WARDEN_ARM_SEGMENTS.add(new WardenArmSegment(level, body.getUUID(), target.getUUID(), start,
                target.getEyePosition(), startTick, endTick, segment, segments,
                (armIndex - 1.5) * 0.34, 0.75 + armIndex * 0.12));
        }
    }

    private static void tickWardenArmSegments() {
        Iterator<WardenArmSegment> iterator = WARDEN_ARM_SEGMENTS.iterator();
        while (iterator.hasNext()) {
            WardenArmSegment segment = iterator.next();
            Entity raw = segment.level.getEntity(segment.entityId);
            long now = segment.level.getGameTime();
            if (!(raw instanceof ItemEntity body) || now >= segment.endTick) {
                if (raw != null) raw.discard();
                iterator.remove();
                continue;
            }
            if (now < segment.startTick) {
                body.setPos(segment.start.x, segment.start.y, segment.start.z);
                continue;
            }
            Entity targetEntity = segment.targetId == null ? null : segment.level.getEntity(segment.targetId);
            Vec3 end = targetEntity instanceof LivingEntity living && living.isAlive()
                ? living.getEyePosition() : segment.fixedEnd;
            double progress = Math.max(0.0, Math.min(1.0,
                (now - segment.startTick) / (double) Math.max(1L, segment.endTick - segment.startTick)));
            double reach = 1.0 - Math.pow(1.0 - progress, 3.0);
            double t = ((segment.segmentIndex + 1.0) / segment.segmentCount) * reach;
            Vec3 direction = end.subtract(segment.start);
            Vec3 horizontal = horizontalDirection(direction);
            Vec3 right = horizontal.cross(new Vec3(0.0, 1.0, 0.0));
            Vec3 position = segment.start.add(direction.scale(t))
                .add(right.scale(Math.sin(Math.PI * t) * segment.sideCurve))
                .add(0.0, Math.sin(Math.PI * t) * segment.lift, 0.0);
            body.setPos(position.x, position.y, position.z);
            body.setDeltaMovement(Vec3.ZERO);
        }
    }

    private static void tickWardenArmStrikes() {
        Iterator<WardenArmStrike> iterator = WARDEN_ARM_STRIKES.iterator();
        while (iterator.hasNext()) {
            WardenArmStrike strike = iterator.next();
            if (strike.level.getGameTime() < strike.impactTick) continue;
            Entity casterEntity = strike.level.getEntity(strike.casterId);
            Entity targetEntity = strike.level.getEntity(strike.targetId);
            if (casterEntity instanceof ServerPlayer caster && targetEntity instanceof LivingEntity target && target.isAlive()) {
                float damage = AncientChargeSystem.damage(switch (strike.strikeType) {
                    case 0 -> 3.0F + strike.stage;
                    case 1 -> 4.0F + strike.stage;
                    case 2 -> 5.0F + strike.stage;
                    default -> 10.0F + strike.stage * 2.0F;
                }, strike.charged);
                target.hurtServer(strike.level, strike.level.damageSources().playerAttack(caster), damage);
                Vec3 toward = horizontalDirection(caster.position().subtract(target.position()));
                switch (strike.strikeType) {
                    case 0 -> target.push(toward.x * 0.55, 0.18, toward.z * 0.55);
                    case 1 -> target.push(toward.x * 1.15, 0.28, toward.z * 1.15);
                    case 2 -> target.push(0.0, strike.charged ? 1.35 : 1.05, 0.0);
                    default -> {
                        Vec3 away = horizontalDirection(target.position().subtract(caster.position()));
                        target.push(away.x * (strike.charged ? 1.8 : 1.25), strike.charged ? 0.82 : 0.58,
                            away.z * (strike.charged ? 1.8 : 1.25));
                        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 70, 2, false, true, true));
                        ServerNetworking.sendScreenShake(strike.level, target.position(), 18.0,
                            strike.charged ? 1.25F : 0.85F, 9);
                    }
                }
                strike.level.playSound(null, target.blockPosition(), SoundEvents.WARDEN_ROAR,
                    SoundSource.PLAYERS, 1.05F, 0.72F + strike.strikeType * 0.09F);
                strike.level.sendParticles(strike.charged ? ParticleTypes.WITCH : ParticleTypes.SCULK_SOUL,
                    target.getX(), target.getY() + 0.8, target.getZ(), strike.charged ? 36 : 22,
                    0.45, 0.55, 0.45, 0.045);
            }
            iterator.remove();
        }
    }

    private static boolean useFlight(ServerPlayer player, PlayerPowerData data, int power, long now, boolean charged) {
        ServerLevel level = (ServerLevel) player.level();
        int stage = data.masteryStage(power);
        boolean formBoost = data.dragonFormUntil() > now || data.classAwakeningActive(now);
        switch (power) {
            case 1 -> {
                // Kuyruk Kasırgası: ışınlanma değil, oyuncunun çevresinde dönen fiziksel alan saldırısı.
                double radius = (formBoost ? 8.5 : 6.5) + stage * 0.55;
                for (int ring = 0; ring < 3; ring++) {
                    double ringRadius = radius * (0.55 + ring * 0.22);
                    for (int i = 0; i < 42; i++) {
                        double angle = Math.PI * 2.0 * i / 42.0 + ring * 0.45;
                        double y = player.getY() + 0.65 + Math.sin(angle * 2.0) * 0.28 + ring * 0.15;
                        level.sendParticles(ring % 2 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.WITCH,
                            player.getX() + Math.cos(angle) * ringRadius, y,
                            player.getZ() + Math.sin(angle) * ringRadius, formBoost ? 3 : 2, 0.10, 0.12, 0.10, 0.015);
                    }
                }
                for (LivingEntity target : nearbyLiving(player, radius)) {
                    if (target == player || protectedAlly(player, target)) continue;
                    target.hurtServer(level, level.damageSources().playerAttack(player),
                        AncientChargeSystem.damage(8.0F + stage * 1.8F, charged || formBoost));
                    Vec3 away = target.position().subtract(player.position());
                    if (away.lengthSqr() > 0.0001) {
                        away = away.normalize().scale(formBoost ? 2.45 : 1.85);
                        target.push(away.x, formBoost ? 0.78 : 0.58, away.z);
                    }
                }
                level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.25F, 1.45F);
                ServerNetworking.sendScreenShake(level, player.position(), 30.0, formBoost ? 1.35F : 0.95F, 12);
                data.setCooldown(1, now, Math.max(110, 190 - stage * 16));
                return true;
            }
            case 2 -> {
                // Süreli ve yönlendirilebilir nefes; oyuncu nişanını çevirdikçe saldırı da döner.
                long duration = 48L + stage * 8L + (charged || formBoost ? 28L : 0L);
                DRAGON_BREATHS.put(player.getUUID(), new DragonBreathState(now + duration, stage, charged || formBoost));
                level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.30F, 1.18F);
                player.sendSystemMessage(Component.literal("Ejderha Nefesi başladı; bakış yönünle nefesi yönlendir."));
                data.setCooldown(2, now, Math.max(260, 390 - stage * 28));
                return true;
            }
            case 3 -> {
                // Sıradan direnç yerine sınırlı sayıda tam saldırı engelleyen ve yansıtan pullar.
                int charges = 3 + stage / 2 + (charged || formBoost ? 2 : 0);
                long duration = 360L + stage * 45L + (charged || formBoost ? 160L : 0L);
                data.setDragonScaleCharges(charges);
                data.setDragonScalesUntil(now + duration);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 72, 1.05, 1.15, 1.05, 0.055);
                level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0, player.getZ(), 35, 0.8, 0.95, 0.8, 0.035);
                level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.75F, 1.65F);
                player.sendSystemMessage(Component.literal("Kadim Pullar: " + charges + " saldırı engelleme hakkı."));
                data.setCooldown(3, now, Math.max(520, 760 - stage * 55));
                return true;
            }
            case 4 -> {
                UUID existing = DRAGON_CLAW_TARGET.get(player.getUUID());
                long until = DRAGON_CLAW_UNTIL.getOrDefault(player.getUUID(), 0L);
                if (existing != null && until > now) {
                    Entity entity = level.getEntity(existing);
                    if (entity instanceof LivingEntity target && target.isAlive()) {
                        Vec3 throwDirection = player.getLookAngle().normalize();
                        target.setDeltaMovement(throwDirection.scale(formBoost ? 2.2 : 1.65).add(0.0, 0.55, 0.0));
                        target.hurtServer(level, level.damageSources().playerAttack(player), AncientChargeSystem.damage(8.0F + stage * 1.4F, charged || formBoost));
                        target.hurtMarked = true;
                        level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 0.8, target.getZ(), 42, 0.55, 0.7, 0.55, 0.055);
                    }
                    clearDragonClaw(player.getUUID());
                    data.setCooldown(4, now, Math.max(300, 430 - stage * 35));
                    return true;
                }
                LivingEntity target = findLookTarget(player, formBoost ? 24.0 : 18.0);
                if (target == null || protectedAlly(player, target)) {
                    player.sendSystemMessage(Component.literal("Avcı Pençesi için hedef bulunamadı."));
                    return false;
                }
                DRAGON_CLAW_TARGET.put(player.getUUID(), target.getUUID());
                DRAGON_CLAW_UNTIL.put(player.getUUID(), now + (formBoost ? 110L : 80L));
                DRAGON_CLAW_ESCAPE_PRESSES.put(target.getUUID(), 0);
                target.setDeltaMovement(Vec3.ZERO);
                level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 1.0, target.getZ(), 54, 0.7, 0.9, 0.7, 0.06);
                player.sendSystemMessage(Component.literal("Hedef yakalandı. Avcı Pençesini tekrar kullanarak fırlat."));
                return true;
            }
            case 5 -> {
                double radius = formBoost ? 16.0 : 12.0;
                for (LivingEntity target : nearbyLiving(player, radius)) {
                    if (target == player || protectedAlly(player, target)) continue;
                    target.hurtServer(level, level.damageSources().playerAttack(player), AncientChargeSystem.damage(6.0F + stage * 1.4F, charged || formBoost));
                    Vec3 push = target.position().subtract(player.position());
                    if (push.lengthSqr() > 0.0001) {
                        push = push.normalize().scale(formBoost ? 3.15 : 2.35);
                        target.push(push.x, formBoost ? 1.45 : 1.05, push.z);
                    }
                    if (target instanceof ServerPlayer targetPlayer) {
                        DRAGON_SILENCE_UNTIL.put(targetPlayer.getUUID(), now + (formBoost ? 100L : 70L));
                    }
                }
                for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(radius))) {
                    if (projectile.getOwner() == player) continue;
                    Vec3 away = projectile.position().subtract(player.position());
                    if (away.lengthSqr() > 0.0001) projectile.setDeltaMovement(away.normalize().scale(1.35));
                    projectile.setOwner(player);
                }
                drawRing(level, player.position(), radius, ParticleTypes.REVERSE_PORTAL, formBoost ? 110 : 78);
                level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.8F, 0.68F);
                ServerNetworking.sendScreenShake(level, player.position(), 38.0, formBoost ? 1.8F : 1.25F, 18);
                data.setCooldown(5, now, Math.max(520, 760 - stage * 55));
                return true;
            }
            case 6 -> {
                int duration = AncientChargeSystem.duration(280 + stage * 35, charged);
                data.setDragonFormUntil(now + duration);
                if (!player.isCreative() && !player.isSpectator()) {
                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();
                }
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, 2, false, true, true));
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 120, 1.35, 1.25, 1.35, 0.075);
                level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0, player.getZ(), 65, 1.1, 1.0, 1.1, 0.055);
                level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 2.0F, 0.55F);
                ServerNetworking.sendScreenShake(level, player.position(), 42.0, 1.9F, 22);
                data.setCooldown(6, now, Math.max(1450, 2050 - stage * 150));
                return true;
            }
            default -> { return false; }
        }
    }

    private static boolean useFire(ServerPlayer player, PlayerPowerData data, int power, long now, boolean charged, boolean comboStarter) {
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
                int duration = AncientChargeSystem.duration((int) (100L + stage * 10L), charged);
                double radius = AncientChargeSystem.radius(10.0 + stage * 0.5, charged);
                data.setFireRingUntil(now + duration);
                data.setChargedFireRing(charged);
                for (LivingEntity target : nearbyLiving(player, radius)) {
                    if (target == player) continue;
                    target.hurtServer(level, level.damageSources().playerAttack(player), AncientChargeSystem.damage(6.0F + stage, charged));
                    target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 80));
                }
                for (int wave = 0; wave < 3; wave++) {
                    drawRing(level, player.position().add(0.0, wave * 0.16, 0.0), radius * (0.38 + wave * 0.31),
                        charged && wave == 2 ? ParticleTypes.WITCH : ParticleTypes.FLAME, charged ? 74 : 52);
                }
                for (int i = 0; i < 4; i++) {
                    double angle = now * 0.18 + Math.PI * 0.5 * i;
                    level.sendParticles(i % 2 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME,
                        player.getX() + Math.cos(angle) * 2.2, player.getY() + 1.0,
                        player.getZ() + Math.sin(angle) * 2.2, charged ? 12 : 7, 0.22, 0.35, 0.22, 0.025);
                }
                level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.35F, 0.72F);
                ServerNetworking.sendScreenShake(level, player.position(), 24.0, charged ? 1.35F : 0.85F, 12);
                data.setCooldown(3, now, Math.max(420, 600 - stage * 50));
                return true;
            }
            case 4 -> {
                hellfireBeam(player, stage, charged, comboStarter);
                data.setCooldown(4, now, Math.max(240, 360 - stage * 40));
                return true;
            }
            case 5 -> {
                scheduleMeteors(player, data, stage, charged);
                data.setCooldown(5, now, Math.max(1800, 2400 - stage * 120));
                return true;
            }
            case 6 -> {
                infernoRay(player, stage, charged);
                data.setCooldown(6, now, Math.max(1700, 2300 - stage * 140));
                return true;
            }
            default -> { return false; }
        }
    }

    /** Ateş sınıfının altıncı gücü: kalın, uzun menzilli ve tek kullanımlık cehennem ışını. */
    private static void infernoRay(ServerPlayer player, int stage, boolean charged) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 start = player.getEyePosition().add(direction.scale(1.2));
        double range = AncientChargeSystem.radius(34.0 + stage * 3.0, charged);
        double hitRadius = AncientChargeSystem.radius(1.75 + stage * 0.18, charged);
        java.util.Set<UUID> hit = new java.util.HashSet<>();
        Vec3 impact = start.add(direction.scale(range));

        for (double distance = 0.0; distance <= range; distance += 0.55) {
            Vec3 point = start.add(direction.scale(distance));
            BlockState state = level.getBlockState(BlockPos.containing(point));
            if (!state.isAir() && !state.is(Blocks.FIRE)) {
                impact = point.subtract(direction.scale(0.35));
                break;
            }

            level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, charged ? 8 : 5,
                hitRadius * 0.24, hitRadius * 0.24, hitRadius * 0.24, 0.01);
            if (((int) (distance * 10.0)) % 11 == 0) {
                level.sendParticles(charged ? ParticleTypes.WITCH : ParticleTypes.LAVA,
                    point.x, point.y, point.z, charged ? 5 : 2, 0.18, 0.18, 0.18, 0.0);
            }

            AABB contact = new AABB(point, point).inflate(hitRadius);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, contact)) {
                if (target == player || protectedAlly(player, target) || !hit.add(target.getUUID())) continue;
                float damage = AncientChargeSystem.damage(20.0F + stage * 3.0F, charged);
                target.hurtServer(level, level.damageSources().playerAttack(player), damage);
                target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 240 + stage * 40));
                Vec3 push = direction.scale(AncientChargeSystem.knockback(1.15 + stage * 0.12, charged));
                target.push(push.x, 0.30, push.z);
            }
            impact = point;
        }

        double blastRadius = AncientChargeSystem.radius(4.2 + stage * 0.45, charged);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact).inflate(blastRadius))) {
            if (target == player || protectedAlly(player, target) || !hit.add(target.getUUID())) continue;
            target.hurtServer(level, level.damageSources().playerAttack(player),
                AncientChargeSystem.damage(12.0F + stage * 2.0F, charged));
            target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 180));
            Vec3 push = target.position().subtract(impact);
            if (push.lengthSqr() > 0.001) {
                push = push.normalize().scale(AncientChargeSystem.knockback(1.25, charged));
                target.push(push.x, 0.55, push.z);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, charged ? 8 : 5, 0.8, 0.8, 0.8, 0.0);
        level.sendParticles(charged ? ParticleTypes.WITCH : ParticleTypes.FLAME,
            impact.x, impact.y, impact.z, charged ? 160 : 100, 1.7, 1.4, 1.7, 0.10);
        drawRing(level, impact, blastRadius, charged ? ParticleTypes.WITCH : ParticleTypes.FLAME, charged ? 96 : 72);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.8F, 0.62F);
        level.playSound(null, BlockPos.containing(impact), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6F, 0.72F);
        ServerNetworking.sendScreenShake(level, impact, 46.0, charged ? 2.1F : 1.65F, charged ? 28 : 22);
    }

    private static boolean useNature(ServerPlayer player, PlayerPowerData data, int power, long now, boolean charged, boolean comboStarter) {
        ServerLevel level = (ServerLevel) player.level();
        int stage = data.masteryStage(power);
        switch (power) {
            case 1 -> {
                player.sendSystemMessage(Component.literal("Doğanın Canı, doğal zeminde ve kritik can durumunda otomatik çalışır."));
                return false;
            }
            case 2 -> {
                launchNatureSeed(player, stage, charged);
                data.setCooldown(2, now, Math.max(80, 125 - stage * 12));
                return true;
            }
            case 3 -> {
                Vec3 direction = horizontalDirection(player.getLookAngle());
                Vec3 center = findGroundPoint(level, player.position().add(direction.scale(7.0 + stage)));
                long duration = AncientChargeSystem.duration((int) (80L + stage * 15L), charged);
                PendingVineTrap trap = new PendingVineTrap(level, player.getUUID(), center, now + duration, stage, charged);
                buildVineTrapVisual(trap);
                if (comboStarter) {
                    data.beginCombo(3, now, 80, center.x, center.y, center.z, true);
                    announceComboReady(player, data);
                }
                VINE_TRAPS.add(trap);
                level.sendParticles(charged ? ParticleTypes.WITCH : ParticleTypes.HAPPY_VILLAGER, center.x, center.y + 0.5, center.z, charged ? 52 : 28, 2.1, 0.6, 2.1, 0.03);
                data.setCooldown(3, now, Math.max(240, 320 - stage * 25));
                return true;
            }
            case 4 -> {
                Vec3 center = findGroundPoint(level, player.position().add(horizontalDirection(player.getLookAngle()).scale(2.5)));
                long duration = AncientChargeSystem.duration((int) (260L + stage * 35L), charged);
                PendingLifeTree tree = new PendingLifeTree(level, player.getUUID(), center, now + duration, stage, charged);
                buildLifeTreeVisual(tree);
                LIFE_TREES.add(tree);
                data.setNatureTreeUntil(now + duration);
                data.setCooldown(4, now, Math.max(520, 700 - stage * 55));
                player.sendSystemMessage(Component.literal("Yaşam Ağacı büyüdü: " + formatSeconds((int) duration) + " saniye."));
                return true;
            }
            case 5 -> {
                Vec3 direction = horizontalDirection(player.getLookAngle());
                Vec3 start = findGroundPoint(level, player.position().add(direction.scale(2.0)));
                int steps = charged ? 38 + stage * 3 : 28 + stage * 2;
                ROOT_WAVES.add(new PendingRootWave(level, player.getUUID(), start, direction, now, steps, stage, charged));
                data.setCooldown(5, now, Math.max(700, 900 - stage * 60));
                ServerNetworking.sendScreenShake(level, player.position(), charged ? 40.0 : 28.0, charged ? 1.85F : 1.25F, charged ? 24 : 16);
                return true;
            }
            default -> { return false; }
        }
    }

    private static boolean useTime(ServerPlayer player, PlayerPowerData data, int power, long now, boolean charged, boolean comboStarter) {
        ServerLevel level = (ServerLevel) player.level();
        int stage = data.masteryStage(power);
        switch (power) {
            case 1 -> {
                data.togglePassive();
                data.setCooldown(1, now, 40);
                player.sendSystemMessage(Component.literal("Zaman Sezgisi: " + (data.passiveEnabled() ? "AÇIK" : "KAPALI")));
                return true;
            }
            case 2 -> {
                launchTimeSpear(player, stage, charged);
                data.setCooldown(2, now, Math.max(80, 140 - stage * 15));
                return true;
            }
            case 3 -> {
                java.util.ArrayDeque<TimeSnapshot> history = TIME_HISTORY.get(player.getUUID());
                if (history == null || history.isEmpty()) {
                    player.sendSystemMessage(Component.literal("Geri sarılacak yeterli zaman kaydı yok."));
                    return false;
                }
                TimeSnapshot chosen = history.peekFirst();
                long targetTick = now - (100L + stage * 10L);
                for (TimeSnapshot snapshot : history) {
                    chosen = snapshot;
                    if (snapshot.tick >= targetTick) break;
                }
                player.setPos(chosen.position.x, chosen.position.y, chosen.position.z);
                player.setYRot(chosen.yRot);
                player.setXRot(chosen.xRot);
                player.setDeltaMovement(Vec3.ZERO);
                player.hurtMarked = true;
                float rewindBonus = 2.0F + stage * 1.5F + (charged ? 4.0F : 0.0F);
                player.setHealth(Math.min(player.getMaxHealth(), Math.max(player.getHealth(), chosen.health + rewindBonus)));
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 50 + stage * 10, charged ? 2 : 1, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80 + stage * 10, charged ? 2 : 1, false, true, true));
                level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.1F, charged ? 0.72F : 1.25F);
                if (charged) AncientChargeSystem.emitChargedBurst(level, player.position().add(0.0, 1.0, 0.0), PowerClass.ANOMALY, 1.35);
                data.setCooldown(3, now, Math.max(300, 520 - stage * 55));
                return true;
            }
            case 4 -> {
                LivingEntity target = findLookTarget(player, AncientChargeSystem.radius(24.0 + stage * 3.0, charged));
                if (target == null) {
                    player.sendSystemMessage(Component.literal("Zaman Hapishanesi için nişangâhında bir hedef olmalı."));
                    return false;
                }
                int duration = AncientChargeSystem.duration(90 + stage * 20, charged);
                PendingTimePrison prison = new PendingTimePrison(level, player.getUUID(), target.getUUID(), target.position(), now + duration, stage, charged);
                buildTimePrisonVisual(prison);
                TIME_PRISONS.add(prison);
                if (comboStarter) {
                    data.beginCombo(4, now, 80, target.getX(), target.getY(), target.getZ(), true);
                    announceComboReady(player, data);
                }
                data.setCooldown(4, now, Math.max(280, 440 - stage * 45));
                return true;
            }
            case 5 -> {
                Vec3 direction = horizontalDirection(player.getLookAngle());
                Vec3 center = findGroundPoint(level, player.position().add(direction.scale(8.0 + stage)));
                createTimeField(player, center, stage, charged);
                data.setCooldown(5, now, Math.max(700, 1000 - stage * 80));
                return true;
            }
            default -> { return false; }
        }
    }

    private static void beginImmediateComboIfNeeded(ServerPlayer player, PlayerPowerData data, int power, long now) {
        if (!data.comboModeEnabled()) return;
        if (data.powerClass() == PowerClass.WARDEN && power == 2) {
            data.beginCombo(2, now, 80);
            announceComboReady(player, data);
        } else if (data.powerClass() == PowerClass.FLIGHT && power == 2) {
            data.beginCombo(2, now, 80);
            announceComboReady(player, data);
        }
        // Ateş işareti küre çarpınca, Doğa işareti kapanın gerçek merkezinde başlatılır.
    }

    private static void announceComboReady(ServerPlayer player, PlayerPowerData data) {
        player.sendSystemMessage(Component.literal(
            "KOMBO HAZIR: " + PowerCatalog.comboName(data.powerClass())
                + " • " + PowerCatalog.powerName(data.powerClass(), PowerCatalog.comboFinisherPower(data.powerClass()))
        ));
        PlayerDataStore.markDirty();
        ServerNetworking.sync(player);
    }

    private static boolean useComboFinisher(ServerPlayer player, PlayerPowerData data, int power, long now, boolean charged) {
        int stage = data.masteryStage(power);
        return switch (data.powerClass()) {
            case WARDEN -> {
                sonicFault(player, stage, charged);
                data.setCooldown(3, now, Math.max(320, 480 - stage * 35));
                player.sendSystemMessage(Component.literal("SONİK FAY!"));
                yield true;
            }
            case FIRE -> {
                Vec3 center = comboTarget(data, findGroundPoint((ServerLevel) player.level(), player.position().add(horizontalDirection(player.getLookAngle()).scale(8.0))));
                scheduleComboMeteors(player, data, stage, charged, center);
                data.setCooldown(5, now, Math.max(1900, 2500 - stage * 110));
                player.sendSystemMessage(Component.literal("CEHENNEM FELAKETİ!"));
                yield true;
            }
            case MOON -> {
                boolean used = MoonPowerSystem.use(player, data, 1, now, true);
                if (used) {
                    data.setCooldown(1, now, Math.max(180, 280 - stage * 20));
                    player.sendSystemMessage(Component.literal("TUTULMA HÜKMÜ!"));
                }
                yield used;
            }
            case FLIGHT -> {
                boolean used = useFlight(player, data, 5, now, true);
                if (used) {
                    useFlight(player, data, 2, now, true);
                    data.setCooldown(5, now, Math.max(620, 860 - stage * 45));
                    player.sendSystemMessage(Component.literal("MOR EJDERHA FIRTINASI!"));
                }
                yield used;
            }
            case MAGNETIC -> {
                boolean used = ExpansionPowerSystem.useMagnetic(player, data, 5, now, true);
                if (used) {
                    data.setCooldown(5, now, Math.max(480, 680 - stage * 40));
                    player.sendSystemMessage(Component.literal("KUTUP KIYAMETİ!"));
                }
                yield used;
            }
            case SAND -> {
                boolean used = ExpansionPowerSystem.useSand(player, data, 6, now, true);
                if (used) {
                    data.setCooldown(6, now, Math.max(900, 1200 - stage * 65));
                    player.sendSystemMessage(Component.literal("ÇÖL EZİCİSİ!"));
                }
                yield used;
            }
            case ANOMALY -> false;
            default -> false;
        };
    }

    static boolean executeCopiedPower(ServerPlayer player, PlayerPowerData data, PowerClass copiedClass, int copiedPower, long now, boolean charged) {
        if (!AnomalySystem.isCopyable(copiedClass, copiedPower)) return false;
        return switch (copiedClass) {
            case WARDEN -> copiedPower == 6
                ? AncientChargeSystem.beginCopiedBeam(player, now)
                : useWarden(player, data, copiedPower, now, charged);
            case FLIGHT -> useFlight(player, data, copiedPower, now, charged);
            case FIRE -> useFire(player, data, copiedPower, now, charged, false);
            case MOON -> MoonPowerSystem.use(player, data, copiedPower, now, charged);
            case MAGNETIC, SAND -> ExpansionPowerSystem.executeCopiedPower(player, data, copiedClass, copiedPower, now, charged);
            default -> false;
        };
    }

    static void clearBorrowedClassEffects(ServerPlayer player, PlayerPowerData data) {
        if (data.temporaryElytraUntil() != 0L) removeTemporaryElytra(player, data);
        data.setWardenHuntUntil(0L);
        data.setAwakeningUntil(0L);
        data.setFireRingUntil(0L);
        data.setNatureTreeUntil(0L);
        data.setDragonScalesUntil(0L);
        data.setDragonFormUntil(0L);
        if (!player.isCreative() && !player.isSpectator() && player.getAbilities().mayfly) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        data.setChargedWardenHunt(false);
        data.setChargedAwakening(false);
        data.setChargedFireRing(false);
        data.setVisionEnabled(false);
    }

    static LivingEntity findTargetForExternalPower(ServerPlayer player, double range) {
        return findLookTarget(player, range);
    }

    static boolean isProtectedAlly(ServerPlayer source, LivingEntity target) {
        return protectedAlly(source, target);
    }

    static void drawExternalRing(ServerLevel level, Vec3 center, double radius, net.minecraft.core.particles.ParticleOptions particle, int points) {
        drawRing(level, center, radius, particle, points);
    }

    private static Vec3 comboTarget(PlayerPowerData data, Vec3 fallback) {
        return data.comboTargetValid()
            ? new Vec3(data.comboTargetX(), data.comboTargetY(), data.comboTargetZ())
            : fallback;
    }

    private static void sonicFault(ServerPlayer player, int stage, boolean charged) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 direction = horizontalDirection(player.getLookAngle());
        Vec3 start = player.position().add(direction.scale(1.5));
        double range = AncientChargeSystem.radius(22.0 + stage * 2.0, charged);
        double hitRadius = AncientChargeSystem.radius(2.1 + stage * 0.25, charged);
        java.util.Set<UUID> hit = new java.util.HashSet<>();

        for (double distance = 0.0; distance <= range; distance += 1.15) {
            Vec3 point = findGroundPoint(level, start.add(direction.scale(distance)));
            level.sendParticles(ParticleTypes.SONIC_BOOM, point.x, point.y + 0.35, point.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(charged ? ParticleTypes.WITCH : ParticleTypes.SCULK_SOUL,
                point.x, point.y + 0.18, point.z, charged ? 9 : 5, 0.45, 0.18, 0.45, 0.025);
            AABB area = new AABB(point, point).inflate(hitRadius, 2.0, hitRadius);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
                if (target == player || protectedAlly(player, target) || !hit.add(target.getUUID())) continue;
                target.hurtServer(level, level.damageSources().playerAttack(player), AncientChargeSystem.damage(16.0F + stage * 3.0F, charged));
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 90 + stage * 15, 2, false, true, true));
                Vec3 push = direction.scale(AncientChargeSystem.knockback(1.15 + stage * 0.12, charged));
                target.push(push.x, AncientChargeSystem.knockback(0.62 + stage * 0.06, charged), push.z);
            }
        }
        Vec3 end = findGroundPoint(level, start.add(direction.scale(range)));
        drawRing(level, end.add(0.0, 0.15, 0.0), AncientChargeSystem.radius(5.0 + stage * 0.5, charged), charged ? ParticleTypes.WITCH : ParticleTypes.SCULK_SOUL, charged ? 88 : 56);
        level.playSound(null, BlockPos.containing(end), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.8F, 0.70F);
        ServerNetworking.sendScreenShake(level, end, charged ? 42.0 : 30.0, charged ? 1.9F : 1.35F, charged ? 25 : 18);
    }

    private static void launchNatureComboSeed(ServerPlayer player, int stage, boolean charged, Vec3 center) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 start = player.getEyePosition().add(player.getLookAngle().normalize().scale(1.0));
        Vec3 target = center.add(0.0, 1.1, 0.0);
        Vec3 direction = target.subtract(start);
        if (direction.lengthSqr() < 0.01) direction = player.getLookAngle();
        Vec3 velocity = direction.normalize().scale(charged ? 1.35 : 1.08);
        long travelTicks = Math.max(8L, Math.min(46L, (long) Math.ceil(direction.length() / Math.max(0.2, velocity.length())) + 5L));
        NATURE_SEEDS.add(new PendingNatureSeed(level, player.getUUID(), start, velocity,
            level.getGameTime() + travelTicks, stage, charged, true, center));
        level.sendParticles(charged ? ParticleTypes.WITCH : ParticleTypes.HAPPY_VILLAGER,
            start.x, start.y, start.z, charged ? 54 : 26, 0.35, 0.35, 0.35, 0.04);
    }

    private static void createThornForest(PendingNatureSeed seed, Vec3 center, ServerPlayer owner) {
        int radius = (seed.charged ? 6 : 5) + seed.stage / 2;
        List<PlacedBlock> blocks = new ArrayList<>();
        BlockPos baseCenter = BlockPos.containing(findGroundPoint(seed.level, center));
        for (int ray = 0; ray < 12; ray++) {
            double angle = Math.PI * 2.0 * ray / 12.0;
            for (int step = 1; step <= radius; step++) {
                int x = baseCenter.getX() + (int) Math.round(Math.cos(angle) * step);
                int z = baseCenter.getZ() + (int) Math.round(Math.sin(angle) * step);
                int y = seed.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos root = new BlockPos(x, y, z);
                BlockState rootState = seed.charged ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.MANGROVE_ROOTS.defaultBlockState();
                placeIfAir(seed.level, blocks, root, rootState);
                if (step % 2 == 0) {
                    BlockState tip = seed.charged ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Blocks.OAK_LEAVES.defaultBlockState();
                    placeIfAir(seed.level, blocks, root.above(), tip);
                    if (step >= radius - 1) placeIfAir(seed.level, blocks, root.above(2), tip);
                }
            }
        }
        for (int y = 0; y < 4 + seed.stage / 2; y++) {
            placeIfAir(seed.level, blocks, baseCenter.above(y), seed.charged ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState());
        }
        NATURE_SHAPES.add(new TemporaryNatureShape(seed.level, blocks, seed.level.getGameTime() + (seed.charged ? 100L : 75L)));

        double damageRadius = AncientChargeSystem.radius(radius + 1.5, seed.charged);
        for (LivingEntity target : seed.level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(damageRadius, 4.0, damageRadius))) {
            if (owner != null && (target == owner || protectedAlly(owner, target))) continue;
            target.hurtServer(seed.level, owner == null ? seed.level.damageSources().generic() : seed.level.damageSources().playerAttack(owner),
                AncientChargeSystem.damage(10.0F + seed.stage * 2.2F, seed.charged));
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 100 + seed.stage * 20, seed.stage >= 2 ? 1 : 0, false, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 90 + seed.stage * 18, 5, false, true, true));
            target.push(0.0, AncientChargeSystem.knockback(0.72 + seed.stage * 0.08, seed.charged), 0.0);
        }
        seed.level.sendParticles(seed.charged ? ParticleTypes.WITCH : ParticleTypes.COMPOSTER,
            center.x, center.y + 0.7, center.z, seed.charged ? 180 : 105, radius * 0.72, 1.4, radius * 0.72, 0.09);
        seed.level.playSound(null, baseCenter, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.4F, 0.72F);
        ServerNetworking.sendScreenShake(seed.level, center, seed.charged ? 34.0 : 24.0, seed.charged ? 1.45F : 1.0F, seed.charged ? 20 : 14);
    }

    private static void sonicBlast(ServerPlayer player, PlayerPowerData data, int stage, boolean charged) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 origin = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        double range = AncientChargeSystem.radius(18.0 + stage * 2.5, charged);
        List<LivingEntity> candidates = nearbyLiving(player, range);
        List<LivingEntity> lineTargets = new ArrayList<>();

        for (LivingEntity target : candidates) {
            if (target == player || protectedAlly(player, target)) continue;
            Vec3 to = target.getEyePosition().subtract(origin);
            double forward = to.dot(look);
            if (forward <= 0.0 || forward > range) continue;
            double side = to.subtract(look.scale(forward)).length();
            if (side <= AncientChargeSystem.radius(1.7 + stage * 0.3, charged)) lineTargets.add(target);
        }

        if (stage == 0 && lineTargets.size() > 2) {
            lineTargets.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
            lineTargets = new ArrayList<>(lineTargets.subList(0, 2));
        }

        for (LivingEntity target : lineTargets) {
            target.hurtServer(level, level.damageSources().playerAttack(player), AncientChargeSystem.damage(14.0F + stage * 3.0F, charged));
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80 + stage * 20, 0, false, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 70 + stage * 15, stage >= 2 ? 2 : 1, false, true, true));
            Vec3 push = target.position().subtract(player.position());
            if (push.lengthSqr() > 0.0001) {
                push = push.normalize().scale(AncientChargeSystem.knockback(1.15 + stage * 0.1, charged));
                target.push(push.x, 0.28, push.z);
            }
        }

        Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 0.001) side = new Vec3(1.0, 0.0, 0.0); else side = side.normalize();
        for (double distance = 1.0; distance <= range; distance += 0.85) {
            Vec3 point = origin.add(look.scale(distance));
            level.sendParticles(ParticleTypes.SONIC_BOOM, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
            double spread = 0.25 + distance * 0.035;
            level.sendParticles(ParticleTypes.SCULK_SOUL, point.x + side.x * spread, point.y, point.z + side.z * spread, 2, 0.07, 0.07, 0.07, 0.01);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x - side.x * spread, point.y, point.z - side.z * spread, 2, 0.07, 0.07, 0.07, 0.01);
            if (charged) level.sendParticles(ParticleTypes.WITCH, point.x, point.y, point.z, 5, 0.20, 0.20, 0.20, 0.012);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.75F, 0.88F);
        ServerNetworking.sendScreenShake(level, origin, charged ? 42.0 : 30.0, charged ? 1.65F : 1.15F, charged ? 18 : 12);
    }

    private static void hellfireBeam(ServerPlayer player, int stage, boolean charged, boolean comboPrimer) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 start = player.getEyePosition().add(direction.scale(1.15));
        Vec3 velocity = direction.scale((1.05 + stage * 0.10) * (charged ? 1.28 : 1.0));
        long now = level.getGameTime();
        HELLFIRE_ORBS.add(new PendingHellfireOrb(
            level,
            player.getUUID(),
            start,
            velocity,
            now + (charged ? 48L : 34L + stage * 4L),
            stage,
            charged,
            comboPrimer
        ));
        level.sendParticles(charged ? ParticleTypes.WITCH : ParticleTypes.FLAME, start.x, start.y, start.z, charged ? 48 : 24, 0.35, 0.35, 0.35, 0.05);
        level.sendParticles(charged ? ParticleTypes.SCULK_SOUL : ParticleTypes.LAVA, start.x, start.y, start.z, charged ? 18 : 5, 0.20, 0.20, 0.20, 0.0);
        drawHellfireMuzzle(level, start, direction, charged, now);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.3F, 0.72F);
    }

    private static void tickHellfireOrbs() {
        Iterator<PendingHellfireOrb> iterator = HELLFIRE_ORBS.iterator();
        while (iterator.hasNext()) {
            PendingHellfireOrb orb = iterator.next();
            ServerLevel level = orb.level;
            long now = level.getGameTime();
            ServerPlayer fieldOwner = AnomalySystem.findRealityOwner(level, orb.position, orb.owner);
            if (fieldOwner != null) {
                orb.realityFrozen = true;
                orb.realityOwner = fieldOwner.getUUID();
                orb.expireTick++;
                clearHellfireVisual(orb);
                placeHellfireVisual(orb, orb.position);
                level.sendParticles(ParticleTypes.WITCH, orb.position.x, orb.position.y, orb.position.z, 12, 0.45, 0.45, 0.45, 0.04);
                continue;
            }
            if (orb.realityFrozen) {
                ServerPlayer originalCaster = level.getServer().getPlayerList().getPlayer(orb.owner);
                if (originalCaster != null) {
                    Vec3 back = originalCaster.getEyePosition().subtract(orb.position);
                    if (back.lengthSqr() > 0.001) orb.velocity = back.normalize().scale(Math.max(1.15, orb.velocity.length()));
                } else {
                    orb.velocity = orb.velocity.scale(-1.0);
                }
                if (orb.realityOwner != null) orb.owner = orb.realityOwner;
                orb.expireTick = now + 45L;
                orb.realityFrozen = false;
            }
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

                double contactRadius = AncientChargeSystem.radius(0.95 + orb.stage * 0.08, orb.charged);
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
            drawHellfireHelix(level, from, to, orb.charged, now);
            level.sendParticles(orb.charged ? ParticleTypes.WITCH : ParticleTypes.FLAME, to.x, to.y, to.z, orb.charged ? 30 : 18, 0.42, 0.42, 0.42, 0.025);
            level.sendParticles(orb.charged ? ParticleTypes.SCULK_SOUL : ParticleTypes.LAVA, to.x, to.y, to.z, orb.charged ? 12 : 3, 0.24, 0.24, 0.24, 0.0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z, orb.charged ? 7 : 3, 0.20, 0.20, 0.20, 0.015);
        }
    }

    /** Cehennem Işını çıkışında namlu ağzını gösteren, bakışa dik parçacık halkaları. */
    private static void drawHellfireMuzzle(ServerLevel level, Vec3 center, Vec3 direction, boolean charged, long now) {
        Vec3 forward = direction.normalize();
        Vec3 side = forward.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 0.001) side = new Vec3(1.0, 0.0, 0.0);
        else side = side.normalize();
        Vec3 up = side.cross(forward).normalize();
        int points = charged ? 32 : 24;
        for (int ring = 0; ring < 2; ring++) {
            double radius = (charged ? 0.95 : 0.72) + ring * 0.38;
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2.0 * i / points + now * 0.16 + ring * 0.5;
                Vec3 point = center.add(side.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
                level.sendParticles((i + ring) % 4 == 0 && charged ? ParticleTypes.WITCH : ParticleTypes.FLAME,
                    point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /** Hareket eden ışının çevresindeki çift sarmallı ateş izi. */
    private static void drawHellfireHelix(ServerLevel level, Vec3 from, Vec3 to, boolean charged, long now) {
        Vec3 delta = to.subtract(from);
        if (delta.lengthSqr() < 0.0001) return;
        Vec3 forward = delta.normalize();
        Vec3 side = forward.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 0.001) side = new Vec3(1.0, 0.0, 0.0);
        else side = side.normalize();
        Vec3 up = side.cross(forward).normalize();
        int samples = charged ? 9 : 7;
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            Vec3 center = from.add(delta.scale(t));
            double angle = now * 0.48 + t * Math.PI * 4.0;
            double radius = charged ? 0.62 : 0.44;
            Vec3 offset = side.scale(Math.cos(angle) * radius).add(up.scale(Math.sin(angle) * radius));
            Vec3 a = center.add(offset);
            Vec3 b = center.subtract(offset);
            level.sendParticles(ParticleTypes.FLAME, a.x, a.y, a.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(charged ? ParticleTypes.WITCH : ParticleTypes.LAVA, b.x, b.y, b.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void placeHellfireVisual(PendingHellfireOrb orb, Vec3 position) {
        BlockPos center = BlockPos.containing(position);
        BlockPos[] shape = orb.charged
            ? new BlockPos[]{center, center.above(), center.east(), center.west(), center.north(), center.south()}
            : new BlockPos[]{center, center.above()};
        for (int i = 0; i < shape.length; i++) {
            BlockPos pos = shape[i];
            if (!orb.level.getBlockState(pos).isAir()) continue;
            BlockState visual = orb.charged && i % 2 == 0
                ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                : Blocks.MAGMA_BLOCK.defaultBlockState();
            orb.level.setBlockAndUpdate(pos, visual);
            orb.visualBlocks.add(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    private static void clearHellfireVisual(PendingHellfireOrb orb) {
        for (BlockPos pos : orb.visualBlocks) {
            if (orb.level.getBlockState(pos).is(Blocks.MAGMA_BLOCK) || orb.level.getBlockState(pos).is(Blocks.CRYING_OBSIDIAN)) {
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
            float directDamage = AncientChargeSystem.damage(13.0F + orb.stage * 2.5F, orb.charged);
            directTarget.hurtServer(level,
                owner == null ? level.damageSources().generic() : level.damageSources().playerAttack(owner),
                directDamage);
            directTarget.setRemainingFireTicks(180 + orb.stage * 35);
        }

        double blastRadius = AncientChargeSystem.radius(3.2 + orb.stage * 0.45, orb.charged);
        AABB blastArea = new AABB(impact, impact).inflate(blastRadius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, blastArea)) {
            if (target == directTarget) continue;
            if (owner != null && (target == owner || protectedAlly(owner, target))) continue;
            double distance = Math.sqrt(target.distanceToSqr(impact));
            if (distance > blastRadius) continue;
            float damage = AncientChargeSystem.damage((float) Math.max(4.0, (9.0 + orb.stage * 1.8) * (1.0 - distance / (blastRadius + 1.0))), orb.charged);
            target.hurtServer(level,
                owner == null ? level.damageSources().generic() : level.damageSources().playerAttack(owner),
                damage);
            target.setRemainingFireTicks(130 + orb.stage * 25);
            Vec3 push = target.position().subtract(impact);
            if (push.lengthSqr() > 0.0001) {
                push = push.normalize().scale(AncientChargeSystem.knockback(0.90 + orb.stage * 0.08, orb.charged));
                target.push(push.x, 0.36, push.z);
            }
        }

        level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 5, 0.55, 0.55, 0.55, 0.0);
        level.sendParticles(orb.charged ? ParticleTypes.WITCH : ParticleTypes.FLAME, impact.x, impact.y, impact.z, orb.charged ? 130 : 70, 1.25, 1.25, 1.25, 0.11);
        level.sendParticles(orb.charged ? ParticleTypes.SCULK_SOUL : ParticleTypes.LAVA, impact.x, impact.y, impact.z, orb.charged ? 50 : 15, 0.85, 0.85, 0.85, 0.0);
        drawRing(level, impact.add(0.0, 0.12, 0.0), blastRadius * 0.55, ParticleTypes.FLAME, orb.charged ? 72 : 48);
        drawRing(level, impact.add(0.0, 0.24, 0.0), blastRadius * 0.90, orb.charged ? ParticleTypes.WITCH : ParticleTypes.LAVA, orb.charged ? 92 : 60);
        drawRing(level, impact.add(0.0, 0.36, 0.0), blastRadius * 1.25, ParticleTypes.LARGE_SMOKE, orb.charged ? 104 : 72);
        for (int i = 0; i < (orb.charged ? 22 : 15); i++) {
            double y = impact.y + 0.25 + i * 0.28;
            double radius = 0.18 + i * 0.025;
            double angle = i * 1.15 + level.getGameTime() * 0.22;
            level.sendParticles(i % 3 == 0 && orb.charged ? ParticleTypes.WITCH : ParticleTypes.FLAME,
                impact.x + Math.cos(angle) * radius, y, impact.z + Math.sin(angle) * radius,
                2, 0.06, 0.08, 0.06, 0.01);
        }
        ServerNetworking.sendScreenShake(level, impact, orb.charged ? 38.0 : 28.0, orb.charged ? 1.65F : 1.15F, orb.charged ? 20 : 14);
        level.playSound(null, BlockPos.containing(impact), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.15F, 1.05F);
        if (orb.comboPrimer && owner != null) {
            PlayerPowerData data = PlayerDataStore.get(owner.getUUID());
            if (data.comboModeEnabled() && data.powerClass() == PowerClass.FIRE) {
                Vec3 ground = findGroundPoint(level, impact);
                data.beginCombo(4, level.getGameTime(), 80, ground.x, ground.y, ground.z, true);
                announceComboReady(owner, data);
            }
        }
    }

    private static void launchNatureSeed(ServerPlayer player, int stage, boolean charged) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 start = player.getEyePosition().add(direction.scale(1.2));
        NATURE_SEEDS.add(new PendingNatureSeed(
            level,
            player.getUUID(),
            start,
            direction.scale((0.85 + stage * 0.07) * (charged ? 1.25 : 1.0)),
            level.getGameTime() + (charged ? 50L : 34L + stage * 3L),
            stage,
            charged
        ));
        level.sendParticles(charged ? ParticleTypes.WITCH : ParticleTypes.HAPPY_VILLAGER, start.x, start.y, start.z, charged ? 34 : 14, 0.25, 0.25, 0.25, 0.02);
    }

    private static void tickNatureSeeds() {
        Iterator<PendingNatureSeed> iterator = NATURE_SEEDS.iterator();
        while (iterator.hasNext()) {
            PendingNatureSeed seed = iterator.next();
            clearPlaced(seed.level, seed.visualBlocks);
            long now = seed.level.getGameTime();
            ServerPlayer fieldOwner = AnomalySystem.findRealityOwner(seed.level, seed.position, seed.owner);
            if (fieldOwner != null) {
                seed.realityFrozen = true;
                seed.realityOwner = fieldOwner.getUUID();
                seed.expireTick++;
                placeNatureSeedVisual(seed, seed.position);
                seed.level.sendParticles(ParticleTypes.WITCH, seed.position.x, seed.position.y, seed.position.z, 10, 0.35, 0.35, 0.35, 0.04);
                continue;
            }
            if (seed.realityFrozen) {
                ServerPlayer originalCaster = seed.level.getServer().getPlayerList().getPlayer(seed.owner);
                if (originalCaster != null) {
                    Vec3 back = originalCaster.getEyePosition().subtract(seed.position);
                    if (back.lengthSqr() > 0.001) seed.velocity = back.normalize().scale(Math.max(1.0, seed.velocity.length()));
                } else seed.velocity = seed.velocity.scale(-1.0);
                if (seed.realityOwner != null) seed.owner = seed.realityOwner;
                seed.expireTick = now + 45L;
                seed.realityFrozen = false;
            }
            ServerPlayer owner = seed.level.getServer().getPlayerList().getPlayer(seed.owner);
            Vec3 from = seed.position;
            Vec3 to = from.add(seed.velocity);
            Vec3 impact = null;
            LivingEntity direct = null;
            int steps = Math.max(3, (int) Math.ceil(seed.velocity.length() / 0.22));
            for (int i = 1; i <= steps; i++) {
                Vec3 point = from.add(seed.velocity.scale(i / (double) steps));
                BlockState state = seed.level.getBlockState(BlockPos.containing(point));
                if (!state.isAir()) { impact = point; break; }
                AABB box = new AABB(point, point).inflate(AncientChargeSystem.radius(0.75 + seed.stage * 0.05, seed.charged));
                for (LivingEntity target : seed.level.getEntitiesOfClass(LivingEntity.class, box)) {
                    if (owner != null && (target == owner || protectedAlly(owner, target))) continue;
                    direct = target;
                    impact = target.getEyePosition();
                    break;
                }
                if (impact != null) break;
            }
            if (impact != null || now >= seed.expireTick) {
                impactNatureSeed(seed, impact == null ? to : impact, direct, owner);
                iterator.remove();
                continue;
            }
            seed.position = to;
            placeNatureSeedVisual(seed, to);
            seed.level.sendParticles(seed.charged ? ParticleTypes.WITCH : ParticleTypes.HAPPY_VILLAGER, from.x, from.y, from.z, seed.charged ? 10 : 4, 0.16, 0.16, 0.16, 0.01);
        }
    }

    private static void placeNatureSeedVisual(PendingNatureSeed seed, Vec3 position) {
        BlockPos center = BlockPos.containing(position);
        placeIfAir(seed.level, seed.visualBlocks, center, seed.charged ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState());
        BlockState shell = seed.charged ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.OAK_LEAVES.defaultBlockState();
        placeIfAir(seed.level, seed.visualBlocks, center.east(), shell);
        placeIfAir(seed.level, seed.visualBlocks, center.west(), shell);
        placeIfAir(seed.level, seed.visualBlocks, center.above(), shell);
        placeIfAir(seed.level, seed.visualBlocks, center.below(), shell);
    }

    private static void impactNatureSeed(PendingNatureSeed seed, Vec3 impact, LivingEntity direct, ServerPlayer owner) {
        if (seed.comboForest) {
            createThornForest(seed, seed.comboCenter == null ? impact : seed.comboCenter, owner);
            return;
        }
        if (direct != null) {
            direct.hurtServer(seed.level, owner == null ? seed.level.damageSources().generic() : seed.level.damageSources().playerAttack(owner), AncientChargeSystem.damage(8.0F + seed.stage * 2.0F, seed.charged));
            direct.addEffect(new MobEffectInstance(MobEffects.POISON, 70 + seed.stage * 15, seed.stage >= 2 ? 1 : 0, false, true, true));
            direct.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 55 + seed.stage * 12, 4, false, true, true));
        }
        seed.level.sendParticles(seed.charged ? ParticleTypes.WITCH : ParticleTypes.HAPPY_VILLAGER, impact.x, impact.y, impact.z, seed.charged ? 75 : 34, 0.85, 0.7, 0.85, 0.06);
        seed.level.sendParticles(ParticleTypes.COMPOSTER, impact.x, impact.y, impact.z, 26, 0.75, 0.55, 0.75, 0.08);
    }

    private static void buildVineTrapVisual(PendingVineTrap trap) {
        int radius = (trap.charged ? 5 : 3) + trap.stage / 2;
        BlockPos center = BlockPos.containing(trap.center);
        for (int i = 0; i < 18; i++) {
            double angle = Math.PI * 2.0 * i / 18.0;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);
            int y = trap.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos base = new BlockPos(x, y, z);
            placeIfAir(trap.level, trap.visualBlocks, base, trap.charged ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.MANGROVE_ROOTS.defaultBlockState());
            if (i % 3 == 0) placeIfAir(trap.level, trap.visualBlocks, base.above(), trap.charged ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Blocks.OAK_LEAVES.defaultBlockState());
        }
    }

    private static void tickVineTraps() {
        Iterator<PendingVineTrap> iterator = VINE_TRAPS.iterator();
        while (iterator.hasNext()) {
            PendingVineTrap trap = iterator.next();
            long now = trap.level.getGameTime();
            ServerPlayer owner = trap.level.getServer().getPlayerList().getPlayer(trap.owner);
            double radius = AncientChargeSystem.radius(5.5 + trap.stage * 0.55, trap.charged);
            for (LivingEntity target : trap.level.getEntitiesOfClass(LivingEntity.class, new AABB(trap.center, trap.center).inflate(radius, 2.5, radius))) {
                if (owner != null && (target == owner || protectedAlly(owner, target))) continue;
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 5, false, true, true));
                if (now % 20L == 0L) target.hurtServer(trap.level, owner == null ? trap.level.damageSources().generic() : trap.level.damageSources().playerAttack(owner), AncientChargeSystem.damage(3.2F + trap.stage * 0.9F, trap.charged));
            }
            if (now % 5L == 0L) trap.level.sendParticles(trap.charged ? ParticleTypes.WITCH : ParticleTypes.COMPOSTER, trap.center.x, trap.center.y + 0.5, trap.center.z, trap.charged ? 30 : 14, radius * 0.55, 0.6, radius * 0.55, 0.02);
            if (now >= trap.expireTick) {
                for (LivingEntity target : trap.level.getEntitiesOfClass(LivingEntity.class, new AABB(trap.center, trap.center).inflate(radius, 2.5, radius))) {
                    if (owner != null && (target == owner || protectedAlly(owner, target))) continue;
                    target.push(0.0, 0.42 + trap.stage * 0.05, 0.0);
                }
                clearPlaced(trap.level, trap.visualBlocks);
                iterator.remove();
            }
        }
    }

    private static void buildLifeTreeVisual(PendingLifeTree tree) {
        BlockPos base = BlockPos.containing(tree.center);
        for (int y = 0; y < 5 + tree.stage / 2; y++) {
            placeIfAir(tree.level, tree.visualBlocks, base.above(y), tree.charged ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.OAK_LOG.defaultBlockState());
        }
        int crownY = 4 + tree.stage / 2;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    if (dx * dx + dz * dz + dy * dy > 7) continue;
                    placeIfAir(tree.level, tree.visualBlocks, base.offset(dx, crownY + dy, dz), tree.charged ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Blocks.OAK_LEAVES.defaultBlockState());
                }
            }
        }
    }

    private static void tickLifeTrees() {
        Iterator<PendingLifeTree> iterator = LIFE_TREES.iterator();
        while (iterator.hasNext()) {
            PendingLifeTree tree = iterator.next();
            long now = tree.level.getGameTime();
            ServerPlayer owner = tree.level.getServer().getPlayerList().getPlayer(tree.owner);
            double radius = AncientChargeSystem.radius(7.0 + tree.stage * 0.5, tree.charged);
            if (now % 20L == 0L) {
                for (LivingEntity target : tree.level.getEntitiesOfClass(LivingEntity.class, new AABB(tree.center, tree.center).inflate(radius, 5.0, radius))) {
                    if (owner != null && (target == owner || protectedAlly(owner, target))) {
                        target.heal(tree.charged ? 4.0F + tree.stage * 0.8F : 2.0F + tree.stage * 0.55F);
                        target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 80, tree.charged ? 2 : 0, false, false, true));
                        target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 35, 0, false, false, true));
                    } else {
                        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 35, 1 + tree.stage / 2, false, true, true));
                    }
                }
            }
            if (now % 4L == 0L) tree.level.sendParticles(tree.charged ? ParticleTypes.WITCH : ParticleTypes.HAPPY_VILLAGER, tree.center.x, tree.center.y + 3.0, tree.center.z, tree.charged ? 28 : 10, 2.1, 2.0, 2.1, 0.02);
            if (now >= tree.expireTick) {
                clearPlaced(tree.level, tree.visualBlocks);
                if (owner != null) {
                    PlayerPowerData data = PlayerDataStore.get(owner.getUUID());
                    data.setNatureTreeUntil(0L);
        data.setDragonScalesUntil(0L);
        data.setDragonFormUntil(0L);
                    PlayerDataStore.markDirty();
                }
                iterator.remove();
            }
        }
    }

    private static void tickRootWaves() {
        Iterator<PendingRootWave> iterator = ROOT_WAVES.iterator();
        while (iterator.hasNext()) {
            PendingRootWave wave = iterator.next();
            long now = wave.level.getGameTime();
            Vec3 nextCenter = findGroundPoint(wave.level, wave.start.add(wave.direction.scale(wave.nextStep + 1.0)));
            ServerPlayer fieldOwner = AnomalySystem.findRealityOwner(wave.level, nextCenter, wave.owner);
            if (fieldOwner != null) {
                wave.realityFrozen = true;
                wave.realityOwner = fieldOwner.getUUID();
                wave.startTick++;
                wave.level.sendParticles(ParticleTypes.WITCH, nextCenter.x, nextCenter.y + 0.7, nextCenter.z, 10, 0.5, 0.5, 0.5, 0.04);
                continue;
            }
            if (wave.realityFrozen) {
                ServerPlayer originalCaster = wave.level.getServer().getPlayerList().getPlayer(wave.owner);
                Vec3 origin = nextCenter;
                if (originalCaster != null) {
                    Vec3 back = originalCaster.position().subtract(origin);
                    if (back.lengthSqr() > 0.001) wave.direction = horizontalDirection(back);
                } else wave.direction = wave.direction.scale(-1.0);
                wave.start = origin;
                wave.nextStep = 0;
                wave.hitTargets.clear();
                if (wave.realityOwner != null) wave.owner = wave.realityOwner;
                wave.startTick = now;
                wave.realityFrozen = false;
            }
            int wantedStep = (int) ((now - wave.startTick) / 2L);
            while (wave.nextStep <= wantedStep && wave.nextStep < wave.maxSteps) {
                spawnRootWaveStep(wave, wave.nextStep++);
            }
            if (wave.nextStep >= wave.maxSteps && now - wave.startTick > wave.maxSteps * 2L + 10L) iterator.remove();
        }
    }

    private static void spawnRootWaveStep(PendingRootWave wave, int step) {
        Vec3 center = findGroundPoint(wave.level, wave.start.add(wave.direction.scale(step + 1.0)));
        Vec3 right = new Vec3(-wave.direction.z, 0.0, wave.direction.x);
        int halfWidth = (wave.charged ? 7 : 5) + wave.stage / 2;
        List<PlacedBlock> blocks = new ArrayList<>();
        for (int side = -halfWidth; side <= halfWidth; side++) {
            Vec3 point = findGroundPoint(wave.level, center.add(right.scale(side)));
            BlockPos pos = BlockPos.containing(point);
            int height = 1 + ((step + side) & 1) + (wave.stage >= 2 && side % 3 == 0 ? 1 : 0);
            for (int y = 0; y < height; y++) {
                BlockState state = wave.charged
                    ? (y == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.AMETHYST_BLOCK.defaultBlockState())
                    : (y == 0 ? Blocks.MANGROVE_ROOTS.defaultBlockState() : Blocks.OAK_LOG.defaultBlockState());
                placeIfAir(wave.level, blocks, pos.above(y), state);
            }
        }
        NATURE_SHAPES.add(new TemporaryNatureShape(wave.level, blocks, wave.level.getGameTime() + 24L));
        ServerPlayer owner = wave.level.getServer().getPlayerList().getPlayer(wave.owner);
        AABB hit = new AABB(center, center).inflate(halfWidth + 0.8, 2.8, halfWidth + 0.8);
        for (LivingEntity target : wave.level.getEntitiesOfClass(LivingEntity.class, hit)) {
            if (owner != null && (target == owner || protectedAlly(owner, target))) continue;
            if (!wave.hitTargets.add(target.getUUID())) continue;
            target.hurtServer(wave.level, owner == null ? wave.level.damageSources().generic() : wave.level.damageSources().playerAttack(owner), AncientChargeSystem.damage(9.0F + wave.stage * 1.8F, wave.charged));
            Vec3 push = wave.direction.scale(AncientChargeSystem.knockback(1.05 + wave.stage * 0.1, wave.charged));
            target.push(push.x, 0.62 + wave.stage * 0.05, push.z);
        }
        wave.level.sendParticles(wave.charged ? ParticleTypes.WITCH : ParticleTypes.COMPOSTER, center.x, center.y + 0.8, center.z, wave.charged ? 48 : 24, halfWidth * 0.65, 0.8, halfWidth * 0.65, 0.08);
        if (step % 4 == 0) ServerNetworking.sendScreenShake(wave.level, center, 20.0, 0.65F, 7);
    }

    private static void tickNatureShapes() {
        Iterator<TemporaryNatureShape> iterator = NATURE_SHAPES.iterator();
        while (iterator.hasNext()) {
            TemporaryNatureShape shape = iterator.next();
            if (shape.level.getGameTime() < shape.expireTick) continue;
            clearPlaced(shape.level, shape.visualBlocks);
            iterator.remove();
        }
    }

    private static void launchFlightSpear(ServerPlayer player, int stage, boolean charged, boolean ultimate) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 start = player.getEyePosition().add(direction.scale(1.4));
        Vec3 velocity = direction.scale((ultimate ? 1.55 : 1.18) + stage * 0.08 + (charged ? 0.22 : 0.0));
        FLIGHT_SPEARS.add(new PendingFlightSpear(level, player.getUUID(), start, velocity,
            level.getGameTime(), level.getGameTime() + (ultimate ? 58L : 40L + stage * 3L), stage, charged, ultimate));
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, charged ? 0.72F : 1.25F);
    }

    private static void tickFlightSpears() {
        Iterator<PendingFlightSpear> iterator = FLIGHT_SPEARS.iterator();
        while (iterator.hasNext()) {
            PendingFlightSpear spear = iterator.next();
            long now = spear.level.getGameTime();
            if (now < spear.spawnTick) continue;
            clearPlaced(spear.level, spear.visualBlocks);
            ServerPlayer fieldOwner = AnomalySystem.findRealityOwner(spear.level, spear.position, spear.owner);
            if (fieldOwner != null) {
                spear.realityFrozen = true;
                spear.realityOwner = fieldOwner.getUUID();
                spear.expireTick++;
                placeFlightSpearVisual(spear, spear.position);
                spear.level.sendParticles(ParticleTypes.WITCH, spear.position.x, spear.position.y, spear.position.z, 12, 0.4, 0.4, 0.4, 0.04);
                continue;
            }
            if (spear.realityFrozen) {
                ServerPlayer originalCaster = spear.level.getServer().getPlayerList().getPlayer(spear.owner);
                if (originalCaster != null) {
                    Vec3 back = originalCaster.getEyePosition().subtract(spear.position);
                    if (back.lengthSqr() > 0.001) spear.velocity = back.normalize().scale(Math.max(1.25, spear.velocity.length()));
                } else spear.velocity = spear.velocity.scale(-1.0);
                if (spear.realityOwner != null) spear.owner = spear.realityOwner;
                spear.expireTick = now + 50L;
                spear.realityFrozen = false;
            }
            ServerPlayer owner = spear.level.getServer().getPlayerList().getPlayer(spear.owner);
            Vec3 from = spear.position;
            Vec3 to = from.add(spear.velocity);
            Vec3 impact = null;
            LivingEntity direct = null;
            int steps = Math.max(4, (int) Math.ceil(spear.velocity.length() / 0.18));
            for (int i = 1; i <= steps; i++) {
                Vec3 point = from.add(spear.velocity.scale(i / (double) steps));
                if (!spear.level.getBlockState(BlockPos.containing(point)).isAir()) { impact = point; break; }
                AABB box = new AABB(point, point).inflate(spear.ultimate ? 1.15 : 0.72);
                for (LivingEntity target : spear.level.getEntitiesOfClass(LivingEntity.class, box)) {
                    if (owner != null && (target == owner || protectedAlly(owner, target))) continue;
                    direct = target;
                    impact = target.getEyePosition();
                    break;
                }
                if (impact != null) break;
            }
            if (impact != null || now >= spear.expireTick) {
                impactFlightSpear(spear, impact == null ? to : impact, direct, owner);
                iterator.remove();
                continue;
            }
            spear.position = to;
            placeFlightSpearVisual(spear, to);
        }
    }

    private static void placeFlightSpearVisual(PendingFlightSpear spear, Vec3 position) {
        Vec3 direction = spear.velocity.normalize();
        BlockState core = spear.charged ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Blocks.SEA_LANTERN.defaultBlockState();
        BlockState shell = spear.charged ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        int length = spear.ultimate ? 7 : 5;
        for (int i = 0; i < length; i++) {
            Vec3 point = position.subtract(direction.scale(i * 0.72));
            placeIfAir(spear.level, spear.visualBlocks, BlockPos.containing(point), i == 0 ? core : shell);
        }
        Vec3 right = new Vec3(-direction.z, 0.0, direction.x);
        if (right.lengthSqr() < 0.001) right = new Vec3(1.0, 0.0, 0.0);
        right = right.normalize();
        placeIfAir(spear.level, spear.visualBlocks, BlockPos.containing(position.subtract(direction.scale(2.4)).add(right)), shell);
        placeIfAir(spear.level, spear.visualBlocks, BlockPos.containing(position.subtract(direction.scale(2.4)).subtract(right)), shell);
    }

    private static void impactFlightSpear(PendingFlightSpear spear, Vec3 impact, LivingEntity direct, ServerPlayer owner) {
        if (direct != null) {
            direct.hurtServer(spear.level, owner == null ? spear.level.damageSources().generic() : spear.level.damageSources().playerAttack(owner),
                AncientChargeSystem.damage((spear.ultimate ? 17.0F : 10.0F) + spear.stage * 2.2F, spear.charged));
            Vec3 push = spear.velocity.normalize().scale(AncientChargeSystem.knockback(spear.ultimate ? 2.0 : 1.25, spear.charged));
            direct.push(push.x, spear.ultimate ? 0.78 : 0.42, push.z);
        }
        double radius = AncientChargeSystem.radius((spear.ultimate ? 4.8 : 2.3) + spear.stage * 0.3, spear.charged);
        for (LivingEntity target : spear.level.getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact).inflate(radius))) {
            if (target == direct || (owner != null && (target == owner || protectedAlly(owner, target)))) continue;
            target.hurtServer(spear.level, owner == null ? spear.level.damageSources().generic() : spear.level.damageSources().playerAttack(owner),
                AncientChargeSystem.damage((spear.ultimate ? 10.0F : 5.0F) + spear.stage, spear.charged));
            Vec3 push = target.position().subtract(impact);
            if (push.lengthSqr() > 0.001) {
                push = push.normalize().scale(AncientChargeSystem.knockback(spear.ultimate ? 1.75 : 0.9, spear.charged));
                target.push(push.x, spear.ultimate ? 0.72 : 0.35, push.z);
            }
        }
        spear.level.sendParticles(spear.charged ? ParticleTypes.WITCH : ParticleTypes.CLOUD, impact.x, impact.y, impact.z,
            spear.ultimate ? 42 : 18, radius * 0.35, radius * 0.25, radius * 0.35, 0.04);
        spear.level.playSound(null, BlockPos.containing(impact), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 0.9F);
        if (spear.ultimate) ServerNetworking.sendScreenShake(spear.level, impact, 26.0, spear.charged ? 1.5F : 1.05F, 16);
    }

    private static void launchSkyBomb(ServerPlayer player, int stage, boolean charged, boolean combo, Vec3 forcedTarget) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 start = player.getEyePosition().add(player.getLookAngle().normalize().scale(1.4));
        Vec3 target = forcedTarget == null
            ? findGroundPoint(level, player.position().add(horizontalDirection(player.getLookAngle()).scale(12.0 + stage * 2.0)))
            : forcedTarget;
        Vec3 direction = target.add(0.0, 1.2, 0.0).subtract(start);
        Vec3 velocity = direction.normalize().scale(charged ? 1.25 : 1.0).add(0.0, 0.18, 0.0);
        SKY_BOMBS.add(new PendingSkyBomb(level, player.getUUID(), start, velocity, level.getGameTime(),
            level.getGameTime() + 70L, stage, charged, combo));
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, charged ? 0.72F : 1.12F);
    }

    private static void launchSkyCataclysm(ServerPlayer player, int stage, boolean charged) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 center = findGroundPoint(level, player.position().add(horizontalDirection(player.getLookAngle()).scale(12.0 + stage * 1.5)));
        int count = charged ? 12 : 6;
        player.setDeltaMovement(player.getDeltaMovement().add(0.0, charged ? 1.55 : 1.15, 0.0));
        player.hurtMarked = true;
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            double radius = i == 0 ? 0.0 : 4.0 + stage * 0.35;
            Vec3 impact = center.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            Vec3 start = impact.add(Math.cos(angle + Math.PI) * 2.5, 23.0 + (i % 3) * 2.0, Math.sin(angle + Math.PI) * 2.5);
            SKY_BOMBS.add(new PendingSkyBomb(level, player.getUUID(), start, new Vec3(0.0, -1.05 - stage * 0.05, 0.0),
                level.getGameTime() + i * 3L, level.getGameTime() + i * 3L + 55L, stage + 1, charged, true));
        }
        level.playSound(null, BlockPos.containing(center), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.9F, 1.65F);
    }

    private static void tickSkyBombs() {
        Iterator<PendingSkyBomb> iterator = SKY_BOMBS.iterator();
        while (iterator.hasNext()) {
            PendingSkyBomb bomb = iterator.next();
            long now = bomb.level.getGameTime();
            if (now < bomb.spawnTick) continue;
            clearPlaced(bomb.level, bomb.visualBlocks);
            ServerPlayer fieldOwner = AnomalySystem.findRealityOwner(bomb.level, bomb.position, bomb.owner);
            if (fieldOwner != null) {
                bomb.realityFrozen = true;
                bomb.realityOwner = fieldOwner.getUUID();
                bomb.expireTick++;
                placeSkyBombVisual(bomb, bomb.position);
                bomb.level.sendParticles(ParticleTypes.WITCH, bomb.position.x, bomb.position.y, bomb.position.z, 14, 0.5, 0.5, 0.5, 0.04);
                continue;
            }
            if (bomb.realityFrozen) {
                ServerPlayer originalCaster = bomb.level.getServer().getPlayerList().getPlayer(bomb.owner);
                if (originalCaster != null) {
                    Vec3 back = originalCaster.getEyePosition().subtract(bomb.position);
                    if (back.lengthSqr() > 0.001) bomb.velocity = back.normalize().scale(Math.max(1.15, bomb.velocity.length()));
                } else bomb.velocity = bomb.velocity.scale(-1.0);
                if (bomb.realityOwner != null) bomb.owner = bomb.realityOwner;
                bomb.expireTick = now + 55L;
                bomb.realityFrozen = false;
            }
            ServerPlayer owner = bomb.level.getServer().getPlayerList().getPlayer(bomb.owner);
            Vec3 from = bomb.position;
            bomb.velocity = bomb.velocity.add(0.0, bomb.ultimate ? -0.025 : -0.055, 0.0);
            Vec3 to = from.add(bomb.velocity);
            Vec3 impact = null;
            LivingEntity direct = null;
            int steps = Math.max(4, (int) Math.ceil(bomb.velocity.length() / 0.20));
            for (int i = 1; i <= steps; i++) {
                Vec3 point = from.add(bomb.velocity.scale(i / (double) steps));
                if (!bomb.level.getBlockState(BlockPos.containing(point)).isAir()) { impact = point; break; }
                AABB box = new AABB(point, point).inflate(bomb.ultimate ? 1.35 : 0.95);
                for (LivingEntity target : bomb.level.getEntitiesOfClass(LivingEntity.class, box)) {
                    if (owner != null && (target == owner || protectedAlly(owner, target))) continue;
                    direct = target; impact = target.position().add(0.0, 0.8, 0.0); break;
                }
                if (impact != null) break;
            }
            if (impact != null || now >= bomb.expireTick) {
                impactSkyBomb(bomb, impact == null ? to : impact, direct, owner);
                iterator.remove();
                continue;
            }
            bomb.position = to;
            placeSkyBombVisual(bomb, to);
        }
    }

    private static void placeSkyBombVisual(PendingSkyBomb bomb, Vec3 position) {
        BlockPos center = BlockPos.containing(position);
        BlockState core = bomb.charged ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Blocks.SEA_LANTERN.defaultBlockState();
        BlockState shell = bomb.charged ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.WHITE_STAINED_GLASS.defaultBlockState();
        placeIfAir(bomb.level, bomb.visualBlocks, center, core);
        for (BlockPos pos : new BlockPos[]{center.east(), center.west(), center.north(), center.south(), center.above(), center.below()}) {
            placeIfAir(bomb.level, bomb.visualBlocks, pos, shell);
        }
        if (bomb.ultimate || bomb.charged) {
            placeIfAir(bomb.level, bomb.visualBlocks, center.east().above(), shell);
            placeIfAir(bomb.level, bomb.visualBlocks, center.west().above(), shell);
            placeIfAir(bomb.level, bomb.visualBlocks, center.north().below(), shell);
            placeIfAir(bomb.level, bomb.visualBlocks, center.south().below(), shell);
        }
    }

    private static void impactSkyBomb(PendingSkyBomb bomb, Vec3 impact, LivingEntity direct, ServerPlayer owner) {
        double radius = AncientChargeSystem.radius((bomb.ultimate ? 6.5 : 4.2) + bomb.stage * 0.45, bomb.charged);
        for (LivingEntity target : bomb.level.getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact).inflate(radius))) {
            if (owner != null && (target == owner || protectedAlly(owner, target))) continue;
            double distance = Math.sqrt(target.distanceToSqr(impact));
            float damage = AncientChargeSystem.damage((float) Math.max(5.0, (bomb.ultimate ? 18.0 : 11.0) + bomb.stage * 2.0 - distance), bomb.charged);
            target.hurtServer(bomb.level, owner == null ? bomb.level.damageSources().generic() : bomb.level.damageSources().playerAttack(owner), damage);
            Vec3 push = target.position().subtract(impact);
            if (push.lengthSqr() > 0.001) {
                push = push.normalize().scale(AncientChargeSystem.knockback(bomb.ultimate ? 2.1 : 1.35, bomb.charged));
                target.push(push.x, bomb.ultimate ? 1.0 : 0.62, push.z);
            }
        }
        bomb.level.sendParticles(bomb.charged ? ParticleTypes.WITCH : ParticleTypes.CLOUD, impact.x, impact.y, impact.z,
            bomb.ultimate ? 90 : 46, radius * 0.52, radius * 0.35, radius * 0.52, 0.08);
        bomb.level.playSound(null, BlockPos.containing(impact), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5F, 0.72F);
        ServerNetworking.sendScreenShake(bomb.level, impact, bomb.ultimate ? 36.0 : 24.0, bomb.charged ? 1.8F : 1.2F, bomb.ultimate ? 22 : 15);
    }

    private static void launchTimeSpear(ServerPlayer player, int stage, boolean charged) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 start = player.getEyePosition().add(direction.scale(1.35));
        TIME_SPEARS.add(new PendingTimeSpear(level, player.getUUID(), start,
            direction.scale(1.28 + stage * 0.10 + (charged ? 0.30 : 0.0)), level.getGameTime() + 60L, stage, charged));
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, charged ? 0.58F : 1.45F);
    }

    private static void tickTimeSpears() {
        Iterator<PendingTimeSpear> iterator = TIME_SPEARS.iterator();
        while (iterator.hasNext()) {
            PendingTimeSpear spear = iterator.next();
            clearPlaced(spear.level, spear.visualBlocks);
            long now = spear.level.getGameTime();
            ServerPlayer owner = spear.level.getServer().getPlayerList().getPlayer(spear.owner);
            Vec3 from = spear.position;
            Vec3 to = from.add(spear.velocity);
            Vec3 impact = null;
            LivingEntity direct = null;
            int steps = Math.max(4, (int) Math.ceil(spear.velocity.length() / 0.18));
            for (int i = 1; i <= steps; i++) {
                Vec3 point = from.add(spear.velocity.scale(i / (double) steps));
                if (!spear.level.getBlockState(BlockPos.containing(point)).isAir()) { impact = point; break; }
                for (LivingEntity target : spear.level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(0.78))) {
                    if (owner != null && (target == owner || protectedAlly(owner, target))) continue;
                    direct = target; impact = target.getEyePosition(); break;
                }
                if (impact != null) break;
            }
            if (impact != null || now >= spear.expireTick) {
                Vec3 end = impact == null ? to : impact;
                double burstRadius = AncientChargeSystem.radius(2.4 + spear.stage * 0.30, spear.charged);
                for (LivingEntity target : spear.level.getEntitiesOfClass(LivingEntity.class, new AABB(end, end).inflate(burstRadius))) {
                    if (owner != null && (target == owner || protectedAlly(owner, target))) continue;
                    boolean directHit = target == direct;
                    float baseDamage = directHit ? 13.0F + spear.stage * 2.5F : 6.0F + spear.stage * 1.5F;
                    target.hurtServer(spear.level, owner == null ? spear.level.damageSources().generic() : spear.level.damageSources().playerAttack(owner),
                        AncientChargeSystem.damage(baseDamage, spear.charged));
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, AncientChargeSystem.duration(120 + spear.stage * 22, spear.charged), spear.charged ? 4 : 2, false, false, false));
                }
                spear.level.sendParticles(spear.charged ? ParticleTypes.WITCH : ParticleTypes.ENCHANT, end.x, end.y, end.z, spear.charged ? 48 : 32, burstRadius * 0.45, 0.7, burstRadius * 0.45, 0.05);
                iterator.remove();
                continue;
            }
            spear.position = to;
            placeTimeSpearVisual(spear, to);
        }
    }

    private static void placeTimeSpearVisual(PendingTimeSpear spear, Vec3 position) {
        Vec3 direction = spear.velocity.normalize();
        BlockState core = spear.charged ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Blocks.GOLD_BLOCK.defaultBlockState();
        BlockState body = spear.charged ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.BLUE_STAINED_GLASS.defaultBlockState();
        for (int i = 0; i < 6; i++) {
            placeIfAir(spear.level, spear.visualBlocks, BlockPos.containing(position.subtract(direction.scale(i * 0.68))), i == 0 ? core : body);
        }
    }

    private static LivingEntity findLookTarget(ServerPlayer player, double range) {
        Vec3 origin = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        LivingEntity best = null;
        double bestDistance = range + 1.0;
        for (LivingEntity target : nearbyLiving(player, range)) {
            if (target == player || protectedAlly(player, target)) continue;
            Vec3 to = target.getEyePosition().subtract(origin);
            double forward = to.dot(look);
            if (forward <= 0.0 || forward > range) continue;
            double side = to.subtract(look.scale(forward)).length();
            if (side > 1.4 + target.getBbWidth() * 0.5) continue;
            if (!player.hasLineOfSight(target)) continue;
            if (forward < bestDistance) { bestDistance = forward; best = target; }
        }
        return best;
    }

    private static void buildTimePrisonVisual(PendingTimePrison prison) {
        BlockPos center = BlockPos.containing(prison.anchor);
        int radius = prison.charged ? 3 : 2;
        for (int ringY = 0; ringY <= 3; ringY += 3) {
            for (int i = 0; i < 24; i++) {
                double angle = Math.PI * 2.0 * i / 24.0;
                BlockPos pos = center.offset((int) Math.round(Math.cos(angle) * radius), ringY + 1, (int) Math.round(Math.sin(angle) * radius));
                BlockState state = prison.charged && i % 3 == 0 ? Blocks.AMETHYST_BLOCK.defaultBlockState()
                    : (i % 2 == 0 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
                placeIfAir(prison.level, prison.visualBlocks, pos, state);
            }
        }
        placeIfAir(prison.level, prison.visualBlocks, center.above(2).east(radius), Blocks.SEA_LANTERN.defaultBlockState());
        placeIfAir(prison.level, prison.visualBlocks, center.above(2).west(radius), Blocks.SEA_LANTERN.defaultBlockState());
    }

    private static void tickTimePrisons() {
        Iterator<PendingTimePrison> iterator = TIME_PRISONS.iterator();
        while (iterator.hasNext()) {
            PendingTimePrison prison = iterator.next();
            long now = prison.level.getGameTime();
            Entity entity = prison.level.getEntity(prison.target);
            if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
                if (entity instanceof LivingEntity living) living.setNoGravity(prison.previousNoGravity);
                clearPlaced(prison.level, prison.visualBlocks);
                iterator.remove();
                continue;
            }
            if (!prison.initialized) {
                prison.previousNoGravity = target.isNoGravity();
                prison.initialized = true;
            }
            if (now >= prison.expireTick) {
                target.setNoGravity(prison.previousNoGravity);
                ServerPlayer owner = prison.level.getServer().getPlayerList().getPlayer(prison.owner);
                float releaseDamage = AncientChargeSystem.damage(8.0F + prison.stage * 2.0F, prison.charged);
                target.hurtServer(prison.level, owner == null ? prison.level.damageSources().generic() : prison.level.damageSources().playerAttack(owner), releaseDamage);
                Vec3 push = target.position().subtract(prison.anchor);
                if (push.lengthSqr() < 0.001) push = new Vec3(0.0, 0.0, 1.0);
                push = push.normalize().scale(AncientChargeSystem.knockback(0.9 + prison.stage * 0.12, prison.charged));
                target.push(push.x, 0.45, push.z);
                prison.level.sendParticles(prison.charged ? ParticleTypes.WITCH : ParticleTypes.ENCHANT, target.getX(), target.getY() + 1.0, target.getZ(), prison.charged ? 55 : 34, 1.2, 1.0, 1.2, 0.06);
                clearPlaced(prison.level, prison.visualBlocks);
                iterator.remove();
                continue;
            }
            target.setNoGravity(true);
            target.setDeltaMovement(Vec3.ZERO);
            target.setPos(prison.anchor.x, prison.anchor.y, prison.anchor.z);
            target.fallDistance = 0.0F;
            if (now % 10L == 0L) prison.level.playSound(null, target.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.35F, 0.75F);
        }
    }

    private static void createTimeField(ServerPlayer owner, Vec3 center, int stage, boolean charged) {
        ServerLevel level = (ServerLevel) owner.level();
        long now = level.getGameTime();
        int duration = AncientChargeSystem.duration(110 + stage * 15, charged);
        PendingTimeField field = new PendingTimeField(level, owner.getUUID(), center, now + duration, stage, charged);
        buildTimeFieldVisual(field);
        TIME_FIELDS.add(field);
        level.playSound(null, BlockPos.containing(center), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, charged ? 1.45F : 0.75F);
        ServerNetworking.sendScreenShake(level, center, charged ? 42.0 : 30.0, charged ? 1.65F : 1.1F, 18);
    }

    private static void buildTimeFieldVisual(PendingTimeField field) {
        BlockPos center = BlockPos.containing(field.center);
        int radius = 9 + field.stage / 2 + (field.charged ? 2 : 0);
        for (int ring : new int[]{radius, Math.max(3, radius - 3)}) {
            int points = ring * 10;
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2.0 * i / points;
                BlockPos pos = center.offset((int) Math.round(Math.cos(angle) * ring), 1, (int) Math.round(Math.sin(angle) * ring));
                BlockState state = field.charged && i % 4 == 0 ? Blocks.AMETHYST_BLOCK.defaultBlockState()
                    : (i % 3 == 0 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.BLUE_STAINED_GLASS.defaultBlockState());
                placeIfAir(field.level, field.visualBlocks, pos, state);
            }
        }
        for (int i = -radius + 1; i < radius; i++) {
            placeIfAir(field.level, field.visualBlocks, center.offset(i, 1, 0), i % 3 == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.GOLD_BLOCK.defaultBlockState());
            if (i % 2 == 0) placeIfAir(field.level, field.visualBlocks, center.offset(0, 1, i), Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
        }
    }

    private static void tickTimeFields() {
        Iterator<PendingTimeField> iterator = TIME_FIELDS.iterator();
        while (iterator.hasNext()) {
            PendingTimeField field = iterator.next();
            long now = field.level.getGameTime();
            ServerPlayer owner = field.level.getServer().getPlayerList().getPlayer(field.owner);
            double radius = AncientChargeSystem.radius(12.0 + field.stage * 1.1, field.charged);
            for (LivingEntity target : field.level.getEntitiesOfClass(LivingEntity.class, new AABB(field.center, field.center).inflate(radius, 5.0, radius))) {
                if (owner != null && (target == owner || protectedAlly(owner, target))) continue;
                Vec3 anchor = field.anchors.computeIfAbsent(target.getUUID(), ignored -> target.position());
                field.previousNoGravity.putIfAbsent(target.getUUID(), target.isNoGravity());
                target.setNoGravity(true);
                target.setDeltaMovement(Vec3.ZERO);
                target.setPos(anchor.x, anchor.y, anchor.z);
                target.fallDistance = 0.0F;
                if (now % 20L == 0L) {
                    float tickDamage = AncientChargeSystem.damage(2.0F + field.stage * 0.6F, field.charged);
                    target.hurtServer(field.level, owner == null ? field.level.damageSources().generic() : field.level.damageSources().playerAttack(owner), tickDamage);
                }
            }
            if (now < field.expireTick) continue;
            float baseDamage = AncientChargeSystem.damage(24.0F + field.stage * 4.5F, field.charged);
            for (Map.Entry<UUID, Vec3> entry : field.anchors.entrySet()) {
                Entity entity = field.level.getEntity(entry.getKey());
                if (!(entity instanceof LivingEntity target)) continue;
                target.setNoGravity(field.previousNoGravity.getOrDefault(entry.getKey(), false));
                target.hurtServer(field.level, owner == null ? field.level.damageSources().generic() : field.level.damageSources().playerAttack(owner), baseDamage);
                Vec3 push = target.position().subtract(field.center);
                if (push.lengthSqr() > 0.001) {
                    push = push.normalize().scale(AncientChargeSystem.knockback(1.8 + field.stage * 0.15, field.charged));
                    target.push(push.x, 0.82, push.z);
                }
            }
            clearPlaced(field.level, field.visualBlocks);
            field.level.sendParticles(field.charged ? ParticleTypes.WITCH : ParticleTypes.ENCHANT, field.center.x, field.center.y + 1.0, field.center.z, 75, radius * 0.45, 1.4, radius * 0.45, 0.08);
            field.level.playSound(null, BlockPos.containing(field.center), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5F, 1.35F);
            iterator.remove();
        }
    }

    private static void tickTimeShapes() {
        Iterator<TemporaryTimeShape> iterator = TIME_SHAPES.iterator();
        while (iterator.hasNext()) {
            TemporaryTimeShape shape = iterator.next();
            if (shape.level.getGameTime() < shape.expireTick) continue;
            clearPlaced(shape.level, shape.visualBlocks);
            iterator.remove();
        }
    }

    private static Vec3 findGroundPoint(ServerLevel level, Vec3 point) {
        int x = (int) Math.floor(point.x);
        int z = (int) Math.floor(point.z);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new Vec3(x + 0.5, y, z + 0.5);
    }

    private static Vec3 horizontalDirection(Vec3 direction) {
        Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z);
        return horizontal.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : horizontal.normalize();
    }

    private static void placeIfAir(ServerLevel level, List<PlacedBlock> list, BlockPos pos, BlockState state) {
        if (!level.getBlockState(pos).isAir()) return;
        level.setBlockAndUpdate(pos, state);
        list.add(new PlacedBlock(new BlockPos(pos.getX(), pos.getY(), pos.getZ()), state));
    }

    private static void clearPlaced(ServerLevel level, List<PlacedBlock> list) {
        for (PlacedBlock placed : list) {
            if (level.getBlockState(placed.pos).is(placed.state.getBlock())) level.setBlockAndUpdate(placed.pos, Blocks.AIR.defaultBlockState());
        }
        list.clear();
    }

    private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-7) return point.distanceToSqr(start);
        double projection = point.subtract(start).dot(segment) / lengthSqr;
        projection = Math.max(0.0, Math.min(1.0, projection));
        return point.distanceToSqr(start.add(segment.scale(projection)));
    }

    private static void airBlast(ServerPlayer player, int stage, boolean charged) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 origin = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        double range = AncientChargeSystem.radius(10.0 + stage * 2.0, charged);
        double minDot = 0.76 - stage * 0.04;

        AABB box = player.getBoundingBox().inflate(range);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (target == player || protectedAlly(player, target)) continue;
            Vec3 to = target.getEyePosition().subtract(origin);
            if (to.lengthSqr() > range * range) continue;
            Vec3 direction = to.normalize();
            if (direction.dot(look) < minDot) continue;
            target.hurtServer(level, level.damageSources().playerAttack(player), AncientChargeSystem.damage(8.0F + stage * 1.5F, charged));
            Vec3 push = look.scale(AncientChargeSystem.knockback(1.3 + stage * 0.18, charged));
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
            level.sendParticles(charged ? ParticleTypes.WITCH : ParticleTypes.CLOUD, point.x, point.y, point.z, charged ? 8 : 4, spread, spread, spread, 0.03);
        }
    }

    private static void scheduleComboMeteors(ServerPlayer player, PlayerPowerData data, int stage, boolean charged, Vec3 center) {
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        RandomSource random = level.getRandom();
        int count = charged ? 20 : 10;
        int outerCount = Math.max(1, count - 1);

        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 6, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 4, false, true, true));
        level.playSound(null, BlockPos.containing(center), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.1F, 1.22F);
        drawRing(level, center.add(0.0, 0.15, 0.0), charged ? 7.5 : 6.0, charged ? ParticleTypes.WITCH : ParticleTypes.FLAME, charged ? 92 : 64);

        for (int i = 0; i < count; i++) {
            double radius = i == 0 ? 0.0 : (charged ? 7.5 : 6.0);
            double angle = i == 0 ? 0.0 : Math.PI * 2.0 * (i - 1) / outerCount;
            int x = (int) Math.floor(center.x + Math.cos(angle) * radius);
            int z = (int) Math.floor(center.z + Math.sin(angle) * radius);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            Vec3 impact = new Vec3(x + 0.5, y, z + 0.5);

            double approachAngle = angle + Math.PI + (random.nextDouble() - 0.5) * 0.35;
            double horizontalOffset = 10.0 + random.nextDouble() * 5.0;
            Vec3 startPosition = new Vec3(
                impact.x + Math.cos(approachAngle) * horizontalOffset,
                impact.y + 40.0 + random.nextInt(9),
                impact.z + Math.sin(approachAngle) * horizontalOffset
            );

            long spawnTick = now + i * (charged ? 3L : 4L);
            long impactTick = spawnTick + 52L + random.nextInt(8);
            int craterRadius = (i == 0 ? 6 : 5) + stage / 2 + (charged ? 3 : 0);
            float damage = charged
                ? ((i == 0 ? 62.0F : 56.0F) + stage * 5.0F)
                : ((i == 0 ? 28.0F : 23.0F) + stage * 3.5F);
            METEORS.add(new PendingMeteor(level, player.getUUID(), startPosition, impact, spawnTick, impactTick, craterRadius, damage, charged));
        }
        ServerNetworking.sendScreenShake(level, center, charged ? 52.0 : 38.0, charged ? 2.1F : 1.55F, charged ? 30 : 22);
    }

    private static void scheduleMeteors(ServerPlayer player, PlayerPowerData data, int stage, boolean charged) {
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        Vec3 center = player.position();
        RandomSource random = level.getRandom();
        int count = charged ? 20 : 10;

        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 36, 6, false, true, true));
        player.sendSystemMessage(Component.literal(charged
            ? "ANTİK METEOR YAĞMURU: 20 büyük meteor çağrıldı."
            : "Meteor Yağmuru: 10 meteor çağrıldı."));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 36, 4, false, true, true));
        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.9F, 1.35F);

        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 3.0 + Math.sqrt(random.nextDouble()) * (charged ? 13.0 + stage : 9.0 + stage);
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

            long spawnTick = now + i * (charged ? 3L : 5L);
            long impactTick = spawnTick + 50L + random.nextInt(9);
            int craterRadius = 5 + stage / 2 + (charged ? 3 : 0);
            float damage = charged ? (56.0F + stage * 5.0F) : (23.0F + stage * 3.5F);
            METEORS.add(new PendingMeteor(level, player.getUUID(), startPosition, impact, spawnTick, impactTick, craterRadius, damage, charged));
        }
    }

    /** Meteor Düşüşü büyüsü için normal Meteor Yağmuru ile aynı gerçek blok gövdesini kullanan tek meteor. */
    public static void scheduleEnchantmentMeteor(ServerPlayer player, Vec3 requestedImpact) {
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        RandomSource random = level.getRandom();
        int x = (int) Math.floor(requestedImpact.x);
        int z = (int) Math.floor(requestedImpact.z);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        Vec3 impact = new Vec3(x + 0.5, y, z + 0.5);
        double angle = random.nextDouble() * Math.PI * 2.0;
        double horizontalOffset = 8.0 + random.nextDouble() * 4.0;
        Vec3 start = new Vec3(
            impact.x + Math.cos(angle) * horizontalOffset,
            impact.y + 34.0 + random.nextInt(7),
            impact.z + Math.sin(angle) * horizontalOffset
        );
        METEORS.add(new PendingMeteor(level, player.getUUID(), start, impact, now, now + 46L, 4, 20.0F, false));
        level.playSound(null, BlockPos.containing(impact), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.55F, 1.55F);
    }

    private static void tickMeteors() {
        Iterator<PendingMeteor> iterator = METEORS.iterator();
        while (iterator.hasNext()) {
            PendingMeteor meteor = iterator.next();
            ServerLevel level = meteor.level;
            long now = level.getGameTime();

            if (now < meteor.spawnTick) continue;

            long remaining = meteor.impactTick - now;
            if (remaining > 0L) {
                double duration = Math.max(1.0, meteor.impactTick - meteor.spawnTick);
                double progress = Math.max(0.0, Math.min(1.0, (now - meteor.spawnTick) / duration));
                double eased = progress * progress;
                Vec3 target = meteor.impact.add(0.0, 1.0, 0.0);
                Vec3 position = meteor.realityFrozen && meteor.frozenPosition != null ? meteor.frozenPosition : new Vec3(
                    meteor.start.x + (target.x - meteor.start.x) * eased,
                    meteor.start.y + (target.y - meteor.start.y) * eased,
                    meteor.start.z + (target.z - meteor.start.z) * eased
                );

                ServerPlayer realityOwner = AnomalySystem.findRealityOwner(level, position, meteor.owner);
                if (realityOwner != null) {
                    meteor.realityFrozen = true;
                    meteor.realityOwner = realityOwner.getUUID();
                    meteor.frozenPosition = position;
                    meteor.spawnTick++;
                    meteor.impactTick++;
                    placeMeteorVisual(meteor, position);
                    level.sendParticles(ParticleTypes.WITCH, position.x, position.y, position.z, 14, 0.7, 0.7, 0.7, 0.05);
                    continue;
                }
                if (meteor.realityFrozen && meteor.frozenPosition != null) {
                    ServerPlayer originalCaster = level.getServer().getPlayerList().getPlayer(meteor.owner);
                    meteor.start = meteor.frozenPosition;
                    meteor.impact = originalCaster == null ? meteor.frozenPosition.add(0.0, -6.0, 0.0) : originalCaster.position();
                    meteor.owner = meteor.realityOwner == null ? meteor.owner : meteor.realityOwner;
                    meteor.spawnTick = now;
                    meteor.impactTick = now + 28L;
                    meteor.realityFrozen = false;
                    meteor.frozenPosition = null;
                    clearMeteorVisual(meteor);
                    continue;
                }

                placeMeteorVisual(meteor, position);
                level.sendParticles(meteor.charged ? ParticleTypes.WITCH : ParticleTypes.FLAME, position.x, position.y, position.z, meteor.charged ? 9 : 6, 0.55, 0.55, 0.55, 0.035);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, position.x, position.y + 0.4, position.z, meteor.charged ? 6 : 4, 0.7, 0.7, 0.7, 0.025);
                level.sendParticles(meteor.charged ? ParticleTypes.SCULK_SOUL : ParticleTypes.LAVA, position.x, position.y, position.z, meteor.charged ? 4 : 2, 0.35, 0.35, 0.35, 0.0);

                if (now % 4L == 0L) {
                    drawRing(level, meteor.impact.add(0.0, 0.15, 0.0), 1.8 + meteor.radius * 0.18, meteor.charged ? ParticleTypes.WITCH : ParticleTypes.FLAME, meteor.charged ? 30 : 20);
                }
                continue;
            }

            impactMeteor(meteor);
            iterator.remove();
        }
    }

    private static void placeMeteorVisual(PendingMeteor meteor, Vec3 position) {
        BlockPos center = BlockPos.containing(position);
        if (center.equals(meteor.visualCenter)) return;

        // Aynı blok konumunda tekrar silip yerleştirmemek hem titreşimi hem de gereksiz blok güncellemelerini azaltır.
        clearMeteorVisual(meteor);
        List<BlockPos> shape = new ArrayList<>();
        if (meteor.charged) {
            // 3x3x3 hacim içindeki köşeler çıkarılır: 19 blokluk yuvarlağa yakın büyük meteor.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx * dx + dy * dy + dz * dz > 2) continue;
                        shape.add(center.offset(dx, dy, dz));
                    }
                }
            }
        } else {
            shape.add(center);
            shape.add(center.east()); shape.add(center.west());
            shape.add(center.north()); shape.add(center.south());
            shape.add(center.above()); shape.add(center.below());
        }
        for (int i = 0; i < shape.size(); i++) {
            BlockPos pos = shape.get(i);
            if (!meteor.level.getBlockState(pos).isAir()) continue;
            BlockState visual = meteor.charged && (i == 0 || i % 4 == 0)
                ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                : Blocks.MAGMA_BLOCK.defaultBlockState();
            meteor.level.setBlockAndUpdate(pos, visual);
            meteor.visualBlocks.add(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
        }
        meteor.visualCenter = new BlockPos(center.getX(), center.getY(), center.getZ());
    }

    private static void clearMeteorVisual(PendingMeteor meteor) {
        for (BlockPos pos : meteor.visualBlocks) {
            if (meteor.level.getBlockState(pos).is(Blocks.MAGMA_BLOCK) || meteor.level.getBlockState(pos).is(Blocks.CRYING_OBSIDIAN)) {
                meteor.level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
        meteor.visualBlocks.clear();
        meteor.visualCenter = null;
    }

    /** Güç çarpışmasında kaybeden oyuncunun havada/zeminde devam eden saldırılarını güvenle temizler. */
    public static void cancelActiveOffense(UUID ownerId) {
        ExpansionPowerSystem.cancelOwner(ownerId);
        Iterator<PendingMeteor> meteorIterator = METEORS.iterator();
        while (meteorIterator.hasNext()) {
            PendingMeteor meteor = meteorIterator.next();
            if (!meteor.owner.equals(ownerId)) continue;
            clearMeteorVisual(meteor);
            meteorIterator.remove();
        }
        Iterator<PendingHellfireOrb> hellIterator = HELLFIRE_ORBS.iterator();
        while (hellIterator.hasNext()) {
            PendingHellfireOrb orb = hellIterator.next();
            if (!orb.owner.equals(ownerId)) continue;
            clearHellfireVisual(orb);
            hellIterator.remove();
        }
        Iterator<PendingNatureSeed> seedIterator = NATURE_SEEDS.iterator();
        while (seedIterator.hasNext()) {
            PendingNatureSeed seed = seedIterator.next();
            if (!seed.owner.equals(ownerId)) continue;
            clearPlaced(seed.level, seed.visualBlocks);
            seedIterator.remove();
        }
        Iterator<PendingVineTrap> trapIterator = VINE_TRAPS.iterator();
        while (trapIterator.hasNext()) {
            PendingVineTrap trap = trapIterator.next();
            if (!trap.owner.equals(ownerId)) continue;
            clearPlaced(trap.level, trap.visualBlocks);
            trapIterator.remove();
        }
        ROOT_WAVES.removeIf(wave -> wave.owner.equals(ownerId));
        Iterator<PendingFlightSpear> spearIterator = FLIGHT_SPEARS.iterator();
        while (spearIterator.hasNext()) {
            PendingFlightSpear spear = spearIterator.next();
            if (!spear.owner.equals(ownerId)) continue;
            clearPlaced(spear.level, spear.visualBlocks);
            spearIterator.remove();
        }
        Iterator<PendingSkyBomb> bombIterator = SKY_BOMBS.iterator();
        while (bombIterator.hasNext()) {
            PendingSkyBomb bomb = bombIterator.next();
            if (!bomb.owner.equals(ownerId)) continue;
            clearPlaced(bomb.level, bomb.visualBlocks);
            bombIterator.remove();
        }
        Iterator<PendingTimeSpear> timeSpearIterator = TIME_SPEARS.iterator();
        while (timeSpearIterator.hasNext()) {
            PendingTimeSpear spear = timeSpearIterator.next();
            if (!spear.owner.equals(ownerId)) continue;
            clearPlaced(spear.level, spear.visualBlocks);
            timeSpearIterator.remove();
        }
    }

    public static void clearAllMeteorVisuals() {
        for (PendingMeteor meteor : METEORS) clearMeteorVisual(meteor);
        for (PendingHellfireOrb orb : HELLFIRE_ORBS) clearHellfireVisual(orb);
        for (PendingNatureSeed seed : NATURE_SEEDS) clearPlaced(seed.level, seed.visualBlocks);
        for (PendingVineTrap trap : VINE_TRAPS) clearPlaced(trap.level, trap.visualBlocks);
        for (PendingLifeTree tree : LIFE_TREES) clearPlaced(tree.level, tree.visualBlocks);
        for (TemporaryNatureShape shape : NATURE_SHAPES) clearPlaced(shape.level, shape.visualBlocks);
        METEORS.clear();
        HELLFIRE_ORBS.clear();
        NATURE_SEEDS.clear();
        VINE_TRAPS.clear();
        LIFE_TREES.clear();
        ROOT_WAVES.clear();
        NATURE_SHAPES.clear();
        for (PendingFlightSpear spear : FLIGHT_SPEARS) clearPlaced(spear.level, spear.visualBlocks);
        for (PendingSkyBomb bomb : SKY_BOMBS) clearPlaced(bomb.level, bomb.visualBlocks);
        for (PendingTimeSpear spear : TIME_SPEARS) clearPlaced(spear.level, spear.visualBlocks);
        for (PendingTimePrison prison : TIME_PRISONS) clearPlaced(prison.level, prison.visualBlocks);
        for (PendingTimeField field : TIME_FIELDS) clearPlaced(field.level, field.visualBlocks);
        for (TemporaryTimeShape shape : TIME_SHAPES) clearPlaced(shape.level, shape.visualBlocks);
        FLIGHT_SPEARS.clear();
        SKY_BOMBS.clear();
        TIME_SPEARS.clear();
        TIME_PRISONS.clear();
        TIME_FIELDS.clear();
        TIME_SHAPES.clear();
        TIME_HISTORY.clear();
        LAST_FLIGHT_POSITION.clear();
        DRAGON_CLAW_TARGET.clear();
        DRAGON_CLAW_UNTIL.clear();
        DRAGON_CLAW_ESCAPE_PRESSES.clear();
        DRAGON_BREATHS.clear();
        DRAGON_SILENCE_UNTIL.clear();
        for (WardenAmbushState state : WARDEN_AMBUSHES.values()) {
            ServerPlayer ambushed = state.level.getServer().getPlayerList().getPlayer(state.playerId);
            if (ambushed != null) restoreAfterAmbush(ambushed, state);
        }
        WARDEN_AMBUSHES.clear();
        for (WardenArmSegment segment : WARDEN_ARM_SEGMENTS) {
            Entity raw = segment.level.getEntity(segment.entityId);
            if (raw != null) raw.discard();
        }
        WARDEN_ARM_SEGMENTS.clear();
        WARDEN_ARM_STRIKES.clear();
        AncientChargeSystem.clearPendingBeams();
    }

    private static void impactMeteor(PendingMeteor meteor) {
        clearMeteorVisual(meteor);
        ServerLevel level = meteor.level;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(meteor.owner);
        Vec3 impact = meteor.impact;
        int radius = meteor.radius;

        level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y + 0.6, impact.z, 14, 1.9, 1.2, 1.9, 0.08);
        level.sendParticles(meteor.charged ? ParticleTypes.WITCH : ParticleTypes.FLAME, impact.x, impact.y + 0.6, impact.z, meteor.charged ? 110 : 75, 3.2, 1.8, 3.2, 0.16);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, impact.x, impact.y + 1.0, impact.z, meteor.charged ? 46 : 34, 2.7, 2.1, 2.7, 0.08);
        level.playSound(null, BlockPos.containing(impact), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.2F, 0.62F);
        ServerNetworking.sendScreenShake(level, impact, 30.0, 1.45F, 14);

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
                push = push.normalize().scale(meteor.charged ? 3.35 : 2.05);
                target.push(push.x, 0.92, push.z);
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
        long[] credits = LAST_MASTERY_CREDIT.computeIfAbsent(player.getUUID(), ignored -> new long[6]);
        int index = Math.max(0, Math.min(5, power - 1));
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

    private static boolean isDragonSilenced(ServerPlayer player, long now) {
        long until = DRAGON_SILENCE_UNTIL.getOrDefault(player.getUUID(), 0L);
        if (until <= now) {
            DRAGON_SILENCE_UNTIL.remove(player.getUUID());
            return false;
        }
        return true;
    }

    public static void tryEscapeDragonClaw(ServerPlayer trappedPlayer) {
        UUID trappedId = trappedPlayer.getUUID();
        UUID holderId = null;
        for (Map.Entry<UUID, UUID> entry : DRAGON_CLAW_TARGET.entrySet()) {
            if (trappedId.equals(entry.getValue())) {
                holderId = entry.getKey();
                break;
            }
        }
        if (holderId == null) return;
        int presses = DRAGON_CLAW_ESCAPE_PRESSES.merge(trappedId, 1, Integer::sum);
        trappedPlayer.sendSystemMessage(Component.literal("Pençeden kaçış: " + Math.min(10, presses) + "/10"));
        if (presses < 10) return;
        ServerLevel level = (ServerLevel) trappedPlayer.level();
        ServerPlayer holder = level.getServer().getPlayerList().getPlayer(holderId);
        Vec3 away = holder == null ? trappedPlayer.getLookAngle().scale(-1.0) : trappedPlayer.position().subtract(holder.position());
        if (away.lengthSqr() < 0.0001) away = new Vec3(0.0, 0.0, 1.0);
        away = away.normalize().scale(1.55);
        trappedPlayer.setDeltaMovement(away.x, 0.65, away.z);
        trappedPlayer.hurtMarked = true;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, trappedPlayer.getX(), trappedPlayer.getY() + 1.0, trappedPlayer.getZ(), 48, 0.65, 0.8, 0.65, 0.08);
        trappedPlayer.sendSystemMessage(Component.literal("Avcı Pençesinden kaçtın!"));
        if (holder != null) holder.sendSystemMessage(Component.literal(trappedPlayer.getName().getString() + " Avcı Pençesinden kaçtı."));
        clearDragonClaw(holderId);
    }

    /**
     * Derinlik Pususu sırasında oyuncu gerçekten yer altındadır.
     * İksir direnci yeterli olmadığı için bütün hasar burada sunucu tarafında engellenir.
     */
    public static boolean allowWardenAmbushDamage(
        LivingEntity victim,
        net.minecraft.world.damagesource.DamageSource source,
        float amount
    ) {
        if (amount <= 0.0F || !(victim instanceof ServerPlayer player)) return true;
        return !WARDEN_AMBUSHES.containsKey(player.getUUID());
    }

    public static boolean allowDragonScalesDamage(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (reflectingDragonScaleDamage || amount <= 0.0F || !(victim instanceof ServerPlayer player)) return true;
        PlayerPowerData data = PlayerDataStore.get(player.getUUID());
        long now = player.level().getGameTime();
        if (data.powerClass() != PowerClass.FLIGHT || data.dragonScalesUntil() <= now || data.dragonScaleCharges() <= 0) return true;
        if (!data.consumeDragonScaleCharge()) return true;
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 45, 0.75, 0.9, 0.75, 0.07);
        level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.55, 0.7, 0.55, 0.04);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 1.7F);
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity living && living != player && !protectedAlly(player, living)) {
            reflectingDragonScaleDamage = true;
            try {
                living.hurtServer(level, level.damageSources().playerAttack(player), Math.max(3.0F, amount * 0.65F));
                Vec3 push = living.position().subtract(player.position());
                if (push.lengthSqr() > 0.0001) {
                    push = push.normalize().scale(1.25);
                    living.push(push.x, 0.45, push.z);
                }
            } finally {
                reflectingDragonScaleDamage = false;
            }
        }
        player.sendSystemMessage(Component.literal("Kadim Pul kırıldı. Kalan: " + data.dragonScaleCharges()));
        PlayerDataStore.markDirty();
        ServerNetworking.sync(player);
        return false;
    }

    private static void tickDragonBreath(ServerPlayer player, DragonBreathState breath, ServerLevel level, long now) {
        Vec3 origin = player.getEyePosition().add(player.getLookAngle().normalize().scale(0.65));
        Vec3 look = player.getLookAngle().normalize();
        double range = breath.empowered ? 18.0 : 14.0;
        for (int i = 1; i <= 22; i++) {
            double d = range * i / 22.0;
            Vec3 point = origin.add(look.scale(d));
            double spread = 0.16 + d * 0.075;
            level.sendParticles(i % 3 == 0 ? ParticleTypes.WITCH : ParticleTypes.REVERSE_PORTAL,
                point.x, point.y, point.z, breath.empowered ? 7 : 4, spread, spread * 0.72, spread, 0.025);
        }
        if (now % 5L != 0L) return;
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(range * 0.48);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (target == player || protectedAlly(player, target)) continue;
            Vec3 to = target.getEyePosition().subtract(origin);
            double distance = to.length();
            if (distance <= 0.01 || distance > range || look.dot(to.normalize()) < 0.68) continue;
            float pulseDamage = (float) (1.8 + breath.stage * 0.45 + (breath.empowered ? 1.7 : 0.0));
            target.hurtServer(level, level.damageSources().playerAttack(player), pulseDamage);
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 45, breath.empowered ? 2 : 1, false, true, true));
            target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), breath.empowered ? 60 : 30));
            Vec3 push = look.scale(breath.empowered ? 0.42 : 0.25);
            target.push(push.x, 0.05, push.z);
        }
    }

    private static void clearDragonClaw(UUID playerId) {
        UUID target = DRAGON_CLAW_TARGET.remove(playerId);
        DRAGON_CLAW_UNTIL.remove(playerId);
        if (target != null) DRAGON_CLAW_ESCAPE_PRESSES.remove(target);
    }

    public static String formatSeconds(int ticks) {
        return String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0);
    }


    private static final class DragonBreathState {
        private final long untilTick;
        private final int stage;
        private final boolean empowered;

        private DragonBreathState(long untilTick, int stage, boolean empowered) {
            this.untilTick = untilTick;
            this.stage = stage;
            this.empowered = empowered;
        }
    }

    private static final class PendingHellfireOrb {
        private final ServerLevel level;
        private UUID owner;
        private Vec3 position;
        private Vec3 velocity;
        private long expireTick;
        private final int stage;
        private final boolean charged;
        private final boolean comboPrimer;
        private boolean realityFrozen;
        private UUID realityOwner;
        private final List<BlockPos> visualBlocks = new ArrayList<>();

        private PendingHellfireOrb(
            ServerLevel level,
            UUID owner,
            Vec3 position,
            Vec3 velocity,
            long expireTick,
            int stage,
            boolean charged,
            boolean comboPrimer
        ) {
            this.level = level;
            this.owner = owner;
            this.position = position;
            this.velocity = velocity;
            this.expireTick = expireTick;
            this.stage = stage;
            this.charged = charged;
            this.comboPrimer = comboPrimer;
        }
    }

    private record PlacedBlock(BlockPos pos, BlockState state) {}

    private static final class PendingNatureSeed {
        private final ServerLevel level;
        private UUID owner;
        private Vec3 position;
        private Vec3 velocity;
        private long expireTick;
        private final int stage;
        private final boolean charged;
        private final boolean comboForest;
        private boolean realityFrozen;
        private UUID realityOwner;
        private final Vec3 comboCenter;
        private final List<PlacedBlock> visualBlocks = new ArrayList<>();
        private PendingNatureSeed(ServerLevel level, UUID owner, Vec3 position, Vec3 velocity, long expireTick, int stage, boolean charged) {
            this(level, owner, position, velocity, expireTick, stage, charged, false, null);
        }
        private PendingNatureSeed(ServerLevel level, UUID owner, Vec3 position, Vec3 velocity, long expireTick, int stage, boolean charged, boolean comboForest, Vec3 comboCenter) {
            this.level = level; this.owner = owner; this.position = position; this.velocity = velocity; this.expireTick = expireTick; this.stage = stage; this.charged = charged; this.comboForest = comboForest; this.comboCenter = comboCenter;
        }
    }

    private static final class PendingVineTrap {
        private final ServerLevel level;
        private final UUID owner;
        private final Vec3 center;
        private final long expireTick;
        private final int stage;
        private final boolean charged;
        private final List<PlacedBlock> visualBlocks = new ArrayList<>();
        private PendingVineTrap(ServerLevel level, UUID owner, Vec3 center, long expireTick, int stage, boolean charged) {
            this.level = level; this.owner = owner; this.center = center; this.expireTick = expireTick; this.stage = stage; this.charged = charged;
        }
    }

    private static final class PendingLifeTree {
        private final ServerLevel level;
        private final UUID owner;
        private final Vec3 center;
        private final long expireTick;
        private final int stage;
        private final boolean charged;
        private final List<PlacedBlock> visualBlocks = new ArrayList<>();
        private PendingLifeTree(ServerLevel level, UUID owner, Vec3 center, long expireTick, int stage, boolean charged) {
            this.level = level; this.owner = owner; this.center = center; this.expireTick = expireTick; this.stage = stage; this.charged = charged;
        }
    }

    private static final class PendingRootWave {
        private final ServerLevel level;
        private UUID owner;
        private Vec3 start;
        private Vec3 direction;
        private long startTick;
        private final int maxSteps;
        private final int stage;
        private final boolean charged;
        private int nextStep;
        private final java.util.Set<UUID> hitTargets = new java.util.HashSet<>();
        private boolean realityFrozen;
        private UUID realityOwner;
        private PendingRootWave(ServerLevel level, UUID owner, Vec3 start, Vec3 direction, long startTick, int maxSteps, int stage, boolean charged) {
            this.level = level; this.owner = owner; this.start = start; this.direction = direction; this.startTick = startTick; this.maxSteps = maxSteps; this.stage = stage; this.charged = charged;
        }
    }

    private static final class TemporaryNatureShape {
        private final ServerLevel level;
        private final List<PlacedBlock> visualBlocks;
        private final long expireTick;
        private TemporaryNatureShape(ServerLevel level, List<PlacedBlock> visualBlocks, long expireTick) {
            this.level = level; this.visualBlocks = visualBlocks; this.expireTick = expireTick;
        }
    }

    private static final class PendingMeteor {
        private final ServerLevel level;
        private UUID owner;
        private Vec3 start;
        private Vec3 impact;
        private long spawnTick;
        private long impactTick;
        private final int radius;
        private final float damage;
        private final boolean charged;
        private final List<BlockPos> visualBlocks = new ArrayList<>();
        private BlockPos visualCenter;
        private boolean realityFrozen;
        private UUID realityOwner;
        private Vec3 frozenPosition;

        private PendingMeteor(
            ServerLevel level,
            UUID owner,
            Vec3 start,
            Vec3 impact,
            long spawnTick,
            long impactTick,
            int radius,
            float damage,
            boolean charged
        ) {
            this.level = level;
            this.owner = owner;
            this.start = start;
            this.impact = impact;
            this.spawnTick = spawnTick;
            this.impactTick = impactTick;
            this.radius = radius;
            this.damage = damage;
            this.charged = charged;
        }
    }

    private static final class PendingFlightSpear {
        private final ServerLevel level;
        private UUID owner;
        private Vec3 position;
        private Vec3 velocity;
        private final long spawnTick;
        private long expireTick;
        private final int stage;
        private final boolean charged;
        private final boolean ultimate;
        private boolean realityFrozen;
        private UUID realityOwner;
        private final List<PlacedBlock> visualBlocks = new ArrayList<>();
        private PendingFlightSpear(ServerLevel level, UUID owner, Vec3 position, Vec3 velocity, long spawnTick, long expireTick, int stage, boolean charged, boolean ultimate) {
            this.level = level; this.owner = owner; this.position = position; this.velocity = velocity; this.spawnTick = spawnTick; this.expireTick = expireTick; this.stage = stage; this.charged = charged; this.ultimate = ultimate;
        }
    }

    private static final class PendingSkyBomb {
        private final ServerLevel level;
        private UUID owner;
        private Vec3 position;
        private Vec3 velocity;
        private final long spawnTick;
        private long expireTick;
        private final int stage;
        private final boolean charged;
        private final boolean ultimate;
        private boolean realityFrozen;
        private UUID realityOwner;
        private final List<PlacedBlock> visualBlocks = new ArrayList<>();
        private PendingSkyBomb(ServerLevel level, UUID owner, Vec3 position, Vec3 velocity, long spawnTick, long expireTick, int stage, boolean charged, boolean ultimate) {
            this.level = level; this.owner = owner; this.position = position; this.velocity = velocity; this.spawnTick = spawnTick; this.expireTick = expireTick; this.stage = stage; this.charged = charged; this.ultimate = ultimate;
        }
    }

    private static final class PendingTimeSpear {
        private final ServerLevel level;
        private final UUID owner;
        private Vec3 position;
        private final Vec3 velocity;
        private final long expireTick;
        private final int stage;
        private final boolean charged;
        private final List<PlacedBlock> visualBlocks = new ArrayList<>();
        private PendingTimeSpear(ServerLevel level, UUID owner, Vec3 position, Vec3 velocity, long expireTick, int stage, boolean charged) {
            this.level = level; this.owner = owner; this.position = position; this.velocity = velocity; this.expireTick = expireTick; this.stage = stage; this.charged = charged;
        }
    }

    private static final class PendingTimePrison {
        private final ServerLevel level;
        private final UUID owner;
        private final UUID target;
        private final Vec3 anchor;
        private final long expireTick;
        private final int stage;
        private final boolean charged;
        private final List<PlacedBlock> visualBlocks = new ArrayList<>();
        private boolean initialized;
        private boolean previousNoGravity;
        private PendingTimePrison(ServerLevel level, UUID owner, UUID target, Vec3 anchor, long expireTick, int stage, boolean charged) {
            this.level = level; this.owner = owner; this.target = target; this.anchor = anchor; this.expireTick = expireTick; this.stage = stage; this.charged = charged;
        }
    }

    private static final class PendingTimeField {
        private final ServerLevel level;
        private final UUID owner;
        private final Vec3 center;
        private final long expireTick;
        private final int stage;
        private final boolean charged;
        private final List<PlacedBlock> visualBlocks = new ArrayList<>();
        private final Map<UUID, Vec3> anchors = new HashMap<>();
        private final Map<UUID, Boolean> previousNoGravity = new HashMap<>();
        private PendingTimeField(ServerLevel level, UUID owner, Vec3 center, long expireTick, int stage, boolean charged) {
            this.level = level; this.owner = owner; this.center = center; this.expireTick = expireTick; this.stage = stage; this.charged = charged;
        }
    }

    private static final class TemporaryTimeShape {
        private final ServerLevel level;
        private final List<PlacedBlock> visualBlocks;
        private final long expireTick;
        private TemporaryTimeShape(ServerLevel level, List<PlacedBlock> visualBlocks, long expireTick) {
            this.level = level; this.visualBlocks = visualBlocks; this.expireTick = expireTick;
        }
    }

    private static final class TimeSnapshot {
        private final long tick;
        private final Vec3 position;
        private final float health;
        private final float yRot;
        private final float xRot;
        private TimeSnapshot(long tick, Vec3 position, float health, float yRot, float xRot) {
            this.tick = tick; this.position = position; this.health = health; this.yRot = yRot; this.xRot = xRot;
        }
    }


    private record WardenAmbushState(ServerLevel level, UUID playerId, Vec3 origin, long startTick,
                                     long endTick, int stage, boolean charged, boolean wasInvisible,
                                     Map<EquipmentSlot, ItemStack> hiddenEquipment,
                                     List<ItemStack> hiddenExtraItems) {}
    private record WardenArmSegment(ServerLevel level, UUID entityId, UUID targetId, Vec3 start, Vec3 fixedEnd,
                                    long startTick, long endTick, int segmentIndex, int segmentCount,
                                    double sideCurve, double lift) {}
    private record WardenArmStrike(ServerLevel level, UUID casterId, UUID targetId, long impactTick,
                                   int strikeType, int stage, boolean charged) {}

}
