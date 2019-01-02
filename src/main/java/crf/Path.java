package crf;

public class Path {
	private int fid;
	private Node rnode;
	private Node lnode;
	private double cost;

	public int getFid() {
		return fid;
	}

	public void setFid(int fid) {
		this.fid = fid;
	}

	public Node getRnode() {
		return rnode;
	}

	public void setRnode(Node rnode) {
		this.rnode = rnode;
	}

	public Node getLnode() {
		return lnode;
	}

	public void setLnode(Node lnode) {
		this.lnode = lnode;
	}

	public double getCost() {
		return cost;
	}

	public void setCost(double cost) {
		this.cost = cost;
	}

	public Path() {
		rnode = null;
		lnode = null;
		cost = 0;
	}

	public void add(Node _lnode, Node _rnode) {
		this.lnode = _lnode;
		this.rnode = _rnode;

		this.lnode.getRpathList().add(this);
		this.rnode.getLpathList().add(this);
	}
}
