package tech.maloandre.chomagerie.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import tech.maloandre.chomagerie.client.network.ClientNetworkHandler;
import tech.maloandre.chomagerie.config.ModState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ChomagerieConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("chomagerie.json");

    private static ChomagerieConfig instance;

    // ShulkerRefill configuration
    public ShulkerRefillConfig shulkerRefill = new ShulkerRefillConfig();

    public TeamTagConfig teamTag = new TeamTagConfig();
    public ServerTeamsMenuConfig serverTeams = new ServerTeamsMenuConfig();

    // Notifications configuration
    public NotificationsConfig notifications = new NotificationsConfig();

    // Future configurations (examples)
    // public AutoCraftConfig autoCraft = new AutoCraftConfig();
    // public StorageManagerConfig storageManager = new StorageManagerConfig();

    private ChomagerieConfig() {
    }

    public static ChomagerieConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static ChomagerieConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                ChomagerieConfig config = GSON.fromJson(json, ChomagerieConfig.class);
                if (config != null) {
                    // Assurer que les sous-configs sont initialisées
                    if (config.shulkerRefill == null) {
                        config.shulkerRefill = new ShulkerRefillConfig();
                    }
                    if (config.teamTag == null) {
                        config.teamTag = new TeamTagConfig();
                    }
                    config.teamTag.ensureDefaults();
                    if (config.serverTeams == null) {
                        config.serverTeams = new ServerTeamsMenuConfig();
                    }
                    config.serverTeams.ensureDefaults();
                    if (config.notifications == null) {
                        config.notifications = new NotificationsConfig();
                    }
                    // Synchroniser ModState avec la configuration chargée
                    ModState.setClientEnabled(config.shulkerRefill.enabled);
                    return config;
                }
            } catch (IOException e) {
                System.err.println("Erreur lors du chargement de la configuration de Chomagerie: " + e.getMessage());
            }
        }

        // Créer une nouvelle configuration par défaut
        ChomagerieConfig config = new ChomagerieConfig();
        config.save();
        // Synchroniser ModState avec la configuration par défaut
        ModState.setClientEnabled(config.shulkerRefill.enabled);
        return config;
    }

    public void save() {
        try {
            String json = GSON.toJson(this);
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, json);
            // Synchroniser ModState après la sauvegarde
            ModState.setClientEnabled(this.shulkerRefill.isEnabled());
            // Envoyer la configuration au serveur
            ClientNetworkHandler.sendConfigToServer();
        } catch (IOException e) {
            System.err.println("Erreur lors de la sauvegarde de la configuration de Chomagerie: " + e.getMessage());
        }
    }

    /**
     * Recharge la configuration depuis le fichier
     */
    public void reload() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                ChomagerieConfig loaded = GSON.fromJson(json, ChomagerieConfig.class);
                if (loaded != null) {
                    // Mettre à jour l'instance actuelle avec les valeurs chargées
                    if (loaded.shulkerRefill != null) {
                        this.shulkerRefill = loaded.shulkerRefill;
                    }
                    if (loaded.teamTag != null) {
                        this.teamTag = loaded.teamTag;
                        this.teamTag.ensureDefaults();
                    }
                    if (loaded.serverTeams != null) {
                        this.serverTeams = loaded.serverTeams;
                        this.serverTeams.ensureDefaults();
                    }
                    if (loaded.notifications != null) {
                        this.notifications = loaded.notifications;
                    }
                    // Synchroniser ModState avec la configuration rechargée
                    ModState.setClientEnabled(this.shulkerRefill.isEnabled());
                }
            } catch (IOException e) {
                System.err.println("Erreur lors du rechargement de la configuration de Chomagerie: " + e.getMessage());
            }
        }
    }

    // Classe interne pour la configuration ShulkerRefill
    public static class ShulkerRefillConfig {
        public boolean enabled = true;
        public boolean showRefillMessages = true;
        public boolean playSounds = true;
        public boolean filterByName = false;
        public String shulkerNameFilter = "restock same";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean shouldShowRefillMessages() {
            return showRefillMessages;
        }

        public void setShowRefillMessages(boolean show) {
            this.showRefillMessages = show;
        }

        public boolean shouldPlaySounds() {
            return playSounds;
        }

        public void setPlaySounds(boolean play) {
            this.playSounds = play;
        }

        public boolean isFilterByNameEnabled() {
            return filterByName;
        }

        public void setFilterByName(boolean filter) {
            this.filterByName = filter;
        }

        public String getShulkerNameFilter() {
            return shulkerNameFilter;
        }

        public void setShulkerNameFilter(String name) {
            this.shulkerNameFilter = name;
        }
    }


    public static class TeamTagConfig {
        public boolean enabled = false;
        public String tag = "";
        public ColorMode colorMode = ColorMode.MANUAL;
        public MinecraftColor selectedColor = MinecraftColor.AQUA;
        public String gradientStartColor = "#977272";
        public String gradientEndColor = "#E32B2B";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public ColorMode getColorMode() {
            return colorMode;
        }

        public void setColorMode(ColorMode colorMode) {
            this.colorMode = colorMode;
        }

        public MinecraftColor getSelectedColor() {
            return selectedColor;
        }

        public void setSelectedColor(MinecraftColor selectedColor) {
            this.selectedColor = selectedColor;
        }

        public String getGradientStartColor() {
            return gradientStartColor;
        }

        public void setGradientStartColor(String gradientStartColor) {
            this.gradientStartColor = normalizeHexColor(gradientStartColor, "#977272");
        }

        public void setGradientStartColor(int rgb) {
            this.gradientStartColor = toHexColor(rgb);
        }

        public String getGradientEndColor() {
            return gradientEndColor;
        }

        public void setGradientEndColor(String gradientEndColor) {
            this.gradientEndColor = normalizeHexColor(gradientEndColor, "#E32B2B");
        }

        public void setGradientEndColor(int rgb) {
            this.gradientEndColor = toHexColor(rgb);
        }

        public void ensureDefaults() {
            if (tag == null) {
                tag = "";
            }
            if (colorMode == null) {
                colorMode = ColorMode.MANUAL;
            }
            if (selectedColor == null) {
                selectedColor = MinecraftColor.AQUA;
            }
            gradientStartColor = normalizeHexColor(gradientStartColor, "#977272");
            gradientEndColor = normalizeHexColor(gradientEndColor, "#E32B2B");
        }

        public String getFormattedTag() {
            ensureDefaults();
            String baseTag = stripColorCodes(tag);
            return switch (colorMode) {
                case SOLID -> selectedColor.code + baseTag;
                case GRADIENT -> "<gradient:" + gradientStartColor + ":" + gradientEndColor + ">" + baseTag;
                case RAINBOW -> rainbowTag(baseTag);
                case MANUAL -> tag;
            };
        }

        private static String normalizeHexColor(String value, String fallback) {
            if (value == null) {
                return fallback;
            }

            String normalized = value.trim();
            if (!normalized.startsWith("#")) {
                normalized = "#" + normalized;
            }
            if (normalized.length() != 7) {
                return fallback;
            }

            for (int i = 1; i < normalized.length(); i++) {
                char current = normalized.charAt(i);
                boolean hex = (current >= '0' && current <= '9')
                        || (current >= 'a' && current <= 'f')
                        || (current >= 'A' && current <= 'F');
                if (!hex) {
                    return fallback;
                }
            }
            return normalized.toUpperCase();
        }

        public static int parseHexColor(String value, int fallback) {
            String normalized = normalizeHexColor(value, toHexColor(fallback));
            return Integer.parseInt(normalized.substring(1), 16);
        }

        private static String toHexColor(int rgb) {
            return String.format("#%06X", rgb & 0xFFFFFF);
        }

        private static String rainbowTag(String text) {
            MinecraftColor[] cycle = {
                    MinecraftColor.RED,
                    MinecraftColor.GOLD,
                    MinecraftColor.YELLOW,
                    MinecraftColor.GREEN,
                    MinecraftColor.AQUA,
                    MinecraftColor.BLUE,
                    MinecraftColor.LIGHT_PURPLE
            };
            StringBuilder builder = new StringBuilder();
            int colorIndex = 0;
            for (int i = 0; i < text.length(); i++) {
                char current = text.charAt(i);
                if (Character.isWhitespace(current)) {
                    builder.append(current);
                    continue;
                }

                builder.append(cycle[colorIndex % cycle.length].code).append(current);
                colorIndex++;
            }
            return builder.toString();
        }

        private static String stripColorCodes(String text) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char current = text.charAt(i);
                if ((current == '&' || current == '\u00a7') && i + 1 < text.length()
                        && isMinecraftColorCode(text.charAt(i + 1))) {
                    i++;
                    continue;
                }

                builder.append(current);
            }
            return builder.toString();
        }

        private static boolean isMinecraftColorCode(char code) {
            char normalized = Character.toLowerCase(code);
            return (normalized >= '0' && normalized <= '9')
                    || (normalized >= 'a' && normalized <= 'f')
                    || normalized == 'k'
                    || normalized == 'l'
                    || normalized == 'm'
                    || normalized == 'n'
                    || normalized == 'o'
                    || normalized == 'r';
        }
    }

    public enum ColorMode {
        MANUAL("Manual codes"),
        SOLID("Selected color"),
        GRADIENT("Gradient"),
        RAINBOW("Rainbow characters");

        private final String label;

        ColorMode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum MinecraftColor {
        BLACK("&0", "Black"),
        DARK_BLUE("&1", "Dark Blue"),
        DARK_GREEN("&2", "Dark Green"),
        DARK_AQUA("&3", "Dark Aqua"),
        DARK_RED("&4", "Dark Red"),
        DARK_PURPLE("&5", "Dark Purple"),
        GOLD("&6", "Gold"),
        GRAY("&7", "Gray"),
        DARK_GRAY("&8", "Dark Gray"),
        BLUE("&9", "Blue"),
        GREEN("&a", "Green"),
        AQUA("&b", "Aqua"),
        RED("&c", "Red"),
        LIGHT_PURPLE("&d", "Light Purple"),
        YELLOW("&e", "Yellow"),
        WHITE("&f", "White");

        private final String code;
        private final String label;

        MinecraftColor(String code, String label) {
            this.code = code;
            this.label = label;
        }

        public String getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }
    }

    public static class ServerTeamsMenuConfig {
        public String selectedTeamName = "";
        public String text = "";
        public ServerTeamAction action = ServerTeamAction.REFRESH;
        public boolean runAction = false;

        public void ensureDefaults() {
            if (selectedTeamName == null) {
                selectedTeamName = "";
            }
            if (text == null) {
                text = "";
            }
            if (action == null) {
                action = ServerTeamAction.REFRESH;
            }
        }
    }

    public enum ServerTeamAction {
        REFRESH("Refresh list"),
        CREATE("Create team"),
        SET_DISPLAY("Set display name"),
        SET_PREFIX("Set prefix"),
        SET_SUFFIX("Set suffix");

        private final String label;

        ServerTeamAction(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    // Classe interne pour la configuration Notifications
    public static class NotificationsConfig {
        public boolean enableNotifications = true;
        public boolean notifyOnSuccess = true;
        public boolean notifyOnError = true;
        public boolean useActionBar = true;
        public int notificationDuration = 3;

        public boolean isNotificationsEnabled() {
            return enableNotifications;
        }

        public void setNotificationsEnabled(boolean enabled) {
            this.enableNotifications = enabled;
        }

        public boolean shouldNotifyOnSuccess() {
            return notifyOnSuccess;
        }

        public void setNotifyOnSuccess(boolean notify) {
            this.notifyOnSuccess = notify;
        }

        public boolean shouldNotifyOnError() {
            return notifyOnError;
        }

        public void setNotifyOnError(boolean notify) {
            this.notifyOnError = notify;
        }

        public boolean shouldUseActionBar() {
            return useActionBar;
        }

        public void setUseActionBar(boolean use) {
            this.useActionBar = use;
        }

        public int getNotificationDuration() {
            return notificationDuration;
        }

        public void setNotificationDuration(int duration) {
            this.notificationDuration = duration;
        }
    }
}

