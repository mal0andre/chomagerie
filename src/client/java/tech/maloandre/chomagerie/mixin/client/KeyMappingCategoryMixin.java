package tech.maloandre.chomagerie.mixin.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.maloandre.chomagerie.Chomagerie;

@Mixin(KeyMapping.Category.class)
public class KeyMappingCategoryMixin {
    private static final Identifier CHOMAGERIE_CATEGORY_ID = Identifier.fromNamespaceAndPath(Chomagerie.MOD_ID, "chomagerie");

    @Inject(method = "label", at = @At("HEAD"), cancellable = true)
    private void chomagerie$useCategoryTranslationKey(CallbackInfoReturnable<Component> cir) {
        KeyMapping.Category category = (KeyMapping.Category) (Object) this;
        if (CHOMAGERIE_CATEGORY_ID.equals(category.id())) {
            cir.setReturnValue(Component.translatable("key.chomagerie.category"));
        }
    }
}
