package tech.maloandre.chomagerie.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import tech.maloandre.chomagerie.config.ServerConfig;
import tech.maloandre.chomagerie.network.TeamManageRequestPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

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

    private static PlayerTeam addServerTeam(ServerScoreboard scoreboard, String teamName) throws CommandSyntaxException {
        if (scoreboard.getPlayerTeam(teamName) != null) {
            throw TEAM_ALREADY_EXISTS.create();
        }
        return scoreboard.addPlayerTeam(teamName);
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
            return gradientTag(gradient.text(), gradient.colors());
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

    private static MutableComponent gradientTag(String text, int[] colors) {
        MutableComponent result = Component.empty();
        String visibleText = stripColorCodes(text);
        int startRgb = colors[0];
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
                int rgb = colorableCharacters <= 1 ? startRgb : interpolateGradient(colors, 0.0F);
                result.append(Component.literal(String.valueOf(current)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb))));
                continue;
            }

            float progress = (float) colorIndex / (float) (colorableCharacters - 1);
            int rgb = interpolateGradient(colors, progress);
            result.append(Component.literal(String.valueOf(current)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb))));
            colorIndex++;
        }
        return result;
    }

    private static int interpolateGradient(int[] colors, float progress) {
        if (colors.length == 1) {
            return colors[0];
        }

        float scaledProgress = progress * (colors.length - 1);
        int startIndex = Math.min((int) scaledProgress, colors.length - 2);
        float localProgress = scaledProgress - startIndex;
        return interpolateColor(colors[startIndex], colors[startIndex + 1], localProgress);
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

        String colorsText = text.substring(GRADIENT_PREFIX.length(), endMarker);
        String[] rawColors = colorsText.split(":");
        if (rawColors.length < 2) {
            return null;
        }

        try {
            int[] colors = new int[rawColors.length];
            for (int i = 0; i < rawColors.length; i++) {
                colors[i] = parseHexColor(normalizeHexColor(rawColors[i]));
            }

            String gradientText = text.substring(endMarker + 1);
            return new GradientFormat(colors, gradientText);
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

    private record GradientFormat(int[] colors, String text) {
    }
}
