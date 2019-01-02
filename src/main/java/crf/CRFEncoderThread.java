package crf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;

public class CRFEncoderThread implements Callable<Integer> {
	private EncoderTagger[] x;
	private int start_i;
	private int thread_num;
	private int zeroone;
	private int err;
	private double obj;
	private Node[][] node_;
	private int[] result_;
	private int max_xsize_;
	private LBFGS lbfgs;
	private int[][] merr;

	public EncoderTagger[] getX() {
		return x;
	}

	public void setX(EncoderTagger[] x) {
		this.x = x;
	}

	public int getStart_i() {
		return start_i;
	}

	public void setStart_i(int start_i) {
		this.start_i = start_i;
	}

	public int getThread_num() {
		return thread_num;
	}

	public void setThread_num(int thread_num) {
		this.thread_num = thread_num;
	}

	public int getZeroone() {
		return zeroone;
	}

	public void setZeroone(int zeroone) {
		this.zeroone = zeroone;
	}

	public int getErr() {
		return err;
	}

	public void setErr(int err) {
		this.err = err;
	}

	public double getObj() {
		return obj;
	}

	public void setObj(double obj) {
		this.obj = obj;
	}

	public Node[][] getNode_() {
		return node_;
	}

	public void setNode_(Node[][] node_) {
		this.node_ = node_;
	}

	public int[] getResult_() {
		return result_;
	}

	public void setResult_(int[] result_) {
		this.result_ = result_;
	}

	public int getMax_xsize_() {
		return max_xsize_;
	}

	public void setMax_xsize_(short max_xsize_) {
		this.max_xsize_ = max_xsize_;
	}

	public LBFGS getLbfgs() {
		return lbfgs;
	}

	public void setLbfgs(LBFGS lbfgs) {
		this.lbfgs = lbfgs;
	}

	public int[][] getMerr() {
		return merr;
	}

	public void setMerr(int[][] merr) {
		this.merr = merr;
	}

	public void init() {
		if (x.length == 0) {
			return;
		}

		int ysize_ = x[0].ysize_;
		max_xsize_ = 0;
		for (int i = start_i; i < x.length; i += thread_num) {
			if (max_xsize_ < x[i].word_num) {
				max_xsize_ = x[i].word_num;
			}
		}

		result_ = new int[max_xsize_];
		node_ = new Node[max_xsize_][ysize_];
		for (int i = 0; i < max_xsize_; i++) {
			for (int j = 0; j < ysize_; j++) {
				node_[i][j] = new Node();
				node_[i][j].setX(i);
				node_[i][j].setY(j);
				node_[i][j].setLpathList(new ArrayList<Path>(ysize_));
				node_[i][j].setRpathList(new ArrayList<Path>(ysize_));
			}
		}

		for (short cur = 1; cur < max_xsize_; ++cur) {
			for (short j = 0; j < ysize_; ++j) {
				for (short i = 0; i < ysize_; ++i) {
					Path path = new Path();
					path.setFid(-1);
					path.setCost(0.0);
					path.add(node_[cur - 1][j], node_[cur][i]);
				}
			}
		}

		merr = new int[ysize_][ysize_];
	}

	@Override
	public Integer call() throws Exception {
		// Initialize thread self data structure
		obj = 0.0f;
		err = zeroone = 0;

		for(int i=0;i<merr.length;i++) {
			Arrays.fill(merr[i], 0);
		}
		
		for (int i = start_i; i < x.length; i += thread_num) {
			x[i].init(result_, node_);
			obj += x[i].gradient(lbfgs.expected);
			int error_num = x[i].eval(merr);
			err += error_num;
			if (error_num > 0) {
				++zeroone;
			}
		}

		return err;
	}
}
