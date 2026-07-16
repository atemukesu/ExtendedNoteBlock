package com.atemukesu.extendednoteblock.client.gui.screen;

import com.atemukesu.extendednoteblock.sound.SoundPackInfo;
import com.atemukesu.extendednoteblock.sound.SoundPackManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.regex.Pattern;

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

    // 用于验证名称的正则表达式
    // 该表达式允许字母(a-z, A-Z)，数字(0-9)，下划线(_)和连字符(-)
    // ^ 和 $ 确保整个字符串都必须匹配这个规则
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    /**
     * 构造函数。
     *
     * @param parent 打开此界面的父屏幕实例。
     */
    public CreatePackScreen(Screen parent) {
        super(Text.translatable("gui.extendednoteblock.create_pack.title"));
        this.parent = parent;
    }

    // 一个辅助方法用于检查名称是否有效
    private boolean isNameValid(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false; // 空名称无效
        }
        return VALID_NAME_PATTERN.matcher(name.trim()).matches();
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

        this.nameField = new TextFieldWidget(this.textRenderer, fieldX, this.height / 2 - 20, fieldWidth, 20,
                Text.translatable("gui.extendednoteblock.create_pack.name_field"));
        this.addDrawableChild(this.nameField);
        this.setInitialFocus(this.nameField);

        // 为输入框添加更严格的监听器
        this.nameField.setChangedListener(text -> {
            String trimmedText = text.trim();
            boolean isValid = isNameValid(trimmedText);

            // 当名称不为空且字符合法时，"创建"按钮才可用
            this.createButton.active = isValid;

            // 如果文本不为空且包含非法字符，则将输入框颜色设为红色以提示用户
            // 如果文本为空或是合法的，则恢复为默认白色
            if (!trimmedText.isEmpty() && !isValid) {
                this.nameField.setEditableColor(Formatting.RED.getColorValue());
            } else {
                this.nameField.setEditableColor(0xFFFFFFFF);
            }
        });

        this.createButton = ButtonWidget.builder(
                Text.translatable("gui.extendednoteblock.create_pack.button.create"),
                button -> createAndEditPack())
                .dimensions(this.width / 2 - 100, this.height / 2 + 20, 200, 20).build();
        this.createButton.active = false;
        this.addDrawableChild(this.createButton);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), (button) -> {
            if (this.client != null)
                this.client.setScreen(this.parent);
        }).dimensions(this.width / 2 - 100, this.height / 2 + 44, 200, 20).build());
    }

    /**
     * 处理创建新音色包并跳转到编辑界面的逻辑。
     */
    private void createAndEditPack() {
        String displayName = this.nameField.getText().trim();
        // 增加一道保险，尽管按钮状态已经阻止了无效输入，但最好还是检查
        if (!isNameValid(displayName))
            return;

        SoundPackInfo newPack = SoundPackManager.getInstance().createNewPack(displayName);
        if (newPack != null && client != null) {
            client.setScreen(new EditPackScreen(this.parent, newPack));
        } else {
            this.nameField.setEditableColor(Formatting.RED.getColorValue());
            // 如果创建失败的原因是重名，变红依然是有效的反馈
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
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 50,
                0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.extendednoteblock.create_pack.name_field"),
                this.nameField.getX(), this.nameField.getY() - 12, 0xA0A0A0);

        // 当输入不合法时，显示一个提示信息
        String trimmedText = this.nameField.getText().trim();
        if (!trimmedText.isEmpty() && !isNameValid(trimmedText)) {
            Text tooltip = Text.translatable("gui.extendednoteblock.create_pack.invalid_name")
                    .formatted(Formatting.RED);
            context.drawTextWithShadow(this.textRenderer, tooltip, this.nameField.getX(),
                    this.nameField.getY() + this.nameField.getHeight() + 4, 0xFFFFFF);
        }
    }
}