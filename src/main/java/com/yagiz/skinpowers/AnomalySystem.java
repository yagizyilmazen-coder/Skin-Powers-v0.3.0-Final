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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Anomali sınıfının blok yerleştirmeyen gerçeklik bozma sistemi. */
public final class AnomalySystem {
    private static final Map<UUID, ReversedTarget> REVERSED = new HashMap<>();
    private static final Map<UUID, VoidedTarget> VOIDED = new HashMap<>();
    private static final Map<UUID, FrozenProjectile> FROZEN_PROJECTILES = new HashMap<>();
    private static final List<PendingEcho> ECHOES = new ArrayList<>();
    private static final List<AnomalyVisual> VISUALS = new ArrayList<>();
    private static final List<ProjectileEscort> PROJECTILE_ESCORTS = new ArrayList<>();
    private static final Map<UUID, Long> COPIED_EXPIRES = new HashMap<>();
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
        Vec3 running = new Vec3(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
        Vec3 direction = player.isSprinting() && running.lengthSqr() > 0.015 ? running.normalize() : horizontal(player.getLookAngle());
        boolean systemCrash = data.classAwakeningActive(now);
        double maximum = (charged || systemCrash ? 21.0 : 14.0) + stage * 2.0;
        Vec3 destination = null;
        // En uzak güvenli noktadan geriye doğru aranır; eğim ve koşu sırasında ilk küçük engelde durmaz.
        for (double distance = maximum; distance >= 2.0; distance -= 0.5) {
            destination = findSafeStepPosition(level, start.add(direction.scale(distance)));
            if (destination != null) break;
        }
        if (destination == null || destination.distanceToSqr(start) < 3.0) {
            player.sendSystemMessage(Component.literal("Kırık Adım için ileride güvenli bir boşluk yok."));
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
        double totalDistance = Math.sqrt(destination.distanceToSqr(start));
        int echoCount = systemCrash ? 5 : 3;
        for (int i = 1; i <= echoCount; i++) {
            double fraction = i / (double) (echoCount + 1);
            Vec3 echoPos = start.add(direction.scale(totalDistance * fraction));
            long detonate = now + 7L + i * 4L;
            spawnGlitchFigure(level, player.getUUID(), echoPos, detonate + 8L, i * 0.4);
            ECHOES.add(new PendingEcho(level, player.getUUID(), echoPos, detonate, stage, charged || systemCrash));
        }
        moveEntity(player, destination);
        double momentum = 1.05 + stage * 0.12 + (charged ? 0.30 : 0.0);
        player.setDeltaMovement(direction.scale(momentum).add(0.0, Math.max(0.05, player.getDeltaMovement().y), 0.0));
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
        spawnVisibleRing(level, player.getUUID(), target.position().add(0.0, 1.0, 0.0), new Item[]{Items.REDSTONE, Items.ENDER_EYE}, charged ? 12 : 8, 1.25, now + duration);
        if (target instanceof ServerPlayer reversedPlayer) reversedPlayer.sendSystemMessage(Component.literal("REVERSED"));
        level.playSound(null, target.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.0F, 0.65F);
        data.setCooldown(2, now, Math.max(260, 420 - stage * 40));
        return true;
    }

    private static boolean useCopied(ServerPlayer player, PlayerPowerData data, long now, boolean charged) {
        long expiresAt = COPIED_EXPIRES.getOrDefault(player.getUUID(), data.hasCopiedPower() ? now + 200L : 0L);
        if (data.hasCopiedPower() && expiresAt <= now) {
            data.clearCopiedPower();
            COPIED_EXPIRES.remove(player.getUUID());
        }
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
        boolean exhausted = data.consumeCopiedPowerUse();
        if (exhausted) COPIED_EXPIRES.remove(player.getUUID());
        data.clearCooldown(copiedLevel, now);
        data.setCooldown(3, now, data.classAwakeningActive(now) ? 180 : 360);
        player.sendSystemMessage(Component.literal(exhausted
            ? "Kopyalanan hamle kullanıldı: " + name + ". ? yeniden boş."
            : "Kopyalanan hamle kullanıldı: " + name + ". Bir kullanım daha kaldı."));
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
        drawAnomalyShield(level, player.position().add(0.0, 1.0, 0.0), charged, now * 0.28);
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
        if (target instanceof ServerPlayer voidedPlayer) {
            voidedPlayer.hurtMarked = true;
            voidedPlayer.sendSystemMessage(Component.literal("Varlıktan çıkarıldın. Süre bitene kadar hareket ve güç kullanımı kilitlendi."));
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 1.0, target.getZ(), charged ? 100 : 65, 0.8, 1.1, 0.8, 0.18);
        drawGlitchCage(level, target.position(), charged ? 1.55 : 1.30, charged ? 2.9 : 2.55, now * 0.22, charged);
        spawnGlitchFigure(level, player.getUUID(), target.position(), now + duration, 0.0);
        level.playSound(null, target.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8F, 1.55F);
        data.setCooldown(5, now, Math.max(700, 980 - stage * 70));
        return true;
    }

    private static boolean reality404(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now, int stage) {
        if (data.anomalyRealityUntil() > now) {
            player.sendSystemMessage(Component.literal("404 alanı zaten açık."));
            return false;
        }
        boolean systemCrash = data.classAwakeningActive(now);
        long duration = 220L + stage * 30L + (systemCrash ? 120L : 0L);
        Vec3 center = player.position();
        data.beginAnomalyReality(now + duration, center.x, center.y, center.z);
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, (int) duration, systemCrash ? 3 : 2, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, (int) duration, systemCrash ? 2 : 1, false, true, true));
        level.sendParticles(ParticleTypes.WITCH, center.x, center.y + 1.0, center.z, 180, 3.0, 1.4, 3.0, 0.14);
        spawn404Body(level, player.getUUID(), center.add(0.0, 1.0, 0.0), now + duration);
        player.sendSystemMessage(Component.literal("404 ALANI: GERÇEKLİK BULUNAMADI"));
        level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.7F, 0.45F);
        ServerNetworking.sendScreenShake(level, center, 42.0, 1.7F, 24);
        data.setCooldown(6, now, Math.max(1900, 2600 - stage * 170));
        return true;
    }

    public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (reflectingDamage) return true;

        if (entity instanceof ServerPlayer player) {
            PlayerPowerData data = PlayerDataStore.get(player.getUUID());
            long now = player.level().getGameTime();
            // Oyuncu, mob, mermi, patlama, ateş ve mod güçleri dâhil bütün normal hasar kaynaklarını yakalar.
            if (data.powerClass() == PowerClass.ANOMALY && data.anomalyDamageStoreUntil() > now && amount > 0.0F) {
                data.addAnomalyStoredDamage(amount);
                PlayerDataStore.markDirty();
                ServerNetworking.sync(player);
                ServerLevel level = (ServerLevel) player.level();
                level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.35, 0.55, 0.35, 0.03);
                PowerSystem.drawExternalRing(level, player.position().add(0.0, 0.55, 0.0),
                    data.classAwakeningActive(now) ? 1.75 : 1.45, ParticleTypes.WITCH, data.classAwakeningActive(now) ? 32 : 24);
                return false;
            }
        }

        ServerLevel level = (ServerLevel) entity.level();
        Entity attackerEntity = source.getEntity();
        ServerPlayer realityOwner = findRealityOwner(level, entity.position(), attackerEntity == null ? null : attackerEntity.getUUID());
        if (realityOwner != null && attackerEntity instanceof LivingEntity attacker && attacker != realityOwner
            && !PowerSystem.isProtectedAlly(realityOwner, attacker)) {
            try {
                reflectingDamage = true;
                attacker.hurtServer(level, level.damageSources().playerAttack(realityOwner), Math.max(2.0F, amount * 1.15F));
                level.sendParticles(ParticleTypes.WITCH, attacker.getX(), attacker.getY() + 1.0, attacker.getZ(), 28, 0.55, 0.75, 0.55, 0.08);
            } finally {
                reflectingDamage = false;
            }
            return false;
        }

        ReversedTarget reversed = REVERSED.get(entity.getUUID());
        if (reversed != null) reflectPartOfDamage(reversed, source, amount);
        return true;
    }


    public static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayer player)) return true;
        PlayerPowerData data = PlayerDataStore.get(player.getUUID());
        long now = player.level().getGameTime();
        if (data.powerClass() == PowerClass.ANOMALY && data.anomalyRealityUntil() > now
            && data.anomalyRealityReviveAvailable()) {
            data.consumeAnomalyRealityRevive();
            player.setHealth(Math.min(player.getMaxHealth(), 10.0F));
            moveEntity(player, new Vec3(data.anomalyRealityX(), data.anomalyRealityY(), data.anomalyRealityZ()));
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 80, 4, false, true, true));
            player.sendSystemMessage(Component.literal("404: Ölüm sonucu reddedildi."));
            PlayerDataStore.markDirty();
            ServerNetworking.sync(player);
            return false;
        }
        // Küçük sınıf pasifi: 10 dakikada bir ölümcül sonuç reddedilir.
        if (data.powerClass() == PowerClass.ANOMALY && now >= data.anomalyErrorCooldownUntil()) {
            data.setAnomalyErrorCooldownUntil(now + 12000L);
            player.setHealth(1.0F);
            dodgeInsideReality(player, data);
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 80, 4, false, true, true));
            player.sendSystemMessage(Component.literal("SONUÇ REDDEDİLDİ"));
            PlayerDataStore.markDirty();
            ServerNetworking.sync(player);
            return false;
        }
        return true;
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
            drawHealthConversion((ServerLevel) player.level(), player.position().add(0.0, 1.0, 0.0), now);
            data.clearAnomalyStoredDamage();
        } else {
            LivingEntity target = findStoredDamageTarget(player, 34.0);
            if (target == null) {
                player.sendSystemMessage(Component.literal("X: Nişangâhında mob veya oyuncu bulunamadı; depolanan hasar korunuyor."));
                return;
            }
            float damage = data.anomalyStoredDamage();
            ServerLevel level = (ServerLevel) player.level();
            boolean damageApplied = target.hurtServer(level, level.damageSources().playerAttack(player), damage);
            if (!damageApplied) {
                String reason = target instanceof ServerPlayer
                    ? "Oyuncuya hasar verilemedi. Sunucuda PvP kapalı olabilir; depolanan hasar korunuyor."
                    : "Hedef şu anda hasar alamıyor; depolanan hasar korunuyor.";
                player.sendSystemMessage(Component.literal("X: " + reason));
                return;
            }
            level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 1.0, target.getZ(), 75, 0.8, 1.0, 0.8, 0.12);
            drawAnomalyBeam(level, player.getEyePosition(), target.getBoundingBox().getCenter(), now);
            drawGlitchCage(level, target.position(), 1.45, 2.8, now * 0.30, true);
            player.sendSystemMessage(Component.literal("X: " + String.format(java.util.Locale.ROOT, "%.1f", damage)
                + " hasar " + (target instanceof ServerPlayer ? "oyuncuya" : "moba") + " geri gönderildi."));
            data.clearAnomalyStoredDamage();
        }
        PlayerDataStore.markDirty();
        ServerNetworking.sync(player);
    }

    /**
     * Depolanmış hasarın moblarda da güvenilir çalışması için önce dar nişan seçimini,
     * sonra hedefin gövdesini kullanan daha geniş bir görüş konisi seçimini dener.
     */
    private static LivingEntity findStoredDamageTarget(ServerPlayer player, double range) {
        LivingEntity direct = PowerSystem.findTargetForExternalPower(player, range);
        if (direct != null) return direct;

        ServerLevel level = (ServerLevel) player.level();
        Vec3 origin = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = origin.add(look.scale(range));
        AABB search = new AABB(origin, end).inflate(3.5);
        LivingEntity best = null;
        double bestForward = range + 1.0;

        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, search)) {
            if (candidate == player || !candidate.isAlive() || PowerSystem.isProtectedAlly(player, candidate)) continue;
            Vec3 center = candidate.getBoundingBox().getCenter();
            Vec3 to = center.subtract(origin);
            double forward = to.dot(look);
            if (forward <= 0.0 || forward > range) continue;
            double side = to.subtract(look.scale(forward)).length();
            double tolerance = 2.25 + candidate.getBbWidth() * 0.75;
            if (side > tolerance || !player.hasLineOfSight(candidate)) continue;
            if (forward < bestForward) {
                bestForward = forward;
                best = candidate;
            }
        }
        return best;
    }

    public static boolean isVoided(UUID entityId) {
        return entityId != null && VOIDED.containsKey(entityId);
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
            COPIED_EXPIRES.put(observer.getUUID(), level.getGameTime() + 200L);
            if (observerData.classAwakeningActive(level.getGameTime())) observerData.setCopiedPowerUses(2);
            observer.sendSystemMessage(Component.literal("Hamle kopyalandı: " + PowerCatalog.powerName(powerClass, power)
                + (observerData.copiedPowerUses() > 1 ? " • 2 kullanım" : "") + " • 10 saniye"));
            level.sendParticles(ParticleTypes.WITCH, observer.getX(), observer.getY() + 1.0, observer.getZ(), 45, 0.7, 0.9, 0.7, 0.08);
            PlayerDataStore.markDirty();
            ServerNetworking.sync(observer);
        }
    }

    public static boolean isCopyable(PowerClass powerClass, int power) {
        return switch (powerClass) {
            case WARDEN -> power >= 1 && power <= 6;
            case FLIGHT -> power >= 1 && power <= 6;
            case FIRE -> power >= 3 && power <= 6;
            case MOON -> power >= 1 && power <= 6;
            case MAGNETIC, SAND -> power >= 1 && power <= 6;
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
            return data.copiedPowerClass().displayName() + " sınıfından çalınmış hamle • " + data.copiedPowerUses() + " kullanım.";
        }
        return PowerCatalog.powerDescription(data.powerClass(), level);
    }

    public static void tickPlayer(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        if (data.hasCopiedPower()) {
            long expiresAt = COPIED_EXPIRES.computeIfAbsent(player.getUUID(), ignored -> now + 200L);
            if (expiresAt <= now) {
                data.clearCopiedPower();
                COPIED_EXPIRES.remove(player.getUUID());
                player.sendSystemMessage(Component.literal("? içindeki kopyalanmış hamle silindi."));
                PlayerDataStore.markDirty();
                ServerNetworking.sync(player);
            }
        }
        if ((data.anomalyDamageStoreUntil() > now || data.anomalyChoiceUntil() > now) && data.anomalyStoredDamage() > 0.0F && now % 8L == 0L) {
            boolean empowered = data.classAwakeningActive(now);
            drawAnomalyShield(level, player.position().add(0.0, 1.0, 0.0), empowered, now * 0.24);
        }
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
        tickFrozenProjectiles();
        tickProjectileEscorts();
        tickEchoes();
        tickVisuals();
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
                ensureProjectileEscort(entry.level, entry.owner, projectile, now + 45L);
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
            target.setInvisible(true);
            target.setNoGravity(true);
            target.setInvulnerable(true);
            target.setDeltaMovement(Vec3.ZERO);
            target.fallDistance = 0.0F;
            moveEntity(target, entry.anchor);
            if (target instanceof ServerPlayer player) {
                player.hurtMarked = true;
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, 255, false, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 25, 255, false, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 25, 255, false, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 0, false, false, false));
            }
            if (now % 6L == 0L) {
                entry.level.sendParticles(ParticleTypes.REVERSE_PORTAL, entry.anchor.x, entry.anchor.y + 1.0, entry.anchor.z, 12, 0.45, 0.8, 0.45, 0.06);
                drawGlitchCage(entry.level, entry.anchor, 1.30, 2.8, now * 0.26, false);
            }
        }
    }

    private static void tickReality(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        Vec3 center = new Vec3(data.anomalyRealityX(), data.anomalyRealityY(), data.anomalyRealityZ());
        int stage = data.masteryStage(6);
        boolean systemCrash = data.classAwakeningActive(now);
        double radius = 18.0 + stage * 2.0 + (systemCrash ? 5.0 : 0.0);
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 2, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 30, 1, false, false, true));
        data.reduceAllCooldowns(now, systemCrash ? 4 : 1); // Sistem Çökmesi sırasında alan çok daha hızlı çalışır.
        if (now % 4L == 0L) PowerSystem.drawExternalRing(level, center, radius, ParticleTypes.WITCH, 72);

        AABB area = new AABB(center, center).inflate(radius, 7.0, radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (target == player || PowerSystem.isProtectedAlly(player, target)) continue;
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 3, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 2, false, false, true));
            if (now % (systemCrash ? 12L : 20L) == 0L) target.hurtServer(level, level.damageSources().playerAttack(player), 5.0F + stage * 1.5F + (systemCrash ? 4.0F : 0.0F));
            if (now % 12L == 0L) {
                double jx = (level.getRandom().nextDouble() - 0.5) * 1.6;
                double jz = (level.getRandom().nextDouble() - 0.5) * 1.6;
                Vec3 candidate = target.position().add(jx, 0.0, jz);
                if (safeForLiving(level, target, candidate)) moveEntity(target, candidate);
            }
        }
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, area)) {
            if (projectile.getOwner() == player) continue;
            FROZEN_PROJECTILES.computeIfAbsent(projectile.getUUID(), ignored -> new FrozenProjectile(
                level, projectile, projectile.position(), projectile.getDeltaMovement(), projectile.isNoGravity(),
                projectile.getOwner() == null ? null : projectile.getOwner().getUUID(), player.getUUID(), data.anomalyRealityUntil()
            ));
            projectile.setNoGravity(true);
            projectile.setDeltaMovement(Vec3.ZERO);
            ensureProjectileEscort(level, player.getUUID(), projectile, now + 70L);
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
        Iterator<Map.Entry<UUID, FrozenProjectile>> frozenIterator = FROZEN_PROJECTILES.entrySet().iterator();
        while (frozenIterator.hasNext()) {
            FrozenProjectile frozen = frozenIterator.next().getValue();
            if (!frozen.anomalyOwner.equals(playerId)) continue;
            releaseProjectile(frozen, false);
            frozenIterator.remove();
        }
    }

    private static void restoreVoidedTarget(VoidedTarget entry, LivingEntity target, boolean applyReturnPenalty) {
        target.setInvisible(entry.wasInvisible);
        target.setNoGravity(entry.wasNoGravity);
        target.setInvulnerable(entry.wasInvulnerable);
        moveEntity(target, entry.anchor);
        if (target instanceof ServerPlayer restoredPlayer) {
            restoredPlayer.hurtMarked = true;
            restoredPlayer.sendSystemMessage(Component.literal("Gerçekliğe geri döndün."));
        }
        if (!applyReturnPenalty || !target.isAlive()) return;

        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 3, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2, false, true, true));
        ServerPlayer owner = entry.level.getServer().getPlayerList().getPlayer(entry.owner);
        target.hurtServer(entry.level, owner == null ? entry.level.damageSources().generic() : entry.level.damageSources().playerAttack(owner), 11.0F);
        for (LivingEntity nearby : entry.level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(4.5))) {
            if (nearby == target || (owner != null && PowerSystem.isProtectedAlly(owner, nearby))) continue;
            nearby.hurtServer(entry.level, owner == null ? entry.level.damageSources().generic() : entry.level.damageSources().playerAttack(owner), 5.0F);
            Vec3 push = nearby.position().subtract(target.position());
            if (push.lengthSqr() > 0.001) {
                push = push.normalize().scale(1.15);
                nearby.push(push.x, 0.45, push.z);
            }
        }
        entry.level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 1.0, target.getZ(), 110, 1.2, 1.35, 1.2, 0.18);
        drawGlitchCage(entry.level, target.position(), 2.10, 3.2, entry.level.getGameTime() * 0.34, true);
        PowerSystem.drawExternalRing(entry.level, target.position().add(0.0, 0.15, 0.0), 3.2, ParticleTypes.REVERSE_PORTAL, 64);
        ServerNetworking.sendScreenShake(entry.level, target.position(), 22.0, 1.0F, 10);
    }

    public static void afterDeath(LivingEntity entity, DamageSource source) {
        if (entity instanceof ServerPlayer player) clearPlayer(player);
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
        COPIED_EXPIRES.remove(ownerId);
        discardOwnerVisuals(ownerId);
        data.clearAnomalyStoredDamage();
        data.clearAnomalyReality();
        Iterator<Map.Entry<UUID, FrozenProjectile>> frozenIterator = FROZEN_PROJECTILES.entrySet().iterator();
        while (frozenIterator.hasNext()) {
            FrozenProjectile frozen = frozenIterator.next().getValue();
            if (!frozen.anomalyOwner.equals(ownerId)) continue;
            releaseProjectile(frozen, false);
            frozenIterator.remove();
        }
    }

    public static void clearAll() {
        for (FrozenProjectile frozen : FROZEN_PROJECTILES.values()) releaseProjectile(frozen, false);
        FROZEN_PROJECTILES.clear();
        for (VoidedTarget entry : VOIDED.values()) {
            restoreVoidedTarget(entry, entry.targetEntity, false);
        }
        REVERSED.clear();
        VOIDED.clear();
        ECHOES.clear();
        for (AnomalyVisual visual : VISUALS) {
            Entity entity = visual.level.getEntity(visual.entityId);
            if (entity != null) entity.discard();
        }
        VISUALS.clear();
        for (ProjectileEscort escort : PROJECTILE_ESCORTS) discardAll(escort.level, escort.ids);
        PROJECTILE_ESCORTS.clear();
        COPIED_EXPIRES.clear();
    }

    static ServerPlayer findRealityOwner(ServerLevel level, Vec3 position, UUID attackOwner) {
        for (ServerPlayer candidate : level.players()) {
            PlayerPowerData data = PlayerDataStore.get(candidate.getUUID());
            long now = level.getGameTime();
            if (data.powerClass() != PowerClass.ANOMALY || data.anomalyRealityUntil() <= now) continue;
            if (attackOwner != null && attackOwner.equals(candidate.getUUID())) continue;
            Vec3 center = new Vec3(data.anomalyRealityX(), data.anomalyRealityY(), data.anomalyRealityZ());
            double radius = 18.0 + data.masteryStage(6) * 2.0;
            if (position.distanceToSqr(center) <= radius * radius) return candidate;
        }
        return null;
    }

    private static void tickFrozenProjectiles() {
        Iterator<FrozenProjectile> iterator = FROZEN_PROJECTILES.values().iterator();
        while (iterator.hasNext()) {
            FrozenProjectile frozen = iterator.next();
            Projectile projectile = frozen.projectile;
            long now = frozen.level.getGameTime();
            if (projectile.isRemoved()) {
                iterator.remove();
                continue;
            }
            PlayerPowerData ownerData = PlayerDataStore.get(frozen.anomalyOwner);
            boolean fieldOpen = ownerData.powerClass() == PowerClass.ANOMALY && ownerData.anomalyRealityUntil() > now;
            if (fieldOpen && now < frozen.releaseAt) {
                projectile.setPos(frozen.anchor.x, frozen.anchor.y, frozen.anchor.z);
                projectile.setDeltaMovement(Vec3.ZERO);
                projectile.setNoGravity(true);
                ensureProjectileEscort(frozen.level, frozen.anomalyOwner, projectile, now + 30L);
                if (now % 4L == 0L) frozen.level.sendParticles(ParticleTypes.WITCH, frozen.anchor.x, frozen.anchor.y, frozen.anchor.z, 8, 0.25, 0.25, 0.25, 0.03);
                continue;
            }
            releaseProjectile(frozen, true);
            iterator.remove();
        }
    }

    private static void releaseProjectile(FrozenProjectile frozen, boolean returnToOwner) {
        Projectile projectile = frozen.projectile;
        if (projectile.isRemoved()) return;
        Vec3 velocity = frozen.velocity.scale(-1.0);
        Entity originalOwner = frozen.originalOwner == null ? null : frozen.level.getEntity(frozen.originalOwner);
        ServerPlayer anomalyOwner = frozen.level.getServer().getPlayerList().getPlayer(frozen.anomalyOwner);
        if (returnToOwner && originalOwner instanceof LivingEntity living && living.isAlive()) {
            Vec3 target = living.getEyePosition().subtract(projectile.position());
            double speed = Math.max(1.15, Math.sqrt(frozen.velocity.lengthSqr()));
            if (target.lengthSqr() > 0.001) velocity = target.normalize().scale(speed);
            if (anomalyOwner != null) projectile.setOwner(anomalyOwner);
        }
        projectile.setNoGravity(frozen.wasNoGravity);
        projectile.setDeltaMovement(velocity);
        ensureProjectileEscort(frozen.level, frozen.anomalyOwner, projectile, frozen.level.getGameTime() + 55L);
        frozen.level.sendParticles(ParticleTypes.REVERSE_PORTAL, projectile.getX(), projectile.getY(), projectile.getZ(), 22, 0.35, 0.35, 0.35, 0.08);
    }

    private static void ensureProjectileEscort(ServerLevel level, UUID owner, Projectile projectile, long expireTick) {
        for (ProjectileEscort escort : PROJECTILE_ESCORTS) {
            if (escort.projectileId.equals(projectile.getUUID())) {
                escort.expireTick = Math.max(escort.expireTick, expireTick);
                return;
            }
        }
        List<UUID> ids = new ArrayList<>();
        Item[] items = {Items.ENDER_EYE, Items.REDSTONE, Items.ECHO_SHARD, Items.AMETHYST_SHARD};
        for (int i = 0; i < 10; i++) {
            ItemEntity visual = createFlyingVisual(level, projectile.position(), items[i % items.length]);
            if (visual != null) ids.add(visual.getUUID());
        }
        PROJECTILE_ESCORTS.add(new ProjectileEscort(level, owner, projectile.getUUID(), ids, expireTick));
    }

    private static ItemEntity createFlyingVisual(ServerLevel level, Vec3 position, Item item) {
        ItemEntity visual = new ItemEntity(level, position.x, position.y, position.z, new ItemStack(item));
        visual.setNoGravity(true);
        visual.setDeltaMovement(Vec3.ZERO);
        visual.setInvulnerable(true);
        visual.setGlowingTag(true);
        visual.setNeverPickUp();
        visual.setUnlimitedLifetime();
        return level.addFreshEntity(visual) ? visual : null;
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
                double angle = now * 0.42 + Math.PI * 2.0 * i / Math.max(1, escort.ids.size());
                double radius = 0.42 + (i % 2) * 0.18;
                Vec3 target = projectile.position()
                    .add(right.scale(Math.cos(angle) * radius))
                    .add(0.0, Math.sin(angle) * radius, 0.0)
                    .add(forward.scale(-0.18 - (i % 3) * 0.14));
                moveFlyingVisual(visual, target);
            }
        }
    }

    private static void moveFlyingVisual(Entity visual, Vec3 target) {
        Vec3 delta = target.subtract(visual.position());
        if (delta.lengthSqr() > 36.0) {
            visual.setPos(target.x, target.y, target.z);
            visual.setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (delta.lengthSqr() < 0.0004) {
            visual.setDeltaMovement(Vec3.ZERO);
            return;
        }
        Vec3 motion = delta.scale(0.96);
        if (motion.lengthSqr() > 9.0) motion = motion.normalize().scale(3.0);
        visual.setDeltaMovement(motion);
    }

    private static void discardAll(ServerLevel level, List<UUID> ids) {
        for (UUID id : new ArrayList<>(ids)) {
            Entity entity = level.getEntity(id);
            if (entity != null) entity.discard();
        }
    }

    private static void tickEchoes() {
        Iterator<PendingEcho> iterator = ECHOES.iterator();
        while (iterator.hasNext()) {
            PendingEcho echo = iterator.next();
            long now = echo.level.getGameTime();
            if (now < echo.detonateTick) {
                echo.level.sendParticles(ParticleTypes.REVERSE_PORTAL, echo.position.x, echo.position.y + 0.9, echo.position.z,
                    echo.empowered ? 8 : 4, 0.28, 0.55, 0.28, 0.035);
                continue;
            }
            ServerPlayer owner = echo.level.getServer().getPlayerList().getPlayer(echo.owner);
            double radius = echo.empowered ? 3.8 : 2.7;
            for (LivingEntity target : echo.level.getEntitiesOfClass(LivingEntity.class, new AABB(echo.position, echo.position).inflate(radius))) {
                if (owner != null && (target == owner || PowerSystem.isProtectedAlly(owner, target))) continue;
                target.hurtServer(echo.level, owner == null ? echo.level.damageSources().generic() : echo.level.damageSources().playerAttack(owner),
                    4.0F + echo.stage * 1.5F + (echo.empowered ? 4.0F : 0.0F));
                Vec3 push = target.position().subtract(echo.position);
                if (push.lengthSqr() > 0.001) {
                    push = push.normalize().scale(echo.empowered ? 1.25 : 0.75);
                    target.push(push.x, echo.empowered ? 0.55 : 0.30, push.z);
                }
            }
            echo.level.sendParticles(ParticleTypes.WITCH, echo.position.x, echo.position.y + 0.8, echo.position.z,
                echo.empowered ? 48 : 28, radius * 0.45, 0.7, radius * 0.45, 0.08);
            iterator.remove();
        }
    }

    /** Parçacıklardan oluşan üç katmanlı Anomali kalkanı; eşya modeli kullanmaz. */
    private static void drawAnomalyShield(ServerLevel level, Vec3 center, boolean empowered, double phase) {
        int points = empowered ? 28 : 22;
        double[] heights = {-0.72, 0.0, 0.72};
        for (int layer = 0; layer < heights.length; layer++) {
            double radius = (empowered ? 1.58 : 1.28) - Math.abs(layer - 1) * 0.16;
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2.0 * i / points + phase + layer * 0.65;
                Vec3 point = center.add(Math.cos(angle) * radius, heights[layer], Math.sin(angle) * radius);
                level.sendParticles((i + layer) % 3 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.WITCH,
                    point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /** Görünmez hedefin yerini açıkça gösteren dönen, dikey parçacık kafesi. */
    private static void drawGlitchCage(ServerLevel level, Vec3 base, double radius, double height, double phase, boolean intense) {
        int rings = intense ? 4 : 3;
        int points = intense ? 20 : 14;
        for (int ring = 0; ring < rings; ring++) {
            double y = 0.15 + height * ring / Math.max(1.0, rings - 1.0);
            double ringRadius = radius * (0.90 + Math.sin(phase + ring * 0.8) * 0.10);
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2.0 * i / points + phase * (ring % 2 == 0 ? 1.0 : -1.0);
                Vec3 point = base.add(Math.cos(angle) * ringRadius, y, Math.sin(angle) * ringRadius);
                level.sendParticles((i + ring) % 4 == 0 ? ParticleTypes.WITCH : ParticleTypes.REVERSE_PORTAL,
                    point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        int columns = intense ? 7 : 5;
        for (int column = 0; column < columns; column++) {
            double angle = Math.PI * 2.0 * column / columns + phase;
            int steps = intense ? 5 : 4;
            for (int step = 0; step <= steps; step++) {
                Vec3 point = base.add(Math.cos(angle) * radius, 0.15 + height * step / steps, Math.sin(angle) * radius);
                level.sendParticles(ParticleTypes.WITCH, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /** Depolanmış hasarın kullanıcıdan hedefe aktığını gösteren çift sarmallı ışın. */
    private static void drawAnomalyBeam(ServerLevel level, Vec3 start, Vec3 end, long now) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 0.01) return;
        Vec3 direction = delta.scale(1.0 / length);
        Vec3 side = direction.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 0.001) side = new Vec3(1.0, 0.0, 0.0);
        else side = side.normalize();
        Vec3 up = side.cross(direction).normalize();
        int samples = Math.max(12, (int) Math.ceil(length * 2.5));
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            double angle = t * Math.PI * 8.0 + now * 0.32;
            double radius = 0.18 + Math.sin(t * Math.PI) * 0.22;
            Vec3 center = start.add(delta.scale(t));
            Vec3 offset = side.scale(Math.cos(angle) * radius).add(up.scale(Math.sin(angle) * radius));
            Vec3 a = center.add(offset);
            Vec3 b = center.subtract(offset);
            level.sendParticles(ParticleTypes.WITCH, a.x, a.y, a.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, b.x, b.y, b.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void drawHealthConversion(ServerLevel level, Vec3 center, long now) {
        for (int ring = 0; ring < 3; ring++) {
            double radius = 0.85 + ring * 0.42;
            double y = -0.65 + ring * 0.65;
            for (int i = 0; i < 24; i++) {
                double angle = Math.PI * 2.0 * i / 24.0 + now * 0.18 + ring * 0.7;
                Vec3 point = center.add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
                level.sendParticles(i % 3 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.WITCH,
                    point.x, point.y, point.z, 1, 0.0, 0.02, 0.0, 0.0);
            }
        }
        level.playSound(null, BlockPos.containing(center), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.85F, 1.45F);
    }

    private static void spawnGlitchFigure(ServerLevel level, UUID owner, Vec3 base, long expireTick, double phase) {
        Item[] items = {Items.ENDER_EYE, Items.REDSTONE, Items.AMETHYST_SHARD, Items.ECHO_SHARD};
        double[][] offsets = {{0,1.9,0},{0,1.35,0},{-0.32,1.28,0},{0.32,1.28,0},{-0.25,0.72,0},{0.25,0.72,0},{-0.18,0.15,0},{0.18,0.15,0}};
        for (int i = 0; i < offsets.length; i++) {
            Vec3 pos = base.add(offsets[i][0], offsets[i][1], offsets[i][2]);
            spawnVisual(level, owner, pos, items[(i + (int) phase) % items.length], expireTick);
        }
    }

    private static void spawn404Body(ServerLevel level, UUID owner, Vec3 center, long expireTick) {
        int[][] pixels = {
            {-5,2},{-5,1},{-5,0},{-4,0},{-3,0},{-3,1},{-3,2},
            {-1,2},{0,2},{1,2},{-1,1},{1,1},{-1,0},{0,0},{1,0},
            {3,2},{3,1},{3,0},{4,0},{5,0},{5,1},{5,2}
        };
        for (int i = 0; i < pixels.length; i++) {
            Item item = i % 3 == 0 ? Items.ENDER_EYE : (i % 2 == 0 ? Items.REDSTONE : Items.AMETHYST_SHARD);
            spawnVisual(level, owner, center.add(pixels[i][0] * 0.42, pixels[i][1] * 0.55, 0.0), item, expireTick);
        }
    }

    private static void spawnVisibleRing(ServerLevel level, UUID owner, Vec3 center, Item[] items, int count, double radius, long expireTick) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / Math.max(1, count);
            Vec3 pos = center.add(Math.cos(angle) * radius, Math.sin(angle * 2.0) * 0.18, Math.sin(angle) * radius);
            spawnVisual(level, owner, pos, items[i % items.length], expireTick);
        }
    }

    private static void spawnVisual(ServerLevel level, UUID owner, Vec3 position, Item item, long expireTick) {
        ItemEntity visual = new ItemEntity(level, position.x, position.y, position.z, new ItemStack(item));
        visual.setNoGravity(true);
        visual.setDeltaMovement(Vec3.ZERO);
        visual.setInvulnerable(true);
        visual.setNeverPickUp();
        visual.setUnlimitedLifetime();
        level.addFreshEntity(visual);
        VISUALS.add(new AnomalyVisual(level, owner, visual.getUUID(), expireTick));
    }

    private static void tickVisuals() {
        Iterator<AnomalyVisual> iterator = VISUALS.iterator();
        while (iterator.hasNext()) {
            AnomalyVisual visual = iterator.next();
            Entity entity = visual.level.getEntity(visual.entityId);
            if (entity == null || entity.isRemoved() || visual.level.getGameTime() >= visual.expireTick) {
                if (entity != null) entity.discard();
                iterator.remove();
            }
        }
    }

    private static void discardOwnerVisuals(UUID owner) {
        Iterator<AnomalyVisual> iterator = VISUALS.iterator();
        while (iterator.hasNext()) {
            AnomalyVisual visual = iterator.next();
            if (!visual.owner.equals(owner)) continue;
            Entity entity = visual.level.getEntity(visual.entityId);
            if (entity != null) entity.discard();
            iterator.remove();
        }
        Iterator<ProjectileEscort> escortIterator = PROJECTILE_ESCORTS.iterator();
        while (escortIterator.hasNext()) {
            ProjectileEscort escort = escortIterator.next();
            if (!escort.owner.equals(owner)) continue;
            discardAll(escort.level, escort.ids);
            escortIterator.remove();
        }
    }

    private static Vec3 findSafeStepPosition(ServerLevel level, Vec3 candidate) {
        BlockPos base = BlockPos.containing(candidate);
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos feet = base.offset(0, dy, 0);
            if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                && !level.getBlockState(feet.below()).isAir()) {
                return new Vec3(candidate.x, feet.getY(), candidate.z);
            }
        }
        return null;
    }

    private static Vec3 horizontal(Vec3 look) {
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        return horizontal.lengthSqr() < 0.0001 ? new Vec3(0.0, 0.0, 1.0) : horizontal.normalize();
    }

    private static Vec3 perpendicular(Vec3 direction) {
        Vec3 h = horizontal(direction);
        return new Vec3(-h.z, 0.0, h.x);
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

    private static final class FrozenProjectile {
        private final ServerLevel level;
        private final Projectile projectile;
        private final Vec3 anchor;
        private final Vec3 velocity;
        private final boolean wasNoGravity;
        private final UUID originalOwner;
        private final UUID anomalyOwner;
        private final long releaseAt;

        private FrozenProjectile(ServerLevel level, Projectile projectile, Vec3 anchor, Vec3 velocity, boolean wasNoGravity,
                                 UUID originalOwner, UUID anomalyOwner, long releaseAt) {
            this.level = level; this.projectile = projectile; this.anchor = anchor; this.velocity = velocity;
            this.wasNoGravity = wasNoGravity; this.originalOwner = originalOwner; this.anomalyOwner = anomalyOwner; this.releaseAt = releaseAt;
        }
    }

    private record AnomalyVisual(ServerLevel level, UUID owner, UUID entityId, long expireTick) {}
    private record PendingEcho(ServerLevel level, UUID owner, Vec3 position, long detonateTick, int stage, boolean empowered) {}
    private record ReversedTarget(ServerLevel level, UUID owner, UUID target, long expireTick) {}
    private record VoidedTarget(ServerLevel level, UUID owner, UUID target, LivingEntity targetEntity, Vec3 anchor, long expireTick,
                                boolean wasInvisible, boolean wasNoGravity, boolean wasInvulnerable) {}
}
