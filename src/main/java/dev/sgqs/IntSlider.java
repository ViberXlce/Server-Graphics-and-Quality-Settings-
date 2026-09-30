package dev.sgqs;

import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

public class IntSlider extends SliderWidget {
	private final int min, max;
	private final IntFunction<Text> format;
	private final IntConsumer onChange;

	public IntSlider(int w, int min, int max, int value, IntFunction<Text> format, IntConsumer onChange) {
		super(0, 0, w, 20, Text.empty(), (double) (value - min) / (max - min));
		this.min = min;
		this.max = max;
		this.format = format;
		this.onChange = onChange;
		updateMessage();
	}

	private int current() {
		return min + (int) Math.round(this.value * (max - min));
	}

	@Override
	protected void updateMessage() {
		if (format == null) return;
		setMessage(format.apply(current()));
	}

	@Override
	protected void applyValue() {
		if (onChange != null) onChange.accept(current());
	}
}
