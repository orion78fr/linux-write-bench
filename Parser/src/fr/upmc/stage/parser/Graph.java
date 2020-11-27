package fr.upmc.stage.parser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;

public class Graph {
	protected ArrayList<Dot> dots;
	
	protected Graph(){
		this.dots = new ArrayList<Dot>();
	}
	
	public Graph(ArrayList<Dot> dots) {
		super();
		this.dots = dots;
	}



	public void exportToFile(String filename) throws Exception{
		/* Eport X then Y on each line */
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(filename))));
		Collections.sort(this.dots);
		for(Dot d : this.dots){
			bw.write(d.getX() + " " + d.getY() + "\n");
		}
		bw.close();
	}
}
