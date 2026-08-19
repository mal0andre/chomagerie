package tech.maloandre.chomagerie;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.maloandre.chomagerie.command.ChomagerieTeamCommand;
import tech.maloandre.chomagerie.config.ServerConfig;
import tech.maloandre.chomagerie.event.ItemStackDepletedCallback;
import tech.maloandre.chomagerie.gamerule.ModGameRules;
import tech.maloandre.chomagerie.network.ConfigSyncPayload;
import tech.maloandre.chomagerie.network.RefillNotificationPayload;
import tech.maloandre.chomagerie.network.TeamManageRequestPayload;
import tech.maloandre.chomagerie.network.TeamManageSyncPayload;
import tech.maloandre.chomagerie.network.VersionCheckPayload;
import tech.maloandre.chomagerie.util.ShulkerRefillHandler;

public class Chomagerie implements ModInitializer {

    public static final String MOD_ID = "chomagerie";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final String MOD_VERSION = FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Chomagerie - Automatic refill system enabled");

        // Initialize server configuration
        ServerConfig.getInstance();
        ModGameRules.register();

        // Register network packet types
        PayloadTypeRegistry.serverboundPlay().register(VersionCheckPayload.ID, VersionCheckPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ConfigSyncPayload.ID, ConfigSyncPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TeamManageRequestPayload.ID, TeamManageRequestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RefillNotificationPayload.ID, RefillNotificationPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TeamManageSyncPayload.ID, TeamManageSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(VersionCheckPayload.ID, VersionCheckPayload.CODEC);

        // Register server-side network handler
        VersionCheckPayload.registerServerHandler();
        ConfigSyncPayload.registerServerHandler();
        TeamManageRequestPayload.registerServerHandler();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ChomagerieTeamCommand.register(dispatcher));

        // Detect when players connect
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (!ServerPlayNetworking.canSend(player, VersionCheckPayload.ID)) {
                if (!ServerPlayNetworking.canSend(player, RefillNotificationPayload.ID)
                        && !ServerPlayNetworking.canSend(player, TeamManageSyncPayload.ID)) {
                    LOGGER.debug("Player {} connected without Chomagerie client mod.",
                            player.getName().getString());
                    return;
                }

                String message = "Chomagerie: version client incompatible ou trop ancienne. "
                        + "Serveur: " + MOD_VERSION + ". Mets ton mod Chomagerie a jour.";
                LOGGER.warn("Player {} has no Chomagerie version-check channel. Disconnecting with a clear message.",
                        player.getName().getString());
                handler.disconnect(Component.literal(message));
                return;
            }

            ServerPlayNetworking.send(player, new VersionCheckPayload(MOD_VERSION));
            LOGGER.debug("Player {} connected, sent Chomagerie server version {}",
                    player.getName().getString(), MOD_VERSION);
        });

        // Register automatic refill event from shulker boxes
        ItemStackDepletedCallback.EVENT.register((player, slot, item, previousStack) -> {
            if (!player.level().isClientSide()) {
                // Check if the mod is enabled for this player on the server
                // This method now also checks if the player has the mod installed
                boolean isEnabled = ServerConfig.getInstance().isShulkerRefillEnabled(player.getUUID());

                if (isEnabled) {
                    // Get the filtering parameters for this player
                    ServerConfig config = ServerConfig.getInstance();
                    boolean filterByName = config.isFilterByNameEnabled(player.getUUID());
                    String nameFilter = config.getShulkerNameFilter(player.getUUID());

                    // Use previousStack to get the complete item data (including enchantment levels for fireworks)
                    ShulkerRefillHandler.RefillResult result = ShulkerRefillHandler.tryRefillFromShulker(
                            player, slot, previousStack, filterByName, nameFilter
                    );

                    // If refill succeeded, send notification to client
                    if (result.success() && player instanceof ServerPlayer serverPlayer) {
                        ServerPlayNetworking.send(serverPlayer, new RefillNotificationPayload(result.itemName()));
                    }
                } else if (!ServerConfig.getInstance().playerHasMod(player.getUUID())) {
                    // Player doesn't have the mod, do nothing (silent)
                    LOGGER.debug("Refill ignored for {} - Mod not installed", player.getName().getString());
                }
            }
        });
    }
}
