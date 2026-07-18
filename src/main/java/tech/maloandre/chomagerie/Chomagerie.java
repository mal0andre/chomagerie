package tech.maloandre.chomagerie;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.maloandre.chomagerie.config.ServerConfig;
import tech.maloandre.chomagerie.event.ItemStackDepletedCallback;
import tech.maloandre.chomagerie.network.ConfigSyncPayload;
import tech.maloandre.chomagerie.network.RefillNotificationPayload;
import tech.maloandre.chomagerie.util.ShulkerRefillHandler;

public class Chomagerie implements ModInitializer {

    public static final String MOD_ID = "chomagerie";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Chomagerie - Automatic refill system enabled");

        // Initialize server configuration
        ServerConfig.getInstance();

        // Register network packet types
        PayloadTypeRegistry.serverboundPlay().register(ConfigSyncPayload.ID, ConfigSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RefillNotificationPayload.ID, RefillNotificationPayload.CODEC);

        // Register server-side network handler
        ConfigSyncPayload.registerServerHandler();

        // Detect when players connect
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // Do nothing here. If the player has the mod, they will send their config automatically
            // If after a few seconds they haven't sent a config, we assume they don't have the mod
            LOGGER.debug("Player {} connected, waiting for configuration...", handler.player.getName().getString());
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
