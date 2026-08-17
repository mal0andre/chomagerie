package tech.maloandre.chomagerie.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.maloandre.chomagerie.event.ItemStackDepletedCallback;

@Mixin(Inventory.class)
public abstract class PlayerInventoryMixin {

    @Shadow
    @Final
    public Player player;

    // Surveillance de tous les slots de l'inventaire principal (0-35)
    @Unique
    private static final int MAIN_INVENTORY_SIZE = 36; // Hotbar (0-8) + Inventaire principal (9-35)
    @Unique
    private final Item[] chomagerie$lastItems = new Item[MAIN_INVENTORY_SIZE];
    @Unique
    private final ItemStack[] chomagerie$lastStacks = new ItemStack[MAIN_INVENTORY_SIZE];
    @Unique
    private final int[] chomagerie$lastCounts = new int[MAIN_INVENTORY_SIZE];
    @Unique
    private final int[] chomagerie$lastUsedStats = new int[MAIN_INVENTORY_SIZE];

    // Surveillance de l'offhand
    @Unique
    private Item chomagerie$lastOffhandItem = null;
    @Unique
    private ItemStack chomagerie$lastOffhandStack = null;
    @Unique
    private int chomagerie$lastOffhandCount = 0;
    @Unique
    private int chomagerie$lastOffhandUsedStatValue = 0;

    @Unique
    private int chomagerie$getUsedStat(Item item) {
        if (player instanceof ServerPlayer serverPlayer) {
            return serverPlayer.getStats().getValue(Stats.ITEM_USED.get(item));
        }
        return 0;
    }

    /**
     * Vérifie si un item est un seau rempli (eau, lave, neige poudreuse, lait)
     */
    @Unique
    private boolean chomagerie$isFilledBucket(Item item) {
        return item == Items.WATER_BUCKET || 
               item == Items.LAVA_BUCKET || 
               item == Items.POWDER_SNOW_BUCKET ||
               item == Items.MILK_BUCKET ||
               item == Items.PUFFERFISH_BUCKET ||
               item == Items.SALMON_BUCKET ||
               item == Items.COD_BUCKET ||
               item == Items.TROPICAL_FISH_BUCKET ||
               item == Items.AXOLOTL_BUCKET ||
               item == Items.TADPOLE_BUCKET;
    }


    /**
     * Surveille quand un stack se vide COMPLÈTEMENT par utilisation dans tous les slots de l'inventaire principal
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (player == null || player.level().isClientSide()) {
            return;
        }

        Inventory inventory = (Inventory) (Object) this;

        // Surveiller tous les slots de l'inventaire principal (0-35)
        chomagerie$checkAllMainInventorySlots(inventory);

        // Surveiller l'offhand
        chomagerie$checkOffhand(inventory);
    }

    /**
     * Vérifie et surveille tous les slots de l'inventaire principal (hotbar + inventaire principal, slots 0-35)
     */
    @Unique
    private void chomagerie$checkAllMainInventorySlots(Inventory inventory) {
        for (int slot = 0; slot < MAIN_INVENTORY_SIZE; slot++) {
            ItemStack currentStack = inventory.getItem(slot);
            Item lastItem = chomagerie$lastItems[slot];
            int lastCount = chomagerie$lastCounts[slot];
            ItemStack lastStack = chomagerie$lastStacks[slot];
            int lastUsedStat = chomagerie$lastUsedStats[slot];

            // Cas 1 : On surveillait un item et le slot est maintenant complètement vide
            if (lastItem != null && currentStack.isEmpty()) {
                // Vérifier que c'était une consommation naturelle (count passé de 1 à 0)
                if (lastCount == 1) {
                    // Vérifier si la statistique USED a augmenté
                    int currentUsedStat = chomagerie$getUsedStat(lastItem);
                    if (currentUsedStat > lastUsedStat) {
                        // Le stack s'est vidé naturellement
                        ItemStackDepletedCallback.EVENT.invoker().onItemStackDepleted(
                                player, slot, lastItem, lastStack
                        );
                    }
                }
                // Réinitialiser la surveillance
                chomagerie$lastItems[slot] = null;
                chomagerie$lastStacks[slot] = null;
                chomagerie$lastCounts[slot] = 0;
                chomagerie$lastUsedStats[slot] = 0;
            }
            // Cas 1.5 : On surveillait un seau rempli et maintenant c'est un seau vide (item remplacé)
            else if (lastItem != null && !currentStack.isEmpty() &&
                     currentStack.getItem() == Items.BUCKET &&
                     chomagerie$isFilledBucket(lastItem)) {
                // Un seau rempli est devenu un seau vide, c'est une utilisation valide
                int currentUsedStat = chomagerie$getUsedStat(lastItem);
                if (currentUsedStat > lastUsedStat) {
                    // Le seau a été utilisé naturellement
                    ItemStackDepletedCallback.EVENT.invoker().onItemStackDepleted(
                            player, slot, lastItem, lastStack
                    );
                }
                // Réinitialiser la surveillance pour le nouveau seau vide
                chomagerie$lastItems[slot] = Items.BUCKET;
                chomagerie$lastStacks[slot] = currentStack.copy();
                chomagerie$lastCounts[slot] = currentStack.getCount();
                chomagerie$lastUsedStats[slot] = chomagerie$getUsedStat(Items.BUCKET);
            }
            // Cas 2 : On a un item dans le slot
            else if (!currentStack.isEmpty()) {
                Item currentItem = currentStack.getItem();
                int currentCount = currentStack.getCount();

                // Si c'est le même item qu'avant
                if (lastItem == currentItem) {
                    // Mettre à jour uniquement si le count a diminué (utilisation normale)
                    if (currentCount < lastCount) {
                        chomagerie$lastCounts[slot] = currentCount;
                        chomagerie$lastStacks[slot] = currentStack.copy();
                        chomagerie$lastUsedStats[slot] = chomagerie$getUsedStat(currentItem);
                    } else if (currentCount > lastCount) {
                        // Le count a augmenté (ajout manuel, stack, etc.), on réinitialise
                        chomagerie$lastCounts[slot] = currentCount;
                        chomagerie$lastStacks[slot] = currentStack.copy();
                        chomagerie$lastUsedStats[slot] = chomagerie$getUsedStat(currentItem);
                    }
                }
                // Si l'item a changé, commencer à surveiller le nouveau
                else {
                    chomagerie$lastItems[slot] = currentItem;
                    chomagerie$lastCounts[slot] = currentCount;
                    chomagerie$lastStacks[slot] = currentStack.copy();
                    chomagerie$lastUsedStats[slot] = chomagerie$getUsedStat(currentItem);
                }
            }
            // Cas 3 : Le slot est vide et on ne surveillait rien
            else {
                chomagerie$lastCounts[slot] = 0;
                chomagerie$lastStacks[slot] = null;
                chomagerie$lastUsedStats[slot] = 0;
            }
        }
    }

    /**
     * Vérifie et surveille l'offhand (slot 40 dans l'inventaire du joueur)
     */
    @Unique
    private void chomagerie$checkOffhand(Inventory inventory) {
        final int OFFHAND_SLOT = 40; // Le slot de l'offhand est toujours 40
        ItemStack currentStack = inventory.getItem(OFFHAND_SLOT);

        // Cas 1 : On surveillait un item et le slot est maintenant complètement vide
        if (chomagerie$lastOffhandItem != null && currentStack.isEmpty()) {
            // Vérifier que c'était une consommation naturelle (count passé de 1 à 0)
            if (chomagerie$lastOffhandCount == 1) {
                // Vérifier si la statistique USED a augmenté
                int currentUsedStat = chomagerie$getUsedStat(chomagerie$lastOffhandItem);
                if (currentUsedStat > chomagerie$lastOffhandUsedStatValue) {
                    // Le stack s'est vidé naturellement
                    ItemStackDepletedCallback.EVENT.invoker().onItemStackDepleted(
                            player, OFFHAND_SLOT, chomagerie$lastOffhandItem, chomagerie$lastOffhandStack
                    );
                }
            }
            // Réinitialiser la surveillance
            chomagerie$lastOffhandItem = null;
            chomagerie$lastOffhandStack = null;
            chomagerie$lastOffhandCount = 0;
            chomagerie$lastOffhandUsedStatValue = 0;
        }
        // Cas 1.5 : On surveillait un seau rempli et maintenant c'est un seau vide (item remplacé)
        else if (chomagerie$lastOffhandItem != null && !currentStack.isEmpty() &&
                 currentStack.getItem() == Items.BUCKET &&
                 chomagerie$isFilledBucket(chomagerie$lastOffhandItem)) {
            // Un seau rempli est devenu un seau vide, c'est une utilisation valide
            int currentUsedStat = chomagerie$getUsedStat(chomagerie$lastOffhandItem);
            if (currentUsedStat > chomagerie$lastOffhandUsedStatValue) {
                // Le seau a été utilisé naturellement
                ItemStackDepletedCallback.EVENT.invoker().onItemStackDepleted(
                        player, OFFHAND_SLOT, chomagerie$lastOffhandItem, chomagerie$lastOffhandStack
                );
            }
            // Réinitialiser la surveillance pour le nouveau seau vide
            chomagerie$lastOffhandItem = Items.BUCKET;
            chomagerie$lastOffhandStack = currentStack.copy();
            chomagerie$lastOffhandCount = currentStack.getCount();
            chomagerie$lastOffhandUsedStatValue = chomagerie$getUsedStat(Items.BUCKET);
        }
        // Cas 2 : On a un item dans l'offhand
        else if (!currentStack.isEmpty()) {
            Item currentItem = currentStack.getItem();
            int currentCount = currentStack.getCount();

            // Si c'est le même item qu'avant
            if (chomagerie$lastOffhandItem == currentItem) {
                // Mettre à jour uniquement si le count a diminué (utilisation normale)
                if (currentCount < chomagerie$lastOffhandCount) {
                    chomagerie$lastOffhandCount = currentCount;
                    chomagerie$lastOffhandStack = currentStack.copy();
                    chomagerie$lastOffhandUsedStatValue = chomagerie$getUsedStat(currentItem);
                } else if (currentCount > chomagerie$lastOffhandCount) {
                    // Le count a augmenté (ajout manuel, stack, etc.), on réinitialise
                    chomagerie$lastOffhandCount = currentCount;
                    chomagerie$lastOffhandStack = currentStack.copy();
                    chomagerie$lastOffhandUsedStatValue = chomagerie$getUsedStat(currentItem);
                }
            }
            // Si l'item a changé, commencer à surveiller le nouveau
            else {
                chomagerie$lastOffhandItem = currentItem;
                chomagerie$lastOffhandCount = currentCount;
                chomagerie$lastOffhandStack = currentStack.copy();
                chomagerie$lastOffhandUsedStatValue = chomagerie$getUsedStat(currentItem);
            }
        }
        // Cas 3 : Le slot est vide et on ne surveillait rien
        else {
            chomagerie$lastOffhandCount = 0;
            chomagerie$lastOffhandStack = null;
            chomagerie$lastOffhandUsedStatValue = 0;
        }
    }
}

