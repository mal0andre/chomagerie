package tech.maloandre.chomagerie.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.maloandre.chomagerie.command.ChomFakePlayerManager;

import java.net.SocketAddress;

@Mixin(PlayerList.class)
public class PlayerListMixin {
    @Inject(method = "canPlayerLogin", at = @At("HEAD"))
    private void chomagerie$removeShadowDuringLogin(SocketAddress address, NameAndId profile, CallbackInfoReturnable<Component> cir) {
        ChomFakePlayerManager.removeForReconnect((PlayerList) (Object) this, profile.id());
    }

    @Inject(method = "placeNewPlayer", at = @At("HEAD"))
    private void chomagerie$removeShadowBeforeReconnect(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        ChomFakePlayerManager.removeForReconnect((PlayerList) (Object) this, player.getUUID());
    }
}
