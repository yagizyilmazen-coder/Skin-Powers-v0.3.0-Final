package com.yagiz.skinpowers;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Eşya kaybı ve gerçek ölüm olmadan yapılan güvenli sınıf düelloları.
 * İlk sürümde düello alanı oyuncuların başlangıç noktalarının çevresidir.
 */
public final class DuelSystem {
    private static final long CHALLENGE_TICKS = 600L;
    private static final long DUEL_LIMIT_TICKS = 6000L;
    private static final double MAX_DISTANCE_SQR = 96.0 * 96.0;

    private static final Map<UUID, Challenge> CHALLENGES_BY_TARGET = new HashMap<>();
    private static final Map<UUID, Duel> ACTIVE_BY_PLAYER = new HashMap<>();

    private DuelSystem() {}

    public static boolean challenge(ServerPlayer challenger, ServerPlayer target) {
        if (challenger == target) {
            challenger.sendSystemMessage(Component.literal("Kendinle düello yapamazsın."));
            return false;
        }
        if (challenger.level() != target.level()) {
            challenger.sendSystemMessage(Component.literal("Düello için aynı boyutta olmalısınız."));
            return false;
        }
        if (isInDuel(challenger.getUUID()) || isInDuel(target.getUUID())) {
            challenger.sendSystemMessage(Component.literal("Oyunculardan biri zaten düelloda."));
            return false;
        }
        long now = challenger.level().getGameTime();
        CHALLENGES_BY_TARGET.put(target.getUUID(), new Challenge(challenger.getUUID(), target.getUUID(), now + CHALLENGE_TICKS));
        challenger.sendSystemMessage(Component.literal(target.getScoreboardName() + " oyuncusuna düello isteği gönderildi."));
        target.sendSystemMessage(Component.literal(challenger.getScoreboardName() + " sana düello isteği gönderdi. /skinpower duello kabul"));
        return true;
    }

    public static boolean accept(ServerPlayer target) {
        Challenge challenge = CHALLENGES_BY_TARGET.remove(target.getUUID());
        long now = target.level().getGameTime();
        if (challenge == null || challenge.expiresAt <= now) {
            target.sendSystemMessage(Component.literal("Bekleyen geçerli bir düello isteğin yok."));
            return false;
        }
        ServerPlayer challenger = target.level().getServer().getPlayerList().getPlayer(challenge.challenger);
        if (challenger == null || challenger.level() != target.level()) {
            target.sendSystemMessage(Component.literal("Düello isteğini gönderen oyuncu artık uygun değil."));
            return false;
        }
        if (isInDuel(challenger.getUUID()) || isInDuel(target.getUUID())) {
            target.sendSystemMessage(Component.literal("Oyunculardan biri zaten düelloda."));
            return false;
        }
        start(challenger, target, now);
        return true;
    }

    public static boolean decline(ServerPlayer target) {
        Challenge challenge = CHALLENGES_BY_TARGET.remove(target.getUUID());
        if (challenge == null) {
            target.sendSystemMessage(Component.literal("Bekleyen düello isteğin yok."));
            return false;
        }
        ServerPlayer challenger = target.level().getServer().getPlayerList().getPlayer(challenge.challenger);
        if (challenger != null) challenger.sendSystemMessage(Component.literal(target.getScoreboardName() + " düello isteğini reddetti."));
        target.sendSystemMessage(Component.literal("Düello isteği reddedildi."));
        return true;
    }

    public static boolean surrender(ServerPlayer player) {
        Duel duel = ACTIVE_BY_PLAYER.get(player.getUUID());
        if (duel == null) {
            player.sendSystemMessage(Component.literal("Şu anda düelloda değilsin."));
            return false;
        }
        UUID winnerId = duel.other(player.getUUID());
        finish(duel, winnerId, player.getUUID(), "teslim oldu");
        return true;
    }

    private static void start(ServerPlayer first, ServerPlayer second, long now) {
        Duel duel = new Duel(
            first.getUUID(), second.getUUID(),
            (ServerLevel) first.level(), first.position(),
            (ServerLevel) second.level(), second.position(),
            now, now + DUEL_LIMIT_TICKS
        );
        ACTIVE_BY_PLAYER.put(first.getUUID(), duel);
        ACTIVE_BY_PLAYER.put(second.getUUID(), duel);
        prepare(first, now);
        prepare(second, now);
        first.sendSystemMessage(Component.literal("DÜELLO BAŞLADI: " + second.getScoreboardName() + " • Eşyalar düşmez, dışarıdakiler zarar görmez."));
        second.sendSystemMessage(Component.literal("DÜELLO BAŞLADI: " + first.getScoreboardName() + " • Eşyalar düşmez, dışarıdakiler zarar görmez."));
    }

    private static void prepare(ServerPlayer player, long now) {
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setRemainingFireTicks(0);
        player.fallDistance = 0.0F;
        PlayerPowerData data = PlayerDataStore.get(player.getUUID());
        data.resetAllCooldowns(now);
        data.setAwakeningEnergy(0.0F);
        data.finishClassAwakening();
        AncientChargeSystem.clearSilently(player);
        ServerNetworking.sync(player);
    }

    public static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
        Duel victimDuel = ACTIVE_BY_PLAYER.get(victim.getUUID());
        Entity attackingEntity = source.getEntity();
        UUID attackerId = attackingEntity == null ? null : attackingEntity.getUUID();

        if (victimDuel != null) {
            UUID victimId = victim.getUUID();
            UUID expected = victimDuel.other(victimId);
            return expected != null && expected.equals(attackerId);
        }

        if (attackerId != null) {
            Duel attackerDuel = ACTIVE_BY_PLAYER.get(attackerId);
            if (attackerDuel != null) {
                UUID expected = attackerDuel.other(attackerId);
                return expected != null && expected.equals(victim.getUUID());
            }
        }
        return true;
    }

    public static boolean allowDeath(LivingEntity victim, DamageSource source, float amount) {
        if (!(victim instanceof ServerPlayer player)) return true;
        Duel duel = ACTIVE_BY_PLAYER.get(player.getUUID());
        if (duel == null) return true;
        UUID winner = duel.other(player.getUUID());
        finish(duel, winner, player.getUUID(), "kazandı");
        return false;
    }

    /** Güçlerin düello dışındaki hedeflere taşmasını engeller. */
    public static boolean protects(ServerPlayer source, LivingEntity target) {
        Duel sourceDuel = ACTIVE_BY_PLAYER.get(source.getUUID());
        if (sourceDuel != null) {
            UUID opponent = sourceDuel.other(source.getUUID());
            return opponent == null || !opponent.equals(target.getUUID());
        }
        Duel targetDuel = ACTIVE_BY_PLAYER.get(target.getUUID());
        if (targetDuel != null) {
            UUID opponent = targetDuel.other(target.getUUID());
            return opponent == null || !opponent.equals(source.getUUID());
        }
        return false;
    }

    public static BattlePanel panelFor(ServerPlayer player) {
        Duel duel = ACTIVE_BY_PLAYER.get(player.getUUID());
        if (duel == null) return BattlePanel.hidden();
        UUID opponentId = duel.other(player.getUUID());
        if (opponentId == null) return BattlePanel.hidden();
        ServerPlayer opponent = player.level().getServer().getPlayerList().getPlayer(opponentId);
        if (opponent == null) return BattlePanel.hidden();
        PlayerPowerData opponentData = PlayerDataStore.get(opponentId);
        long now = opponent.level().getGameTime();
        float awakening = opponentData.classAwakeningActive(now)
            ? Math.max(0.0F, Math.min(100.0F, (opponentData.classAwakeningUntil() - now) / 4.8F))
            : opponentData.awakeningEnergy();
        String detail = opponentData.classAwakeningActive(now) ? "UYANIŞ AKTİF" : "Sınıf düellosu";
        return new BattlePanel(true, "DÜELLO", opponent.getScoreboardName(), opponentData.powerClass().displayName(),
            opponent.getHealth(), opponent.getMaxHealth(), awakening, detail);
    }

    public static boolean isInDuel(UUID playerId) {
        return ACTIVE_BY_PLAYER.containsKey(playerId);
    }

    public static boolean areOpponents(UUID first, UUID second) {
        Duel duel = ACTIVE_BY_PLAYER.get(first);
        return duel != null && second.equals(duel.other(first));
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        CHALLENGES_BY_TARGET.values().removeIf(challenge -> challenge.expiresAt <= now);

        java.util.HashSet<Duel> processed = new java.util.HashSet<>();
        for (Duel duel : new java.util.ArrayList<>(ACTIVE_BY_PLAYER.values())) {
            if (!processed.add(duel)) continue;
            ServerPlayer first = server.getPlayerList().getPlayer(duel.first);
            ServerPlayer second = server.getPlayerList().getPlayer(duel.second);
            if (first == null || second == null) {
                UUID winner = first == null ? duel.second : duel.first;
                UUID loser = first == null ? duel.first : duel.second;
                finish(duel, winner, loser, "bağlantı koptu");
                continue;
            }
            if (now >= duel.endsAt) {
                finish(duel, null, null, "süre doldu");
                continue;
            }
            if (first.level() != second.level() || first.distanceToSqr(second) > MAX_DISTANCE_SQR) {
                finish(duel, null, null, "oyuncular çok uzaklaştı");
            }
        }
    }

    public static void handleDisconnect(ServerPlayer player) {
        CHALLENGES_BY_TARGET.remove(player.getUUID());
        Iterator<Map.Entry<UUID, Challenge>> iterator = CHALLENGES_BY_TARGET.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().challenger.equals(player.getUUID())) iterator.remove();
        }
        Duel duel = ACTIVE_BY_PLAYER.get(player.getUUID());
        if (duel != null) finish(duel, duel.other(player.getUUID()), player.getUUID(), "bağlantı koptu");
    }

    public static void clearAll() {
        CHALLENGES_BY_TARGET.clear();
        ACTIVE_BY_PLAYER.clear();
    }

    private static void finish(Duel duel, UUID winnerId, UUID loserId, String reason) {
        ACTIVE_BY_PLAYER.remove(duel.first);
        ACTIVE_BY_PLAYER.remove(duel.second);
        MinecraftServer server = duel.firstLevel.getServer();
        ServerPlayer first = server.getPlayerList().getPlayer(duel.first);
        ServerPlayer second = server.getPlayerList().getPlayer(duel.second);
        restore(first, duel.firstLevel, duel.firstStart);
        restore(second, duel.secondLevel, duel.secondStart);

        if (winnerId == null) {
            if (first != null) first.sendSystemMessage(Component.literal("Düello berabere bitti: " + reason + "."));
            if (second != null) second.sendSystemMessage(Component.literal("Düello berabere bitti: " + reason + "."));
        } else {
            ServerPlayer winner = server.getPlayerList().getPlayer(winnerId);
            ServerPlayer loser = loserId == null ? null : server.getPlayerList().getPlayer(loserId);
            if (winner != null) winner.sendSystemMessage(Component.literal("DÜELLOYU KAZANDIN!"));
            if (loser != null) loser.sendSystemMessage(Component.literal("Düello sona erdi: " + reason + "."));
        }
    }

    private static void restore(ServerPlayer player, ServerLevel level, Vec3 position) {
        if (player == null) return;
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setRemainingFireTicks(0);
        player.setPos(position.x, position.y, position.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        PlayerPowerData data = PlayerDataStore.get(player.getUUID());
        data.resetAllCooldowns(level.getGameTime());
        data.setAwakeningEnergy(0.0F);
        data.finishClassAwakening();
        data.setDragonFormUntil(0L);
        data.setDragonScalesUntil(0L);
        if (!player.isCreative() && !player.isSpectator() && player.getAbilities().mayfly) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        ServerNetworking.sync(player);
    }

    private record Challenge(UUID challenger, UUID target, long expiresAt) {}

    private static final class Duel {
        private final UUID first;
        private final UUID second;
        private final ServerLevel firstLevel;
        private final Vec3 firstStart;
        private final ServerLevel secondLevel;
        private final Vec3 secondStart;
        private final long startedAt;
        private final long endsAt;

        private Duel(UUID first, UUID second, ServerLevel firstLevel, Vec3 firstStart,
                     ServerLevel secondLevel, Vec3 secondStart, long startedAt, long endsAt) {
            this.first = first;
            this.second = second;
            this.firstLevel = firstLevel;
            this.firstStart = firstStart;
            this.secondLevel = secondLevel;
            this.secondStart = secondStart;
            this.startedAt = startedAt;
            this.endsAt = endsAt;
        }

        private UUID other(UUID player) {
            if (first.equals(player)) return second;
            if (second.equals(player)) return first;
            return null;
        }
    }
}
