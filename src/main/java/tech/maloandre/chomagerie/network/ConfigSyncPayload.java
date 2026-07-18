package tech.maloandre.chomagerie.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import tech.maloandre.chomagerie.Chomagerie;
import tech.maloandre.chomagerie.config.ServerConfig;

/**
 * Paquet pour synchroniser la configuration du client vers le serveur
 */
public record ConfigSyncPayload(
        boolean shulkerRefillEnabled,
        boolean showRefillMessages,
        boolean filterByName,
        String shulkerNameFilter
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ConfigSyncPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.parse(Chomagerie.MOD_ID + ":config_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSyncPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeBoolean(value.shulkerRefillEnabled());
                buf.writeBoolean(value.showRefillMessages());
                buf.writeBoolean(value.filterByName());
                buf.writeUtf(value.shulkerNameFilter());
            },
            (buf) -> new ConfigSyncPayload(
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readUtf()
            )
    );

    /**
     * Enregistre le handler côté serveur
     */
    public static void registerServerHandler() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> {
            ServerPlayer player = context.player();

            // Mettre à jour la configuration du joueur côté serveur
            ServerConfig config = ServerConfig.getInstance();

            // Marquer que le joueur a le mod installé
            config.setPlayerHasMod(player.getUUID(), true);

            // Appliquer sa configuration
            config.setShulkerRefillEnabled(player.getUUID(), payload.shulkerRefillEnabled);
            config.setShowRefillMessages(player.getUUID(), payload.showRefillMessages);
            config.setFilterByName(player.getUUID(), payload.filterByName);
            config.setShulkerNameFilter(player.getUUID(), payload.shulkerNameFilter);

            Chomagerie.LOGGER.info("Configuration synchronisée pour le joueur {} - ShulkerRefill: {}, Filtre: {} (Mod installé)",
                    player.getName().getString(), payload.shulkerRefillEnabled, payload.filterByName ? payload.shulkerNameFilter : "désactivé");
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}

