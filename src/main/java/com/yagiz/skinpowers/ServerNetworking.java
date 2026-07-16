package com.yagiz.skinpowers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yagiz.skinpowers.network.ClientCommandPayload;
import com.yagiz.skinpowers.network.ServerStatePayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public final class ServerNetworking {
    private static final Gson GSON = new GsonBuilder().create();

    private ServerNetworking() {}

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(ClientCommandPayload.TYPE, ClientCommandPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerStatePayload.TYPE, ServerStatePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ClientCommandPayload.TYPE, (payload, context) -> {
            handle(context.player(), payload.command());
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player));
    }

    private static void handle(ServerPlayer player, String rawCommand) {
        if (rawCommand == null || rawCommand.length() > 256) return;
        String command = rawCommand.trim().toUpperCase(Locale.ROOT);
        PlayerPowerData data = PlayerDataStore.get(player.getUUID());

        if (command.startsWith("CHOOSE:")) {
            PowerClass selected = PowerClass.safeValueOf(command.substring("CHOOSE:".length()));
            if (data.powerClass() == PowerClass.NONE && selected != PowerClass.NONE) {
                data.chooseClass(selected);
                PlayerDataStore.markDirty();
                player.sendSystemMessage(Component.literal("Sınıfın seçildi: " + selected.displayName()));
            }
        } else if (command.equals("UNLOCK")) {
            unlockNext(player, data);
        } else if (command.startsWith("SELECT:")) {
            try {
                int selectedLevel = Integer.parseInt(command.substring("SELECT:".length()));
                if (selectedLevel >= 1 && selectedLevel <= data.unlockedLevel()) {
                    data.setSelectedPower(selectedLevel);
                    PlayerDataStore.markDirty();
                }
            } catch (NumberFormatException ignored) {
                return;
            }
        } else if (command.equals("NEXT")) {
            data.selectRelative(1);
        } else if (command.equals("PREV")) {
            data.selectRelative(-1);
        } else if (command.equals("ACTIVE")) {
            PowerSystem.useSelectedPower(player, data);
        } else if (command.equals("TOGGLE")) {
            PowerSystem.toggleSelectedFeature(player, data);
        } else if (command.equals("LAUNCH")) {
            PowerSystem.tryRocketlessLaunch(player, data);
        } else {
            return;
        }

        sync(player);
    }

    private static void unlockNext(ServerPlayer player, PlayerPowerData data) {
        if (data.powerClass() == PowerClass.NONE) {
            player.sendSystemMessage(Component.literal("Önce bir sınıf seçmelisin."));
            return;
        }
        if (data.unlockedLevel() >= 5) {
            player.sendSystemMessage(Component.literal("Bütün seviyeler açık."));
            return;
        }
        int nextLevel = data.unlockedLevel() + 1;
        int cost = PowerCatalog.xpCostForLevel(nextLevel);
        if (player.experienceLevel < cost && !player.isCreative()) {
            player.sendSystemMessage(Component.literal("Seviye " + nextLevel + " için " + cost + " XP seviyesi gerekiyor."));
            return;
        }
        if (!player.isCreative()) player.giveExperienceLevels(-cost);
        data.unlockNextLevel();
        data.setSelectedPower(nextLevel);
        PlayerDataStore.markDirty();
        player.sendSystemMessage(Component.literal("Açıldı: " + PowerCatalog.powerName(data.powerClass(), nextLevel)));
    }

    public static void sync(ServerPlayer player) {
        PlayerPowerData data = PlayerDataStore.get(player.getUUID());
        long gameTime = player.level().getGameTime();
        State state = new State(
            data.powerClass().name(),
            data.unlockedLevel(),
            data.selectedPower(),
            data.cooldownRemaining(data.selectedPower(), gameTime),
            data.passiveEnabled(),
            data.visionEnabled(),
            (int) Math.max(0L, data.temporaryElytraUntil() - gameTime),
            (int) Math.max(0L, data.wardenHuntUntil() - gameTime),
            data.masteryCopy(),
            player.experienceLevel,
            PowerCatalog.powerName(data.powerClass(), data.selectedPower())
        );
        ServerPlayNetworking.send(player, new ServerStatePayload(GSON.toJson(state)));
    }

    public record State(
        String powerClass,
        int unlockedLevel,
        int selectedPower,
        int cooldownTicks,
        boolean passiveEnabled,
        boolean visionEnabled,
        int temporaryElytraTicks,
        int wardenHuntTicks,
        int[] masteryUses,
        int xpLevel,
        String powerName
    ) {}
}
