package com.yagiz.skinpowers.network;

import com.yagiz.skinpowers.SkinPowersMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientEffectPayload(String effect, float strength, int durationTicks) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SkinPowersMod.MOD_ID, "client_effect");
    public static final Type<ClientEffectPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientEffectPayload> CODEC = new StreamCodec<>() {
        @Override
        public ClientEffectPayload decode(RegistryFriendlyByteBuf buffer) {
            return new ClientEffectPayload(buffer.readUtf(32), buffer.readFloat(), buffer.readVarInt());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ClientEffectPayload payload) {
            buffer.writeUtf(payload.effect(), 32);
            buffer.writeFloat(payload.strength());
            buffer.writeVarInt(Math.max(0, payload.durationTicks()));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
