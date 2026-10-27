package net.silvertide.quality_api.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.api.Qualities;

import java.util.Collection;
import java.util.List;

public final class QualityCommand {
    private QualityCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("quality")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("quality", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(Qualities.idsByLevel(), builder))
                                .executes(context -> set(context.getSource(), ResourceLocationArgument.getId(context, "quality")))))
                .then(Commands.literal("remove")
                        .executes(context -> remove(context.getSource())))
                .then(Commands.literal("entity")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.literal("set")
                                        .then(Commands.argument("quality", ResourceLocationArgument.id())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(Qualities.idsByLevel(), builder))
                                                .executes(context -> setEntities(context.getSource(), EntityArgument.getEntities(context, "targets"), ResourceLocationArgument.getId(context, "quality")))))
                                .then(Commands.literal("remove")
                                        .executes(context -> removeEntities(context.getSource(), EntityArgument.getEntities(context, "targets")))))));
    }

    private static int setEntities(CommandSourceStack source, Collection<? extends Entity> targets, ResourceLocation id) {
        if (Qualities.get(id).isEmpty()) {
            source.sendFailure(Component.translatable("commands.quality_api.unknown", id.toString()));
            return 0;
        }
        targets.forEach(entity -> Qualities.apply(entity, id));
        if (targets.size() == 1) {
            Entity entity = targets.iterator().next();
            source.sendSuccess(() -> Component.translatable("commands.quality_api.set", entity.getDisplayName(), Qualities.displayName(entity, id), id.toString()), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.quality_api.entity.set", targets.size(), Qualities.displayName(id), id.toString()), true);
        }
        return targets.size();
    }

    private static int removeEntities(CommandSourceStack source, Collection<? extends Entity> targets) {
        List<? extends Entity> withQuality = targets.stream().filter(entity -> entity.hasData(QualityAPI.ENTITY_QUALITY)).toList();
        if (withQuality.isEmpty()) {
            source.sendFailure(Component.translatable("commands.quality_api.entity.none"));
            return 0;
        }
        withQuality.forEach(Qualities::remove);
        source.sendSuccess(() -> Component.translatable("commands.quality_api.entity.removed", withQuality.size()), true);
        return withQuality.size();
    }

    private static int set(CommandSourceStack source, ResourceLocation id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            source.sendFailure(Component.translatable("commands.quality_api.empty_hand"));
            return 0;
        }
        if (Qualities.get(id).isEmpty()) {
            source.sendFailure(Component.translatable("commands.quality_api.unknown", id.toString()));
            return 0;
        }
        if (!Qualities.apply(stack, id)) {
            source.sendFailure(Component.translatable("commands.quality_api.not_applicable", stack.getHoverName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.quality_api.set", stack.getHoverName(), Qualities.displayName(stack, id), id.toString()), true);
        return 1;
    }

    private static int remove(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            source.sendFailure(Component.translatable("commands.quality_api.empty_hand"));
            return 0;
        }
        if (Qualities.getId(stack).isEmpty()) {
            source.sendFailure(Component.translatable("commands.quality_api.no_quality", stack.getHoverName()));
            return 0;
        }
        Qualities.remove(stack);
        source.sendSuccess(() -> Component.translatable("commands.quality_api.removed", stack.getHoverName()), true);
        return 1;
    }
}
