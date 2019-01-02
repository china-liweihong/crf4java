package crf;

public class Crf_term_out {
	public double prob;

	// Raw CRF model output
	public String[] result_;
	public double[] weight_;

	public int max_word_num = Utils.DEFAULT_CRF_MAX_WORD_NUM;

	public Crf_term_out(int max_word_num) {
		prob = 0;
		this.max_word_num = max_word_num;
		result_ = new String[max_word_num];
		weight_ = new double[max_word_num];
	}
}
