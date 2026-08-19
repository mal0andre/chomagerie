package tech.maloandre.chomagerie.mixin;

import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.maloandre.chomagerie.command.ChomFakePlayerManager;

import java.util.ArrayList;
import java.util.List;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Shadow
    public abstract PlayerList getPlayerList();

    @Inject(method = "buildPlayerStatus", at = @At("RETURN"), cancellable = true)
    private void chomagerie$hideFakePlayersFromServerStatus(CallbackInfoReturnable<ServerStatus.Players> cir) {
        int online = 0;
        List<NameAndId> sample = new ArrayList<>();
        for (ServerPlayer player : getPlayerList().getPlayers()) {
            if (ChomFakePlayerManager.isManagedFake(player)) {
                continue;
            }

            online++;
            if (sample.size() < 12) {
                sample.add(player.allowsListing() ? player.nameAndId() : MinecraftServer.ANONYMOUS_PLAYER_PROFILE);
            }
        }

        cir.setReturnValue(new ServerStatus.Players(getPlayerList().getMaxPlayers(), online, sample));
    }
}
