package tech.maloandre.chomagerie.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import tech.maloandre.chomagerie.config.ServerConfig;
import tech.maloandre.chomagerie.network.TeamManageRequestPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ChomagerieTeamCommand {
    private static final String TEAM_PREFIX = "chom_";
    private static final int MAX_TAG_LENGTH = 16;
    private static final char COLOR_CODE = '\u00a7';
    private static final String GRADIENT_PREFIX = "<gradient:";
    private static final SimpleCommandExceptionType EMPTY_TAG =
            new SimpleCommandExceptionType(Component.literal("Le tag ne peut pas etre vide."));
    private static final SimpleCommandExceptionType TAG_TOO_LONG =
            new SimpleCommandExceptionType(Component.literal("Le tag doit faire " + MAX_TAG_LENGTH + " caracteres maximum."));
    private static final SimpleCommandExceptionType INVALID_GRADIENT =
            new SimpleCommandExceptionType(Component.literal("Gradient invalide. Utilise: #977272 #E32B2B tag"));
    private static final SimpleCommandExceptionType TEAM_NOT_FOUND =
            new SimpleCommandExceptionType(Component.literal("Team introuvable."));
    private static final SimpleCommandExceptionType TEAM_ALREADY_EXISTS =
            new SimpleCommandExceptionType(Component.literal("Cette team existe deja."));

    private ChomagerieTeamCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(teamRoot("chomteam"));
        dispatcher.register(teamRoot("teamtag"));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> teamRoot(String name) {
        return Commands.literal(name)
                .then(Commands.literal("set")
                        .then(Commands.argument("tag", StringArgumentType.greedyString())
                                .executes(ChomagerieTeamCommand::setTag)))
                .then(Commands.literal("gradient")
                        .then(Commands.argument("start", StringArgumentType.word())
                                .then(Commands.argument("end", StringArgumentType.word())
                                        .then(Commands.argument("tag", StringArgumentType.greedyString())
                                                .executes(ChomagerieTeamCommand::setGradientTag)))))
                .then(Commands.literal("clear")
                        .executes(ChomagerieTeamCommand::clearTag))
                .then(Commands.literal("status")
                        .executes(ChomagerieTeamCommand::showStatus))
                .then(Commands.literal("manage")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("list")
                                .executes(ChomagerieTeamCommand::listServerTeams))
                        .then(Commands.literal("add")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .executes(ChomagerieTeamCommand::addServerTeam)))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("team", StringArgumentType.string())
                                        .suggests(ChomagerieTeamCommand::suggestServerTeamDisplayNames)
                                        .executes(ChomagerieTeamCommand::removeServerTeam)))
                        .then(Commands.literal("display")
                                .then(Commands.argument("team", StringArgumentType.string())
                                        .suggests(ChomagerieTeamCommand::suggestServerTeamDisplayNames)
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(context -> updateServerTeamText(context, TeamTextTarget.DISPLAY)))))
                        .then(Commands.literal("prefix")
                                .then(Commands.argument("team", StringArgumentType.string())
                                        .suggests(ChomagerieTeamCommand::suggestServerTeamDisplayNames)
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(context -> updateServerTeamText(context, TeamTextTarget.PREFIX)))))
                        .then(Commands.literal("suffix")
                                .then(Commands.argument("team", StringArgumentType.string())
                                        .suggests(ChomagerieTeamCommand::suggestServerTeamDisplayNames)
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(context -> updateServerTeamText(context, TeamTextTarget.SUFFIX))))));
    }

    private static int setTag(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String tag = normalizeTag(StringArgumentType.getString(context, "tag"));
        applyTeamTag(context.getSource().getServer().getScoreboard(), player, tag);

        context.getSource().sendSuccess(
                () -> Component.literal("[Chomagerie] Tag defini sur ")
                        .withStyle(ChatFormatting.GREEN)
                        .append(formatTagPrefix(tag)),
                false
        );
        return 1;
    }

    private static int setGradientTag(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String start = normalizeHexColor(StringArgumentType.getString(context, "start"));
        String end = normalizeHexColor(StringArgumentType.getString(context, "end"));
        String tag = GRADIENT_PREFIX + start + ":" + end + ">" + StringArgumentType.getString(context, "tag");
        tag = normalizeTag(tag);
        String formattedTag = tag;

        applyTeamTag(context.getSource().getServer().getScoreboard(), player, tag);
        context.getSource().sendSuccess(
                () -> Component.literal("[Chomagerie] Tag gradient defini sur ")
                        .withStyle(ChatFormatting.GREEN)
                        .append(formatTagPrefix(formattedTag)),
                false
        );
        return 1;
    }

    private static int clearTag(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean removed = clearTeamTag(context.getSource().getServer().getScoreboard(), player);

        if (!removed) {
            context.getSource().sendFailure(Component.literal("[Chomagerie] Tu n'as pas de tag Chomagerie."));
            return 0;
        }

        context.getSource().sendSuccess(
                () -> Component.literal("[Chomagerie] Tag retire.").withStyle(ChatFormatting.YELLOW),
                false
        );
        return 1;
    }

    private static int showStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Team currentTeam = context.getSource().getServer().getScoreboard().getPlayersTeam(player.getScoreboardName());

        if (currentTeam instanceof PlayerTeam playerTeam && playerTeam.getName().startsWith(TEAM_PREFIX)) {
            context.getSource().sendSuccess(
                    () -> Component.literal("[Chomagerie] Tag actuel: ")
                            .withStyle(ChatFormatting.YELLOW)
                            .append(playerTeam.getPlayerPrefix()),
                    false
            );
            return 1;
        }

        context.getSource().sendSuccess(
                () -> Component.literal("[Chomagerie] Aucun tag defini.").withStyle(ChatFormatting.YELLOW),
                false
        );
        return 0;
    }

    public static void applyTeamTag(ServerScoreboard scoreboard, ServerPlayer player, String rawTag) throws CommandSyntaxException {
        String tag = normalizeTag(rawTag);
        String teamName = teamNameFor(player.getUUID());
        cleanupPlayerChomagerieTeams(scoreboard, player);

        PlayerTeam team = getOrCreateTeam(scoreboard, teamName, tag);
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        ServerConfig.getInstance().setTeamTagIdentity(player.getUUID(), player.getScoreboardName(), teamName);
    }

    public static boolean clearTeamTag(ServerScoreboard scoreboard, ServerPlayer player) {
        boolean removed = cleanupPlayerChomagerieTeams(scoreboard, player);
        ServerConfig.getInstance().clearTeamTagIdentity(player.getUUID());
        return removed;
    }

    public static void applyTeamManagementPayload(ServerScoreboard scoreboard, TeamManageRequestPayload.Action action, String teamName, String value) {
        try {
            switch (action) {
                case LIST -> {
                }
                case ADD -> addServerTeam(scoreboard, teamName);
                case REMOVE -> scoreboard.removePlayerTeam(requireServerTeam(scoreboard, teamName));
                case DISPLAY -> requireServerTeam(scoreboard, teamName).setDisplayName(parseColorCodes(value));
                case PREFIX -> requireServerTeam(scoreboard, teamName).setPlayerPrefix(parseColorCodes(value));
                case SUFFIX -> requireServerTeam(scoreboard, teamName).setPlayerSuffix(parseColorCodes(value));
            }

            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team != null) {
                scoreboard.onTeamChanged(team);
            }
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static PlayerTeam getOrCreateTeam(ServerScoreboard scoreboard, String teamName, String tag) {
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);

        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
        }

        team.setDisplayName(parseColorCodes(tag));
        team.setPlayerPrefix(formatTagPrefix(tag));
        team.setColor(Optional.empty());
        team.setAllowFriendlyFire(true);
        team.setSeeFriendlyInvisibles(false);
        scoreboard.onTeamChanged(team);
        return team;
    }

    private static boolean cleanupPlayerChomagerieTeams(ServerScoreboard scoreboard, ServerPlayer player) {
        ServerConfig config = ServerConfig.getInstance();
        String currentPlayerName = player.getScoreboardName();
        String previousPlayerName = config.getTeamTagPlayerName(player.getUUID());
        boolean removed = false;

        List<PlayerTeam> teams = new ArrayList<>(scoreboard.getPlayerTeams());
        for (PlayerTeam team : teams) {
            if (!team.getName().startsWith(TEAM_PREFIX)) {
                continue;
            }

            if (team.getPlayers().contains(currentPlayerName)) {
                scoreboard.removePlayerFromTeam(currentPlayerName, team);
                removed = true;
            }
            if (!previousPlayerName.isBlank() && !previousPlayerName.equals(currentPlayerName) && team.getPlayers().contains(previousPlayerName)) {
                scoreboard.removePlayerFromTeam(previousPlayerName, team);
                removed = true;
            }
            if (team.getPlayers().isEmpty()) {
                scoreboard.removePlayerTeam(team);
            }
        }

        return removed;
    }

    private static int listServerTeams(CommandContext<CommandSourceStack> context) {
        ServerScoreboard scoreboard = context.getSource().getServer().getScoreboard();
        if (scoreboard.getPlayerTeams().isEmpty()) {
            context.getSource().sendSuccess(
                    () -> Component.literal("[Chomagerie] Aucune team scoreboard.").withStyle(ChatFormatting.YELLOW),
                    false
            );
            return 0;
        }

        MutableComponent message = Component.literal("[Chomagerie] Teams: ").withStyle(ChatFormatting.YELLOW);
        boolean first = true;
        for (PlayerTeam team : scoreboard.getPlayerTeams()) {
            if (!first) {
                message.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            message.append(Component.literal(getServerTeamDisplayName(team)).withStyle(ChatFormatting.AQUA));
            first = false;
        }
        context.getSource().sendSuccess(() -> message, false);
        return scoreboard.getPlayerTeams().size();
    }

    private static int addServerTeam(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerScoreboard scoreboard = context.getSource().getServer().getScoreboard();
        String teamName = StringArgumentType.getString(context, "team");
        if (scoreboard.getPlayerTeam(teamName) != null) {
            throw TEAM_ALREADY_EXISTS.create();
        }

        addServerTeam(scoreboard, teamName);
        context.getSource().sendSuccess(
                () -> Component.literal("[Chomagerie] Team creee: " + teamName).withStyle(ChatFormatting.GREEN),
                true
        );
        return 1;
    }

    private static PlayerTeam addServerTeam(ServerScoreboard scoreboard, String teamName) throws CommandSyntaxException {
        if (scoreboard.getPlayerTeam(teamName) != null) {
            throw TEAM_ALREADY_EXISTS.create();
        }
        return scoreboard.addPlayerTeam(teamName);
    }

    private static int removeServerTeam(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerScoreboard scoreboard = context.getSource().getServer().getScoreboard();
        PlayerTeam team = getServerTeam(context, scoreboard);
        scoreboard.removePlayerTeam(team);
        context.getSource().sendSuccess(
                () -> Component.literal("[Chomagerie] Team supprimee: " + team.getName()).withStyle(ChatFormatting.YELLOW),
                true
        );
        return 1;
    }

    private static int updateServerTeamText(CommandContext<CommandSourceStack> context, TeamTextTarget target) throws CommandSyntaxException {
        ServerScoreboard scoreboard = context.getSource().getServer().getScoreboard();
        PlayerTeam team = getServerTeam(context, scoreboard);
        String text = StringArgumentType.getString(context, "text");
        MutableComponent formatted = parseColorCodes(text);

        switch (target) {
            case DISPLAY -> team.setDisplayName(formatted);
            case PREFIX -> team.setPlayerPrefix(formatted);
            case SUFFIX -> team.setPlayerSuffix(formatted);
        }

        scoreboard.onTeamChanged(team);
        context.getSource().sendSuccess(
                () -> Component.literal("[Chomagerie] Team " + team.getName() + " mise a jour.").withStyle(ChatFormatting.GREEN),
                true
        );
        return 1;
    }

    private static PlayerTeam getServerTeam(CommandContext<CommandSourceStack> context, ServerScoreboard scoreboard) throws CommandSyntaxException {
        String team = StringArgumentType.getString(context, "team");
        return requireServerTeam(scoreboard, team);
    }

    private static PlayerTeam requireServerTeam(ServerScoreboard scoreboard, String team) throws CommandSyntaxException {
        PlayerTeam playerTeam = scoreboard.getPlayerTeam(team);
        if (playerTeam != null) {
            return playerTeam;
        }

        for (PlayerTeam candidate : scoreboard.getPlayerTeams()) {
            if (getServerTeamDisplayName(candidate).equals(team)) {
                return candidate;
            }
        }

        for (PlayerTeam candidate : scoreboard.getPlayerTeams()) {
            if (getServerTeamDisplayName(candidate).equalsIgnoreCase(team) || candidate.getName().equalsIgnoreCase(team)) {
                return candidate;
            }
        }

        throw TEAM_NOT_FOUND.create();
    }

    private static CompletableFuture<Suggestions> suggestServerTeamDisplayNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        for (PlayerTeam team : context.getSource().getServer().getScoreboard().getPlayerTeams()) {
            suggestQuoted(builder, getServerTeamDisplayName(team));
        }
        return builder.buildFuture();
    }

    private static void suggestQuoted(SuggestionsBuilder builder, String value) {
        if (value.isBlank()) {
            return;
        }

        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        String normalizedValue = value.toLowerCase(Locale.ROOT);
        if (!normalizedValue.startsWith(remaining) && !quote(value).toLowerCase(Locale.ROOT).startsWith(remaining)) {
            return;
        }

        builder.suggest(quote(value));
    }

    private static String quote(String value) {
        if (value.indexOf(' ') < 0 && value.indexOf('"') < 0 && value.indexOf('\\') < 0) {
            return value;
        }

        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String getServerTeamDisplayName(PlayerTeam team) {
        String displayName = team.getDisplayName().getString();
        if (!displayName.isBlank()) {
            return displayName;
        }
        return team.getName();
    }

    private static String normalizeTag(String rawTag) throws CommandSyntaxException {
        String tag = rawTag.trim().replaceAll("\\s+", " ");
        String visibleTag = stripColorCodes(tag);
        if (tag.isEmpty()) {
            throw EMPTY_TAG.create();
        }
        if (visibleTag.length() > MAX_TAG_LENGTH) {
            throw TAG_TOO_LONG.create();
        }
        return tag;
    }

    private static String teamNameFor(UUID playerUuid) {
        return TEAM_PREFIX + playerUuid.toString().replace("-", "").substring(0, 11);
    }

    private static MutableComponent formatTagPrefix(String tag) {
        return parseColorCodes(tag)
                .append(Component.literal(" ").withStyle(ChatFormatting.GRAY));
    }

    private static MutableComponent parseColorCodes(String text) {
        GradientFormat gradient = parseGradientFormat(text);
        if (gradient != null) {
            return gradientTag(gradient.text(), gradient.startRgb(), gradient.endRgb());
        }

        MutableComponent result = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder segment = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if ((current == '&' || current == COLOR_CODE) && i + 1 < text.length()) {
                ChatFormatting formatting = ChatFormatting.getByCode(text.charAt(i + 1));
                if (formatting != null) {
                    appendSegment(result, segment, style);
                    style = style.applyLegacyFormat(formatting);
                    i++;
                    continue;
                }
            }

            segment.append(current);
        }

        appendSegment(result, segment, style);
        return result;
    }

    private static MutableComponent gradientTag(String text, int startRgb, int endRgb) {
        MutableComponent result = Component.empty();
        String visibleText = stripColorCodes(text);
        int colorableCharacters = 0;
        for (int i = 0; i < visibleText.length(); i++) {
            if (!Character.isWhitespace(visibleText.charAt(i))) {
                colorableCharacters++;
            }
        }

        int colorIndex = 0;
        for (int i = 0; i < visibleText.length(); i++) {
            char current = visibleText.charAt(i);
            if (Character.isWhitespace(current) || colorableCharacters <= 1) {
                int rgb = colorableCharacters <= 1 ? startRgb : interpolateColor(startRgb, endRgb, 0.0F);
                result.append(Component.literal(String.valueOf(current)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb))));
                continue;
            }

            float progress = (float) colorIndex / (float) (colorableCharacters - 1);
            int rgb = interpolateColor(startRgb, endRgb, progress);
            result.append(Component.literal(String.valueOf(current)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb))));
            colorIndex++;
        }
        return result;
    }

    private static int interpolateColor(int startRgb, int endRgb, float progress) {
        int startRed = (startRgb >> 16) & 0xFF;
        int startGreen = (startRgb >> 8) & 0xFF;
        int startBlue = startRgb & 0xFF;
        int endRed = (endRgb >> 16) & 0xFF;
        int endGreen = (endRgb >> 8) & 0xFF;
        int endBlue = endRgb & 0xFF;

        int red = startRed + Math.round((endRed - startRed) * progress);
        int green = startGreen + Math.round((endGreen - startGreen) * progress);
        int blue = startBlue + Math.round((endBlue - startBlue) * progress);
        return (red << 16) | (green << 8) | blue;
    }

    private static void appendSegment(MutableComponent result, StringBuilder segment, Style style) {
        if (segment.isEmpty()) {
            return;
        }

        result.append(Component.literal(segment.toString()).withStyle(style));
        segment.setLength(0);
    }

    private static String stripColorCodes(String text) {
        text = stripGradientFormat(text);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if ((current == '&' || current == COLOR_CODE) && i + 1 < text.length()
                    && ChatFormatting.getByCode(text.charAt(i + 1)) != null) {
                i++;
                continue;
            }

            result.append(current);
        }
        return result.toString();
    }

    private static GradientFormat parseGradientFormat(String text) {
        if (!text.startsWith(GRADIENT_PREFIX)) {
            return null;
        }

        int endMarker = text.indexOf('>');
        if (endMarker < 0) {
            return null;
        }

        String colors = text.substring(GRADIENT_PREFIX.length(), endMarker);
        int separator = colors.indexOf(':');
        if (separator < 0) {
            return null;
        }

        try {
            String start = normalizeHexColor(colors.substring(0, separator));
            String end = normalizeHexColor(colors.substring(separator + 1));
            String gradientText = text.substring(endMarker + 1);
            return new GradientFormat(parseHexColor(start), parseHexColor(end), gradientText);
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    private static String stripGradientFormat(String text) {
        GradientFormat gradient = parseGradientFormat(text);
        return gradient == null ? text : gradient.text();
    }

    private static String normalizeHexColor(String value) throws CommandSyntaxException {
        if (value == null) {
            throw INVALID_GRADIENT.create();
        }

        String normalized = value.trim();
        if (!normalized.startsWith("#")) {
            normalized = "#" + normalized;
        }
        if (normalized.length() != 7) {
            throw INVALID_GRADIENT.create();
        }
        for (int i = 1; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            boolean hex = (current >= '0' && current <= '9')
                    || (current >= 'a' && current <= 'f')
                    || (current >= 'A' && current <= 'F');
            if (!hex) {
                throw INVALID_GRADIENT.create();
            }
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private static int parseHexColor(String value) {
        return Integer.parseInt(value.substring(1), 16);
    }

    private enum TeamTextTarget {
        DISPLAY,
        PREFIX,
        SUFFIX
    }

    private record GradientFormat(int startRgb, int endRgb, String text) {
    }
}
