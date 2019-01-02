package crfwrapper;

public class DecoderArgs {
	public String strModelFileName;
	public String strInputFileName;
	public String strOutputFileName;
	public String strOutputSegFileName;
	public int nBest;
	public int thread;
	public int probLevel;
	public int maxword;

	public DecoderArgs() {
		thread = 1;
		nBest = 1;
		probLevel = 0;
		maxword = 100;
	}
}
