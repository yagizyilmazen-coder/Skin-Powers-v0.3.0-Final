package com.yagiz.skinpowers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 1.2.0: Manyetik ve Kum sınıflarının yüksek kaliteli, gerçek eşya/blok gövdeli güçleri.
 * Parçacıklar yalnızca darbe/patlama vurgusudur; ana modeller ItemEntity gövdeleridir.
 */
public final class ExpansionPowerSystem {
    private static final List<StaticVisual> STATIC_VISUALS = new ArrayList<>();
    private static final List<MovingAttack> MOVING_ATTACKS = new ArrayList<>();
    private static final List<SandWave> SAND_WAVES = new ArrayList<>();
    private static final List<MagneticCage> MAGNETIC_CAGES = new ArrayList<>();
    private static final List<SandGrave> SAND_GRAVES = new ArrayList<>();
    private static final List<SandGiantArm> SAND_GIANT_ARMS = new ArrayList<>();
    private static final Map<UUID, MagneticStorm> MAGNETIC_STORMS = new HashMap<>();
    private static final Map<UUID, SandArmor> SAND_ARMORS = new HashMap<>();
    private static final Map<UUID, DesertMirrors> DESERT_MIRRORS = new HashMap<>();
    private static boolean internalDamage;

    private ExpansionPowerSystem() {}

    public static void tickServer(MinecraftServer server) {
        tickStaticVisuals();
        tickMovingAttacks();
        tickSandWaves();
        tickMagneticStorms(server);
        tickMagneticCages(server);
        tickSandArmors(server);
        tickDesertMirrors(server);
        tickSandGraves(server);
        tickSandGiantArms(server);
    }

    public static void tickPlayer(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        if (data.powerClass() == PowerClass.MAGNETIC) {
            // Metal zırh kullanan rakipleri hissettiren hafif pasif. Sürekli parçacık üretmez.
            if (data.unlockedLevel() >= 1 && now % 20L == 0L) {
                for (LivingEntity target : nearby(player, 12.0)) {
                    if (target == player || PowerSystem.isProtectedAlly(player, target) || metalArmorPieces(target) <= 0) continue;
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 28, 0, false, false, true));
                }
            }
        } else if (data.powerClass() == PowerClass.SAND) {
            // Kum üzerinde akıcı hareket; görsel spam oluşturmaz.
            BlockState below = level.getBlockState(player.blockPosition().below());
            if (data.unlockedLevel() >= 1 && (below.is(net.minecraft.tags.BlockTags.SAND)
                || below.is(Blocks.SANDSTONE) || below.is(Blocks.RED_SANDSTONE)
                || below.is(Blocks.TERRACOTTA) || below.is(Blocks.WHITE_TERRACOTTA)
                || below.is(Blocks.ORANGE_TERRACOTTA) || below.is(Blocks.YELLOW_TERRACOTTA)
                || below.is(Blocks.BROWN_TERRACOTTA) || below.is(Blocks.RED_TERRACOTTA))) {
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 24, 0, false, false, true));
                player.fallDistance = Math.min(player.fallDistance, 3.0F);
            }
        }
    }

    public static boolean useMagnetic(ServerPlayer player, PlayerPowerData data, int power, long now, boolean awakened) {
        ServerLevel level = (ServerLevel) player.level();
        int stage = data.masteryStage(power);
        switch (power) {
            case 1 -> {
                LivingEntity target = findTarget(player, 14.0 + stage, 1.8);
                if (target == null) {
                    player.sendSystemMessage(Component.literal("Manyetik Çekim için nişangâhında bir hedef olmalı."));
                    return false;
                }
                Vec3 start = player.getEyePosition().add(player.getLookAngle().normalize().scale(0.6));
                Vec3 end = target.getEyePosition();
                spawnLine(level, start, end, new Item[]{Items.IRON_NUGGET, Items.IRON_INGOT, Items.COPPER_INGOT}, 12, now + 22L, 0.35);
                int metal = metalArmorPieces(target);
                double pull = 1.05 + stage * 0.12 + metal * 0.18 + (awakened ? 0.55 : 0.0);
                Vec3 toward = player.position().add(0.0, 0.7, 0.0).subtract(target.position());
                if (toward.lengthSqr() > 0.001) {
                    toward = toward.normalize().scale(pull);
                    target.setDeltaMovement(toward.x, Math.max(0.25, toward.y + 0.18), toward.z);
                    target.hurtMarked = true;
                }
                target.hurtServer(level, level.damageSources().playerAttack(player), 5.0F + stage + metal * 0.7F);
                level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.9F, 0.55F);
                ServerNetworking.sendScreenShake(level, target.position(), 18.0, 0.45F, 7);
                data.setCooldown(1, now, Math.max(150, 240 - stage * 20));
                return true;
            }
            case 2 -> {
                double radius = 7.5 + stage * 0.65 + (awakened ? 2.0 : 0.0);
                spawnVisibleRing(level, player.position().add(0.0, 0.9, 0.0),
                    new Item[]{Items.IRON_BLOCK, Items.COPPER_BLOCK, Items.IRON_INGOT}, 14, radius * 0.42, now + 30L, 0.18);
                for (LivingEntity target : nearby(player, radius)) {
                    if (target == player || PowerSystem.isProtectedAlly(player, target)) continue;
                    Vec3 push = horizontal(target.position().subtract(player.position()));
                    if (push.lengthSqr() < 0.001) push = new Vec3(1.0, 0.0, 0.0);
                    push = push.normalize().scale(1.25 + stage * 0.16 + (awakened ? 0.55 : 0.0));
                    target.push(push.x, 0.55 + stage * 0.06, push.z);
                    target.hurtServer(level, level.damageSources().playerAttack(player), 6.0F + stage * 1.2F);
                }
                for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(radius))) {
                    if (projectile.getOwner() == player) continue;
                    projectile.setOwner(player);
                    projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-1.18));
                    projectile.hurtMarked = true;
                }
                level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(), 36, radius * 0.35, 0.8, radius * 0.35, 0.08);
                level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.15F, 0.58F);
                ServerNetworking.sendScreenShake(level, player.position(), 24.0, 0.78F, 10);
                data.setCooldown(2, now, Math.max(230, 360 - stage * 30));
                return true;
            }
            case 3 -> {
                Vec3 start = player.getEyePosition().add(player.getLookAngle().normalize().scale(0.8));
                Vec3 velocity = player.getLookAngle().normalize().scale(0.88 + stage * 0.06 + (awakened ? 0.22 : 0.0));
                MOVING_ATTACKS.add(createMovingAttack(level, player.getUUID(), MovingType.IRON_FIST,
                    start, velocity, now + 34L, 12.0F + stage * 1.8F, 2.25 + stage * 0.12, 1,
                    new Item[]{Items.IRON_BLOCK, Items.ANVIL, Items.IRON_INGOT, Items.COPPER_INGOT}, 10));
                level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.1F, 0.42F);
                data.setCooldown(3, now, Math.max(290, 440 - stage * 35));
                return true;
            }
            case 4 -> {
                MagneticStorm active = MAGNETIC_STORMS.remove(player.getUUID());
                if (active != null) {
                    launchMagneticStorm(player, data, active, now, awakened);
                    return false;
                }
                List<UUID> ids = spawnVisualItems(level, player.position().add(0.0, 1.0, 0.0),
                    new Item[]{Items.IRON_INGOT, Items.COPPER_INGOT, Items.IRON_NUGGET, Items.CHAIN}, 12, true);
                MAGNETIC_STORMS.put(player.getUUID(), new MagneticStorm(level, player.getUUID(), ids, now, now + 180L, stage));
                data.setCooldown(4, now, 1);
                player.sendSystemMessage(Component.literal("Metal Fırtınası hazır. Tekrar R ile nişangâhına fırlat."));
                level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.9F, 1.45F);
                return true;
            }
            case 5 -> {
                Vec3 start = player.getEyePosition().add(player.getLookAngle().normalize().scale(1.0));
                Vec3 velocity = player.getLookAngle().normalize().scale(1.75 + stage * 0.10 + (awakened ? 0.32 : 0.0));
                MOVING_ATTACKS.add(createMovingAttack(level, player.getUUID(), MovingType.RAILGUN,
                    start, velocity, now + 42L, 14.0F + stage * 2.1F, 1.25 + stage * 0.08, 4,
                    new Item[]{Items.IRON_BLOCK, Items.REDSTONE, Items.COPPER_INGOT, Items.IRON_NUGGET}, 9));
                spawnLine(level, start, start.add(player.getLookAngle().normalize().scale(3.0)),
                    new Item[]{Items.REDSTONE, Items.IRON_NUGGET}, 8, now + 12L, 0.05);
                level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2F, 1.55F);
                ServerNetworking.sendScreenShake(level, player.position(), 22.0, 0.82F, 8);
                data.setCooldown(5, now, Math.max(560, 780 - stage * 45));
                return true;
            }
            case 6 -> {
                LivingEntity target = findTarget(player, 17.0 + stage, 2.0);
                if (target == null) {
                    player.sendSystemMessage(Component.literal("Manyetik Kafes için nişangâhında bir hedef olmalı."));
                    return false;
                }
                List<UUID> ids = spawnVisualItems(level, target.position().add(0.0, 1.0, 0.0),
                    new Item[]{Items.IRON_BARS, Items.CHAIN, Items.LODESTONE, Items.IRON_BLOCK}, 18, true);
                MAGNETIC_CAGES.add(new MagneticCage(level, player.getUUID(), target.getUUID(), ids,
                    target.position(), now, now + (awakened ? 130L : 100L), stage, awakened));
                level.playSound(null, target.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.9F, 0.62F);
                data.setCooldown(6, now, Math.max(980, 1320 - stage * 70));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public static boolean useSand(ServerPlayer player, PlayerPowerData data, int power, long now, boolean awakened) {
        ServerLevel level = (ServerLevel) player.level();
        int stage = data.masteryStage(power);
        switch (power) {
            case 1 -> {
                Vec3 start = player.getEyePosition().add(player.getLookAngle().normalize().scale(0.75));
                Vec3 velocity = player.getLookAngle().normalize().scale(0.92 + stage * 0.06 + (awakened ? 0.18 : 0.0));
                MOVING_ATTACKS.add(createMovingAttack(level, player.getUUID(), MovingType.SAND_SHOT,
                    start, velocity, now + 48L, 7.0F + stage * 1.25F, 1.55 + stage * 0.10, 1,
                    sandItems(), awakened ? 11 : 8));
                level.playSound(null, player.blockPosition(), SoundEvents.GRASS_BREAK, SoundSource.PLAYERS, 1.0F, 0.65F);
                data.setCooldown(1, now, Math.max(130, 210 - stage * 18));
                return true;
            }
            case 2 -> {
                Vec3 direction = horizontal(player.getLookAngle()).normalize();
                Vec3 start = ground(level, player.position().add(direction.scale(1.2)));
                List<UUID> ids = spawnVisualItems(level, start.add(0.0, 0.45, 0.0), sandItems(), awakened ? 11 : 8, false);
                SAND_WAVES.add(new SandWave(level, player.getUUID(), ids, start, direction, now,
                    now + (awakened ? 34L : 28L), stage, awakened));
                level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7F, 1.45F);
                data.setCooldown(2, now, Math.max(230, 360 - stage * 28));
                return true;
            }
            case 3 -> {
                DesertMirrors previous = DESERT_MIRRORS.remove(player.getUUID());
                if (previous != null) discardAll(previous.level, previous.allIds());
                Vec3 forward = horizontal(player.getLookAngle()).normalize();
                Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
                List<UUID> left = spawnSandStatue(level, player.position().add(right.scale(-1.2)), now + 130L);
                List<UUID> rightIds = spawnSandStatue(level, player.position().add(right.scale(1.2)), now + 130L);
                DESERT_MIRRORS.put(player.getUUID(), new DesertMirrors(level, player.getUUID(), left, rightIds,
                    player.position(), forward.add(right.scale(-0.8)).normalize().scale(0.15),
                    forward.add(right.scale(0.8)).normalize().scale(0.15), now, now + (awakened ? 150L : 120L), awakened ? 3 : 2));
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, awakened ? 90 : 65, awakened ? 2 : 1, false, false, true));
                level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.75F, 1.55F);
                data.setCooldown(3, now, Math.max(480, 680 - stage * 42));
                return true;
            }
            case 4 -> {
                SandArmor previous = SAND_ARMORS.remove(player.getUUID());
                if (previous != null) discardAll(previous.level, previous.ids);
                int charges = 4 + (stage >= 2 ? 1 : 0) + (awakened ? 2 : 0);
                List<UUID> ids = spawnVisualItems(level, player.position().add(0.0, 1.0, 0.0), sandItems(), charges * 2, false);
                SAND_ARMORS.put(player.getUUID(), new SandArmor(level, player.getUUID(), ids, now + (awakened ? 300L : 220L), charges));
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, awakened ? 300 : 220, 0, false, false, true));
                level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 0.82F);
                data.setCooldown(4, now, Math.max(650, 900 - stage * 55));
                return true;
            }
            case 5 -> {
                LivingEntity target = findTarget(player, 16.0 + stage, 2.0);
                if (target == null) {
                    player.sendSystemMessage(Component.literal("Kum Mezarı için nişangâhında bir hedef olmalı."));
                    return false;
                }
                List<UUID> ids = spawnVisualItems(level, target.position().add(0.0, 0.45, 0.0), sandItems(), awakened ? 18 : 14, false);
                SAND_GRAVES.add(new SandGrave(level, player.getUUID(), target.getUUID(), ids, target.position(), now,
                    now + (awakened ? 110L : 82L), stage, awakened));
                if (target instanceof ServerPlayer targetPlayer) ServerNetworking.sendSandScreen(targetPlayer, 80);
                level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.85F, 0.58F);
                data.setCooldown(5, now, Math.max(620, 860 - stage * 50));
                return true;
            }
            case 6 -> {
                LivingEntity target = findTarget(player, 18.0 + stage, 2.2);
                Vec3 impact = target == null
                    ? ground(level, player.position().add(horizontal(player.getLookAngle()).normalize().scale(8.0)))
                    : target.position();
                for (int arm = 0; arm < 2; arm++) {
                    Vec3 side = new Vec3(-player.getLookAngle().z, 0.0, player.getLookAngle().x).normalize().scale(arm == 0 ? -3.2 : 3.2);
                    Vec3 start = ground(level, impact.add(side)).add(0.0, 0.4, 0.0);
                    List<UUID> ids = spawnVisualItems(level, start, sandItems(), awakened ? 12 : 9, false);
                    SAND_GIANT_ARMS.add(new SandGiantArm(level, player.getUUID(), target == null ? null : target.getUUID(), ids,
                        start, impact.add(0.0, 1.0, 0.0), now + arm * 7L, now + 24L + arm * 7L, arm, stage, awakened));
                }
                level.playSound(null, BlockPos.containing(impact), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.9F, 1.45F);
                data.setCooldown(6, now, Math.max(1050, 1450 - stage * 80));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public static boolean executeCopiedPower(ServerPlayer player, PlayerPowerData data, PowerClass powerClass,
                                             int power, long now, boolean charged) {
        return switch (powerClass) {
            case MAGNETIC -> useMagnetic(player, data, power, now, charged);
            case SAND -> useSand(player, data, power, now, charged);
            default -> false;
        };
    }

    public static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
        if (internalDamage || amount <= 0.0F || !(victim instanceof ServerPlayer player)) return true;
        long now = player.level().getGameTime();

        DesertMirrors mirrors = DESERT_MIRRORS.get(player.getUUID());
        if (mirrors != null && mirrors.endTick > now && mirrors.dodges > 0) {
            mirrors.dodges--;
            List<UUID> broken = mirrors.dodges % 2 == 0 ? mirrors.leftIds : mirrors.rightIds;
            discardAll(mirrors.level, broken);
            broken.clear();
            Vec3 escape = safeOffset(mirrors.level, player.position(), horizontal(player.getLookAngle()).scale(-2.6));
            player.setPos(escape.x, escape.y, escape.z);
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            burstSand(mirrors.level, player.position().add(0.0, 0.8, 0.0), 1.8, 34);
            if (source.getEntity() instanceof ServerPlayer attacker) ServerNetworking.sendSandScreen(attacker, 36);
            if (mirrors.dodges <= 0) {
                discardAll(mirrors.level, mirrors.allIds());
                DESERT_MIRRORS.remove(player.getUUID());
            }
            return false;
        }

        SandArmor armor = SAND_ARMORS.get(player.getUUID());
        if (armor != null && armor.endTick > now && armor.charges > 0) {
            armor.charges--;
            if (!armor.ids.isEmpty()) {
                int removeCount = Math.min(2, armor.ids.size());
                for (int i = 0; i < removeCount; i++) {
                    UUID id = armor.ids.remove(armor.ids.size() - 1);
                    Entity entity = armor.level.getEntity(id);
                    if (entity != null) entity.discard();
                }
            }
            burstSand(armor.level, player.position().add(0.0, 1.0, 0.0), 1.4, 24);
            try {
                internalDamage = true;
                player.hurtServer(armor.level, source, amount * 0.42F);
            } finally {
                internalDamage = false;
            }
            if (armor.charges <= 0) {
                discardAll(armor.level, armor.ids);
                SAND_ARMORS.remove(player.getUUID());
                player.sendSystemMessage(Component.literal("Kum Zırhın parçalandı."));
            }
            return false;
        }
        return true;
    }

    public static void tickAwakening(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        if (data.powerClass() == PowerClass.MAGNETIC) {
            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 30, 2, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 2, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 1, false, false, true));
            for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(9.0))) {
                if (projectile.getOwner() == player) continue;
                Vec3 toPlayer = player.getEyePosition().subtract(projectile.position());
                if (toPlayer.lengthSqr() > 0.01) projectile.setDeltaMovement(projectile.getDeltaMovement().scale(0.58).add(toPlayer.normalize().scale(-0.18)));
            }
            for (LivingEntity target : nearby(player, 9.5)) {
                if (target == player || PowerSystem.isProtectedAlly(player, target)) continue;
                Vec3 pull = player.position().subtract(target.position());
                if (pull.lengthSqr() > 1.0) target.setDeltaMovement(target.getDeltaMovement().add(pull.normalize().scale(0.045)));
            }
            if (now % 18L == 0L) {
                spawnVisibleRing(level, player.position().add(0.0, 1.0, 0.0),
                    new Item[]{Items.IRON_BLOCK, Items.COPPER_BLOCK, Items.CHAIN, Items.LODESTONE}, 12, 2.2, now + 24L, 0.35);
            }
        } else if (data.powerClass() == PowerClass.SAND) {
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 2, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 2, false, false, true));
            for (LivingEntity target : nearby(player, 10.0)) {
                if (target == player || PowerSystem.isProtectedAlly(player, target)) continue;
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 35, 2, false, false, true));
                if (target instanceof ServerPlayer targetPlayer && now % 20L == 0L) ServerNetworking.sendSandScreen(targetPlayer, 35);
            }
            for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(8.5))) {
                if (projectile.getOwner() == player) continue;
                Vec3 outward = horizontal(projectile.position().subtract(player.position()));
                if (outward.lengthSqr() < 0.001) outward = new Vec3(1.0, 0.0, 0.0);
                projectile.setDeltaMovement(projectile.getDeltaMovement().scale(0.45).add(outward.normalize().scale(0.28)));
            }
            if (now % 20L == 0L) {
                spawnVisibleRing(level, player.position().add(0.0, 0.65, 0.0), sandItems(), 14, 2.6, now + 28L, -0.22);
            }
        }
    }

    public static void finishAwakening(ServerPlayer player, ServerLevel level, PowerClass powerClass) {
        if (powerClass == PowerClass.MAGNETIC) {
            double radius = 13.0;
            spawnVisibleRing(level, player.position().add(0.0, 1.0, 0.0),
                new Item[]{Items.IRON_BLOCK, Items.COPPER_BLOCK, Items.LODESTONE, Items.CHAIN}, 20, 4.2, level.getGameTime() + 40L, 0.55);
            for (LivingEntity target : nearby(player, radius)) {
                if (target == player || PowerSystem.isProtectedAlly(player, target)) continue;
                target.hurtServer(level, level.damageSources().playerAttack(player), 19.0F);
                Vec3 push = horizontal(target.position().subtract(player.position())).normalize().scale(1.8);
                target.push(push.x, 0.85, push.z);
            }
        } else if (powerClass == PowerClass.SAND) {
            double radius = 12.0;
            spawnVisibleRing(level, player.position().add(0.0, 0.55, 0.0), sandItems(), 24, 4.5, level.getGameTime() + 42L, 0.32);
            for (LivingEntity target : nearby(player, radius)) {
                if (target == player || PowerSystem.isProtectedAlly(player, target)) continue;
                target.hurtServer(level, level.damageSources().playerAttack(player), 17.0F);
                Vec3 push = horizontal(target.position().subtract(player.position())).normalize().scale(1.55);
                target.push(push.x, 0.75, push.z);
                if (target instanceof ServerPlayer targetPlayer) ServerNetworking.sendSandScreen(targetPlayer, 80);
            }
            burstSand(level, player.position().add(0.0, 0.8, 0.0), radius, 100);
        }
    }

    public static void cancelOwner(UUID ownerId) {
        MagneticStorm storm = MAGNETIC_STORMS.remove(ownerId);
        if (storm != null) discardAll(storm.level, storm.ids);
        SandArmor armor = SAND_ARMORS.remove(ownerId);
        if (armor != null) discardAll(armor.level, armor.ids);
        DesertMirrors mirrors = DESERT_MIRRORS.remove(ownerId);
        if (mirrors != null) discardAll(mirrors.level, mirrors.allIds());
        MOVING_ATTACKS.removeIf(attack -> {
            if (!attack.ownerId.equals(ownerId)) return false;
            discardAll(attack.level, attack.ids);
            return true;
        });
        SAND_WAVES.removeIf(wave -> {
            if (!wave.ownerId.equals(ownerId)) return false;
            discardAll(wave.level, wave.ids);
            return true;
        });
    }

    public static void clearAll() {
        for (StaticVisual visual : STATIC_VISUALS) {
            Entity entity = visual.level.getEntity(visual.entityId);
            if (entity != null) entity.discard();
        }
        for (MovingAttack attack : MOVING_ATTACKS) discardAll(attack.level, attack.ids);
        for (SandWave wave : SAND_WAVES) discardAll(wave.level, wave.ids);
        for (MagneticCage cage : MAGNETIC_CAGES) discardAll(cage.level, cage.ids);
        for (SandGrave grave : SAND_GRAVES) discardAll(grave.level, grave.ids);
        for (SandGiantArm arm : SAND_GIANT_ARMS) discardAll(arm.level, arm.ids);
        for (MagneticStorm storm : MAGNETIC_STORMS.values()) discardAll(storm.level, storm.ids);
        for (SandArmor armor : SAND_ARMORS.values()) discardAll(armor.level, armor.ids);
        for (DesertMirrors mirrors : DESERT_MIRRORS.values()) discardAll(mirrors.level, mirrors.allIds());
        STATIC_VISUALS.clear();
        MOVING_ATTACKS.clear();
        SAND_WAVES.clear();
        MAGNETIC_CAGES.clear();
        SAND_GRAVES.clear();
        SAND_GIANT_ARMS.clear();
        MAGNETIC_STORMS.clear();
        SAND_ARMORS.clear();
        DESERT_MIRRORS.clear();
    }

    public static void handleDisconnect(ServerPlayer player) {
        cancelOwner(player.getUUID());
    }

    private static void launchMagneticStorm(ServerPlayer player, PlayerPowerData data, MagneticStorm storm, long now, boolean awakened) {
        Vec3 center = player.getEyePosition().add(player.getLookAngle().normalize().scale(0.8));
        for (UUID id : storm.ids) {
            Entity entity = storm.level.getEntity(id);
            if (entity != null) entity.setPos(center.x, center.y, center.z);
        }
        MOVING_ATTACKS.add(new MovingAttack(storm.level, player.getUUID(), MovingType.METAL_STORM, storm.ids,
            center, player.getLookAngle().normalize().scale(1.05 + storm.stage * 0.06 + (awakened ? 0.25 : 0.0)),
            now + 52L, 13.0F + storm.stage * 1.8F, 3.0 + storm.stage * 0.15, 5));
        data.setCooldown(4, now, Math.max(430, 620 - storm.stage * 40));
        player.sendSystemMessage(Component.literal("Metal Fırtınası fırlatıldı!"));
        storm.level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.1F, 0.72F);
        PlayerDataStore.markDirty();
        ServerNetworking.sync(player);
    }

    private static MovingAttack createMovingAttack(ServerLevel level, UUID ownerId, MovingType type,
                                                    Vec3 start, Vec3 velocity, long expireTick,
                                                    float damage, double radius, int hitLimit,
                                                    Item[] items, int count) {
        return new MovingAttack(level, ownerId, type, spawnVisualItems(level, start, items, count, true),
            start, velocity, expireTick, damage, radius, hitLimit);
    }

    private static void tickMovingAttacks() {
        Iterator<MovingAttack> iterator = MOVING_ATTACKS.iterator();
        while (iterator.hasNext()) {
            MovingAttack attack = iterator.next();
            long now = attack.level.getGameTime();
            ServerPlayer owner = attack.level.getServer().getPlayerList().getPlayer(attack.ownerId);
            if (owner == null || now >= attack.expireTick) {
                if (attack.type == MovingType.SAND_SHOT) burstSand(attack.level, attack.center, attack.radius, 22);
                discardAll(attack.level, attack.ids);
                iterator.remove();
                continue;
            }
            Vec3 next = attack.center.add(attack.velocity);
            BlockState state = attack.level.getBlockState(BlockPos.containing(next));
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                impactMovingAttack(owner, attack, null);
                discardAll(attack.level, attack.ids);
                iterator.remove();
                continue;
            }
            attack.center = next;
            positionCluster(attack.level, attack.ids, attack.center, now, attack.type == MovingType.RAILGUN ? 0.42 : 0.72);

            AABB hitBox = new AABB(attack.center, attack.center).inflate(attack.radius);
            for (LivingEntity target : attack.level.getEntitiesOfClass(LivingEntity.class, hitBox)) {
                if (target == owner || PowerSystem.isProtectedAlly(owner, target) || !target.isAlive() || attack.hitTargets.contains(target.getUUID())) continue;
                attack.hitTargets.add(target.getUUID());
                attack.hits++;
                impactMovingAttack(owner, attack, target);
                if (attack.hits >= attack.hitLimit || attack.type == MovingType.SAND_SHOT || attack.type == MovingType.IRON_FIST) {
                    discardAll(attack.level, attack.ids);
                    iterator.remove();
                    break;
                }
            }
        }
    }

    private static void impactMovingAttack(ServerPlayer owner, MovingAttack attack, LivingEntity directTarget) {
        if (attack.type == MovingType.SAND_SHOT) {
            for (LivingEntity target : attack.level.getEntitiesOfClass(LivingEntity.class,
                new AABB(attack.center, attack.center).inflate(attack.radius + 0.8))) {
                if (target == owner || PowerSystem.isProtectedAlly(owner, target)) continue;
                target.hurtServer(attack.level, attack.level.damageSources().playerAttack(owner), attack.damage);
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 65, 1, false, true, true));
                if (target instanceof ServerPlayer player) ServerNetworking.sendSandScreen(player, 80);
                Vec3 push = horizontal(target.position().subtract(attack.center));
                if (push.lengthSqr() > 0.001) target.push(push.normalize().x * 0.75, 0.28, push.normalize().z * 0.75);
            }
            burstSand(attack.level, attack.center, attack.radius + 1.1, 42);
            attack.level.playSound(null, BlockPos.containing(attack.center), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.75F, 1.18F);
        } else {
            if (directTarget != null) {
                directTarget.hurtServer(attack.level, attack.level.damageSources().playerAttack(owner), attack.damage);
                Vec3 push = horizontal(attack.velocity).normalize().scale(attack.type == MovingType.RAILGUN ? 1.5 : 1.05);
                directTarget.push(push.x, attack.type == MovingType.IRON_FIST ? 0.72 : 0.38, push.z);
            }
            if (attack.type == MovingType.IRON_FIST) {
                for (LivingEntity target : attack.level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(attack.center, attack.center).inflate(attack.radius))) {
                    if (target == owner || target == directTarget || PowerSystem.isProtectedAlly(owner, target)) continue;
                    target.hurtServer(attack.level, attack.level.damageSources().playerAttack(owner), attack.damage * 0.55F);
                    Vec3 push = horizontal(target.position().subtract(attack.center));
                    if (push.lengthSqr() > 0.001) target.push(push.normalize().x, 0.5, push.normalize().z);
                }
            }
            attack.level.sendParticles(ParticleTypes.CRIT, attack.center.x, attack.center.y, attack.center.z,
                attack.type == MovingType.RAILGUN ? 26 : 38, 0.65, 0.55, 0.65, 0.10);
            ServerNetworking.sendScreenShake(attack.level, attack.center, 18.0, attack.type == MovingType.RAILGUN ? 0.82F : 0.65F, 8);
        }
    }

    private static void tickSandWaves() {
        Iterator<SandWave> iterator = SAND_WAVES.iterator();
        while (iterator.hasNext()) {
            SandWave wave = iterator.next();
            long now = wave.level.getGameTime();
            ServerPlayer owner = wave.level.getServer().getPlayerList().getPlayer(wave.ownerId);
            if (owner == null || now >= wave.endTick) {
                discardAll(wave.level, wave.ids);
                iterator.remove();
                continue;
            }
            double elapsed = now - wave.startTick;
            Vec3 center = ground(wave.level, wave.start.add(wave.direction.scale(elapsed * (wave.awakened ? 0.82 : 0.68))));
            Vec3 right = new Vec3(-wave.direction.z, 0.0, wave.direction.x);
            for (int i = 0; i < wave.ids.size(); i++) {
                Entity entity = wave.level.getEntity(wave.ids.get(i));
                if (entity == null) continue;
                double side = (i - (wave.ids.size() - 1) / 2.0) * 0.72;
                double crest = Math.max(0.0, 1.35 - Math.abs(side) * 0.18) + Math.sin(now * 0.48 + i) * 0.16;
                Vec3 pos = center.add(right.scale(side)).add(0.0, 0.35 + crest, 0.0);
                entity.setPos(pos.x, pos.y, pos.z);
            }
            AABB box = new AABB(center, center).inflate(3.6 + wave.stage * 0.25, 2.3, 3.6 + wave.stage * 0.25);
            for (LivingEntity target : wave.level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (target == owner || PowerSystem.isProtectedAlly(owner, target) || !wave.hitTargets.add(target.getUUID())) continue;
                target.hurtServer(wave.level, wave.level.damageSources().playerAttack(owner), 8.0F + wave.stage * 1.3F);
                target.push(wave.direction.x * (1.1 + wave.stage * 0.12), 0.48, wave.direction.z * (1.1 + wave.stage * 0.12));
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 1, false, true, true));
                if (target instanceof ServerPlayer player) ServerNetworking.sendSandScreen(player, 80);
            }
            if (now % 4L == 0L) wave.level.sendParticles(ParticleTypes.POOF, center.x, center.y + 0.55, center.z, 8, 2.1, 0.55, 2.1, 0.025);
        }
    }

    private static void tickMagneticStorms(MinecraftServer server) {
        Iterator<MagneticStorm> iterator = MAGNETIC_STORMS.values().iterator();
        while (iterator.hasNext()) {
            MagneticStorm storm = iterator.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(storm.ownerId);
            long now = storm.level.getGameTime();
            if (owner == null || owner.level() != storm.level) {
                discardAll(storm.level, storm.ids);
                iterator.remove();
                continue;
            }
            if (now >= storm.endTick) {
                iterator.remove();
                launchMagneticStorm(owner, PlayerDataStore.get(owner.getUUID()), storm, now, false);
                continue;
            }
            positionOrbit(storm.level, storm.ids, owner.position().add(0.0, 1.05, 0.0), now, 2.0, 0.75);
            for (Projectile projectile : storm.level.getEntitiesOfClass(Projectile.class, owner.getBoundingBox().inflate(3.2))) {
                if (projectile.getOwner() == owner) continue;
                projectile.setDeltaMovement(projectile.getDeltaMovement().scale(0.35));
            }
        }
    }

    private static void tickMagneticCages(MinecraftServer server) {
        Iterator<MagneticCage> iterator = MAGNETIC_CAGES.iterator();
        while (iterator.hasNext()) {
            MagneticCage cage = iterator.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(cage.ownerId);
            Entity raw = cage.level.getEntity(cage.targetId);
            long now = cage.level.getGameTime();
            if (owner == null || !(raw instanceof LivingEntity target) || !target.isAlive()) {
                discardAll(cage.level, cage.ids);
                iterator.remove();
                continue;
            }
            if (now >= cage.endTick) {
                target.hurtServer(cage.level, cage.level.damageSources().playerAttack(owner), 15.0F + cage.stage * 1.7F);
                target.setDeltaMovement(0.0, -1.1, 0.0);
                target.hurtMarked = true;
                cage.level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(), 55, 0.9, 1.1, 0.9, 0.12);
                ServerNetworking.sendScreenShake(cage.level, target.position(), 22.0, 1.0F, 11);
                discardAll(cage.level, cage.ids);
                iterator.remove();
                continue;
            }
            cage.center = cage.center.scale(0.85).add(target.position().scale(0.15));
            positionCage(cage.level, cage.ids, cage.center.add(0.0, 1.0, 0.0), now, cage.awakened ? 2.4 : 1.85);
            Vec3 offset = target.position().subtract(cage.center);
            if (offset.lengthSqr() > 1.6 * 1.6) {
                Vec3 pull = offset.normalize().scale(-0.34);
                target.setDeltaMovement(target.getDeltaMovement().scale(0.35).add(pull));
                target.hurtMarked = true;
            }
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 5, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 10, 1, false, false, true));
            for (Projectile projectile : cage.level.getEntitiesOfClass(Projectile.class, target.getBoundingBox().inflate(3.0))) {
                if (projectile.getOwner() == owner) continue;
                Vec3 toCenter = cage.center.add(0.0, 1.0, 0.0).subtract(projectile.position());
                projectile.setDeltaMovement(projectile.getDeltaMovement().scale(0.45).add(toCenter.normalize().scale(0.18)));
            }
        }
    }

    private static void tickSandArmors(MinecraftServer server) {
        Iterator<SandArmor> iterator = SAND_ARMORS.values().iterator();
        while (iterator.hasNext()) {
            SandArmor armor = iterator.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(armor.ownerId);
            long now = armor.level.getGameTime();
            if (owner == null || owner.level() != armor.level || now >= armor.endTick || armor.charges <= 0) {
                discardAll(armor.level, armor.ids);
                iterator.remove();
                continue;
            }
            positionOrbit(armor.level, armor.ids, owner.position().add(0.0, 1.0, 0.0), now, 1.35, 0.95);
        }
    }

    private static void tickDesertMirrors(MinecraftServer server) {
        Iterator<DesertMirrors> iterator = DESERT_MIRRORS.values().iterator();
        while (iterator.hasNext()) {
            DesertMirrors mirrors = iterator.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(mirrors.ownerId);
            long now = mirrors.level.getGameTime();
            if (owner == null || owner.level() != mirrors.level || now >= mirrors.endTick || mirrors.dodges <= 0) {
                discardAll(mirrors.level, mirrors.allIds());
                iterator.remove();
                continue;
            }
            double elapsed = now - mirrors.startTick;
            updateSandStatue(mirrors.level, mirrors.leftIds, mirrors.origin.add(mirrors.leftVelocity.scale(elapsed)), now);
            updateSandStatue(mirrors.level, mirrors.rightIds, mirrors.origin.add(mirrors.rightVelocity.scale(elapsed)), now + 7L);
        }
    }

    private static void tickSandGraves(MinecraftServer server) {
        Iterator<SandGrave> iterator = SAND_GRAVES.iterator();
        while (iterator.hasNext()) {
            SandGrave grave = iterator.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(grave.ownerId);
            Entity raw = grave.level.getEntity(grave.targetId);
            long now = grave.level.getGameTime();
            if (owner == null || !(raw instanceof LivingEntity target) || !target.isAlive()) {
                discardAll(grave.level, grave.ids);
                iterator.remove();
                continue;
            }
            if (target.isInWater() || now >= grave.endTick) {
                if (now >= grave.endTick) {
                    target.hurtServer(grave.level, grave.level.damageSources().playerAttack(owner), 8.0F + grave.stage * 1.3F);
                    burstSand(grave.level, target.position(), 3.1, 50);
                }
                discardAll(grave.level, grave.ids);
                iterator.remove();
                continue;
            }
            grave.center = grave.center.scale(0.92).add(target.position().scale(0.08));
            positionSandGrave(grave.level, grave.ids, grave.center, now, grave.awakened ? 2.7 : 2.15);
            target.setDeltaMovement(target.getDeltaMovement().multiply(0.12, 0.0, 0.12));
            target.hurtMarked = true;
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 12, 6, false, false, true));
            if (now % 20L == 0L) {
                target.hurtServer(grave.level, grave.level.damageSources().playerAttack(owner), 1.5F + grave.stage * 0.35F);
                if (target instanceof ServerPlayer player) ServerNetworking.sendSandScreen(player, 80);
            }
        }
    }

    private static void tickSandGiantArms(MinecraftServer server) {
        Iterator<SandGiantArm> iterator = SAND_GIANT_ARMS.iterator();
        while (iterator.hasNext()) {
            SandGiantArm arm = iterator.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(arm.ownerId);
            long now = arm.level.getGameTime();
            Entity raw = arm.targetId == null ? null : arm.level.getEntity(arm.targetId);
            Vec3 end = raw instanceof LivingEntity living && living.isAlive() ? living.getEyePosition() : arm.fixedEnd;
            if (owner == null || now >= arm.endTick) {
                if (owner != null) impactSandGiant(owner, arm, raw instanceof LivingEntity living ? living : null, end);
                discardAll(arm.level, arm.ids);
                iterator.remove();
                continue;
            }
            if (now < arm.startTick) continue;
            double progress = Math.max(0.0, Math.min(1.0, (now - arm.startTick) / (double) Math.max(1L, arm.endTick - arm.startTick)));
            for (int i = 0; i < arm.ids.size(); i++) {
                Entity entity = arm.level.getEntity(arm.ids.get(i));
                if (entity == null) continue;
                double segment = (i + 1.0) / arm.ids.size();
                double t = segment * (1.0 - Math.pow(1.0 - progress, 3.0));
                Vec3 pos = arm.start.add(end.subtract(arm.start).scale(t));
                double arch = Math.sin(Math.PI * t) * (2.2 + arm.armIndex * 0.35);
                pos = pos.add(0.0, arch, 0.0);
                entity.setPos(pos.x, pos.y, pos.z);
            }
        }
    }

    private static void impactSandGiant(ServerPlayer owner, SandGiantArm arm, LivingEntity direct, Vec3 impact) {
        if (direct != null) {
            direct.hurtServer(arm.level, arm.level.damageSources().playerAttack(owner), 10.0F + arm.stage * 1.5F);
            if (arm.armIndex == 0) {
                direct.setDeltaMovement(0.0, 1.05 + arm.stage * 0.08, 0.0);
            } else {
                direct.setDeltaMovement(0.0, -1.45, 0.0);
                if (direct instanceof ServerPlayer directPlayer) ServerNetworking.sendSandScreen(directPlayer, 80);
            }
            direct.hurtMarked = true;
        }
        if (arm.armIndex == 1 || direct == null) {
            for (LivingEntity target : arm.level.getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact).inflate(5.0 + arm.stage * 0.3))) {
                if (target == owner || PowerSystem.isProtectedAlly(owner, target)) continue;
                target.hurtServer(arm.level, arm.level.damageSources().playerAttack(owner), 9.0F + arm.stage * 1.2F);
                Vec3 push = horizontal(target.position().subtract(impact));
                if (push.lengthSqr() > 0.001) target.push(push.normalize().x * 1.2, 0.65, push.normalize().z * 1.2);
                if (target instanceof ServerPlayer player) ServerNetworking.sendSandScreen(player, 80);
            }
            burstSand(arm.level, impact, 5.2, 80);
            ServerNetworking.sendScreenShake(arm.level, impact, 30.0, 1.35F, 15);
            arm.level.playSound(null, BlockPos.containing(impact), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.25F, 0.55F);
        }
    }

    private static void tickStaticVisuals() {
        Iterator<StaticVisual> iterator = STATIC_VISUALS.iterator();
        while (iterator.hasNext()) {
            StaticVisual visual = iterator.next();
            Entity entity = visual.level.getEntity(visual.entityId);
            if (entity == null || entity.isRemoved() || visual.level.getGameTime() >= visual.expireTick) {
                if (entity != null) entity.discard();
                iterator.remove();
            }
        }
    }

    private static ItemEntity createVisual(ServerLevel level, Item item, Vec3 position, boolean glowing) {
        ItemEntity visual = new ItemEntity(level, position.x, position.y, position.z, new ItemStack(item));
        visual.setNoGravity(true);
        visual.setNeverPickUp();
        visual.setUnlimitedLifetime();
        visual.setInvulnerable(true);
        visual.setGlowingTag(glowing);
        visual.setDeltaMovement(Vec3.ZERO);
        return level.addFreshEntity(visual) ? visual : null;
    }

    private static List<UUID> spawnVisualItems(ServerLevel level, Vec3 center, Item[] items, int count, boolean glowing) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ItemEntity visual = createVisual(level, items[i % items.length], center, glowing);
            if (visual != null) ids.add(visual.getUUID());
        }
        return ids;
    }

    private static void spawnVisibleRing(ServerLevel level, Vec3 center, Item[] items, int count,
                                         double radius, long expireTick, double verticalWave) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            Vec3 pos = center.add(Math.cos(angle) * radius, Math.sin(angle * 2.0) * verticalWave, Math.sin(angle) * radius);
            ItemEntity visual = createVisual(level, items[i % items.length], pos, true);
            if (visual != null) STATIC_VISUALS.add(new StaticVisual(level, visual.getUUID(), expireTick));
        }
    }

    private static void spawnLine(ServerLevel level, Vec3 start, Vec3 end, Item[] items, int count,
                                  long expireTick, double arc) {
        for (int i = 0; i < count; i++) {
            double t = (i + 1.0) / (count + 1.0);
            Vec3 pos = start.add(end.subtract(start).scale(t)).add(0.0, Math.sin(Math.PI * t) * arc, 0.0);
            ItemEntity visual = createVisual(level, items[i % items.length], pos, true);
            if (visual != null) STATIC_VISUALS.add(new StaticVisual(level, visual.getUUID(), expireTick));
        }
    }

    private static void positionCluster(ServerLevel level, List<UUID> ids, Vec3 center, long now, double radius) {
        for (int i = 0; i < ids.size(); i++) {
            Entity entity = level.getEntity(ids.get(i));
            if (entity == null) continue;
            if (i == 0) {
                entity.setPos(center.x, center.y, center.z);
                continue;
            }
            double angle = now * 0.72 + Math.PI * 2.0 * (i - 1) / Math.max(1, ids.size() - 1);
            double r = radius * (0.65 + (i % 3) * 0.18);
            entity.setPos(center.x + Math.cos(angle) * r,
                center.y + Math.sin(angle * 1.7) * radius * 0.45,
                center.z + Math.sin(angle) * r);
        }
    }

    private static void positionOrbit(ServerLevel level, List<UUID> ids, Vec3 center, long now,
                                      double radius, double height) {
        for (int i = 0; i < ids.size(); i++) {
            Entity entity = level.getEntity(ids.get(i));
            if (entity == null) continue;
            double angle = now * 0.12 + Math.PI * 2.0 * i / Math.max(1, ids.size());
            double layer = (i % 3 - 1) * height * 0.48;
            entity.setPos(center.x + Math.cos(angle) * radius,
                center.y + layer + Math.sin(angle * 2.0) * 0.18,
                center.z + Math.sin(angle) * radius);
        }
    }

    private static void positionCage(ServerLevel level, List<UUID> ids, Vec3 center, long now, double radius) {
        for (int i = 0; i < ids.size(); i++) {
            Entity entity = level.getEntity(ids.get(i));
            if (entity == null) continue;
            int ring = i % 3;
            int inRing = i / 3;
            double angle = now * (ring == 1 ? -0.09 : 0.09) + Math.PI * 2.0 * inRing / Math.max(1, ids.size() / 3);
            double y = (ring - 1) * 0.95;
            entity.setPos(center.x + Math.cos(angle) * radius, center.y + y, center.z + Math.sin(angle) * radius);
        }
    }

    private static void positionSandGrave(ServerLevel level, List<UUID> ids, Vec3 center, long now, double radius) {
        for (int i = 0; i < ids.size(); i++) {
            Entity entity = level.getEntity(ids.get(i));
            if (entity == null) continue;
            double angle = Math.PI * 2.0 * i / Math.max(1, ids.size()) + now * 0.035;
            double height = 0.2 + (i % 4) * 0.55 + Math.sin(now * 0.16 + i) * 0.12;
            entity.setPos(center.x + Math.cos(angle) * radius, center.y + height, center.z + Math.sin(angle) * radius);
        }
    }

    private static List<UUID> spawnSandStatue(ServerLevel level, Vec3 base, long expireTick) {
        List<UUID> ids = spawnVisualItems(level, base, new Item[]{Items.SANDSTONE, Items.SAND, Items.CUT_SANDSTONE}, 7, false);
        for (UUID id : ids) STATIC_VISUALS.add(new StaticVisual(level, id, expireTick));
        updateSandStatue(level, ids, base, 0L);
        return ids;
    }

    private static void updateSandStatue(ServerLevel level, List<UUID> ids, Vec3 base, long now) {
        Vec3[] offsets = {
            new Vec3(0.0, 1.9, 0.0), new Vec3(0.0, 1.15, 0.0),
            new Vec3(-0.52, 1.25, 0.0), new Vec3(0.52, 1.25, 0.0),
            new Vec3(-0.25, 0.45, 0.0), new Vec3(0.25, 0.45, 0.0),
            new Vec3(0.0, 0.95 + Math.sin(now * 0.15) * 0.08, 0.0)
        };
        for (int i = 0; i < ids.size() && i < offsets.length; i++) {
            Entity entity = level.getEntity(ids.get(i));
            if (entity == null) continue;
            Vec3 pos = base.add(offsets[i]);
            entity.setPos(pos.x, pos.y, pos.z);
        }
    }

    private static void discardAll(ServerLevel level, List<UUID> ids) {
        for (UUID id : new ArrayList<>(ids)) {
            Entity entity = level.getEntity(id);
            if (entity != null) entity.discard();
        }
    }

    private static Item[] sandItems() {
        return new Item[]{Items.SAND, Items.SANDSTONE, Items.CUT_SANDSTONE, Items.CHISELED_SANDSTONE, Items.SMOOTH_SANDSTONE};
    }

    private static void burstSand(ServerLevel level, Vec3 center, double radius, int particles) {
        // Ana gövde her zaman kum/kumtaşı ItemEntity'dir; parçacık yalnızca patlama anında çıkar.
        spawnVisibleRing(level, center.add(0.0, 0.45, 0.0), sandItems(), Math.max(8, particles / 6),
            Math.max(1.2, radius * 0.58), level.getGameTime() + 28L, 0.42);
        level.sendParticles(ParticleTypes.POOF, center.x, center.y + 0.5, center.z, particles,
            radius * 0.35, radius * 0.22, radius * 0.35, 0.055);
        level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.35, center.z, Math.max(8, particles / 3),
            radius * 0.28, radius * 0.14, radius * 0.28, 0.025);
    }

    private static List<LivingEntity> nearby(ServerPlayer player, double radius) {
        return ((ServerLevel) player.level()).getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius));
    }

    private static LivingEntity findTarget(ServerPlayer player, double range, double width) {
        Vec3 origin = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = origin.add(look.scale(range));
        AABB area = new AABB(origin, end).inflate(width);
        LivingEntity best = null;
        double bestForward = range + 1.0;
        for (LivingEntity target : ((ServerLevel) player.level()).getEntitiesOfClass(LivingEntity.class, area)) {
            if (target == player || !target.isAlive() || target.isSpectator() || PowerSystem.isProtectedAlly(player, target)) continue;
            Vec3 to = target.getEyePosition().subtract(origin);
            double forward = to.dot(look);
            if (forward <= 0.4 || forward > range) continue;
            double side = to.subtract(look.scale(forward)).length();
            if (side > width || !player.hasLineOfSight(target)) continue;
            if (forward < bestForward) {
                best = target;
                bestForward = forward;
            }
        }
        return best;
    }

    private static int metalArmorPieces(LivingEntity target) {
        int pieces = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = target.getItemBySlot(slot);
            if (isMetalArmor(stack)) pieces++;
        }
        return pieces;
    }

    private static boolean isMetalArmor(ItemStack stack) {
        return stack.is(Items.IRON_HELMET) || stack.is(Items.IRON_CHESTPLATE) || stack.is(Items.IRON_LEGGINGS) || stack.is(Items.IRON_BOOTS)
            || stack.is(Items.CHAINMAIL_HELMET) || stack.is(Items.CHAINMAIL_CHESTPLATE) || stack.is(Items.CHAINMAIL_LEGGINGS) || stack.is(Items.CHAINMAIL_BOOTS)
            || stack.is(Items.GOLDEN_HELMET) || stack.is(Items.GOLDEN_CHESTPLATE) || stack.is(Items.GOLDEN_LEGGINGS) || stack.is(Items.GOLDEN_BOOTS)
            || stack.is(Items.NETHERITE_HELMET) || stack.is(Items.NETHERITE_CHESTPLATE) || stack.is(Items.NETHERITE_LEGGINGS) || stack.is(Items.NETHERITE_BOOTS);
    }

    private static Vec3 horizontal(Vec3 vector) {
        Vec3 flat = new Vec3(vector.x, 0.0, vector.z);
        return flat.lengthSqr() < 0.0001 ? new Vec3(0.0, 0.0, 1.0) : flat;
    }

    private static Vec3 ground(ServerLevel level, Vec3 position) {
        BlockPos pos = BlockPos.containing(position);
        for (int i = 0; i < 8; i++) {
            if (!level.getBlockState(pos.below()).isAir()) return new Vec3(position.x, pos.getY(), position.z);
            pos = pos.below();
        }
        return position;
    }

    private static Vec3 safeOffset(ServerLevel level, Vec3 origin, Vec3 offset) {
        Vec3 candidate = origin.add(offset);
        BlockPos feet = BlockPos.containing(candidate);
        if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()) return candidate;
        return origin;
    }

    private enum MovingType { SAND_SHOT, IRON_FIST, RAILGUN, METAL_STORM }

    private record StaticVisual(ServerLevel level, UUID entityId, long expireTick) {}

    private static final class MovingAttack {
        private final ServerLevel level;
        private final UUID ownerId;
        private final MovingType type;
        private final List<UUID> ids;
        private Vec3 center;
        private final Vec3 velocity;
        private final long expireTick;
        private final float damage;
        private final double radius;
        private final int hitLimit;
        private int hits;
        private final Set<UUID> hitTargets = new HashSet<>();

        private MovingAttack(ServerLevel level, UUID ownerId, MovingType type, List<UUID> ids,
                             Vec3 center, Vec3 velocity, long expireTick, float damage,
                             double radius, int hitLimit) {
            this.level = level;
            this.ownerId = ownerId;
            this.type = type;
            this.ids = ids;
            this.center = center;
            this.velocity = velocity;
            this.expireTick = expireTick;
            this.damage = damage;
            this.radius = radius;
            this.hitLimit = hitLimit;
        }
    }

    private record SandWave(ServerLevel level, UUID ownerId, List<UUID> ids, Vec3 start, Vec3 direction,
                            long startTick, long endTick, int stage, boolean awakened,
                            Set<UUID> hitTargets) {
        private SandWave(ServerLevel level, UUID ownerId, List<UUID> ids, Vec3 start, Vec3 direction,
                         long startTick, long endTick, int stage, boolean awakened) {
            this(level, ownerId, ids, start, direction, startTick, endTick, stage, awakened, new HashSet<>());
        }
    }

    private static final class MagneticStorm {
        private final ServerLevel level;
        private final UUID ownerId;
        private final List<UUID> ids;
        private final long startTick;
        private final long endTick;
        private final int stage;
        private MagneticStorm(ServerLevel level, UUID ownerId, List<UUID> ids, long startTick, long endTick, int stage) {
            this.level = level; this.ownerId = ownerId; this.ids = ids; this.startTick = startTick; this.endTick = endTick; this.stage = stage;
        }
    }

    private static final class MagneticCage {
        private final ServerLevel level;
        private final UUID ownerId;
        private final UUID targetId;
        private final List<UUID> ids;
        private Vec3 center;
        private final long startTick;
        private final long endTick;
        private final int stage;
        private final boolean awakened;
        private MagneticCage(ServerLevel level, UUID ownerId, UUID targetId, List<UUID> ids, Vec3 center,
                             long startTick, long endTick, int stage, boolean awakened) {
            this.level = level; this.ownerId = ownerId; this.targetId = targetId; this.ids = ids; this.center = center;
            this.startTick = startTick; this.endTick = endTick; this.stage = stage; this.awakened = awakened;
        }
    }

    private static final class SandArmor {
        private final ServerLevel level;
        private final UUID ownerId;
        private final List<UUID> ids;
        private final long endTick;
        private int charges;
        private SandArmor(ServerLevel level, UUID ownerId, List<UUID> ids, long endTick, int charges) {
            this.level = level; this.ownerId = ownerId; this.ids = ids; this.endTick = endTick; this.charges = charges;
        }
    }

    private static final class DesertMirrors {
        private final ServerLevel level;
        private final UUID ownerId;
        private final List<UUID> leftIds;
        private final List<UUID> rightIds;
        private final Vec3 origin;
        private final Vec3 leftVelocity;
        private final Vec3 rightVelocity;
        private final long startTick;
        private final long endTick;
        private int dodges;
        private DesertMirrors(ServerLevel level, UUID ownerId, List<UUID> leftIds, List<UUID> rightIds,
                              Vec3 origin, Vec3 leftVelocity, Vec3 rightVelocity,
                              long startTick, long endTick, int dodges) {
            this.level = level; this.ownerId = ownerId; this.leftIds = leftIds; this.rightIds = rightIds;
            this.origin = origin; this.leftVelocity = leftVelocity; this.rightVelocity = rightVelocity;
            this.startTick = startTick; this.endTick = endTick; this.dodges = dodges;
        }
        private List<UUID> allIds() {
            List<UUID> all = new ArrayList<>(leftIds);
            all.addAll(rightIds);
            return all;
        }
    }

    private static final class SandGrave {
        private final ServerLevel level;
        private final UUID ownerId;
        private final UUID targetId;
        private final List<UUID> ids;
        private Vec3 center;
        private final long startTick;
        private final long endTick;
        private final int stage;
        private final boolean awakened;
        private SandGrave(ServerLevel level, UUID ownerId, UUID targetId, List<UUID> ids, Vec3 center,
                          long startTick, long endTick, int stage, boolean awakened) {
            this.level = level; this.ownerId = ownerId; this.targetId = targetId; this.ids = ids; this.center = center;
            this.startTick = startTick; this.endTick = endTick; this.stage = stage; this.awakened = awakened;
        }
    }

    private static final class SandGiantArm {
        private final ServerLevel level;
        private final UUID ownerId;
        private final UUID targetId;
        private final List<UUID> ids;
        private final Vec3 start;
        private final Vec3 fixedEnd;
        private final long startTick;
        private final long endTick;
        private final int armIndex;
        private final int stage;
        private final boolean awakened;
        private SandGiantArm(ServerLevel level, UUID ownerId, UUID targetId, List<UUID> ids,
                             Vec3 start, Vec3 fixedEnd, long startTick, long endTick,
                             int armIndex, int stage, boolean awakened) {
            this.level = level; this.ownerId = ownerId; this.targetId = targetId; this.ids = ids;
            this.start = start; this.fixedEnd = fixedEnd; this.startTick = startTick; this.endTick = endTick;
            this.armIndex = armIndex; this.stage = stage; this.awakened = awakened;
        }
    }
}
