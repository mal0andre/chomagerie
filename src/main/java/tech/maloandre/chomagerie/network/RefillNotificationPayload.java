package tech.maloandre.chomagerie.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import tech.maloandre.chomagerie.Chomagerie;

public record RefillNotificationPayload(String itemName) implements CustomPacketPayload {
    public static final Type<RefillNotificationPayload> ID = new Type<>(Identifier.parse(Chomagerie.MOD_ID + ":refill_notification"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RefillNotificationPayload> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeUtf(value.itemName()),
            buf -> new RefillNotificationPayload(buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}

