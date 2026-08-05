package com.yagiz.skinpowers;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;

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
            .then(selfClass("ay", PowerClass.MOON))
            .then(selfClass("anomali", PowerClass.ANOMALY))
            .then(selfClass("manyetik", PowerClass.MAGNETIC))
            .then(selfClass("orumcek", PowerClass.SPIDER))
            .then(selfClass("spider", PowerClass.SPIDER))
            .then(selfClass("spiderman", PowerClass.SPIDER))
            .then(selfClass("buz", PowerClass.ICE)));

        // Dünya olaylarını yalnızca yetkili oyuncular elle başlatabilir.
        root.then(Commands.literal("olay")
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
            .then(worldEventLiteral("sculk"))
            .then(worldEventLiteral("meteor"))
            .then(worldEventLiteral("gok"))
            .then(worldEventLiteral("ay"))
            .then(worldEventLiteral("anomali"))
            .then(worldEventLiteral("rastgele"))
            .then(Commands.literal("durdur")
                .executes(context -> stopWorldEvent(context.getSource())))
            .then(Commands.literal("durum")
                .executes(context -> worldEventStatus(context.getSource()))));

        // Sınıf büyülü kitaplarını test etmek için gerçek büyülü kitap verir.
        // Kitaplar normal örste uygun eşyaya basılır.
        LiteralArgumentBuilder<CommandSourceStack> enchantmentBooks = Commands.literal("kitap");
        for (String enchantmentId : ClassEnchantments.commandEntries().keySet()) {
            enchantmentBooks.then(enchantmentBookLiteral(enchantmentId));
        }
        root.then(Commands.literal("buyu")
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
            .then(enchantmentBooks));

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
            .then(triggerLiteral("moon_crescent"))
            .then(triggerLiteral("moon_crescent_charged"))
            .then(triggerLiteral("moon_step"))
            .then(triggerLiteral("moon_step_charged"))
            .then(triggerLiteral("moon_gravity"))
            .then(triggerLiteral("moon_gravity_charged"))
            .then(triggerLiteral("moon_mirror"))
            .then(triggerLiteral("moon_mirror_charged"))
            .then(triggerLiteral("moon_eclipse"))
            .then(triggerLiteral("moon_eclipse_charged"))
            .then(triggerLiteral("moon_beast"))
            .then(triggerLiteral("moon_beast_charged"))
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
                    .then(targetClass("ay", PowerClass.MOON))
                    .then(targetClass("anomali", PowerClass.ANOMALY))
                    .then(targetClass("manyetik", PowerClass.MAGNETIC))
                    .then(targetClass("orumcek", PowerClass.SPIDER))
                    .then(targetClass("spider", PowerClass.SPIDER))
                    .then(targetClass("spiderman", PowerClass.SPIDER))
                    .then(targetClass("buz", PowerClass.ICE))))
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

    private static LiteralArgumentBuilder<CommandSourceStack> enchantmentBookLiteral(String literal) {
        return Commands.literal(literal).executes(context ->
            giveEnchantmentBook(context.getSource(), literal)
        );
    }

    private static int giveEnchantmentBook(CommandSourceStack source, String enchantmentId) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var key = ClassEnchantments.byCommand(enchantmentId);
        if (key == null) {
            source.sendFailure(Component.literal("Bilinmeyen sınıf büyüsü: " + enchantmentId));
            return 0;
        }
        ItemStack book = ClassEnchantments.createBook(source.getServer().registryAccess(), key);
        player.getInventory().add(book);
        if (!book.isEmpty()) {
            player.drop(book, false, false);
        }
        source.sendSuccess(() -> Component.literal("Büyülü kitap verildi: " + enchantmentId), false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> worldEventLiteral(String literal) {
        return Commands.literal(literal).executes(context ->
            startWorldEvent(context.getSource(), literal)
        );
    }

    private static int startWorldEvent(CommandSourceStack source, String eventName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!WorldEventSystem.startNearPlayer(player, eventName)) {
            source.sendFailure(Component.literal("Dünya olayı başlatılamadı. " + WorldEventSystem.status()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Dünya olayı başlatıldı: " + eventName), true);
        return 1;
    }

    private static int stopWorldEvent(CommandSourceStack source) {
        if (!WorldEventSystem.stop(source.getServer())) {
            source.sendFailure(Component.literal("Durdurulacak aktif dünya olayı yok."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Dünya olayı durduruldu."), true);
        return 1;
    }

    private static int worldEventStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(WorldEventSystem.status()), false);
        return 1;
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
        MoonPowerSystem.clearPlayer(target);
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
        MoonPowerSystem.clearPlayer(target);
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
