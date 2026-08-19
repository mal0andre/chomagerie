package tech.maloandre.chomagerie.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import tech.maloandre.chomagerie.Chomagerie;
import tech.maloandre.chomagerie.client.config.ChomagerieConfig;
import tech.maloandre.chomagerie.network.ConfigSyncPayload;
import tech.maloandre.chomagerie.network.RefillNotificationPayload;
import tech.maloandre.chomagerie.network.TeamManageRequestPayload;
import tech.maloandre.chomagerie.network.TeamManageSyncPayload;
import tech.maloandre.chomagerie.network.VersionCheckPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side network handler
 */
public class ClientNetworkHandler {
    private static List<TeamManageSyncPayload.TeamInfo> serverTeams = new ArrayList<>();

    public static List<TeamManageSyncPayload.TeamInfo> getServerTeams() {
        return List.copyOf(serverTeams);
    }

    public static boolean canManageServerTeams() {
        return ClientPlayNetworking.canSend(TeamManageRequestPayload.ID);
    }

    public static boolean syncVersionWithServer() {
        if (!ClientPlayNetworking.canSend(VersionCheckPayload.ID)) {
            if (ClientPlayNetworking.canSend(ConfigSyncPayload.ID)) {
                disconnectWithVersionMessage("Chomagerie: version serveur incompatible ou trop ancienne. "
                        + "Client: " + Chomagerie.MOD_VERSION + ". Mets le mod Chomagerie du serveur a jour.");
                return false;
            }

            return true;
        }

        ClientPlayNetworking.send(new VersionCheckPayload(Chomagerie.MOD_VERSION));
        return true;
    }

    public static void requestServerTeams() {
        if (!canManageServerTeams()) {
            serverTeams = new ArrayList<>();
            return;
        }

        ClientPlayNetworking.send(new TeamManageRequestPayload(TeamManageRequestPayload.Action.LIST, "", ""));
    }

    public static void sendTeamManagementAction(TeamManageRequestPayload.Action action, String teamName, String value) {
        if (!canManageServerTeams()) {
            return;
        }
        ClientPlayNetworking.send(new TeamManageRequestPayload(action, safe(teamName), safe(value)));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

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
                config.shulkerRefill.getShulkerNameFilters(),
                config.teamTag.isEnabled(),
                config.teamTag.getFormattedTag()
        );

        ClientPlayNetworking.send(payload);
        requestServerTeams();
    }

    /**
     * Initializes network handlers on client side
     */
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(VersionCheckPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (!Chomagerie.MOD_VERSION.equals(payload.modVersion())) {
                    disconnectWithVersionMessage("Chomagerie: versions differentes entre le client et le serveur. "
                            + "Client: " + Chomagerie.MOD_VERSION + ", serveur: " + payload.modVersion() + ".");
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(TeamManageSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> serverTeams = new ArrayList<>(payload.teams()));
        });

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

    private static void disconnectWithVersionMessage(String message) {
        Minecraft client = Minecraft.getInstance();
        Component title = Component.literal("Connexion refusee");
        Component reason = Component.literal(message);

        if (client.getConnection() != null) {
            client.getConnection().getConnection().disconnect(reason);
        }

        client.disconnect(new DisconnectedScreen(new TitleScreen(), title, reason), false);
    }
}
