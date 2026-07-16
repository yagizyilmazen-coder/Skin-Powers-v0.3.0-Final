package com.yagiz.skinpowers.network;

import com.yagiz.skinpowers.SkinPowersMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ServerStatePayload(String stateJson) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SkinPowersMod.MOD_ID, "server_state");
    public static final Type<ServerStatePayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerStatePayload> CODEC = new StreamCodec<>() {
        @Override
        public ServerStatePayload decode(RegistryFriendlyByteBuf buffer) {
            return new ServerStatePayload(buffer.readUtf(8192));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ServerStatePayload payload) {
            buffer.writeUtf(payload.stateJson(), 8192);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
