package tech.maloandre.chomagerie.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;
import tech.maloandre.chomagerie.Chomagerie;
import tech.maloandre.chomagerie.client.command.ChomagerieCommand;
import tech.maloandre.chomagerie.client.config.ChomagerieConfig;
import tech.maloandre.chomagerie.client.config.ModMenuIntegration;
import tech.maloandre.chomagerie.client.network.ClientNetworkHandler;
import tech.maloandre.chomagerie.config.ModState;
import tech.maloandre.chomagerie.mixin.client.OptionsAccessor;

import java.util.Arrays;

public class ChomagerieClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("chomagerie-client");
    private static final KeyMapping.Category CHOMAGERIE_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(Chomagerie.MOD_ID, "chomagerie")
    );
    private static KeyMapping openConfigKey;
    private static boolean keyMappingAdded;

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
        registerKeyBindings();

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

    private static void registerKeyBindings() {
        openConfigKey = new KeyMapping(
                "key.chomagerie.open_config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                CHOMAGERIE_CATEGORY
        );

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> addKeyMapping(client.options, openConfigKey));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.consumeClick()) {
                client.setScreenAndShow(ModMenuIntegration.createConfigScreen(null));
            }
        });
    }

    private static void addKeyMapping(Options options, KeyMapping keyMapping) {
        if (keyMappingAdded) {
            return;
        }

        KeyMapping[] updatedMappings = Arrays.copyOf(options.keyMappings, options.keyMappings.length + 1);
        updatedMappings[updatedMappings.length - 1] = keyMapping;

        ((OptionsAccessor) options).chomagerie$setKeyMappings(updatedMappings);
        KeyMapping.resetMapping();
        keyMappingAdded = true;
    }
}
