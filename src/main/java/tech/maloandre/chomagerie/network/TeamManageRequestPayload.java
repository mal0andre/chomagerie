package tech.maloandre.chomagerie.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import tech.maloandre.chomagerie.Chomagerie;
import tech.maloandre.chomagerie.command.ChomagerieTeamCommand;

public record TeamManageRequestPayload(
        Action action,
        String teamName,
        String value
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TeamManageRequestPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.parse(Chomagerie.MOD_ID + ":team_manage_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TeamManageRequestPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeEnum(value.action());
                buf.writeUtf(value.teamName());
                buf.writeUtf(value.value());
            },
            buf -> new TeamManageRequestPayload(buf.readEnum(Action.class), buf.readUtf(), buf.readUtf())
    );

    public static void registerServerHandler() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!player.level().getServer().getPlayerList().isOp(new NameAndId(player.getUUID(), player.getGameProfile().name()))) {
                Chomagerie.LOGGER.warn("Player {} tried to manage teams without op permission", player.getName().getString());
                return;
            }

            ServerScoreboard scoreboard = player.level().getServer().getScoreboard();
            ChomagerieTeamCommand.applyTeamManagementPayload(scoreboard, payload.action(), payload.teamName(), payload.value());
            ServerPlayNetworking.send(player, TeamManageSyncPayload.fromScoreboard(scoreboard));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public enum Action {
        LIST,
        ADD,
        REMOVE,
        DISPLAY,
        PREFIX,
        SUFFIX
    }
}
