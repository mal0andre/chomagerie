package tech.maloandre.chomagerie.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import tech.maloandre.chomagerie.Chomagerie;

import java.lang.reflect.Method;

public final class Pl3xMapCompat {
    private static final String MOD_ID = "pl3xmap";

    private Pl3xMapCompat() {
    }

    public static void notifyQuit(ServerPlayer player) {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return;
        }

        try {
            Class<?> apiClass = Class.forName("net.pl3x.map.core.Pl3xMap");
            Object api = apiClass.getMethod("api").invoke(null);
            Object registry = apiClass.getMethod("getPlayerRegistry").invoke(api);
            Object mapPlayer = registry.getClass().getMethod("get", java.util.UUID.class).invoke(registry, player.getUUID());
            if (mapPlayer == null) {
                return;
            }

            try {
                Method setHidden = mapPlayer.getClass().getMethod("setHidden", boolean.class, boolean.class);
                setHidden.invoke(mapPlayer, true, false);
            } catch (ReflectiveOperationException ignored) {
            }

            try {
                Class<?> listenerClass = Class.forName("net.pl3x.map.core.player.PlayerListener");
                Object listener = listenerClass.getConstructor().newInstance();
                Method onQuit = listenerClass.getMethod("onQuit", Class.forName("net.pl3x.map.core.player.Player"));
                onQuit.invoke(listener, mapPlayer);
            } catch (ReflectiveOperationException ignored) {
            }

            registry.getClass().getMethod("unregister", java.util.UUID.class).invoke(registry, player.getUUID());
        } catch (ReflectiveOperationException | LinkageError error) {
            Chomagerie.LOGGER.debug("Could not notify Pl3xMap about chomplayer quit: {}", player.getScoreboardName(), error);
        }
    }
}
