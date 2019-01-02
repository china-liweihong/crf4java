package crfwrapper;

import java.util.ArrayList;
import java.util.List;

import crf.Crf_term_out;

public class Crf_seg_out extends Crf_term_out {

	// Segmented token by merging raw CRF model output
	public int termTotalLength; // the total term length in character
	public List<SegToken> tokenList;
	private int count;

	public int getCount() {
		return tokenList.size();
	}

	public Crf_seg_out(int max_word_num) {
		super(max_word_num);

		termTotalLength = 0;
		tokenList = new ArrayList<SegToken>();
	}

	public void Clear() {
		termTotalLength = 0;
		tokenList.clear();
		tokenList = new ArrayList<>();
	}

}
