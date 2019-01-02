package crf;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class EncoderTagger extends Tagger {
	public ModelWriter feature_index_;
	public short[] answer_;

	public EncoderTagger(ModelWriter modelWriter) {
		feature_index_ = modelWriter;
		ysize_ = (short) feature_index_.ysize();
	}

	public boolean generateFeature(List<List<String>> recordList) {
		word_num = (short) recordList.size();
		if (word_num == 0) {
			return false;
		}

		// Try to find each record's answer tag
		int x_num = 0;
		int xsize = (int) feature_index_.xsize_;
		answer_ = new short[word_num];
		for (int index = 0; index < recordList.size(); index++) {
			List<String> record = recordList.get(index);
			// get result tag's index and fill answer
			for (short k = 0; k < ysize_; k++) {
				if (feature_index_.y(k).equals(record.get(xsize))) {
					answer_[x_num] = k;
					break;
				}
			}
			x_num++;
		}

		// Build record feature set
		x_ = recordList;
		Z_ = 0.0;
		feature_cache_ = new ArrayList();
		feature_index_.buildFeatures(this);
		x_ = null;

		return true;
	}

	public void init(int[] result, Node[][] node) {
		result_ = result;
		node_ = node;
	}

	public int eval(int[][] merr) {
		int err = 0;
		for (int i = 0; i < word_num; ++i) {
			if (answer_[i] != result_[i]) {
				++err;
				merr[answer_[i]][result_[i]]++;
			}
		}
		return err;
	}

	public double gradient(double[] expected) {
		buildLattice();
		forwardbackward();
		double s = 0.0;

		for (int i = 0; i < word_num; ++i) {
			for (int j = 0; j < ysize_; ++j) {
				calcExpectation(i, j, expected);
			}
		}

		for (int i = 0; i < word_num; ++i) {
			short answer_val = answer_[i];
			Node answer_Node = node_[i][answer_val];
			int offset = answer_val + 1; // since expected array is based on 1
			for (int index = 0; index < feature_cache_.get(answer_Node.getFid()).length; index++) {
				int fid = (int) feature_cache_.get(answer_Node.getFid())[index];
				lockFreeAdd(expected, fid + offset, -1.0f);
			}
			s += answer_Node.getCost(); // UNIGRAM cost

			for (int index = 0; index < answer_Node.getLpathList().size(); index++) {
				Path lpath = answer_Node.getLpathList().get(index);
				if (lpath.getLnode().getY() == answer_[lpath.getLnode().getX()]) {
					offset = lpath.getLnode().getY() * ysize_ + lpath.getRnode().getY() + 1;
					for (int index1 = 0; index1 < feature_cache_.get(lpath.getFid()).length; index1++) {
						int fid = (int) feature_cache_.get(lpath.getFid())[index1];
						lockFreeAdd(expected, fid + offset, -1.0f);
					}

					s += lpath.getCost(); // BIGRAM COST
					break;
				}
			}
		}

		viterbi(); // call for eval()
		return Z_ - s;
	}

	private void lockFreeAdd(double[] expected, int exp_offset, double addValue) {
		double initialValue;
		double newValue;
		initialValue = expected[exp_offset]; // read current value
		newValue = initialValue + addValue; // calculate new value
		expected[exp_offset] = newValue;
	}

	private void calcExpectation(int x, int y, double[] expected) {
		Node n = node_[x][y];
		double c = Math.exp(n.getAlpha() + n.getBeta() - n.getCost() - Z_);
		int offset = y + 1; // since expected array is based on 1
		for (int index = 0; index < feature_cache_.get(n.getFid()).length; index++) {
			int item = (int) feature_cache_.get(n.getFid())[index];
			lockFreeAdd(expected, item + offset, c);
		}

		for (int index = 0; index < n.getLpathList().size(); index++) {
			Path p = n.getLpathList().get(index);
			c = Math.exp(p.getLnode().getAlpha() + p.getCost() + p.getRnode().getBeta() - Z_);
			offset = p.getLnode().getY() * ysize_ + p.getRnode().getY() + 1; // since expected array is based on 1
			for (int i = 0; i < feature_cache_.get(p.getFid()).length; i++) {
				int item = (int) feature_cache_.get(p.getFid())[i];
				lockFreeAdd(expected, item + offset, c);
			}
		}
	}

	public void buildLattice() {
		rebuildFeatures();
		for (int i = 0; i < word_num; ++i) {
			for (int j = 0; j < ysize_; ++j) {
				Node node_i_j = node_[i][j];
				node_i_j.setCost(calcCost(node_i_j.getFid(), j));
				for (int index = 0; index < node_i_j.getLpathList().size(); index++) {
					Path p = node_i_j.getLpathList().get(index);
					int offset = p.getLnode().getY() * ysize_ + p.getRnode().getY();
					p.setCost(calcCost(p.getFid(), offset));
				}
			}
		}
	}

	public double calcCost(int featureListIdx, int offset) {
		double c = 0.0f;
		offset++; // since alpha_ array is based on 1
		for (int index = 0; index < feature_cache_.get(featureListIdx).length; index++) {
			int fid = (int) feature_cache_.get(featureListIdx)[index];
			c += feature_index_.alpha_[fid + offset];
		}
		return feature_index_.cost_factor_ * c;
	}
}
