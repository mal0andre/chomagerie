package tech.maloandre.chomagerie.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.maloandre.chomagerie.client.command.ChomagerieCommand;
import tech.maloandre.chomagerie.client.config.ChomagerieConfig;
import tech.maloandre.chomagerie.client.network.ClientNetworkHandler;
import tech.maloandre.chomagerie.config.ModState;

public class ChomagerieClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("chomagerie-client");

    @Override
    public void onInitializeClient() {
        // Load configuration
        ChomagerieConfig config = ChomagerieConfig.getInstance();

        // Update global state with ShulkerRefill config
        ModState.setClientEnabled(config.shulkerRefill.isEnabled());

        if (config.shulkerRefill.isEnabled()) {
            LOGGER.info("Chomagerie - ShulkerRefill is enabled on client");
        } else {
            LOGGER.info("Chomagerie - ShulkerRefill is disabled on client");
        }

        // Initialize network handler
        ClientNetworkHandler.init();

        // Synchronize config to server on connection
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ClientNetworkHandler.syncVersionWithServer()) {
                ClientNetworkHandler.sendConfigToServer();
                LOGGER.info("Configuration sent to server");
            }
        });

        // Register client commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            ChomagerieCommand.register(dispatcher);
        });
    }
}
