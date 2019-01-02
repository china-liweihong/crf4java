package crf;

import java.util.List;

import com.hankcs.hanlp.dependency.nnparser.parser_dll;

public class Tagger {
	public List<List<String>> x_;
	public Node[][] node_; // Node matrix
	public int ysize_;
	public int word_num; // the number of tokens need to be labeled
	public double Z_; // 概率值
	public double cost_; // The path cost
	public int[] result_;
	public List<long[]> feature_cache_;

	// Calculate the cost of each path. It's used for finding the best or N-best
	// result
	public int viterbi() {
		double bestc = Double.MIN_VALUE;
		Node bestNode = null;

		for (int i = 0; i < word_num; ++i) {
			for (int j = 0; j < ysize_; ++j) {
				bestc = Double.MIN_VALUE;
				bestNode = null;

				Node node_i_j = node_[i][j];

				for (int index = 0; index < node_i_j.getLpathList().size(); ++index) {
					Path p = node_i_j.getLpathList().get(index);
					double cost = p.getLnode().getBestCost() + p.getCost() + node_i_j.getCost();
					if (cost > bestc) {
						bestc = cost;
						bestNode = p.getLnode();
					}
				}

				node_i_j.setPrev(bestNode);
				node_i_j.setBestCost(bestNode != null ? bestc : node_i_j.getCost());
			}
		}

		bestc = Double.MIN_VALUE;
		bestNode = null;

		short s = (short) (word_num - 1);
		for (short j = 0; j < ysize_; ++j) {
			if (bestc < node_[s][j].getBestCost()) {
				bestNode = node_[s][j];
				bestc = node_[s][j].getBestCost();
			}
		}

		Node n = bestNode;
		while (n != null) {
			result_[n.getX()] = n.getY();
			n = n.getPrev();
		}

		cost_ = -node_[s][result_[s]].getBestCost();

		return Utils.ERROR_SUCCESS;
	}

	public void forwardbackward() {
		for (int i = 0, k = word_num - 1; i < word_num; ++i, --k) {
			for (int j = 0; j < ysize_; ++j) {
				calcAlpha(i, j);
				calcBeta(k, j);
			}
		}

		Z_ = 0.0;
		for (int j = 0; j < ysize_; ++j) {
			Z_ = Utils.logsumexp(Z_, node_[0][j].getBeta(), j == 0);
		}
	}

	private void calcAlpha(int m, int n) {
		Node nd = node_[m][n];
		nd.setAlpha(0.0);

		int i = 0;
		for (int index = 0; index < nd.getLpathList().size(); index++) {
			Path p = nd.getLpathList().get(index);
			nd.setAlpha(Utils.logsumexp(nd.getAlpha(), p.getCost() + p.getLnode().getAlpha(), (i == 0)));
			i++;
		}
		nd.setAlpha(nd.getAlpha() + nd.getCost());
	}

	private void calcBeta(int m, int n) {
		Node nd = node_[m][n];
		nd.setBeta(0.0);
		if (m + 1 < word_num) {
			int i = 0;
			for (int index = 0; index < nd.getRpathList().size(); index++) {
				Path p = nd.getRpathList().get(index);
				nd.setBeta(Utils.logsumexp(nd.getBeta(), p.getCost() + p.getRnode().getBeta(), (i == 0)));
				i++;
			}
		}
		nd.setBeta(nd.getBeta() + nd.getCost());
	}

	public int rebuildFeatures() {
		int fid = 0;
		for (short cur = 0; cur < word_num; ++cur) {
			for (short i = 0; i < ysize_; ++i) {
				node_[cur][i].setFid(fid);
				if (cur > 0) {
					Node previousNode = node_[cur - 1][i];
					for (int index = 0; index < previousNode.getRpathList().size(); ++index) {
						Path path = previousNode.getRpathList().get(index);
						path.setFid(fid + word_num - 1);
					}
				}
			}

			++fid;
		}

		return 0;
	}
}
