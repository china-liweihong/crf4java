package crf;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class LBFGS {

	private Logger logger = Logger.getLogger("LBFGS");

	double[] diag;
	double[] w;
	Mcsrch mcsrch_;
	long nfev, point, npt, iter, info, ispt, iypt;
	int iflag_;
	double stp;
	public int zeroone;
	public int err;
	public double obj;

	public double[] expected;
	public double[] v;
	public double[] xi;

	private int thread_num;

	public LBFGS(int thread_num) {
		iflag_ = 0;
		nfev = 0;
		point = 0;
		npt = 0;
		iter = 0;
		info = 0;
		ispt = 0;
		iypt = 0;
		stp = 0.0;
		mcsrch_ = new Mcsrch(thread_num);

		this.thread_num = thread_num;
	}

	public int optimize(double[] x, double C, boolean orthant) {
		long msize = 5;
		int size = x.length - 1;

		if (w == null || w.length == 0) {
			iflag_ = 0;
			w = new double[(int) (size * (2 * msize + 1) + 2 * msize)];
			diag = new double[size + 1];
			if (orthant) {
				xi = new double[size + 1];
				v = new double[size + 1];
			}
		}

		if (orthant) {
			pseudo_gradient(x, C);
		} else {
			v = expected;
		}

		lbfgs_optimize(msize, x, orthant, C);
		if (iflag_ < 0) {
			System.out.println("routine stops with unexpected error");
			return -1;
		}

		return iflag_;
	}

	private void lbfgs_optimize(long msize, double[] x, boolean orthant, double C) {
		int size = x.length - 1;
		double yy = 0.0;
		double ys = 0.0;
		long bound = 0;
		long cp = 0;
		boolean bExit = false;

		// initialization
		if (iflag_ == 0) {
			point = 0;
			ispt = size + (msize << 1);
			iypt = ispt + size * msize;

			ForkJoinPool pool = new ForkJoinPool(thread_num);
			pool.invoke(new OptimizeFork(1, size + 1, diag, v, expected, w, (int) ispt));
			try {
				pool.awaitTermination(1, TimeUnit.SECONDS);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			pool.shutdown();

			if (orthant) {
				pool = new ForkJoinPool(thread_num);
				pool.invoke(new Opti1Fork(1, size + 1, x, xi, v));
				try {
					pool.awaitTermination(1, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				pool.shutdown();
			}

			// 第一次试探步长
			stp = 1.0f / Math.sqrt(ddot_(size, v, 1, v, 1));

			++iter;
			info = 0;
			nfev = 0;
		}

		// MAIN ITERATION LOOP
		bExit = lineSearchAndUpdateStepGradient(msize, x, orthant);
		while (!bExit) {
			++iter;
			info = 0;

			if (orthant) {
				ForkJoinPool pool = new ForkJoinPool(thread_num);
				pool.invoke(new Opti1Fork(1, size + 1, x, xi, v));
				try {
					pool.awaitTermination(1, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				pool.shutdown();
			}

			if (iter > size) {
				bound = size;
			}

			// COMPUTE -H*G USING THE FORMULA GIVEN IN: Nocedal, J. 1980,
			// "Updating quasi-Newton matrices with limited storage",
			// Mathematics of Computation, Vol.24, No.151, pp. 773-782.
			ys = ddot_(size, w, (int) (iypt + npt + 1), w, (int) (ispt + npt + 1));
			yy = ddot_(size, w, (int) (iypt + npt + 1), w, (int) (iypt + npt + 1));

			double r_ys_yy = ys / yy;
			ForkJoinPool pool = new ForkJoinPool(thread_num);
			pool.invoke(new Opti4Fork(1, size + 1, w, x, w, r_ys_yy));
			try {
				pool.awaitTermination(1, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			pool.shutdown();

			cp = point;
			if (point == 0) {
				cp = msize;
			}

			w[size + (int) cp] = (1.0 / ys);

			// 回退次数
			bound = Math.min(iter - 1, msize);
			cp = point;
			for (int i = 1; i <= bound; ++i) {
				--cp;
				if (cp == -1)
					cp = msize - 1;
				double sq = ddot_(size, w, (int) (ispt + cp * size + 1), w, 1);
				int inmc = (int) (size + msize + cp + 1);
				int iycn = (int) (iypt + cp * size);
				w[inmc] = w[(int) (size + cp + 1)] * sq;
				double d = -w[inmc];

				pool = new ForkJoinPool(thread_num);
				pool.invoke(new Opti5Fork(1, size + 1, w, d, iycn));
				try {
					pool.awaitTermination(1, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				pool.shutdown();
			}

			pool = new ForkJoinPool(thread_num);
			pool.invoke(new Opti7Fork(1, size + 1, w, diag));
			try {
				pool.awaitTermination(1, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			pool.shutdown();

			for (int i = 1; i <= bound; ++i) {
				double yr = ddot_(size, w, (int) (iypt + cp * size + 1), w, 1);

				double beta = w[(int) (size + cp + 1)] * yr;
				int inmc = (int) (size + msize + cp + 1);
				beta = w[inmc] - beta;
				int iscn = (int) (ispt + cp * size);

				pool = new ForkJoinPool(thread_num);
				pool.invoke(new Opti5Fork(1, size + 1, w, beta, iscn));
				try {
					pool.awaitTermination(1, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				pool.shutdown();

				++cp;
				if (cp == msize) {
					cp = 0;
				}
			}

			if (orthant) {

				pool = new ForkJoinPool(thread_num);
				pool.invoke(new Opti6Fork(1, size + 1, w, v));
				try {
					pool.awaitTermination(1, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				pool.shutdown();

			}

			// STORE THE NEW SEARCH DIRECTION
			int offset = (int) ispt + (int) point * size;

			pool = new ForkJoinPool(thread_num);
			pool.invoke(new Opti8Fork(1, size + 1, offset, w, expected));
			try {
				pool.awaitTermination(1, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			pool.shutdown();

			stp = 1.0f;
			nfev = 0;
			bExit = lineSearchAndUpdateStepGradient(msize, x, orthant);
		}
	}

	private void pseudo_gradient(double[] x, double C) {
		int size = expected.length - 1;
		ForkJoinPool pool = new ForkJoinPool(thread_num);
		pool.invoke(new GrediantFork(1, size + 1, x, x, x, C));
		try {
			pool.awaitTermination(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		pool.shutdownNow();
	}

	private double ddot_(int size, double[] dx, int dx_idx, double[] dy, int dy_idx) {
		double ret = 0.0;
		ForkJoinPool pool = new ForkJoinPool(thread_num);
		Double subtotal = pool.invoke(new DotFork(0, size, dx, dy, dx_idx, dy_idx));
		try {
			pool.awaitTermination(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		pool.shutdown();
		ret += subtotal.doubleValue();
		return ret;
	}

	private boolean lineSearchAndUpdateStepGradient(long msize, double[] x, boolean orthant) {
		int size = x.length - 1;
		boolean bExit = false;
		mcsrch_.mcsrch(x, obj, v, w, ispt + point * size, this, diag);
		if (info == -1) {
			if (orthant) {
				ForkJoinPool pool = new ForkJoinPool(thread_num);
				pool.invoke(new Opti2Fork(1, size + 1, x, xi));
				try {
					pool.awaitTermination(1, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				pool.shutdown();
			}

			iflag_ = 1; // next value
			bExit = true;
		} else if (info != 1) {
			// MCSRCH error, please see error code in info
			iflag_ = -1;
			bExit = true;
		} else {
			// COMPUTE THE NEW STEP AND GRADIENT CHANGE
			npt = point * size;
			ForkJoinPool pool = new ForkJoinPool(thread_num);
			pool.invoke(new Opti3Fork(1, size + 1, w, expected, (int) ispt, (int) npt, (int) iypt, stp));
			try {
				pool.awaitTermination(1, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			pool.shutdown();

			++point;
			if (point == msize) {
				point = 0;
			}

			double gnorm = Math.sqrt(ddot_(size, v, 1, v, 1));
			double xnorm = Math.max(1.0, Math.sqrt(ddot_(size, x, 1, x, 1)));
			if (gnorm / xnorm <= Utils.eps) {
				iflag_ = 0; // OK terminated
				bExit = true;
			}
		}

		return bExit;
	}
}

class DotFork extends RecursiveTask<Double> {

	private int start;
	private int end;
	private double[] dx, dy;
	private int dx_idx, dy_idx;

	public DotFork(int start, int end, double[] dx, double[] dy, int dx_idx, int dy_idx) {
		this.start = start;
		this.end = end;
		this.dx = dx;
		this.dy = dy;
		this.dx_idx = dx_idx;
		this.dy_idx = dy_idx;
	}

	@Override
	protected Double compute() {
		int middle = (end - start) / 2;
		if (middle > 5000) {
			DotFork cf1 = new DotFork(start, start + middle, dx, dy, dx_idx, dy_idx);
			DotFork cf2 = new DotFork(start + middle, end, dx, dy, dx_idx, dy_idx);

			cf1.invoke();
			cf2.invoke();

			return cf1.join() + cf2.join();

		} else {
			double subtotal = 0;
			for (int i = start; i < end; i++) {
				subtotal += dx[i + dx_idx] * dy[i + dy_idx];
			}
			return subtotal;
		}
	}

}

class GrediantFork extends RecursiveAction {
	private int start;
	private int end;
	private double[] x;
	private double[] expected;
	private double C;
	private double[] v;

	public GrediantFork(int start, int end, double[] x, double[] expected, double[] v, double c) {
		this.start = start;
		this.end = end;
		this.x = x;
		this.expected = expected;
		this.v = v;
		this.C = c;
	}

	double sigma(double x) {
		if (x > 0)
			return 1.0;
		else if (x < 0)
			return -1.0;
		return 0.0;
	}

	@Override
	protected void compute() {
		int middle = (end - start) / 2;
		if (middle > 5000) {
			GrediantFork gf1 = new GrediantFork(start, middle + start, x, expected, v, C);
			GrediantFork gf2 = new GrediantFork(start + middle, end, x, expected, v, C);

			gf1.invoke();
			gf2.invoke();
		} else {
			for (int i = start; i < end; i++) {
				if (x[i] == 0) {
					if (expected[i] + C < 0) {
						v[i] = (expected[i] + C);
					} else if (expected[i] - C > 0) {
						v[i] = (expected[i] - C);
					} else {
						v[i] = 0;
					}
				} else {
					v[i] = (expected[i] + C * sigma(x[i]));
				}
			}
		}
	}

}

class OptimizeFork extends RecursiveAction {
	private int start;
	private int end;
	private double[] w, dialog, v, expected;
	private int ispt;

	public OptimizeFork(int start, int end, double[] dialog, double[] v, double[] expected, double[] w, int ispt) {
		this.start = start;
		this.end = end;
		this.dialog = dialog;
		this.v = v;
		this.expected = expected;
		this.w = w;
		this.ispt = ispt;
	}

	@Override
	protected void compute() {
		int middle = (end - start) / 2;
		if (middle > 5000) {
			OptimizeFork of1 = new OptimizeFork(start, start + middle, dialog, v, expected, w, ispt);
			OptimizeFork of2 = new OptimizeFork(start + middle, end, dialog, v, expected, w, ispt);

			of1.invoke();
			of2.invoke();
		} else {
			for (int i = start; i < end; i++) {
				dialog[i] = 1.0f;
				w[ispt + i] = -v[i];
				w[i] = expected[i];
			}
		}
	}
}

class Opti1Fork extends RecursiveAction {
	private int start;
	private int end;
	private double[] x, xi, v;

	public Opti1Fork(int start, int end, double[] x, double[] xi, double[] v) {
		this.start = start;
		this.end = end;
		this.x = x;
		this.xi = xi;
		this.v = v;
	}

	double sigma(double x) {
		if (x > 0)
			return 1.0;
		else if (x < 0)
			return -1.0;
		return 0.0;
	}

	@Override
	protected void compute() {
		int middle = (end - start) / 2;
		if (middle > 5000) {
			Opti1Fork of1 = new Opti1Fork(start, start + middle, x, xi, v);
			Opti1Fork of2 = new Opti1Fork(start + middle, end, x, xi, v);

			of1.invoke();
			of2.invoke();
		} else {
			for (int i = start; i < end; i++) {
				xi[i] = (x[i] != 0 ? sigma(x[i]) : sigma(-v[i]));
			}
		}
	}

}

class Opti2Fork extends RecursiveAction {
	private int start;
	private int end;
	private double[] x, xi;

	public Opti2Fork(int start, int end, double[] x, double[] xi) {
		this.start = start;
		this.end = end;
		this.x = x;
		this.xi = xi;
	}

	double sigma(double x) {
		if (x > 0)
			return 1.0;
		else if (x < 0)
			return -1.0;
		return 0.0;
	}

	@Override
	protected void compute() {
		int middle = (end - start) / 2;
		if (middle > 5000) {
			Opti2Fork of1 = new Opti2Fork(start, start + middle, x, xi);
			Opti2Fork of2 = new Opti2Fork(start + middle, end, x, xi);

			of1.invoke();
			of2.invoke();
		} else {
			for (int i = start; i < end; i++) {
				x[i] = (sigma(x[i]) == sigma(xi[i]) ? x[i] : 0);
			}
		}
	}

}

class Opti3Fork extends RecursiveAction {
	private int start;
	private int end;
	private double[] expected, w;
	private int ispt, npt, iypt;
	private double stp;

	public Opti3Fork(int start, int end, double[] w, double[] expected, int ispt, int npt, int iypt, double stp) {
		this.start = start;
		this.end = end;
		this.w = w;
		this.ispt = ispt;
		this.npt = npt;
		this.iypt = iypt;
		this.stp = stp;
	}

	@Override
	protected void compute() {
		int middle = (end - start) / 2;
		if (middle > 5000) {
			Opti3Fork of1 = new Opti3Fork(start, start + middle, w, expected, ispt, npt, iypt, stp);
			Opti3Fork of2 = new Opti3Fork(start + middle, end, w, expected, ispt, npt, iypt, stp);

			of1.invoke();
			of2.invoke();
		} else {
			for (int i = start; i < end; i++) {
				w[ispt + npt + i] = stp * w[ispt + npt + i];
				w[iypt + npt + i] = expected[i] - w[i];
			}
		}
	}

}

class Opti4Fork extends RecursiveAction {
	private int start;
	private int end;
	private double[] diag, w, v;
	private double r_ys_yy;

	public Opti4Fork(int start, int end, double[] w, double[] diag, double[] v, double r_ys_yy) {
		this.start = start;
		this.end = end;
		this.w = w;
		this.diag = diag;
		this.v = v;
		this.r_ys_yy = r_ys_yy;
	}

	@Override
	protected void compute() {
		int middle = (end - start) / 2;
		if (middle > 5000) {
			Opti4Fork of1 = new Opti4Fork(start, start + middle, w, diag, v, r_ys_yy);
			Opti4Fork of2 = new Opti4Fork(start + middle, end, w, diag, v, r_ys_yy);

			of1.invoke();
			of2.invoke();
		} else {
			for (int i = start; i < end; i++) {
				diag[i] = r_ys_yy;
				w[i] = -v[i];
			}
		}
	}

}

class Opti5Fork extends RecursiveAction {
	private int start;
	private int end;
	private double[] w;
	private double d;
	private int iycn;

	public Opti5Fork(int start, int end, double[] w, double d, int iycn) {
		this.start = start;
		this.end = end;
		this.w = w;
		this.d = d;
		this.iycn = iycn;
	}

	@Override
	protected void compute() {
		int middle = (end - start) / 2;
		if (middle > 5000) {
			Opti5Fork of1 = new Opti5Fork(start, start + middle, w, d, iycn);
			Opti5Fork of2 = new Opti5Fork(start + middle, end, w, d, iycn);

			of1.invoke();
			of2.invoke();
		} else {
			for (int i = start; i < end; i++) {
				w[i] = w[i] + d * w[iycn + i];
			}
		}
	}

}

class Opti6Fork extends RecursiveAction {
	private int start;
	private int end;
	private double[] w, v;

	public Opti6Fork(int start, int end, double[] w, double[] v) {
		this.start = start;
		this.end = end;
		this.w = w;
		this.v = v;
	}

	double sigma(double x) {
		if (x > 0)
			return 1.0;
		else if (x < 0)
			return -1.0;
		return 0.0;
	}

	@Override
	protected void compute() {
		int middle = (end - start) / 2;
		if (middle > 5000) {
			Opti6Fork of1 = new Opti6Fork(start, start + middle, w, v);
			Opti6Fork of2 = new Opti6Fork(start + middle, end, w, v);

			of1.invoke();
			of2.invoke();
		} else {
			for (int i = start; i < end; i++) {
				w[i] = (sigma(w[i]) == sigma(-v[i]) ? w[i] : 0);
			}
		}
	}

}

class Opti7Fork extends RecursiveAction {
	private int start;
	private int end;
	private double[] w, diag;

	public Opti7Fork(int start, int end, double[] w, double[] diag) {
		this.start = start;
		this.end = end;
		this.w = w;
		this.diag = diag;
	}

	@Override
	protected void compute() {
		int middle = (end - start) / 2;
		if (middle > 5000) {
			Opti7Fork of1 = new Opti7Fork(start, start + middle, w, diag);
			Opti7Fork of2 = new Opti7Fork(start + middle, end, w, diag);

			of1.invoke();
			of2.invoke();
		} else {
			for (int i = start; i < end; i++) {
				w[i] = (diag[i] * w[i]);
			}
		}
	}

}

class Opti8Fork extends RecursiveAction {
	private int start;
	private int end;
	private int offset;
	private double[] w, expected;

	public Opti8Fork(int start, int end, int offset, double[] w, double[] expected) {
		this.start = start;
		this.end = end;
		this.w = w;
		this.expected = expected;
	}

	@Override
	protected void compute() {
		int middle = (end - start) / 2;
		if (middle > 5000) {
			Opti8Fork of1 = new Opti8Fork(start, start + middle, offset, w, expected);
			Opti8Fork of2 = new Opti8Fork(start + middle, end, offset, w, expected);

			of1.invoke();
			of2.invoke();
		} else {
			for (int i = start; i < end; i++) {
				w[offset + i] = w[i];
				w[i] = expected[i];
			}
		}
	}

}
