package com.yagiz.skinpowers.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yagiz.skinpowers.PowerClass;
import com.yagiz.skinpowers.SkinPowersMod;
import com.yagiz.skinpowers.network.ClientCommandPayload;
import com.yagiz.skinpowers.network.ClientEffectPayload;
import com.yagiz.skinpowers.network.ServerStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;

public final class SkinPowersClient implements ClientModInitializer {
    private final KeyMapping.Category category = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath(SkinPowersMod.MOD_ID, "controls")
    );

    private final KeyMapping activeKey = register("key.skinpowers.active", GLFW.GLFW_KEY_R);
    private final KeyMapping toggleKey = register("key.skinpowers.toggle", GLFW.GLFW_KEY_Y);
    private final KeyMapping previousKey = register("key.skinpowers.previous", GLFW.GLFW_KEY_LEFT);
    private final KeyMapping nextKey = register("key.skinpowers.next", GLFW.GLFW_KEY_RIGHT);
    private final KeyMapping menuKey = register("key.skinpowers.menu", GLFW.GLFW_KEY_O);
    private final KeyMapping comboKey = register("key.skinpowers.combo", GLFW.GLFW_KEY_K);
    private final KeyMapping anomalyHealthKey = register("key.skinpowers.anomaly_health", GLFW.GLFW_KEY_V);
    private final KeyMapping anomalyReturnKey = register("key.skinpowers.anomaly_return", GLFW.GLFW_KEY_X);

    private boolean selectionScreenOpened;
    private boolean previousJumpDown;
    private long lastJumpPress;
    private float appliedShakeYaw;
    private float appliedShakePitch;
    private long shakePhase;
    private boolean previousRotationFieldsResolved;
    private Field previousYawField;
    private Field previousPitchField;

    @Override
    public void onInitializeClient() {
        ClientConfig.load();

        ClientPlayNetworking.registerGlobalReceiver(ServerStatePayload.TYPE, (payload, context) ->
            context.client().execute(() -> ClientState.updateFromJson(payload.stateJson()))
        );
        ClientPlayNetworking.registerGlobalReceiver(ClientEffectPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if ("SHAKE".equalsIgnoreCase(payload.effect())) {
                    ClientState.startShake(payload.strength(), payload.durationTicks());
                }
            })
        );

        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath(SkinPowersMod.MOD_ID, "power_hud"),
            HudOverlay::extract
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            removePreviousShake(client);
            ClientState.clientTick();
            if (client.player == null || client.level == null) return;

            applyScreenShake(client);

            if (ClientState.receivedState() && ClientState.powerClass() != PowerClass.NONE) {
                selectionScreenOpened = false;
            }
            if (ClientState.receivedState() && ClientState.powerClass() == PowerClass.NONE
                && !selectionScreenOpened && !isScreenOpen(client)) {
                selectionScreenOpened = true;
                client.setScreen(new SkinSelectionScreen());
            }

            while (activeKey.consumeClick()) send("ACTIVE");
            while (toggleKey.consumeClick()) send("TOGGLE");
            while (previousKey.consumeClick()) send("PREV");
            while (nextKey.consumeClick()) send("NEXT");
            while (comboKey.consumeClick()) send("COMBO_TOGGLE");
            while (anomalyHealthKey.consumeClick()) send("ANOMALY_HEALTH");
            while (anomalyReturnKey.consumeClick()) send("ANOMALY_RETURN");
            while (menuKey.consumeClick()) {
                Object currentScreen = getCurrentScreen(client);
                if (currentScreen instanceof PowerMenuScreen) {
                    client.setScreen(null);
                } else if (ClientState.powerClass() == PowerClass.NONE) {
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
            removePreviousShake(client);
            ClientState.reset();
            selectionScreenOpened = false;
            previousJumpDown = false;
            lastJumpPress = 0L;
            shakePhase = 0L;
        });
    }

    private void applyScreenShake(Minecraft client) {
        if (client.player == null || ClientState.shakeTicks() <= 0) return;
        int setting = ClientConfig.get().screenShakePercent();
        if (setting <= 0) return;

        // Yalnızca çok küçük bir açı vermek Minecraft kamera interpolasyonunda fark edilmiyordu.
        // Mevcut ve önceki dönüş değerlerini birlikte oynatarak sarsıntı ilk şahıs ve üçüncü şahısta görünür olur.
        float strength = Math.max(0.35F, ClientState.shakeStrength()) * setting / 100.0F;
        float fade = Math.min(1.0F, ClientState.shakeTicks() / 7.0F);
        double phase = ++shakePhase * 2.17;
        appliedShakeYaw = (float) ((Math.sin(phase) * 2.05 + Math.sin(phase * 0.47) * 0.80) * strength * fade);
        appliedShakePitch = (float) ((Math.cos(phase * 1.31) * 1.35 + Math.sin(phase * 0.73) * 0.55) * strength * fade);
        client.player.setYRot(client.player.getYRot() + appliedShakeYaw);
        client.player.setXRot(clampPitch(client.player.getXRot() + appliedShakePitch));
        adjustPreviousRotation(client.player, appliedShakeYaw, appliedShakePitch);
    }

    private void removePreviousShake(Minecraft client) {
        if (client.player != null && (appliedShakeYaw != 0.0F || appliedShakePitch != 0.0F)) {
            client.player.setYRot(client.player.getYRot() - appliedShakeYaw);
            client.player.setXRot(clampPitch(client.player.getXRot() - appliedShakePitch));
            adjustPreviousRotation(client.player, -appliedShakeYaw, -appliedShakePitch);
        }
        appliedShakeYaw = 0.0F;
        appliedShakePitch = 0.0F;
    }

    private void adjustPreviousRotation(Object player, float yawDelta, float pitchDelta) {
        resolvePreviousRotationFields(player.getClass());
        try {
            if (previousYawField != null) previousYawField.setFloat(player, previousYawField.getFloat(player) + yawDelta);
            if (previousPitchField != null) {
                previousPitchField.setFloat(player, clampPitch(previousPitchField.getFloat(player) + pitchDelta));
            }
        } catch (IllegalAccessException ignored) {
            previousYawField = null;
            previousPitchField = null;
        }
    }

    private void resolvePreviousRotationFields(Class<?> playerClass) {
        if (previousRotationFieldsResolved) return;
        previousRotationFieldsResolved = true;
        previousYawField = findFloatField(playerClass, "yRotO", "previousYaw", "lastYaw");
        previousPitchField = findFloatField(playerClass, "xRotO", "previousPitch", "lastPitch");
    }

    private static Field findFloatField(Class<?> type, String... names) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    if (field.getType() == float.class) {
                        field.setAccessible(true);
                        return field;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Sıradaki uyumlu ad denenir.
                }
            }
        }
        return null;
    }

    private static float clampPitch(float value) {
        return Math.max(-89.9F, Math.min(89.9F, value));
    }

    private static boolean isScreenOpen(Object client) {
        return getCurrentScreen(client) != null;
    }

    private static Object getCurrentScreen(Object client) {
        for (String fieldName : new String[]{"currentScreen", "screen"}) {
            try {
                java.lang.reflect.Field field = client.getClass().getField(fieldName);
                return field.get(client);
            } catch (ReflectiveOperationException ignored) {
                try {
                    java.lang.reflect.Field field = client.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.get(client);
                } catch (ReflectiveOperationException ignoredAgain) {
                    // Sıradaki ad denenir.
                }
            }
        }

        for (String methodName : new String[]{"currentScreen", "screen", "getScreen"}) {
            try {
                return client.getClass().getMethod(methodName).invoke(client);
            } catch (ReflectiveOperationException ignored) {
                // Sıradaki ad denenir.
            }
        }
        return null;
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
