package crf;

import java.util.ArrayList;
import java.util.List;

public class DecoderTagger extends Tagger {
	public int forward_backward_stat; // 前向后向过程运行状态，0为未运行，1为已经运行
	public int vlevel_; // Need to calculate probability 0 - no need to calculate, 1 - calculate
						// sequence label probability, 2 - calculate both sequence label and individual
						// entity probability
	protected int nbest_; // output top N-best result

	// 概率计算函数
	double toprob(Node n, double Z) {
		return Math.exp(n.getAlpha() + n.getBeta() - Z);
	}

	// CrfModel model;
	ModelReader featureIndex;

	private Node node(int i, int j) {
		return node_[i][j];
	}

	Heap heap_queue; // Using min-heap to get next result, it's only used when nbest > 1
	public int crf_max_word_num = Utils.DEFAULT_CRF_MAX_WORD_NUM;

	public DecoderTagger(int nbest, int this_crf_max_word_num) {
		crf_max_word_num = this_crf_max_word_num;
		vlevel_ = 0;
		nbest_ = nbest;
		cost_ = 0.0;
		Z_ = 0;

		ysize_ = 0;
		word_num = 0;
		heap_queue = null;
		node_ = null;
		x_ = null;
		result_ = null;
	}

	public void InitializeFeatureCache() {
		feature_cache_ = new ArrayList();
		int feature_cache_every_row_size = 0;
		if (featureIndex.unigram_templs_.size() > featureIndex.bigram_templs_.size()) {
			feature_cache_every_row_size = featureIndex.unigram_templs_.size() + 1;
		} else {
			feature_cache_every_row_size = featureIndex.bigram_templs_.size() + 1;
		}
		for (int i = 0; i < crf_max_word_num * 2; i++) {
			long[] features = new long[feature_cache_every_row_size];
			for (int j = 0; j < feature_cache_every_row_size; j++) {
				features[j] = -1;
			}
			feature_cache_.add(features);
		}
	}

	// 获取序列的词数
	public int get_word_num() {
		return word_num;
	}

	public double prob(int i, int j) {
		return toprob(node_[i][j], Z_);
	}

	// Get the probability of the i-th word's best result
	public double prob(int i) {
		return toprob(node_[i][result_[i]], Z_);
	}

	// Get entire sequence probability
	public double prob() {
		return Math.exp(-cost_ - Z_);
	}

	// Get the string of i-th tag
	public String yname(int i) {
		return featureIndex.y(i);
	}

	// 设置vlevel
	public void set_vlevel(int vlevel_value) {
		vlevel_ = vlevel_value;
	}

	// 使用模型初始化tag，必须先使用该函数初始化才能使用add和parse
	// 正常返回为0， 错误返回<0
	public int init_by_model(ModelReader model_p) {
		featureIndex = model_p;
		ysize_ = (short) model_p.ysize();

		if (nbest_ > 1) {
			// Only allocate heap when nbest is more than 1
			heap_queue = Utils.heap_init((int) (crf_max_word_num * ysize_ * ysize_));
		}

		// Initialize feature set cache according unigram and bigram templates
		InitializeFeatureCache();

		node_ = new Node[crf_max_word_num][ysize_];
		result_ = new int[crf_max_word_num];

		// Create node and path cache
		for (short cur = 0; cur < crf_max_word_num; cur++) {
			for (short i = 0; i < ysize_; i++) {
				Node n = new Node();
				node_[cur][i] = n;

				n.setLpathList(new ArrayList<Path>());
				n.setRpathList(new ArrayList<Path>());
				n.setX(cur);
				n.setY(i);
			}
		}

		for (int cur = 1; cur < crf_max_word_num; cur++) {
			for (int j = 0; j < ysize_; ++j) {
				for (int i = 0; i < ysize_; ++i) {
					Path p = new Path();
					p.add(node_[cur - 1][j], node_[cur][i]);
				}
			}
		}

		return Utils.ERROR_SUCCESS;
	}

	public int initNbest() {
		int k = word_num - 1;
		for (int i = 0; i < ysize_; ++i) {
			QueueElement eos = Utils.allc_from_heap(heap_queue);
			eos.setNode(node_[k][i]);
			eos.setFx(-node_[k][i].getBestCost());
			eos.setGx(-node_[k][i].getCost());
			eos.setNext(null);
			if (Utils.heap_insert(eos, heap_queue) < 0) {
				return Utils.ERROR_INSERT_HEAP_FAILED;
			}
		}
		return Utils.ERROR_SUCCESS;
	}

	public int next() {
		while (!Utils.is_heap_empty(heap_queue)) {
			QueueElement top = Utils.heap_delete_min(heap_queue);
			Node rnode = top.getNode();

			if (rnode.getX() == 0) {
				for (QueueElement n = top; n != null; n = n.getNext()) {
					result_[n.getNode().getX()] = n.getNode().getY();
				}
				cost_ = top.getGx();
				return 0;
			}

			for (int index = 0; index < rnode.getLpathList().size(); index++) {
				Path p = rnode.getLpathList().get(index);
				QueueElement n = Utils.allc_from_heap(heap_queue);
				int x_num = (rnode.getX()) - 1;
				n.setNode(p.getLnode());
				n.setGx(-p.getLnode().getCost() - p.getCost() + top.getGx());
				n.setFx(-p.getLnode().getBestCost() - p.getCost() + top.getGx());
				// | h(x) | | g(x) |
				n.setNext(top);
				if (Utils.heap_insert(n, heap_queue) < 0) {
					return Utils.ERROR_INSERT_HEAP_FAILED;
				}
			}
		}
		return 0;
	}

	public int reset() {
		word_num = 0;
		Z_ = cost_ = 0.0;

		Utils.heap_reset(heap_queue);
		return Utils.ERROR_SUCCESS;
	}

	int buildLattice() {
		// Generate feature ids for all nodes and paths
		rebuildFeatures();

		for (int i = 0; i < word_num; ++i) {
			for (int j = 0; j < ysize_; ++j) {
				Node currentNode = node_[i][j];
				calcCost(currentNode);
				for (int index = 0; index < currentNode.getLpathList().size(); ++index) {
					Path p = currentNode.getLpathList().get(index);
					calcCost(p);
				}
			}
		}

		return Utils.ERROR_SUCCESS;
	}

	public int add(List<List<String>> row_p) {
		x_ = row_p;
		word_num = (short) x_.size();

		return Utils.ERROR_SUCCESS;
	}

	public int termbuf_build(Crf_term_out term_buf) {
		if (vlevel_ > 0) {
			// Calcuate the sequence label probability
			term_buf.prob = prob();
		}

		int this_word_num = get_word_num();

		for (int i = 0; i < this_word_num; ++i) {
			term_buf.result_[i] = yname(result_[i]);
			switch (vlevel_) {
			case 0:
				term_buf.weight_[i] = 0.0;
				break;
			case 2:
				term_buf.weight_[i] = prob(i);
				break;
			}
		}
		return Utils.ERROR_SUCCESS;
	}

	// Label input string. The result is saved as result []
	// If nbest > 1, get nbest result by "next"
	// Returen value: Successed - 0, Failed < 0
	public int parse() {
		int ret = 0;
		// no word need to be labeled
		if (word_num == 0) {
			return Utils.ERROR_SUCCESS;
		}

		// building feature set
		ret = buildFeatures();
		if (ret < 0) {
			return ret;
		}

		ret = buildLattice();
		if (ret < 0) {
			return ret;
		}

		// 4.forward-backward when we need to calcuate probability
		if (vlevel_ > 0) {
			forwardbackward();
		}

		// 5.using viterbi to search best result path
		ret = viterbi();
		if (ret < 0) {
			return ret;
		}

		// 6.initNbest
		// 求nbest(n>1)时的数据结构初始化，此后可以调用next()来获取nbest结果
		if (nbest_ > 1) {
			// 如果只求1-best，不需要使用initNbest()和next()获取结果
			ret = initNbest();
			if (ret < 0) {
				return ret;
			}

		}

		return Utils.ERROR_SUCCESS;
	}

	public int buildFeatures() {
		if (word_num <= 0) {
			return Utils.ERROR_INVALIDATED_PARAMETER;
		}

		StringBuffer builder = new StringBuffer();

		int id = 0;
		int feature_cache_row_size = 0;
		int feature_cache_size = 0;
		for (int cur = 0; cur < word_num; cur++) {
			feature_cache_row_size = 0;
			for (int index = 0; index < featureIndex.unigram_templs_.size(); index++) {
				String templ = featureIndex.unigram_templs_.get(index);
				StringBuffer res = featureIndex.apply_rule(templ, cur, builder, this);
				if (res == null) {
					return Utils.ERROR_EMPTY_FEATURE;
				}
				id = featureIndex.get_id(res.toString());
				if (id != -1) {
					feature_cache_.get(feature_cache_size)[feature_cache_row_size] = id;
					feature_cache_row_size++;
				}
			}
			feature_cache_.get(feature_cache_size)[feature_cache_row_size] = -1;
			feature_cache_size++;
		}

		for (int cur = 0; cur < word_num; cur++) {
			feature_cache_row_size = 0;
			for (int index = 0; index < featureIndex.bigram_templs_.size(); index++) {
				String templ = featureIndex.bigram_templs_.get(index);
				StringBuffer strFeature = featureIndex.apply_rule(templ, cur, builder, this);
				if (strFeature == null) {
					return Utils.ERROR_EMPTY_FEATURE;
				}

				id = featureIndex.get_id(strFeature.toString());
				if (id != -1) {
					feature_cache_.get(feature_cache_size)[feature_cache_row_size] = id;
					feature_cache_row_size++;
				}
			}
			feature_cache_.get(feature_cache_size)[feature_cache_row_size] = -1;
			feature_cache_size++;
		}
		return Utils.ERROR_SUCCESS;
	}

	public void calcCost(Path p) {
		double c = 0;
		long[] f = feature_cache_.get(p.getFid());
		for (int i = 0; i < f.length; ++i) {
			int fCurrent = (int) f[i];
			if (fCurrent == -1)
				break;
			c += featureIndex.getAlpha(fCurrent + p.getLnode().getY() * ysize_ + p.getRnode().getY());
		}

		p.setCost(featureIndex.cost_factor_ * c);
	}

	public void calcCost(Node n) {
		double c = 0;
		long[] f = feature_cache_.get(n.getFid());

		for (int i = 0; i < f.length; ++i) {
			int fCurrent = (int) f[i];
			if (fCurrent == -1)
				break;
			c += featureIndex.getAlpha(fCurrent + n.getY());
		}

		n.setCost(featureIndex.cost_factor_ * c);
	}

	public int output(Crf_term_out[] pout) {
		int n = 0;
		int ret = 0;

		if (nbest_ == 1) {
			// If only best result and no need probability, "next" is not to be used
			ret = termbuf_build(pout[0]);
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

				ret = termbuf_build(pout[n]);
				if (ret < 0) {
					return ret;
				}
			}
		}

		return Utils.ERROR_SUCCESS;
	}

}
