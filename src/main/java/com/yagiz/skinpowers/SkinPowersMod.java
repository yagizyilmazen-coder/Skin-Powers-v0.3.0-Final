package com.yagiz.skinpowers;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SkinPowersMod implements ModInitializer {
    public static final String MOD_ID = "skinpowers";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ServerNetworking.register();
        SkinPowersCommands.register();

        ServerLifecycleEvents.SERVER_STARTED.register(PlayerDataStore::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PowerSystem.clearAllMeteorVisuals();
            AnomalySystem.clearAll();
            MoonPowerSystem.clearAll();
            PowerCollisionSystem.clearAll();
            WorldEventSystem.clearAll();
            ExpansionPowerSystem.clearAll();
            IcePowerSystem.clearAll();
            ClassEnchantmentSystem.clearAll();
            PlayerDataStore.save();
        });
        ServerTickEvents.END_SERVER_TICK.register(PowerSystem::tickServer);
        ServerTickEvents.END_SERVER_TICK.register(ClassEnchantmentSystem::tickServer);
        AttackEntityCallback.EVENT.register(PowerSystem::onAttackEntity);
        AttackEntityCallback.EVENT.register(ClassEnchantmentSystem::onAttackEntity);
        // Derinlik Pususu, diğer hasar/uyanış sistemlerinden önce bütün hasarı keser.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(PowerSystem::allowWardenAmbushDamage);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(PowerSystem::allowDragonScalesDamage);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(ExpansionPowerSystem::allowDamage);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(MoonPowerSystem::allowDamage);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(ClassEnchantmentSystem::allowDamage);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(AnomalySystem::allowDamage);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(AwakeningSystem::allowDamage);
        ServerLivingEntityEvents.ALLOW_DEATH.register(ClassEnchantmentSystem::allowDeath);
        ServerLivingEntityEvents.ALLOW_DEATH.register(AnomalySystem::allowDeath);
        // Tick tabanlı isAlive kontrolü hızlı yeniden doğmada yetersiz kalır.
        // Oyuncu öldüğü kesinleştiği anda bağlı kum/metal görsellerini sil.
        ServerLivingEntityEvents.AFTER_DEATH.register(ExpansionPowerSystem::afterDeath);
        ServerLivingEntityEvents.AFTER_DEATH.register(IcePowerSystem::afterDeath);
        ServerLivingEntityEvents.AFTER_DEATH.register(MoonPowerSystem::afterDeath);
        ServerLivingEntityEvents.AFTER_DEATH.register(AnomalySystem::afterDeath);

        LOGGER.info("Skin Powers 1.3.0 Ay ve Anomali 2.0 yüklendi.");
    }
}
