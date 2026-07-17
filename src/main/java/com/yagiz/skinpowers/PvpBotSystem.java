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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Tek oyuncuda sınıf savaşlarını denemek için özel PvP rakipleri.
 * Sahte hesap bağlamak yerine mevcut bir savaş varlığı kullanır; güç kararlarını bu sistem verir.
 */
public final class PvpBotSystem {
    private static final Map<UUID, BotState> BOTS = new HashMap<>();
    private static final Map<UUID, UUID> BOT_BY_OWNER = new HashMap<>();
    private static final ArrayList<BotMeteor> METEORS = new ArrayList<>();
    private static boolean reflecting;

    private PvpBotSystem() {}

    public enum BotDifficulty {
        EASY("Kolay", 0.82, 45L, 0.78F, 32.0F),
        NORMAL("Normal", 1.0, 32L, 1.0F, 42.0F),
        HARD("Zor", 1.18, 23L, 1.20F, 56.0F),
        NIGHTMARE("Kâbus", 1.36, 16L, 1.42F, 72.0F);

        private final String displayName;
        private final double speed;
        private final long reactionTicks;
        private final float damageMultiplier;
        private final float health;

        BotDifficulty(String displayName, double speed, long reactionTicks, float damageMultiplier, float health) {
            this.displayName = displayName;
            this.speed = speed;
            this.reactionTicks = reactionTicks;
            this.damageMultiplier = damageMultiplier;
            this.health = health;
        }

        public String displayName() { return displayName; }
        public static BotDifficulty parse(String value) {
            if (value == null) return NORMAL;
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "kolay", "easy" -> EASY;
                case "zor", "hard" -> HARD;
                case "kabus", "kâbus", "nightmare" -> NIGHTMARE;
                default -> NORMAL;
            };
        }
    }

    public static boolean spawn(ServerPlayer owner, PowerClass powerClass, BotDifficulty difficulty) {
        if (powerClass == null || powerClass == PowerClass.NONE) return false;
        removeOwnerBot(owner, false);
        ServerLevel level = (ServerLevel) owner.level();
        Vec3 look = horizontal(owner.getLookAngle());
        Vec3 spawn = safeSpawn(level, owner.position().add(look.scale(5.0)));

        Husk bot = new Husk(EntityType.HUSK, level);
        bot.setPos(spawn.x, spawn.y, spawn.z);
        bot.setPersistenceRequired();
        bot.setCanPickUpLoot(false);
        bot.setCustomName(Component.literal(powerClass.displayName() + " Ustası [" + difficulty.displayName() + "]"));
        bot.setCustomNameVisible(true);
        bot.setTarget(owner);
        configureAttributes(bot, difficulty);
        equip(bot, powerClass);
        if (!level.addFreshEntity(bot)) return false;

        long now = level.getGameTime();
        BotState state = new BotState(bot.getUUID(), owner.getUUID(), level, powerClass, difficulty, now + 35L);
        BOTS.put(bot.getUUID(), state);
        BOT_BY_OWNER.put(owner.getUUID(), bot.getUUID());
        level.sendParticles(classParticle(powerClass), bot.getX(), bot.getY() + 1.0, bot.getZ(), 70, 0.9, 1.1, 0.9, 0.08);
        level.playSound(null, bot.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8F, classPitch(powerClass));
        owner.sendSystemMessage(Component.literal(powerClass.displayName() + " PvP botu çağrıldı: " + difficulty.displayName() + "."));
        return true;
    }

    private static void configureAttributes(Husk bot, BotDifficulty difficulty) {
        if (bot.getAttribute(Attributes.MAX_HEALTH) != null) bot.getAttribute(Attributes.MAX_HEALTH).setBaseValue(difficulty.health);
        if (bot.getAttribute(Attributes.MOVEMENT_SPEED) != null) bot.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.25D + (difficulty.ordinal() * 0.025D));
        if (bot.getAttribute(Attributes.ATTACK_DAMAGE) != null) bot.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(5.0D + difficulty.ordinal() * 1.5D);
        if (bot.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) bot.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.35D + difficulty.ordinal() * 0.12D);
        bot.setHealth(difficulty.health);
    }

    private static void equip(Husk bot, PowerClass powerClass) {
        ItemStack weapon = switch (powerClass) {
            case WARDEN -> new ItemStack(Items.NETHERITE_AXE);
            case FLIGHT -> new ItemStack(Items.NETHERITE_SWORD);
            case FIRE -> new ItemStack(Items.BLAZE_ROD);
            case NATURE -> new ItemStack(Items.TRIDENT);
            case ANOMALY -> new ItemStack(Items.ECHO_SHARD);
            default -> ItemStack.EMPTY;
        };
        ItemStack helmet = switch (powerClass) {
            case WARDEN -> new ItemStack(Items.DIAMOND_HELMET);
            case FLIGHT -> new ItemStack(Items.NETHERITE_HELMET);
            case FIRE -> new ItemStack(Items.GOLDEN_HELMET);
            case NATURE -> new ItemStack(Items.TURTLE_HELMET);
            case ANOMALY -> new ItemStack(Items.CHAINMAIL_HELMET);
            default -> ItemStack.EMPTY;
        };
        bot.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        bot.setItemSlot(EquipmentSlot.HEAD, helmet);
        bot.setItemSlot(EquipmentSlot.CHEST, powerClass == PowerClass.FLIGHT ? new ItemStack(Items.NETHERITE_CHESTPLATE) : new ItemStack(Items.CHAINMAIL_CHESTPLATE));
    }

    public static boolean removeOwnerBot(ServerPlayer owner, boolean notify) {
        UUID botId = BOT_BY_OWNER.remove(owner.getUUID());
        if (botId == null) return false;
        BotState state = BOTS.remove(botId);
        if (state != null) {
            Entity entity = state.level.getEntity(botId);
            if (entity != null) entity.discard();
        }
        if (notify) owner.sendSystemMessage(Component.literal("PvP botu kaldırıldı."));
        return true;
    }

    public static void removeAll(MinecraftServer server) {
        for (BotState state : BOTS.values()) {
            Entity entity = state.level.getEntity(state.entityId);
            if (entity != null) entity.discard();
        }
        BOTS.clear();
        BOT_BY_OWNER.clear();
        METEORS.clear();
    }

    public static boolean pause(ServerPlayer owner, boolean paused) {
        BotState state = ownerState(owner);
        if (state == null) return false;
        state.paused = paused;
        Entity entity = state.level.getEntity(state.entityId);
        if (entity instanceof Husk bot) {
            bot.setTarget(paused ? null : owner);
            if (paused) bot.getNavigation().stop();
        }
        owner.sendSystemMessage(Component.literal("PvP botu: " + (paused ? "DURDURULDU" : "DEVAM EDİYOR")));
        return true;
    }

    public static void tick(MinecraftServer server) {
        Iterator<BotState> iterator = BOTS.values().iterator();
        while (iterator.hasNext()) {
            BotState state = iterator.next();
            Entity entity = state.level.getEntity(state.entityId);
            ServerPlayer owner = server.getPlayerList().getPlayer(state.ownerId);
            if (!(entity instanceof Husk bot) || bot.isRemoved() || !bot.isAlive() || owner == null) {
                if (owner != null) owner.sendSystemMessage(Component.literal("PvP botu yenildi veya kayboldu."));
                BOT_BY_OWNER.remove(state.ownerId);
                iterator.remove();
                continue;
            }
            long now = state.level.getGameTime();
            if (state.paused) {
                bot.getNavigation().stop();
                bot.setTarget(null);
                aura(bot, state, now, false);
                continue;
            }
            if (!owner.isAlive() || owner.isSpectator() || owner.level() != state.level) {
                bot.getNavigation().stop();
                continue;
            }
            bot.setTarget(owner);
            bot.getLookControl().setLookAt(owner, 35.0F, 35.0F);
            double distance = Math.sqrt(bot.distanceToSqr(owner));
            double preferred = preferredRange(state.powerClass);
            if (distance > preferred + 1.5) {
                bot.getNavigation().moveTo(owner, state.difficulty.speed);
            } else if (distance < Math.max(2.0, preferred - 2.0)) {
                Vec3 away = horizontal(bot.position().subtract(owner.position()));
                bot.setDeltaMovement(bot.getDeltaMovement().add(away.scale(0.11 + state.difficulty.ordinal() * 0.025)));
                bot.hurtMarked = true;
            } else if (now % 16L == 0L) {
                Vec3 side = horizontal(owner.position().subtract(bot.position())).cross(new Vec3(0.0, 1.0, 0.0));
                if (state.level.getRandom().nextBoolean()) side = side.scale(-1.0);
                bot.setDeltaMovement(bot.getDeltaMovement().add(side.scale(0.10 + state.difficulty.ordinal() * 0.02)));
                bot.hurtMarked = true;
            }

            if (state.awakeningUntil <= now && state.awakeningEnergy >= 100.0F) {
                state.awakeningEnergy = 0.0F;
                state.awakeningUntil = now + 240L;
                state.level.playSound(null, bot.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0F, classPitch(state.powerClass));
            }
            boolean awakened = state.awakeningUntil > now;
            aura(bot, state, now, awakened);
            if (awakened) {
                bot.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 1, false, false, true));
                bot.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 1, false, false, true));
            }

            if (distance <= 2.7 && now >= state.nextMeleeTick) {
                bot.swing(InteractionHand.MAIN_HAND);
                float damage = (5.0F + state.difficulty.ordinal() * 1.5F) * state.difficulty.damageMultiplier * (awakened ? 1.35F : 1.0F);
                owner.hurtServer(state.level, state.level.damageSources().mobAttack(bot), damage);
                state.nextMeleeTick = now + Math.max(10L, 24L - state.difficulty.ordinal() * 3L);
            }
            if (now >= state.nextAbilityTick) {
                useAbility(bot, owner, state, now, awakened);
                long jitter = state.level.getRandom().nextInt(12);
                state.nextAbilityTick = now + Math.max(12L, state.difficulty.reactionTicks + jitter - (awakened ? 7L : 0L));
            }
        }
        tickMeteors();
    }

    private static void aura(Husk bot, BotState state, long now, boolean awakened) {
        if (now % (awakened ? 2L : 5L) != 0L) return;
        state.level.sendParticles(classParticle(state.powerClass), bot.getX(), bot.getY() + 1.0, bot.getZ(), awakened ? 9 : 4, 0.55, 0.8, 0.55, 0.03);
        if (awakened && now % 10L == 0L) PowerSystem.drawExternalRing(state.level, bot.position(), 2.7, classParticle(state.powerClass), 32);
    }

    private static void useAbility(Husk bot, ServerPlayer target, BotState state, long now, boolean awakened) {
        int phase = state.abilityIndex++;
        switch (state.powerClass) {
            case WARDEN -> botWarden(bot, target, state, awakened, phase % 5);
            case FIRE -> botFire(bot, target, state, now, awakened, phase % 5);
            case NATURE -> botNature(bot, target, state, awakened, phase % 5);
            case ANOMALY -> botAnomaly(bot, target, state, now, awakened, phase % 5);
            case FLIGHT -> botDragon(bot, target, state, awakened, phase % 5);
            default -> { }
        }
    }


    private static void botWarden(Husk bot, ServerPlayer target, BotState state, boolean awakened, int phase) {
        switch (phase) {
            case 0 -> {
                bot.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, awakened ? 130 : 90, awakened ? 2 : 1, false, true, true));
                bot.addEffect(new MobEffectInstance(MobEffects.STRENGTH, awakened ? 130 : 90, awakened ? 2 : 1, false, true, true));
                state.level.sendParticles(ParticleTypes.SCULK_SOUL, bot.getX(), bot.getY() + 1.0, bot.getZ(), 55, 0.8, 1.0, 0.8, 0.06);
            }
            case 1 -> botQuake(bot, target, state, awakened);
            case 2 -> botSonic(bot, target, state, awakened);
            case 3 -> {
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, awakened ? 150 : 100, 0, false, false, true));
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, awakened ? 110 : 75, awakened ? 3 : 2, false, true, true));
                state.level.sendParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getY() + 1.0, target.getZ(), 48, 0.7, 1.0, 0.7, 0.05);
            }
            default -> {
                botQuake(bot, target, state, awakened);
                if (bot.distanceToSqr(target) > 20.0) botSonic(bot, target, state, awakened);
            }
        }
    }

    private static void botFire(Husk bot, ServerPlayer target, BotState state, long now, boolean awakened, int phase) {
        switch (phase) {
            case 0 -> {
                double radius = awakened ? 7.0 : 5.0;
                PowerSystem.drawExternalRing(state.level, bot.position(), radius, ParticleTypes.FLAME, 64);
                if (bot.distanceToSqr(target) <= radius * radius) {
                    target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 11.0F : 7.0F) * state.difficulty.damageMultiplier);
                    target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), awakened ? 180 : 100));
                }
            }
            case 1 -> botFireBurst(bot, target, state, awakened);
            case 2 -> {
                // Cehennem Küresi: görünür çekirdek hedefe doğru akar ve temas hasarı verir.
                Vec3 start = bot.getEyePosition();
                Vec3 dir = target.getEyePosition().subtract(start).normalize();
                for (double d = 0.5; d <= Math.min(22.0, Math.sqrt(bot.distanceToSqr(target)) + 1.5); d += 0.45) {
                    Vec3 point = start.add(dir.scale(d));
                    state.level.sendParticles(d % 0.9 < 0.45 ? ParticleTypes.LAVA : ParticleTypes.FLAME,
                        point.x, point.y, point.z, awakened ? 7 : 4, 0.22, 0.22, 0.22, 0.025);
                }
                target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 16.0F : 11.0F) * state.difficulty.damageMultiplier);
                target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), awakened ? 220 : 130));
            }
            case 3 -> launchMeteor(bot, target, state, now, awakened);
            default -> {
                botFireBurst(bot, target, state, true);
                launchMeteor(bot, target, state, now, awakened);
            }
        }
    }

    private static void botSonic(Husk bot, ServerPlayer target, BotState state, boolean awakened) {
        Vec3 start = bot.getEyePosition();
        Vec3 end = target.getEyePosition();
        Vec3 direction = end.subtract(start);
        double length = Math.max(0.1, direction.length());
        direction = direction.normalize();
        for (double d = 0.5; d <= length; d += 1.0) {
            Vec3 p = start.add(direction.scale(d));
            state.level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 16.0F : 11.0F) * state.difficulty.damageMultiplier);
        Vec3 push = horizontal(target.position().subtract(bot.position())).scale(awakened ? 1.7 : 1.1);
        target.push(push.x, awakened ? 0.48 : 0.25, push.z);
        state.level.playSound(null, bot.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.3F, 1.0F);
    }

    private static void botQuake(Husk bot, ServerPlayer target, BotState state, boolean awakened) {
        double radius = awakened ? 8.0 : 5.5;
        PowerSystem.drawExternalRing(state.level, bot.position(), radius, ParticleTypes.SCULK_SOUL, 60);
        if (bot.distanceToSqr(target) <= radius * radius) {
            target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 12.0F : 8.0F) * state.difficulty.damageMultiplier);
            Vec3 push = horizontal(target.position().subtract(bot.position())).scale(awakened ? 2.0 : 1.35);
            target.push(push.x, awakened ? 0.75 : 0.45, push.z);
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, awakened ? 90 : 55, 2, false, true, true));
        }
    }

    private static void botFireBurst(Husk bot, ServerPlayer target, BotState state, boolean awakened) {
        Vec3 start = bot.getEyePosition();
        Vec3 direction = target.getEyePosition().subtract(start).normalize();
        double range = Math.min(24.0, Math.sqrt(bot.distanceToSqr(target)) + 2.0);
        for (double d = 0.7; d <= range; d += 0.55) {
            Vec3 p = start.add(direction.scale(d));
            state.level.sendParticles(d % 1.1 < 0.55 ? ParticleTypes.FLAME : ParticleTypes.LAVA, p.x, p.y, p.z, awakened ? 5 : 3, 0.18, 0.18, 0.18, 0.02);
        }
        target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 14.0F : 9.0F) * state.difficulty.damageMultiplier);
        target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), awakened ? 180 : 100));
        state.level.playSound(null, bot.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.1F, 0.72F);
    }

    private static void launchMeteor(Husk bot, ServerPlayer target, BotState state, long now, boolean awakened) {
        Vec3 impact = target.position();
        Vec3 start = impact.add((state.level.getRandom().nextDouble() - 0.5) * 7.0, awakened ? 24.0 : 18.0, (state.level.getRandom().nextDouble() - 0.5) * 7.0);
        METEORS.add(new BotMeteor(state.level, bot.getUUID(), state.ownerId, start, impact, now, now + (awakened ? 26L : 36L), awakened, state.difficulty));
        state.level.playSound(null, bot.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.65F, 1.45F);
    }

    private static void tickMeteors() {
        Iterator<BotMeteor> iterator = METEORS.iterator();
        while (iterator.hasNext()) {
            BotMeteor meteor = iterator.next();
            long now = meteor.level.getGameTime();
            if (now >= meteor.impactTick) {
                Entity caster = meteor.level.getEntity(meteor.botId);
                ServerPlayer target = meteor.level.getServer().getPlayerList().getPlayer(meteor.targetId);
                Vec3 impact = target != null && target.isAlive() ? target.position() : meteor.impact;
                meteor.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, impact.x, impact.y + 0.4, impact.z, 1, 0, 0, 0, 0);
                meteor.level.sendParticles(ParticleTypes.FLAME, impact.x, impact.y + 0.7, impact.z, meteor.awakened ? 100 : 65, 2.2, 1.1, 2.2, 0.11);
                if (target != null && target.distanceToSqr(impact) < (meteor.awakened ? 49.0 : 30.0) && caster instanceof LivingEntity living) {
                    target.hurtServer(meteor.level, meteor.level.damageSources().mobAttack(living), (meteor.awakened ? 18.0F : 12.0F) * meteor.difficulty.damageMultiplier);
                    Vec3 push = horizontal(target.position().subtract(impact)).scale(meteor.awakened ? 2.1 : 1.4);
                    target.push(push.x, meteor.awakened ? 0.9 : 0.55, push.z);
                }
                iterator.remove();
                continue;
            }
            double p = Math.max(0.0, Math.min(1.0, (now - meteor.startTick) / (double) Math.max(1L, meteor.impactTick - meteor.startTick)));
            Vec3 pos = meteor.start.lerp(meteor.impact.add(0.0, 0.7, 0.0), p * p);
            meteor.level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, meteor.awakened ? 12 : 7, 0.45, 0.45, 0.45, 0.035);
            meteor.level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.3, pos.z, meteor.awakened ? 7 : 4, 0.55, 0.55, 0.55, 0.02);
        }
    }

    private static void botNature(Husk bot, ServerPlayer target, BotState state, boolean awakened, int phase) {
        if (phase == 2 && bot.getHealth() < bot.getMaxHealth() * 0.78F) {
            bot.heal(awakened ? 12.0F : 7.0F);
            bot.addEffect(new MobEffectInstance(MobEffects.REGENERATION, awakened ? 120 : 80, awakened ? 2 : 1, false, true, true));
            state.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, bot.getX(), bot.getY() + 0.8, bot.getZ(), 52, 0.9, 1.1, 0.9, 0.08);
            return;
        }
        if (phase == 3) {
            // Yaşam Ağacı karşılığı: botun çevresinde iyileştirme alanı ve mermi saptırma.
            bot.heal(awakened ? 8.0F : 4.0F);
            for (net.minecraft.world.entity.projectile.Projectile projectile : state.level.getEntitiesOfClass(
                net.minecraft.world.entity.projectile.Projectile.class, bot.getBoundingBox().inflate(5.5))) {
                Vec3 away = projectile.position().subtract(bot.position());
                if (away.lengthSqr() > 0.001) projectile.setDeltaMovement(away.normalize().scale(1.15));
            }
            PowerSystem.drawExternalRing(state.level, bot.position(), awakened ? 6.5 : 4.5, ParticleTypes.HAPPY_VILLAGER, 54);
            return;
        }
        float damage = switch (phase) {
            case 0 -> awakened ? 9.0F : 5.5F;
            case 1 -> awakened ? 11.0F : 7.0F;
            default -> awakened ? 14.0F : 9.0F;
        };
        target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), damage * state.difficulty.damageMultiplier);
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, awakened ? 120 : 75, phase >= 4 ? 6 : 4, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.POISON, awakened ? 110 : 65, phase >= 4 ? 1 : 0, false, true, true));
        int particles = phase >= 4 ? 72 : 38;
        for (int i = 0; i < particles; i++) {
            double angle = Math.PI * 2.0 * i / particles;
            double radius = phase >= 4 ? 2.8 : 1.8;
            state.level.sendParticles(i % 3 == 0 ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.COMPOSTER,
                target.getX() + Math.cos(angle) * radius, target.getY() + 0.3,
                target.getZ() + Math.sin(angle) * radius, 2, 0.08, 0.45, 0.08, 0.02);
        }
    }

    private static void botAnomaly(Husk bot, ServerPlayer target, BotState state, long now, boolean awakened, int phase) {
        if (phase == 0) {
            Vec3 behind = target.position().subtract(horizontal(target.getLookAngle()).scale(2.2));
            bot.setPos(behind.x, behind.y, behind.z);
            bot.setDeltaMovement(Vec3.ZERO);
            target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 12.0F : 7.0F) * state.difficulty.damageMultiplier);
            state.level.sendParticles(ParticleTypes.REVERSE_PORTAL, bot.getX(), bot.getY() + 1.0, bot.getZ(), 55, 0.7, 1.0, 0.7, 0.12);
        } else if (phase == 1) {
            Vec3 motion = target.getDeltaMovement();
            target.setDeltaMovement(-motion.x * 1.35, Math.min(0.28, -motion.y * 0.55 + 0.12), -motion.z * 1.35);
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, awakened ? 120 : 80, awakened ? 6 : 4, false, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, awakened ? 120 : 80, awakened ? 3 : 2, false, true, true));
            target.hurtMarked = true;
            state.level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 1.0, target.getZ(), 60, 0.9, 1.0, 0.9, 0.12);
        } else if (phase == 2) {
            // Hasar Mevcut Değil: gelen bir sonraki saldırıları kısa süre geri yollar.
            state.anomalyShieldUntil = now + (awakened ? 120L : 75L);
            state.level.sendParticles(ParticleTypes.WITCH, bot.getX(), bot.getY() + 1.0, bot.getZ(), 75, 1.0, 1.2, 1.0, 0.10);
        } else if (phase == 3) {
            // Varlıktan Çıkar'ın bot sürümü: hedefi kısa süre etkisizleştirir, sonra çöküş hasarı verir.
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, awakened ? 70 : 45, 0, false, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, awakened ? 70 : 45, 10, false, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, awakened ? 90 : 60, 4, false, true, true));
            target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 13.0F : 8.0F) * state.difficulty.damageMultiplier);
            state.level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 1.0, target.getZ(), 90, 1.0, 1.2, 1.0, 0.15);
        } else {
            // 404 alanı: mermiler döner, rakip zayıflar ve alan hasarı alır.
            double radius = awakened ? 10.0 : 7.0;
            PowerSystem.drawExternalRing(state.level, bot.position(), radius, ParticleTypes.WITCH, 78);
            for (net.minecraft.world.entity.projectile.Projectile projectile : state.level.getEntitiesOfClass(
                net.minecraft.world.entity.projectile.Projectile.class, bot.getBoundingBox().inflate(radius))) {
                if (projectile.getOwner() == bot) continue;
                Vec3 velocity = projectile.getDeltaMovement();
                projectile.setDeltaMovement(velocity.scale(-1.2));
                projectile.setOwner(bot);
            }
            if (bot.distanceToSqr(target) <= radius * radius) {
                target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 15.0F : 9.0F) * state.difficulty.damageMultiplier);
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, awakened ? 120 : 80, 3, false, true, true));
            }
        }
    }

    private static void botDragon(Husk bot, ServerPlayer target, BotState state, boolean awakened, int phase) {
        if (phase == 0 && bot.distanceToSqr(target) < 90.0) {
            double radius = awakened ? 7.5 : 5.2;
            PowerSystem.drawExternalRing(state.level, bot.position(), radius, ParticleTypes.REVERSE_PORTAL, 65);
            if (bot.distanceToSqr(target) <= radius * radius) {
                target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 13.0F : 8.0F) * state.difficulty.damageMultiplier);
                Vec3 push = horizontal(target.position().subtract(bot.position())).scale(awakened ? 2.5 : 1.8);
                target.push(push.x, awakened ? 1.0 : 0.65, push.z);
            }
        } else if (phase == 1) {
            Vec3 start = bot.getEyePosition();
            Vec3 dir = target.getEyePosition().subtract(start).normalize();
            for (double d = 0.5; d <= 18.0; d += 0.5) {
                Vec3 p = start.add(dir.scale(d));
                state.level.sendParticles(d % 1.0 < 0.5 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.WITCH, p.x, p.y, p.z, awakened ? 5 : 3, 0.23, 0.23, 0.23, 0.02);
            }
            target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 15.0F : 10.0F) * state.difficulty.damageMultiplier);
        } else if (phase == 2) {
            state.guardCharges = awakened ? 5 : 3;
            state.anomalyShieldUntil = state.level.getGameTime() + (awakened ? 150L : 100L);
            state.level.sendParticles(ParticleTypes.REVERSE_PORTAL, bot.getX(), bot.getY() + 1.0, bot.getZ(), 85, 1.0, 1.2, 1.0, 0.08);
        } else if (phase == 3) {
            Vec3 pull = horizontal(bot.position().subtract(target.position())).scale(1.8);
            target.push(pull.x, 0.45, pull.z);
            target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 12.0F : 8.0F) * state.difficulty.damageMultiplier);
            state.level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 1.0, target.getZ(), 45, 0.65, 0.9, 0.65, 0.08);
        } else {
            Vec3 push = horizontal(target.position().subtract(bot.position())).scale(awakened ? 3.2 : 2.3);
            target.push(push.x, awakened ? 1.25 : 0.85, push.z);
            target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 11.0F : 7.0F) * state.difficulty.damageMultiplier);
            state.level.playSound(null, bot.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.3F, 1.4F);
        }
    }

    public static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
        if (reflecting) return true;
        BotState victimState = BOTS.get(victim.getUUID());
        Entity attacker = source.getEntity();
        BotState attackerState = attacker == null ? null : BOTS.get(attacker.getUUID());
        if (victimState != null) {
            victimState.awakeningEnergy = Math.min(100.0F, victimState.awakeningEnergy + Math.max(1.0F, amount * 1.15F));
            long now = victim.level().getGameTime();
            if (victimState.powerClass == PowerClass.ANOMALY && victimState.anomalyShieldUntil > now && attacker instanceof LivingEntity living) {
                try {
                    reflecting = true;
                    living.hurtServer(victimState.level, victimState.level.damageSources().mobAttack(victim), Math.max(2.0F, amount * 0.8F));
                } finally {
                    reflecting = false;
                }
                victimState.level.sendParticles(ParticleTypes.WITCH, victim.getX(), victim.getY() + 1.0, victim.getZ(), 38, 0.7, 0.9, 0.7, 0.08);
                return false;
            }
            if (victimState.powerClass == PowerClass.FLIGHT && victimState.anomalyShieldUntil > now && victimState.guardCharges > 0) {
                victimState.guardCharges--;
                if (attacker instanceof LivingEntity living) {
                    try {
                        reflecting = true;
                        living.hurtServer(victimState.level, victimState.level.damageSources().mobAttack(victim), Math.max(2.0F, amount * 0.45F));
                    } finally {
                        reflecting = false;
                    }
                }
                victimState.level.sendParticles(ParticleTypes.REVERSE_PORTAL, victim.getX(), victim.getY() + 1.0, victim.getZ(), 42, 0.8, 1.0, 0.8, 0.08);
                return false;
            }
        }
        if (attackerState != null) attackerState.awakeningEnergy = Math.min(100.0F, attackerState.awakeningEnergy + Math.max(1.0F, amount * 1.35F));
        return true;
    }

    public static BattlePanel panelFor(ServerPlayer player) {
        BotState state = ownerState(player);
        if (state == null) {
            for (BotState candidate : BOTS.values()) {
                Entity entity = candidate.level.getEntity(candidate.entityId);
                if (candidate.ownerId.equals(player.getUUID()) || !(entity instanceof Husk bot) || bot.getTarget() != player) continue;
                state = candidate;
                break;
            }
        }
        if (state == null) return BattlePanel.hidden();
        Entity entity = state.level.getEntity(state.entityId);
        if (!(entity instanceof Husk bot) || !bot.isAlive()) return BattlePanel.hidden();
        long now = state.level.getGameTime();
        String detail = state.paused ? "Durduruldu" : state.awakeningUntil > now ? "UYANIŞ AKTİF" : state.difficulty.displayName();
        float awakening = state.awakeningUntil > now
            ? Math.max(0.0F, Math.min(100.0F, (state.awakeningUntil - now) / 2.4F))
            : state.awakeningEnergy;
        return new BattlePanel(true, "BOT", bot.getName().getString(), state.powerClass.displayName(), bot.getHealth(), bot.getMaxHealth(), awakening, detail);
    }

    private static BotState ownerState(ServerPlayer owner) {
        UUID botId = BOT_BY_OWNER.get(owner.getUUID());
        return botId == null ? null : BOTS.get(botId);
    }

    private static double preferredRange(PowerClass powerClass) {
        return switch (powerClass) {
            case FIRE -> 10.0;
            case NATURE -> 8.0;
            case ANOMALY -> 6.5;
            case WARDEN -> 5.0;
            case FLIGHT -> 6.0;
            default -> 4.0;
        };
    }

    private static Vec3 safeSpawn(ServerLevel level, Vec3 candidate) {
        BlockPos base = BlockPos.containing(candidate);
        for (int dy = 4; dy >= -4; dy--) {
            BlockPos feet = base.offset(0, dy, 0);
            if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir() && !level.getBlockState(feet.below()).isAir()) {
                return new Vec3(candidate.x, feet.getY(), candidate.z);
            }
        }
        return candidate.add(0.0, 1.0, 0.0);
    }

    private static Vec3 horizontal(Vec3 vector) {
        Vec3 flat = new Vec3(vector.x, 0.0, vector.z);
        return flat.lengthSqr() < 0.0001 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
    }

    private static net.minecraft.core.particles.SimpleParticleType classParticle(PowerClass powerClass) {
        return switch (powerClass) {
            case WARDEN -> ParticleTypes.SCULK_SOUL;
            case FIRE -> ParticleTypes.FLAME;
            case NATURE -> ParticleTypes.HAPPY_VILLAGER;
            case ANOMALY -> ParticleTypes.WITCH;
            case FLIGHT -> ParticleTypes.REVERSE_PORTAL;
            default -> ParticleTypes.CLOUD;
        };
    }

    private static float classPitch(PowerClass powerClass) {
        return switch (powerClass) {
            case WARDEN -> 0.65F;
            case FIRE -> 1.25F;
            case NATURE -> 1.45F;
            case ANOMALY -> 0.55F;
            case FLIGHT -> 0.82F;
            default -> 1.0F;
        };
    }

    private static final class BotState {
        private final UUID entityId;
        private final UUID ownerId;
        private final ServerLevel level;
        private final PowerClass powerClass;
        private final BotDifficulty difficulty;
        private long nextAbilityTick;
        private long nextMeleeTick;
        private int abilityIndex;
        private float awakeningEnergy;
        private long awakeningUntil;
        private long anomalyShieldUntil;
        private int guardCharges;
        private boolean paused;

        private BotState(UUID entityId, UUID ownerId, ServerLevel level, PowerClass powerClass, BotDifficulty difficulty, long nextAbilityTick) {
            this.entityId = entityId;
            this.ownerId = ownerId;
            this.level = level;
            this.powerClass = powerClass;
            this.difficulty = difficulty;
            this.nextAbilityTick = nextAbilityTick;
        }
    }

    private record BotMeteor(ServerLevel level, UUID botId, UUID targetId, Vec3 start, Vec3 impact,
                             long startTick, long impactTick, boolean awakened, BotDifficulty difficulty) {}
}
