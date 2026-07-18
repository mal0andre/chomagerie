package tech.maloandre.chomagerie.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import tech.maloandre.chomagerie.client.config.ChomagerieConfig;
import tech.maloandre.chomagerie.network.ConfigSyncPayload;
import tech.maloandre.chomagerie.network.RefillNotificationPayload;

/**
 * Client-side network handler
 */
public class ClientNetworkHandler {

    /**
     * Sends client configuration to server
     */
    public static void sendConfigToServer() {
        if (!ClientPlayNetworking.canSend(ConfigSyncPayload.ID)) {
            return;
        }

        ChomagerieConfig config = ChomagerieConfig.getInstance();
        ConfigSyncPayload payload = new ConfigSyncPayload(
                config.shulkerRefill.isEnabled(),
                config.shulkerRefill.shouldShowRefillMessages(),
                config.shulkerRefill.isFilterByNameEnabled(),
                config.shulkerRefill.getShulkerNameFilter()
        );

        ClientPlayNetworking.send(payload);
    }

    /**
     * Initializes network handlers on client side
     */
    public static void init() {
        // Handler for refill notifications
        ClientPlayNetworking.registerGlobalReceiver(RefillNotificationPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                ChomagerieConfig config = ChomagerieConfig.getInstance();
                Minecraft client = context.client();

                // Display message if enabled
                if (config.shulkerRefill.shouldShowRefillMessages() && client.player != null) {
                    client.player.sendOverlayMessage(
                            Component.literal("§7[§6Refill§7] §a" + payload.itemName() + " refilled from a shulker box")
                    );
                }

                // Play sound if enabled
                if (config.shulkerRefill.shouldPlaySounds() && client.player != null && client.level != null) {
                    client.level.playSound(
                            client.player,
                            client.player.blockPosition(),
                            SoundEvents.ITEM_PICKUP,
                            SoundSource.PLAYERS,
                            0.5f, // Volume
                            1.2f  // Pitch
                    );
                }
            });
        });
    }
}

