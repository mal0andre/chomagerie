package tech.maloandre.chomagerie.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server configuration for storing player preferences
 */
public class ServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("chomagerie");
    private static final Path PLAYERS_CONFIG_PATH = CONFIG_DIR.resolve("players.json");

    private static ServerConfig instance;
    private Map<String, PlayerConfig> playerConfigs = new HashMap<>();

    private ServerConfig() {
        load();
    }

    public static ServerConfig getInstance() {
        if (instance == null) {
            instance = new ServerConfig();
        }
        return instance;
    }

    private void load() {
        try {
            Files.createDirectories(CONFIG_DIR);

            if (Files.exists(PLAYERS_CONFIG_PATH)) {
                String json = Files.readString(PLAYERS_CONFIG_PATH);
                Map<String, PlayerConfig> loaded = GSON.fromJson(json,
                        new TypeToken<Map<String, PlayerConfig>>() {
                        }.getType());
                if (loaded != null) {
                    playerConfigs = loaded;
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lors du chargement de la configuration serveur de Chomagerie: " + e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            String json = GSON.toJson(playerConfigs);
            Files.writeString(PLAYERS_CONFIG_PATH, json);
        } catch (IOException e) {
            System.err.println("Erreur lors de la sauvegarde de la configuration serveur de Chomagerie: " + e.getMessage());
        }
    }

    /**
     * Récupère la configuration d'un joueur
     */
    public PlayerConfig getPlayerConfig(UUID playerUuid) {
        String uuidString = playerUuid.toString();
        return playerConfigs.computeIfAbsent(uuidString, k -> new PlayerConfig());
    }

    /**
     * Met à jour la configuration d'un joueur
     */
    public void setPlayerConfig(UUID playerUuid, PlayerConfig config) {
        playerConfigs.put(playerUuid.toString(), config);
        save();
    }

    /**
     * Active/désactive le ShulkerRefill pour un joueur
     */
    public void setShulkerRefillEnabled(UUID playerUuid, boolean enabled) {
        PlayerConfig config = getPlayerConfig(playerUuid);
        config.shulkerRefillEnabled = enabled;
        setPlayerConfig(playerUuid, config);
    }

    /**
     * Vérifie si le ShulkerRefill est activé pour un joueur
     */
    public boolean isShulkerRefillEnabled(UUID playerUuid) {
        PlayerConfig config = getPlayerConfig(playerUuid);
        // Si le joueur n'a pas le mod, ne pas activer le refill
        return config.hasModInstalled && config.shulkerRefillEnabled;
    }

    /**
     * Marque qu'un joueur a le mod installé côté client
     */
    public void setPlayerHasMod(UUID playerUuid, boolean hasMod) {
        PlayerConfig config = getPlayerConfig(playerUuid);
        config.hasModInstalled = hasMod;
        setPlayerConfig(playerUuid, config);
    }

    /**
     * Vérifie si un joueur a le mod installé
     */
    public boolean playerHasMod(UUID playerUuid) {
        return getPlayerConfig(playerUuid).hasModInstalled;
    }

    /**
     * Active/désactive les messages de refill pour un joueur
     */
    public void setShowRefillMessages(UUID playerUuid, boolean show) {
        PlayerConfig config = getPlayerConfig(playerUuid);
        config.showRefillMessages = show;
        setPlayerConfig(playerUuid, config);
    }

    /**
     * Vérifie si les messages de refill sont activés pour un joueur
     */
    public boolean shouldShowRefillMessages(UUID playerUuid) {
        return getPlayerConfig(playerUuid).showRefillMessages;
    }

    /**
     * Active/désactive le filtrage par nom de shulker pour un joueur
     */
    public void setFilterByName(UUID playerUuid, boolean filter) {
        PlayerConfig config = getPlayerConfig(playerUuid);
        config.filterByName = filter;
        setPlayerConfig(playerUuid, config);
    }

    /**
     * Vérifie si le filtrage par nom est activé pour un joueur
     */
    public boolean isFilterByNameEnabled(UUID playerUuid) {
        return getPlayerConfig(playerUuid).filterByName;
    }

    /**
     * Définit le nom de filtre pour les shulker boxes d'un joueur
     */
    public void setShulkerNameFilter(UUID playerUuid, String filter) {
        PlayerConfig config = getPlayerConfig(playerUuid);
        config.shulkerNameFilter = filter;
        setPlayerConfig(playerUuid, config);
    }

    /**
     * Récupère le nom de filtre pour les shulker boxes d'un joueur
     */
    public String getShulkerNameFilter(UUID playerUuid) {
        return getPlayerConfig(playerUuid).shulkerNameFilter;
    }

    public void setTeamTagEnabled(UUID playerUuid, boolean enabled) {
        PlayerConfig config = getPlayerConfig(playerUuid);
        config.teamTagEnabled = enabled;
        setPlayerConfig(playerUuid, config);
    }

    public boolean isTeamTagEnabled(UUID playerUuid) {
        return getPlayerConfig(playerUuid).teamTagEnabled;
    }

    public void setTeamTag(UUID playerUuid, String tag) {
        PlayerConfig config = getPlayerConfig(playerUuid);
        config.teamTag = tag;
        setPlayerConfig(playerUuid, config);
    }

    public String getTeamTag(UUID playerUuid) {
        return getPlayerConfig(playerUuid).teamTag;
    }

    public void setTeamTagIdentity(UUID playerUuid, String playerName, String teamName) {
        PlayerConfig config = getPlayerConfig(playerUuid);
        config.teamTagPlayerName = playerName;
        config.teamTagTeamName = teamName;
        setPlayerConfig(playerUuid, config);
    }

    public void clearTeamTagIdentity(UUID playerUuid) {
        PlayerConfig config = getPlayerConfig(playerUuid);
        config.teamTagPlayerName = "";
        config.teamTagTeamName = "";
        setPlayerConfig(playerUuid, config);
    }

    public String getTeamTagPlayerName(UUID playerUuid) {
        return getPlayerConfig(playerUuid).teamTagPlayerName;
    }

    /**
     * Configuration individuelle d'un joueur
     */
    public static class PlayerConfig {
        public boolean hasModInstalled = false; // false par défaut, true quand le client envoie la config
        public boolean shulkerRefillEnabled = true;
        public boolean showRefillMessages = true;
        public boolean filterByName = false;
        public String shulkerNameFilter = "restock same";
        public boolean teamTagEnabled = false;
        public String teamTag = "";
        public String teamTagPlayerName = "";
        public String teamTagTeamName = "";

        public PlayerConfig() {
        }
    }
}

