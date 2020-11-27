package fr.upmc.stage.parser;

import java.util.ArrayList;

public class CentralMovingAverageGraph extends Graph{
	public CentralMovingAverageGraph(ArrayList<Dot> dots, double step, double interval, double endTime) throws Exception{
		super();
		/* You can start when you have half of the interval at the left of the point you're computing */
		double currentStep = interval / 2;
		ArrayList<Couple<Interval, ArrayList<Double>>> couples = new ArrayList<Couple<Interval, ArrayList<Double>>>();
		
		/* Create the intevals with an arraylist */
		while((currentStep + (interval/2)) <= endTime){
			couples.add(new Couple<Interval, ArrayList<Double>>(Interval.getIntervalWithCenter(currentStep, interval), new ArrayList<Double>()));
			currentStep += step;
		}
		if(couples.size() == 0){
			throw new BlkException("Can't create average graph, bad parameters (no dots found, probably first dot after the end)");
		}
		
		/* Adds the dots to the correct intervals */
		for(Dot d : dots){
			for(Couple<Interval, ArrayList<Double>> c : couples){
				if(c.getLeft().isInside(d.getX())){
					c.getRight().add(d.getY());
				}
			}
		}
		
		/* Then do the average per interval and add it to the graph plots */
		for(Couple<Interval, ArrayList<Double>> c : couples){
			double average = 0;
			if(c.getRight().size() != 0){
				for(Double d : c.getRight()){
					average += d;
				}
				average /= c.getRight().size();
				this.dots.add(new Dot(c.getLeft().getCenter(), average));
			}
		}
	}
	
	/* A class for combining two items, one left, one right */
	private class Couple<T,U>{
		private T gauche;
		private U droit;
		
		public T getLeft() {
			return gauche;
		}
		public U getRight() {
			return droit;
		}
		public Couple(T gauche, U droit) {
			super();
			this.gauche = gauche;
			this.droit = droit;
		}
	}
}