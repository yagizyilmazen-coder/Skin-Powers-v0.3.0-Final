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
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.item.ItemEntity;
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
    private static final ArrayList<BotAttackVisual> ATTACK_VISUALS = new ArrayList<>();
    private static boolean reflecting;

    private PvpBotSystem() {}

    public enum BotDifficulty {
        EASY("Kolay", 52L, 0.68F, 0.45F),
        NORMAL("Normal", 38L, 0.84F, 0.68F),
        HARD("Zor", 28L, 0.94F, 0.86F),
        NIGHTMARE("Kâbus", 22L, 0.98F, 0.96F);

        private final String displayName;
        private final long reactionTicks;
        private final float aimAccuracy;
        private final float tacticalChance;

        BotDifficulty(String displayName, long reactionTicks, float aimAccuracy, float tacticalChance) {
            this.displayName = displayName;
            this.reactionTicks = reactionTicks;
            this.aimAccuracy = aimAccuracy;
            this.tacticalChance = tacticalChance;
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
        // Zorluk yalnızca karar kalitesini değiştirir; can, hız ve hasar oyuncuyla aynı temelde kalır.
        if (bot.getAttribute(Attributes.MAX_HEALTH) != null) bot.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0D);
        if (bot.getAttribute(Attributes.MOVEMENT_SPEED) != null) bot.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.23D);
        if (bot.getAttribute(Attributes.ATTACK_DAMAGE) != null) bot.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0D);
        if (bot.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) bot.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.10D);
        bot.setHealth(20.0F);
    }

    private static void equip(Husk bot, PowerClass powerClass) {
        // Bütün botlarda aynı zırh ve ana silah vardır; sınıf farkı güçlerden ve parçacıklardan gelir.
        // Doğal Husk saldırısı ekstra hasar vermesin; bütün yakın dövüş hasarını bu sistem eşit uygular.
        bot.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        ItemStack focus = switch (powerClass) {
            case WARDEN -> new ItemStack(Items.ECHO_SHARD);
            case FLIGHT -> new ItemStack(Items.AMETHYST_SHARD);
            case FIRE -> new ItemStack(Items.BLAZE_ROD);
            case NATURE -> new ItemStack(Items.SPORE_BLOSSOM);
            case ANOMALY -> new ItemStack(Items.ENDER_EYE);
            default -> ItemStack.EMPTY;
        };
        bot.setItemSlot(EquipmentSlot.OFFHAND, focus);
        bot.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        bot.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        bot.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        bot.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
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
        for (BotAttackVisual visual : ATTACK_VISUALS) {
            Entity entity = visual.level.getEntity(visual.entityId);
            if (entity != null) entity.discard();
        }
        BOTS.clear();
        BOT_BY_OWNER.clear();
        METEORS.clear();
        ATTACK_VISUALS.clear();
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
            bot.getLookControl().setLookAt(owner, 30.0F, 30.0F);
            double distance = Math.sqrt(bot.distanceToSqr(owner));
            double preferred = preferredRange(state.powerClass);
            if (distance > preferred + 1.25) {
                bot.getNavigation().moveTo(owner, 1.0D);
            } else if (distance < Math.max(2.0, preferred - 1.75)) {
                Vec3 away = horizontal(bot.position().subtract(owner.position()));
                bot.setDeltaMovement(bot.getDeltaMovement().add(away.scale(0.10)));
                bot.hurtMarked = true;
            } else if (now % 18L == 0L) {
                Vec3 side = horizontal(owner.position().subtract(bot.position())).cross(new Vec3(0.0, 1.0, 0.0));
                if (state.level.getRandom().nextBoolean()) side = side.scale(-1.0);
                bot.setDeltaMovement(bot.getDeltaMovement().add(side.scale(0.09)));
                bot.hurtMarked = true;
            }

            maybeActivateAwakening(bot, owner, state, now);
            boolean awakened = state.awakeningUntil > now;
            aura(bot, state, now, awakened);
            if (awakened) {
                bot.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 1, false, false, true));
                bot.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 1, false, false, true));
            }

            if (distance <= 2.7 && now >= state.nextMeleeTick) {
                bot.swing(InteractionHand.MAIN_HAND);
                float damage = awakened ? 5.0F : 4.0F;
                owner.hurtServer(state.level, state.level.damageSources().mobAttack(bot), damage);
                state.nextMeleeTick = now + 20L;
            }

            if (now >= state.nextDecisionTick) {
                int ability = chooseAbility(bot, owner, state, now, awakened);
                if (ability > 0) useAbility(bot, owner, state, now, awakened, ability);
                state.nextDecisionTick = now + state.difficulty.reactionTicks + state.level.getRandom().nextInt(8);
            }
        }
        tickAttackVisuals();
        tickMeteors();
    }

    private static void maybeActivateAwakening(Husk bot, ServerPlayer target, BotState state, long now) {
        if (state.awakeningUntil > now || state.awakeningEnergy < 20.0F) return;
        boolean urgent = bot.getHealth() <= bot.getMaxHealth() * 0.55F || state.awakeningEnergy >= 85.0F;
        boolean tactical = state.level.getRandom().nextFloat() < state.difficulty.tacticalChance * 0.08F
            && bot.distanceToSqr(target) <= 14.0 * 14.0;
        if (!urgent && !tactical) return;
        int duration = Math.max(48, Math.round(state.awakeningEnergy * 2.4F));
        state.awakeningEnergy = 0.0F;
        state.awakeningUntil = now + duration;
        state.level.playSound(null, bot.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0F, classPitch(state.powerClass));
    }

    private static int chooseAbility(Husk bot, ServerPlayer target, BotState state, long now, boolean awakened) {
        // Bot da oyuncu gibi bir gücü kullandıktan sonra kısa bir kullanım animasyonunu tamamlar.
        // Bu ortak toparlanma süresi, farklı güçleri arka arkaya aynı anda basmasını engeller.
        if (now < state.globalRecoveryUntil) return 0;
        ArrayList<Integer> usable = new ArrayList<>();
        for (int ability = 1; ability <= 5; ability++) {
            if (now < state.cooldownUntil[ability - 1]) continue;
            if (abilityUsable(bot, target, state, ability)) usable.add(ability);
        }
        if (usable.isEmpty()) return 0;

        int tactical = tacticalAbility(bot, target, state, usable);
        if (tactical > 0 && state.level.getRandom().nextFloat() <= state.difficulty.tacticalChance) return tactical;
        return usable.get(state.level.getRandom().nextInt(usable.size()));
    }

    private static int tacticalAbility(Husk bot, ServerPlayer target, BotState state, ArrayList<Integer> usable) {
        double distance = Math.sqrt(bot.distanceToSqr(target));
        int preferred = switch (state.powerClass) {
            case WARDEN -> bot.getHealth() < 12.0F && usable.contains(1) ? 1
                : distance <= 6.5 && usable.contains(2) ? 2
                : distance >= 6.0 && usable.contains(3) ? 3
                : usable.contains(4) ? 4 : usable.get(0);
            case FIRE -> distance <= 5.5 && usable.contains(1) ? 1
                : distance >= 8.0 && usable.contains(4) ? 4
                : distance >= 6.0 && usable.contains(3) ? 3
                : usable.contains(2) ? 2 : usable.get(0);
            case NATURE -> bot.getHealth() < 9.0F && usable.contains(3) ? 3
                : bot.getHealth() < 14.0F && usable.contains(4) ? 4
                : distance <= 11.5 && usable.contains(2) ? 2
                : distance >= 8.0 && usable.contains(5) ? 5
                : usable.contains(1) ? 1 : usable.get(0);
            case ANOMALY -> hasIncomingProjectile(bot, state) && usable.contains(3) ? 3
                : bot.getHealth() < 10.0F && usable.contains(3) ? 3
                : distance <= 6.5 && usable.contains(1) ? 1
                : distance <= 8.0 && usable.contains(5) ? 5
                : distance <= 10.0 && usable.contains(4) ? 4
                : usable.contains(2) ? 2 : usable.get(0);
            case FLIGHT -> hasIncomingProjectile(bot, state) && usable.contains(3) ? 3
                : bot.getHealth() < 10.0F && usable.contains(3) ? 3
                : distance <= 5.8 && usable.contains(1) ? 1
                : distance <= 7.0 && usable.contains(5) ? 5
                : distance <= 8.0 && usable.contains(4) ? 4
                : usable.contains(2) ? 2 : usable.get(0);
            default -> usable.get(0);
        };
        return preferred;
    }

    private static boolean abilityUsable(Husk bot, ServerPlayer target, BotState state, int ability) {
        double range = abilityRange(state.powerClass, ability);
        if (range > 0.0 && bot.distanceToSqr(target) > range * range) return false;
        if (requiresLineOfSight(state.powerClass, ability) && !bot.hasLineOfSight(target)) return false;
        if (isSelfAbility(state.powerClass, ability)) {
            if (state.powerClass == PowerClass.NATURE && ability == 3 && bot.getHealth() >= bot.getMaxHealth() * 0.78F) return false;
            if ((state.powerClass == PowerClass.ANOMALY || state.powerClass == PowerClass.FLIGHT) && ability == 3
                && bot.getHealth() >= bot.getMaxHealth() * 0.75F && !hasIncomingProjectile(bot, state)) return false;
        }
        return true;
    }

    private static boolean hasIncomingProjectile(Husk bot, BotState state) {
        for (net.minecraft.world.entity.projectile.Projectile projectile : state.level.getEntitiesOfClass(
            net.minecraft.world.entity.projectile.Projectile.class, bot.getBoundingBox().inflate(8.0))) {
            if (projectile.getOwner() == bot) continue;
            Vec3 towardBot = bot.getEyePosition().subtract(projectile.position());
            if (towardBot.lengthSqr() > 0.001 && projectile.getDeltaMovement().dot(towardBot.normalize()) > 0.12) return true;
        }
        return false;
    }

    private static boolean isSelfAbility(PowerClass powerClass, int ability) {
        return switch (powerClass) {
            case WARDEN -> ability == 1;
            case NATURE -> ability == 3 || ability == 4;
            case ANOMALY, FLIGHT -> ability == 3;
            default -> false;
        };
    }

    private static boolean requiresLineOfSight(PowerClass powerClass, int ability) {
        return switch (powerClass) {
            case WARDEN -> ability == 3;
            case FIRE -> ability == 2 || ability == 3 || ability == 4 || ability == 5;
            case NATURE -> ability == 1 || ability == 2 || ability == 5;
            case ANOMALY -> ability == 1 || ability == 2 || ability == 4;
            case FLIGHT -> ability == 2 || ability == 4;
            default -> false;
        };
    }

    private static boolean requiresAim(PowerClass powerClass, int ability) {
        return switch (powerClass) {
            case WARDEN -> ability == 3;
            case FIRE -> ability == 2 || ability == 3;
            case NATURE -> ability == 1 || ability == 2 || ability == 5;
            case ANOMALY -> ability == 1 || ability == 2 || ability == 4;
            case FLIGHT -> ability == 2 || ability == 4;
            default -> false;
        };
    }

    private static double abilityRange(PowerClass powerClass, int ability) {
        return switch (powerClass) {
            case WARDEN -> new double[]{0.0, 7.0, 18.0, 14.0, 10.0}[ability - 1];
            case FIRE -> new double[]{5.5, 12.0, 18.0, 18.0, 18.0}[ability - 1];
            case NATURE -> new double[]{16.0, 12.0, 0.0, 0.0, 16.0}[ability - 1];
            case ANOMALY -> new double[]{7.0, 12.0, 0.0, 10.0, 8.0}[ability - 1];
            case FLIGHT -> new double[]{6.5, 12.0, 0.0, 8.0, 7.0}[ability - 1];
            default -> 0.0;
        };
    }

    private static long abilityCooldown(PowerClass powerClass, int ability, boolean awakened) {
        // Değerler oyuncuların ustalıksız temel cooldownlarıyla aynıdır.
        // Uyanış bütün sınıfların cooldownunu hileli biçimde azaltmaz; oyuncuda olduğu gibi
        // yalnızca Anomali/Sistem Çökmesi cooldownları hızlandırabilir.
        long base = switch (powerClass) {
            case WARDEN -> new long[]{900L, 600L, 380L, 900L, 2400L}[ability - 1];
            case FIRE -> new long[]{600L, 360L, 360L, 2400L, 2400L}[ability - 1];
            case NATURE -> new long[]{125L, 320L, 700L, 700L, 900L}[ability - 1];
            case ANOMALY -> new long[]{130L, 420L, 360L, 980L, 980L}[ability - 1];
            case FLIGHT -> new long[]{190L, 390L, 760L, 430L, 760L}[ability - 1];
            default -> 200L;
        };
        return awakened && powerClass == PowerClass.ANOMALY
            ? Math.max(40L, Math.round(base * 0.65D))
            : base;
    }

    private static void renderMiss(Husk bot, ServerPlayer target, BotState state) {
        Vec3 start = bot.getEyePosition();
        double spread = 1.0 + (1.0F - state.difficulty.aimAccuracy) * 5.0;
        Vec3 miss = target.getEyePosition().add(
            (state.level.getRandom().nextDouble() - 0.5) * spread,
            (state.level.getRandom().nextDouble() - 0.5) * spread * 0.55,
            (state.level.getRandom().nextDouble() - 0.5) * spread
        );
        Vec3 direction = miss.subtract(start);
        double length = Math.min(14.0, Math.max(1.0, direction.length()));
        direction = direction.normalize();
        for (double d = 0.7; d <= length; d += 0.8) {
            Vec3 point = start.add(direction.scale(d));
            state.level.sendParticles(classParticle(state.powerClass), point.x, point.y, point.z, 2, 0.12, 0.12, 0.12, 0.015);
        }
    }

    private static void aura(Husk bot, BotState state, long now, boolean awakened) {
        if (now % (awakened ? 2L : 5L) != 0L) return;
        state.level.sendParticles(classParticle(state.powerClass), bot.getX(), bot.getY() + 1.0, bot.getZ(), awakened ? 9 : 4, 0.55, 0.8, 0.55, 0.03);
        if (awakened && now % 10L == 0L) PowerSystem.drawExternalRing(state.level, bot.position(), 2.7, classParticle(state.powerClass), 32);
    }

    private static void useAbility(Husk bot, ServerPlayer target, BotState state, long now, boolean awakened, int ability) {
        state.cooldownUntil[ability - 1] = now + abilityCooldown(state.powerClass, ability, awakened);
        state.globalRecoveryUntil = now + 30L;
        spawnCastVisuals(bot, state, ability, awakened);
        if (requiresAim(state.powerClass, ability) && state.level.getRandom().nextFloat() > state.difficulty.aimAccuracy) {
            renderMiss(bot, target, state);
            return;
        }
        int phase = ability - 1;
        switch (state.powerClass) {
            case WARDEN -> botWarden(bot, target, state, awakened, phase);
            case FIRE -> botFire(bot, target, state, now, awakened, phase);
            case NATURE -> botNature(bot, target, state, awakened, phase);
            case ANOMALY -> botAnomaly(bot, target, state, now, awakened, phase);
            case FLIGHT -> botDragon(bot, target, state, awakened, phase);
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
                spawnTravelVisual(state, bot.getEyePosition(), target.getEyePosition(), new ItemStack(Items.SCULK_SENSOR), 13L, 0.35);
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, awakened ? 150 : 100, 0, false, false, true));
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, awakened ? 110 : 75, awakened ? 3 : 2, false, true, true));
                state.level.sendParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getY() + 1.0, target.getZ(), 48, 0.7, 1.0, 0.7, 0.05);
            }
            default -> {
                // Warden Uyanışı gücü: ek saldırı hilesi yerine oyuncudaki gibi süreli savaş güçlendirmesi.
                bot.addEffect(new MobEffectInstance(MobEffects.STRENGTH, awakened ? 300 : 220, awakened ? 2 : 1, false, true, true));
                bot.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, awakened ? 300 : 220, awakened ? 2 : 1, false, true, true));
                bot.addEffect(new MobEffectInstance(MobEffects.REGENERATION, awakened ? 160 : 100, 1, false, true, true));
                state.level.sendParticles(ParticleTypes.SCULK_SOUL, bot.getX(), bot.getY() + 1.0, bot.getZ(), 85, 1.1, 1.2, 1.1, 0.06);
            }
        }
    }

    private static void botFire(Husk bot, ServerPlayer target, BotState state, long now, boolean awakened, int phase) {
        switch (phase) {
            case 0 -> {
                double radius = awakened ? 7.0 : 5.0;
                PowerSystem.drawExternalRing(state.level, bot.position(), radius, ParticleTypes.FLAME, 64);
                if (bot.distanceToSqr(target) <= radius * radius) {
                    target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 11.0F : 7.0F));
                    target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), awakened ? 180 : 100));
                }
            }
            case 1 -> botFireBurst(bot, target, state, awakened);
            case 2 -> {
                // Cehennem Küresi: gerçek eşya gövdesi hedefe doğru akar; parçacıklar yalnızca izidir.
                Vec3 start = bot.getEyePosition();
                spawnTravelVisual(state, start, target.getEyePosition(), new ItemStack(Items.MAGMA_CREAM), awakened ? 10L : 14L, 0.55);
                Vec3 dir = target.getEyePosition().subtract(start).normalize();
                for (double d = 0.5; d <= Math.min(22.0, Math.sqrt(bot.distanceToSqr(target)) + 1.5); d += 0.45) {
                    Vec3 point = start.add(dir.scale(d));
                    state.level.sendParticles(d % 0.9 < 0.45 ? ParticleTypes.LAVA : ParticleTypes.FLAME,
                        point.x, point.y, point.z, awakened ? 7 : 4, 0.22, 0.22, 0.22, 0.025);
                }
                target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 16.0F : 11.0F));
                target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), awakened ? 220 : 130));
            }
            case 3 -> launchMeteor(bot, target, state, now, awakened);
            default -> {
                // Ateş pasifleri: fazladan birleşik saldırı yerine oyuncuyla aynı savunma/yakın dövüş hazırlığı.
                bot.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 260, 0, false, true, true));
                bot.addEffect(new MobEffectInstance(MobEffects.STRENGTH, awakened ? 180 : 120, awakened ? 2 : 1, false, true, true));
                state.level.sendParticles(ParticleTypes.FLAME, bot.getX(), bot.getY() + 1.0, bot.getZ(), 58, 0.9, 1.0, 0.9, 0.055);
            }
        }
    }

    private static void botSonic(Husk bot, ServerPlayer target, BotState state, boolean awakened) {
        Vec3 start = bot.getEyePosition();
        Vec3 end = target.getEyePosition();
        spawnTravelVisual(state, start, end, new ItemStack(Items.ECHO_SHARD), awakened ? 7L : 10L, 0.15);
        Vec3 direction = end.subtract(start);
        double length = Math.max(0.1, direction.length());
        direction = direction.normalize();
        for (double d = 0.5; d <= length; d += 1.0) {
            Vec3 p = start.add(direction.scale(d));
            state.level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 16.0F : 11.0F));
        Vec3 push = horizontal(target.position().subtract(bot.position())).scale(awakened ? 1.7 : 1.1);
        target.push(push.x, awakened ? 0.48 : 0.25, push.z);
        state.level.playSound(null, bot.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.3F, 1.0F);
    }

    private static void botQuake(Husk bot, ServerPlayer target, BotState state, boolean awakened) {
        double radius = awakened ? 8.0 : 5.5;
        PowerSystem.drawExternalRing(state.level, bot.position(), radius, ParticleTypes.SCULK_SOUL, 60);
        if (bot.distanceToSqr(target) <= radius * radius) {
            target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 12.0F : 8.0F));
            Vec3 push = horizontal(target.position().subtract(bot.position())).scale(awakened ? 2.0 : 1.35);
            target.push(push.x, awakened ? 0.75 : 0.45, push.z);
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, awakened ? 90 : 55, 2, false, true, true));
        }
    }

    private static void botFireBurst(Husk bot, ServerPlayer target, BotState state, boolean awakened) {
        Vec3 start = bot.getEyePosition();
        spawnTravelVisual(state, start, target.getEyePosition(), new ItemStack(Items.FIRE_CHARGE), awakened ? 8L : 12L, 0.35);
        Vec3 direction = target.getEyePosition().subtract(start).normalize();
        double range = Math.min(24.0, Math.sqrt(bot.distanceToSqr(target)) + 2.0);
        for (double d = 0.7; d <= range; d += 0.55) {
            Vec3 p = start.add(direction.scale(d));
            state.level.sendParticles(d % 1.1 < 0.55 ? ParticleTypes.FLAME : ParticleTypes.LAVA, p.x, p.y, p.z, awakened ? 5 : 3, 0.18, 0.18, 0.18, 0.02);
        }
        target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 14.0F : 9.0F));
        target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), awakened ? 180 : 100));
        state.level.playSound(null, bot.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.1F, 0.72F);
    }

    private static void launchMeteor(Husk bot, ServerPlayer target, BotState state, long now, boolean awakened) {
        Vec3 predicted = target.position().add(target.getDeltaMovement().scale(8.0));
        double error = (1.0F - state.difficulty.aimAccuracy) * 5.5;
        Vec3 impact = predicted.add(
            (state.level.getRandom().nextDouble() - 0.5) * error,
            0.0,
            (state.level.getRandom().nextDouble() - 0.5) * error
        );
        Vec3 start = impact.add((state.level.getRandom().nextDouble() - 0.5) * 7.0, awakened ? 24.0 : 18.0, (state.level.getRandom().nextDouble() - 0.5) * 7.0);
        long impactTick = now + (awakened ? 26L : 36L);
        METEORS.add(new BotMeteor(state.level, bot.getUUID(), state.ownerId, start, impact, now, impactTick, awakened, state.difficulty));
        spawnTravelVisual(state, start, impact.add(0.0, 0.7, 0.0), new ItemStack(Items.MAGMA_BLOCK), impactTick - now, 0.0);
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
                Vec3 impact = meteor.impact;
                meteor.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, impact.x, impact.y + 0.4, impact.z, 1, 0, 0, 0, 0);
                meteor.level.sendParticles(ParticleTypes.FLAME, impact.x, impact.y + 0.7, impact.z, meteor.awakened ? 100 : 65, 2.2, 1.1, 2.2, 0.11);
                if (target != null && target.isAlive() && target.distanceToSqr(impact) < (meteor.awakened ? 49.0 : 30.0) && caster instanceof LivingEntity living) {
                    target.hurtServer(meteor.level, meteor.level.damageSources().mobAttack(living), (meteor.awakened ? 18.0F : 12.0F));
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
        ItemStack natureBody = switch (phase) {
            case 0 -> new ItemStack(Items.WHEAT_SEEDS);
            case 1 -> new ItemStack(Items.VINE);
            default -> new ItemStack(Items.SPORE_BLOSSOM);
        };
        spawnTravelVisual(state, bot.getEyePosition(), target.getEyePosition(), natureBody, phase >= 4 ? 15L : 11L, phase >= 4 ? 0.8 : 0.35);
        float damage = switch (phase) {
            case 0 -> awakened ? 9.0F : 5.5F;
            case 1 -> awakened ? 11.0F : 7.0F;
            default -> awakened ? 14.0F : 9.0F;
        };
        target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), damage);
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
            Vec3 oldPosition = bot.getEyePosition();
            Vec3 behind = target.position().subtract(horizontal(target.getLookAngle()).scale(2.2));
            spawnTravelVisual(state, oldPosition, behind.add(0.0, 1.0, 0.0), new ItemStack(Items.ENDER_EYE), awakened ? 6L : 9L, 0.55);
            bot.setPos(behind.x, behind.y, behind.z);
            bot.setDeltaMovement(Vec3.ZERO);
            target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 12.0F : 7.0F));
            state.level.sendParticles(ParticleTypes.REVERSE_PORTAL, bot.getX(), bot.getY() + 1.0, bot.getZ(), 55, 0.7, 1.0, 0.7, 0.12);
        } else if (phase == 1) {
            spawnTravelVisual(state, bot.getEyePosition(), target.getEyePosition(), new ItemStack(Items.ENDER_PEARL), awakened ? 7L : 11L, 0.45);
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
            // Varlıktan Çıkar'ın bot sürümü: görünür chorus çekirdeği hedefe ulaşır.
            spawnTravelVisual(state, bot.getEyePosition(), target.getEyePosition(), new ItemStack(Items.CHORUS_FRUIT), awakened ? 9L : 13L, 0.7);
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, awakened ? 70 : 45, 0, false, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, awakened ? 70 : 45, 10, false, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, awakened ? 90 : 60, 4, false, true, true));
            target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 13.0F : 8.0F));
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
                target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 15.0F : 9.0F));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, awakened ? 120 : 80, 3, false, true, true));
            }
        }
    }

    private static void botDragon(Husk bot, ServerPlayer target, BotState state, boolean awakened, int phase) {
        if (phase == 0 && bot.distanceToSqr(target) < 90.0) {
            double radius = awakened ? 7.5 : 5.2;
            PowerSystem.drawExternalRing(state.level, bot.position(), radius, ParticleTypes.REVERSE_PORTAL, 65);
            if (bot.distanceToSqr(target) <= radius * radius) {
                target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 13.0F : 8.0F));
                Vec3 push = horizontal(target.position().subtract(bot.position())).scale(awakened ? 2.5 : 1.8);
                target.push(push.x, awakened ? 1.0 : 0.65, push.z);
            }
        } else if (phase == 1) {
            Vec3 start = bot.getEyePosition();
            spawnTravelVisual(state, start, target.getEyePosition(), new ItemStack(Items.DRAGON_BREATH), awakened ? 8L : 12L, 0.30);
            Vec3 dir = target.getEyePosition().subtract(start).normalize();
            double breathLength = Math.min(12.0, Math.max(1.0, start.distanceTo(target.getEyePosition())));
            for (double d = 0.5; d <= breathLength; d += 0.5) {
                Vec3 p = start.add(dir.scale(d));
                state.level.sendParticles(d % 1.0 < 0.5 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.WITCH, p.x, p.y, p.z, awakened ? 5 : 3, 0.23, 0.23, 0.23, 0.02);
            }
            target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 15.0F : 10.0F));
        } else if (phase == 2) {
            state.guardCharges = awakened ? 5 : 3;
            state.anomalyShieldUntil = state.level.getGameTime() + (awakened ? 150L : 100L);
            state.level.sendParticles(ParticleTypes.REVERSE_PORTAL, bot.getX(), bot.getY() + 1.0, bot.getZ(), 85, 1.0, 1.2, 1.0, 0.08);
        } else if (phase == 3) {
            spawnTravelVisual(state, bot.getEyePosition(), target.getEyePosition(), new ItemStack(Items.AMETHYST_SHARD), awakened ? 7L : 10L, 0.25);
            Vec3 pull = horizontal(bot.position().subtract(target.position())).scale(1.8);
            target.push(pull.x, 0.45, pull.z);
            target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 12.0F : 8.0F));
            state.level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 1.0, target.getZ(), 45, 0.65, 0.9, 0.65, 0.08);
        } else {
            Vec3 push = horizontal(target.position().subtract(bot.position())).scale(awakened ? 3.2 : 2.3);
            target.push(push.x, awakened ? 1.25 : 0.85, push.z);
            target.hurtServer(state.level, state.level.damageSources().mobAttack(bot), (awakened ? 11.0F : 7.0F));
            state.level.playSound(null, bot.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.3F, 1.4F);
        }
    }

    public static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
        if (reflecting) return true;
        BotState victimState = BOTS.get(victim.getUUID());
        Entity attacker = source.getEntity();
        BotState attackerState = attacker == null ? null : BOTS.get(attacker.getUUID());
        if (victimState != null) {
            if (victimState.awakeningUntil <= victim.level().getGameTime())
                victimState.awakeningEnergy = Math.min(100.0F, victimState.awakeningEnergy + Math.min(10.0F, amount * 1.15F));
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
        if (attackerState != null && attackerState.awakeningUntil <= attacker.level().getGameTime())
            attackerState.awakeningEnergy = Math.min(100.0F, attackerState.awakeningEnergy + Math.min(8.0F, amount * 0.85F));
        return true;
    }

    private static void spawnCastVisuals(Husk bot, BotState state, int ability, boolean awakened) {
        ItemStack focus = switch (state.powerClass) {
            case WARDEN -> new ItemStack(ability >= 3 ? Items.ECHO_SHARD : Items.SCULK_CATALYST);
            case FIRE -> new ItemStack(ability >= 4 ? Items.MAGMA_CREAM : Items.FIRE_CHARGE);
            case NATURE -> new ItemStack(ability >= 4 ? Items.SPORE_BLOSSOM : Items.VINE);
            case ANOMALY -> new ItemStack(ability >= 4 ? Items.ENDER_EYE : Items.ENDER_PEARL);
            case FLIGHT -> new ItemStack(ability >= 4 ? Items.DRAGON_BREATH : Items.AMETHYST_SHARD);
            default -> new ItemStack(Items.NETHER_STAR);
        };
        int count = awakened ? 4 : 3;
        for (int i = 0; i < count; i++) {
            spawnOrbitVisual(state, bot, focus.copy(), awakened ? 30L : 22L,
                awakened ? 1.65 : 1.25, 0.85 + i * 0.18, Math.PI * 2.0 * i / count);
        }
    }

    private static void spawnTravelVisual(BotState state, Vec3 start, Vec3 end, ItemStack stack, long duration, double arcHeight) {
        ItemEntity body = new ItemEntity(state.level, start.x, start.y, start.z, stack);
        body.setNoGravity(true);
        body.setDeltaMovement(Vec3.ZERO);
        body.setNeverPickUp();
        body.setUnlimitedLifetime();
        body.setInvulnerable(true);
        if (!state.level.addFreshEntity(body)) return;
        long now = state.level.getGameTime();
        ATTACK_VISUALS.add(new BotAttackVisual(state.level, body.getUUID(), null, start, end,
            now, now + Math.max(2L, duration), arcHeight, 0.0, 0.0, 0.0));
    }

    private static void spawnOrbitVisual(BotState state, Entity anchor, ItemStack stack, long duration,
                                         double radius, double yOffset, double phase) {
        Vec3 start = anchor.position().add(0.0, yOffset, 0.0);
        ItemEntity body = new ItemEntity(state.level, start.x, start.y, start.z, stack);
        body.setNoGravity(true);
        body.setDeltaMovement(Vec3.ZERO);
        body.setNeverPickUp();
        body.setUnlimitedLifetime();
        body.setInvulnerable(true);
        if (!state.level.addFreshEntity(body)) return;
        long now = state.level.getGameTime();
        ATTACK_VISUALS.add(new BotAttackVisual(state.level, body.getUUID(), anchor.getUUID(), start, start,
            now, now + Math.max(2L, duration), 0.0, radius, yOffset, phase));
    }

    private static void tickAttackVisuals() {
        Iterator<BotAttackVisual> iterator = ATTACK_VISUALS.iterator();
        while (iterator.hasNext()) {
            BotAttackVisual visual = iterator.next();
            Entity raw = visual.level.getEntity(visual.entityId);
            long now = visual.level.getGameTime();
            if (!(raw instanceof ItemEntity body) || now >= visual.endTick) {
                if (raw != null) raw.discard();
                iterator.remove();
                continue;
            }
            double progress = Math.max(0.0, Math.min(1.0,
                (now - visual.startTick) / (double) Math.max(1L, visual.endTick - visual.startTick)));
            Vec3 position;
            if (visual.anchorId != null && visual.orbitRadius > 0.0) {
                Entity anchor = visual.level.getEntity(visual.anchorId);
                if (anchor == null || anchor.isRemoved()) {
                    body.discard();
                    iterator.remove();
                    continue;
                }
                double angle = visual.phase + progress * Math.PI * 4.0;
                position = anchor.position().add(
                    Math.cos(angle) * visual.orbitRadius,
                    visual.yOffset + Math.sin(progress * Math.PI * 2.0) * 0.16,
                    Math.sin(angle) * visual.orbitRadius
                );
            } else {
                double eased = 1.0 - Math.pow(1.0 - progress, 2.0);
                position = visual.start.lerp(visual.end, eased)
                    .add(0.0, Math.sin(Math.PI * progress) * visual.arcHeight, 0.0);
            }
            body.setPos(position.x, position.y, position.z);
            body.setDeltaMovement(Vec3.ZERO);
        }
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
        String detail;
        if (state.paused) detail = "Durduruldu";
        else if (state.awakeningUntil > now) detail = "UYANIŞ AKTİF";
        else if (state.globalRecoveryUntil > now) detail = String.format(Locale.ROOT, "Hazırlanıyor: %.1f sn", (state.globalRecoveryUntil - now) / 20.0);
        else detail = state.difficulty.displayName();
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
        private long nextDecisionTick;
        private long nextMeleeTick;
        private long globalRecoveryUntil;
        private final long[] cooldownUntil = new long[5];
        private float awakeningEnergy;
        private long awakeningUntil;
        private long anomalyShieldUntil;
        private int guardCharges;
        private boolean paused;

        private BotState(UUID entityId, UUID ownerId, ServerLevel level, PowerClass powerClass, BotDifficulty difficulty, long nextDecisionTick) {
            this.entityId = entityId;
            this.ownerId = ownerId;
            this.level = level;
            this.powerClass = powerClass;
            this.difficulty = difficulty;
            this.nextDecisionTick = nextDecisionTick;
        }
    }

    private record BotMeteor(ServerLevel level, UUID botId, UUID targetId, Vec3 start, Vec3 impact,
                             long startTick, long impactTick, boolean awakened, BotDifficulty difficulty) {}

    private record BotAttackVisual(ServerLevel level, UUID entityId, UUID anchorId, Vec3 start, Vec3 end,
                                   long startTick, long endTick, double arcHeight, double orbitRadius,
                                   double yOffset, double phase) {}
}
