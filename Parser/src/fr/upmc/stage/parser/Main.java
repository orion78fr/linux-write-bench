package fr.upmc.stage.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

@SuppressWarnings("unused")
public class Main {
	private static int split_void = 0;
	private static int split_device = 1;
	private static int split_cpuid = 2;
	private static int split_seqnum = 3;
	private static int split_time = 4;
	private static int split_pid = 5;
	private static int split_action = 6;
	private static int split_rwbs = 7;
	/* Only in certain cases! */
	private static int split_blknum = 8;
	private static int split_plus = 9;
	private static int split_numofblk = 10;
	private static int split_pname = 11;
	
	private static ArrayList<BlkOp> timeList = new ArrayList<BlkOp>();
	private static ArrayList<Dot> ioQueueSize = new ArrayList<Dot>();
	private static long currentIoSize = 0;
	private static HashMap<Long, BlkOp> blkList = new HashMap<Long, BlkOp>();
	/* useful for merge ops (from back - M op) */
	private static HashMap<Long, BlkOp> blkEndList = new HashMap<Long, BlkOp>();
	
	private static PrintStream errlog;
	
	private static String outDir = ".";
	private static String inFile;
	
	private static void parseArgs(String args[]){
		for(int i=0; i<args.length; i++){
			if(args[i].equals("-o") && ++i != args.length){
				outDir = args[i];
			}else if(args[i].equals("-i") && ++i != args.length){
				inFile = args[i];
			}
		}
	}
	
	public static void main(String args[]) throws Exception {
		System.out.println("Start!");
		
		parseArgs(args);
		
		if(inFile == null){
			System.err.println("No input file specified, exiting...");
			return;
		}
		if(!outDir.endsWith("/")){
			outDir+="/";
		}
		File outDirFile = new File(outDir);
		if(!outDirFile.exists()){
			if(new File(outDir).mkdirs() != true){
				System.err.println("Cannot create out folder");
				return;
			}
		}
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(inFile))));
		errlog = new PrintStream(new File(outDir+"error.log"));
		String line = null;
		
		/* Read each line and parse it */
		while((line = br.readLine()) != null){
			if(!line.startsWith("  ")){
				break;
			}
			parseLine(line);
		}
		System.out.println("Number of ops is : " + timeList.size());
		
		ArrayList<Dot> dotsQC = new ArrayList<Dot>();
		ArrayList<Dot> dotsQD = new ArrayList<Dot>();
		ArrayList<Dot> dotsDC = new ArrayList<Dot>();
		ArrayList<Dot> dotsIOSize = new ArrayList<Dot>();
		ArrayList<Dot> dotsBlkNums = new ArrayList<Dot>();
		double blkAverage = 0;
		double average = 0;
		double endTime = 0;
		double throughput = 0, throughputRw = 0;
		
		/* Now that all lines are parsed, do ops on it! */
		for(BlkOp op : timeList){
			try{
				if(op.getTimeCompleted() > endTime){
					endTime = op.getTimeCompleted();
				}
				average += (op.getTimeCompleted() - op.getTimeQueued());
				dotsQC.add(new Dot(op.getTimeQueued(), op.getTimeCompleted() - op.getTimeQueued()));
				dotsQD.add(new Dot(op.getTimeQueued(), op.getTimeSubmitted() - op.getTimeQueued()));
				dotsDC.add(new Dot(op.getTimeQueued(), op.getTimeCompleted() - op.getTimeSubmitted()));
				blkAverage += op.getBlkNum();
				dotsBlkNums.add(new Dot(op.getTimeCompleted(), op.getBlkNum()));
				if(op.isFirst()){
					if(op.getReqType().equals(RWBS.write)){
						dotsIOSize.add(new Dot(op.getTimeQueued(), op.getOpLength()));
						throughput += op.getOpLength();
					}
					throughputRw += op.getOpLength();
				}
			} catch(Exception e) {	
				/* Error is just logged, the op will not be plotted */
				errlog.println(e.getMessage());
			}
		}
		average /= timeList.size();
		throughput /= endTime;
		throughputRw /= endTime;
		
		Graph g = new CentralMovingAverageGraph(dotsQC, endTime/100, endTime/50, endTime);
		g.exportToFile(outDir+"avGraphQC.txt");	
		
		Graph g2 = new CentralMovingAverageGraph(dotsQD, endTime/100, endTime/50, endTime);
		g2.exportToFile(outDir+"avGraphQD.txt");
		
		Graph g3 = new CentralMovingAverageGraph(dotsDC, endTime/100, endTime/50, endTime);
		g3.exportToFile(outDir+"avGraphDC.txt");
		
		//Graph g4 = new CentralMovingAverageGraph(dotsIOSize, endTime/100, endTime/50, endTime);
		Graph g4 = new Graph(dotsIOSize);
		g4.exportToFile(outDir+"graphIOSize.txt");
		
		Graph g5 = new CentralMovingAverageGraph(ioQueueSize, endTime/100, endTime/50, endTime);
		g5.exportToFile(outDir+"graphIOQueueSize.txt");
		
		/* Essai de retirer les points abérrents */
		blkAverage /= dotsBlkNums.size();
		Collections.sort(dotsBlkNums, new Comparator<Dot>() {
			@Override
			public int compare(Dot o1, Dot o2) {
				return new Double(o1.getY()).compareTo(o2.getY()); /* Tri par y */
			}
		});
		double Q1 = dotsBlkNums.get((int)(0.25*dotsBlkNums.size())).getY();
		double Q3 = dotsBlkNums.get((int)(0.75*dotsBlkNums.size())).getY();
		ArrayList<Dot> dotsBlkNums2 = new ArrayList<Dot>();
		System.out.println("Q1 : " + Q1 + "\nQ3 : " + Q3 + "\nQ3-Q1 : " + (Q3-Q1) + "\nMin : " + (2*Q1 - Q3) + "\n Max : " + (2*Q3 - Q1));
		for(Dot d : dotsBlkNums){
			if (d.getY() > (2*Q1 - Q3) && d.getY() < (2*Q3 - Q1)){
				dotsBlkNums2.add(d);
			}
		}
		Graph g6 = new Graph(dotsBlkNums2);
		g6.exportToFile(outDir+"graphBlkNums.txt");
		
		System.out.println("File : " + inFile);
		System.out.println("The average time in writeback (Q->C) is : " + average);
		System.out.println("The throughput is : " + ((throughput / 2) / 1024) + " Mo/s");
		System.out.println("The throughput R/W is : " + ((throughputRw / 2) / 1024) + " Mo/s");
		double avIOQueueSize = 0;
		for(int i=1; i<ioQueueSize.size(); i++){
			avIOQueueSize += (ioQueueSize.get(i).getX() - ioQueueSize.get(i-1).getX())*ioQueueSize.get(i).getY();
		}
		avIOQueueSize /= endTime;
		System.out.println("The average IO Queue Size is : " + avIOQueueSize);
		br.close();
	}
	public static void parseLine(String line) throws BlkException{
		String splitted[] = line.split(" +");
		Double time = Double.parseDouble(splitted[split_time]);
		if(splitted[split_action].equals("Q")){
			/* Queued in io sched */
			RWBS rwbs;
			if(splitted[split_rwbs].contains("F")){
				/* Don't know what it is, but it's odd, seems to be some kind of flush ops, but with no block number */
				return;
			} else if(splitted[split_rwbs].contains("R")){
				rwbs = RWBS.read;
			} else if(splitted[split_rwbs].contains("W")) {
				rwbs = RWBS.write;
			} else {
				/* Nothing interesting here? */
				return;
			}
			Long blknum = Long.parseLong(splitted[split_blknum]);
			Integer numofblk = Integer.parseInt(splitted[split_numofblk]);
			
			currentIoSize += numofblk;
			ioQueueSize.add(new Dot(time, currentIoSize));
		
			BlkOp newOp = new BlkOp(time,
					rwbs,
					blknum,
					numofblk,
					Long.parseLong(splitted[split_pid]));
			
			timeList.add(newOp);
			blkList.put(blknum, newOp);
			blkEndList.put(blknum + numofblk, newOp);
		} else if(splitted[split_action].equals("D")){
			/* Submitted to driver */
			BlkOp op = blkList.get(Long.parseLong(splitted[split_blknum]));
			
			currentIoSize -= Integer.parseInt(splitted[split_numofblk]);
			ioQueueSize.add(new Dot(time, currentIoSize));
			
			if(op == null){
				errlog.println("[D] Problem with blk " + Long.parseLong(splitted[split_blknum]) + ", not found in the hashmap...");
				return;
			}
			op.setTimeSubmitted(time);
		} else if(splitted[split_action].equals("C")){
			/* Completed by driver */
			BlkOp op = blkList.get(Long.parseLong(splitted[split_blknum]));
			if(op == null){
				errlog.println("[C] Problem with blk " + Long.parseLong(splitted[split_blknum]) + ", not found in the hashmap...");
				return;
			}
			op.setTimeCompleted(time);
		} else if(splitted[split_action].equals("M")){
			/* Merge op */
			Long blknum = Long.parseLong(splitted[split_blknum]);
			/* Get the op to merge */
			BlkOp op = blkList.get(blknum);
			if(op == null){
				errlog.println("[M] Problem with blk " + blknum + ", not found in the hashmap...");
				return;
			}
			/* Get the op to merge with */
			BlkOp mergeOp = blkEndList.get(blknum);
			if(mergeOp == null){
				errlog.println("[M] Unable to find blk which end with " + blknum + ", not found in the hashmap...");
				return;
			}
			/* Add it to the merged list to propagate the times */
			mergeOp.merge(op);
		} else if(splitted[split_action].equals("F")){
			/* Merge from front */
			Long blknum = Long.parseLong(splitted[split_blknum]);
			/* Get the op to merge */
			BlkOp op = blkList.get(blknum);
			if(op == null){
				errlog.println("[F] Problem with blk " + blknum + ", not found in the hashmap...");
				return;
			}
			/* Get the op to merge with */
			BlkOp mergeOp = blkList.get(blknum + op.getNumberOfBlocks());
			if(mergeOp == null){
				errlog.println("[F] Unable to find blk which start with " + (blknum + op.getNumberOfBlocks()) + ", not found in the hashmap...");
				return;
			}
			/* Add it to the merged list to propagate the times */
			op.merge(mergeOp);
		} else {
			/* Nothing interesting here? */
			return;
		}
	}
}
