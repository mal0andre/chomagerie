package tech.maloandre.chomagerie.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.PlayerTeam;
import tech.maloandre.chomagerie.Chomagerie;

import java.util.ArrayList;
import java.util.List;

public record TeamManageSyncPayload(List<TeamInfo> teams) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TeamManageSyncPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.parse(Chomagerie.MOD_ID + ":team_manage_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TeamManageSyncPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarInt(value.teams().size());
                for (TeamInfo team : value.teams()) {
                    buf.writeUtf(team.name());
                    buf.writeUtf(team.displayName());
                    buf.writeUtf(team.prefix());
                    buf.writeUtf(team.suffix());
                    buf.writeVarInt(team.memberCount());
                }
            },
            buf -> {
                int size = buf.readVarInt();
                List<TeamInfo> teams = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    teams.add(new TeamInfo(
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readVarInt()
                    ));
                }
                return new TeamManageSyncPayload(teams);
            }
    );

    public static TeamManageSyncPayload fromScoreboard(ServerScoreboard scoreboard) {
        List<TeamInfo> teams = new ArrayList<>();
        for (PlayerTeam team : scoreboard.getPlayerTeams()) {
            teams.add(new TeamInfo(
                    team.getName(),
                    componentString(team.getDisplayName()),
                    componentString(team.getPlayerPrefix()),
                    componentString(team.getPlayerSuffix()),
                    team.getPlayers().size()
            ));
        }
        return new TeamManageSyncPayload(teams);
    }

    private static String componentString(Component component) {
        return component == null ? "" : component.getString();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public record TeamInfo(String name, String displayName, String prefix, String suffix, int memberCount) {
    }
}
