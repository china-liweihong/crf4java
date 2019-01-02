package console;

import java.util.Arrays;
import java.util.logging.Logger;

/**
 * 工程主入口
 * 
 * @author chmm
 *
 */
public class Crf4java {

	static void Usage() {
		System.out.println("Linear-chain CRF encoder & decoder by Daniells");
		System.out.println("java -jar crf4java-<version>-jar-with-dependencies.jar [parameters list...]");
		System.out.println("  -encode [parameters list...] - Encode CRF model from training corpus");
		System.out.println("  -decode [parameters list...] - Decode CRF model on test corpus");
	}

	public static void main(String[] args) {
		if (args.length < 1) {
			Usage();
			return;
		}

		boolean bEncoder = false;
		boolean bDecoder = false;

		String strType = args[0].substring(1).toLowerCase().trim();
		if ("encode".equals(strType)) {
			bEncoder = true;
		} else if ("decode".equals(strType)) {
			bDecoder = true;
		}

		// Invalidated parameter
		if (bEncoder == false && bDecoder == false) {
			Usage();
			return;
		}

		if (bEncoder == true) {
			EncoderConsole encoderConsole = new EncoderConsole();
			encoderConsole.run(Arrays.copyOfRange(args, 1, args.length));
		} else if (bDecoder == true) {
			DecoderConsole decoderConsole = new DecoderConsole();
			decoderConsole.run(Arrays.copyOfRange(args, 1, args.length));
		} else {
			Usage();
		}
	}

}
