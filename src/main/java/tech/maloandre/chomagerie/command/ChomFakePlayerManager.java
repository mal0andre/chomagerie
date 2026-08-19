package tech.maloandre.chomagerie.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import tech.maloandre.chomagerie.compat.Pl3xMapCompat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.mojang.datafixers.util.Pair;

public final class ChomFakePlayerManager {
    private static final String TAB_TEAM_PREFIX = "chomplayer_";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("chomagerie");
    private static final Path PERSISTENT_PLAYERS_PATH = CONFIG_DIR.resolve("chomplayers.json");
    private static final Path REAL_PLAYERS_PATH = CONFIG_DIR.resolve("real_players.json");
    private static final Map<UUID, ChomFakePlayerState> PLAYERS = new HashMap<>();
    private static final Set<UUID> PENDING_FAKE_PLAYERS = new HashSet<>();
    private static final Map<String, PersistentPlayerData> PERSISTENT_PLAYERS = new HashMap<>();
    private static final Set<String> REAL_PLAYER_NAMES = new HashSet<>();
    private static final double REACH = 4.5D;
    private static boolean registered;
    private static boolean loadedRealPlayers;
    private static int tickCounter;

    private ChomFakePlayerManager() {
    }

    public static void registerTickHandler() {
        if (registered) {
            return;
        }

        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(ChomFakePlayerManager::tick);
    }

    public static void spawnPersistentPlayers(MinecraftServer server) {
        loadPersistentPlayers();
        for (PersistentPlayerData data : PERSISTENT_PLAYERS.values()) {
            if (server.getPlayerList().getPlayerByName(data.name) != null) {
                continue;
            }

            ServerLevel level = levelFromId(server, data.dimension).orElse(server.overworld());
            GameType gameMode = GameType.byName(data.gameMode, GameType.SURVIVAL);
            GameProfile profile = resolveProfile(server, data.name);
            ServerPlayer player = spawn(server, level, profile, new Vec3(data.x, data.y, data.z), data.yaw, data.pitch, gameMode, null, data.ownerName);
            state(player).ifPresent(fake -> fake.persistent = true);
        }
    }

    public static ServerPlayer spawn(ServerPlayer source, String name) {
        return spawn(source, name, source.level(), source.position(), source.getYRot(), source.getXRot(), source.gameMode(), source.getScoreboardName());
    }

    public static ServerPlayer spawn(ServerPlayer source, String name, ServerLevel level, Vec3 position, float yaw, float pitch, GameType gameMode, String ownerName) {
        MinecraftServer server = source.level().getServer();
        GameProfile profile = resolveProfile(server, name);
        return spawn(server, level, profile, position, yaw, pitch, gameMode, null, ownerName);
    }

    public static ServerPlayer shadow(ServerPlayer source) {
        MinecraftServer server = source.level().getServer();
        GameProfile profile = source.getGameProfile();
        Vec3 position = source.position();
        float yaw = source.getYRot();
        float pitch = source.getXRot();
        GameType gameMode = source.gameMode();

        source.connection.disconnect(Component.literal("Shadowed by Chomagerie."));
        server.getPlayerList().remove(source);
        return spawn(server, source.level(), profile, position, yaw, pitch, gameMode, source, source.getScoreboardName());
    }

    private static ServerPlayer spawn(
            MinecraftServer server,
            ServerLevel level,
            GameProfile profile,
            Vec3 position,
            float yaw,
            float pitch,
            GameType gameMode,
            ServerPlayer copyFrom,
            String ownerName
    ) {
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        if (copyFrom != null) {
            player.restoreFrom(copyFrom, false);
        }

        player.setPos(position);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
        player.setGameMode(gameMode);

        FakeClientConnection connection = new FakeClientConnection();
        player.connection = new ServerGamePacketListenerImpl(
                server,
                connection,
                player,
                CommonListenerCookie.createInitial(profile, false)
        );

        PENDING_FAKE_PLAYERS.add(player.getUUID());
        server.getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));
        PENDING_FAKE_PLAYERS.remove(player.getUUID());
        PLAYERS.put(player.getUUID(), new ChomFakePlayerState(player, ownerName));
        applyTabOwnerTag(server.getScoreboard(), player, ownerName);
        return player;
    }

    public static boolean kill(ServerPlayer player) {
        ChomFakePlayerState state = PLAYERS.remove(player.getUUID());
        if (state == null) {
            return false;
        }

        removeTabOwnerTag(player.level().getServer().getScoreboard(), player);
        state.releaseChunkTickets();
        Pl3xMapCompat.notifyQuit(player);
        player.level().getServer().getPlayerList().remove(player);
        return true;
    }

    public static boolean removeForReconnect(PlayerList playerList, UUID uuid) {
        ServerPlayer player = playerList.getPlayer(uuid);
        if (player == null || !isFake(player)) {
            return false;
        }

        ChomFakePlayerState state = PLAYERS.remove(player.getUUID());
        if (state != null) {
            removeTabOwnerTag(player.level().getServer().getScoreboard(), player);
            state.releaseChunkTickets();
        }
        Pl3xMapCompat.notifyQuit(player);
        playerList.remove(player);
        return true;
    }

    public static void stop(ServerPlayer player) {
        state(player).ifPresent(ChomFakePlayerState::stop);
    }

    public static boolean isFake(ServerPlayer player) {
        return PLAYERS.containsKey(player.getUUID());
    }

    public static boolean isManagedFake(ServerPlayer player) {
        return PLAYERS.containsKey(player.getUUID()) || PENDING_FAKE_PLAYERS.contains(player.getUUID());
    }

    public static Optional<ServerPlayer> find(MinecraftServer server, String name) {
        ServerPlayer player = server.getPlayerList().getPlayerByName(name);
        if (player == null || !isFake(player) || player.isRemoved() || player.isDeadOrDying() || !player.isAlive()) {
            return Optional.empty();
        }
        return Optional.of(player);
    }

    public static Collection<ChomFakePlayerState> states() {
        return PLAYERS.values();
    }

    public static boolean canSpawnName(MinecraftServer server, String name) {
        if (server.getPlayerList().getPlayerByName(name) != null) {
            return false;
        }

        String normalizedName = name.toLowerCase(Locale.ROOT);
        return !PERSISTENT_PLAYERS.containsKey(normalizedName) && !isProtectedRealPlayerName(server, name);
    }

    public static boolean isProtectedRealPlayerName(MinecraftServer server, String name) {
        loadRealPlayers(server);
        return REAL_PLAYER_NAMES.contains(name.toLowerCase(Locale.ROOT));
    }

    public static void recordRealPlayerLogin(MinecraftServer server, ServerPlayer player) {
        if (isManagedFake(player)) {
            return;
        }

        loadRealPlayers(server);
        if (REAL_PLAYER_NAMES.add(player.getScoreboardName().toLowerCase(Locale.ROOT))) {
            saveRealPlayers();
        }
    }

    public static void setAction(ServerPlayer player, ChomPlayerAction action, ActionSchedule schedule) {
        state(player).ifPresent(fake -> fake.setAction(action, schedule));
    }

    public static void setAction(ServerPlayer player, ChomPlayerAction action, ActionSchedule schedule, int interval) {
        state(player).ifPresent(fake -> fake.setAction(action, schedule, interval));
    }

    public static void move(ServerPlayer player, MoveDirection direction) {
        state(player).ifPresent(fake -> fake.move(direction));
    }

    public static void look(ServerPlayer player, float yaw, float pitch) {
        player.setYRot(Mth.wrapDegrees(yaw));
        player.setXRot(Mth.clamp(pitch, -90.0F, 90.0F));
        player.setYHeadRot(player.getYRot());
        player.setYBodyRot(player.getYRot());
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        player.level().getChunkSource().sendToTrackingPlayersAndSelf(player, new ClientboundRotateHeadPacket(player, (byte) (player.getYHeadRot() * 256.0F / 360.0F)));
    }

    public static void lookAt(ServerPlayer player, Vec3 target) {
        Vec3 eye = player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * 180.0F / Math.PI) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy, horizontal) * 180.0F / Math.PI));
        look(player, yaw, pitch);
    }

    public static void turn(ServerPlayer player, float yawOffset) {
        look(player, player.getYRot() + yawOffset, player.getXRot());
    }

    public static void sneak(ServerPlayer player, boolean enabled) {
        player.setShiftKeyDown(enabled);
        player.setPose(enabled ? Pose.CROUCHING : Pose.STANDING);
        state(player).ifPresent(fake -> fake.refreshInput());
    }

    public static void sprint(ServerPlayer player, boolean enabled) {
        player.setSprinting(false);
        state(player).ifPresent(fake -> fake.refreshInput());
    }

    public static void hotbar(ServerPlayer player, int slot) {
        player.getInventory().setSelectedSlot(slot);
        syncEquipment(player);
    }

    public static void drop(ServerPlayer player, DropMode mode) {
        switch (mode) {
            case SINGLE -> player.drop(false);
            case STACK -> player.drop(true);
            case ALL -> {
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (!stack.isEmpty()) {
                        player.drop(stack.copyAndClear(), false, true);
                    }
                }
            }
        }
        syncEquipment(player);
    }

    public static void dropSlot(ServerPlayer player, DropMode mode, int slot) {
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack.isEmpty()) {
            return;
        }

        ItemStack dropped = mode == DropMode.STACK ? player.getInventory().removeItemNoUpdate(slot) : player.getInventory().removeItem(slot, 1);
        player.drop(dropped, false, true);
        player.getInventory().setChanged();
        syncEquipment(player);
    }

    public static void swapHands(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        ItemStack mainHand = player.getMainHandItem().copy();
        ItemStack offHand = player.getOffhandItem().copy();
        inventory.setItem(inventory.getSelectedSlot(), offHand);
        inventory.setItem(Inventory.SLOT_OFFHAND, mainHand);
        syncEquipment(player);
    }

    public static void setPersistent(ServerPlayer player, boolean persistent) {
        state(player).ifPresent(fake -> fake.persistent = persistent);
        String key = player.getScoreboardName().toLowerCase(Locale.ROOT);
        if (persistent) {
            PERSISTENT_PLAYERS.put(key, PersistentPlayerData.from(player, state(player).map(ChomFakePlayerState::ownerName).orElse("server")));
        } else {
            PERSISTENT_PLAYERS.remove(key);
        }
        savePersistentPlayers();
    }

    public static void openInventory(ServerPlayer viewer, ServerPlayer target) {
        viewer.openMenu(new SimpleMenuProvider(
                (syncId, inventory, player) -> ChestMenu.sixRows(syncId, inventory, new PlayerInventoryView(target)),
                Component.literal(target.getScoreboardName() + " inventory")
        ));
    }

    public static boolean mount(ServerPlayer player) {
        Entity vehicle = player.level().getEntities(player, player.getBoundingBox().inflate(3.0D), EntitySelector.ENTITY_STILL_ALIVE)
                .stream()
                .filter(entity -> entity != player && entity.isPickable())
                .min((left, right) -> Double.compare(player.distanceToSqr(left), player.distanceToSqr(right)))
                .orElse(null);
        return vehicle != null && player.startRiding(vehicle, true, true);
    }

    public static void dismount(ServerPlayer player) {
        player.removeVehicle();
    }

    private static void tick(MinecraftServer server) {
        Iterator<ChomFakePlayerState> iterator = PLAYERS.values().iterator();
        while (iterator.hasNext()) {
            ChomFakePlayerState state = iterator.next();
            if (state.player().isRemoved() || state.player().isDeadOrDying() || !state.player().isAlive()) {
                iterator.remove();
                handleRemovedPlayer(server, state);
            }
        }
        for (ChomFakePlayerState state : PLAYERS.values()) {
            state.tick();
        }

        tickCounter++;
        if (tickCounter % 100 == 0) {
            boolean changed = false;
            for (ChomFakePlayerState state : PLAYERS.values()) {
                if (state.persistent) {
                    PERSISTENT_PLAYERS.put(state.player().getScoreboardName().toLowerCase(Locale.ROOT), PersistentPlayerData.from(state.player(), state.ownerName()));
                    changed = true;
                }
            }
            if (changed) {
                savePersistentPlayers();
            }
        }
    }

    public static Optional<ChomFakePlayerState> state(ServerPlayer player) {
        return Optional.ofNullable(PLAYERS.get(player.getUUID()));
    }

    private static UUID fakeUuid(String name) {
        return UUID.nameUUIDFromBytes(("ChomagerieFakePlayer:" + name.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
    }

    private static void handleRemovedPlayer(MinecraftServer server, ChomFakePlayerState state) {
        removeTabOwnerTag(server.getScoreboard(), state.player());
        state.releaseChunkTickets();
        Pl3xMapCompat.notifyQuit(state.player());
        server.getPlayerList().remove(state.player());
        if (state.persistent) {
            PersistentPlayerData data = PersistentPlayerData.from(state.player(), state.ownerName());
            PERSISTENT_PLAYERS.put(state.player().getScoreboardName().toLowerCase(Locale.ROOT), data);
            savePersistentPlayers();
            ServerLevel level = levelFromId(server, data.dimension).orElse(server.overworld());
            GameProfile profile = resolveProfile(server, data.name);
            ServerPlayer respawned = spawn(server, level, profile, new Vec3(data.x, data.y, data.z), data.yaw, data.pitch, GameType.byName(data.gameMode, GameType.SURVIVAL), null, data.ownerName);
            state(respawned).ifPresent(fake -> fake.persistent = true);
        }
    }

    private static void syncEquipment(ServerPlayer player) {
        ArrayList<Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
        equipment.add(Pair.of(EquipmentSlot.MAINHAND, player.getMainHandItem().copy()));
        equipment.add(Pair.of(EquipmentSlot.OFFHAND, player.getOffhandItem().copy()));
        equipment.add(Pair.of(EquipmentSlot.HEAD, player.getItemBySlot(EquipmentSlot.HEAD).copy()));
        equipment.add(Pair.of(EquipmentSlot.CHEST, player.getItemBySlot(EquipmentSlot.CHEST).copy()));
        equipment.add(Pair.of(EquipmentSlot.LEGS, player.getItemBySlot(EquipmentSlot.LEGS).copy()));
        equipment.add(Pair.of(EquipmentSlot.FEET, player.getItemBySlot(EquipmentSlot.FEET).copy()));
        player.level().getChunkSource().sendToTrackingPlayersAndSelf(player, new ClientboundSetEquipmentPacket(player.getId(), equipment));
    }

    private static void applyTabOwnerTag(ServerScoreboard scoreboard, ServerPlayer player, String ownerName) {
        String safeOwnerName = ownerName == null || ownerName.isBlank() ? "server" : ownerName;
        String teamName = TAB_TEAM_PREFIX + player.getUUID().toString().replace("-", "").substring(0, 16);
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
        }

        team.setPlayerPrefix(Component.literal("[" + safeOwnerName + "] "));
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        scoreboard.onTeamChanged(team);
    }

    private static void removeTabOwnerTag(ServerScoreboard scoreboard, ServerPlayer player) {
        for (PlayerTeam team : scoreboard.getPlayerTeams()) {
            if (team.getName().startsWith(TAB_TEAM_PREFIX) && team.getPlayers().contains(player.getScoreboardName())) {
                scoreboard.removePlayerFromTeam(player.getScoreboardName(), team);
                if (team.getPlayers().isEmpty()) {
                    scoreboard.removePlayerTeam(team);
                }
                return;
            }
        }
    }

    private static GameProfile resolveProfile(MinecraftServer server, String name) {
        try {
            Optional<com.mojang.authlib.yggdrasil.response.NameAndId> nameAndId = server.services().profileRepository().findProfileByName(name);
            if (nameAndId.isPresent()) {
                ProfileResult result = server.services().sessionService().fetchProfile(nameAndId.get().id(), true);
                if (result != null) {
                    return result.profile();
                }
                return new GameProfile(nameAndId.get().id(), nameAndId.get().name());
            }
        } catch (Exception ignored) {
        }

        return new GameProfile(fakeUuid(name), name);
    }

    private static void loadPersistentPlayers() {
        try {
            Files.createDirectories(CONFIG_DIR);
            if (!Files.exists(PERSISTENT_PLAYERS_PATH)) {
                return;
            }

            Map<String, PersistentPlayerData> loaded = GSON.fromJson(
                    Files.readString(PERSISTENT_PLAYERS_PATH),
                    new TypeToken<Map<String, PersistentPlayerData>>() {
                    }.getType()
            );
            if (loaded != null) {
                PERSISTENT_PLAYERS.clear();
                PERSISTENT_PLAYERS.putAll(loaded);
            }
        } catch (IOException ignored) {
        }
    }

    private static void savePersistentPlayers() {
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(PERSISTENT_PLAYERS_PATH, GSON.toJson(PERSISTENT_PLAYERS));
        } catch (IOException ignored) {
        }
    }

    private static void loadRealPlayers(MinecraftServer server) {
        if (loadedRealPlayers) {
            return;
        }

        loadedRealPlayers = true;
        try {
            Files.createDirectories(CONFIG_DIR);
            if (Files.exists(REAL_PLAYERS_PATH)) {
                Set<String> loaded = GSON.fromJson(
                        Files.readString(REAL_PLAYERS_PATH),
                        new TypeToken<Set<String>>() {
                        }.getType()
                );
                if (loaded != null) {
                    for (String name : loaded) {
                        if (name != null && !name.isBlank()) {
                            REAL_PLAYER_NAMES.add(name.toLowerCase(Locale.ROOT));
                        }
                    }
                }
            }
        } catch (IOException ignored) {
        }

        loadRealPlayersFromUserCache(server);
        saveRealPlayers();
    }

    private static void loadRealPlayersFromUserCache(MinecraftServer server) {
        Path userCachePath = server.getServerDirectory().resolve("usercache.json");
        if (!Files.exists(userCachePath)) {
            return;
        }

        try {
            JsonArray entries = GSON.fromJson(Files.readString(userCachePath), JsonArray.class);
            if (entries == null) {
                return;
            }

            for (JsonElement entry : entries) {
                if (!entry.isJsonObject()) {
                    continue;
                }

                JsonObject object = entry.getAsJsonObject();
                JsonElement name = object.get("name");
                if (name != null && name.isJsonPrimitive()) {
                    REAL_PLAYER_NAMES.add(name.getAsString().toLowerCase(Locale.ROOT));
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private static void saveRealPlayers() {
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(REAL_PLAYERS_PATH, GSON.toJson(REAL_PLAYER_NAMES));
        } catch (IOException ignored) {
        }
    }

    private static Optional<ServerLevel> levelFromId(MinecraftServer server, String dimension) {
        Identifier identifier = Identifier.tryParse(dimension);
        if (identifier == null) {
            return Optional.empty();
        }

        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, identifier);
        return Optional.ofNullable(server.getLevel(key));
    }

    public enum ChomPlayerAction {
        ATTACK,
        USE
    }

    public enum ActionSchedule {
        ONCE,
        CONTINUOUS,
        INTERVAL
    }

    public enum MoveDirection {
        FORWARD,
        BACKWARD,
        LEFT,
        RIGHT,
        NONE
    }

    public enum DropMode {
        SINGLE,
        STACK,
        ALL
    }

    public static final class ChomFakePlayerState {
        private final ServerPlayer player;
        private final String ownerName;
        private final Map<ChomPlayerAction, ScheduledAction> actions = new HashMap<>();
        private MoveDirection moveDirection = MoveDirection.NONE;
        private ChunkPos ticketChunk;
        private boolean persistent;

        private ChomFakePlayerState(ServerPlayer player, String ownerName) {
            this.player = player;
            this.ownerName = ownerName == null || ownerName.isBlank() ? "server" : ownerName;
        }

        public ServerPlayer player() {
            return player;
        }

        public String ownerName() {
            return ownerName;
        }

        private void setAction(ChomPlayerAction action, ActionSchedule schedule) {
            setAction(action, schedule, 1);
        }

        public void setAction(ChomPlayerAction action, ActionSchedule schedule, int interval) {
            actions.put(action, new ScheduledAction(schedule, Math.max(1, interval)));
        }

        private void move(MoveDirection direction) {
            moveDirection = direction;
            refreshInput();
        }

        private void stop() {
            actions.clear();
            moveDirection = MoveDirection.NONE;
            player.setJumping(false);
            refreshInput();
        }

        private void refreshInput() {
            player.setLastClientInput(new Input(
                    moveDirection == MoveDirection.FORWARD,
                    moveDirection == MoveDirection.BACKWARD,
                    moveDirection == MoveDirection.LEFT,
                    moveDirection == MoveDirection.RIGHT,
                    false,
                    player.isShiftKeyDown(),
                    player.isSprinting()
            ));
        }

        private void tick() {
            refreshInput();
            updateChunkTickets();
            movePlayer();
            pickUpItems();
            actions.entrySet().removeIf(entry -> runScheduled(entry.getKey(), entry.getValue()));
        }

        private void updateChunkTickets() {
            ChunkPos currentChunk = player.chunkPosition();
            if (currentChunk.equals(ticketChunk)) {
                return;
            }

            releaseChunkTickets();
            ticketChunk = currentChunk;
            int loadingRadius = Math.max(2, player.level().getServer().getPlayerList().getViewDistance());
            int simulationRadius = Math.max(2, player.level().getServer().getPlayerList().getSimulationDistance());
            player.level().getChunkSource().addTicketWithRadius(TicketType.PLAYER_LOADING, ticketChunk, loadingRadius);
            player.level().getChunkSource().addTicketWithRadius(TicketType.PLAYER_SIMULATION, ticketChunk, simulationRadius);
        }

        private void releaseChunkTickets() {
            if (ticketChunk == null || player.level().getServer() == null) {
                return;
            }

            int loadingRadius = Math.max(2, player.level().getServer().getPlayerList().getViewDistance());
            int simulationRadius = Math.max(2, player.level().getServer().getPlayerList().getSimulationDistance());
            player.level().getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, ticketChunk, loadingRadius);
            player.level().getChunkSource().removeTicketWithRadius(TicketType.PLAYER_SIMULATION, ticketChunk, simulationRadius);
            ticketChunk = null;
        }

        private void movePlayer() {
            if (moveDirection == MoveDirection.NONE) {
                return;
            }

            double speed = player.isShiftKeyDown() ? 0.035D : player.isSprinting() ? 0.14D : 0.1D;
            double yawRadians = Math.toRadians(player.getYRot());
            double forwardX = -Math.sin(yawRadians);
            double forwardZ = Math.cos(yawRadians);
            double rightX = Math.cos(yawRadians);
            double rightZ = Math.sin(yawRadians);
            double x = 0.0D;
            double z = 0.0D;

            switch (moveDirection) {
                case FORWARD -> {
                    x = forwardX;
                    z = forwardZ;
                }
                case BACKWARD -> {
                    x = -forwardX;
                    z = -forwardZ;
                }
                case LEFT -> {
                    x = -rightX;
                    z = -rightZ;
                }
                case RIGHT -> {
                    x = rightX;
                    z = rightZ;
                }
                case NONE -> {
                }
            }

            player.move(MoverType.PLAYER, new Vec3(x * speed, 0.0D, z * speed));
        }

        private void pickUpItems() {
            for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(1.0D), entity -> entity instanceof ItemEntity && entity.isAlive())) {
                ((ItemEntity) entity).playerTouch(player);
            }
        }

        private boolean runScheduled(ChomPlayerAction action, ScheduledAction scheduledAction) {
            boolean shouldRun = switch (scheduledAction.schedule()) {
                case ONCE -> scheduledAction.tick() == 0;
                case CONTINUOUS -> true;
                case INTERVAL -> scheduledAction.tick() % scheduledAction.interval() == 0;
            };

            if (shouldRun) {
                perform(action);
            }

            scheduledAction.increment();
            return scheduledAction.schedule() == ActionSchedule.ONCE && scheduledAction.tick() > 0;
        }

        private void perform(ChomPlayerAction action) {
            switch (action) {
                case ATTACK -> attack();
                case USE -> use();
            }
        }

        private void attack() {
            EntityHitResult hit = entityHitResult();
            if (hit != null) {
                player.attack(hit.getEntity());
                player.swing(InteractionHand.MAIN_HAND);
            }
        }

        private void use() {
            EntityHitResult entityHitResult = entityHitResult();
            if (entityHitResult != null && player.isWithinEntityInteractionRange(entityHitResult.getEntity(), 1.0D)) {
                Vec3 hitLocation = entityHitResult.getLocation().subtract(entityHitResult.getEntity().position());
                InteractionResult result = player.interactOn(entityHitResult.getEntity(), InteractionHand.MAIN_HAND, hitLocation);
                if (result.consumesAction()) {
                    player.swing(InteractionHand.MAIN_HAND);
                    return;
                }
            }

            HitResult hit = player.pick(REACH, 1.0F, false);
            InteractionResult result = InteractionResult.PASS;
            if (hit instanceof BlockHitResult blockHitResult && hit.getType() == HitResult.Type.BLOCK) {
                result = player.gameMode.useItemOn(player, player.level(), player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, blockHitResult);
            }

            if (!result.consumesAction()) {
                result = player.gameMode.useItem(player, player.level(), player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND);
            }

            if (result.consumesAction()) {
                player.swing(InteractionHand.MAIN_HAND);
            }
        }

        private EntityHitResult entityHitResult() {
            Vec3 start = player.getEyePosition();
            Vec3 end = start.add(player.getViewVector(1.0F).scale(REACH));
            return net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                    player.level(),
                    player,
                    start,
                    end,
                    player.getBoundingBox().expandTowards(player.getViewVector(1.0F).scale(REACH)).inflate(1.0D),
                    entity -> entity != player && entity.isPickable() && EntitySelector.NO_SPECTATORS.test(entity),
                    0.3F
            );
        }
    }

    private static final class ScheduledAction {
        private final ActionSchedule schedule;
        private final int interval;
        private int tick;

        private ScheduledAction(ActionSchedule schedule, int interval) {
            this.schedule = schedule;
            this.interval = interval;
        }

        private ActionSchedule schedule() {
            return schedule;
        }

        private int interval() {
            return interval;
        }

        private int tick() {
            return tick;
        }

        private void increment() {
            tick++;
        }
    }

    private static final class PlayerInventoryView implements Container {
        private static final int MENU_SIZE = 54;
        private static final ItemStack BLOCKED_SLOT = blockedSlot();
        private final ServerPlayer target;

        private PlayerInventoryView(ServerPlayer target) {
            this.target = target;
        }

        @Override
        public int getContainerSize() {
            return MENU_SIZE;
        }

        @Override
        public boolean isEmpty() {
            return target.getInventory().isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            int inventorySlot = inventorySlot(slot);
            return inventorySlot >= 0 ? target.getInventory().getItem(inventorySlot) : BLOCKED_SLOT.copy();
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            int inventorySlot = inventorySlot(slot);
            return inventorySlot >= 0 ? target.getInventory().removeItem(inventorySlot, amount) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            int inventorySlot = inventorySlot(slot);
            return inventorySlot >= 0 ? target.getInventory().removeItemNoUpdate(inventorySlot) : ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            int inventorySlot = inventorySlot(slot);
            if (inventorySlot >= 0) {
                target.getInventory().setItem(inventorySlot, stack);
            }
        }

        @Override
        public void setChanged() {
            target.getInventory().setChanged();
            syncEquipment(target);
        }

        @Override
        public boolean stillValid(Player player) {
            return !target.isRemoved();
        }

        @Override
        public void clearContent() {
            target.getInventory().clearContent();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return inventorySlot(slot) >= 0;
        }

        @Override
        public boolean canTakeItem(Container targetContainer, int slot, ItemStack stack) {
            return inventorySlot(slot) >= 0;
        }

        private int inventorySlot(int slot) {
            if (slot >= 0 && slot <= 3) {
                return 39 - slot;
            }
            if (slot == 8) {
                return Inventory.SLOT_OFFHAND;
            }
            if (slot >= 9 && slot <= 35) {
                return slot;
            }
            if (slot >= 45 && slot <= 53) {
                return slot - 45;
            }
            return -1;
        }

        private static ItemStack blockedSlot() {
            ItemStack stack = new ItemStack(Items.STAINED_GLASS_PANE.black());
            stack.set(net.minecraft.core.component.DataComponents.ITEM_NAME, Component.literal(" "));
            return stack;
        }
    }

    private static final class PersistentPlayerData {
        private String name;
        private String dimension;
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;
        private String gameMode;
        private String ownerName;

        private static PersistentPlayerData from(ServerPlayer player, String ownerName) {
            PersistentPlayerData data = new PersistentPlayerData();
            data.name = player.getScoreboardName();
            data.dimension = player.level().dimension().identifier().toString();
            data.x = player.getX();
            data.y = player.getY();
            data.z = player.getZ();
            data.yaw = player.getYRot();
            data.pitch = player.getXRot();
            data.gameMode = player.gameMode().getName();
            data.ownerName = ownerName;
            return data;
        }
    }
}
