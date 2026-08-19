package tech.maloandre.chomagerie.client.mixin;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.maloandre.chomagerie.command.ChomFakePlayerManager;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
    @Inject(method = "isPaused", at = @At("RETURN"), cancellable = true)
    private void chomagerie$keepIntegratedServerRunningForFakePlayers(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }

        IntegratedServer server = (IntegratedServer) (Object) this;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ChomFakePlayerManager.isManagedFake(player)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
