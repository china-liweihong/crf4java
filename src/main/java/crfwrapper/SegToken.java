package crfwrapper;

public class SegToken {
	public int offset;
	public int length;
	public String strTag; // CRF对应于term组合后的Tag字符串
	public double fWeight; // 对应属性id的概率值，或者得分
}
