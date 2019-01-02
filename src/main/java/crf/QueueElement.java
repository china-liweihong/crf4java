package crf;

public class QueueElement {
	private Node node;
	private QueueElement next;
	private double fx;
	private double gx;
	public Node getNode() {
		return node;
	}
	public void setNode(Node node) {
		this.node = node;
	}
	public QueueElement getNext() {
		return next;
	}
	public void setNext(QueueElement next) {
		this.next = next;
	}
	public double getFx() {
		return fx;
	}
	public void setFx(double fx) {
		this.fx = fx;
	}
	public double getGx() {
		return gx;
	}
	public void setGx(double gx) {
		this.gx = gx;
	}
}
