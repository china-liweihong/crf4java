package crf;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.hankcs.hanlp.collection.trie.DoubleArrayTrie;

import utils.BinaryReader;

public class ModelReader extends BaseModel {

	private Logger logger = Logger.getLogger("ModelReader");

	private FileInputStream modelLoader = null;
	public int version; // 模型版本号,读取模型时读入
	private DoubleArrayTrie<String> da; // 特征集合
	public String modelPath;
	private static final String featureFileNameExtension = ".feature";
	private static final String weightFileNameExtension = ".alpha";

	public ModelReader(String modelPath) {
		this.modelPath = modelPath;
		this.modelLoader = getStreamFromFileSystem(modelPath);
	}

	public void loadModel() throws IOException {
		// Load model meta data
		loadMetadata();

		// Load all feature set data
		loadFeatureSet();

		// Load all features alpha data
		loadFeatureWeights();
	}

	private void loadMetadata() throws IOException {
		BufferedReader sr = new BufferedReader(new InputStreamReader(getStreamFromFileSystem(modelPath), "utf-8"));
		String strLine = "";
		// 读入版本号
		strLine = sr.readLine();
		version = Integer.parseInt(strLine.split(":")[1].trim());

		// 读入cost_factor
		strLine = sr.readLine();
		cost_factor_ = Double.parseDouble(strLine.split(":")[1].trim());
		// 读入maxid
		strLine = sr.readLine();
		maxid_ = Integer.parseInt(strLine.split(":")[1].trim());
		// 读入xsize
		strLine = sr.readLine();
		xsize_ = Integer.parseInt(strLine.split(":")[1].trim());
		// 读入空行
		strLine = sr.readLine();

		// 读入待标注的标签
		y_ = new ArrayList<>();
		while (true) {
			strLine = sr.readLine();
			if (strLine.length() == 0) {
				break;
			}
			y_.add(strLine);
		}

		// 读入unigram和bigram模板
		unigram_templs_ = new ArrayList<>();
		bigram_templs_ = new ArrayList<>();
		while ((strLine = sr.readLine()) != null) {
			if (strLine.length() == 0) {
				break;
			}
			if (strLine.charAt(0) == 'U') {
				unigram_templs_.add(strLine);
			}
			if (strLine.charAt(0) == 'B') {
				bigram_templs_.add(strLine);
			}
		}
		sr.close();

	}

	private void loadFeatureSet() {
		String featureSetFilePath = modelPath + featureFileNameExtension;
		da = new DoubleArrayTrie<>();
		da.load(featureSetFilePath);
	}

	private void loadFeatureWeights() throws IOException {
		// feature weight array
		alpha_ = new double[maxid_ + 1];

		String featureWeightFilePath = modelPath + weightFileNameExtension;
		File weightFile = new File(featureWeightFilePath);
		BinaryReader br_alpha = new BinaryReader(weightFile);

		int vqSize = br_alpha.readInt32();
		if (vqSize > 0) {
			System.out.println("This is a VQ Model. VQSize: " + vqSize);
			List<Double> vqCodeBook = new ArrayList<>();
			for (int i = 0; i < vqSize; i++) {
				vqCodeBook.add(br_alpha.readDouble());
			}
			for (int i = 0; i < maxid_; i++) {
				int vqIdx = br_alpha.read();
				alpha_[i] = vqCodeBook.get(vqIdx);
			}
		} else {
			// This is a normal model
			System.out.println("This is a normal model.");
			for (int i = 0; i < maxid_; i++) {
				alpha_[i] = br_alpha.readFloat();
			}
		}

		br_alpha.close();
	}

	private static FileInputStream getStreamFromFileSystem(String path) {
		File file = new File(path);
		try {
			return new FileInputStream(file);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	// 获取key对应的特征id
	public int get_id(String str) {
		return da.getSearcher(str).index;
	}

	public double getAlpha(int index) {
		return alpha_[index];
	}

	public String getModelPath() {
		return modelPath;
	}

	public void setModelPath(String modelPath) {
		this.modelPath = modelPath;
	}

}
