package crf;

import java.util.List;

public class Node {
	private int fid;
	private int x;
	private int y;
	private double alpha;
	private double beta;
	private double cost;
	private double bestCost;
	private Node prev;

	private List<Path> lpathList;
	private List<Path> rpathList;
	public int getFid() {
		return fid;
	}
	public void setFid(int fid) {
		this.fid = fid;
	}
	public int getX() {
		return x;
	}
	public void setX(int x) {
		this.x = x;
	}
	public int getY() {
		return y;
	}
	public void setY(int y) {
		this.y = y;
	}
	public double getAlpha() {
		return alpha;
	}
	public void setAlpha(double alpha) {
		this.alpha = alpha;
	}
	public double getBeta() {
		return beta;
	}
	public void setBeta(double beta) {
		this.beta = beta;
	}
	public double getCost() {
		return cost;
	}
	public void setCost(double cost) {
		this.cost = cost;
	}
	public double getBestCost() {
		return bestCost;
	}
	public void setBestCost(double bestCost) {
		this.bestCost = bestCost;
	}
	public Node getPrev() {
		return prev;
	}
	public void setPrev(Node prev) {
		this.prev = prev;
	}
	public List<Path> getLpathList() {
		return lpathList;
	}
	public void setLpathList(List<Path> lpathList) {
		this.lpathList = lpathList;
	}
	public List<Path> getRpathList() {
		return rpathList;
	}
	public void setRpathList(List<Path> rpathList) {
		this.rpathList = rpathList;
	}
}
