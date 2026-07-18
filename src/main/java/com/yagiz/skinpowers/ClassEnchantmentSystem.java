package com.yagiz.skinpowers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClassEnchantmentSystem {
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, EmberCombo> EMBER_COMBOS = new HashMap<>();
    private static final Map<UUID, Integer> DEPTH_ENERGY = new HashMap<>();
    private static final Map<UUID, Boolean> DOUBLE_JUMP_USED = new HashMap<>();
    private static final Map<UUID, ProjectileState> PROJECTILES = new HashMap<>();
    private static final Set<UUID> SEEN_PROJECTILES = new HashSet<>();
    private static final List<DelayedDamage> DELAYED_DAMAGE = new ArrayList<>();
    private static final List<AshPatch> ASH_PATCHES = new ArrayList<>();
    private static final List<HealingSprout> HEALING_SPROUTS = new ArrayList<>();
    private static final List<PurpleBreathZone> PURPLE_BREATH_ZONES = new ArrayList<>();
    private static final List<EnchantMeteor> METEORS = new ArrayList<>();
    private static boolean internalDamage;

    private ClassEnchantmentSystem() {}

    public static InteractionResult onAttackEntity(
        net.minecraft.world.entity.player.Player player,
        Level level,
        InteractionHand hand,
        Entity entity,
        EntityHitResult hitResult
    ) {
        if (!(player instanceof ServerPlayer attacker)
            || !(level instanceof ServerLevel serverLevel)
            || !(entity instanceof LivingEntity target)
            || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        PlayerPowerData data = PlayerDataStore.get(attacker.getUUID());
        ItemStack weapon = attacker.getMainHandItem();
        long now = serverLevel.getGameTime();

        if (data.powerClass() == PowerClass.WARDEN) {
            if (ClassEnchantments.has(serverLevel.registryAccess(), weapon, ClassEnchantments.ECHO_STRIKE)
                && ready(attacker, "echo", now, 70L)) {
                echoStrike(attacker, target, serverLevel);
            }
            if (weapon.is(Items.MACE)
                && attacker.fallDistance >= 2.0F
                && ClassEnchantments.has(serverLevel.registryAccess(), weapon, ClassEnchantments.ANCIENT_COLLAPSE)
                && ready(attacker, "ancient_collapse", now, 180L)) {
                ancientCollapse(attacker, target, serverLevel);
            }
        } else if (data.powerClass() == PowerClass.FIRE) {
            if (ClassEnchantments.has(serverLevel.registryAccess(), weapon, ClassEnchantments.EMBER_BUILDUP)) {
                emberHit(attacker, target, serverLevel, now);
            }
            if (weapon.is(Items.MACE)
                && attacker.fallDistance >= 2.0F
                && ClassEnchantments.has(serverLevel.registryAccess(), weapon, ClassEnchantments.METEOR_FALL)
                && ready(attacker, "meteor_fall", now, 240L)) {
                startMeteor(attacker, target, serverLevel, now);
            }
        } else if (data.powerClass() == PowerClass.NATURE) {
            if (ClassEnchantments.has(serverLevel.registryAccess(), weapon, ClassEnchantments.ROOT_BIND)
                && ready(attacker, "root_bind", now, 120L)) {
                rootBind(attacker, target, serverLevel);
            }
        } else if (data.powerClass() == PowerClass.ANOMALY) {
            if (ClassEnchantments.has(serverLevel.registryAccess(), weapon, ClassEnchantments.DELAYED_STRIKE)
                && ready(attacker, "delayed_strike", now, 90L)) {
                DELAYED_DAMAGE.add(new DelayedDamage(serverLevel, attacker.getUUID(), target.getUUID(), now + 35L, 3.5F));
                serverLevel.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 1.0, target.getZ(), 18, 0.35, 0.55, 0.35, 0.04);
            }
        } else if (data.powerClass() == PowerClass.FLIGHT) {
            if (!attacker.onGround()
                && ClassEnchantments.has(serverLevel.registryAccess(), weapon, ClassEnchantments.DRAGON_CLAW)
                && ready(attacker, "dragon_claw_enchant", now, 100L)) {
                dragonClaw(attacker, target, serverLevel);
            }
        }

        return InteractionResult.PASS;
    }

    public static void tickServer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player);
            tickOwnedProjectiles(player);
        }
        tickDelayedDamage();
        tickAshPatches();
        tickHealingSprouts();
        tickPurpleBreathZones();
        tickMeteors();
        long now = server.overworld().getGameTime();
        COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() + 2400L < now);
    }

    private static void tickPlayer(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        PlayerPowerData data = PlayerDataStore.get(player.getUUID());
        long now = level.getGameTime();
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);

        if (player.onGround()) DOUBLE_JUMP_USED.put(player.getUUID(), false);

        if (data.powerClass() == PowerClass.WARDEN
            && ClassEnchantments.has(level.registryAccess(), boots, ClassEnchantments.DEPTH_STEP)) {
            tickDepthStep(player, level, now);
        }
        if (data.powerClass() == PowerClass.FIRE
            && ClassEnchantments.has(level.registryAccess(), boots, ClassEnchantments.ASH_WALK)) {
            tickAshWalk(player, level, now);
        }
        if (data.powerClass() == PowerClass.NATURE
            && ClassEnchantments.has(level.registryAccess(), boots, ClassEnchantments.FOREST_LEAP)) {
            tickForestLeap(player, level);
        }
    }

    public static boolean tryDragonJump(ServerPlayer player) {
        if (player.onGround()) return false;
        PlayerPowerData data = PlayerDataStore.get(player.getUUID());
        if (data.powerClass() != PowerClass.FLIGHT) return false;
        ServerLevel level = (ServerLevel) player.level();
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        if (!ClassEnchantments.has(level.registryAccess(), boots, ClassEnchantments.PURPLE_WING)) return false;
        if (DOUBLE_JUMP_USED.getOrDefault(player.getUUID(), false)) return false;

        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x * 1.08, 0.72, motion.z * 1.08);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        DOUBLE_JUMP_USED.put(player.getUUID(), true);
        level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 0.5, player.getZ(), 34, 0.65, 0.35, 0.65, 0.08);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.8F, 1.35F);
        return true;
    }

    public static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
        if (internalDamage || amount <= 0.0F) return true;
        ServerLevel level = (ServerLevel) victim.level();
        long now = level.getGameTime();

        if (victim instanceof ServerPlayer player) {
            PlayerPowerData data = PlayerDataStore.get(player.getUUID());
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);

            if (data.powerClass() == PowerClass.FLIGHT
                && source.getDirectEntity() instanceof Projectile projectile
                && ClassEnchantments.has(level.registryAccess(), chest, ClassEnchantments.ANCIENT_SCALES)
                && ready(player, "ancient_scales_enchant", now, 220L)) {
                projectile.setOwner(player);
                projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-1.18));
                level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0, player.getZ(), 45, 0.7, 0.9, 0.7, 0.09);
                level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.65F);
                return false;
            }

            if (data.powerClass() == PowerClass.WARDEN
                && amount >= 6.0F
                && ClassEnchantments.has(level.registryAccess(), chest, ClassEnchantments.SCULK_ARMOR)
                && ready(player, "sculk_armor", now, 320L)) {
                try {
                    internalDamage = true;
                    player.hurtServer(level, source, amount * 0.55F);
                } finally {
                    internalDamage = false;
                }
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 80, 1, false, true, true));
                level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 38, 0.6, 0.8, 0.6, 0.07);
                return false;
            }

            if (data.powerClass() == PowerClass.FIRE
                && player.getHealth() - amount <= 8.0F
                && ClassEnchantments.has(level.registryAccess(), chest, ClassEnchantments.HELL_CORE)
                && ready(player, "hell_core_enchant", now, 700L)) {
                hellCore(player, level);
            }

            if (data.powerClass() == PowerClass.NATURE
                && source.getEntity() instanceof LivingEntity attacker
                && !(source.getDirectEntity() instanceof Projectile)
                && ClassEnchantments.has(level.registryAccess(), chest, ClassEnchantments.THORNY_DEFENSE)
                && ready(player, "thorny_defense", now, 40L)) {
                try {
                    internalDamage = true;
                    attacker.hurtServer(level, level.damageSources().thorns(player), 2.5F);
                } finally {
                    internalDamage = false;
                }
                Vec3 push = attacker.position().subtract(player.position()).normalize().scale(0.55);
                attacker.push(push.x, 0.25, push.z);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, attacker.getX(), attacker.getY() + 1.0, attacker.getZ(), 18, 0.35, 0.55, 0.35, 0.03);
            }
        }

        if (source.getDirectEntity() instanceof Projectile projectile) {
            ProjectileState state = PROJECTILES.get(projectile.getUUID());
            if (state != null && state.purpleBreath) {
                Entity owner = level.getEntity(state.owner);
                if (owner instanceof ServerPlayer player && PlayerDataStore.get(player.getUUID()).powerClass() == PowerClass.FLIGHT) {
                    PURPLE_BREATH_ZONES.add(new PurpleBreathZone(level, player.getUUID(), victim.position(), now + 100L));
                    level.sendParticles(ParticleTypes.DRAGON_BREATH, victim.getX(), victim.getY() + 0.6, victim.getZ(), 55, 1.0, 0.45, 1.0, 0.05);
                    state.purpleBreath = false;
                }
            }
        }

        return true;
    }

    public static boolean allowDeath(LivingEntity victim, DamageSource source, float amount) {
        if (victim instanceof ServerPlayer player) {
            ServerLevel level = (ServerLevel) player.level();
            PlayerPowerData data = PlayerDataStore.get(player.getUUID());
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            long now = level.getGameTime();
            if (data.powerClass() == PowerClass.ANOMALY
                && ClassEnchantments.has(level.registryAccess(), chest, ClassEnchantments.ERROR_MARGIN)
                && ready(player, "error_margin", now, 12000L)) {
                player.setHealth(2.0F);
                Vec3 back = player.getLookAngle().scale(-3.0);
                player.teleportTo(level, player.getX() + back.x, player.getY() + 0.5, player.getZ() + back.z, Set.of(), player.getYRot(), player.getXRot(), false);
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 4, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 50, 0, false, false, true));
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 80, 1.1, 1.0, 1.1, 0.12);
                player.sendSystemMessage(Component.literal("Hata Payı: Ölümcül sonuç iptal edildi."));
                return false;
            }
        }

        if (source.getEntity() instanceof ServerPlayer attacker) {
            ServerLevel level = (ServerLevel) attacker.level();
            PlayerPowerData data = PlayerDataStore.get(attacker.getUUID());
            ItemStack helmet = attacker.getItemBySlot(EquipmentSlot.HEAD);
            if (data.powerClass() == PowerClass.NATURE
                && ClassEnchantments.has(level.registryAccess(), helmet, ClassEnchantments.LIFE_SPROUT)
                && level.getRandom().nextFloat() < 0.38F) {
                HEALING_SPROUTS.add(new HealingSprout(level, attacker.getUUID(), victim.position(), level.getGameTime() + 140L));
            }
        }
        return true;
    }

    public static void clearAll() {
        for (EnchantMeteor meteor : METEORS) {
            Entity visual = meteor.level.getEntity(meteor.visualId);
            if (visual != null) visual.discard();
        }
        COOLDOWNS.clear();
        EMBER_COMBOS.clear();
        DEPTH_ENERGY.clear();
        DOUBLE_JUMP_USED.clear();
        PROJECTILES.clear();
        SEEN_PROJECTILES.clear();
        DELAYED_DAMAGE.clear();
        ASH_PATCHES.clear();
        HEALING_SPROUTS.clear();
        PURPLE_BREATH_ZONES.clear();
        METEORS.clear();
    }

    private static boolean ready(ServerPlayer player, String id, long now, long cooldown) {
        String key = player.getUUID() + ":" + id;
        long until = COOLDOWNS.getOrDefault(key, 0L);
        if (until > now) return false;
        COOLDOWNS.put(key, now + cooldown);
        return true;
    }

    private static void echoStrike(ServerPlayer player, LivingEntity target, ServerLevel level) {
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 start = target.position().add(0.0, 0.8, 0.0);
        for (int i = 0; i < 15; i++) {
            Vec3 point = start.add(direction.scale(i * 0.55));
            level.sendParticles(ParticleTypes.SONIC_BOOM, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        AABB area = new AABB(start, start.add(direction.scale(8.0))).inflate(1.35);
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (other == player || other == target || PowerSystem.isProtectedAlly(player, other)) continue;
            other.hurtServer(level, level.damageSources().playerAttack(player), 4.0F);
            other.push(direction.x * 0.55, 0.18, direction.z * 0.55);
        }
        level.playSound(null, target.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8F, 1.25F);
    }

    private static void ancientCollapse(ServerPlayer player, LivingEntity target, ServerLevel level) {
        Vec3 direction = player.getLookAngle();
        direction = new Vec3(direction.x, 0.0, direction.z).normalize();
        Vec3 origin = target.position();
        for (int step = 0; step < 10; step++) {
            Vec3 point = origin.add(direction.scale(step * 1.15));
            level.sendParticles(ParticleTypes.SCULK_SOUL, point.x, point.y + 0.15, point.z, 9, 0.45, 0.12, 0.45, 0.02);
            AABB hit = new AABB(point, point).inflate(1.1, 1.2, 1.1);
            for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, hit)) {
                if (other == player || PowerSystem.isProtectedAlly(player, other)) continue;
                other.hurtServer(level, level.damageSources().playerAttack(player), 3.0F);
                other.push(direction.x * 0.35, 0.55, direction.z * 0.35);
            }
        }
        ServerNetworking.sendScreenShake(level, origin, 18.0, 0.8F, 10);
    }

    private static void emberHit(ServerPlayer player, LivingEntity target, ServerLevel level, long now) {
        EmberCombo combo = EMBER_COMBOS.get(player.getUUID());
        int hits = combo != null && combo.target.equals(target.getUUID()) && combo.expireTick > now ? combo.hits + 1 : 1;
        if (hits < 3) {
            EMBER_COMBOS.put(player.getUUID(), new EmberCombo(target.getUUID(), hits, now + 80L));
            level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 1.0, target.getZ(), 5 + hits * 4, 0.25, 0.4, 0.25, 0.02);
            return;
        }
        EMBER_COMBOS.remove(player.getUUID());
        if (!ready(player, "ember_burst", now, 70L)) return;
        AABB area = target.getBoundingBox().inflate(3.0);
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (other == player || PowerSystem.isProtectedAlly(player, other)) continue;
            other.hurtServer(level, level.damageSources().playerAttack(player), 4.5F);
            other.setRemainingFireTicks(Math.max(other.getRemainingFireTicks(), 100));
        }
        level.sendParticles(ParticleTypes.LAVA, target.getX(), target.getY() + 0.8, target.getZ(), 42, 1.0, 0.65, 1.0, 0.08);
        level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.75F, 1.35F);
    }

    private static void rootBind(ServerPlayer player, LivingEntity target, ServerLevel level) {
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 70, 4, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 70, 1, false, true, true));
        for (int i = 0; i < 4; i++) {
            PowerSystem.drawExternalRing(level, target.position().add(0.0, i * 0.28, 0.0), 0.65 + i * 0.08, ParticleTypes.HAPPY_VILLAGER, 18);
        }
        level.playSound(null, target.blockPosition(), SoundEvents.GRASS_BREAK, SoundSource.PLAYERS, 0.8F, 0.75F);
    }

    private static void dragonClaw(ServerPlayer player, LivingEntity target, ServerLevel level) {
        target.hurtServer(level, level.damageSources().playerAttack(player), 3.0F);
        target.push(0.0, 0.85, 0.0);
        target.fallDistance = 0.0F;
        Vec3 center = target.position().add(0.0, 1.0, 0.0);
        for (int arm = 0; arm < 3; arm++) {
            double angle = arm * Math.PI * 2.0 / 3.0;
            for (int i = 0; i < 7; i++) {
                double radius = 1.35 - i * 0.14;
                level.sendParticles(ParticleTypes.WITCH,
                    center.x + Math.cos(angle) * radius,
                    center.y + (i - 3) * 0.22,
                    center.z + Math.sin(angle) * radius,
                    2, 0.08, 0.08, 0.08, 0.01);
            }
        }
        level.playSound(null, target.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.45F, 1.6F);
    }

    private static void hellCore(ServerPlayer player, ServerLevel level) {
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 160, 0, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 1, false, true, true));
        AABB area = player.getBoundingBox().inflate(4.5);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (target == player || PowerSystem.isProtectedAlly(player, target)) continue;
            target.hurtServer(level, level.damageSources().playerAttack(player), 4.0F);
            target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 120));
            Vec3 push = target.position().subtract(player.position()).normalize().scale(0.9);
            target.push(push.x, 0.35, push.z);
        }
        PowerSystem.drawExternalRing(level, player.position().add(0.0, 0.4, 0.0), 4.2, ParticleTypes.FLAME, 72);
        level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 1.0, player.getZ(), 65, 1.4, 1.0, 1.4, 0.08);
        ServerNetworking.sendScreenShake(level, player.position(), 18.0, 0.9F, 10);
    }

    private static void tickDepthStep(ServerPlayer player, ServerLevel level, long now) {
        int energy = DEPTH_ENERGY.getOrDefault(player.getUUID(), 0);
        if (!player.isSprinting() || !player.onGround()) {
            DEPTH_ENERGY.put(player.getUUID(), Math.max(0, energy - 1));
            return;
        }
        BlockState below = level.getBlockState(player.blockPosition().below());
        int gain = below.is(Blocks.SCULK) || below.is(Blocks.SCULK_VEIN) || below.is(Blocks.DEEPSLATE) ? 3 : 1;
        energy += gain;
        if (energy >= 90 && ready(player, "depth_step", now, 180L)) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 80, 1, false, true, true));
            level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 0.2, player.getZ(), 30, 0.55, 0.15, 0.55, 0.04);
            energy = 0;
        }
        DEPTH_ENERGY.put(player.getUUID(), Math.min(90, energy));
    }

    private static void tickAshWalk(ServerPlayer player, ServerLevel level, long now) {
        if (!player.isSprinting() || !player.onGround() || player.isInWater()) return;
        if (now % 7L != 0L) return;
        ASH_PATCHES.add(new AshPatch(level, player.getUUID(), player.position(), now + 55L));
        level.sendParticles(ParticleTypes.ASH, player.getX(), player.getY() + 0.08, player.getZ(), 12, 0.45, 0.04, 0.45, 0.02);
    }

    private static void tickForestLeap(ServerPlayer player, ServerLevel level) {
        BlockState below = level.getBlockState(player.blockPosition().below());
        boolean natural = below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.MOSS_BLOCK)
            || below.is(Blocks.OAK_LEAVES) || below.is(Blocks.BIRCH_LEAVES)
            || below.is(Blocks.SPRUCE_LEAVES) || below.is(Blocks.JUNGLE_LEAVES)
            || below.is(Blocks.ACACIA_LEAVES) || below.is(Blocks.DARK_OAK_LEAVES)
            || below.is(Blocks.MANGROVE_LEAVES) || below.is(Blocks.CHERRY_LEAVES);
        if (!natural) return;
        player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 25, 1, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, 25, 0, false, false, true));
        if (!player.onGround() && player.getDeltaMovement().y < -0.25) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 12, 0, false, false, true));
        }
    }

    private static void startMeteor(ServerPlayer player, LivingEntity target, ServerLevel level, long now) {
        Vec3 impact = target.position();
        Vec3 start = impact.add(0.0, 14.0, 0.0);
        ItemEntity visual = new ItemEntity(level, start.x, start.y, start.z, new ItemStack(Items.MAGMA_BLOCK));
        visual.setNoGravity(true);
        visual.setPickUpDelay(32767);
        visual.setGlowingTag(true);
        if (level.addFreshEntity(visual)) {
            METEORS.add(new EnchantMeteor(level, player.getUUID(), visual.getUUID(), start, impact, now, now + 24L));
        }
    }

    private static void tickMeteors() {
        Iterator<EnchantMeteor> iterator = METEORS.iterator();
        while (iterator.hasNext()) {
            EnchantMeteor meteor = iterator.next();
            long now = meteor.level.getGameTime();
            Entity visual = meteor.level.getEntity(meteor.visualId);
            Entity ownerEntity = meteor.level.getEntity(meteor.ownerId);
            if (!(visual instanceof ItemEntity) || !(ownerEntity instanceof ServerPlayer owner)) {
                if (visual != null) visual.discard();
                iterator.remove();
                continue;
            }
            double progress = Math.min(1.0, (now - meteor.startTick) / (double) Math.max(1L, meteor.impactTick - meteor.startTick));
            Vec3 position = meteor.start.lerp(meteor.impact, progress);
            visual.setPos(position.x, position.y, position.z);
            meteor.level.sendParticles(ParticleTypes.FLAME, position.x, position.y, position.z, 7, 0.32, 0.32, 0.32, 0.03);
            meteor.level.sendParticles(ParticleTypes.LARGE_SMOKE, position.x, position.y + 0.25, position.z, 3, 0.22, 0.22, 0.22, 0.01);
            if (now < meteor.impactTick) continue;
            visual.discard();
            AABB area = new AABB(meteor.impact, meteor.impact).inflate(3.4);
            for (LivingEntity target : meteor.level.getEntitiesOfClass(LivingEntity.class, area)) {
                if (target == owner || PowerSystem.isProtectedAlly(owner, target)) continue;
                target.hurtServer(meteor.level, meteor.level.damageSources().playerAttack(owner), 7.0F);
                target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 100));
                Vec3 push = target.position().subtract(meteor.impact).normalize().scale(0.8);
                target.push(push.x, 0.45, push.z);
            }
            meteor.level.sendParticles(ParticleTypes.EXPLOSION, meteor.impact.x, meteor.impact.y + 0.4, meteor.impact.z, 8, 1.1, 0.5, 1.1, 0.02);
            meteor.level.playSound(null, BlockPos.containing(meteor.impact), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 0.75F);
            ServerNetworking.sendScreenShake(meteor.level, meteor.impact, 20.0, 1.0F, 12);
            iterator.remove();
        }
    }

    private static void tickOwnedProjectiles(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        PlayerPowerData data = PlayerDataStore.get(player.getUUID());
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        long now = level.getGameTime();
        AABB area = player.getBoundingBox().inflate(72.0);
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, area)) {
            if (projectile.getOwner() != player) continue;
            ProjectileState state = PROJECTILES.get(projectile.getUUID());
            if (state == null && !SEEN_PROJECTILES.contains(projectile.getUUID())) {
                ItemStack firedFrom = projectile.getWeaponItem();
                boolean phase = data.powerClass() == PowerClass.ANOMALY
                    && (ClassEnchantments.has(level.registryAccess(), firedFrom, ClassEnchantments.PHASE_SHIFT)
                        || hasEither(level, main, off, ClassEnchantments.PHASE_SHIFT));
                boolean broken = data.powerClass() == PowerClass.ANOMALY
                    && (ClassEnchantments.has(level.registryAccess(), firedFrom, ClassEnchantments.BROKEN_TRAJECTORY)
                        || hasEither(level, main, off, ClassEnchantments.BROKEN_TRAJECTORY));
                boolean breath = data.powerClass() == PowerClass.FLIGHT
                    && (ClassEnchantments.has(level.registryAccess(), firedFrom, ClassEnchantments.PURPLE_BREATH)
                        || hasEither(level, main, off, ClassEnchantments.PURPLE_BREATH));
                if (phase || broken || breath) {
                    state = new ProjectileState(level, player.getUUID(), now, phase, broken, breath);
                    PROJECTILES.put(projectile.getUUID(), state);
                }
                SEEN_PROJECTILES.add(projectile.getUUID());
            }
            if (state == null) continue;
            long age = now - state.spawnTick;
            if (state.phaseShift && age <= 18L) {
                projectile.setNoGravity(true);
                Vec3 velocity = projectile.getDeltaMovement();
                if (velocity.lengthSqr() > 0.001) {
                    Vec3 next = projectile.position().add(velocity.normalize().scale(0.85));
                    if (!level.getBlockState(BlockPos.containing(next)).isAir() && !state.phaseUsed) {
                        Vec3 exit = projectile.position().add(velocity.normalize().scale(1.85));
                        if (level.getBlockState(BlockPos.containing(exit)).isAir()) {
                            projectile.setPos(exit.x, exit.y, exit.z);
                            state.phaseUsed = true;
                            level.sendParticles(ParticleTypes.REVERSE_PORTAL, exit.x, exit.y, exit.z, 20, 0.3, 0.3, 0.3, 0.05);
                        }
                    }
                }
            }
            if (state.phaseShift && age > 18L && !state.gravityRestored) {
                projectile.setNoGravity(false);
                state.gravityRestored = true;
            }
            if (state.brokenTrajectory && !state.trajectoryUsed && age >= 12L) {
                LivingEntity target = nearestTarget(level, player, projectile.position(), 12.0);
                if (target != null) {
                    Vec3 speed = projectile.getDeltaMovement();
                    double magnitude = Math.max(0.75, speed.length());
                    Vec3 direction = target.getEyePosition().subtract(projectile.position()).normalize();
                    projectile.setDeltaMovement(direction.scale(magnitude));
                    state.trajectoryUsed = true;
                    level.sendParticles(ParticleTypes.WITCH, projectile.getX(), projectile.getY(), projectile.getZ(), 24, 0.45, 0.45, 0.45, 0.08);
                }
            }
        }
        PROJECTILES.entrySet().removeIf(entry -> {
            Entity entity = entry.getValue().level.getEntity(entry.getKey());
            return entity == null || entity.isRemoved() || entry.getValue().level.getGameTime() - entry.getValue().spawnTick > 300L;
        });
    }

    private static boolean hasEither(ServerLevel level, ItemStack first, ItemStack second, net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key) {
        return ClassEnchantments.has(level.registryAccess(), first, key) || ClassEnchantments.has(level.registryAccess(), second, key);
    }

    private static LivingEntity nearestTarget(ServerLevel level, ServerPlayer owner, Vec3 center, double radius) {
        LivingEntity best = null;
        double bestDistance = radius * radius;
        AABB area = new AABB(center, center).inflate(radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (target == owner || !target.isAlive() || PowerSystem.isProtectedAlly(owner, target)) continue;
            double distance = target.position().distanceToSqr(center);
            if (distance < bestDistance) {
                best = target;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void tickDelayedDamage() {
        Iterator<DelayedDamage> iterator = DELAYED_DAMAGE.iterator();
        while (iterator.hasNext()) {
            DelayedDamage entry = iterator.next();
            if (entry.level.getGameTime() < entry.hitTick) continue;
            Entity attackerEntity = entry.level.getEntity(entry.attacker);
            Entity targetEntity = entry.level.getEntity(entry.target);
            if (attackerEntity instanceof ServerPlayer attacker && targetEntity instanceof LivingEntity target && target.isAlive()) {
                target.hurtServer(entry.level, entry.level.damageSources().playerAttack(attacker), entry.damage);
                entry.level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 1.0, target.getZ(), 28, 0.45, 0.65, 0.45, 0.08);
                entry.level.playSound(null, target.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 0.6F);
            }
            iterator.remove();
        }
    }

    private static void tickAshPatches() {
        Iterator<AshPatch> iterator = ASH_PATCHES.iterator();
        while (iterator.hasNext()) {
            AshPatch patch = iterator.next();
            long now = patch.level.getGameTime();
            Entity ownerEntity = patch.level.getEntity(patch.owner);
            if (!(ownerEntity instanceof ServerPlayer owner) || now >= patch.expireTick) {
                iterator.remove();
                continue;
            }
            if (now % 8L == 0L) {
                patch.level.sendParticles(ParticleTypes.ASH, patch.center.x, patch.center.y + 0.08, patch.center.z, 10, 0.65, 0.04, 0.65, 0.01);
                AABB area = new AABB(patch.center, patch.center).inflate(1.2, 0.7, 1.2);
                for (LivingEntity target : patch.level.getEntitiesOfClass(LivingEntity.class, area)) {
                    if (target == owner || PowerSystem.isProtectedAlly(owner, target)) continue;
                    target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 45));
                    target.hurtServer(patch.level, patch.level.damageSources().playerAttack(owner), 1.0F);
                }
            }
        }
    }

    private static void tickHealingSprouts() {
        Iterator<HealingSprout> iterator = HEALING_SPROUTS.iterator();
        while (iterator.hasNext()) {
            HealingSprout sprout = iterator.next();
            long now = sprout.level.getGameTime();
            Entity ownerEntity = sprout.level.getEntity(sprout.owner);
            if (!(ownerEntity instanceof ServerPlayer owner) || now >= sprout.expireTick) {
                iterator.remove();
                continue;
            }
            sprout.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, sprout.center.x, sprout.center.y + 0.45, sprout.center.z, 4, 0.25, 0.35, 0.25, 0.01);
            if (owner.position().distanceToSqr(sprout.center) <= 4.0) {
                owner.heal(5.0F);
                sprout.level.sendParticles(ParticleTypes.HEART, owner.getX(), owner.getY() + 1.0, owner.getZ(), 8, 0.45, 0.6, 0.45, 0.02);
                iterator.remove();
            }
        }
    }

    private static void tickPurpleBreathZones() {
        Iterator<PurpleBreathZone> iterator = PURPLE_BREATH_ZONES.iterator();
        while (iterator.hasNext()) {
            PurpleBreathZone zone = iterator.next();
            long now = zone.level.getGameTime();
            Entity ownerEntity = zone.level.getEntity(zone.owner);
            if (!(ownerEntity instanceof ServerPlayer owner) || now >= zone.expireTick) {
                iterator.remove();
                continue;
            }
            if (now % 4L == 0L) {
                zone.level.sendParticles(ParticleTypes.DRAGON_BREATH, zone.center.x, zone.center.y + 0.35, zone.center.z, 14, 1.15, 0.35, 1.15, 0.02);
            }
            if (now % 20L == 0L) {
                AABB area = new AABB(zone.center, zone.center).inflate(2.7, 1.5, 2.7);
                for (LivingEntity target : zone.level.getEntitiesOfClass(LivingEntity.class, area)) {
                    if (target == owner || PowerSystem.isProtectedAlly(owner, target)) continue;
                    target.hurtServer(zone.level, zone.level.damageSources().playerAttack(owner), 2.0F);
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1, false, true, true));
                }
            }
        }
    }

    private record EmberCombo(UUID target, int hits, long expireTick) {}
    private record DelayedDamage(ServerLevel level, UUID attacker, UUID target, long hitTick, float damage) {}
    private record AshPatch(ServerLevel level, UUID owner, Vec3 center, long expireTick) {}
    private record HealingSprout(ServerLevel level, UUID owner, Vec3 center, long expireTick) {}
    private record PurpleBreathZone(ServerLevel level, UUID owner, Vec3 center, long expireTick) {}
    private record EnchantMeteor(ServerLevel level, UUID ownerId, UUID visualId, Vec3 start, Vec3 impact, long startTick, long impactTick) {}

    private static final class ProjectileState {
        private final ServerLevel level;
        private final UUID owner;
        private final long spawnTick;
        private final boolean phaseShift;
        private final boolean brokenTrajectory;
        private boolean purpleBreath;
        private boolean phaseUsed;
        private boolean trajectoryUsed;
        private boolean gravityRestored;

        private ProjectileState(ServerLevel level, UUID owner, long spawnTick, boolean phaseShift, boolean brokenTrajectory, boolean purpleBreath) {
            this.level = level;
            this.owner = owner;
            this.spawnTick = spawnTick;
            this.phaseShift = phaseShift;
            this.brokenTrajectory = brokenTrajectory;
            this.purpleBreath = purpleBreath;
        }
    }
}
