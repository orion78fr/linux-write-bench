package fr.upmc.stage.parser;

public class BlkOp {
	/* Queued in io sched -> Submitted to driver -> Completed by driver */
	private double timeQueued;    /* Q */
	private double timeSubmitted = -1; /* D */;
	private double timeCompleted = -1; /* C */
	private double timeMerged = -1;    /* M */

	private RWBS reqType;
	
	private long blkNum;
	private int numberOfBlocks;
	
	private long pid;
	
	private BlkOp merged = null;
	private boolean isFirst = true;
	
	public boolean isFirst() {
		return isFirst;
	}
	public void setIsFirst(boolean isFirst) {
		this.isFirst = isFirst;
	}
	public BlkOp(double timeQueued, RWBS reqType, long blkNum, int numberOfBlocks, long pid) {
		super();
		this.timeQueued = timeQueued;
		this.reqType = reqType;
		this.blkNum = blkNum;
		this.numberOfBlocks = numberOfBlocks;
		this.pid = pid;
	}
	public double getTimeSubmitted() throws BlkException {
		if(this.timeSubmitted == -1) throw new BlkException("Time submitted not set \t" + this.getBasicInfo());
		return this.timeSubmitted;
	}
	public boolean isMerged(){
		return merged != null;
	}
	public void setTimeSubmitted(double timeSubmitted) {
		this.timeSubmitted = timeSubmitted;
		if(this.isMerged()){
			this.merged.setTimeSubmitted(timeSubmitted);
		}
	}
	public double getTimeCompleted() throws BlkException {
		if(this.timeCompleted == -1) throw new BlkException("Time completed not set \t" + this.getBasicInfo());
		return this.timeCompleted;
	}
	public void setTimeCompleted(double timeCompleted) {
		this.timeCompleted = timeCompleted;
		if(this.isMerged()){
			this.merged.setTimeCompleted(timeCompleted);
		}
	}
	public int getNumberOfBlocks() {
		return this.numberOfBlocks;
	}
	public void setNumberOfBlocks(int numberOfBlocks) {
		this.numberOfBlocks = numberOfBlocks;
	}
	public double getTimeQueued() {
		return this.timeQueued;
	}
	public RWBS getReqType() {
		return this.reqType;
	}
	public long getBlkNum() {
		return this.blkNum;
	}
	public double getTimeMerged() {
		return timeMerged;
	}
	public void setTimeMerged(double timeMerged) {
		this.timeMerged = timeMerged;
	}
	
	public void merge(BlkOp op) throws BlkException {
		if(this.merged != null) throw new BlkException("Already merged \t" + this.getBasicInfo());
		this.merged = op;
		op.setIsFirst(false);
	}
	
	public long getPid() {
		return pid;
	}
	
	public String getBasicInfo(){
		StringBuffer sb = new StringBuffer();
		sb.append("Time Queued : ");
		sb.append(this.getTimeQueued());
		sb.append("\tBlock Number : ");
		sb.append(this.getBlkNum());
		sb.append("\tOperation type : ");
		sb.append(this.getReqType().name());
		return sb.toString();
	}
	public double getOpLength() {
		if(this.isMerged()){
			return this.numberOfBlocks + this.merged.getNumberOfBlocks();
		}
		else {
			return this.numberOfBlocks;
		}
	}
}