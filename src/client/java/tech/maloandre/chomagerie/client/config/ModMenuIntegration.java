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

            ConfigCategory teamTagCategory = builder.getOrCreateCategory(Component.literal("Team Tag"));

            teamTagCategory.addEntry(entryBuilder.startBooleanToggle(
                            Component.literal("Enable Team Tag"),
                            config.teamTag.isEnabled()
                    )
                    .setDefaultValue(false)
                    .setTooltip(Component.literal("Displays your tag before your name using Minecraft scoreboard teams"))
                    .setSaveConsumer(newValue -> {
                        config.teamTag.setEnabled(newValue);
                    })
                    .build());

            teamTagCategory.addEntry(entryBuilder.startStrField(
                            Component.literal("Tag"),
                            config.teamTag.getTag()
                    )
                    .setDefaultValue("")
                    .setTooltip(Component.literal("Use Minecraft color codes with &: &2Mal&6o. Maximum 16 visible characters, color codes not counted."))
                    .setSaveConsumer(newValue -> {
                        config.teamTag.setTag(newValue);
                    })
                    .build());

            teamTagCategory.addEntry(entryBuilder.startEnumSelector(
                            Component.literal("Color Mode"),
                            ChomagerieConfig.ColorMode.class,
                            config.teamTag.getColorMode()
                    )
                    .setDefaultValue(ChomagerieConfig.ColorMode.MANUAL)
                    .setEnumNameProvider(value -> Component.literal(((ChomagerieConfig.ColorMode) value).getLabel()))
                    .setTooltip(
                            Component.literal("Manual keeps the codes you type."),
                            Component.literal("Selected color applies one Minecraft color to the whole tag."),
                            Component.literal("Gradient blends between two hex colors across the tag."),
                            Component.literal("Rainbow applies a different color to each character.")
                    )
                    .setSaveConsumer(newValue -> {
                        config.teamTag.setColorMode(newValue);
                    })
                    .build());

            teamTagCategory.addEntry(entryBuilder.startEnumSelector(
                            Component.literal("Minecraft Color"),
                            ChomagerieConfig.MinecraftColor.class,
                            config.teamTag.getSelectedColor()
                    )
                    .setDefaultValue(ChomagerieConfig.MinecraftColor.AQUA)
                    .setEnumNameProvider(value -> {
                        ChomagerieConfig.MinecraftColor color = (ChomagerieConfig.MinecraftColor) value;
                        return Component.literal(color.getCode() + " " + color.getLabel());
                    })
                    .setTooltip(Component.literal("Used when Color Mode is set to Selected color."))
                    .setSaveConsumer(newValue -> {
                        config.teamTag.setSelectedColor(newValue);
                    })
                    .build());

            teamTagCategory.addEntry(entryBuilder.startColorField(
                            Component.literal("Gradient Start"),
                            ChomagerieConfig.TeamTagConfig.parseHexColor(config.teamTag.getGradientStartColor(), 0x977272)
                    )
                    .setDefaultValue(0x977272)
                    .setTooltip(Component.literal("Used when Color Mode is set to Gradient. Example: #977272"))
                    .setSaveConsumer(config.teamTag::setGradientStartColor)
                    .build());

            teamTagCategory.addEntry(entryBuilder.startColorField(
                            Component.literal("Gradient End"),
                            ChomagerieConfig.TeamTagConfig.parseHexColor(config.teamTag.getGradientEndColor(), 0xE32B2B)
                    )
                    .setDefaultValue(0xE32B2B)
                    .setTooltip(Component.literal("Used when Color Mode is set to Gradient. Example: #E32B2B"))
                    .setSaveConsumer(config.teamTag::setGradientEndColor)
                    .build());

            teamTagCategory.addEntry(entryBuilder.startTextDescription(
                    Component.literal("Gradient preview: " + config.teamTag.getGradientStartColor() + " -> " + config.teamTag.getGradientEndColor())
            ).build());

            builder.setSavingRunnable(() -> {
                config.save();
                ModState.setClientEnabled(config.shulkerRefill.enabled);
            });

            return builder.build();
        };
    }
}
