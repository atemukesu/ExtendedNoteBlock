package com.atemukesu.extendednoteblock.command;

import com.atemukesu.extendednoteblock.util.SmoothMoveManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SmoothMoveCommand {
    private static final SuggestionProvider<ServerCommandSource> DIRECTION_SUGGESTIONS = (context,
            builder) -> CommandSource.suggestMatching(new String[] { "north", "south", "east", "west", "x", "-x", "y",
                    "-y", "z", "-z", "forward", "look" }, builder);

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("smoothmove")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("stop")
                        .then(CommandManager.argument("targets", EntityArgumentType.entities())
                                .executes(SmoothMoveCommand::executeStop)))
                .then(CommandManager.argument("targets", EntityArgumentType.entities())
                        .then(CommandManager.argument("direction", StringArgumentType.word())
                                .suggests(DIRECTION_SUGGESTIONS)
                                .then(CommandManager.argument("speed", FloatArgumentType.floatArg(0.0001f, 10.0f))
                                        .executes(ctx -> executeStart(ctx, -1)) // Optional duration -> -1
                                        .then(CommandManager
                                                .argument("duration", IntegerArgumentType.integer(0))
                                                .executes(ctx -> executeStart(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "duration"))))))));
    }

    private static int executeStop(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Collection<? extends Entity> targets = EntityArgumentType.getEntities(context, "targets");
        int count = 0;
        for (Entity entity : targets) {
            if (SmoothMoveManager.isMoving(entity)) {
                SmoothMoveManager.stopMove(entity);
                count++;
            }
        }

        final int c = count;
        if (count > 0) {
            context.getSource().sendFeedback(
                    () -> Text.translatable("commands.extendednoteblock.smoothmove.stop.success", c), true);
        }
        return count;
    }

    private static int executeStart(CommandContext<ServerCommandSource> context, int duration)
            throws CommandSyntaxException {
        Collection<? extends Entity> targets = EntityArgumentType.getEntities(context, "targets");
        String direction = StringArgumentType.getString(context, "direction");
        float speed = FloatArgumentType.getFloat(context, "speed");

        int count = 0;
        List<Entity> failed = new ArrayList<>();

        for (Entity entity : targets) {
            if (SmoothMoveManager.isMoving(entity)) {
                failed.add(entity);
                continue;
            }

            Vec3d vel = calculateVelocity(entity, direction, speed);
            if (vel != null) {
                SmoothMoveManager.startMove(entity, vel, duration);
                count++;
            }
        }

        // Feedback
        if (!failed.isEmpty()) {
            for (Entity e : failed) {
                context.getSource().sendError(
                        Text.translatable("commands.extendednoteblock.smoothmove.already_moving", e.getDisplayName()));
            }
        }

        if (count > 0) {
            final int c = count;
            final boolean infinite = duration < 0;
            context.getSource().sendFeedback(
                    () -> infinite
                            ? Text.translatable("commands.extendednoteblock.smoothmove.success_infinite", c, direction)
                            : Text.translatable("commands.extendednoteblock.smoothmove.success", c, direction),
                    true);
        } else if (failed.size() == targets.size()) {
            // All failed
            // Already sent errors
        } else {
            context.getSource().sendError(Text.translatable("commands.extendednoteblock.smoothmove.failed"));
        }

        return count;
    }

    private static Vec3d calculateVelocity(Entity entity, String direction, float speed) {
        Vec3d vec = switch (direction.toLowerCase()) {
            case "north" -> new Vec3d(0, 0, -1);
            case "south" -> new Vec3d(0, 0, 1);
            case "east" -> new Vec3d(1, 0, 0);
            case "west" -> new Vec3d(-1, 0, 0);
            case "x" -> new Vec3d(1, 0, 0);
            case "-x" -> new Vec3d(-1, 0, 0);
            case "y" -> new Vec3d(0, 1, 0);
            case "-y" -> new Vec3d(0, -1, 0);
            case "z" -> new Vec3d(0, 0, 1);
            case "-z" -> new Vec3d(0, 0, -1);
            case "forward" -> Vec3d.fromPolar(0, entity.getYaw()).normalize();
            case "look" -> entity.getRotationVector().normalize();
            default -> null;
        };

        return vec != null ? vec.multiply(speed) : null;
    }
}
