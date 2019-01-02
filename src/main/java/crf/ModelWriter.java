package crf;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import com.hankcs.hanlp.collection.dartsclone.details.DoubleArrayBuilder;
import com.hankcs.hanlp.collection.trie.DoubleArrayTrie;
import com.hankcs.hanlp.collection.trie.bintrie.BinTrie;
import com.hankcs.hanlp.utility.TextUtility;

import utils.BTree;

public class ModelWriter extends BaseModel {
	private Logger logger = Logger.getLogger("ModelWriter");

	private String modelFileName;
	private int thread_num_;
	public IFeatureLexicalDict featureLexicalDict;
	private List<List<List<String>>> trainCorpusList;

	/*
	 * modelFileName--retrainModelFileName
	 */
	public ModelWriter(int thread_num, double cost_factor, int hugeLexShrinkMemLoad, String modelFileName) {
		this.cost_factor_ = cost_factor;
		this.maxid_ = 0;
		this.thread_num_ = thread_num;
		this.modelFileName = modelFileName;
		if (hugeLexShrinkMemLoad > 0) {
//			featureLexicalDict = new HugeFeatureLexicalDict(thread_num_, hugeLexShrinkMemLoad);
		} else {
			featureLexicalDict = new DefaultFeatureLexicalDict(thread_num_);
		}
	}

	public void shrink(EncoderTagger[] xList, int freq) {
//		BTree<Long, Integer> old2new = new BTree<>();
		BinTrie<Integer> old2new = new BinTrie<>();
		featureLexicalDict.shrink(freq);
//		maxid_ = featureLexicalDict.regenerateFeatureId(old2new, y_.size());
		maxid_ = featureLexicalDict.regenerateFeatureId(old2new, y_.size());

		int feature_count = xList.length;

		ForkJoinPool pool = new ForkJoinPool(thread_num_);
		pool.invoke(new ShrinkFork(0, feature_count, xList, old2new));
		try {
			pool.awaitTermination(1, TimeUnit.SECONDS);
			pool.shutdown();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		System.out.println("Feature size in total : " + maxid_);
	}

	class ShrinkFork extends RecursiveAction {
		private int start;
		private int end;
		private EncoderTagger[] xList;
		private BinTrie<Integer> old2new;

		public ShrinkFork(int start, int end, EncoderTagger[] xList, BinTrie<Integer> old2new) {
			this.start = start;
			this.end = end;
			this.xList = xList;
			this.old2new = old2new;
		}

		@Override
		protected void compute() {
			int middle = (end - start) / 2;
			if (middle > 500) {
				ShrinkFork sf1 = new ShrinkFork(start, start + middle, xList, old2new);
				ShrinkFork sf2 = new ShrinkFork(start + middle, end, xList, old2new);

				invokeAll(sf1, sf2);
			} else {
				for (int i = start; i < end; i++) {
					for (int j = 0; j < xList[i].feature_cache_.size(); j++) {
						List<Integer> newfs = new ArrayList<>();
						Integer rstValue = 0;
						for (int index = 0; index < xList[i].feature_cache_.get(j).length; index++) {
							long v = xList[i].feature_cache_.get(j)[index];
							if ((rstValue = old2new.get(v + "")) != null) {
								newfs.add(rstValue);
							}
						}
						xList[i].feature_cache_.set(j, newfs.stream().mapToLong(t -> t.longValue()).toArray());
					}
				}
			}
		}

	}

	// Open and check training and template file
	public boolean open(String strTemplateFileName, String strTrainCorpusFileName) {
		try {
			return openTemplateFile(strTemplateFileName) && openTrainCorpusFile(strTrainCorpusFileName);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	// Load all records and generate features
	public EncoderTagger[] readAllRecords() {
		EncoderTagger[] arrayEncoderTagger = new EncoderTagger[trainCorpusList.size()];
		int[] arrayEncoderTaggerSize = { 0 };

		// 根据模板特征生成特征数据
		ForkJoinPool pool = new ForkJoinPool(thread_num_);
		
		pool.invoke(new ReadRecordsFork(0, trainCorpusList.size(), arrayEncoderTagger, trainCorpusList,
				arrayEncoderTaggerSize, this));
		try {
			int size=pool.getPoolSize();
			pool.awaitTermination(1, TimeUnit.SECONDS);
			pool.shutdown();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		trainCorpusList.clear();
		trainCorpusList = null;

		System.out.println();
		return arrayEncoderTagger;
	}

	// Save model meta data into file
	public boolean saveModelMetaData(String filename) throws IOException {
		File metaFile = new File(filename);
		BufferedWriter tofs = new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(metaFile, false), "utf-8"));

		// header
		tofs.write("version: " + Utils.MODEL_TYPE_NORM + "\n");
		tofs.write("cost-factor: " + cost_factor_ + "\n");
		tofs.write("maxid: " + maxid_ + "\n");
		tofs.write("xsize: " + xsize_ + "\n");
		tofs.newLine();

		// y_
		for (int i = 0; i < y_.size(); i++) {
			tofs.write(y_.get(i) + "\n");
		}
		tofs.newLine();

		// template
		for (int i = 0; i < unigram_templs_.size(); i++) {
			tofs.write(unigram_templs_.get(i) + "\n");
		}
		for (int i = 0; i < bigram_templs_.size(); i++) {
			tofs.write(bigram_templs_.get(i) + "\n");
		}
		tofs.close();
		return true;
	}

	class ReadRecordsFork extends RecursiveAction {
		private int start;
		private int end;
		private EncoderTagger[] arrayEncoderTagger;
		private List<List<List<String>>> trainCorpusList;
		private int[] arrayEncoderTaggerSize;
		private ModelWriter modelWriter;

		public ReadRecordsFork(int start, int end, EncoderTagger[] arrayEncoderTagger,
				List<List<List<String>>> trainCorpusList, int[] arrayEncoderTaggerSize, ModelWriter modelWriter) {
			this.start = start;
			this.end = end;
			this.arrayEncoderTagger = arrayEncoderTagger;
			this.trainCorpusList = trainCorpusList;
			this.arrayEncoderTaggerSize = arrayEncoderTaggerSize;
			this.modelWriter = modelWriter;
		}

		@Override
		protected void compute() {
			int middle = (end - start) / 2;
			if (middle > 50) {
				ReadRecordsFork rf1 = new ReadRecordsFork(start, start + middle, arrayEncoderTagger, trainCorpusList,
						arrayEncoderTaggerSize, modelWriter);
				ReadRecordsFork rf2 = new ReadRecordsFork(start + middle, end, arrayEncoderTagger, trainCorpusList,
						arrayEncoderTaggerSize, modelWriter);

				invokeAll(rf1, rf2);
			} else {
				for (int i = start; i < end; i++) {
					EncoderTagger _x = new EncoderTagger(modelWriter);
					if (!_x.generateFeature(trainCorpusList.get(i))) {
						System.out.println("Load a training sentence failed, skip it.");
					} else {
						arrayEncoderTagger[i] = _x;

						arrayEncoderTaggerSize[0] = new AtomicInteger(arrayEncoderTaggerSize[0]).incrementAndGet();
						int oldValue = arrayEncoderTaggerSize[0] - 1;
						if (oldValue % 100 == 0) {
							// Show current progress on console
							System.out.print(oldValue + "...");
						}
					}
				}
			}
		}

	}

	private boolean openTemplateFile(String filename) throws IOException {
		File tempFile = new File(filename);
		BufferedReader ifs = new BufferedReader(new InputStreamReader(new FileInputStream(tempFile), "utf-8"));
		unigram_templs_ = new ArrayList<String>();
		bigram_templs_ = new ArrayList<String>();
		String line = "";
		while ((line = ifs.readLine()) != null) {
			if (line.length() == 0 || line.charAt(0) == '#') {
				continue;
			}
			if (line.charAt(0) == 'U') {
				unigram_templs_.add(line);
			} else if (line.charAt(0) == 'B') {
				bigram_templs_.add(line);
			} else {
				System.out.println("unknown type:" + line);
			}
		}
		ifs.close();
		return true;

	}

	private boolean openTrainCorpusFile(String strTrainingCorpusFileName) throws IOException {
		File trainCorpusFile = new File(strTrainingCorpusFileName);
		BufferedReader ifs = new BufferedReader(new InputStreamReader(new FileInputStream(trainCorpusFile), "utf-8"));
		y_ = new ArrayList<>();
		trainCorpusList = new ArrayList<>();
		Set<String> hashCand = new HashSet<>();
		List<List<String>> recordList = new ArrayList<>();
		int last_xsize = -1;

		String line = "";
		while ((line = ifs.readLine()) != null) {
			if (line.length() == 0 || line.charAt(0) == ' ' || line.charAt(0) == '\t') {
				if (recordList.size() > 0) {
					trainCorpusList.add(recordList);
					recordList = new ArrayList<>();
				}
				continue;
			}

			String[] items = line.split("[\t ]");
			int size = items.length;
			if (last_xsize >= 0 && last_xsize != size) {
				return false;
			}
			last_xsize = size;
			xsize_ = size - 1;
			recordList.add(Arrays.asList(items));

			if (!hashCand.contains(items[items.length - 1])) {
				hashCand.add(items[items.length - 1]);
				y_.add(items[items.length - 1]);
			}

		}
		ifs.close();

		System.out.println("Training corpus size: " + trainCorpusList.size());
		return true;
	}

	// Get feature id from feature set by feature string
	// If feature string is not existed in the set, generate a new id and return it
	public boolean buildFeatures(EncoderTagger tagger) {
		List<Long> feature = new ArrayList<>();
		StringBuffer localBuilder = new StringBuffer();
		for (int cur = 0; cur < tagger.word_num; cur++) {
			for (int index = 0; index < unigram_templs_.size(); index++) {
				String it = unigram_templs_.get(index);
				StringBuffer strFeature = apply_rule(it, cur, localBuilder, tagger);
				if (strFeature == null)
					System.out.println("format error: " + it);
				else {
					long id = featureLexicalDict.getOrAddId(strFeature.toString());
					feature.add(id);
				}
			}
			tagger.feature_cache_.add(feature.stream().mapToLong(t -> t.longValue()).toArray());
			feature.clear();
		}

		for (int cur = 0; cur < tagger.word_num; cur++) {
			for (int index = 0; index < bigram_templs_.size(); index++) {
				String it = bigram_templs_.get(index);
				StringBuffer strFeature = apply_rule(it, cur, localBuilder, tagger);
				if (strFeature == null) {
					System.out.println(" format error: " + it);
				} else {
					long id = featureLexicalDict.getOrAddId(strFeature.toString());
					feature.add(id);
				}
			}
			tagger.feature_cache_.add(feature.stream().mapToLong(t -> t.longValue()).toArray());
			feature.clear();
		}

		return true;
	}

	// Build feature set into indexed data
	public boolean buildFeatureSetIntoIndex(String filename, int debugLevel) throws IOException {
		System.out.println("Building " + featureLexicalDict.getSize() + " features into index...");

		Map<String, Object[]> result = featureLexicalDict.generateLexicalIdList();
		String[] keyList = (String[]) result.get("keyList");
		Integer[] valList = (Integer[]) result.get("valList");

		if (debugLevel > 0) {
			System.out.println("Debug: Write raw feature set into file");
			String filename_featureset_raw_format = filename + ".feature.raw_text";
			File rawFile = new File(filename_featureset_raw_format);
			BufferedWriter sw = new BufferedWriter(
					new OutputStreamWriter(new FileOutputStream(rawFile, false), "utf-8"));
			for (int i = 0; i < keyList.length; i++) {
				sw.write(keyList[i] + "\t" + valList[i] + "\n");
				sw.flush();
			}
			sw.flush();
			sw.close();
		}

		// Build feature index
		String filename_featureset = filename + ".feature";
		DoubleArrayTrie<Integer> da = new DoubleArrayTrie<>();
		int error = da.build(Arrays.asList(keyList), valList);
		if (error > 0) {
			System.out.println("Build lexical dictionary failed.");
			return false;
		}
		// Save indexed feature set into file
		da.save(filename_featureset);

		if (TextUtility.isBlank(modelFileName)) {
			featureLexicalDict.clear();
			featureLexicalDict = null;
			keyList = null;
			valList = null;

			System.gc();
			System.runFinalization();

			// Create weight matrix
			alpha_ = new double[feature_size() + 1];
		} else {
			System.out.println("");
			System.out.println("Loading the existed model for re-training...");
			// Create weight matrix
			alpha_ = new double[feature_size() + 1];
			ModelReader modelReader = new ModelReader(this.modelFileName);
			try {
				modelReader.loadModel();
			} catch (IOException e) {
				e.printStackTrace();
			}

			if (modelReader.y_.size() == y_.size()) {
				for (int i = 0; i < keyList.length; i++) {
					int index = modelReader.get_id(keyList[i]);
					if (index < 0) {
						continue;
					}
					int size = (keyList[i].charAt(0) == 'U' ? y_.size() : y_.size() * y_.size());
					for (int j = 0; j < size; j++) {
						alpha_[valList[i] + j + 1] = modelReader.getAlpha(index + j);
					}
				}
			} else {
				System.out.println("The number of tags isn't equal between two models, it cannot be re-trained.");
			}

			// Clean up all data
			featureLexicalDict.clear();
			featureLexicalDict = null;
			keyList = null;
			valList = null;

			System.gc();
			System.runFinalization();
		}

		return true;
	}

	public void saveFeatureWeight(String filename) throws IOException {
		String filename_alpha = filename + ".alpha";
		File weightFile = new File(filename_alpha);
		DataOutputStream bw = new DataOutputStream(new FileOutputStream(weightFile, true));

		System.out.println("Save feature weights into a normal model:" + filename_alpha + "\n");
		bw.writeInt(0);
		// Save weights
		for (int i = 1; i <= maxid_; ++i) {
			bw.writeFloat((float) alpha_[i]);
		}
		bw.close();
	}
}
