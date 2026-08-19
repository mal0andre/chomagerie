package tech.maloandre.chomagerie.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import tech.maloandre.chomagerie.Chomagerie;

public record VersionCheckPayload(String modVersion) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<VersionCheckPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.parse(Chomagerie.MOD_ID + ":version_check"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VersionCheckPayload> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeUtf(value.modVersion()),
            buf -> new VersionCheckPayload(buf.readUtf())
    );

    public static void registerServerHandler() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> {
            if (Chomagerie.MOD_VERSION.equals(payload.modVersion())) {
                return;
            }

            ServerPlayer player = context.player();
            String message = "Chomagerie: versions differentes entre le client et le serveur. "
                    + "Client: " + payload.modVersion() + ", serveur: " + Chomagerie.MOD_VERSION + ".";
            Chomagerie.LOGGER.warn("Player {} has Chomagerie {}, server has {}. Disconnecting.",
                    player.getName().getString(), payload.modVersion(), Chomagerie.MOD_VERSION);
            player.connection.disconnect(Component.literal(message));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
