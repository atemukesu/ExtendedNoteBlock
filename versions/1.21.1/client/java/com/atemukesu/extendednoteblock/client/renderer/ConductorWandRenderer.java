package com.atemukesu.extendednoteblock.client.renderer;

import com.atemukesu.extendednoteblock.item.ConductorWandItem;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class ConductorWandRenderer {

    public static void onLastRender(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (player == null)
            return;

        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof ConductorWandItem))
            return;

        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        if (nbt == null || nbt.isEmpty())
            return;

        BlockPos pos1 = null;
        BlockPos pos2 = null;

        if (nbt.contains("Pos1"))
            pos1 = NbtHelper.toBlockPos(nbt, "Pos1").orElse(null);
        if (nbt.contains("Pos2"))
            pos2 = NbtHelper.toBlockPos(nbt, "Pos2").orElse(null);

        if (pos1 == null && pos2 == null)
            return;

        Camera camera = context.camera();
        Vec3d camPos = camera.getPos();
        MatrixStack matrices = context.matrixStack();

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(2.0f);

        // Draw Pos1 (Red)
        if (pos1 != null) {
            drawBox(matrices, new Box(pos1), 1.0f, 0.0f, 0.0f, 0.4f, 1.0f);
        }

        // Draw Pos2 (Blue)
        if (pos2 != null) {
            drawBox(matrices, new Box(pos2), 0.0f, 0.5f, 1.0f, 0.4f, 1.0f);
        }

        // Draw Connection Box (White Outline)
        if (pos1 != null && pos2 != null) {
            int minX = Math.min(pos1.getX(), pos2.getX());
            int minY = Math.min(pos1.getY(), pos2.getY());
            int minZ = Math.min(pos1.getZ(), pos2.getZ());
            int maxX = Math.max(pos1.getX(), pos2.getX()) + 1;
            int maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
            int maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1;
            Box selection = new Box(minX, minY, minZ, maxX, maxY, maxZ);

            drawBoxOutline(matrices, selection, 1.0f, 1.0f, 1.0f, 0.8f);
            drawBoxFace(matrices, selection, 1.0f, 1.0f, 1.0f, 0.15f);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        matrices.pop();
    }

    private static void drawBox(MatrixStack matrices, Box box, float r, float g, float b, float a,
            float outlineA) {
        drawBoxFace(matrices, box, r, g, b, a);
        drawBoxOutline(matrices, box, r, g, b, outlineA);
    }

    private static void drawBoxFace(MatrixStack matrices, Box box, float r, float g, float b,
            float a) {
        Matrix4f mat = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        // Down
        buffer.vertex(mat, (float) box.minX, (float) box.minY, (float) box.minZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.maxX, (float) box.minY, (float) box.minZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.maxX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.minX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);

        // Up
        buffer.vertex(mat, (float) box.minX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.minX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.maxX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.maxX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);

        // North
        buffer.vertex(mat, (float) box.minX, (float) box.minY, (float) box.minZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.minX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.maxX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.maxX, (float) box.minY, (float) box.minZ).color(r, g, b, a);

        // South
        buffer.vertex(mat, (float) box.minX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.maxX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.maxX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.minX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);

        // West
        buffer.vertex(mat, (float) box.minX, (float) box.minY, (float) box.minZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.minX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.minX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.minX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);

        // East
        buffer.vertex(mat, (float) box.maxX, (float) box.minY, (float) box.minZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.maxX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.maxX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.maxX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void drawBoxOutline(MatrixStack matrices, Box box, float r, float g, float b,
            float a) {
        Matrix4f mat = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        // Bottom rect
        buffer.vertex(mat, (float) box.minX, (float) box.minY, (float) box.minZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.maxX, (float) box.minY, (float) box.minZ).color(r, g, b, a);

        buffer.vertex(mat, (float) box.maxX, (float) box.minY, (float) box.minZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.maxX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);

        buffer.vertex(mat, (float) box.maxX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.minX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);

        buffer.vertex(mat, (float) box.minX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.minX, (float) box.minY, (float) box.minZ).color(r, g, b, a);

        // Top rect
        buffer.vertex(mat, (float) box.minX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.maxX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);

        buffer.vertex(mat, (float) box.maxX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.maxX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);

        buffer.vertex(mat, (float) box.maxX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.minX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);

        buffer.vertex(mat, (float) box.minX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.minX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);

        // Vertical lines
        buffer.vertex(mat, (float) box.minX, (float) box.minY, (float) box.minZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.minX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);

        buffer.vertex(mat, (float) box.maxX, (float) box.minY, (float) box.minZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.maxX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);

        buffer.vertex(mat, (float) box.maxX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.maxX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);

        buffer.vertex(mat, (float) box.minX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);
        buffer.vertex(mat, (float) box.minX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
}
