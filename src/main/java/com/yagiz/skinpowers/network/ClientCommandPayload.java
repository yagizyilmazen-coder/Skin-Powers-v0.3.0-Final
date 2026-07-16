package com.yagiz.skinpowers.network;

import com.yagiz.skinpowers.SkinPowersMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientCommandPayload(String command) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SkinPowersMod.MOD_ID, "client_command");
    public static final Type<ClientCommandPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientCommandPayload> CODEC = new StreamCodec<>() {
        @Override
        public ClientCommandPayload decode(RegistryFriendlyByteBuf buffer) {
            return new ClientCommandPayload(buffer.readUtf(256));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ClientCommandPayload payload) {
            buffer.writeUtf(payload.command(), 256);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
