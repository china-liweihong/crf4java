package crfwrapper;

public class EncoderArgs {
	public int max_iter = 1000; // maximum iteration, when encoding iteration reaches this value, the process
								// will be ended.
	public int min_feature_freq = 2; // minimum feature frequency, if one feature's frequency is less than this
										// value, the feature will be dropped.
	public double min_diff = 0.0001; // minimum diff value, when diff less than the value consecutive 3 times, the
										// process will be ended.
	public int threads_num = 1; // the amount of threads used to train model.
	public Encoder.REG_TYPE regType = Encoder.REG_TYPE.L2; // regularization type
	public String strTemplateFileName = null; // template file name
	public String strTrainingCorpus = null; // training corpus file name
	public String strEncodedModelFileName = null; // encoded model file name
	public String strRetrainModelFileName = null; // the model file name for re-training
	public int debugLevel = 0; // Debug level
	public int hugeLexMemLoad = 0;
	public double C = 1.0; // cost factor, too big or small value may lead encoded model over tune or under
							// tune

}
