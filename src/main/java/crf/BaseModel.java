package crf;

import java.util.List;

public class BaseModel {
	public int maxid_;
	public double cost_factor_;

	public List<String> unigram_templs_;
	public List<String> bigram_templs_;

	// Labeling tag list
	public List<String> y_;

	public int ysize() {
		return y_.size();
	}

	// The dimension training corpus
	public int xsize_;

	// Feature set value array
	public double[] alpha_;

	public BaseModel() {
		cost_factor_ = 1.0;
	}

	// 获取类别i的字符表示
	public String y(int i) {
		return y_.get(i);
	}
	

	public int feature_size() {
		return maxid_;
	}

	public StringBuffer apply_rule(String p, int pos, StringBuffer resultContainer, Tagger tagger) {
		resultContainer = new StringBuffer();
		for (int i = 0; i < p.length(); i++) {
			if (p.charAt(i) == '%') {
				i++;
				if (p.charAt(i) == 'x') {
					i++;
					Index res = get_index(p, pos, i, tagger);
					i = res.idx;
					if (res.value == null) {
						return null;
					}
					resultContainer.append(res.value);
				} else {
					return null;
				}
			} else {
				resultContainer.append(p.charAt(i));
			}
		}
		return resultContainer;
	}

	Index get_index(String p, int pos, int i, Tagger tagger) {
		if (p.charAt(i) != '[') {
			return new Index(null, i);
		}
		i++;
		boolean isInRow = true;
		int col = 0;
		int row = 0;
		int neg = 1;

		if (p.charAt(i) == '-') {
			neg = -1;
			i++;
		}

		for (; i < p.length(); i++) {
			char c = p.charAt(i);
			if (isInRow) {
				if (c >= '0' && c <= '9') {
					row = 10 * row + (c - '0');
				} else if (c == ',') {
					isInRow = false;
				} else {
					return new Index(null, i);
				}
			} else {
				if (c >= '0' && c <= '9') {
					col = 10 * col + (c - '0');
				} else if (c == ']') {
					break;
				} else {
					return new Index(null, i);
				}
			}
		}

		row *= neg;

		if (col < 0 || col >= xsize_) {
			return new Index(null, i);
		}
		int idx = pos + row;
		if (idx < 0) {
			return new Index("_B-" + String.valueOf(-idx), i);
		}
		if (idx >= tagger.word_num) {
			return new Index("_B+" + String.valueOf(idx - tagger.word_num + 1), i);
		}

		return new Index(tagger.x_.get(idx).get(col), i);

	}

	private class Index {
		public int idx;
		public String value;

		/// <summary>
		/// Initializes a new instance of the <see cref="T:System.Object"/> class.
		/// </summary>
		public Index(String value, int idx) {
			this.idx = idx;
			this.value = value;
		}
	}
}
