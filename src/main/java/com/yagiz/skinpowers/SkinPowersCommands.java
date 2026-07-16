package com.yagiz.skinpowers;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public final class SkinPowersCommands {
    private SkinPowersCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(buildTurkishRoot());
            dispatcher.register(buildEnglishRoot());
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildTurkishRoot() {
        return Commands.literal("skingucu")
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
            .then(Commands.literal("reset")
                .then(Commands.argument("oyuncu", EntityArgument.player())
                    .executes(context -> resetPlayer(context.getSource(), EntityArgument.getPlayer(context, "oyuncu")))))
            .then(Commands.literal("meteor")
                .then(Commands.literal("blokhasari")
                    .then(Commands.argument("durum", BoolArgumentType.bool())
                        .executes(context -> setMeteorDamage(context.getSource(), BoolArgumentType.getBool(context, "durum"))))))
            .then(Commands.literal("sarj")
                .then(Commands.literal("ver")
                    .then(Commands.argument("oyuncu", EntityArgument.player())
                        .then(Commands.argument("saniye", IntegerArgumentType.integer(1))
                            .executes(context -> giveCharge(
                                context.getSource(),
                                EntityArgument.getPlayer(context, "oyuncu"),
                                IntegerArgumentType.getInteger(context, "saniye")
                            )))))
                .then(Commands.literal("temizle")
                    .then(Commands.argument("oyuncu", EntityArgument.player())
                        .executes(context -> clearCharge(context.getSource(), EntityArgument.getPlayer(context, "oyuncu"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildEnglishRoot() {
        return Commands.literal("skinpowers")
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
            .then(Commands.literal("charge")
                .then(Commands.literal("give")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                            .executes(context -> giveCharge(
                                context.getSource(),
                                EntityArgument.getPlayer(context, "player"),
                                IntegerArgumentType.getInteger(context, "seconds")
                            )))))
                .then(Commands.literal("clear")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> clearCharge(context.getSource(), EntityArgument.getPlayer(context, "player"))))));
    }

    private static int resetPlayer(CommandSourceStack source, ServerPlayer target) {
        PlayerDataStore.reset(target.getUUID());
        PlayerDataStore.save();
        ServerNetworking.sync(target);
        source.sendSuccess(
            () -> Component.literal(target.getScoreboardName() + " için Skin Powers kaydı sıfırlandı."),
            true
        );
        return 1;
    }

    private static int setMeteorDamage(CommandSourceStack source, boolean enabled) {
        PlayerDataStore.config().setMeteorBlockDamage(enabled);
        PlayerDataStore.markDirty();
        PlayerDataStore.save();
        source.sendSuccess(
            () -> Component.literal("Meteor blok hasarı: " + (enabled ? "AÇIK" : "KAPALI")),
            true
        );
        return 1;
    }

    private static int giveCharge(CommandSourceStack source, ServerPlayer target, int requestedSeconds) {
        int seconds = Math.max(1, Math.min(20, requestedSeconds));
        AncientChargeSystem.grant(target, seconds * 20, true);
        source.sendSuccess(
            () -> Component.literal(target.getScoreboardName() + " için Antik Şehir Şarjı: " + seconds + " saniye."),
            true
        );
        return 1;
    }

    private static int clearCharge(CommandSourceStack source, ServerPlayer target) {
        AncientChargeSystem.clear(target);
        source.sendSuccess(
            () -> Component.literal(target.getScoreboardName() + " için Antik Şehir etkileri temizlendi."),
            true
        );
        return 1;
    }
}
