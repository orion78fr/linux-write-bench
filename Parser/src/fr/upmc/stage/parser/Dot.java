package fr.upmc.stage.parser;

public class Dot implements Comparable<Dot>{
	private double x;
	private double y;
	public double getX() {
		return this.x;
	}
	public double getY() {
		return this.y;
	}
	public Dot(double x, double y) {
		super();
		this.x = x;
		this.y = y;
	}
	@Override
	public int compareTo(Dot o) {
		return new Double(this.getX()).compareTo(o.getX());
	}
}
