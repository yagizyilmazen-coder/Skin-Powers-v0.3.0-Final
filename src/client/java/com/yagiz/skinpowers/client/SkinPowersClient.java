package com.yagiz.skinpowers.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yagiz.skinpowers.SkinPowersMod;
import com.yagiz.skinpowers.network.ClientCommandPayload;
import com.yagiz.skinpowers.network.ServerStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class SkinPowersClient implements ClientModInitializer {
    private final KeyMapping.Category category = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath(SkinPowersMod.MOD_ID, "controls")
    );

    private final KeyMapping activeKey = register("key.skinpowers.active", GLFW.GLFW_KEY_R);
    private final KeyMapping toggleKey = register("key.skinpowers.toggle", GLFW.GLFW_KEY_Y);
    private final KeyMapping previousKey = register("key.skinpowers.previous", GLFW.GLFW_KEY_LEFT);
    private final KeyMapping nextKey = register("key.skinpowers.next", GLFW.GLFW_KEY_RIGHT);
    private final KeyMapping menuKey = register("key.skinpowers.menu", GLFW.GLFW_KEY_O);

    private boolean selectionScreenOpened;
    private boolean previousJumpDown;
    private long lastJumpPress;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ServerStatePayload.TYPE, (payload, context) ->
            context.client().execute(() -> ClientState.updateFromJson(payload.stateJson()))
        );

        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath(SkinPowersMod.MOD_ID, "power_hud"),
            HudOverlay::extract
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientState.clientTick();
            if (client.player == null || client.level == null) return;

            if (ClientState.receivedState() && ClientState.powerClass() != com.yagiz.skinpowers.PowerClass.NONE) {
                // Yönetici oyuncuyu sıfırlarsa seçim ekranının yeniden otomatik açılabilmesi için kilidi bırak.
                selectionScreenOpened = false;
            }
            if (ClientState.receivedState() && ClientState.powerClass() == com.yagiz.skinpowers.PowerClass.NONE
                && !selectionScreenOpened && !isScreenOpen(client)) {
                selectionScreenOpened = true;
                client.setScreen(new SkinSelectionScreen());
            }

            while (activeKey.consumeClick()) send("ACTIVE");
            while (toggleKey.consumeClick()) send("TOGGLE");
            while (previousKey.consumeClick()) send("PREV");
            while (nextKey.consumeClick()) send("NEXT");
            while (menuKey.consumeClick()) {
                if (ClientState.powerClass() == com.yagiz.skinpowers.PowerClass.NONE) {
                    client.setScreen(new SkinSelectionScreen());
                } else {
                    client.setScreen(new PowerMenuScreen());
                }
            }

            boolean jumpDown = client.options.keyJump.isDown();
            if (jumpDown && !previousJumpDown) {
                long now = System.currentTimeMillis();
                if (now - lastJumpPress <= 320L) send("LAUNCH");
                lastJumpPress = now;
            }
            previousJumpDown = jumpDown;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientState.reset();
            selectionScreenOpened = false;
            previousJumpDown = false;
            lastJumpPress = 0L;
        });
    }


    private static boolean isScreenOpen(Object client) {
        // 26.1.x sürümlerinde ekran alanının adı dağıtıma göre currentScreen
        // veya screen olabiliyor. Doğrudan alan adına bağlanmadan ikisini de destekle.
        for (String fieldName : new String[]{"currentScreen", "screen"}) {
            try {
                java.lang.reflect.Field field = client.getClass().getField(fieldName);
                return field.get(client) != null;
            } catch (ReflectiveOperationException ignored) {
                try {
                    java.lang.reflect.Field field = client.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.get(client) != null;
                } catch (ReflectiveOperationException ignoredAgain) {
                    // Sıradaki ad denenir.
                }
            }
        }

        for (String methodName : new String[]{"currentScreen", "screen", "getScreen"}) {
            try {
                Object screen = client.getClass().getMethod(methodName).invoke(client);
                return screen != null;
            } catch (ReflectiveOperationException ignored) {
                // Sıradaki ad denenir.
            }
        }

        // Alan bulunamazsa seçim ekranının bir kez açılmasına izin ver.
        return false;
    }

    private KeyMapping register(String translationKey, int keyCode) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
            translationKey,
            InputConstants.Type.KEYSYM,
            keyCode,
            category
        ));
    }

    private static void send(String command) {
        ClientPlayNetworking.send(new ClientCommandPayload(command));
    }
}
