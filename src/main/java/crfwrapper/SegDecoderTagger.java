package crfwrapper;

import crf.DecoderTagger;
import crf.Utils;

public class SegDecoderTagger extends DecoderTagger {

	private int this_crf_max_word_num = Utils.DEFAULT_CRF_MAX_WORD_NUM;

	public SegDecoderTagger(int nbest, int this_crf_max_word_num) {
		super(nbest, this_crf_max_word_num);
		this.this_crf_max_word_num = this_crf_max_word_num;
		this.crf_max_word_num = this_crf_max_word_num;
	}

	int seg_termbuf_build(Crf_seg_out term_buf) {
		term_buf.Clear();

		// build raw result at first
		int iRet = termbuf_build(term_buf);
		if (iRet != Utils.ERROR_SUCCESS) {
			return iRet;
		}

		// Then build token result
		int term_len = 0;
		double weight = 0.0;
		int num = 0;
		for (int i = 0; i < x_.size(); i++) {
			// Adding the length of current token
			String strTag = term_buf.result_[i];
			term_len += x_.get(i).get(0).length();
			weight += term_buf.weight_[i];
			num++;

			// Check if current term is the end of a token
			if ((strTag.startsWith("B_") == false && strTag.startsWith("M_") == false) || i == x_.size() - 1) {
				SegToken tkn = new SegToken();
				tkn.length = term_len;
				tkn.offset = term_buf.termTotalLength;

				int spos = strTag.indexOf('_');
				if (spos < 0) {
					if (strTag == "NOR") {
						tkn.strTag = "";
					} else {
						tkn.strTag = strTag;
					}
				} else {
					tkn.strTag = strTag.substring(spos + 1);
				}

				term_buf.termTotalLength += term_len;
				// Calculate each token's weight
				switch (vlevel_) {
				case 0:
					tkn.fWeight = 0.0;
					break;
				case 2:
					tkn.fWeight = weight / num;
					weight = 0.0;
					num = 0;
					break;
				}

				term_buf.tokenList.add(tkn);
				term_len = 0;
			}
		}

		return Utils.ERROR_SUCCESS;
	}

	public int output(Crf_seg_out[] pout) {
		int n = 0;
		int ret = 0;

		if (nbest_ == 1) {
			// If only best result and no need probability, "next" is not to be used
			ret = seg_termbuf_build(pout[0]);
			if (ret < 0) {
				return ret;
			}
		} else {
			// Fill the n best result
			int iNBest = nbest_;
			if (pout.length < iNBest) {
				iNBest = pout.length;
			}

			for (n = 0; n < iNBest; ++n) {
				ret = next();
				if (ret < 0) {
					break;
				}

				ret = seg_termbuf_build(pout[n]);
				if (ret < 0) {
					return ret;
				}
			}
		}

		return Utils.ERROR_SUCCESS;
	}

}
