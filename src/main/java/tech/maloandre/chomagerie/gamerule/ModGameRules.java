package tech.maloandre.chomagerie.gamerule;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;

public final class ModGameRules {
    public static final GameRule<Boolean> PLAYERS_TRAMPLE_FARMLAND = registerBoolean(
            "players_trample_farmland",
            GameRuleCategory.PLAYER,
            false
    );

    public static final GameRule<Boolean> MOBS_TRAMPLE_FARMLAND = registerBoolean(
            "mobs_trample_farmland",
            GameRuleCategory.MOBS,
            false
    );

    public static final GameRule<Boolean> LEATHER_BOOTS_TRAMPLE_FARMLAND = registerBoolean(
            "leather_boots_trample_farmland",
            GameRuleCategory.PLAYER,
            false
    );

    private ModGameRules() {
    }

    public static void register() {
    }

    private static GameRule<Boolean> registerBoolean(String name, GameRuleCategory category, boolean defaultValue) {
        return Registry.register(
                BuiltInRegistries.GAME_RULE,
                name,
                new GameRule<>(
                        category,
                        GameRuleType.BOOL,
                        BoolArgumentType.bool(),
                        GameRuleTypeVisitor::visitBoolean,
                        Codec.BOOL,
                        value -> value ? 1 : 0,
                        defaultValue,
                        FeatureFlagSet.of()
                )
        );
    }
}
