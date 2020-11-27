package fr.upmc.stage.parser;

public class Interval{
	private double begin;
	private double end;
	
	private Interval(double begin, double end){
		this.begin = begin;
		this.end = end;
	}
	
	public static Interval getIntervalWithLimit(double begin, double end){
		return new Interval(begin, end);
	}
	
	public static Interval getIntervalWithCenter(double center, double interval){
		return new Interval(center - (interval/2), center + (interval/2));
	}

	public double getBegin() {
		return this.begin;
	}

	public double getEnd() {
		return this.end;
	}
	
	public double getCenter() {
		return (this.begin + this.end) / 2;
	}
	
	public boolean isInside(double num){
		return (this.begin <= num) && (num <= this.end);
	}
	
	public boolean isStrictlyInside(double num){
		return (this.begin < num) && (num < this.end);
	}
	
	
}