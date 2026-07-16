package com.yagiz.skinpowers;

import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public final class SkinPowersCommands {
    private SkinPowersCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            Commands.literal("skingucu")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                .then(Commands.literal("reset")
                    .then(Commands.argument("oyuncu", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "oyuncu");
                            PlayerDataStore.reset(target.getUUID());
                            PlayerDataStore.save();
                            ServerNetworking.sync(target);
                            context.getSource().sendSuccess(
                                () -> Component.literal(target.getScoreboardName() + " için Skin Powers kaydı sıfırlandı."),
                                true
                            );
                            return 1;
                        })))
                .then(Commands.literal("meteor")
                    .then(Commands.literal("blokhasari")
                        .then(Commands.argument("durum", BoolArgumentType.bool())
                            .executes(context -> {
                                boolean enabled = BoolArgumentType.getBool(context, "durum");
                                PlayerDataStore.config().setMeteorBlockDamage(enabled);
                                PlayerDataStore.markDirty();
                                PlayerDataStore.save();
                                context.getSource().sendSuccess(
                                    () -> Component.literal("Meteor blok hasarı: " + (enabled ? "AÇIK" : "KAPALI")),
                                    true
                                );
                                return 1;
                            }))))
        ));
    }
}
