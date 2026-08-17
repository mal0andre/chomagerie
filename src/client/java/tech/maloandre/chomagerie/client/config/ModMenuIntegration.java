package tech.maloandre.chomagerie.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;
import tech.maloandre.chomagerie.config.ModState;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            // Reload configuration to ensure it's up to date
            ChomagerieConfig config = ChomagerieConfig.getInstance();
            config.reload();

            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Component.literal("Chomagerie Configuration"));

            // ShulkerRefill category
            ConfigCategory shulkerRefillCategory = builder.getOrCreateCategory(Component.literal("ShulkerRefill"));
            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            // Option to show refill messages
            shulkerRefillCategory.addEntry(entryBuilder.startBooleanToggle(
                            Component.literal("Show Messages"),
                            config.shulkerRefill.shouldShowRefillMessages()
                    )
                    .setDefaultValue(true)
                    .setTooltip(Component.literal("Displays a message when an item is refilled from a shulker box"))
                    .setSaveConsumer(newValue -> {
                        config.shulkerRefill.setShowRefillMessages(newValue);
                    })
                    .build());

            // Option to enable/disable ShulkerRefill
            shulkerRefillCategory.addEntry(entryBuilder.startBooleanToggle(
                            Component.literal("Enable ShulkerRefill"),
                            config.shulkerRefill.isEnabled()
                    )
                    .setDefaultValue(true)
                    .setTooltip(Component.literal("Enables or disables the automatic refill system from shulker boxes"))
                    .setSaveConsumer(newValue -> {
                        config.shulkerRefill.setEnabled(newValue);
                        ModState.setClientEnabled(newValue);
                    })
                    .build());


            // Option to play sounds during refill
            shulkerRefillCategory.addEntry(entryBuilder.startBooleanToggle(
                            Component.literal("Play Sounds"),
                            config.shulkerRefill.shouldPlaySounds()
                    )
                    .setDefaultValue(true)
                    .setTooltip(Component.literal("Plays a sound when an item is refilled from a shulker box"))
                    .setSaveConsumer(newValue -> {
                        config.shulkerRefill.setPlaySounds(newValue);
                    })
                    .build());

            // Option to filter by shulker box name
            shulkerRefillCategory.addEntry(entryBuilder.startBooleanToggle(
                            Component.literal("Filter by Shulker Name"),
                            config.shulkerRefill.isFilterByNameEnabled()
                    )
                    .setDefaultValue(false)
                    .setTooltip(Component.literal("Only uses shulker boxes with a specific name for refill"))
                    .setSaveConsumer(newValue -> {
                        config.shulkerRefill.setFilterByName(newValue);
                    })
                    .build());

            // Option to set the name of shulker boxes to use
            shulkerRefillCategory.addEntry(entryBuilder.startStrField(
                            Component.literal("Shulker Box Name"),
                            config.shulkerRefill.getShulkerNameFilter()
                    )
                    .setDefaultValue("restock same")
                    .setTooltip(Component.literal("Only shulker boxes with this exact name will be used for refill"))
                    .setSaveConsumer(newValue -> {
                        config.shulkerRefill.setShulkerNameFilter(newValue);
                    })
                    .build());


            builder.setSavingRunnable(() -> {
                config.save();
                ModState.setClientEnabled(config.shulkerRefill.enabled);
            });

            return builder.build();
        };
    }
}
