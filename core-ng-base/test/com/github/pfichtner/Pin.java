package com.github.pfichtner;

public abstract class Pin {

	private final int num;

	protected Pin(int num) {
		this.num = num;
	}

	public int pinNum() {
		return num;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + num;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Pin other = (Pin) obj;
		if (num != other.num)
			return false;
		return true;
	}

	public static class AnalogPin extends Pin {
		private AnalogPin(int num) {
			super(num);
		}
	}

	public static class DigitalPin extends Pin {
		private DigitalPin(int num) {
			super(num);
		}
	}

	public static AnalogPin analogPin(int num) {
		return new AnalogPin(num);
	}

	public static DigitalPin digitalPin(int num) {
		return new DigitalPin(num);
	}

}
