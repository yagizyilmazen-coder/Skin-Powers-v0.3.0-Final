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
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(buildRoot())
        );
    }

    /** Tek komut kökü. Sınıf adları yalnızca "degistir" seçildikten sonra görünür. */
    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("skinpower");

        root.then(Commands.literal("degistir")
            .then(selfClass("warden", PowerClass.WARDEN))
            .then(selfClass("ucus", PowerClass.FLIGHT))
            .then(selfClass("ates", PowerClass.FIRE))
            .then(selfClass("alev", PowerClass.FIRE))
            .then(selfClass("doga", PowerClass.NATURE))
            .then(selfClass("anomali", PowerClass.ANOMALY)));

        root.then(Commands.literal("admin")
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
            .then(Commands.literal("degistir")
                .then(Commands.argument("oyuncu", EntityArgument.player())
                    .then(targetClass("warden", PowerClass.WARDEN))
                    .then(targetClass("ucus", PowerClass.FLIGHT))
                    .then(targetClass("ates", PowerClass.FIRE))
                    .then(targetClass("alev", PowerClass.FIRE))
                    .then(targetClass("doga", PowerClass.NATURE))
                    .then(targetClass("anomali", PowerClass.ANOMALY))))
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
                        .executes(context -> clearCharge(context.getSource(), EntityArgument.getPlayer(context, "oyuncu")))))));

        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> selfClass(String literal, PowerClass powerClass) {
        return Commands.literal(literal).executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            return changeClass(context.getSource(), player, powerClass, false);
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> targetClass(String literal, PowerClass powerClass) {
        return Commands.literal(literal).executes(context ->
            changeClass(context.getSource(), EntityArgument.getPlayer(context, "oyuncu"), powerClass, true)
        );
    }

    private static int changeClass(CommandSourceStack source, ServerPlayer target, PowerClass powerClass, boolean adminChange) {
        PlayerPowerData data = PlayerDataStore.get(target.getUUID());
        if (data.powerClass() == powerClass) {
            source.sendFailure(Component.literal(target.getScoreboardName() + " zaten " + powerClass.displayName() + " sınıfında."));
            return 0;
        }

        AncientChargeSystem.clearSilently(target);
        AnomalySystem.clearPlayer(target);
        data.changeClass(powerClass);
        PlayerDataStore.markDirty();
        PlayerDataStore.save();
        ServerNetworking.sync(target);

        target.sendSystemMessage(Component.literal("Sınıfın değiştirildi: " + powerClass.displayName() + ". Güç seviyeleri yeniden açılmalıdır."));
        if (adminChange) {
            source.sendSuccess(() -> Component.literal(target.getScoreboardName() + " artık " + powerClass.displayName() + " sınıfında."), true);
        }
        return 1;
    }

    private static int resetPlayer(CommandSourceStack source, ServerPlayer target) {
        AnomalySystem.clearPlayer(target);
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
