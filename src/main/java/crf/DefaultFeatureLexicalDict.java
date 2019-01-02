package crf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import com.hankcs.hanlp.collection.trie.bintrie.BinTrie;

import utils.BTree;

public class DefaultFeatureLexicalDict implements IFeatureLexicalDict {
	private Logger logger = Logger.getLogger("DefaultFeatureLexicalDict");

	private BinTrie<FeatureIdPair> featureset_dict_;
	private long maxid_;
	Object thisLock = new Object();
	private int forkThread_num = 0;

	public DefaultFeatureLexicalDict(int thread_num) {
		this.featureset_dict_ = new BinTrie<>();
		this.maxid_ = 0;
		this.forkThread_num = thread_num;
	}

	@Override
	public void shrink(int freq) {
		int i = 0;
		for (String key : featureset_dict_.keySet()) {
			if (featureset_dict_.get(key).value < freq)
				featureset_dict_.remove(key);
			else {
				i++;
			}
		}
	}

	private long getId(String key) {
		FeatureIdPair pair;
		if ((pair = featureset_dict_.get(key)) != null) {
			return pair.key;
		}

		return Utils.ERROR_INVALIDATED_FEATURE;
	}

	@Override
	public long getOrAddId(String key) {
		FeatureIdPair pair;
		synchronized (thisLock) {
			if ((pair = featureset_dict_.get(key)) != null) {
				int newValue = new AtomicInteger(pair.value).incrementAndGet();
				pair.value = newValue;
			} else {
				maxid_ = new AtomicLong(maxid_).incrementAndGet();
				long oldValue = maxid_ - 1;
				pair = new FeatureIdPair(oldValue, 1);
				featureset_dict_.put(key, pair);
			}
		}

		return pair.key;
	}

//	@Override
//	public int regenerateFeatureId(BTree<Long, Integer> old2new, int ysize) {
//
//		int new_maxid = 0;
//		// Regenerate new feature id and create feature ids mapping
//		for (Entry<String, FeatureIdPair> it : featureset_dict_.entrySet()) {
//
//			String strFeature = it.getKey();
//			// Regenerate new feature id
//			old2new.put(it.getValue().key, new_maxid);
//			it.getValue().key = new_maxid;
//			int addValue = (strFeature.charAt(0) == 'U' ? ysize : ysize * ysize);
//			new_maxid += addValue;
//		}
//
//		return new_maxid;
//	}
	@Override
	public int regenerateFeatureId(BinTrie<Integer> old2new, int ysize) {

		int new_maxid = 0;
		// Regenerate new feature id and create feature ids mapping
		for (Entry<String, FeatureIdPair> it : featureset_dict_.entrySet()) {

			String strFeature = it.getKey();
			// Regenerate new feature id
			old2new.put("" + it.getValue().key, new_maxid);
			it.getValue().key = new_maxid;
			int addValue = (strFeature.charAt(0) == 'U' ? ysize : ysize * ysize);
			new_maxid += addValue;
		}

		return new_maxid;
	}

	@Override
	public Map<String, Object[]> generateLexicalIdList() {
		Map<String, Object[]> out = new HashMap<>();

		String[] keyList = featureset_dict_.keySet().toArray(new String[0]);
		out.put("keyList", keyList);

		Integer[] valList = new Integer[keyList.length];

		FeatureIdPair[] featureValue = featureset_dict_.getValueArray(new FeatureIdPair[0]);
		ForkJoinPool pool = new ForkJoinPool(forkThread_num);
		pool.invoke(new GenerateIdFork(0, keyList.length, valList, featureValue));
		try {
			pool.awaitTermination(2, TimeUnit.SECONDS);
			pool.shutdown();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		out.put("valList", valList);

		return out;
	}

	class GenerateIdFork extends RecursiveAction {
		private int start;
		private int end;
		private Integer[] valList;
		private FeatureIdPair[] featureValue;

		public GenerateIdFork(int start, int end, Integer[] valList, FeatureIdPair[] featureValue) {
			this.start = start;
			this.end = end;
			this.valList = valList;
			this.featureValue = featureValue;
		}

		@Override
		protected void compute() {
			int middle = (end - start) / 2;
			if (middle > 500) {
				GenerateIdFork gf1 = new GenerateIdFork(start, start + middle, valList, featureValue);
				GenerateIdFork gf2 = new GenerateIdFork(start + middle, end, valList, featureValue);

				gf1.invoke();
				gf2.invoke();
			} else {
				for (int i = start; i < end; i++) {
					valList[i] = (int) featureValue[i].key;
				}
			}

		}

	}

	@Override
	public void clear() {
		featureset_dict_ = null;
	}

	@Override
	public int getSize() {
		// TODO Auto-generated method stub
		return featureset_dict_.size();
	}

}
