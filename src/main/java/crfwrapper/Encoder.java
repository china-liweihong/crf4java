package crfwrapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import crf.CRFEncoderThread;
import crf.EncoderTagger;
import crf.LBFGS;
import crf.ModelWriter;

public class Encoder {
	private Logger logger = Logger.getLogger("Encoder");

	public enum REG_TYPE {
		L1, L2
	};

	public boolean learn(EncoderArgs args) throws IOException {
		if (args.min_diff <= 0.0) {
			System.out.println("eta must be > 0.0");
			return false;
		}

		if (args.C < 0.0) {
			System.out.println("C must be >= 0.0");
			return false;
		}

		if (args.threads_num <= 0) {
			System.out.println("thread must be > 0");
			return false;
		}

		if (args.hugeLexMemLoad > 0) {
			System.out.println("Build feature lexical dictionary in huge mode[shrink when mem used rate:{"
					+ args.hugeLexMemLoad + "}%]");
		}

		System.out.println("Open and check training corpus and templates...");
		ModelWriter modelWriter = new ModelWriter(args.threads_num, args.C, args.hugeLexMemLoad,
				args.strRetrainModelFileName);

		if (!modelWriter.open(args.strTemplateFileName, args.strTrainingCorpus)) {
			System.out.println("Open training corpus or template file failed.");
			return false;
		}

		System.out.println("Load training data and generate lexical features: ");
		EncoderTagger[] xList = modelWriter.readAllRecords();

		System.out.println();

		System.out.println("Shrinking feature set [frequency is less than " + args.min_feature_freq + "]...");
		modelWriter.shrink(xList, args.min_feature_freq);

		System.out.println("Saving model meta data...");
		if (!modelWriter.saveModelMetaData(args.strEncodedModelFileName)) {
			System.out.println("Save MetaData Failed!");
			return false;
		} else {
			System.out.println("Save MetaData Success");
		}

		if (!modelWriter.buildFeatureSetIntoIndex(args.strEncodedModelFileName, args.debugLevel)) {
			System.out.println("Failed!");
			return false;
		} else {
			System.out.println("Success");
		}

		System.out.println("Sentences size:        \t" + xList.length);
		System.out.println("Features size :        \t" + modelWriter.feature_size());
		System.out.println("Thread(s):             \t" + args.threads_num);
		System.out.println("Regularization type:   \t" + args.regType.toString());
		System.out.println("Freq:                  \t" + args.min_feature_freq);
		System.out.println("eta:                   \t" + args.min_diff);
		System.out.println("C:                     \t" + args.C);

		if (xList.length == 0) {
			System.out.println("No sentence for training.");
			return false;
		}

		boolean orthant = false;
		if (args.regType == REG_TYPE.L1) {
			orthant = true;
		}
		if (!runCRF(xList, modelWriter, orthant, args)) {
			System.out.println("Some warnings are raised during encoding...");
		}

		System.out.println("Saving model feature's weight...");
		modelWriter.saveFeatureWeight(args.strEncodedModelFileName);

		return true;
	}

	boolean runCRF(EncoderTagger[] x, ModelWriter modelWriter, boolean orthant, EncoderArgs args) throws IOException {
		double old_obj = Double.MAX_VALUE;
		int converge = 0;
		LBFGS lbfgs = new LBFGS(args.threads_num);
		lbfgs.expected = new double[modelWriter.feature_size() + 1];

		List<CRFEncoderThread> processList = new ArrayList<>();

		// Initialize encoding threads
		for (int i = 0; i < args.threads_num; i++) {
			CRFEncoderThread thread = new CRFEncoderThread();
			thread.setStart_i(i);
			thread.setThread_num(args.threads_num);
			thread.setX(x);
			thread.setLbfgs(lbfgs);
			thread.init();
			processList.add(thread);
		}

		// Statistic term and result tags frequency
		int termNum = 0;
		int[] yfreq;
		yfreq = new int[modelWriter.y_.size()];
		for (int index = 0; index < x.length; index++) {
			EncoderTagger tagger = x[index];
			termNum += tagger.word_num;
			for (int j = 0; j < tagger.word_num; j++) {
				yfreq[tagger.answer_[j]]++;
			}
		}

		// Iterative training
		long startDT = System.currentTimeMillis();
		double dMinErrRecord = 1.0;
		ExecutorService executor  = Executors.newFixedThreadPool(args.threads_num);
		for (int itr = 0; itr < args.max_iter; itr++) {
			// Clear result container
			lbfgs.obj = 0.0f;
			lbfgs.err = 0;
			lbfgs.zeroone = 0;
			Arrays.fill(lbfgs.expected, 0.0);
			
			////////
			
			for(CRFEncoderThread thread:processList) {
				thread.init();
			}
			try {
				executor.invokeAll(processList);
				executor.awaitTermination(1, TimeUnit.SECONDS);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			
			System.out.println("lbfgs obj:");
			for(int i=0;i<args.threads_num; ++i) {
				double a=processList.get(i).getObj();
				System.out.print(a+" ");
			}

			/////
			int[][] merr;
			merr = new int[modelWriter.y_.size()][modelWriter.y_.size()];
			for (int i = 0; i < args.threads_num; ++i) {

				lbfgs.obj += processList.get(i).getObj();
				lbfgs.err += processList.get(i).getErr();
				lbfgs.zeroone += processList.get(i).getZeroone();

				// Calculate error
				for (int j = 0; j < modelWriter.y_.size(); j++) {
					for (int k = 0; k < modelWriter.y_.size(); k++) {
						merr[j][k] += processList.get(i).getMerr()[j][k];
					}
				}
			}
			long[] num_nonzero = new long[1];
			int fsize = modelWriter.feature_size();
			if (!orthant)
				num_nonzero[0] = fsize;

			double[] alpha = modelWriter.alpha_;
			ForkJoinPool pool = new ForkJoinPool(args.threads_num);
			Double subtotal = pool.invoke(new CRFFork(1, fsize + 1, alpha, lbfgs, modelWriter, orthant, num_nonzero));
			try {
				pool.awaitTermination(1, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			pool.shutdown();
			System.out.print(subtotal.doubleValue()+" ");
			System.out.println();
			lbfgs.obj += subtotal.doubleValue();

			// Show each iteration result
			double diff = (itr == 0 ? 1.0f : Math.abs(old_obj - lbfgs.obj) / old_obj);
			double a=old_obj;
			double b=lbfgs.obj;
			System.out.println("diff "+a+"-"+b);
			old_obj = lbfgs.obj;

			showEvaluation(x.length, modelWriter, lbfgs, termNum, itr, merr, yfreq, diff, startDT, num_nonzero[0],
					args);
			if (diff < args.min_diff) {
				converge++;
			} else {
				converge = 0;
			}
			
			if (itr > args.max_iter || converge == 3) {
				break; // 3 is ad-hoc
			}

			if (args.debugLevel > 0 && (double) lbfgs.zeroone / (double) x.length < dMinErrRecord) {
				System.out.println("[Debug Mode] ");
				System.out.println("Save intermediate feature weights at current directory\n");

				// Save current best feature weight into file
				dMinErrRecord = (double) lbfgs.zeroone / (double) x.length;
				modelWriter.saveFeatureWeight("feature_weight_tmp");
			}

			int iret;
			iret = lbfgs.optimize(alpha, modelWriter.cost_factor_, orthant);
			if (iret <= 0) {
				return false;
			}
		}

		return true;
	}

	private void showEvaluation(int recordNum, ModelWriter feature_index, LBFGS lbfgs, int termNum, int itr,
			int[][] merr, int[] yfreq, double diff, long startDT, long nonzero_feature_num, EncoderArgs args) {
		long ts = System.currentTimeMillis() - startDT;
		if (args.debugLevel > 1) {
			for (int i = 0; i < feature_index.y_.size(); i++) {
				int total_merr = 0;
				
				SortedMap<Double, List<String>> sdict = new TreeMap();
				for (int j = 0; j < feature_index.y_.size(); j++) {
					total_merr += merr[i][j];
					double v = (double) merr[i][j] / (double) yfreq[i];
					if (v > 0.0001) {
						if (!sdict.containsKey(v)) {
							sdict.put(v, new ArrayList());
						}
						sdict.get(v).add(feature_index.y_.get(j));
					}
				}
				double vet = (double) total_merr / (double) yfreq[i];
				vet = vet * 100.0F;

				System.out.print(feature_index.y_.get(i) + " ");
				System.out.print("[FR=" + yfreq[i] + ", TE=");
				System.out.print(vet + "% ");
				System.out.print("Tmerr="+total_merr);
				System.out.print("]\n");

				int n = 0;
				Double[] keySet = sdict.keySet().toArray(new Double[0]);
				Arrays.sort(keySet, Collections.reverseOrder());
				for (double key : keySet) {
					List<String> value = sdict.get(key);
					for (int index = 0; index < value.size(); index++) {
						String item = value.get(index);
						n += item.length() + 1 + 7;
						if (n > 80) {
							// only show data in one line, more data in tail will not be show.
							break;
						}
						System.out.print(item + ":");
						System.out.print(key * 100 + "% \n");
					}
					if (n > 80) {
						break;
					}
				}
			}
		}
		double act_feature_rate = (double) (nonzero_feature_num) / (double) (feature_index.feature_size()) * 100.0;
		System.out.println("iter=" + itr + " terr=" + 1.0 * lbfgs.err / termNum + " serr="
				+ 1.0 * lbfgs.zeroone / recordNum + " diff=" + diff + " fsize=" + feature_index.feature_size() + "("
				+ act_feature_rate + "% act)\n");
		System.out.println("Time span: " + sec2time(ts / 1000) + ", Aver. time span per iter: "
				+ sec2time(ts / (itr + 1) / 1000) + "\n");
	}

	private String sec2time(long timeSpan) {
		long min = timeSpan / 60;
		long hour = min / 60;
		long sec = timeSpan % 60;
		return hour + "小时" + (min - (hour * 60)) + "分" + sec + "秒";
	}
}

class CRFFork extends RecursiveTask<Double> {

	private int start;
	private int end;
	private double[] alpha;
	private LBFGS lbfgs;
	private ModelWriter modelWriter;
	private boolean orthant;
	private long[] num_nonzero;

	public CRFFork(int start, int end, double[] alpha, LBFGS lbfgs, ModelWriter modelWriter, boolean orthant,
			long[] num_nonzero) {
		this.start = start;
		this.end = end;
		this.alpha = alpha;
		this.lbfgs = lbfgs;
		this.modelWriter = modelWriter;
		this.orthant = orthant;
		this.num_nonzero = num_nonzero;
	}

	@Override
	protected Double compute() {
		int middle = (end - start) / 2;
		if (middle > 5000) {
			CRFFork cf1 = new CRFFork(start, start + middle, alpha, lbfgs, modelWriter, orthant, num_nonzero);
			CRFFork cf2 = new CRFFork(start + middle, end, alpha, lbfgs, modelWriter, orthant, num_nonzero);

			cf1.invoke();
			cf2.invoke();

			return cf1.join() + cf2.join();

		} else {
			double subtotal = 0;
			if (!orthant) {
				// L2 regularization

				for (int k = start; k < end; k++) {
					subtotal += (alpha[k] * alpha[k] / (2.0 * modelWriter.cost_factor_));
					lbfgs.expected[k] += (alpha[k] / modelWriter.cost_factor_);
				}

			} else {
				// L1 regularization
				for (int k = start; k < end; k++) {
					subtotal += Math.abs(alpha[k] / modelWriter.cost_factor_);
					if (alpha[k] != 0.0) {
						num_nonzero[0] = new AtomicLong(num_nonzero[0]).incrementAndGet();
					}
				}
			}
			return subtotal;

		}

	}

}
