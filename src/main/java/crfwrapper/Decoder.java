package crfwrapper;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import crf.Crf_term_out;
import crf.DecoderTagger;
import crf.ModelReader;
import crf.Utils;

public class Decoder {
	private Logger logger = Logger.getLogger("Decoder");

	private ModelReader _modelReader;

	private int this_crf_max_word_num = Utils.DEFAULT_CRF_MAX_WORD_NUM;

	public void loadModel(String modelFilename) {
		_modelReader = new ModelReader(modelFilename);
		try {
			_modelReader.loadModel();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public SegDecoderTagger createTagger(int nbest, int this_crf_max_word_num) {
		if (_modelReader == null) {
			return null;
		}
		this.this_crf_max_word_num = this_crf_max_word_num;

		SegDecoderTagger tagger = new SegDecoderTagger(nbest, this_crf_max_word_num);
		tagger.init_by_model(_modelReader);

		return tagger;
	}

	// Segment given text
	public int segment(Crf_seg_out[] pout, // segment result
			SegDecoderTagger tagger, // Tagger per thread
			List<List<String>> inbuf // feature set for segment
	) {
		int ret = 0;
		if (inbuf.size() == 0) {
			// Empty input string
			return Utils.ERROR_SUCCESS;
		}

		ret = tagger.reset();
		if (ret < 0) {
			return ret;
		}

		ret = tagger.add(inbuf);
		if (ret < 0) {
			return ret;
		}

		// parse
		ret = tagger.parse();
		if (ret < 0) {
			return ret;
		}

		// wrap result
		ret = tagger.output(pout);
		if (ret < 0) {
			return ret;
		}

		return Utils.ERROR_SUCCESS;
	}

	// Segment given text
	public int Segment(Crf_term_out[] pout, // segment result
			DecoderTagger tagger, // Tagger per thread
			List<List<String>> inbuf // feature set for segment
	) {
		int ret = 0;
		if (inbuf.size() == 0) {
			// Empty input string
			return Utils.ERROR_SUCCESS;
		}

		ret = tagger.reset();
		if (ret < 0) {
			return ret;
		}

		ret = tagger.add(inbuf);
		if (ret < 0) {
			return ret;
		}

		// parse
		ret = tagger.parse();
		if (ret < 0) {
			return ret;
		}

		// wrap result
		ret = tagger.output(pout);
		if (ret < 0) {
			return ret;
		}

		return Utils.ERROR_SUCCESS;
	}
}
