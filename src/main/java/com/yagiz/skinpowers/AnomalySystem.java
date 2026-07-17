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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Anomali sınıfının blok yerleştirmeyen gerçeklik bozma sistemi. */
public final class AnomalySystem {
    private static final Map<UUID, ReversedTarget> REVERSED = new HashMap<>();
    private static final Map<UUID, VoidedTarget> VOIDED = new HashMap<>();
    private static boolean reflectingDamage;

    private AnomalySystem() {}

    public static boolean use(ServerPlayer player, PlayerPowerData data, int power, long now, boolean charged) {
        ServerLevel level = (ServerLevel) player.level();
        int stage = data.masteryStage(power);
        return switch (power) {
            case 1 -> brokenStep(player, data, level, now, stage, charged);
            case 2 -> reverseTarget(player, data, level, now, stage, charged);
            case 3 -> useCopied(player, data, now, charged);
            case 4 -> storeDamage(player, data, level, now, stage, charged);
            case 5 -> voidTarget(player, data, level, now, stage, charged);
            case 6 -> reality404(player, data, level, now, stage);
            default -> false;
        };
    }

    private static boolean brokenStep(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now, int stage, boolean charged) {
        Vec3 start = player.position();
        Vec3 direction = horizontal(player.getLookAngle());
        double maximum = (charged ? 17.0 : 11.0) + stage * 1.5;
        Vec3 destination = start;
        for (double distance = 1.0; distance <= maximum; distance += 0.75) {
            Vec3 candidate = start.add(direction.scale(distance));
            if (!safeForPlayer(level, candidate)) break;
            destination = candidate;
        }
        if (destination.distanceToSqr(start) < 1.0) {
            player.sendSystemMessage(Component.literal("Kırık Adım için önünde güvenli bir boşluk yok."));
            return false;
        }

        AABB corridor = new AABB(start, destination).inflate(1.5 + stage * 0.2, 1.4, 1.5 + stage * 0.2);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, corridor)) {
            if (target == player || PowerSystem.isProtectedAlly(player, target)) continue;
            target.hurtServer(level, level.damageSources().playerAttack(player), 10.0F + stage * 2.5F + (charged ? 8.0F : 0.0F));
            Vec3 push = direction.scale(0.8 + stage * 0.12);
            target.push(push.x, 0.25, push.z);
        }

        for (double distance = 0.0; distance <= Math.sqrt(destination.distanceToSqr(start)); distance += 0.65) {
            Vec3 point = start.add(direction.scale(distance)).add(0.0, 0.9, 0.0);
            level.sendParticles(distance % 1.3 < 0.65 ? ParticleTypes.WITCH : ParticleTypes.REVERSE_PORTAL,
                point.x, point.y, point.z, charged ? 8 : 4, 0.20, 0.35, 0.20, 0.02);
        }
        moveEntity(player, destination);
        player.setDeltaMovement(direction.scale(0.35));
        player.hurtMarked = true;
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 18 + stage * 4, charged ? 4 : 2, false, false, true));
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.55F);
        data.setCooldown(1, now, Math.max(70, 130 - stage * 15));
        return true;
    }

    private static boolean reverseTarget(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now, int stage, boolean charged) {
        LivingEntity target = PowerSystem.findTargetForExternalPower(player, 25.0 + stage * 3.0 + (charged ? 8.0 : 0.0));
        if (target == null) {
            player.sendSystemMessage(Component.literal("Tersine Çevir için nişangâhında bir hedef olmalı."));
            return false;
        }
        long duration = 90L + stage * 25L + (charged ? 70L : 0L);
        REVERSED.put(target.getUUID(), new ReversedTarget(level, player.getUUID(), target.getUUID(), now + duration));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, (int) duration, charged ? 3 : 2, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) duration, 0, false, false, true));
        level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 1.0, target.getZ(), charged ? 75 : 45, 0.8, 1.0, 0.8, 0.08);
        level.playSound(null, target.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.0F, 0.65F);
        data.setCooldown(2, now, Math.max(260, 420 - stage * 40));
        return true;
    }

    private static boolean useCopied(ServerPlayer player, PlayerPowerData data, long now, boolean charged) {
        if (!data.hasCopiedPower()) {
            player.sendSystemMessage(Component.literal("? henüz bir hamle yakalamadı. Yakınındaki rakibin aktif bir güç kullanmasını bekle."));
            return false;
        }
        PowerClass copiedClass = data.copiedPowerClass();
        int copiedLevel = data.copiedPowerLevel();
        boolean used = PowerSystem.executeCopiedPower(player, data, copiedClass, copiedLevel, now, charged);
        if (!used) {
            player.sendSystemMessage(Component.literal("Kopyalanan hamle şu anda kullanılamadı; kaybolmadı."));
            return false;
        }
        String name = PowerCatalog.powerName(copiedClass, copiedLevel);
        data.clearCopiedPower();
        data.clearCooldown(copiedLevel, now);
        data.setCooldown(3, now, 360);
        player.sendSystemMessage(Component.literal("Kopyalanan hamle kullanıldı: " + name + ". ? yeniden boş."));
        return true;
    }

    private static boolean storeDamage(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now, int stage, boolean charged) {
        if (data.anomalyDamageStoreUntil() > now || data.anomalyChoiceUntil() > now) {
            player.sendSystemMessage(Component.literal("Önce mevcut depolanmış hasarı V veya X ile kullanmalısın."));
            return false;
        }
        long duration = 100L + stage * 10L + (charged ? 40L : 0L);
        data.beginAnomalyDamageStore(now + duration);
        level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0, player.getZ(), 55, 0.8, 1.0, 0.8, 0.04);
        player.sendSystemMessage(Component.literal("Hasar Mevcut Değil: " + String.format(java.util.Locale.ROOT, "%.1f", duration / 20.0) + " saniye boyunca hasar depolanıyor."));
        data.setCooldown(4, now, Math.max(700, 980 - stage * 70));
        return true;
    }

    private static boolean voidTarget(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now, int stage, boolean charged) {
        LivingEntity target = PowerSystem.findTargetForExternalPower(player, 27.0 + stage * 3.0 + (charged ? 8.0 : 0.0));
        if (target == null) {
            player.sendSystemMessage(Component.literal("Varlıktan Çıkar için nişangâhında bir hedef olmalı."));
            return false;
        }
        if (VOIDED.containsKey(target.getUUID())) {
            player.sendSystemMessage(Component.literal("Bu hedef zaten gerçekliğin dışında."));
            return false;
        }
        long duration = 70L + stage * 15L + (charged ? 55L : 0L);
        VoidedTarget voided = new VoidedTarget(level, player.getUUID(), target.getUUID(), target, target.position(), now + duration,
            target.isInvisible(), target.isNoGravity(), target.isInvulnerable());
        VOIDED.put(target.getUUID(), voided);
        target.setInvisible(true);
        target.setNoGravity(true);
        target.setInvulnerable(true);
        target.setDeltaMovement(Vec3.ZERO);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 1.0, target.getZ(), charged ? 100 : 65, 0.8, 1.1, 0.8, 0.18);
        level.playSound(null, target.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8F, 1.55F);
        data.setCooldown(5, now, Math.max(700, 980 - stage * 70));
        return true;
    }

    private static boolean reality404(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now, int stage) {
        if (data.anomalyRealityUntil() > now) {
            player.sendSystemMessage(Component.literal("404 alanı zaten açık."));
            return false;
        }
        long duration = 220L + stage * 30L;
        Vec3 center = player.position();
        data.beginAnomalyReality(now + duration, center.x, center.y, center.z);
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, (int) duration, 2, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, (int) duration, 1, false, true, true));
        level.sendParticles(ParticleTypes.WITCH, center.x, center.y + 1.0, center.z, 180, 3.0, 1.4, 3.0, 0.14);
        level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.7F, 0.45F);
        ServerNetworking.sendScreenShake(level, center, 42.0, 1.7F, 24);
        data.setCooldown(6, now, Math.max(1900, 2600 - stage * 170));
        return true;
    }

    public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayer player)) {
            ReversedTarget reversed = REVERSED.get(entity.getUUID());
            if (reversed != null) reflectPartOfDamage(reversed, source, amount);
            return true;
        }
        PlayerPowerData data = PlayerDataStore.get(player.getUUID());
        long now = player.level().getGameTime();
        if (data.powerClass() == PowerClass.ANOMALY && data.anomalyDamageStoreUntil() > now) {
            data.addAnomalyStoredDamage(amount);
            PlayerDataStore.markDirty();
            ServerLevel level = (ServerLevel) player.level();
            level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.35, 0.55, 0.35, 0.03);
            return false;
        }
        if (data.powerClass() == PowerClass.ANOMALY && data.anomalyRealityUntil() > now) {
            if (player.level().getRandom().nextFloat() < 0.22F) {
                dodgeInsideReality(player, data);
                return false;
            }
        }
        ReversedTarget reversed = REVERSED.get(player.getUUID());
        if (reversed != null) reflectPartOfDamage(reversed, source, amount);
        return true;
    }


    public static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayer player)) return true;
        PlayerPowerData data = PlayerDataStore.get(player.getUUID());
        long now = player.level().getGameTime();
        if (data.powerClass() != PowerClass.ANOMALY || data.anomalyRealityUntil() <= now
            || !data.anomalyRealityReviveAvailable()) return true;
        data.consumeAnomalyRealityRevive();
        player.setHealth(Math.min(player.getMaxHealth(), 10.0F));
        moveEntity(player, new Vec3(data.anomalyRealityX(), data.anomalyRealityY(), data.anomalyRealityZ()));
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 80, 4, false, true, true));
        player.sendSystemMessage(Component.literal("404: Ölüm sonucu reddedildi."));
        PlayerDataStore.markDirty();
        ServerNetworking.sync(player);
        return false;
    }

    private static void reflectPartOfDamage(ReversedTarget reversed, DamageSource source, float amount) {
        if (reflectingDamage || !(source.getEntity() instanceof LivingEntity attacker) || !attacker.isAlive()) return;
        ServerPlayer owner = reversed.level.getServer().getPlayerList().getPlayer(reversed.owner);
        try {
            reflectingDamage = true;
            attacker.hurtServer(reversed.level,
                owner == null ? reversed.level.damageSources().generic() : reversed.level.damageSources().playerAttack(owner),
                Math.max(1.0F, amount * 0.55F));
        } finally {
            reflectingDamage = false;
        }
    }

    public static void chooseStoredDamage(ServerPlayer player, PlayerPowerData data, boolean convertToHealth) {
        long now = player.level().getGameTime();
        if (data.powerClass() != PowerClass.ANOMALY || data.anomalyChoiceUntil() <= now || data.anomalyStoredDamage() <= 0.0F) return;
        if (convertToHealth) {
            double available = Math.min(10.0, data.anomalyStoredDamage() * 0.5); // Tek kullanımda en fazla 5 kalp.
            double oldBonus = data.anomalyBonusHealth();
            double newBonus = Math.min(20.0, oldBonus + available); // Toplam en fazla 10 kalp.
            double actualGain = newBonus - oldBonus;
            if (actualGain <= 0.0) {
                player.sendSystemMessage(Component.literal("Geçici kalp sınırına ulaştın."));
                return;
            }
            var attribute = player.getAttribute(Attributes.MAX_HEALTH);
            if (attribute == null) return;
            double baseBefore = data.anomalyHealthBaseBeforeBonus();
            if (oldBonus <= 0.0D || baseBefore < 1.0D) {
                baseBefore = Math.max(1.0D, attribute.getBaseValue() - oldBonus);
            }
            attribute.setBaseValue(baseBefore + newBonus);
            data.setAnomalyBonusHealth(newBonus, now + 3600L, baseBefore);
            player.sendSystemMessage(Component.literal("+" + String.format(java.util.Locale.ROOT, "%.1f", actualGain / 2.0) + " geçici kırmızı kalp kapasitesi • 03:00"));
            data.clearAnomalyStoredDamage();
        } else {
            LivingEntity target = PowerSystem.findTargetForExternalPower(player, 34.0);
            if (target == null) {
                player.sendSystemMessage(Component.literal("X: Hedef bulunamadı; depolanan hasar korunuyor."));
                return;
            }
            float damage = data.anomalyStoredDamage();
            target.hurtServer((ServerLevel) player.level(), player.level().damageSources().playerAttack(player), damage);
            ((ServerLevel) player.level()).sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 1.0, target.getZ(), 75, 0.8, 1.0, 0.8, 0.12);
            player.sendSystemMessage(Component.literal("X: " + String.format(java.util.Locale.ROOT, "%.1f", damage) + " hasar geri gönderildi."));
            data.clearAnomalyStoredDamage();
        }
        PlayerDataStore.markDirty();
        ServerNetworking.sync(player);
    }

    public static void recordPowerUse(ServerPlayer caster, PowerClass powerClass, int power) {
        if (!isCopyable(powerClass, power)) return;
        ServerLevel level = (ServerLevel) caster.level();
        for (ServerPlayer observer : level.players()) {
            if (observer == caster || observer.distanceToSqr(caster) > 30.0 * 30.0
                || PowerSystem.isProtectedAlly(observer, caster)) continue;
            PlayerPowerData observerData = PlayerDataStore.get(observer.getUUID());
            if (observerData.powerClass() != PowerClass.ANOMALY || observerData.unlockedLevel() < 3 || observerData.hasCopiedPower()) continue;
            observerData.setCopiedPower(powerClass, power);
            observer.sendSystemMessage(Component.literal("Hamle kopyalandı: " + PowerCatalog.powerName(powerClass, power)));
            level.sendParticles(ParticleTypes.WITCH, observer.getX(), observer.getY() + 1.0, observer.getZ(), 45, 0.7, 0.9, 0.7, 0.08);
            PlayerDataStore.markDirty();
            ServerNetworking.sync(observer);
        }
    }

    public static boolean isCopyable(PowerClass powerClass, int power) {
        return switch (powerClass) {
            case WARDEN -> power >= 1 && power <= 6;
            case FLIGHT -> power >= 2 && power <= 5;
            case FIRE -> power >= 3 && power <= 5;
            case NATURE -> power >= 2 && power <= 5;
            default -> false;
        };
    }

    public static String displayPowerName(PlayerPowerData data, int level) {
        if (data.powerClass() == PowerClass.ANOMALY && level == 3 && data.hasCopiedPower()) {
            return PowerCatalog.powerName(data.copiedPowerClass(), data.copiedPowerLevel());
        }
        return PowerCatalog.powerName(data.powerClass(), level);
    }

    public static String displayPowerDescription(PlayerPowerData data, int level) {
        if (data.powerClass() == PowerClass.ANOMALY && level == 3 && data.hasCopiedPower()) {
            return data.copiedPowerClass().displayName() + " sınıfından çalınmış tek kullanımlık hamle.";
        }
        return PowerCatalog.powerDescription(data.powerClass(), level);
    }

    public static void tickPlayer(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        if (data.anomalyDamageStoreUntil() > 0L && data.anomalyDamageStoreUntil() <= now) {
            data.finishAnomalyDamageStore(now + 240L);
            if (data.anomalyStoredDamage() > 0.0F) {
                player.sendSystemMessage(Component.literal("Depolanan hasar: " + String.format(java.util.Locale.ROOT, "%.1f", data.anomalyStoredDamage()) + " • V: kalp  X: geri gönder"));
            }
            PlayerDataStore.markDirty();
        }
        if (data.anomalyChoiceUntil() > 0L && data.anomalyChoiceUntil() <= now) {
            data.clearAnomalyStoredDamage();
            player.sendSystemMessage(Component.literal("Depolanan hasar dağıldı."));
            PlayerDataStore.markDirty();
        }
        if (data.anomalyBonusHealth() > 0.0 && data.anomalyBonusHealthUntil() <= now) {
            removeBonusHealth(player, data);
        } else if (data.anomalyBonusHealth() > 0.0) {
            ensureBonusHealthApplied(player, data);
        }
        if (data.anomalyRealityUntil() > now) {
            tickReality(player, data, level, now);
        } else if (data.anomalyRealityUntil() != 0L) {
            data.clearAnomalyReality();
            player.sendSystemMessage(Component.literal("404 alanı kapandı."));
            PlayerDataStore.markDirty();
        }
    }

    public static void tickServer(MinecraftServer server) {
        tickReversed();
        tickVoided();
    }

    private static void tickReversed() {
        Iterator<ReversedTarget> iterator = REVERSED.values().iterator();
        while (iterator.hasNext()) {
            ReversedTarget entry = iterator.next();
            long now = entry.level.getGameTime();
            Entity entity = entry.level.getEntity(entry.target);
            if (!(entity instanceof LivingEntity target) || !target.isAlive() || now >= entry.expireTick) {
                iterator.remove();
                continue;
            }
            Vec3 motion = target.getDeltaMovement();
            target.setDeltaMovement(-motion.x * 0.82, Math.min(0.15, -motion.y * 0.45), -motion.z * 0.82);
            if (target instanceof ServerPlayer player) player.hurtMarked = true;
            AABB area = target.getBoundingBox().inflate(4.5);
            for (Projectile projectile : entry.level.getEntitiesOfClass(Projectile.class, area)) {
                if (projectile.getOwner() != target) continue;
                projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-1.1));
            }
            if (now % 5L == 0L) entry.level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 1.0, target.getZ(), 8, 0.35, 0.55, 0.35, 0.03);
        }
    }

    private static void tickVoided() {
        Iterator<VoidedTarget> iterator = VOIDED.values().iterator();
        while (iterator.hasNext()) {
            VoidedTarget entry = iterator.next();
            long now = entry.level.getGameTime();
            LivingEntity target = entry.targetEntity;
            if (!target.isAlive()) {
                restoreVoidedTarget(entry, target, false);
                iterator.remove();
                continue;
            }
            if (now >= entry.expireTick) {
                restoreVoidedTarget(entry, target, true);
                iterator.remove();
                continue;
            }
            target.setDeltaMovement(Vec3.ZERO);
            moveEntity(target, entry.anchor);
            if (target instanceof ServerPlayer player) player.hurtMarked = true;
            if (now % 6L == 0L) entry.level.sendParticles(ParticleTypes.REVERSE_PORTAL, entry.anchor.x, entry.anchor.y + 1.0, entry.anchor.z, 12, 0.45, 0.8, 0.45, 0.06);
        }
    }

    private static void tickReality(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        Vec3 center = new Vec3(data.anomalyRealityX(), data.anomalyRealityY(), data.anomalyRealityZ());
        int stage = data.masteryStage(6);
        double radius = 18.0 + stage * 2.0;
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 2, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 30, 1, false, false, true));
        data.reduceAllCooldowns(now, 1); // Alanda bekleme süreleri yaklaşık iki kat hızlı dolar.
        if (now % 4L == 0L) PowerSystem.drawExternalRing(level, center, radius, ParticleTypes.WITCH, 72);

        AABB area = new AABB(center, center).inflate(radius, 7.0, radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (target == player || PowerSystem.isProtectedAlly(player, target)) continue;
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 3, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 2, false, false, true));
            if (now % 20L == 0L) target.hurtServer(level, level.damageSources().playerAttack(player), 5.0F + stage * 1.5F);
            if (now % 12L == 0L) {
                double jx = (level.getRandom().nextDouble() - 0.5) * 1.6;
                double jz = (level.getRandom().nextDouble() - 0.5) * 1.6;
                Vec3 candidate = target.position().add(jx, 0.0, jz);
                if (safeForLiving(level, target, candidate)) moveEntity(target, candidate);
            }
        }
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, area)) {
            if (projectile.getOwner() == player) continue;
            if (now % 8L == 0L) projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-1.05));
        }
    }

    private static void dodgeInsideReality(ServerPlayer player, PlayerPowerData data) {
        ServerLevel level = (ServerLevel) player.level();
        for (int i = 0; i < 8; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
            double distance = 2.0 + level.getRandom().nextDouble() * 4.0;
            Vec3 candidate = player.position().add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
            if (!safeForPlayer(level, candidate)) continue;
            moveEntity(player, candidate);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, candidate.x, candidate.y + 1.0, candidate.z, 24, 0.5, 0.8, 0.5, 0.08);
            player.hurtMarked = true;
            return;
        }
    }

    private static void ensureBonusHealthApplied(ServerPlayer player, PlayerPowerData data) {
        var attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) return;
        double baseBefore = data.anomalyHealthBaseBeforeBonus();
        if (baseBefore < 1.0D) {
            baseBefore = Math.max(1.0D, attribute.getBaseValue() - data.anomalyBonusHealth());
            data.setAnomalyBonusHealth(data.anomalyBonusHealth(), data.anomalyBonusHealthUntil(), baseBefore);
            PlayerDataStore.markDirty();
        }
        double expected = baseBefore + data.anomalyBonusHealth();
        if (Math.abs(attribute.getBaseValue() - expected) > 0.001D) attribute.setBaseValue(expected);
    }

    private static void removeBonusHealth(ServerPlayer player, PlayerPowerData data) {
        double bonus = data.anomalyBonusHealth();
        var attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) {
            double baseBefore = data.anomalyHealthBaseBeforeBonus();
            if (baseBefore < 1.0D) baseBefore = Math.max(1.0D, attribute.getBaseValue() - bonus);
            attribute.setBaseValue(baseBefore);
        }
        data.clearAnomalyBonusHealth();
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
        player.sendSystemMessage(Component.literal("Anomali'nin geçici kırmızı kalpleri silindi."));
        PlayerDataStore.markDirty();
    }

    /** Bağlantı kesilirse hiçbir oyuncu görünmez/ölümsüz durumda kaydedilmez; sahibin hedefi de hemen geri getirilir. */
    public static void handleDisconnect(ServerPlayer player) {
        UUID playerId = player.getUUID();
        VoidedTarget asTarget = VOIDED.remove(playerId);
        if (asTarget != null) restoreVoidedTarget(asTarget, player, false);

        Iterator<Map.Entry<UUID, VoidedTarget>> iterator = VOIDED.entrySet().iterator();
        while (iterator.hasNext()) {
            VoidedTarget entry = iterator.next().getValue();
            if (!entry.owner.equals(playerId)) continue;
            restoreVoidedTarget(entry, entry.targetEntity, false);
            iterator.remove();
        }
    }

    private static void restoreVoidedTarget(VoidedTarget entry, LivingEntity target, boolean applyReturnPenalty) {
        target.setInvisible(entry.wasInvisible);
        target.setNoGravity(entry.wasNoGravity);
        target.setInvulnerable(entry.wasInvulnerable);
        moveEntity(target, entry.anchor);
        if (!applyReturnPenalty || !target.isAlive()) return;

        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 3, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2, false, true, true));
        ServerPlayer owner = entry.level.getServer().getPlayerList().getPlayer(entry.owner);
        target.hurtServer(entry.level, owner == null ? entry.level.damageSources().generic() : entry.level.damageSources().playerAttack(owner), 8.0F);
        entry.level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 1.0, target.getZ(), 85, 0.8, 1.1, 0.8, 0.16);
    }

    public static void clearPlayer(ServerPlayer player) {
        UUID ownerId = player.getUUID();
        REVERSED.entrySet().removeIf(entry -> entry.getValue().owner.equals(ownerId));
        Iterator<Map.Entry<UUID, VoidedTarget>> iterator = VOIDED.entrySet().iterator();
        while (iterator.hasNext()) {
            VoidedTarget entry = iterator.next().getValue();
            if (!entry.owner.equals(ownerId)) continue;
            restoreVoidedTarget(entry, entry.targetEntity, false);
            iterator.remove();
        }
        PlayerPowerData data = PlayerDataStore.get(ownerId);
        PowerSystem.clearBorrowedClassEffects(player, data);
        if (data.anomalyBonusHealth() > 0.0D) removeBonusHealth(player, data);
        data.clearCopiedPower();
        data.clearAnomalyStoredDamage();
        data.clearAnomalyReality();
    }

    public static void clearAll() {
        for (VoidedTarget entry : VOIDED.values()) {
            restoreVoidedTarget(entry, entry.targetEntity, false);
        }
        REVERSED.clear();
        VOIDED.clear();
    }

    private static Vec3 horizontal(Vec3 look) {
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        return horizontal.lengthSqr() < 0.0001 ? new Vec3(0.0, 0.0, 1.0) : horizontal.normalize();
    }

    /** Minecraft 26.x eşlemelerinde kararlı olan konum güncellemesi; oyuncularda ağ senkronunu da zorlar. */
    private static void moveEntity(LivingEntity entity, Vec3 position) {
        entity.setPos(position.x, position.y, position.z);
        entity.setDeltaMovement(Vec3.ZERO);
        if (entity instanceof ServerPlayer player) player.hurtMarked = true;
    }

    private static boolean safeForPlayer(ServerLevel level, Vec3 position) {
        BlockPos feet = BlockPos.containing(position);
        return level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
            && !level.getBlockState(feet.below()).isAir();
    }

    private static boolean safeForLiving(ServerLevel level, LivingEntity entity, Vec3 position) {
        return level.noCollision(entity, entity.getBoundingBox().move(position.subtract(entity.position())));
    }

    private record ReversedTarget(ServerLevel level, UUID owner, UUID target, long expireTick) {}
    private record VoidedTarget(ServerLevel level, UUID owner, UUID target, LivingEntity targetEntity, Vec3 anchor, long expireTick,
                                boolean wasInvisible, boolean wasNoGravity, boolean wasInvulnerable) {}
}
