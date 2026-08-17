package tech.maloandre.chomagerie.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tech.maloandre.chomagerie.gamerule.ModGameRules;

@Mixin(FarmlandBlock.class)
public abstract class FarmlandBlockMixin {
    @Redirect(
            method = "fallOn",
            at = @At(
                    value = "INVOKESTATIC",
                    target = "Lnet/minecraft/world/level/block/FarmlandBlock;turnToDirt(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"
            )
    )
    private void chomagerie$preventFarmlandTrampling(Entity entity, BlockState state, Level level, BlockPos pos) {
        if (chomagerie$canTrampleFarmland(entity, level)) {
            FarmlandBlock.turnToDirt(entity, state, level, pos);
        }
    }

    private static boolean chomagerie$canTrampleFarmland(Entity entity, Level level) {
        if (!(entity instanceof LivingEntity)) {
            return true;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return true;
        }

        if (entity instanceof Player player) {
            if (!serverLevel.getGameRules().get(ModGameRules.PLAYERS_TRAMPLE_FARMLAND)) {
                return false;
            }

            return !serverLevel.getGameRules().get(ModGameRules.LEATHER_BOOTS_TRAMPLE_FARMLAND)
                    || player.getItemBySlot(EquipmentSlot.FEET).is(Items.LEATHER_BOOTS);
        }

        return serverLevel.getGameRules().get(ModGameRules.MOBS_TRAMPLE_FARMLAND);
    }
}
