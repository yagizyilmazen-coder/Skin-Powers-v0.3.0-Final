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
    private static final Map<UUID, Long> LAST_SKY_IMPACT = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_FLIGHT_POSITION = new HashMap<>();
    private static final Map<UUID, long[]> LAST_MASTERY_CREDIT = new HashMap<>();
    private static long lastAutosaveTick;

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

        PlayerPowerData data = PlayerDataStore.get(serverPlayer.getUUID());
        if (data.powerClass() != PowerClass.FIRE || data.unlockedLevel() < 2) {
            return InteractionResult.PASS;
        }

        long now = serverLevel.getGameTime();
        boolean charged = data.selectedPower() == 2 && AncientChargeSystem.isUsableCharge(data, now, 2);
        float damage = AncientChargeSystem.damage(4.0F, charged);
        target.hurtServer(serverLevel, serverLevel.damageSources().playerAttack(serverPlayer), damage);
        target.setRemainingFireTicks(charged ? 220 : 80);
        serverLevel.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 1.0, target.getZ(), charged ? 28 : 12, 0.35, 0.45, 0.35, 0.02);
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
        AncientChargeSystem.tick(server);

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

        switch (data.powerClass()) {
            case WARDEN -> tickWarden(player, data, level, now);
            case FLIGHT -> tickFlight(player, data, level, now);
            case FIRE -> tickFire(player, data, level, now);
            case NATURE -> tickNature(player, data, level, now);
            default -> { }
        }

        if (now % 10L == 0L) ServerNetworking.sync(player);
    }

    private static void tickWarden(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        if (data.wardenHuntUntil() > now) {
            int stage = data.masteryStage(4);
            boolean boosted = data.chargedWardenHunt();
            double radius = AncientChargeSystem.radius(20.0 + stage * 2.0, boosted);
            if (now % 5L == 0L) {
                for (LivingEntity living : nearbyLiving(player, radius)) {
                    if (living == player || protectedAlly(player, living)) continue;
                    living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 35, 0, false, false, true));
                    living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 35, stage >= 2 ? 2 : 1, false, false, true));
                    living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 35, stage >= 3 ? 1 : 0, false, false, true));
                    if (now % 20L == 0L && living.getDeltaMovement().horizontalDistanceSqr() > 0.001) {
                        living.hurtServer(level, level.damageSources().playerAttack(player), AncientChargeSystem.damage(2.0F + stage, boosted));
                        level.sendParticles(boosted ? ParticleTypes.WITCH : ParticleTypes.SCULK_SOUL, living.getX(), living.getY() + 0.8, living.getZ(), boosted ? 16 : 8, 0.35, 0.45, 0.35, 0.025);
                    }
                }
                drawRing(level, player.position(), Math.min(boosted ? 14.0 : 9.0, radius * 0.42), boosted ? ParticleTypes.WITCH : ParticleTypes.SCULK_SOUL, boosted ? 58 : 34);
            }
        } else if (data.wardenHuntUntil() != 0L || data.visionEnabled()) {
            data.setWardenHuntUntil(0L);
            data.setChargedWardenHunt(false);
            data.setVisionEnabled(false);
            player.sendSystemMessage(Component.literal("Sculk Avı sona erdi."));
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
        // 0.3.1 sürümünden kalmış sınırsız mayfly yetkisini temizle.
        if (player.getAbilities().mayfly && !player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }

        if (data.unlockedLevel() >= 1 && data.passiveEnabled()) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false, true));
            player.fallDistance = 0.0F;
        }

        boolean temporaryFlight = data.temporaryElytraUntil() > now;
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (temporaryFlight) {
            if (!chest.is(Items.ELYTRA)) {
                data.setTemporaryElytraUntil(0L);
                data.setChargedTemporaryElytra(false);
                player.sendSystemMessage(Component.literal("Süreli Elytra çıkarıldığı için uçuş sona erdi."));
                PlayerDataStore.markDirty();
                temporaryFlight = false;
            } else if (player.getDeltaMovement().lengthSqr() > 0.08 && now % 2L == 0L) {
                Vec3 back = player.getLookAngle().scale(-0.75);
                level.sendParticles(data.chargedTemporaryElytra() ? ParticleTypes.WITCH : ParticleTypes.CLOUD,
                    player.getX() + back.x, player.getY() + 0.9, player.getZ() + back.z,
                    data.chargedTemporaryElytra() ? 9 : 4, 0.30, 0.24, 0.30, 0.015);
                if (data.chargedTemporaryElytra()) {
                    level.sendParticles(ParticleTypes.SCULK_SOUL,
                        player.getX() + back.x, player.getY() + 0.9, player.getZ() + back.z,
                        3, 0.22, 0.18, 0.22, 0.01);
                }
            }
        } else if (data.temporaryElytraUntil() != 0L) {
            removeTemporaryElytra(player, data);
            player.sendSystemMessage(Component.literal("Süreli Elytra kayboldu."));
            PlayerDataStore.markDirty();
        }

        Vec3 currentPosition = player.position();
        Vec3 previousPosition = LAST_FLIGHT_POSITION.put(player.getUUID(), currentPosition);
        if (!temporaryFlight) {
            LAST_FLIGHT_POSITION.remove(player.getUUID());
            return;
        }

        if (data.unlockedLevel() >= 5 && player.getDeltaMovement().lengthSqr() > 0.42) {
            // Gökyüzü Hâkimiyeti yüksek hızlı çarpma hasarını büyük ölçüde emer.
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 6, 2, false, false, true));
            player.fallDistance = 0.0F;
        }

        if (data.unlockedLevel() >= 5 && previousPosition != null
            && previousPosition.distanceToSqr(currentPosition) <= 36.0
            && player.getDeltaMovement().lengthSqr() > 0.42) {
            long lastImpact = LAST_SKY_IMPACT.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
            if (now - lastImpact >= 24L) {
                int stage = data.masteryStage(5);
                boolean skyCombo = data.selectedPower() == 5 && data.comboActive(now)
                    && data.comboStarterPower() == 2 && data.powerClass() == PowerClass.FLIGHT;
                boolean chargeFromReadyState = data.selectedPower() == 5 && AncientChargeSystem.isUsableCharge(data, now, 5);
                // Şarjlı Süreli Elytra yalnızca kendi süresini ve görünümünü güçlendirir.
                // Gökyüzü Hâkimiyeti ancak 5. güç seçiliyken tek kullanım hakkını ayrıca tüketir.
                boolean boosted = chargeFromReadyState;
                double hitRadius = AncientChargeSystem.radius((skyCombo ? 4.2 : 2.25) + stage * 0.20, boosted);
                AABB sweptArea = new AABB(previousPosition, currentPosition).inflate(hitRadius);
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, sweptArea)) {
                    if (target == player || protectedAlly(player, target)) continue;
                    Vec3 targetCenter = target.getEyePosition();
                    if (distanceToSegmentSqr(targetCenter, previousPosition, currentPosition) > hitRadius * hitRadius) continue;

                    target.hurtServer(level, level.damageSources().playerAttack(player), AncientChargeSystem.damage((skyCombo ? 18.0F : 12.0F) + stage * 2.0F, boosted));
                    Vec3 push = player.getDeltaMovement();
                    if (push.lengthSqr() < 0.0001) push = player.getLookAngle();
                    push = push.normalize().scale(AncientChargeSystem.knockback((skyCombo ? 1.85 : 1.25) + stage * 0.14, boosted));
                    target.push(push.x, skyCombo ? 0.68 : 0.42, push.z);

                    // Darbenin oyuncuya geri dönmesini azalt: hız yumuşatılır ve düşüş birikimi sıfırlanır.
                    player.setDeltaMovement(player.getDeltaMovement().scale(0.58));
                    player.fallDistance = 0.0F;
                    player.hurtMarked = true;
                    LAST_SKY_IMPACT.put(player.getUUID(), now);
                    level.sendParticles(boosted ? ParticleTypes.WITCH : ParticleTypes.CLOUD, target.getX(), target.getY() + 0.7, target.getZ(), skyCombo ? 96 : (boosted ? 68 : 36), skyCombo ? 1.5 : 0.9, skyCombo ? 1.25 : 0.9, skyCombo ? 1.5 : 0.9, 0.11);
                    if (skyCombo) {
                        Vec3 impactCenter = target.position();
                        double shockRadius = AncientChargeSystem.radius(6.0 + stage * 0.45, boosted);
                        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, new AABB(impactCenter, impactCenter).inflate(shockRadius))) {
                            if (nearby == player || nearby == target || protectedAlly(player, nearby)) continue;
                            nearby.hurtServer(level, level.damageSources().playerAttack(player), AncientChargeSystem.damage(9.0F + stage * 1.4F, boosted));
                            Vec3 outward = nearby.position().subtract(impactCenter);
                            if (outward.lengthSqr() > 0.001) {
                                outward = outward.normalize().scale(AncientChargeSystem.knockback(1.35 + stage * 0.10, boosted));
                                nearby.push(outward.x, 0.48, outward.z);
                            }
                        }
                        drawRing(level, impactCenter.add(0.0, 0.2, 0.0), shockRadius, boosted ? ParticleTypes.WITCH : ParticleTypes.CLOUD, boosted ? 110 : 76);
                        ServerNetworking.sendScreenShake(level, impactCenter, boosted ? 38.0 : 28.0, boosted ? 1.8F : 1.3F, boosted ? 24 : 17);
                        player.sendSystemMessage(Component.literal("GÖK DALIŞI!"));
                        data.clearCombo();
                        data.setCooldown(5, now, Math.max(420, 620 - stage * 45));
                        PlayerDataStore.markDirty();
                    }
                    if (chargeFromReadyState) {
                        if (skyCombo) data.consumeAncientChargeForCombo(now, 2, 5);
                        else AncientChargeSystem.consume(player, data, 5, now);
                    }
                    creditMastery(player, data, 5, now, 24L);
                    ServerNetworking.sync(player);
                    break;
                }
            }
        }
    }

    private static void tickFire(ServerPlayer player, PlayerPowerData data, ServerLevel level, long now) {
        if (data.unlockedLevel() >= 1) {
            boolean preventedFire = player.getRemainingFireTicks() > 0;
            player.setRemainingFireTicks(0);
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, true));
            if (preventedFire) creditMastery(player, data, 1, now, 200L);
        }

        if (data.fireRingUntil() > now) {
            int stage = data.masteryStage(3);
            boolean boosted = data.chargedFireRing();
            double radius = AncientChargeSystem.radius(10.0 + stage * 0.6, boosted);
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
        if (naturalGround && player.getHealth() < player.getMaxHealth() && now % 160L == 0L) {
            int stage = data.masteryStage(1);
            player.heal(1.0F + stage * 0.35F);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 0.3, player.getZ(), 8, 0.45, 0.25, 0.45, 0.02);
            creditMastery(player, data, 1, now, 600L);
        }
        if (data.natureTreeUntil() != 0L && data.natureTreeUntil() <= now) {
            data.setNatureTreeUntil(0L);
            PlayerDataStore.markDirty();
        }
    }

    public static void useSelectedPower(ServerPlayer player, PlayerPowerData data) {
        if (data.powerClass() == PowerClass.NONE || data.unlockedLevel() == 0) {
            player.sendSystemMessage(Component.literal("Önce O ekranından bir seviye açmalısın."));
            return;
        }
        int power = data.selectedPower();
        if (power > data.unlockedLevel()) return;

        long now = player.level().getGameTime();
        data.comboActive(now); // Süresi dolmuş kombo penceresini temizle.
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

        if (expectedFinisher && data.powerClass() != PowerClass.FLIGHT) {
            boolean comboUsed = useComboFinisher(player, data, power, now, charged);
            if (comboUsed) {
                recordMasteryUse(player, data, power);
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
        boolean normalCharged = charged && !comboStarter;
        boolean used = switch (data.powerClass()) {
            case WARDEN -> useWarden(player, data, power, now, normalCharged);
            case FLIGHT -> useFlight(player, data, power, now, normalCharged);
            case FIRE -> useFire(player, data, power, now, normalCharged, comboStarter);
            case NATURE -> useNature(player, data, power, now, normalCharged, comboStarter);
            default -> false;
        };

        if (used) {
            if (comboStarter) beginImmediateComboIfNeeded(player, data, power, now);
            recordMasteryUse(player, data, power);
            if (normalCharged) AncientChargeSystem.consume(player, data, power, now);
            PlayerDataStore.markDirty();
            ServerNetworking.sync(player);
        }
    }

    public static void toggleSelectedFeature(ServerPlayer player, PlayerPowerData data) {
        boolean changed = false;
        long now = player.level().getGameTime();
        if (data.powerClass() == PowerClass.FLIGHT && data.unlockedLevel() >= 1) {
            int remaining = data.cooldownRemaining(1, now);
            if (remaining > 0) {
                player.sendSystemMessage(Component.literal("Yavaş Düşüş " + formatSeconds(remaining) + " saniye sonra değiştirilebilir."));
                return;
            }
            data.togglePassive();
            data.setCooldown(1, now, 40);
            recordMasteryUse(player, data, 1);
            changed = true;
            player.sendSystemMessage(Component.literal("Yavaş Düşüş: " + (data.passiveEnabled() ? "AÇIK" : "KAPALI")));
        } else if (data.powerClass() == PowerClass.WARDEN && data.unlockedLevel() >= 4) {
            player.sendSystemMessage(Component.literal("Sculk Avı aç/kapat değildir; 4. gücü seçip R ile kullan."));
        } else if (data.powerClass() == PowerClass.FIRE) {
            player.sendSystemMessage(Component.literal("Ateş sınıfındaki güçler R ile veya otomatik olarak çalışır."));
        } else if (data.powerClass() == PowerClass.NATURE) {
            player.sendSystemMessage(Component.literal("Doğa sınıfındaki güçler R ile veya otomatik olarak çalışır."));
        }
        if (changed) {
            PlayerDataStore.markDirty();
            ServerNetworking.sync(player);
        }
    }

    public static void toggleComboMode(ServerPlayer player, PlayerPowerData data) {
        boolean enabled = data.toggleComboMode();
        PlayerDataStore.markDirty();
        player.sendSystemMessage(Component.literal("Kombo Modu: " + (enabled ? "AÇIK" : "KAPALI")));
        ServerNetworking.sync(player);
    }

    public static void tryRocketlessLaunch(ServerPlayer player, PlayerPowerData data) {
        long now = player.level().getGameTime();
        boolean charged = data.selectedPower() == 3 && AncientChargeSystem.isUsableCharge(data, now, 3);
        if (performRocketlessLaunch(player, data, now, charged)) {
            recordMasteryUse(player, data, 3);
            if (charged) AncientChargeSystem.consume(player, data, 3, now);
            ServerNetworking.sync(player);
        }
    }

    private static boolean performRocketlessLaunch(ServerPlayer player, PlayerPowerData data, long now, boolean charged) {
        if (data.powerClass() != PowerClass.FLIGHT || data.unlockedLevel() < 3) return false;
        if (data.temporaryElytraUntil() <= now || !player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            player.sendSystemMessage(Component.literal("Önce Süreli Elytra gücünü açmalısın."));
            return false;
        }
        int remaining = data.cooldownRemaining(3, now);
        if (remaining > 0) return false;

        int stage = data.masteryStage(3);
        Vec3 look = player.getLookAngle();
        double launchScale = charged ? 1.62 : 1.0;
        player.setDeltaMovement(
            look.x * (1.0 + stage * 0.14) * launchScale,
            (1.25 + stage * 0.14) * (charged ? 1.28 : 1.0),
            look.z * (1.0 + stage * 0.14) * launchScale
        );
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        data.setCooldown(3, now, Math.max(70, 150 - stage * 20));
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), charged ? 55 : 30, 0.6, 0.25, 0.6, 0.10);
        if (charged) AncientChargeSystem.emitChargedBurst(level, player.position().add(0.0, 0.7, 0.0), PowerClass.FLIGHT, 1.15);
        return true;
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
                drawRing(level, player.position(), radius, charged ? ParticleTypes.WITCH : ParticleTypes.SCULK_SOUL, charged ? 96 : 68);
                level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1.5F, 0.72F);
                data.setCooldown(2, now, Math.max(360, 600 - stage * 60));
                return true;
            }
            case 3 -> {
                sonicBlast(player, data, stage, charged);
                data.setCooldown(3, now, Math.max(220, 380 - stage * 40));
                return true;
            }
            case 4 -> {
                int duration = AncientChargeSystem.duration(400 + stage * 100, charged);
                data.setWardenHuntUntil(now + duration);
                data.setChargedWardenHunt(charged);
                data.setVisionEnabled(true);
                data.setCooldown(4, now, Math.max(600, 900 - stage * 90));
                player.sendSystemMessage(Component.literal("Sculk Avı başladı: " + formatSeconds(duration) + " saniye."));
                level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.9F, 1.35F);
                level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 46, 1.0, 1.0, 1.0, 0.035);
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

    private static boolean useFlight(ServerPlayer player, PlayerPowerData data, int power, long now, boolean charged) {
        int stage = data.masteryStage(power);
        if (power == 1) {
            data.togglePassive();
            data.setCooldown(1, now, 40);
            player.sendSystemMessage(Component.literal("Yavaş Düşüş: " + (data.passiveEnabled() ? "AÇIK" : "KAPALI")));
            return true;
        }
        if (power == 2) {
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            if (!chest.isEmpty()) {
                player.sendSystemMessage(Component.literal("Süreli Elytra için göğüs zırhı yuvasını boşaltmalısın."));
                return false;
            }
            int duration = AncientChargeSystem.duration(400 + stage * 100, charged);
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
            data.setTemporaryElytraUntil(now + duration);
            data.setChargedTemporaryElytra(charged);
            data.setCooldown(2, now, Math.max(700, 1000 - stage * 100));
            ServerLevel level = (ServerLevel) player.level();
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), charged ? 68 : 36, 0.8, 0.8, 0.8, 0.05);
            if (charged) AncientChargeSystem.emitChargedBurst(level, player.position().add(0.0, 1.0, 0.0), PowerClass.FLIGHT, 1.45);
            player.sendSystemMessage(Component.literal("Süreli Elytra takıldı: " + formatSeconds(duration) + " saniye."));
            return true;
        }
        if (power == 3) {
            return performRocketlessLaunch(player, data, now, charged);
        }
        if (power == 4) {
            airBlast(player, stage, charged);
            data.setCooldown(4, now, Math.max(160, 300 - stage * 35));
            return true;
        }
        if (power == 5) {
            player.sendSystemMessage(Component.literal("Gökyüzü Hâkimiyeti, süreli Elytra ile hızlı çarpışmada otomatik çalışır."));
            return false;
        }
        return false;
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
                drawRing(level, player.position(), radius, charged ? ParticleTypes.WITCH : ParticleTypes.FLAME, charged ? 92 : 64);
                level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.8F);
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
            default -> { return false; }
        }
    }

    private static boolean useNature(ServerPlayer player, PlayerPowerData data, int power, long now, boolean charged, boolean comboStarter) {
        ServerLevel level = (ServerLevel) player.level();
        int stage = data.masteryStage(power);
        switch (power) {
            case 1 -> {
                player.sendSystemMessage(Component.literal("Doğal Yenilenme, doğal zeminde otomatik çalışır."));
                return false;
            }
            case 2 -> {
                launchNatureSeed(player, stage, charged);
                data.setCooldown(2, now, Math.max(90, 140 - stage * 12));
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
                int steps = charged ? 32 + stage * 3 : 22 + stage * 2;
                ROOT_WAVES.add(new PendingRootWave(level, player.getUUID(), start, direction, now, steps, stage, charged));
                data.setCooldown(5, now, Math.max(700, 900 - stage * 60));
                ServerNetworking.sendScreenShake(level, player.position(), charged ? 40.0 : 28.0, charged ? 1.85F : 1.25F, charged ? 24 : 16);
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
            case NATURE -> {
                Vec3 center = comboTarget(data, findGroundPoint((ServerLevel) player.level(), player.position().add(horizontalDirection(player.getLookAngle()).scale(7.0))));
                launchNatureComboSeed(player, stage, charged, center);
                data.setCooldown(2, now, Math.max(120, 180 - stage * 10));
                player.sendSystemMessage(Component.literal("DİKEN ORMANI!"));
                yield true;
            }
            default -> false;
        };
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
        seed.level.playSound(null, baseCenter, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1.4F, 0.72F);
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

        for (double distance = 1.0; distance <= range; distance += 1.25) {
            Vec3 point = origin.add(look.scale(distance));
            level.sendParticles(ParticleTypes.SONIC_BOOM, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
            if (charged) level.sendParticles(ParticleTypes.WITCH, point.x, point.y, point.z, 4, 0.18, 0.18, 0.18, 0.01);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6F, 0.92F);
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
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.3F, 0.72F);
    }

    private static void tickHellfireOrbs() {
        Iterator<PendingHellfireOrb> iterator = HELLFIRE_ORBS.iterator();
        while (iterator.hasNext()) {
            PendingHellfireOrb orb = iterator.next();
            ServerLevel level = orb.level;
            long now = level.getGameTime();
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
            level.sendParticles(orb.charged ? ParticleTypes.WITCH : ParticleTypes.FLAME, to.x, to.y, to.z, orb.charged ? 30 : 18, 0.42, 0.42, 0.42, 0.025);
            level.sendParticles(orb.charged ? ParticleTypes.SCULK_SOUL : ParticleTypes.LAVA, to.x, to.y, to.z, orb.charged ? 12 : 3, 0.24, 0.24, 0.24, 0.0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z, orb.charged ? 7 : 3, 0.20, 0.20, 0.20, 0.015);
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
            direct.hurtServer(seed.level, owner == null ? seed.level.damageSources().generic() : seed.level.damageSources().playerAttack(owner), AncientChargeSystem.damage(5.0F + seed.stage * 1.4F, seed.charged));
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
            double radius = AncientChargeSystem.radius(4.5 + trap.stage * 0.4, trap.charged);
            for (LivingEntity target : trap.level.getEntitiesOfClass(LivingEntity.class, new AABB(trap.center, trap.center).inflate(radius, 2.5, radius))) {
                if (owner != null && (target == owner || protectedAlly(owner, target))) continue;
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 5, false, true, true));
                if (now % 20L == 0L) target.hurtServer(trap.level, owner == null ? trap.level.damageSources().generic() : trap.level.damageSources().playerAttack(owner), AncientChargeSystem.damage(2.0F + trap.stage * 0.6F, trap.charged));
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
                        target.heal(tree.charged ? 3.0F + tree.stage * 0.7F : 1.0F + tree.stage * 0.4F);
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
        int halfWidth = (wave.charged ? 6 : 4) + wave.stage / 2;
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
            int craterRadius = (i == 0 ? 6 : 5) + stage / 2 + (charged ? 1 : 0);
            float damage = AncientChargeSystem.damage((i == 0 ? 28.0F : 23.0F) + stage * 3.5F, charged);
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
            int craterRadius = 5 + stage / 2 + (charged ? 1 : 0);
            float damage = AncientChargeSystem.damage(23.0F + stage * 3.5F, charged);
            METEORS.add(new PendingMeteor(level, player.getUUID(), startPosition, impact, spawnTick, impactTick, craterRadius, damage, charged));
        }
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
                Vec3 position = new Vec3(
                    meteor.start.x + (target.x - meteor.start.x) * eased,
                    meteor.start.y + (target.y - meteor.start.y) * eased,
                    meteor.start.z + (target.z - meteor.start.z) * eased
                );

                placeMeteorVisual(meteor, position);
                level.sendParticles(meteor.charged ? ParticleTypes.WITCH : ParticleTypes.FLAME, position.x, position.y, position.z, meteor.charged ? 25 : 13, 0.55, 0.55, 0.55, 0.05);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, position.x, position.y + 0.4, position.z, meteor.charged ? 14 : 8, 0.7, 0.7, 0.7, 0.035);
                level.sendParticles(meteor.charged ? ParticleTypes.SCULK_SOUL : ParticleTypes.LAVA, position.x, position.y, position.z, meteor.charged ? 9 : 3, 0.35, 0.35, 0.35, 0.0);

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
        BlockPos[] shape = {
            center,
            center.east(), center.west(), center.north(), center.south(),
            center.above(), center.below()
        };
        for (int i = 0; i < shape.length; i++) {
            BlockPos pos = shape[i];
            if (!meteor.level.getBlockState(pos).isAir()) continue;
            BlockState visual = meteor.charged && (i == 0 || i % 3 == 0)
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
        LAST_FLIGHT_POSITION.clear();
        AncientChargeSystem.clearPendingBeams();
    }

    private static void impactMeteor(PendingMeteor meteor) {
        clearMeteorVisual(meteor);
        ServerLevel level = meteor.level;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(meteor.owner);
        Vec3 impact = meteor.impact;
        int radius = meteor.radius;

        level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y + 0.6, impact.z, 14, 1.9, 1.2, 1.9, 0.08);
        level.sendParticles(meteor.charged ? ParticleTypes.WITCH : ParticleTypes.FLAME, impact.x, impact.y + 0.6, impact.z, meteor.charged ? 220 : 130, 3.2, 1.8, 3.2, 0.16);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, impact.x, impact.y + 1.0, impact.z, meteor.charged ? 85 : 55, 2.7, 2.1, 2.7, 0.08);
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
                push = push.normalize().scale(meteor.charged ? 2.65 : 2.05);
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

    private static String formatSeconds(int ticks) {
        return String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0);
    }


    private static final class PendingHellfireOrb {
        private final ServerLevel level;
        private final UUID owner;
        private Vec3 position;
        private final Vec3 velocity;
        private final long expireTick;
        private final int stage;
        private final boolean charged;
        private final boolean comboPrimer;
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
        private final UUID owner;
        private Vec3 position;
        private final Vec3 velocity;
        private final long expireTick;
        private final int stage;
        private final boolean charged;
        private final boolean comboForest;
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
        private final UUID owner;
        private final Vec3 start;
        private final Vec3 direction;
        private final long startTick;
        private final int maxSteps;
        private final int stage;
        private final boolean charged;
        private int nextStep;
        private final java.util.Set<UUID> hitTargets = new java.util.HashSet<>();
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
        private final UUID owner;
        private final Vec3 start;
        private final Vec3 impact;
        private final long spawnTick;
        private final long impactTick;
        private final int radius;
        private final float damage;
        private final boolean charged;
        private final List<BlockPos> visualBlocks = new ArrayList<>();
        private BlockPos visualCenter;

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
}
