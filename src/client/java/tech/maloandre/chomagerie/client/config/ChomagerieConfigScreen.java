package tech.maloandre.chomagerie.client.config;

import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigColorList;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import fi.dy.masa.malilib.config.options.ConfigString;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.util.data.Color4f;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import tech.maloandre.chomagerie.Chomagerie;
import tech.maloandre.chomagerie.config.ModState;

import java.util.ArrayList;
import java.util.List;

public class ChomagerieConfigScreen extends GuiConfigsBase {
    private static final int LIST_Y = 72;
    private static final int TAB_Y = 40;
    private static final int TAB_WIDTH = 112;
    private static final int TAB_SPACING = 2;

    private static ConfigTab activeTab = ConfigTab.ALL;

    private final List<ConfigOptionWrapper> allOptions;
    private final List<ConfigOptionWrapper> shulkerRefillOptions;
    private final List<ConfigOptionWrapper> teamTagOptions;

    public ChomagerieConfigScreen(Screen parent) {
        super(10, LIST_Y, Chomagerie.MOD_ID, parent, "chomagerie.gui.title.configs");

        ChomagerieConfig config = ChomagerieConfig.getInstance();
        config.reload();
        this.shulkerRefillOptions = createShulkerRefillOptions(config);
        this.teamTagOptions = createTeamTagOptions(config);
        this.allOptions = createAllOptions(this.shulkerRefillOptions, this.teamTagOptions);
    }

    @Override
    public void initGui() {
        super.initGui();

        int x = 10;
        for (ConfigTab tab : ConfigTab.values()) {
            ButtonGeneric button = new ButtonGeneric(x, TAB_Y, TAB_WIDTH, 20, I18n.get(tab.translationKey));
            button.setEnabled(activeTab != tab);
            addButton(button, (ButtonBase clickedButton, int mouseButton) -> {
                activeTab = tab;
                reCreateListWidget();
                initGui();
            });
            x += TAB_WIDTH + TAB_SPACING;
        }
    }

    public List<ConfigOptionWrapper> getAllConfigs() {
        return this.allOptions;
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        return switch (activeTab) {
            case ALL -> this.allOptions;
            case SHULKER_REFILL -> this.shulkerRefillOptions;
            case TEAM_TAG -> this.teamTagOptions;
        };
    }

    private static List<ConfigOptionWrapper> createAllOptions(List<ConfigOptionWrapper> shulkerRefillOptions,
            List<ConfigOptionWrapper> teamTagOptions) {
        List<ConfigOptionWrapper> configs = new ArrayList<>();
        configs.add(new ConfigOptionWrapper(I18n.get("chomagerie.config.section.shulker_refill")));
        configs.addAll(shulkerRefillOptions);
        configs.add(new ConfigOptionWrapper(I18n.get("chomagerie.config.section.team_tag")));
        configs.addAll(teamTagOptions);
        return configs;
    }

    private static List<ConfigOptionWrapper> createShulkerRefillOptions(ChomagerieConfig config) {
        List<ConfigOptionWrapper> configs = new ArrayList<>();

        configs.add(new ConfigOptionWrapper(booleanOption("chomagerie.config.shulker_refill.show_messages",
                config.shulkerRefill.shouldShowRefillMessages(),
                "chomagerie.config.shulker_refill.show_messages.comment",
                option -> config.shulkerRefill.setShowRefillMessages(option.getBooleanValue()), config)));
        configs.add(new ConfigOptionWrapper(booleanOption("chomagerie.config.shulker_refill.enabled",
                config.shulkerRefill.isEnabled(),
                "chomagerie.config.shulker_refill.enabled.comment",
                option -> {
                    config.shulkerRefill.setEnabled(option.getBooleanValue());
                    ModState.setClientEnabled(option.getBooleanValue());
                }, config)));
        configs.add(new ConfigOptionWrapper(booleanOption("chomagerie.config.shulker_refill.play_sounds",
                config.shulkerRefill.shouldPlaySounds(),
                "chomagerie.config.shulker_refill.play_sounds.comment",
                option -> config.shulkerRefill.setPlaySounds(option.getBooleanValue()), config)));
        configs.add(new ConfigOptionWrapper(booleanOption("chomagerie.config.shulker_refill.filter_by_name",
                config.shulkerRefill.isFilterByNameEnabled(),
                "chomagerie.config.shulker_refill.filter_by_name.comment",
                option -> config.shulkerRefill.setFilterByName(option.getBooleanValue()), config)));
        configs.add(new ConfigOptionWrapper(stringListOption("chomagerie.config.shulker_refill.shulker_names",
                config.shulkerRefill.getShulkerNameFilters(),
                "chomagerie.config.shulker_refill.shulker_name.comment",
                option -> config.shulkerRefill.setShulkerNameFilters(option.getStrings()), config)));

        return configs;
    }

    private static List<ConfigOptionWrapper> createTeamTagOptions(ChomagerieConfig config) {
        List<ConfigOptionWrapper> configs = new ArrayList<>();

        configs.add(new ConfigOptionWrapper(booleanOption("chomagerie.config.team_tag.enabled",
                config.teamTag.isEnabled(),
                "chomagerie.config.team_tag.enabled.comment",
                option -> config.teamTag.setEnabled(option.getBooleanValue()), config)));
        configs.add(new ConfigOptionWrapper(stringOption("chomagerie.config.team_tag.tag",
                config.teamTag.getTag(),
                "chomagerie.config.team_tag.tag.comment",
                option -> config.teamTag.setTag(option.getStringValue()), config)));
        configs.add(new ConfigOptionWrapper(optionList("chomagerie.config.team_tag.color_mode",
                new ColorModeEntry(config.teamTag.getColorMode()),
                "chomagerie.config.team_tag.color_mode.comment",
                option -> config.teamTag.setColorMode(((ColorModeEntry) option.getOptionListValue()).value()), config)));
        configs.add(new ConfigOptionWrapper(optionList("chomagerie.config.team_tag.minecraft_color",
                new MinecraftColorEntry(config.teamTag.getSelectedColor()),
                "chomagerie.config.team_tag.minecraft_color.comment",
                option -> config.teamTag.setSelectedColor(((MinecraftColorEntry) option.getOptionListValue()).value()), config)));
        configs.add(new ConfigOptionWrapper(colorListOption("chomagerie.config.team_tag.gradient_colors",
                config.teamTag.getGradientColors(),
                "chomagerie.config.team_tag.gradient_colors.comment",
                option -> config.teamTag.setGradientColors(hexStringsFromColors(option.getColors())), config)));

        return configs;
    }

    private static ConfigBoolean booleanOption(String translationKey, boolean value, String commentKey,
            BooleanConsumer consumer, ChomagerieConfig config) {
        ConfigBoolean option = new ConfigBoolean(translationKey, value, commentKey);
        applyTranslation(option, translationKey);
        option.setValueChangeCallback(changed -> {
            consumer.accept(changed);
            save(config);
        });
        return option;
    }

    private static ConfigString stringOption(String translationKey, String value, String commentKey,
            StringConsumer consumer, ChomagerieConfig config) {
        ConfigString option = new ConfigString(translationKey, value, commentKey);
        applyTranslation(option, translationKey);
        option.setValueChangeCallback(changed -> {
            consumer.accept(changed);
            save(config);
        });
        return option;
    }

    private static ConfigOptionList optionList(String translationKey, IConfigOptionListEntry value, String commentKey,
            OptionListConsumer consumer, ChomagerieConfig config) {
        ConfigOptionList option = new ConfigOptionList(translationKey, value, commentKey);
        applyTranslation(option, translationKey);
        option.setValueChangeCallback(changed -> {
            consumer.accept(changed);
            save(config);
        });
        return option;
    }

    private static ConfigStringList stringListOption(String translationKey, List<String> value, String commentKey,
            StringListConsumer consumer, ChomagerieConfig config) {
        ConfigStringList option = new ConfigStringList(translate(translationKey), ImmutableList.copyOf(value), commentKey);
        applyTranslation(option, translationKey);
        option.setValueChangeCallback(changed -> {
            consumer.accept(changed);
            save(config);
        });
        return option;
    }

    private static ConfigColorList colorListOption(String translationKey, List<String> value, String commentKey,
            ColorListConsumer consumer, ChomagerieConfig config) {
        ConfigColorList option = new ConfigColorList(translate(translationKey), colorsFromHexStrings(value), commentKey);
        applyTranslation(option, translationKey);
        option.setValueChangeCallback(changed -> {
            consumer.accept(changed);
            save(config);
        });
        return option;
    }

    private static ImmutableList<Color4f> colorsFromHexStrings(List<String> colors) {
        ImmutableList.Builder<Color4f> builder = ImmutableList.builder();
        for (String color : colors) {
            builder.add(Color4f.fromString(color));
        }
        return builder.build();
    }

    private static List<String> hexStringsFromColors(List<Color4f> colors) {
        List<String> hexColors = new ArrayList<>();
        for (Color4f color : colors) {
            hexColors.add(String.format("#%02X%02X%02X", color.ri, color.gi, color.bi));
        }
        return hexColors;
    }

    private static void applyTranslation(fi.dy.masa.malilib.config.IConfigBase option, String translationKey) {
        String translatedName = translate(translationKey);
        option.setPrettyName(translatedName);
        option.setTranslatedName(translatedName);
    }

    private static String translate(String translationKey) {
        return I18n.get(translationKey);
    }

    private static void save(ChomagerieConfig config) {
        config.save();
        ModState.setClientEnabled(config.shulkerRefill.isEnabled());
    }

    private interface BooleanConsumer {
        void accept(ConfigBoolean option);
    }

    private interface StringConsumer {
        void accept(ConfigString option);
    }

    private interface OptionListConsumer {
        void accept(ConfigOptionList option);
    }

    private interface StringListConsumer {
        void accept(ConfigStringList option);
    }

    private interface ColorListConsumer {
        void accept(ConfigColorList option);
    }

    private enum ConfigTab {
        ALL("chomagerie.config.tab.all"),
        SHULKER_REFILL("chomagerie.config.tab.shulker_refill"),
        TEAM_TAG("chomagerie.config.tab.team_tag");

        private final String translationKey;

        ConfigTab(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private record ColorModeEntry(ChomagerieConfig.ColorMode value) implements IConfigOptionListEntry {
        private static final ChomagerieConfig.ColorMode[] VALUES = ChomagerieConfig.ColorMode.values();

        @Override
        public String getStringValue() {
            return this.value.name();
        }

        @Override
        public String getDisplayName() {
            return this.value.getLabel();
        }

        @Override
        public IConfigOptionListEntry cycle(boolean forward) {
            return new ColorModeEntry(cycleValue(VALUES, this.value, forward));
        }

        @Override
        public IConfigOptionListEntry fromString(String value) {
            return new ColorModeEntry(parseEnum(ChomagerieConfig.ColorMode.class, value, ChomagerieConfig.ColorMode.MANUAL));
        }
    }

    private record MinecraftColorEntry(ChomagerieConfig.MinecraftColor value) implements IConfigOptionListEntry {
        private static final ChomagerieConfig.MinecraftColor[] VALUES = ChomagerieConfig.MinecraftColor.values();

        @Override
        public String getStringValue() {
            return this.value.name();
        }

        @Override
        public String getDisplayName() {
            return this.value.getCode() + " " + this.value.getLabel();
        }

        @Override
        public IConfigOptionListEntry cycle(boolean forward) {
            return new MinecraftColorEntry(cycleValue(VALUES, this.value, forward));
        }

        @Override
        public IConfigOptionListEntry fromString(String value) {
            return new MinecraftColorEntry(parseEnum(ChomagerieConfig.MinecraftColor.class, value, ChomagerieConfig.MinecraftColor.AQUA));
        }
    }

    private static <T extends Enum<T>> T cycleValue(T[] values, T current, boolean forward) {
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                index = i;
                break;
            }
        }

        int offset = forward ? 1 : -1;
        return values[Math.floorMod(index + offset, values.length)];
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> enumType, String value, T fallback) {
        if (value != null) {
            for (T candidate : enumType.getEnumConstants()) {
                if (candidate.name().equalsIgnoreCase(value)) {
                    return candidate;
                }
            }
        }
        return fallback;
    }
}
