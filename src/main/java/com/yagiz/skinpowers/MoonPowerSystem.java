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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
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

/** Ay sınıfı: hilaller, yerçekimi, ay aynası, tutulma ve Dolunay Canavarı. */
public final class MoonPowerSystem {
    private static final List<MoonVisual> VISUALS = new ArrayList<>();
    private static final List<CrescentAttack> CRESCENTS = new ArrayList<>();
    private static final List<LunarSeal> LUNAR_SEALS = new ArrayList<>();
    private static final List<GravityField> GRAVITY_FIELDS = new ArrayList<>();
    private static final List<EclipseField> ECLIPSE_FIELDS = new ArrayList<>();
    private static final List<FullMoonBeast> FULL_MOON_BEASTS = new ArrayList<>();
    private static final List<ProjectileEscort> PROJECTILE_ESCORTS = new ArrayList<>();
    private static final Map<UUID, MoonMirror> MOON_MIRRORS = new HashMap<>();
    private static boolean reflectingDamage;

    private MoonPowerSystem() {}

    public static boolean use(ServerPlayer player, PlayerPowerData data, int power, long now, boolean awakened) {
        ServerLevel level = (ServerLevel) player.level();
        int stage = data.masteryStage(power);
        boolean eclipse = insideOwnedEclipse(player, now);
        boolean empowered = awakened || eclipse;
        return switch (power) {
            case 1 -> crescentSlash(player, data, level, now, stage, empowered);
            case 2 -> lunarSeal(player, data, level, now, stage, empowered);
            case 3 -> gravityPressure(player, data, level, now, stage, empowered);
            case 4 -> moonMirror(player, data, level, now, stage, empowered);
            case 5 -> eclipseField(player, data, level, now, stage, empowered);
            case 6 -> fullMoonBeast(player, data, level, now, stage, empowered);
            default -> false;
        };
    }

    public static void tickServer(MinecraftServer server) {
        tickVisuals();
        tickCrescents();
        tickLunarSeals();
        tickGravityFields();
        tickMoonMirrors(server);
        tickProjectileEscorts();
        tickEclipseFields();
        tickFullMoonBeasts();
    }

    public static void tickPlayer(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        if (data.powerClass() != PowerClass.MOON || data.unlockedLevel() < 1) return;
        // Ay pasifi: gece veya açık gökyüzünde hafif çeviklik ve düşme kontrolü.
        boolean openSky = level.canSeeSky(player.blockPosition().above());
        boolean night = level.getOverworldClockTime() % 24000L >= 12500L;
        if (openSky && night) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 28, 0, false, false, true));
            player.fallDistance = Math.min(player.fallDistance, 4.0F);
        }
        if (now % 20L == 0L && openSky) {
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(14.0))) {
                if (target == player || PowerSystem.isProtectedAlly(player, target)) continue;
                if (target.getHealth() <= target.getMaxHealth() * 0.35F) {
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 35, 0, false, false, true));
                }
            }
        }
    }

    private static boolean crescentSlash(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now, int stage, boolean empowered) {
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 start = player.getEyePosition().add(direction.scale(0.9));
        // Hilal artık eşya parçalarından değil, kesintisiz beyaz ay ışığı halkasından oluşur.
        List<UUID> ids = List.of();
        CRESCENTS.add(new CrescentAttack(level, player.getUUID(), ids, start, direction,
            now, now + (empowered ? 62L : 50L), stage, empowered, new HashSet<>(), false));
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.1F, 1.55F);
        data.setCooldown(1, now, Math.max(100, 170 - stage * 14));
        return true;
    }

    private static boolean lunarSeal(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now, int stage, boolean empowered) {
        Vec3 center = targetGround(level, player, 13.0 + stage * 1.8 + (empowered ? 4.0 : 0.0));
        double radius = 4.8 + stage * 0.45 + (empowered ? 1.6 : 0.0);
        LUNAR_SEALS.add(new LunarSeal(level, player.getUUID(), center, now, now + 26L,
            now + (empowered ? 72L : 62L), radius, stage, empowered, 0));
        drawWhiteGroundRing(level, center.add(0.0, 0.18, 0.0), radius, empowered ? 64 : 48);
        level.playSound(null, BlockPos.containing(center), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.85F, 1.55F);
        player.sendSystemMessage(Component.literal("Ay Mührü hedefe kilitlendi."));
        data.setCooldown(2, now, Math.max(300, 430 - stage * 28));
        return true;
    }

    private static boolean gravityPressure(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now, int stage, boolean empowered) {
        Vec3 center = targetGround(level, player, 12.0 + stage * 2.0 + (empowered ? 5.0 : 0.0));
        double radius = 6.0 + stage * 0.55 + (empowered ? 2.2 : 0.0);
        List<UUID> ids = spawnVisualItems(level, center.add(0.0, 0.35, 0.0),
            new Item[]{Items.ENDER_EYE, Items.QUARTZ, Items.IRON_NUGGET, Items.AMETHYST_SHARD}, empowered ? 20 : 14, true, player.getUUID());
        GRAVITY_FIELDS.add(new GravityField(level, player.getUUID(), center, ids, now, now + (empowered ? 150L : 110L), radius, stage, empowered));
        level.playSound(null, BlockPos.containing(center), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.0F, 0.55F);
        data.setCooldown(3, now, Math.max(420, 650 - stage * 45));
        return true;
    }

    private static boolean moonMirror(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now, int stage, boolean empowered) {
        MoonMirror existing = MOON_MIRRORS.remove(player.getUUID());
        if (existing != null) {
            discardAll(existing.level, existing.ids);
            Vec3 direction = player.getLookAngle().normalize();
            Vec3 start = player.getEyePosition().add(direction.scale(0.9));
            List<UUID> ids = List.of();
            CRESCENTS.add(new CrescentAttack(level, player.getUUID(), ids, start, direction,
                now, now + 42L, stage + 1, true, new HashSet<>(), false));
            level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.15F, 1.75F);
            data.setCooldown(4, now, Math.max(320, 520 - stage * 38));
            return true;
        }
        List<UUID> ids = spawnVisualItems(level, player.position().add(0.0, 1.0, 0.0),
            new Item[]{Items.QUARTZ, Items.AMETHYST_SHARD, Items.PRISMARINE_SHARD}, empowered ? 12 : 8, true, player.getUUID());
        MOON_MIRRORS.put(player.getUUID(), new MoonMirror(level, player.getUUID(), ids,
            now + (empowered ? 220L : 160L), empowered ? 3 : 2, stage, empowered));
        player.sendSystemMessage(Component.literal("Ay Aynası açık. Tekrar R: diski beyaz ay halkası olarak fırlat."));
        level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.9F, 1.35F);
        data.setCooldown(4, now, 18); // İkinci basış hemen yapılabilsin.
        return true;
    }

    private static boolean eclipseField(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now, int stage, boolean empowered) {
        Vec3 center = targetGround(level, player, 8.0 + stage * 1.4);
        double radius = 11.0 + stage * 0.85 + (empowered ? 3.0 : 0.0);
        List<UUID> ids = spawnVisualItems(level, center.add(0.0, 4.5, 0.0),
            new Item[]{Items.ENDER_EYE, Items.QUARTZ, Items.AMETHYST_SHARD, Items.NETHER_STAR}, empowered ? 28 : 20, true, player.getUUID());
        ECLIPSE_FIELDS.add(new EclipseField(level, player.getUUID(), center, ids, now,
            now + (empowered ? 340L : 270L), radius, stage, empowered));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, empowered ? 340 : 270, empowered ? 2 : 1, false, true, true));
        level.playSound(null, BlockPos.containing(center), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.25F, 0.72F);
        ServerNetworking.sendScreenShake(level, center, 28.0, 0.75F, 10);
        data.setCooldown(5, now, Math.max(820, 1160 - stage * 70));
        return true;
    }

    private static boolean fullMoonBeast(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now, int stage, boolean empowered) {
        Vec3 direction = directionToNearestEnemy(level, player, 24.0);
        Vec3 center = player.position().add(direction.scale(4.5));
        List<UUID> ids = spawnVisualItems(level, center.add(0.0, 1.0, 0.0),
            new Item[]{Items.QUARTZ, Items.AMETHYST_SHARD, Items.IRON_NUGGET, Items.ENDER_EYE, Items.NETHER_STAR},
            empowered ? 46 : 36, true, player.getUUID());
        FULL_MOON_BEASTS.add(new FullMoonBeast(level, player.getUUID(), ids, center, direction, now,
            now + (empowered ? 112L : 92L), stage, empowered, 0));
        level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.5F, 0.48F);
        ServerNetworking.sendScreenShake(level, center, 34.0, 1.0F, 14);
        data.setCooldown(6, now, Math.max(980, 1380 - stage * 80));
        return true;
    }

    public static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
        if (reflectingDamage || !(victim instanceof ServerPlayer player) || amount <= 0.0F) return true;
        MoonMirror mirror = MOON_MIRRORS.get(player.getUUID());
        if (mirror == null || mirror.charges <= 0 || !(source.getDirectEntity() instanceof Projectile projectile)) return true;
        Entity owner = projectile.getOwner();
        projectile.setOwner(player);
        Vec3 velocity = projectile.getDeltaMovement();
        if (owner instanceof LivingEntity living && living.isAlive()) {
            Vec3 direction = living.getEyePosition().subtract(projectile.position());
            projectile.setDeltaMovement(direction.lengthSqr() > 0.001 ? direction.normalize().scale(Math.max(1.15, velocity.length())) : velocity.scale(-1.25));
        } else {
            projectile.setDeltaMovement(velocity.scale(-1.25));
        }
        attachProjectileEscort(mirror.level, player.getUUID(), projectile, mirror.level.getGameTime() + 55L);
        mirror.charges--;
        mirror.level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 36, 0.8, 0.9, 0.8, 0.08);
        mirror.level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 1.65F);
        if (mirror.charges <= 0) {
            discardAll(mirror.level, mirror.ids);
            MOON_MIRRORS.remove(player.getUUID());
        }
        return false;
    }

    private static void tickCrescents() {
        Iterator<CrescentAttack> iterator = CRESCENTS.iterator();
        while (iterator.hasNext()) {
            CrescentAttack attack = iterator.next();
            long now = attack.level.getGameTime();
            ServerPlayer owner = attack.level.getServer().getPlayerList().getPlayer(attack.owner);
            if (owner == null || !owner.isAlive() || now >= attack.expireTick) {
                discardAll(attack.level, attack.ids);
                iterator.remove();
                continue;
            }
            long age = now - attack.startTick;
            long duration = Math.max(1L, attack.expireTick - attack.startTick);
            boolean shouldReturn = age >= duration / 2L;
            if (shouldReturn && !attack.returning) {
                attack.returning = true;
                attack.hit.clear();
            }
            Vec3 desiredDirection;
            if (attack.returning) {
                Vec3 toOwner = owner.getEyePosition().subtract(attack.center);
                desiredDirection = toOwner.lengthSqr() < 0.01 ? attack.direction : toOwner.normalize();
            } else {
                desiredDirection = attack.direction;
            }
            double speed = 0.82 + attack.stage * 0.055 + (attack.empowered ? 0.20 : 0.0);
            attack.center = attack.center.add(desiredDirection.scale(speed));
            drawWhiteLunarRing(attack.level, attack.center, desiredDirection, attack.empowered ? 2.15 : 1.82, attack.empowered);
            drawWhiteTrail(attack.level, attack.center, desiredDirection, attack.empowered);
            AABB hitBox = new AABB(attack.center, attack.center).inflate(1.85 + attack.stage * 0.10);
            for (LivingEntity target : attack.level.getEntitiesOfClass(LivingEntity.class, hitBox)) {
                if (target == owner || PowerSystem.isProtectedAlly(owner, target) || !attack.hit.add(target.getUUID())) continue;
                float damage = 8.0F + attack.stage * 1.5F + (attack.empowered ? 4.0F : 0.0F);
                target.hurtServer(attack.level, attack.level.damageSources().playerAttack(owner), damage);
                target.push(desiredDirection.x * 0.65, 0.22, desiredDirection.z * 0.65);
                attack.level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 1.0, target.getZ(), 22, 0.4, 0.6, 0.4, 0.06);
            }
            if (attack.returning && attack.center.distanceToSqr(owner.getEyePosition()) < 2.0) {
                discardAll(attack.level, attack.ids);
                iterator.remove();
            }
        }
    }

    private static void tickLunarSeals() {
        Iterator<LunarSeal> iterator = LUNAR_SEALS.iterator();
        while (iterator.hasNext()) {
            LunarSeal seal = iterator.next();
            long now = seal.level.getGameTime();
            ServerPlayer owner = seal.level.getServer().getPlayerList().getPlayer(seal.owner);
            if (owner == null || !owner.isAlive() || now >= seal.expireTick) {
                iterator.remove();
                continue;
            }

            double pulse = seal.radius * (0.86 + Math.sin((now - seal.startTick) * 0.18) * 0.08);
            drawWhiteGroundRing(seal.level, seal.center.add(0.0, 0.16, 0.0), pulse, seal.empowered ? 72 : 56);
            AABB area = new AABB(seal.center, seal.center).inflate(seal.radius, 5.0, seal.radius);
            for (LivingEntity target : seal.level.getEntitiesOfClass(LivingEntity.class, area)) {
                if (target == owner || PowerSystem.isProtectedAlly(owner, target)) continue;
                Vec3 pull = seal.center.subtract(target.position());
                if (pull.lengthSqr() > 0.01) {
                    pull = pull.normalize().scale(seal.empowered ? 0.22 : 0.16);
                    target.setDeltaMovement(target.getDeltaMovement().scale(0.72).add(pull.x, 0.0, pull.z));
                }
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, seal.empowered ? 3 : 2, false, false, true));
            }

            if (seal.phase == 0 && now >= seal.triggerTick) {
                seal.phase = 1;
                for (LivingEntity target : seal.level.getEntitiesOfClass(LivingEntity.class, area)) {
                    if (target == owner || PowerSystem.isProtectedAlly(owner, target)) continue;
                    target.hurtServer(seal.level, seal.level.damageSources().playerAttack(owner),
                        10.0F + seal.stage * 1.8F + (seal.empowered ? 5.0F : 0.0F));
                    target.setDeltaMovement(target.getDeltaMovement().x * 0.35, 1.0 + seal.stage * 0.08, target.getDeltaMovement().z * 0.35);
                }
                drawMoonBeam(seal.level, seal.center, 12.0, seal.empowered ? 80 : 58);
                seal.level.playSound(null, BlockPos.containing(seal.center), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.9F, 1.75F);
                ServerNetworking.sendScreenShake(seal.level, seal.center, 24.0, 0.8F, 10);
            } else if (seal.phase == 1 && now >= seal.triggerTick + 16L) {
                seal.phase = 2;
                for (LivingEntity target : seal.level.getEntitiesOfClass(LivingEntity.class, area.inflate(1.5))) {
                    if (target == owner || PowerSystem.isProtectedAlly(owner, target)) continue;
                    target.hurtServer(seal.level, seal.level.damageSources().playerAttack(owner),
                        12.0F + seal.stage * 2.0F + (seal.empowered ? 6.0F : 0.0F));
                    Vec3 push = target.position().subtract(seal.center);
                    if (push.lengthSqr() > 0.001) {
                        push = push.normalize().scale(seal.empowered ? 1.45 : 1.05);
                        target.push(push.x, 0.55, push.z);
                    }
                }
                drawWhiteGroundRing(seal.level, seal.center.add(0.0, 0.22, 0.0), seal.radius * 1.35, seal.empowered ? 92 : 72);
                seal.level.sendParticles(ParticleTypes.EXPLOSION, seal.center.x, seal.center.y + 0.6, seal.center.z, 8, 1.1, 0.5, 1.1, 0.02);
                seal.level.playSound(null, BlockPos.containing(seal.center), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9F, 1.35F);
                ServerNetworking.sendScreenShake(seal.level, seal.center, 30.0, 1.15F, 14);
            }
        }
    }

    private static void tickGravityFields() {
        Iterator<GravityField> iterator = GRAVITY_FIELDS.iterator();
        while (iterator.hasNext()) {
            GravityField field = iterator.next();
            long now = field.level.getGameTime();
            ServerPlayer owner = field.level.getServer().getPlayerList().getPlayer(field.owner);
            if (owner == null || !owner.isAlive() || now >= field.expireTick) {
                discardAll(field.level, field.ids);
                iterator.remove();
                continue;
            }
            positionDisc(field.level, field.ids, field.center.add(0.0, 0.35, 0.0), now, field.radius * 0.72);
            AABB area = new AABB(field.center, field.center).inflate(field.radius, 8.0, field.radius);
            for (LivingEntity target : field.level.getEntitiesOfClass(LivingEntity.class, area)) {
                if (target == owner || PowerSystem.isProtectedAlly(owner, target)) continue;
                Vec3 motion = target.getDeltaMovement();
                target.setDeltaMovement(motion.x * 0.72, Math.min(-0.55 - field.stage * 0.06, motion.y - 0.32), motion.z * 0.72);
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 28, field.empowered ? 4 : 3, false, false, true));
                if (now % 20L == 0L) target.hurtServer(field.level, field.level.damageSources().playerAttack(owner), 3.0F + field.stage + (field.empowered ? 2.5F : 0.0F));
            }
            for (Projectile projectile : field.level.getEntitiesOfClass(Projectile.class, area)) {
                if (projectile.getOwner() == owner) continue;
                Vec3 motion = projectile.getDeltaMovement();
                projectile.setDeltaMovement(motion.x * 0.72, motion.y - 0.32, motion.z * 0.72);
            }
            if (now % 5L == 0L) PowerSystem.drawExternalRing(field.level, field.center, field.radius, ParticleTypes.END_ROD, 64);
        }
    }

    private static void tickMoonMirrors(MinecraftServer server) {
        Iterator<MoonMirror> iterator = MOON_MIRRORS.values().iterator();
        while (iterator.hasNext()) {
            MoonMirror mirror = iterator.next();
            long now = mirror.level.getGameTime();
            ServerPlayer owner = server.getPlayerList().getPlayer(mirror.owner);
            if (owner == null || !owner.isAlive() || now >= mirror.expireTick || mirror.charges <= 0) {
                discardAll(mirror.level, mirror.ids);
                iterator.remove();
                continue;
            }
            positionOrbit(mirror.level, mirror.ids, owner.position().add(0.0, 1.1, 0.0), now, 1.55, 0.9);
            for (Projectile projectile : mirror.level.getEntitiesOfClass(Projectile.class, owner.getBoundingBox().inflate(3.2))) {
                if (projectile.getOwner() == owner) continue;
                Entity originalOwner = projectile.getOwner();
                Vec3 velocity = projectile.getDeltaMovement();
                projectile.setOwner(owner);
                if (originalOwner instanceof LivingEntity living && living.isAlive()) {
                    Vec3 direction = living.getEyePosition().subtract(projectile.position());
                    if (direction.lengthSqr() > 0.001) projectile.setDeltaMovement(direction.normalize().scale(Math.max(1.1, velocity.length())));
                } else projectile.setDeltaMovement(velocity.scale(-1.2));
                attachProjectileEscort(mirror.level, owner.getUUID(), projectile, now + 55L);
                mirror.charges--;
                mirror.level.sendParticles(ParticleTypes.END_ROD, projectile.getX(), projectile.getY(), projectile.getZ(), 20, 0.35, 0.35, 0.35, 0.06);
                break;
            }
        }
    }

    private static void tickEclipseFields() {
        Iterator<EclipseField> iterator = ECLIPSE_FIELDS.iterator();
        while (iterator.hasNext()) {
            EclipseField field = iterator.next();
            long now = field.level.getGameTime();
            ServerPlayer owner = field.level.getServer().getPlayerList().getPlayer(field.owner);
            if (owner == null || !owner.isAlive() || now >= field.expireTick) {
                discardAll(field.level, field.ids);
                iterator.remove();
                continue;
            }
            positionEclipse(field.level, field.ids, field.center.add(0.0, 4.8, 0.0), now, field.radius * 0.28);
            if (now % 2L == 0L) drawWhiteGroundRing(field.level, field.center.add(0.0, 0.12, 0.0), field.radius, field.empowered ? 88 : 68);
            AABB area = new AABB(field.center, field.center).inflate(field.radius, 7.0, field.radius);
            for (LivingEntity target : field.level.getEntitiesOfClass(LivingEntity.class, area)) {
                if (target == owner || PowerSystem.isProtectedAlly(owner, target)) {
                    target.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, field.empowered ? 3 : 2, false, false, true));
                    target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, field.empowered ? 1 : 0, false, false, true));
                    target.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 30, field.empowered ? 1 : 0, false, false, true));
                } else {
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, field.empowered ? 4 : 3, false, false, true));
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, field.empowered ? 3 : 2, false, false, true));
                    Vec3 pull = field.center.subtract(target.position());
                    if (pull.lengthSqr() > 0.01) {
                        pull = pull.normalize().scale(field.empowered ? 0.15 : 0.10);
                        target.setDeltaMovement(target.getDeltaMovement().scale(0.82).add(pull.x, -0.08, pull.z));
                    }
                    if (now % 16L == 0L) target.hurtServer(field.level, field.level.damageSources().playerAttack(owner), 5.0F + field.stage * 1.1F + (field.empowered ? 2.5F : 0.0F));
                    if (now % 32L == 0L) {
                        drawMoonBeam(field.level, target.position(), 9.0, field.empowered ? 56 : 42);
                        target.hurtServer(field.level, field.level.damageSources().playerAttack(owner), 6.0F + field.stage + (field.empowered ? 3.0F : 0.0F));
                    }
                }
            }
            if (now % 6L == 0L) PowerSystem.drawExternalRing(field.level, field.center, field.radius * 0.72, ParticleTypes.REVERSE_PORTAL, 72);
        }
    }

    private static void tickFullMoonBeasts() {
        Iterator<FullMoonBeast> iterator = FULL_MOON_BEASTS.iterator();
        while (iterator.hasNext()) {
            FullMoonBeast beast = iterator.next();
            long now = beast.level.getGameTime();
            ServerPlayer owner = beast.level.getServer().getPlayerList().getPlayer(beast.owner);
            if (owner == null || !owner.isAlive() || now >= beast.expireTick) {
                discardAll(beast.level, beast.ids);
                iterator.remove();
                continue;
            }
            long age = now - beast.startTick;
            Vec3 center = owner.position().add(beast.direction.scale(4.8 + Math.min(6.0, age * 0.075)));
            beast.center = center;
            positionBeast(beast.level, beast.ids, center, beast.direction, now, beast.empowered);
            if (beast.phase == 0 && age >= 12L) {
                beast.phase = 1;
                beastSwipe(beast, owner, center.add(perpendicular(beast.direction).scale(-2.4)), 13.0F + beast.stage * 1.9F);
            } else if (beast.phase == 1 && age >= 25L) {
                beast.phase = 2;
                beastSwipe(beast, owner, center.add(perpendicular(beast.direction).scale(2.4)), 14.0F + beast.stage * 2.0F);
            } else if (beast.phase == 2 && age >= 39L) {
                beast.phase = 3;
                beastSwipe(beast, owner, center, 16.0F + beast.stage * 2.2F);
                drawMoonBeam(beast.level, center, 10.0, beast.empowered ? 72 : 56);
            } else if (beast.phase == 3 && age >= 57L) {
                beast.phase = 4;
                double radius = 9.0 + beast.stage * 0.55 + (beast.empowered ? 2.5 : 0.0);
                for (LivingEntity target : beast.level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius))) {
                    if (target == owner || PowerSystem.isProtectedAlly(owner, target)) continue;
                    target.hurtServer(beast.level, beast.level.damageSources().playerAttack(owner), 24.0F + beast.stage * 2.8F + (beast.empowered ? 8.0F : 0.0F));
                    Vec3 push = target.position().subtract(center);
                    if (push.lengthSqr() > 0.001) {
                        push = push.normalize().scale(2.25);
                        target.push(push.x, 1.15, push.z);
                    }
                }
                drawWhiteGroundRing(beast.level, center.add(0.0, 0.35, 0.0), radius, beast.empowered ? 112 : 88);
                drawMoonBeam(beast.level, center, 15.0, beast.empowered ? 110 : 86);
                beast.level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.7, center.z, 16, 2.2, 1.0, 2.2, 0.10);
                ServerNetworking.sendScreenShake(beast.level, center, 44.0, 2.0F, 22);
            }
        }
    }

    private static void beastSwipe(FullMoonBeast beast, ServerPlayer owner, Vec3 center, float baseDamage) {
        double radius = 6.8 + beast.stage * 0.42;
        for (LivingEntity target : beast.level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius, 3.0, radius))) {
            if (target == owner || PowerSystem.isProtectedAlly(owner, target)) continue;
            target.hurtServer(beast.level, beast.level.damageSources().playerAttack(owner), baseDamage + (beast.empowered ? 4.0F : 0.0F));
            Vec3 push = target.position().subtract(center);
            if (push.lengthSqr() > 0.001) {
                push = push.normalize().scale(1.2);
                target.push(push.x, 0.45, push.z);
            }
        }
        beast.level.sendParticles(ParticleTypes.END_ROD, center.x, center.y + 1.0, center.z, 60, 2.2, 1.1, 2.2, 0.12);
        ServerNetworking.sendScreenShake(beast.level, center, 25.0, 0.9F, 10);
    }

    private static boolean insideOwnedEclipse(ServerPlayer player, long now) {
        for (EclipseField field : ECLIPSE_FIELDS) {
            if (!field.owner.equals(player.getUUID()) || field.expireTick <= now) continue;
            if (player.position().distanceToSqr(field.center) <= field.radius * field.radius) return true;
        }
        return false;
    }

    public static void clearPlayer(ServerPlayer player) {
        clearOwner(player.getUUID());
    }

    public static void afterDeath(LivingEntity entity, DamageSource source) {
        if (entity instanceof ServerPlayer player) clearOwner(player.getUUID());
    }

    public static void handleDisconnect(ServerPlayer player) {
        clearOwner(player.getUUID());
    }

    private static void clearOwner(UUID owner) {
        VISUALS.removeIf(visual -> {
            if (!visual.owner.equals(owner)) return false;
            Entity entity = visual.level.getEntity(visual.entityId);
            if (entity != null) entity.discard();
            return true;
        });
        CRESCENTS.removeIf(attack -> discardOwned(attack.owner, owner, attack.level, attack.ids));
        LUNAR_SEALS.removeIf(seal -> seal.owner.equals(owner));
        GRAVITY_FIELDS.removeIf(field -> discardOwned(field.owner, owner, field.level, field.ids));
        ECLIPSE_FIELDS.removeIf(field -> discardOwned(field.owner, owner, field.level, field.ids));
        FULL_MOON_BEASTS.removeIf(beast -> discardOwned(beast.owner, owner, beast.level, beast.ids));
        PROJECTILE_ESCORTS.removeIf(escort -> discardOwned(escort.owner, owner, escort.level, escort.ids));
        MoonMirror mirror = MOON_MIRRORS.remove(owner);
        if (mirror != null) discardAll(mirror.level, mirror.ids);
    }

    private static boolean discardOwned(UUID candidate, UUID owner, ServerLevel level, List<UUID> ids) {
        if (!candidate.equals(owner)) return false;
        discardAll(level, ids);
        return true;
    }

    public static void clearAll() {
        for (MoonVisual visual : VISUALS) {
            Entity entity = visual.level.getEntity(visual.entityId);
            if (entity != null) entity.discard();
        }
        for (CrescentAttack attack : CRESCENTS) discardAll(attack.level, attack.ids);
        for (GravityField field : GRAVITY_FIELDS) discardAll(field.level, field.ids);
        for (EclipseField field : ECLIPSE_FIELDS) discardAll(field.level, field.ids);
        for (FullMoonBeast beast : FULL_MOON_BEASTS) discardAll(beast.level, beast.ids);
        for (ProjectileEscort escort : PROJECTILE_ESCORTS) discardAll(escort.level, escort.ids);
        for (MoonMirror mirror : MOON_MIRRORS.values()) discardAll(mirror.level, mirror.ids);
        VISUALS.clear();
        CRESCENTS.clear();
        LUNAR_SEALS.clear();
        GRAVITY_FIELDS.clear();
        ECLIPSE_FIELDS.clear();
        FULL_MOON_BEASTS.clear();
        PROJECTILE_ESCORTS.clear();
        MOON_MIRRORS.clear();
    }

    private static void tickVisuals() {
        Iterator<MoonVisual> iterator = VISUALS.iterator();
        while (iterator.hasNext()) {
            MoonVisual visual = iterator.next();
            Entity entity = visual.level.getEntity(visual.entityId);
            if (entity == null || entity.isRemoved() || visual.level.getGameTime() >= visual.expireTick) {
                if (entity != null) entity.discard();
                iterator.remove();
            }
        }
    }

    private static void drawWhiteLunarRing(ServerLevel level, Vec3 center, Vec3 direction, double radius, boolean empowered) {
        Vec3 forward = direction.normalize();
        Vec3 right = perpendicular(horizontal(forward));
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        int count = empowered ? 56 : 44;
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            Vec3 outer = center.add(right.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
            level.sendParticles(ParticleTypes.END_ROD, outer.x, outer.y, outer.z, 1, 0.0, 0.0, 0.0, 0.0);
            if (i % 2 == 0) {
                Vec3 inner = center.add(right.scale(Math.cos(angle) * radius * 0.78)).add(up.scale(Math.sin(angle) * radius * 0.78));
                level.sendParticles(ParticleTypes.END_ROD, inner.x, inner.y, inner.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, empowered ? 5 : 3, 0.12, 0.12, 0.12, 0.0);
    }

    private static void drawWhiteTrail(ServerLevel level, Vec3 center, Vec3 direction, boolean empowered) {
        Vec3 forward = direction.normalize();
        int count = empowered ? 8 : 5;
        for (int i = 1; i <= count; i++) {
            Vec3 point = center.add(forward.scale(-i * 0.34));
            level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 2, 0.06, 0.06, 0.06, 0.0);
        }
    }

    private static void drawWhiteGroundRing(ServerLevel level, Vec3 center, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / Math.max(1, count);
            Vec3 point = center.add(Math.cos(angle) * radius, Math.sin(angle * 3.0) * 0.05, Math.sin(angle) * radius);
            level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void drawMoonBeam(ServerLevel level, Vec3 ground, double height, int count) {
        int steps = Math.max(10, count / 4);
        for (int i = 0; i < steps; i++) {
            double y = ground.y + 0.2 + height * i / Math.max(1.0, steps - 1.0);
            double radius = 0.30 + (i % 3) * 0.08;
            double angle = i * 0.92 + level.getGameTime() * 0.18;
            level.sendParticles(ParticleTypes.END_ROD,
                ground.x + Math.cos(angle) * radius, y, ground.z + Math.sin(angle) * radius,
                Math.max(1, count / steps), 0.08, 0.10, 0.08, 0.0);
        }
    }

    private static Vec3 directionToNearestEnemy(ServerLevel level, ServerPlayer player, double range) {
        LivingEntity nearest = null;
        double best = range * range;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range))) {
            if (target == player || PowerSystem.isProtectedAlly(player, target)) continue;
            double distance = player.distanceToSqr(target);
            if (distance < best) {
                best = distance;
                nearest = target;
            }
        }
        if (nearest == null) return horizontal(player.getLookAngle());
        return horizontal(nearest.position().subtract(player.position()));
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

    private static List<UUID> spawnVisualItems(ServerLevel level, Vec3 center, Item[] items, int count, boolean glowing, UUID owner) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ItemEntity visual = createVisual(level, items[i % items.length], center, glowing);
            if (visual != null) ids.add(visual.getUUID());
        }
        return ids;
    }

    private static void spawnVisibleRing(ServerLevel level, Vec3 center, Item[] items, int count, double radius, long expireTick, UUID owner) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / Math.max(1, count);
            Vec3 position = center.add(Math.cos(angle) * radius, Math.sin(angle * 2.0) * 0.28, Math.sin(angle) * radius);
            ItemEntity visual = createVisual(level, items[i % items.length], position, true);
            if (visual != null) VISUALS.add(new MoonVisual(level, owner, visual.getUUID(), expireTick));
        }
    }

    private static void positionCrescent(ServerLevel level, List<UUID> ids, Vec3 center, Vec3 direction, long now, double radius) {
        Vec3 forward = direction.normalize();
        Vec3 right = perpendicular(horizontal(direction));
        int arcCount = Math.max(9, (int) Math.ceil(ids.size() * 0.72));
        for (int i = 0; i < ids.size(); i++) {
            Entity entity = level.getEntity(ids.get(i));
            if (entity == null) continue;
            Vec3 position;
            if (i < arcCount) {
                double t = arcCount <= 1 ? 0.5 : i / (double) (arcCount - 1);
                double curve = (t - 0.5) * Math.PI;
                double thickness = ((i % 3) - 1) * 0.16;
                position = center
                    .add(right.scale(Math.sin(curve) * radius))
                    .add(0.0, Math.cos(curve) * radius * 0.72 + thickness + Math.sin(now * 0.28 + i) * 0.06, 0.0)
                    .add(forward.scale(((i % 2) - 0.5) * 0.20));
            } else {
                int tailIndex = i - arcCount;
                double back = 0.28 + tailIndex * 0.18;
                double side = ((tailIndex % 3) - 1) * 0.24;
                double vertical = ((tailIndex / 3) % 3 - 1) * 0.22;
                position = center.add(forward.scale(-back)).add(right.scale(side)).add(0.0, vertical, 0.0);
            }
            moveVisualSmoothly(entity, position, 0.94, 2.25);
        }
    }

    private static void spawnAirTrail(ServerLevel level, UUID owner, Vec3 center, Vec3 direction, long expireTick, boolean empowered) {
        Vec3 forward = direction.normalize();
        Vec3 right = perpendicular(horizontal(direction));
        Item[] items = moonFlightItems();
        int count = empowered ? 9 : 6;
        for (int i = 0; i < count; i++) {
            double back = 0.35 + (i / 3) * 0.55;
            double side = ((i % 3) - 1) * (empowered ? 0.42 : 0.30);
            double height = ((i + 1) % 3 - 1) * 0.20;
            Vec3 position = center.add(forward.scale(-back)).add(right.scale(side)).add(0.0, height, 0.0);
            ItemEntity visual = createVisual(level, items[i % items.length], position, true);
            if (visual != null) VISUALS.add(new MoonVisual(level, owner, visual.getUUID(), expireTick));
        }
    }

    private static void attachProjectileEscort(ServerLevel level, UUID owner, Projectile projectile, long expireTick) {
        for (ProjectileEscort escort : PROJECTILE_ESCORTS) {
            if (escort.projectileId.equals(projectile.getUUID())) {
                escort.expireTick = Math.max(escort.expireTick, expireTick);
                return;
            }
        }
        List<UUID> ids = spawnVisualItems(level, projectile.position(), moonFlightItems(), 10, true, owner);
        PROJECTILE_ESCORTS.add(new ProjectileEscort(level, owner, projectile.getUUID(), ids, expireTick));
    }

    private static void tickProjectileEscorts() {
        Iterator<ProjectileEscort> iterator = PROJECTILE_ESCORTS.iterator();
        while (iterator.hasNext()) {
            ProjectileEscort escort = iterator.next();
            long now = escort.level.getGameTime();
            Entity projectile = escort.level.getEntity(escort.projectileId);
            if (projectile == null || projectile.isRemoved() || now >= escort.expireTick) {
                discardAll(escort.level, escort.ids);
                iterator.remove();
                continue;
            }
            Vec3 velocity = projectile.getDeltaMovement();
            Vec3 forward = velocity.lengthSqr() < 0.001 ? new Vec3(0.0, 0.0, 1.0) : velocity.normalize();
            Vec3 right = perpendicular(horizontal(forward));
            for (int i = 0; i < escort.ids.size(); i++) {
                Entity visual = escort.level.getEntity(escort.ids.get(i));
                if (visual == null) continue;
                double angle = now * 0.35 + Math.PI * 2.0 * i / Math.max(1, escort.ids.size());
                double radius = 0.38 + (i % 2) * 0.18;
                Vec3 target = projectile.position()
                    .add(right.scale(Math.cos(angle) * radius))
                    .add(0.0, Math.sin(angle) * radius, 0.0)
                    .add(forward.scale(-0.20 - (i % 3) * 0.12));
                moveVisualSmoothly(visual, target, 0.96, 2.8);
            }
        }
    }

    private static void positionDisc(ServerLevel level, List<UUID> ids, Vec3 center, long now, double radius) {
        for (int i = 0; i < ids.size(); i++) {
            Entity entity = level.getEntity(ids.get(i));
            if (entity == null) continue;
            double angle = now * 0.08 + Math.PI * 2.0 * i / Math.max(1, ids.size());
            double r = radius * (0.35 + (i % 4) * 0.18);
            Vec3 target = center.add(Math.cos(angle) * r, (i % 3) * 0.12, Math.sin(angle) * r);
            moveVisualSmoothly(entity, target, 0.82, 1.5);
        }
    }

    private static void positionOrbit(ServerLevel level, List<UUID> ids, Vec3 center, long now, double radius, double height) {
        for (int i = 0; i < ids.size(); i++) {
            Entity entity = level.getEntity(ids.get(i));
            if (entity == null) continue;
            double angle = now * 0.16 + Math.PI * 2.0 * i / Math.max(1, ids.size());
            Vec3 target = center.add(Math.cos(angle) * radius, (i % 3 - 1) * height * 0.42 + Math.sin(angle * 2.0) * 0.14, Math.sin(angle) * radius);
            moveVisualSmoothly(entity, target, 0.86, 1.65);
        }
    }

    private static void positionEclipse(ServerLevel level, List<UUID> ids, Vec3 center, long now, double radius) {
        for (int i = 0; i < ids.size(); i++) {
            Entity entity = level.getEntity(ids.get(i));
            if (entity == null) continue;
            double angle = now * 0.045 + Math.PI * 2.0 * i / Math.max(1, ids.size());
            double ring = i < ids.size() / 3 ? radius * 0.35 : radius;
            Vec3 target = center.add(Math.cos(angle) * ring, Math.sin(angle * 2.0) * 0.45, Math.sin(angle) * ring);
            moveVisualSmoothly(entity, target, 0.84, 1.45);
        }
    }

    private static void positionBeast(ServerLevel level, List<UUID> ids, Vec3 base, Vec3 direction, long now, boolean empowered) {
        Vec3 right = perpendicular(direction);
        Vec3[] offsets = {
            new Vec3(0,3.4,0), new Vec3(0,2.8,0), new Vec3(0,2.2,0), new Vec3(0,1.6,0),
            right.scale(-0.8).add(0,2.8,0), right.scale(0.8).add(0,2.8,0),
            right.scale(-1.55).add(0,2.35,0), right.scale(1.55).add(0,2.35,0),
            right.scale(-2.2).add(0,1.85 + Math.sin(now * 0.18) * 0.35,0), right.scale(2.2).add(0,1.85 - Math.sin(now * 0.18) * 0.35,0),
            right.scale(-0.55).add(0,0.95,0), right.scale(0.55).add(0,0.95,0),
            right.scale(-0.75).add(0,0.25,0), right.scale(0.75).add(0,0.25,0)
        };
        for (int i = 0; i < ids.size(); i++) {
            Entity entity = level.getEntity(ids.get(i));
            if (entity == null) continue;
            Vec3 offset = offsets[i % offsets.length];
            double layer = i / (double) offsets.length;
            Vec3 target = base.add(offset).add(direction.scale(layer * (empowered ? 0.45 : 0.25)));
            moveVisualSmoothly(entity, target, 0.90, 1.8);
        }
    }

    private static void moveVisualSmoothly(Entity entity, Vec3 target, double smoothing, double maxStep) {
        Vec3 delta = target.subtract(entity.position());
        double distance = delta.length();
        if (distance > 10.0) {
            entity.setPos(target.x, target.y, target.z);
            entity.setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (distance < 0.015) {
            entity.setDeltaMovement(Vec3.ZERO);
            return;
        }
        Vec3 motion = delta.scale(smoothing);
        if (motion.length() > maxStep) motion = motion.normalize().scale(maxStep);
        entity.setDeltaMovement(motion);
    }

    private static void discardAll(ServerLevel level, List<UUID> ids) {
        for (UUID id : new ArrayList<>(ids)) {
            Entity entity = level.getEntity(id);
            if (entity != null) entity.discard();
        }
    }

    private static Item[] moonFlightItems() {
        return new Item[]{Items.NETHER_STAR, Items.ENDER_EYE, Items.QUARTZ, Items.AMETHYST_SHARD, Items.PRISMARINE_SHARD};
    }

    private static Item[] moonItems() {
        return new Item[]{Items.QUARTZ, Items.AMETHYST_SHARD, Items.IRON_NUGGET, Items.PRISMARINE_SHARD, Items.ENDER_EYE};
    }

    private static Vec3 targetGround(ServerLevel level, ServerPlayer player, double distance) {
        Vec3 look = horizontal(player.getLookAngle());
        Vec3 target = player.position().add(look.scale(distance));
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(target.x), (int) Math.floor(target.z));
        return new Vec3(target.x, y, target.z);
    }

    private static Vec3 horizontal(Vec3 vector) {
        Vec3 result = new Vec3(vector.x, 0.0, vector.z);
        return result.lengthSqr() < 0.0001 ? new Vec3(0.0, 0.0, 1.0) : result.normalize();
    }

    private static Vec3 perpendicular(Vec3 direction) {
        Vec3 horizontal = horizontal(direction);
        return new Vec3(-horizontal.z, 0.0, horizontal.x);
    }

    private record MoonVisual(ServerLevel level, UUID owner, UUID entityId, long expireTick) {}

    private static final class ProjectileEscort {
        private final ServerLevel level;
        private final UUID owner;
        private final UUID projectileId;
        private final List<UUID> ids;
        private long expireTick;

        private ProjectileEscort(ServerLevel level, UUID owner, UUID projectileId, List<UUID> ids, long expireTick) {
            this.level = level;
            this.owner = owner;
            this.projectileId = projectileId;
            this.ids = ids;
            this.expireTick = expireTick;
        }
    }

    private static final class CrescentAttack {
        private final ServerLevel level;
        private final UUID owner;
        private final List<UUID> ids;
        private Vec3 center;
        private final Vec3 direction;
        private final long startTick;
        private final long expireTick;
        private final int stage;
        private final boolean empowered;
        private final Set<UUID> hit;
        private boolean returning;

        private CrescentAttack(ServerLevel level, UUID owner, List<UUID> ids, Vec3 center, Vec3 direction,
                               long startTick, long expireTick, int stage, boolean empowered, Set<UUID> hit, boolean returning) {
            this.level = level; this.owner = owner; this.ids = ids; this.center = center; this.direction = direction;
            this.startTick = startTick; this.expireTick = expireTick; this.stage = stage; this.empowered = empowered;
            this.hit = hit; this.returning = returning;
        }
    }

    private static final class LunarSeal {
        private final ServerLevel level;
        private final UUID owner;
        private final Vec3 center;
        private final long startTick;
        private final long triggerTick;
        private final long expireTick;
        private final double radius;
        private final int stage;
        private final boolean empowered;
        private int phase;

        private LunarSeal(ServerLevel level, UUID owner, Vec3 center, long startTick, long triggerTick,
                          long expireTick, double radius, int stage, boolean empowered, int phase) {
            this.level = level; this.owner = owner; this.center = center; this.startTick = startTick;
            this.triggerTick = triggerTick; this.expireTick = expireTick; this.radius = radius;
            this.stage = stage; this.empowered = empowered; this.phase = phase;
        }
    }

    private record GravityField(ServerLevel level, UUID owner, Vec3 center, List<UUID> ids,
                                long startTick, long expireTick, double radius, int stage, boolean empowered) {}

    private static final class MoonMirror {
        private final ServerLevel level;
        private final UUID owner;
        private final List<UUID> ids;
        private final long expireTick;
        private int charges;
        private final int stage;
        private final boolean empowered;

        private MoonMirror(ServerLevel level, UUID owner, List<UUID> ids, long expireTick, int charges, int stage, boolean empowered) {
            this.level = level; this.owner = owner; this.ids = ids; this.expireTick = expireTick;
            this.charges = charges; this.stage = stage; this.empowered = empowered;
        }
    }

    private record EclipseField(ServerLevel level, UUID owner, Vec3 center, List<UUID> ids,
                                long startTick, long expireTick, double radius, int stage, boolean empowered) {}

    private static final class FullMoonBeast {
        private final ServerLevel level;
        private final UUID owner;
        private final List<UUID> ids;
        private Vec3 center;
        private final Vec3 direction;
        private final long startTick;
        private final long expireTick;
        private final int stage;
        private final boolean empowered;
        private int phase;

        private FullMoonBeast(ServerLevel level, UUID owner, List<UUID> ids, Vec3 center, Vec3 direction,
                              long startTick, long expireTick, int stage, boolean empowered, int phase) {
            this.level = level; this.owner = owner; this.ids = ids; this.center = center; this.direction = direction;
            this.startTick = startTick; this.expireTick = expireTick; this.stage = stage; this.empowered = empowered; this.phase = phase;
        }
    }
}
