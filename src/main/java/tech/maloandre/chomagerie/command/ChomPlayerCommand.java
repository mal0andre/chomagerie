package tech.maloandre.chomagerie.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CompletableFuture;

public final class ChomPlayerCommand {
    private static final String[] INTERVAL_SUGGESTIONS = {"1", "2", "4", "5", "10", "20", "40", "100"};
    private static final String[] SLOT_SUGGESTIONS = {"1", "2", "3", "4", "5", "6", "7", "8", "9"};
    private static final String[] YAW_SUGGESTIONS = {"0", "90", "180", "-90"};
    private static final String[] PITCH_SUGGESTIONS = {"0", "-30", "30", "-90", "90"};
    private static final SimpleCommandExceptionType PLAYER_NOT_FOUND =
            new SimpleCommandExceptionType(Component.literal("Chomplayer introuvable."));
    private static final SimpleCommandExceptionType PLAYER_ALREADY_EXISTS =
            new SimpleCommandExceptionType(Component.literal("Un joueur avec ce nom existe deja."));

    private ChomPlayerCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chomplayer")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(ChomPlayerCommand::suggestFakePlayers)
                        .then(spawnCommand())
                        .then(Commands.literal("shadow")
                                .executes(ChomPlayerCommand::shadow))
                        .then(Commands.literal("kill")
                                .executes(ChomPlayerCommand::kill))
                        .then(Commands.literal("stop")
                                .executes(ChomPlayerCommand::stop))
                        .then(action("attack", ChomFakePlayerManager.ChomPlayerAction.ATTACK))
                        .then(action("use", ChomFakePlayerManager.ChomPlayerAction.USE))
                        .then(Commands.literal("move")
                                .then(Commands.literal("forward").executes(context -> move(context, ChomFakePlayerManager.MoveDirection.FORWARD)))
                                .then(Commands.literal("backward").executes(context -> move(context, ChomFakePlayerManager.MoveDirection.BACKWARD)))
                                .then(Commands.literal("left").executes(context -> move(context, ChomFakePlayerManager.MoveDirection.LEFT)))
                                .then(Commands.literal("right").executes(context -> move(context, ChomFakePlayerManager.MoveDirection.RIGHT))))
                        .then(Commands.literal("look")
                                .then(Commands.literal("north").executes(context -> look(context, 180.0F, 0.0F)))
                                .then(Commands.literal("south").executes(context -> look(context, 0.0F, 0.0F)))
                                .then(Commands.literal("east").executes(context -> look(context, -90.0F, 0.0F)))
                                .then(Commands.literal("west").executes(context -> look(context, 90.0F, 0.0F)))
                                .then(Commands.literal("up").executes(context -> look(context, currentPlayer(context).getYRot(), -90.0F)))
                                .then(Commands.literal("down").executes(context -> look(context, currentPlayer(context).getYRot(), 90.0F)))
                                .then(Commands.literal("at")
                                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                                .suggests(ChomPlayerCommand::suggestCoordinates)
                                                .executes(ChomPlayerCommand::lookAt)))
                                .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(YAW_SUGGESTIONS, builder))
                                        .then(Commands.argument("pitch", FloatArgumentType.floatArg(-90.0F, 90.0F))
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(PITCH_SUGGESTIONS, builder))
                                                .executes(context -> look(
                                                        context,
                                                        FloatArgumentType.getFloat(context, "yaw"),
                                                        FloatArgumentType.getFloat(context, "pitch"))))))
                        .then(Commands.literal("turn")
                                .then(Commands.literal("left").executes(context -> turn(context, -90.0F)))
                                .then(Commands.literal("right").executes(context -> turn(context, 90.0F)))
                                .then(Commands.literal("back").executes(context -> turn(context, 180.0F)))
                                .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[]{"45", "90", "180", "-45", "-90"}, builder))
                                        .executes(context -> turn(context, FloatArgumentType.getFloat(context, "yaw")))))
                        .then(Commands.literal("sneak").executes(context -> sneak(context, true)))
                        .then(Commands.literal("unsneak").executes(context -> sneak(context, false)))
                        .then(Commands.literal("hotbar")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 9))
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(SLOT_SUGGESTIONS, builder))
                                        .executes(ChomPlayerCommand::hotbar)))
                        .then(Commands.literal("drop")
                                .executes(context -> drop(context, ChomFakePlayerManager.DropMode.SINGLE))
                                .then(Commands.literal("all").executes(context -> drop(context, ChomFakePlayerManager.DropMode.ALL)))
                                .then(Commands.literal("mainhand").executes(context -> dropSlot(context, ChomFakePlayerManager.DropMode.SINGLE, SlotTarget.MAIN_HAND)))
                                .then(Commands.literal("offhand").executes(context -> dropSlot(context, ChomFakePlayerManager.DropMode.SINGLE, SlotTarget.OFF_HAND)))
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 9))
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(SLOT_SUGGESTIONS, builder))
                                        .executes(context -> dropSlot(context, ChomFakePlayerManager.DropMode.SINGLE, SlotTarget.HOTBAR))))
                        .then(Commands.literal("dropStack")
                                .executes(context -> drop(context, ChomFakePlayerManager.DropMode.STACK))
                                .then(Commands.literal("mainhand").executes(context -> dropSlot(context, ChomFakePlayerManager.DropMode.STACK, SlotTarget.MAIN_HAND)))
                                .then(Commands.literal("offhand").executes(context -> dropSlot(context, ChomFakePlayerManager.DropMode.STACK, SlotTarget.OFF_HAND)))
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 9))
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(SLOT_SUGGESTIONS, builder))
                                        .executes(context -> dropSlot(context, ChomFakePlayerManager.DropMode.STACK, SlotTarget.HOTBAR))))
                        .then(Commands.literal("swapHands").executes(ChomPlayerCommand::swapHands))
                        .then(Commands.literal("persist")
                                .then(Commands.literal("true").executes(context -> persist(context, true)))
                                .then(Commands.literal("false").executes(context -> persist(context, false))))
                        .then(Commands.literal("inv").executes(ChomPlayerCommand::inventory))
                        .then(Commands.literal("mount").executes(ChomPlayerCommand::mount))
                        .then(Commands.literal("dismount").executes(ChomPlayerCommand::dismount))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> spawnCommand() {
        return Commands.literal("spawn")
                .executes(ChomPlayerCommand::spawn)
                .then(Commands.literal("at")
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .suggests(ChomPlayerCommand::suggestCoordinates)
                                .executes(ChomPlayerCommand::spawn)
                                .then(facingArgument())
                                .then(inArgument())
                                .then(gamemodeArgument())))
                .then(facingArgument())
                .then(inArgument())
                .then(gamemodeArgument());
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> facingArgument() {
        return Commands.literal("facing")
                .then(Commands.argument("rotation", RotationArgument.rotation())
                        .executes(ChomPlayerCommand::spawn)
                        .then(inArgument())
                        .then(gamemodeArgument()));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> inArgument() {
        return Commands.literal("in")
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .executes(ChomPlayerCommand::spawn)
                        .then(gamemodeArgument()))
                .then(Commands.argument("gamemode", GameModeArgument.gameMode())
                        .executes(ChomPlayerCommand::spawn));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> gamemodeArgument() {
        return Commands.literal("inGamemode")
                .then(Commands.argument("gamemode", GameModeArgument.gameMode())
                        .executes(ChomPlayerCommand::spawn));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> action(
            String name,
            ChomFakePlayerManager.ChomPlayerAction action
    ) {
        return Commands.literal(name)
                .then(Commands.literal("once").executes(context -> schedule(context, action, ChomFakePlayerManager.ActionSchedule.ONCE, 1)))
                .then(Commands.literal("continuous").executes(context -> schedule(context, action, ChomFakePlayerManager.ActionSchedule.CONTINUOUS, 1)))
                .then(Commands.literal("interval")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(INTERVAL_SUGGESTIONS, builder))
                                .executes(context -> schedule(context, action, ChomFakePlayerManager.ActionSchedule.INTERVAL, IntegerArgumentType.getInteger(context, "ticks")))));
    }

    private static int spawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        if (!ChomFakePlayerManager.canSpawnName(context.getSource().getServer(), name)) {
            throw PLAYER_ALREADY_EXISTS.create();
        }

        ServerPlayer source = context.getSource().getPlayerOrException();
        SpawnOptions options = SpawnOptions.fromContext(context);
        ServerPlayer player = ChomFakePlayerManager.spawn(source, name, options.level(), options.position(), options.yaw(), options.pitch(), options.gameMode(), source.getScoreboardName());
        actionBar(context, "Spawn: " + player.getScoreboardName());
        return 1;
    }

    private static int shadow(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        ServerPlayer source = context.getSource().getPlayerOrException();
        if (!source.getScoreboardName().equalsIgnoreCase(name)) {
            throw new SimpleCommandExceptionType(Component.literal("Shadow ne peut remplacer que le joueur qui execute la commande.")).create();
        }

        ChomFakePlayerManager.shadow(source);
        return 1;
    }

    private static int kill(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = currentPlayer(context);
        ChomFakePlayerManager.kill(player);
        actionBar(context, "Retire: " + player.getScoreboardName());
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = currentPlayer(context);
        ChomFakePlayerManager.stop(player);
        actionBar(context, "Stop: " + player.getScoreboardName());
        return 1;
    }

    private static int schedule(
            CommandContext<CommandSourceStack> context,
            ChomFakePlayerManager.ChomPlayerAction action,
            ChomFakePlayerManager.ActionSchedule schedule,
            int interval
    ) throws CommandSyntaxException {
        ServerPlayer player = currentPlayer(context);
        ChomFakePlayerManager.setAction(player, action, schedule, interval);
        actionBar(context, actionLabel(action) + " " + scheduleLabel(schedule) + ": " + player.getScoreboardName());
        return 1;
    }

    private static int move(CommandContext<CommandSourceStack> context, ChomFakePlayerManager.MoveDirection direction) throws CommandSyntaxException {
        ServerPlayer player = currentPlayer(context);
        ChomFakePlayerManager.move(player, direction);
        actionBar(context, "Move " + directionLabel(direction) + ": " + player.getScoreboardName());
        return 1;
    }

    private static int look(CommandContext<CommandSourceStack> context, float yaw, float pitch) throws CommandSyntaxException {
        ServerPlayer player = currentPlayer(context);
        ChomFakePlayerManager.look(player, yaw, pitch);
        actionBar(context, "Regarde: " + player.getScoreboardName());
        return 1;
    }

    private static int lookAt(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Vec3 pos = Vec3Argument.getVec3(context, "pos");
        ServerPlayer player = currentPlayer(context);
        ChomFakePlayerManager.lookAt(player, pos);
        actionBar(context, "Regarde la position: " + player.getScoreboardName());
        return 1;
    }

    private static int turn(CommandContext<CommandSourceStack> context, float yawOffset) throws CommandSyntaxException {
        ServerPlayer player = currentPlayer(context);
        ChomFakePlayerManager.turn(player, yawOffset);
        actionBar(context, "Tourne: " + player.getScoreboardName());
        return 1;
    }

    private static int sneak(CommandContext<CommandSourceStack> context, boolean enabled) throws CommandSyntaxException {
        ServerPlayer player = currentPlayer(context);
        ChomFakePlayerManager.sneak(player, enabled);
        actionBar(context, (enabled ? "Sneak: " : "Sneak off: ") + player.getScoreboardName());
        return 1;
    }

    private static int hotbar(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = currentPlayer(context);
        int slot = IntegerArgumentType.getInteger(context, "slot");
        ChomFakePlayerManager.hotbar(player, slot - 1);
        actionBar(context, "Slot " + slot + ": " + player.getScoreboardName());
        return 1;
    }

    private static int drop(CommandContext<CommandSourceStack> context, ChomFakePlayerManager.DropMode mode) throws CommandSyntaxException {
        ServerPlayer player = currentPlayer(context);
        ChomFakePlayerManager.drop(player, mode);
        actionBar(context, "Drop: " + player.getScoreboardName());
        return 1;
    }

    private static int dropSlot(CommandContext<CommandSourceStack> context, ChomFakePlayerManager.DropMode mode, SlotTarget slotTarget) throws CommandSyntaxException {
        ServerPlayer player = currentPlayer(context);
        int slot = switch (slotTarget) {
            case MAIN_HAND -> player.getInventory().getSelectedSlot();
            case OFF_HAND -> net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND;
            case HOTBAR -> IntegerArgumentType.getInteger(context, "slot") - 1;
        };
        ChomFakePlayerManager.dropSlot(player, mode, slot);
        actionBar(context, "Drop slot: " + player.getScoreboardName());
        return 1;
    }

    private static int swapHands(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = currentPlayer(context);
        ChomFakePlayerManager.swapHands(player);
        actionBar(context, "Main changee: " + player.getScoreboardName());
        return 1;
    }

    private static int persist(CommandContext<CommandSourceStack> context, boolean enabled) throws CommandSyntaxException {
        ServerPlayer player = currentPlayer(context);
        ChomFakePlayerManager.setPersistent(player, enabled);
        actionBar(context, (enabled ? "Persistant: " : "Non persistant: ") + player.getScoreboardName());
        return 1;
    }

    private static int inventory(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = currentPlayer(context);
        ChomFakePlayerManager.openInventory(context.getSource().getPlayerOrException(), player);
        actionBar(context, "Inventaire: " + player.getScoreboardName());
        return 1;
    }

    private static int mount(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = currentPlayer(context);
        if (!ChomFakePlayerManager.mount(player)) {
            actionBar(context, "Rien a monter.");
            return 0;
        }
        actionBar(context, "Monte: " + player.getScoreboardName());
        return 1;
    }

    private static int dismount(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = currentPlayer(context);
        ChomFakePlayerManager.dismount(player);
        actionBar(context, "Descend: " + player.getScoreboardName());
        return 1;
    }

    private static void actionBar(CommandContext<CommandSourceStack> context, String message) throws CommandSyntaxException {
        context.getSource().getPlayerOrException().sendSystemMessage(Component.literal(message), true);
    }

    private static String actionLabel(ChomFakePlayerManager.ChomPlayerAction action) {
        return switch (action) {
            case ATTACK -> "Attaque";
            case USE -> "Utilise";
        };
    }

    private static String scheduleLabel(ChomFakePlayerManager.ActionSchedule schedule) {
        return switch (schedule) {
            case ONCE -> "une fois";
            case CONTINUOUS -> "en boucle";
            case INTERVAL -> "intervalle";
        };
    }

    private static String directionLabel(ChomFakePlayerManager.MoveDirection direction) {
        return switch (direction) {
            case FORWARD -> "avant";
            case BACKWARD -> "arriere";
            case LEFT -> "gauche";
            case RIGHT -> "droite";
            case NONE -> "stop";
        };
    }

    private static ServerPlayer currentPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        return ChomFakePlayerManager.find(context.getSource().getServer(), name)
                .orElseThrow(PLAYER_NOT_FOUND::create);
    }

    private static CompletableFuture<Suggestions> suggestFakePlayers(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (ChomFakePlayerManager.ChomFakePlayerState state : ChomFakePlayerManager.states()) {
            String name = state.player().getScoreboardName();
            if (name.toLowerCase().startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestCoordinates(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestCoordinates(
                builder.getRemaining(),
                context.getSource().getRelevantCoordinates(),
                builder,
                value -> true
        );
    }

    private enum SlotTarget {
        MAIN_HAND,
        OFF_HAND,
        HOTBAR
    }

    private record SpawnOptions(ServerLevel level, Vec3 position, float yaw, float pitch, GameType gameMode) {
        private static SpawnOptions fromSource(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
            ServerPlayer player = context.getSource().getPlayerOrException();
            return new SpawnOptions(player.level(), player.position(), player.getYRot(), player.getXRot(), player.gameMode());
        }

        private static SpawnOptions fromContext(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
            SpawnOptions options = fromSource(context);

            try {
                options = new SpawnOptions(options.level(), Vec3Argument.getVec3(context, "pos"), options.yaw(), options.pitch(), options.gameMode());
            } catch (IllegalArgumentException ignored) {
            }

            try {
                Coordinates coordinates = RotationArgument.getRotation(context, "rotation");
                Vec2 rotation = coordinates.getRotation(context.getSource());
                options = new SpawnOptions(options.level(), options.position(), rotation.y, rotation.x, options.gameMode());
            } catch (IllegalArgumentException ignored) {
            }

            try {
                options = new SpawnOptions(DimensionArgument.getDimension(context, "dimension"), options.position(), options.yaw(), options.pitch(), options.gameMode());
            } catch (IllegalArgumentException ignored) {
            }

            try {
                options = new SpawnOptions(options.level(), options.position(), options.yaw(), options.pitch(), GameModeArgument.getGameMode(context, "gamemode"));
            } catch (IllegalArgumentException ignored) {
            }

            return options;
        }
    }
}
