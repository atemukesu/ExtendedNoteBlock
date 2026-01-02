package com.atemukesu.extendednoteblock.client.gui.widget;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MathExpressionWidget extends TextFieldWidget {
    // 匹配数学函数、变量 t、d 和数字
    private static final Pattern HIGHLIGHT_PATTERN = Pattern.compile(
            "(?<func>sin|cos|tan|abs|sqrt|pi|exp|log|pow)|(?<var>t|d)|(?<num>\\d+(\\.\\d+)?)|(?<op>[+\\-*/^()])"
    );

    public MathExpressionWidget(TextRenderer textRenderer, int x, int y, int width, int height, Text text) {
        super(textRenderer, x, y, width, height, text);
        // 设置一个渲染器，通过 Style 覆盖默认文本渲染逻辑
        this.setRenderTextProvider((string, firstCharacterIndex) -> {
            return getHighlightedText(string);
        });
    }

    private OrderedText getHighlightedText(String text) {
        net.minecraft.text.MutableText mutableText = Text.empty();
        Matcher matcher = HIGHLIGHT_PATTERN.matcher(text);
        int lastPos = 0;

        while (matcher.find()) {
            // 添加匹配前的普通文本
            if (matcher.start() > lastPos) {
                mutableText.append(Text.literal(text.substring(lastPos, matcher.start())).formatted(Formatting.GRAY));
            }

            String match = matcher.group();
            Formatting color = Formatting.WHITE;
            
            if (matcher.group("func") != null) color = Formatting.AQUA;      // 函数为青色
            else if (matcher.group("var") != null) color = Formatting.GREEN; // 变量t为绿色
            else if (matcher.group("num") != null) color = Formatting.GOLD;  // 数字为金色
            else if (matcher.group("op") != null) color = Formatting.LIGHT_PURPLE; // 运算符

            mutableText.append(Text.literal(match).formatted(color));
            lastPos = matcher.end();
        }

        if (lastPos < text.length()) {
            mutableText.append(Text.literal(text.substring(lastPos)).formatted(Formatting.GRAY));
        }

        return mutableText.asOrderedText();
    }
}