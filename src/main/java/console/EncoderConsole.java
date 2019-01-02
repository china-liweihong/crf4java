package console;

import java.io.IOException;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import crfwrapper.Encoder;
import crfwrapper.Encoder.REG_TYPE;
import crfwrapper.EncoderArgs;

public class EncoderConsole {

	private Logger logger = Logger.getLogger("EncoderConsole");

	private static void usage() {
		System.out.println("Linear-chain CRF encoder & decoder by ww");
		System.out.println("java -jar crf4java-<version>-jar-with-dependencies.jar -encode [parameters list]");
		System.out.println("\t-template <string> : template file name");
		System.out.println("\t-trainfile <string> : training corpus file name");
		System.out.println("\t-modelfile <string> : encoded model file name");
		System.out.println("\t-maxiter <int> : The maximum encoding iteration. Default value is 1000");
		System.out.println(
				"\t-minfeafreq <int> : Any feature's frequency is less than the value will be dropped. Default value is 2");
		System.out.println(
				"\t-mindiff <float> : If diff is less than the value consecutive 3 times, the encoding will be ended. Default value is 0.0001");
		System.out.println("\t-thread <int> : the amount of threads for encoding. Default value is 1");
		System.out.println(
				"\t-regtype <string> : regularization type (L1 and L2). L1 will generate a sparse model. Default is L2");
		System.out.println(
				"\t-hugelexmem <int> : build lexical dictionary in huge mode and shrinking start when used memory reaches this value. This mode can build more lexical items, but slowly. Value ranges [1,100] and default is disabled.");
		System.out.println("\t-retrainmodel <string> : the existed model for re-training.");
		System.out.println("\t-debug <int> : debug level, default value is 1");
		System.out.println("\t               0 - no debug information output");
		System.out.println("\t               1 - only output raw lexical dictionary for feature set");
		System.out.println(
				"\t               2 - full debug information output, both raw lexical dictionary and detailed encoded information for each iteration");
		System.out.println();
		System.out.println(
				"Note: either -maxiter reaches setting value or -mindiff reaches setting value in consecutive three times, the training process will be finished and saved encoded model.");
		System.out.println(
				"Note: -hugelexmem is only used for special task, and it is not recommended for common task, since it costs lots of time for memory shrink in order to load more lexical features into memory");
		System.out.println();
		System.out.println("A command line example as follows:");
		System.out.println(
				"\tjava -jar crf4java-<version>-jar-with-dependencies.jar -encode -template template.1 -trainfile ner.train -modelfile ner.model -maxiter 100 -minfeafreq 1 -mindiff 0.0001 -thread 4 -debug 2 -vq 1 -slotrate 0.95");
	}

	private Options getEncoderOptions() {
		Options option = new Options();
		option.addOption("template", "template <string>", true, "template file name");
		option.addOption("trainfile", "trainfile <string>", true, "training corpus file name");
		option.addOption("modelfile", "modelfile <string>", true, "encoded model file name");
		option.addOption("maxiter", "maxiter <int>", true, "The maximum encoding iteration. Default value is 1000");
		option.addOption("minfeafreq", "minfeafreq <int>", true,
				"Any feature's frequency is less than the value will be dropped. Default value is 2");
		option.addOption("mindiff", "mindiff <float>", true,
				"If diff is less than the value consecutive 3 times, the encoding will be ended. Default value is 0.0001");
		option.addOption("thread", "thread <int>", true, "the amount of threads for encoding. Default value is 1");
		option.addOption("slotrate", "slotrate <float>", true,
				"the maximum slot usage rate threshold when building feature set. it is ranged in (0.0, 1.0). the higher value takes longer time to build feature set, but smaller feature set size.  Default value is 0.95");
		option.addOption("regtype", "regtype <string>", true,
				"regularization type (L1 and L2). L1 will generate a sparse model. Default is L2");
		option.addOption("hugelexmem", "hugelexmem <int>", true,
				"build lexical dictionary in huge mode and shrinking start when used memory reaches this value. This mode can build more lexical items, but slowly. Value ranges [1,100] and default is disabled.");
		option.addOption("template", "template <string>", true, "template file name");
		option.addOption("retrainmodel", "retrainmodel <string>", true, "the existed model for re-training.");
		option.addOption("debug", "debug <int>", true,
				"debug level, default value is 1\n\t0 - no debug information output\n\t1 - only output raw lexical dictionary for feature set\n\t2 - full debug information output, both raw lexical dictionary and detailed encoded information for each iteration");
		return option;
	}

	public void run(String[] args) {
		Encoder encoder = new Encoder();
		EncoderArgs options = new EncoderArgs();

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(getEncoderOptions(), args);
		} catch (ParseException e) {
			e.printStackTrace();
			usage();
			return;
		}

		if (cmd.hasOption("debug"))
			options.debugLevel = Integer.parseInt(cmd.getOptionValue("debug"));
		options.strTemplateFileName = cmd.getOptionValue("template");
		options.strTrainingCorpus = cmd.getOptionValue("trainfile");
		options.strEncodedModelFileName = cmd.getOptionValue("modelfile");
		options.max_iter = Integer.parseInt(cmd.getOptionValue("maxiter"));
		options.min_feature_freq = Integer.parseInt(cmd.getOptionValue("minfeafreq"));
		options.min_diff = Double.parseDouble(cmd.getOptionValue("mindiff"));
		options.threads_num = Integer.parseInt(cmd.getOptionValue("thread"));
//		options.C = Double.parseDouble(cmd.getOptionValue("costfactor"));
//		options.hugeLexMemLoad = Integer.parseInt(cmd.getOptionValue("hugelexmem"));
		options.strRetrainModelFileName = cmd.getOptionValue("retrainmodel");
		String regType = cmd.getOptionValue("regtype").toLowerCase().trim();
		if ("l1".equals(regType)) {
			options.regType = REG_TYPE.L1;
		} else if ("l2".equals(regType)) {
			options.regType = REG_TYPE.L2;
		} else {
			System.out.println("Invalidated regularization type");
			usage();
			return;
		}

		if (options.strTemplateFileName == null || options.strEncodedModelFileName == null
				|| options.strTrainingCorpus == null) {
			usage();
			return;
		}

		if (options.threads_num <= 0) {
			options.threads_num = 1;
		}

		boolean bRet;
		try {
			bRet = encoder.learn(options);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
