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

import java.util.ArrayList;
import java.util.List;

/**
 * Paquet pour synchroniser la configuration du client vers le serveur.
 */
public record ConfigSyncPayload(
        boolean shulkerRefillEnabled,
        boolean showRefillMessages,
        boolean filterByName,
        List<String> shulkerNameFilters,
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
                buf.writeVarInt(value.shulkerNameFilters().size());
                for (String filter : value.shulkerNameFilters()) {
                    buf.writeUtf(filter);
                }
                buf.writeBoolean(value.teamTagEnabled());
                buf.writeUtf(value.teamTag());
            },
            (buf) -> {
                boolean shulkerRefillEnabled = buf.readBoolean();
                boolean showRefillMessages = buf.readBoolean();
                boolean filterByName = buf.readBoolean();
                int filterCount = buf.readVarInt();
                List<String> shulkerNameFilters = new ArrayList<>();
                for (int i = 0; i < filterCount; i++) {
                    shulkerNameFilters.add(buf.readUtf());
                }
                return new ConfigSyncPayload(
                        shulkerRefillEnabled,
                        showRefillMessages,
                        filterByName,
                        shulkerNameFilters,
                        buf.readBoolean(),
                        buf.readUtf()
                );
            }
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
            config.setShulkerNameFilters(player.getUUID(), payload.shulkerNameFilters);
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
                    payload.filterByName ? String.join(", ", payload.shulkerNameFilters) : "desactive",
                    payload.teamTagEnabled ? payload.teamTag : "desactive");
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
