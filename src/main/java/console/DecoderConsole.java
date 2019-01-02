package console;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import crf.Crf_term_out;
import crf.DecoderTagger;
import crfwrapper.Crf_seg_out;
import crfwrapper.Decoder;
import crfwrapper.DecoderArgs;
import crfwrapper.SegDecoderTagger;

public class DecoderConsole {

	private Logger logger = Logger.getLogger("DecoderConsole");

	public void usage() {
		System.out.println("java -jar crf4java-<version>-jar-with-dependencies.jar -decode <options>");
		System.out.println("-modelfile <string>  : The model file used for decoding");
		System.out.println("-inputfile <string>  : The input file to predict its content tags");
		System.out.println("-outputfile <string> : The output file to save raw tagged result");
		System.out.println("-outputsegfile <string> : The output file to save segmented tagged result");
		System.out.println("-nbest <int>         : Output n-best result, default value is 1");
		System.out.println("-thread <int>        : <int> threads used for decoding");
		System.out.println("-prob                : output probability, default is not output");
		System.out.println("                       0 - not output probability");
		System.out.println("                       1 - only output the sequence label probability");
		System.out.println("                       2 - output both sequence label and individual entity probability");
		System.out.println("-maxword <int>       : <int> max words per sentence, default value is 100");
		System.out.println("Example: ");
		System.out.println(
				"         java -jar crf4java-<version>-jar-with-dependencies.jar -decode -modelfile ner.model -inputfile ner_test.txt -outputfile ner_test_result.txt -outputsegfile ner_test_result_seg.txt -thread 4 -nbest 3 -prob 2 -maxword 500");
	}

	private Options getDecodeOptions() {
		Options options = new Options();
		options.addRequiredOption("modelfile", "modelfile", true, "The model file used for decoding");
		options.addOption("inputfile", "inputfile <string>", true, "The input file to predict its content tags");
		options.addOption("outputfile", "outputfile <string>", true, "The output file to save raw tagged result");
		options.addOption("outputsegfile", "outputsegfile <string>", true,
				"The output file to save segmented tagged result");
		options.addOption("nbest", "nbest <int>", true, "Output n-best result, default value is 1");
		options.addOption("thread", "thread <int>", true, "threads used for decoding");
		options.addOption("prob", "prob", true,
				"output probability, default is not output\n\t0 - not output probability\n\t1 - only output the sequence label probability\n\t2 - output both sequence label and individual entity probability");
		options.addOption("maxword", "maxword <int>", true, "show this help and exit");

		return options;
	}

	public void run(String[] args) {
		DecoderArgs options = new DecoderArgs();

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(getDecodeOptions(), args);
		} catch (ParseException e1) {
			usage();
			return;
		}

		options.strOutputFileName = cmd.getOptionValue("outputfile");
		options.strInputFileName = cmd.getOptionValue("inputfile");
		options.strModelFileName = cmd.getOptionValue("modelfile");
		try {

			if (cmd.hasOption("outputsegfile"))
				options.strOutputSegFileName = cmd.getOptionValue("outputsegfile");
			if (cmd.hasOption("thread"))
				options.thread = Integer.parseInt(cmd.getOptionValue("thread"));
			if (cmd.hasOption("nbest")) {
				options.nBest = Integer.parseInt(cmd.getOptionValue("nbest"));
			}
			if (cmd.hasOption("prob"))
				options.probLevel = Integer.parseInt(cmd.getOptionValue("prob"));
			if (cmd.hasOption("maxword"))
				options.maxword = Integer.parseInt(cmd.getOptionValue("maxword"));
		} catch (Exception e) {
			e.printStackTrace();
			usage();
			return;
		}

		if (options.strInputFileName == null || options.strModelFileName == null) {
			usage();
			return;
		}

		try {
			decode(options);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private Object rdLocker = new Object();

	boolean decode(DecoderArgs options) throws IOException {
		File inputFile = new File(options.strInputFileName);
		if (!inputFile.exists()) {
			System.out.println("FAILED: Open " + options.strInputFileName + " file failed.");
			return false;
		}

		File modelFile = new File(options.strModelFileName);
		if (!modelFile.exists()) {
			System.out.println("FAILED: Open " + options.strModelFileName + " file failed.");
			return false;
		}

		BufferedReader sr = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), "utf-8"));
		BufferedWriter sw = null, swSeg = null;

		if (options.strOutputFileName != null && options.strOutputFileName.length() > 0) {
			File outFile = new File(options.strOutputFileName);
			sw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile, true), "utf-8"));
		}

		if (options.strOutputSegFileName != null && options.strOutputSegFileName.length() > 0) {
			File outputSegFile = new File(options.strOutputSegFileName);
			swSeg = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputSegFile, true), "utf-8"));
		}

		// Create CRFSharp wrapper instance. It's a global instance
		Decoder crfWrapper = new Decoder();

		// Load encoded model from file
		System.out.println("Loading model from " + options.strModelFileName);
		crfWrapper.loadModel(options.strModelFileName);

		ConcurrentLinkedQueue<List<List<String>>> queueRecords = new ConcurrentLinkedQueue<>();
		ConcurrentLinkedQueue<List<List<String>>> queueSegRecords = new ConcurrentLinkedQueue<>();

		SegDecoderTagger tagger = crfWrapper.createTagger(options.nBest, options.maxword);
		tagger.set_vlevel(options.probLevel);

		// Initialize result
		Crf_seg_out[] crf_out = new Crf_seg_out[options.nBest];
		for (int i = 0; i < options.nBest; i++) {
			crf_out[i] = new Crf_seg_out(tagger.crf_max_word_num);
		}

		List<List<String>> inbuf = new ArrayList<List<String>>();
		while (true) {

			synchronized (rdLocker) {
				if (ReadRecord(inbuf, sr) == false) {
					break;
				}
				queueRecords.add(inbuf);
				queueSegRecords.add(inbuf);

			}

			// Call CRFSharp wrapper to predict given string's tags
			if (swSeg != null) {

				System.out.println(" crfout value：" + crf_out[0]);
				System.out.println(" tagge value：" + tagger);

				crfWrapper.segment(crf_out, tagger, inbuf);
			} else {
				crfWrapper.Segment((Crf_term_out[]) crf_out, (DecoderTagger) tagger, inbuf);
			}

			List<List<String>> peek = null;
			// Save segmented tagged result into file
			if (swSeg != null) {
				List<String> rstList = convertCRFTermOutToStringList(inbuf, crf_out);
				while (peek != inbuf) {
					peek = queueSegRecords.peek();
				}
				for (int index = 0; index < rstList.size(); index++) {
					String item = rstList.get(index);
					try {
						swSeg.write(item + "\n");
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				peek = queueSegRecords.poll();
				peek = null;
			}

			// Save raw tagged result (with probability) into file
			if (sw != null) {
				while (peek != inbuf) {
					peek = queueRecords.peek();
				}
				try {
					OutputRawResultToFile(inbuf, crf_out, tagger, sw);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				peek = queueRecords.poll();

			}
		}

		sr.close();

		if (sw != null) {
			sw.close();
		}
		if (swSeg != null) {
			swSeg.close();
		}

		return true;
	}

	// Output raw result with probability
	private void OutputRawResultToFile(List<List<String>> inbuf, Crf_term_out[] crf_out, SegDecoderTagger tagger,
			BufferedWriter sw) throws IOException {
		for (int k = 0; k < crf_out.length; k++) {
			if (crf_out[k] == null) {
				// No more result
				break;
			}

			StringBuffer sb = new StringBuffer();

			Crf_term_out crf_seg_out = crf_out[k];
			// Show the entire sequence probability
			// For each token
			for (int i = 0; i < inbuf.size(); i++) {
				// Show all features
				for (int j = 0; j < inbuf.get(i).size(); j++) {
					sb.append(inbuf.get(i).get(j));
					sb.append("\t");
				}

				// Show the best result and its probability
				sb.append(crf_seg_out.result_[i]);

				if (tagger.vlevel_ > 1) {
					sb.append("\t");
					sb.append(crf_seg_out.weight_[i]);

					// Show the probability of all tags
					sb.append("\t");
					for (int j = 0; j < tagger.ysize_; j++) {
						sb.append(tagger.yname(j));
						sb.append("/");
						sb.append(tagger.prob(i, j));

						if (j < tagger.ysize_ - 1) {
							sb.append("\t");
						}
					}
				}
				sb.append("\n");
			}
			if (tagger.vlevel_ > 0) {
				sw.write("#" + crf_seg_out.prob + "\n");
			}
			sw.write(sb.toString().trim() + "\n");
			sw.newLine();
		}
	}

	// Convert output format to string list
	private List<String> convertCRFTermOutToStringList(List<List<String>> inbuf, Crf_seg_out[] crf_out) {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < inbuf.size(); i++) {
			sb.append(inbuf.get(i).get(0));
		}

		String strText = sb.toString();
		List<String> rstList = new ArrayList<String>();
		for (int i = 0; i < crf_out.length; i++) {
			if (crf_out[i] == null) {
				// No more result
				break;
			}

			sb.setLength(0);
			Crf_seg_out crf_term_out = crf_out[i];
			for (int j = 0; j < crf_term_out.getCount(); j++) {
				int startIndex=crf_term_out.tokenList.get(j).offset;
				int length=crf_term_out.tokenList.get(j).length;
				String str = strText.substring(startIndex,
						startIndex+length);
				String strNE = crf_term_out.tokenList.get(j).strTag;

				sb.append(str);
				if (strNE.length() > 0) {
					sb.append("[" + strNE + "]");
				}
				sb.append(" ");
			}
			rstList.add(sb.toString().trim());
		}

		return rstList;
	}

	private boolean ReadRecord(List<List<String>> inbuf, BufferedReader sr) {
		inbuf.clear();

		while (true) {
			String strLine = null;
			try {
				strLine = sr.readLine();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (strLine == null) {
				// At the end of current file
				if (inbuf.size() == 0) {
					return false;
				} else {
					return true;
				}
			}
			strLine = strLine.trim();
			if (strLine.length() == 0) {
				return true;
			}

			// Read feature set for each record
			String[] items = strLine.split("[\t ]");
			inbuf.add(new ArrayList<String>());
			for (int index = 0; index < items.length; index++) {
				String item = items[index];
				inbuf.get(inbuf.size() - 1).add(item);
			}
		}
	}
}
