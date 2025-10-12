package com.atemukesu.extendednoteblock.client.gui.screen;

import com.atemukesu.extendednoteblock.sound.SoundPackInfo;
import com.atemukesu.extendednoteblock.sound.SoundPackManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * “创建新音色包”界面。
 * 提供一个文本输入框让用户为新音色包命名。
 */
public class CreatePackScreen extends Screen {
    /**
     * 打开此界面的父界面，用于返回。
     */
    private final Screen parent;
    /**
     * 用于输入新音色包名称的文本框。
     */
    private TextFieldWidget nameField;
    /**
     * 用于确认创建操作的按钮。
     */
    private ButtonWidget createButton;

    /**
     * 构造函数。
     * 
     * @param parent 打开此界面的父屏幕实例。
     */
    public CreatePackScreen(Screen parent) {
        super(Text.translatable("gui.extendednoteblock.create_pack.title"));
        this.parent = parent;
    }

    /**
     * 初始化界面布局和组件。
     * 在屏幕显示或窗口大小改变时调用。
     */
    @Override
    protected void init() {
        super.init();
        int fieldWidth = 200;
        int fieldX = this.width / 2 - fieldWidth / 2;

        // 初始化名称输入框
        this.nameField = new TextFieldWidget(this.textRenderer, fieldX, this.height / 2 - 20, fieldWidth, 20,
                Text.translatable("gui.extendednoteblock.create_pack.name_field"));
        this.addDrawableChild(this.nameField);
        // 界面打开时，自动聚焦到名称输入框
        this.setInitialFocus(this.nameField);

        // 为输入框添加监听器，当文本改变时触发
        this.nameField.setChangedListener(text -> {
            // 只有当输入框内容（去除首尾空格后）不为空时，"创建"按钮才可用
            this.createButton.active = !text.trim().isEmpty();
            // 每次文本改变时，重置文本框颜色为默认白色
            this.nameField.setEditableColor(0xFFFFFFFF);
        });

        // 初始化 "创建" 按钮
        this.createButton = ButtonWidget.builder(
                Text.translatable("gui.extendednoteblock.create_pack.button.create"),
                button -> createAndEditPack()) // 点击时调用 createAndEditPack 方法
                .dimensions(this.width / 2 - 100, this.height / 2 + 20, 200, 20).build();
        // 初始状态下，"创建" 按钮不可用
        this.createButton.active = false;
        this.addDrawableChild(this.createButton);

        // 初始化 "取消" 按钮
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), (button) -> {
            if (this.client != null)
                this.client.setScreen(this.parent); // 点击时返回父界面
        }).dimensions(this.width / 2 - 100, this.height / 2 + 44, 200, 20).build());
    }

    /**
     * 处理创建新音色包并跳转到编辑界面的逻辑。
     */
    private void createAndEditPack() {
        String displayName = this.nameField.getText().trim();
        if (displayName.isEmpty())
            return; // 如果名称为空，则不执行任何操作

        // 调用管理器来创建新的音色包
        SoundPackInfo newPack = SoundPackManager.getInstance().createNewPack(displayName);
        if (newPack != null && client != null) {
            // 如果创建成功，则直接跳转到该音色包的编辑界面
            client.setScreen(new EditPackScreen(this.parent, newPack));
        } else {
            // 如果创建失败（通常是因为名称已存在），则将输入框文本颜色设为红色以提示用户
            this.nameField.setEditableColor(Formatting.RED.getColorValue());
            // 你也可以在这里添加一个文本组件来显示更详细的错误信息
        }
    }

    /**
     * 当屏幕被关闭时调用（例如按ESC键）。
     * 确保返回到父屏幕。
     */
    @Override
    public void close() {
        if (this.client != null)
            this.client.setScreen(this.parent);
    }

    /**
     * 渲染屏幕上的所有元素。
     * 
     * @param context 绘图上下文
     * @param mouseX  鼠标X坐标
     * @param mouseY  鼠标Y坐标
     * @param delta   帧时间差
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        // 绘制屏幕标题
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 50,
                0xFFFFFF);
        // 绘制名称输入框上方的标签文本
        context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.extendednoteblock.create_pack.name_field"),
                this.nameField.getX(), this.nameField.getY() - 12, 0xA0A0A0);
        // 渲染所有子组件（按钮、文本框等）
        super.render(context, mouseX, mouseY, delta);
    }
}