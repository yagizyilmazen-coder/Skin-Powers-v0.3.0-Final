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
            .then(selfClass("ejderha", PowerClass.FLIGHT))
            .then(selfClass("ates", PowerClass.FIRE))
            .then(selfClass("alev", PowerClass.FIRE))
            .then(selfClass("doga", PowerClass.NATURE))
            .then(selfClass("anomali", PowerClass.ANOMALY)));

        root.then(Commands.literal("duello")
            .then(Commands.literal("kabul").executes(context -> DuelSystem.accept(context.getSource().getPlayerOrException()) ? 1 : 0))
            .then(Commands.literal("reddet").executes(context -> DuelSystem.decline(context.getSource().getPlayerOrException()) ? 1 : 0))
            .then(Commands.literal("bitir").executes(context -> DuelSystem.surrender(context.getSource().getPlayerOrException()) ? 1 : 0))
            .then(Commands.argument("oyuncu", EntityArgument.player()).executes(context ->
                DuelSystem.challenge(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "oyuncu")) ? 1 : 0)));

        root.then(Commands.literal("bot")
            .then(Commands.literal("cagir")
                .then(botClass("warden", PowerClass.WARDEN))
                .then(botClass("ejderha", PowerClass.FLIGHT))
                .then(botClass("ates", PowerClass.FIRE))
                .then(botClass("doga", PowerClass.NATURE))
                .then(botClass("anomali", PowerClass.ANOMALY)))
            .then(Commands.literal("temizle").executes(context ->
                PvpBotSystem.removeOwnerBot(context.getSource().getPlayerOrException(), true) ? 1 : 0))
            .then(Commands.literal("durdur").executes(context ->
                PvpBotSystem.pause(context.getSource().getPlayerOrException(), true) ? 1 : 0))
            .then(Commands.literal("devam").executes(context ->
                PvpBotSystem.pause(context.getSource().getPlayerOrException(), false) ? 1 : 0)));

        root.then(Commands.literal("olay")
            .executes(context -> {
                context.getSource().sendSuccess(() -> Component.literal(WorldEventSystem.status()), false);
                return 1;
            }));

        // Test/yönetim komutu: sınıf, seviye ve cooldown şartını değiştirmeden saldırıyı doğrudan çağırır.
        // Yayınlanan sunucularda kötüye kullanılmaması için yalnızca moderatör yetkisine açıktır.
        root.then(Commands.literal("trigger")
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
            .then(triggerLiteral("earthquake"))
            .then(triggerLiteral("earthquake_charged"))
            .then(triggerLiteral("sonic"))
            .then(triggerLiteral("sonic_charged"))
            .then(triggerLiteral("sonic_fault"))
            .then(triggerLiteral("sonic_fault_charged"))
            .then(triggerLiteral("sky_spear"))
            .then(triggerLiteral("sky_spear_charged"))
            .then(triggerLiteral("sky_bomb"))
            .then(triggerLiteral("sky_bomb_charged"))
            .then(triggerLiteral("sky_cataclysm"))
            .then(triggerLiteral("sky_cataclysm_charged"))
            .then(triggerLiteral("dragon_dash"))
            .then(triggerLiteral("dragon_dash_charged"))
            .then(triggerLiteral("dragon_breath"))
            .then(triggerLiteral("dragon_breath_charged"))
            .then(triggerLiteral("dragon_scales"))
            .then(triggerLiteral("dragon_claw"))
            .then(triggerLiteral("dragon_roar"))
            .then(triggerLiteral("dragon_form"))
            .then(triggerLiteral("fire_ring"))
            .then(triggerLiteral("fire_ring_charged"))
            .then(triggerLiteral("hellfire"))
            .then(triggerLiteral("hellfire_charged"))
            .then(triggerLiteral("meteor"))
            .then(triggerLiteral("meteor_charged"))
            .then(triggerLiteral("nature_seed"))
            .then(triggerLiteral("nature_seed_charged"))
            .then(triggerLiteral("vine_trap"))
            .then(triggerLiteral("vine_trap_charged"))
            .then(triggerLiteral("life_tree"))
            .then(triggerLiteral("life_tree_charged"))
            .then(triggerLiteral("root_wave"))
            .then(triggerLiteral("root_wave_charged"))
            .then(triggerLiteral("thorn_forest"))
            .then(triggerLiteral("thorn_forest_charged"))
            .then(triggerLiteral("broken_step"))
            .then(triggerLiteral("broken_step_charged"))
            .then(triggerLiteral("reverse"))
            .then(triggerLiteral("reverse_charged"))
            .then(triggerLiteral("void_out"))
            .then(triggerLiteral("void_out_charged"))
            .then(triggerLiteral("reality_404")));

        root.then(Commands.literal("admin")
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
            .then(Commands.literal("degistir")
                .then(Commands.argument("oyuncu", EntityArgument.player())
                    .then(targetClass("warden", PowerClass.WARDEN))
                    .then(targetClass("ejderha", PowerClass.FLIGHT))
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
            .then(Commands.literal("olay")
                .then(Commands.literal("baslat")
                    .then(eventLiteral("sculk"))
                    .then(eventLiteral("meteor"))
                    .then(eventLiteral("gok"))
                    .then(eventLiteral("doga"))
                    .then(eventLiteral("anomali"))
                    .then(eventLiteral("rastgele")))
                .then(Commands.literal("durdur").executes(context -> {
                    boolean stopped = WorldEventSystem.stop(context.getSource().getServer());
                    if (!stopped) context.getSource().sendFailure(Component.literal("Aktif dünya olayı yok."));
                    return stopped ? 1 : 0;
                })))
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

    private static LiteralArgumentBuilder<CommandSourceStack> botClass(String literal, PowerClass powerClass) {
        return Commands.literal(literal)
            .then(botDifficulty("kolay", powerClass, PvpBotSystem.BotDifficulty.EASY))
            .then(botDifficulty("normal", powerClass, PvpBotSystem.BotDifficulty.NORMAL))
            .then(botDifficulty("zor", powerClass, PvpBotSystem.BotDifficulty.HARD))
            .then(botDifficulty("kabus", powerClass, PvpBotSystem.BotDifficulty.NIGHTMARE))
            .executes(context -> PvpBotSystem.spawn(
                context.getSource().getPlayerOrException(), powerClass, PvpBotSystem.BotDifficulty.NORMAL
            ) ? 1 : 0);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> botDifficulty(
        String literal, PowerClass powerClass, PvpBotSystem.BotDifficulty difficulty
    ) {
        return Commands.literal(literal).executes(context -> PvpBotSystem.spawn(
            context.getSource().getPlayerOrException(), powerClass, difficulty
        ) ? 1 : 0);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> triggerLiteral(String literal) {
        return Commands.literal(literal).executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            boolean triggered = PowerSystem.triggerAttack(player, literal);
            if (!triggered) {
                context.getSource().sendFailure(Component.literal("Atak çağrılamadı: " + literal));
                return 0;
            }
            context.getSource().sendSuccess(() -> Component.literal("Atak çağrıldı: " + literal), false);
            return 1;
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> eventLiteral(String literal) {
        return Commands.literal(literal).executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            boolean started = WorldEventSystem.startNearPlayer(player, literal);
            if (!started) context.getSource().sendFailure(Component.literal("Başka bir dünya olayı zaten aktif."));
            return started ? 1 : 0;
        });
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
