package tech.maloandre.chomagerie.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import tech.maloandre.chomagerie.client.config.ChomagerieConfig;
import tech.maloandre.chomagerie.config.ModState;

public class ChomagerieCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("chomagerie")
                        .then(ClientCommands.literal("shulkerrefill")
                                .then(ClientCommands.literal("toggle")
                                        .executes(ChomagerieCommand::toggleShulkerRefill))
                                .then(ClientCommands.literal("enable")
                                        .executes(context -> setShulkerRefillEnabled(context, true)))
                                .then(ClientCommands.literal("disable")
                                        .executes(context -> setShulkerRefillEnabled(context, false)))
                                .then(ClientCommands.literal("status")
                                        .executes(ChomagerieCommand::showShulkerRefillStatus))
                        )
                // Future commands for other features
                // .then(ClientCommandManager.literal("autocraft")...)
        );
    }

    private static int toggleShulkerRefill(CommandContext<FabricClientCommandSource> context) {
        ChomagerieConfig config = ChomagerieConfig.getInstance();
        boolean newState = !config.shulkerRefill.isEnabled();
        config.shulkerRefill.setEnabled(newState);
        ModState.setClientEnabled(newState);
        config.save();

        if (newState) {
            context.getSource().sendFeedback(Component.literal("§a[Chomagerie] ShulkerRefill enabled"));
        } else {
            context.getSource().sendFeedback(Component.literal("§c[Chomagerie] ShulkerRefill disabled"));
        }

        return 1;
    }

    private static int setShulkerRefillEnabled(CommandContext<FabricClientCommandSource> context, boolean enabled) {
        ChomagerieConfig config = ChomagerieConfig.getInstance();
        config.shulkerRefill.setEnabled(enabled);
        ModState.setClientEnabled(enabled);
        config.save();

        if (enabled) {
            context.getSource().sendFeedback(Component.literal("§a[Chomagerie] ShulkerRefill enabled"));
        } else {
            context.getSource().sendFeedback(Component.literal("§c[Chomagerie] ShulkerRefill disabled"));
        }

        return 1;
    }

    private static int showShulkerRefillStatus(CommandContext<FabricClientCommandSource> context) {
        ChomagerieConfig config = ChomagerieConfig.getInstance();
        String status = config.shulkerRefill.isEnabled() ? "§aenabled" : "§cdisabled";
        context.getSource().sendFeedback(Component.literal("§e[Chomagerie] ShulkerRefill is currently " + status));
        return 1;
    }
}

