package com.yagiz.skinpowers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Yakın zamanda kullanılan büyük güçler birbirine yakınsa görünür bir güç çarpışması oluşturur. */
public final class PowerCollisionSystem {
    private static final long CLASH_WINDOW = 14L;
    private static final double CLASH_RANGE_SQR = 24.0 * 24.0;
    private static final Map<UUID, CastPulse> RECENT = new HashMap<>();

    private PowerCollisionSystem() {}

    public static void registerCast(ServerPlayer caster, PlayerPowerData data, int power, long now, boolean charged) {
        if (power < 2) return;
        ServerLevel level = (ServerLevel) caster.level();
        int strength = power * 12 + data.masteryStage(power) * 4 + (charged ? 20 : 0);
        CastPulse current = new CastPulse(caster.getUUID(), data.powerClass(), power, level, caster.position(), now, strength);

        Iterator<Map.Entry<UUID, CastPulse>> iterator = RECENT.entrySet().iterator();
        while (iterator.hasNext()) {
            CastPulse other = iterator.next().getValue();
            if (other.caster.equals(caster.getUUID()) || other.level != level || now - other.tick > CLASH_WINDOW) continue;
            ServerPlayer opponent = level.getServer().getPlayerList().getPlayer(other.caster);
            if (opponent == null || opponent.distanceToSqr(caster) > CLASH_RANGE_SQR || PowerSystem.isProtectedAlly(caster, opponent)) continue;
            clash(caster, current, opponent, other);
            iterator.remove();
            return;
        }
        RECENT.put(caster.getUUID(), current);
    }

    private static void clash(ServerPlayer currentPlayer, CastPulse current, ServerPlayer otherPlayer, CastPulse other) {
        ServerLevel level = current.level;
        Vec3 center = currentPlayer.position().add(otherPlayer.position()).scale(0.5).add(0.0, 1.0, 0.0);
        int difference = current.strength - other.strength;
        boolean draw = Math.abs(difference) <= 5;

        if (draw) {
            PowerSystem.cancelActiveOffense(current.caster);
            PowerSystem.cancelActiveOffense(other.caster);
            pushApart(currentPlayer, otherPlayer, 1.4);
            currentPlayer.hurtServer(level, level.damageSources().playerAttack(otherPlayer), 5.0F);
            otherPlayer.hurtServer(level, level.damageSources().playerAttack(currentPlayer), 5.0F);
            currentPlayer.sendSystemMessage(Component.literal("GÜÇ ÇARPIŞMASI: İki saldırı da parçalandı!"));
            otherPlayer.sendSystemMessage(Component.literal("GÜÇ ÇARPIŞMASI: İki saldırı da parçalandı!"));
        } else {
            ServerPlayer winner = difference > 0 ? currentPlayer : otherPlayer;
            ServerPlayer loser = difference > 0 ? otherPlayer : currentPlayer;
            UUID loserId = difference > 0 ? other.caster : current.caster;
            PowerSystem.cancelActiveOffense(loserId);
            loser.hurtServer(level, level.damageSources().playerAttack(winner), Math.min(12.0F, 4.0F + Math.abs(difference) * 0.15F));
            Vec3 push = loser.position().subtract(winner.position());
            if (push.lengthSqr() > 0.001) {
                push = push.normalize().scale(1.5);
                loser.push(push.x, 0.55, push.z);
                loser.hurtMarked = true;
            }
            winner.sendSystemMessage(Component.literal("GÜÇ ÇARPIŞMASINI KAZANDIN!"));
            loser.sendSystemMessage(Component.literal("Rakibin gücü saldırını parçaladı."));
        }

        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 8, 1.2, 1.0, 1.2, 0.0);
        level.sendParticles(ParticleTypes.WITCH, center.x, center.y, center.z, 90, 2.2, 1.5, 2.2, 0.15);
        level.sendParticles(ParticleTypes.SCULK_SOUL, center.x, center.y, center.z, 45, 1.6, 1.1, 1.6, 0.08);
        level.playSound(null, BlockPos.containing(center), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5F, 0.70F);
        ServerNetworking.sendScreenShake(level, center, 30.0, 1.2F, 12);
    }

    private static void pushApart(ServerPlayer first, ServerPlayer second, double strength) {
        Vec3 direction = first.position().subtract(second.position());
        if (direction.lengthSqr() < 0.001) direction = new Vec3(1.0, 0.0, 0.0);
        direction = new Vec3(direction.x, 0.0, direction.z).normalize().scale(strength);
        first.push(direction.x, 0.5, direction.z);
        second.push(-direction.x, 0.5, -direction.z);
        first.hurtMarked = true;
        second.hurtMarked = true;
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        RECENT.values().removeIf(pulse -> now - pulse.tick > CLASH_WINDOW);
    }

    public static void clearAll() {
        RECENT.clear();
    }

    private record CastPulse(UUID caster, PowerClass powerClass, int power, ServerLevel level, Vec3 position, long tick, int strength) {}
}
