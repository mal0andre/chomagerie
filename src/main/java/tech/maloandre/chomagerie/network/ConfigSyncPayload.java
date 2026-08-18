package tech.maloandre.chomagerie.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import tech.maloandre.chomagerie.Chomagerie;
import tech.maloandre.chomagerie.command.ChomagerieTeamCommand;
import tech.maloandre.chomagerie.config.ServerConfig;

/**
 * Paquet pour synchroniser la configuration du client vers le serveur.
 */
public record ConfigSyncPayload(
        boolean shulkerRefillEnabled,
        boolean showRefillMessages,
        boolean filterByName,
        String shulkerNameFilter,
        boolean teamTagEnabled,
        String teamTag
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ConfigSyncPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.parse(Chomagerie.MOD_ID + ":config_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSyncPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeBoolean(value.shulkerRefillEnabled());
                buf.writeBoolean(value.showRefillMessages());
                buf.writeBoolean(value.filterByName());
                buf.writeUtf(value.shulkerNameFilter());
                buf.writeBoolean(value.teamTagEnabled());
                buf.writeUtf(value.teamTag());
            },
            (buf) -> new ConfigSyncPayload(
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readUtf(),
                    buf.readBoolean(),
                    buf.readUtf()
            )
    );

    /**
     * Enregistre le handler cote serveur.
     */
    public static void registerServerHandler() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> {
            ServerPlayer player = context.player();
            ServerConfig config = ServerConfig.getInstance();

            config.setPlayerHasMod(player.getUUID(), true);
            config.setShulkerRefillEnabled(player.getUUID(), payload.shulkerRefillEnabled);
            config.setShowRefillMessages(player.getUUID(), payload.showRefillMessages);
            config.setFilterByName(player.getUUID(), payload.filterByName);
            config.setShulkerNameFilter(player.getUUID(), payload.shulkerNameFilter);
            config.setTeamTagEnabled(player.getUUID(), payload.teamTagEnabled);
            config.setTeamTag(player.getUUID(), payload.teamTag);

            ServerScoreboard scoreboard = player.level().getServer().getScoreboard();
            if (payload.teamTagEnabled) {
                try {
                    ChomagerieTeamCommand.applyTeamTag(scoreboard, player, payload.teamTag);
                } catch (Exception e) {
                    ChomagerieTeamCommand.clearTeamTag(scoreboard, player);
                    player.sendSystemMessage(Component.literal("[Chomagerie] Tag invalide: " + e.getMessage()));
                }
            } else {
                ChomagerieTeamCommand.clearTeamTag(scoreboard, player);
            }

            Chomagerie.LOGGER.info("Configuration synchronisee pour le joueur {} - ShulkerRefill: {}, Filtre: {}, TeamTag: {} (Mod installe)",
                    player.getName().getString(),
                    payload.shulkerRefillEnabled,
                    payload.filterByName ? payload.shulkerNameFilter : "desactive",
                    payload.teamTagEnabled ? payload.teamTag : "desactive");
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
