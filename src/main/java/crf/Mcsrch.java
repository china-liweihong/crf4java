package crf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class Mcsrch {

	private Logger logger = Logger.getLogger("Mcsrch");

	private int infoc;
	private boolean stage1, brackt;
	private double dginit;
	private double width, width1;
	private double fx, dgx, fy, dgy;
	private double finit;
	private double dgtest;
	private double stx, sty;
	private double stmin, stmax;
	private int thread_num;

	private final double lb3_1_gtol = 0.9;
	private final double xtol = 1e-16;
	private final double lb3_1_stpmin = 1e-20;
	private final double lb3_1_stpmax = 1e20;
	private final double ftol = 1e-4;
	private final double p5 = 0.5;
	private final double p66 = 0.66;
	private final double xtrapf = 4.0;
	private final int maxfev = 20;

	public Mcsrch(int thread_num) {
		infoc = 0;
		stage1 = false;
		brackt = false;
		finit = 0.0;
		dginit = 0.0;
		dgtest = 0.0;
		width = 0.0;
		width1 = 0.0;
		stx = 0.0;
		fx = 0.0;
		dgx = 0.0;
		sty = 0.0;
		fy = 0.0;
		dgy = 0.0;
		stmin = 0.0;
		stmax = 0.0;
		this.thread_num = thread_num;
	}

	public void mcsrch(double[] x, double f, double[] g, double[] s, long s_idx, LBFGS lbfgs, double[] wa) {

		int size = x.length - 1;
		/* Parameter adjustments */
		if (lbfgs.info == -1) {
			lbfgs.info = 0;
			lbfgs.nfev++;

			double dg = ddot_(size, g, 1, s, (int) s_idx + 1);
			double ftest1 = finit + lbfgs.stp * dgtest;

			if (brackt && ((lbfgs.stp <= stmin || lbfgs.stp >= stmax) || infoc == 0)) {
				lbfgs.info = 6;
				System.out.println(
						"MCSRCH warning: Rounding errors prevent further progress.There may not be a step which satisfies the sufficient decrease and curvature conditions. Tolerances may be too small.");
				System.out.println("bracket: " + brackt + ", stp:" + lbfgs.stp + ", stmin:" + stmin + ", stmax:" + stmax
						+ ", infoc:" + infoc);
			}
			if (lbfgs.stp == lb3_1_stpmax && f <= ftest1 && dg <= dgtest) {
				lbfgs.info = 5;
				System.out.println("MCSRCH warning: The step is too large.");
			}
			if (lbfgs.stp == lb3_1_stpmin && (f > ftest1 || dg >= dgtest)) {
				lbfgs.info = 4;
				System.out.println("MCSRCH warning: The step is too small.");
				System.out.println("stp:" + lbfgs.stp + ", lb3_1_stpmin:" + lb3_1_stpmin + ", f:" + f + ", ftest1:"
						+ ftest1 + ", dg:" + dg + ", dgtest:" + dgtest);
			}
			if (lbfgs.nfev >= maxfev) {
				lbfgs.info = 3;
				System.out.println("MCSRCH warning: More than " + maxfev
						+ " function evaluations were required at the present iteration.");
			}
			if (brackt && stmax - stmin <= xtol * stmax) {
				lbfgs.info = 2;
				System.out.println("MCSRCH warning: Relative width of the interval of uncertainty is at most xtol.");
			}
			if (f <= ftest1 && Math.abs(dg) <= lb3_1_gtol * (-dginit)) {
				lbfgs.info = 1;
			}

			if (lbfgs.info != 0) {
				return;
			}

			if (stage1 && f <= ftest1 && dg >= Math.min(ftol, lb3_1_gtol) * dginit) {
				stage1 = false;
			}

			if (stage1 && f <= fx && f > ftest1) {
				double fm = f - lbfgs.stp * dgtest;
				double fxm = fx - stx * dgtest;
				double fym = fy - sty * dgtest;
				double dgm = dg - dgtest;
				double dgxm = dgx - dgtest;
				double dgym = dgy - dgtest;
				mcstep(stx, fxm, dgxm, sty, fym, dgym, lbfgs.stp, fm, dgm, brackt, stmin, stmax, infoc);
				fx = fxm + stx * dgtest;
				fy = fym + sty * dgtest;
				dgx = dgxm + dgtest;
				dgy = dgym + dgtest;
			} else {
				mcstep(stx, fx, dgx, sty, fy, dgy, lbfgs.stp, f, dg, brackt, stmin, stmax, infoc);
			}

			if (brackt) {
				double d1 = 0.0;
				d1 = sty - stx;
				if (Math.abs(d1) >= p66 * width1) {
					lbfgs.stp = stx + p5 * (sty - stx);
				}
				width1 = width;
				d1 = sty - stx;
				width = Math.abs(d1);
			}
		} else {
			infoc = 1;
			if (size <= 0 || lbfgs.stp <= 0.0) {
				return;
			}

			dginit = ddot_(size, g, 1, s, (int) s_idx + 1);
			if (dginit >= 0.0) {
				return;
			}

			brackt = false;
			stage1 = true;
			lbfgs.nfev = 0;
			finit = f;
			dgtest = ftol * dginit;
			width = lb3_1_stpmax - lb3_1_stpmin;
			width1 = width / p5;

			ForkJoinPool pool = new ForkJoinPool(thread_num);
			pool.invoke(new Mcsrch1Fork(1, size + 1, wa, x));
			try {
				pool.awaitTermination(1, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			pool.shutdown();

			stx = 0.0;
			fx = finit;
			dgx = dginit;
			sty = 0.0;
			fy = finit;
			dgy = dginit;
		}

		if (brackt) {
			stmin = Math.min(stx, sty);
			stmax = Math.max(stx, sty);
		} else {
			stmin = stx;
			stmax = lbfgs.stp + xtrapf * (lbfgs.stp - stx);
		}

		lbfgs.stp = Math.max(lbfgs.stp, lb3_1_stpmin);
		lbfgs.stp = Math.min(lbfgs.stp, lb3_1_stpmax);

		if ((brackt && ((lbfgs.stp <= stmin || lbfgs.stp >= stmax) || lbfgs.nfev >= maxfev - 1 || infoc == 0))
				|| (brackt && (stmax - stmin <= xtol * stmax))) {
			lbfgs.stp = stx;
		}

		double stp_t = lbfgs.stp;
		ForkJoinPool pool = new ForkJoinPool(thread_num);
		pool.invoke(new Mcsrch2Fork(1, size + 1, wa, x, s, stp_t, (int) s_idx));
		try {
			pool.awaitTermination(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		pool.shutdown();
		lbfgs.info = -1;
	}

	void mcstep(double stx, double fx, double dx, double sty, double fy, double dy, double stp, double fp, double dp,
			boolean brackt, double stpmin, double stpmax, int info) {
		boolean bound = true;
		double p, q, d3, r, stpq, stpc, stpf;
		double gamma;
		double s;
		double d1, d2;
		double theta;
		info = 0;

		if (brackt == true && ((stp <= Math.min(stx, sty) || stp >= Math.max(stx, sty)) || dx * (stp - stx) >= 0.0
				|| stpmax < stpmin)) {
			return;
		}

		double sgnd = dp * (dx / Math.abs(dx));
		if (fp > fx) {
			info = 1;
			bound = true;
			theta = (fx - fp) * 3 / (stp - stx) + dx + dp;
			d1 = Math.abs(theta);
			d2 = Math.abs(dx);
			d1 = Math.max(d1, d2);
			d2 = Math.abs(dp);
			s = Math.max(d1, d2);
			d1 = theta / s;
			gamma = s * Math.sqrt(d1 * d1 - dx / s * (dp / s));
			if (stp < stx) {
				gamma = -gamma;
			}
			p = gamma - dx + theta;
			q = gamma - dx + gamma + dp;
			r = p / q;
			stpc = stx + r * (stp - stx);
			stpq = stx + dx / ((fx - fp) / (stp - stx) + dx) / 2 * (stp - stx);
			d1 = stpc - stx;
			d2 = stpq - stx;
			if (Math.abs(d1) < Math.abs(d2)) {
				stpf = stpc;
			} else {
				stpf = stpc + (stpq - stpc) / 2;
			}
			brackt = true;
		} else if (sgnd < 0.0) {
			info = 2;
			bound = false;
			theta = (fx - fp) * 3 / (stp - stx) + dx + dp;
			d1 = Math.abs(theta);
			d2 = Math.abs(dx);
			d1 = Math.max(d1, d2);
			d2 = Math.abs(dp);
			s = Math.max(d1, d2);
			d1 = theta / s;
			gamma = s * Math.sqrt(d1 * d1 - dx / s * (dp / s));
			if (stp > stx) {
				gamma = -gamma;
			}
			p = gamma - dp + theta;
			q = gamma - dp + gamma + dx;
			r = p / q;
			stpc = stp + r * (stx - stp);
			stpq = stp + dp / (dp - dx) * (stx - stp);

			d1 = stpc - stp;
			d2 = stpq - stp;
			if (Math.abs(d1) > Math.abs(d2)) {
				stpf = stpc;
			} else {
				stpf = stpq;
			}
			brackt = true;
		} else if (Math.abs(dp) < Math.abs(dx)) {
			info = 3;
			bound = true;
			theta = (fx - fp) * 3 / (stp - stx) + dx + dp;
			d1 = Math.abs(theta);
			d2 = Math.abs(dx);
			d1 = Math.max(d1, d2);
			d2 = Math.abs(dp);
			s = Math.max(d1, d2);
			d3 = theta / s;
			d1 = 0.0f;
			d2 = d3 * d3 - dx / s * (dp / s);
			gamma = s * Math.sqrt((Math.max(d1, d2)));
			if (stp > stx) {
				gamma = -gamma;
			}
			p = gamma - dp + theta;
			q = gamma + (dx - dp) + gamma;
			r = p / q;
			if (r < 0.0 && gamma != 0.0) {
				stpc = stp + r * (stx - stp);
			} else if (stp > stx) {
				stpc = stpmax;
			} else {
				stpc = stpmin;
			}
			stpq = stp + dp / (dp - dx) * (stx - stp);
			if (brackt == true) {
				d1 = stp - stpc;
				d2 = stp - stpq;
				if (Math.abs(d1) < Math.abs(d2)) {
					stpf = stpc;
				} else {
					stpf = stpq;
				}
			} else {
				d1 = stp - stpc;
				d2 = stp - stpq;
				if (Math.abs(d1) > Math.abs(d2)) {
					stpf = stpc;
				} else {
					stpf = stpq;
				}
			}
		} else {
			info = 4;
			bound = false;
			if (brackt == true) {
				theta = (fp - fy) * 3 / (sty - stp) + dy + dp;
				d1 = Math.abs(theta);
				d2 = Math.abs(dy);
				d1 = Math.max(d1, d2);
				d2 = Math.abs(dp);
				s = Math.max(d1, d2);
				d1 = theta / s;
				gamma = s * Math.sqrt(d1 * d1 - dy / s * (dp / s));
				if (stp > sty) {
					gamma = -gamma;
				}
				p = gamma - dp + theta;
				q = gamma - dp + gamma + dy;
				r = p / q;
				stpc = stp + r * (sty - stp);
				stpf = stpc;
			} else if (stp > stx) {
				stpf = stpmax;
			} else {
				stpf = stpmin;
			}
		}

		if (fp > fx) {
			sty = stp;
			fy = fp;
			dy = dp;
		} else {
			if (sgnd < 0.0) {
				sty = stx;
				fy = fx;
				dy = dx;
			}
			stx = stp;
			fx = fp;
			dx = dp;
		}

		stpf = Math.min(stpmax, stpf);
		stpf = Math.max(stpmin, stpf);
		stp = stpf;
		if (brackt == true && bound) {
			if (sty > stx) {
				d1 = stx + (sty - stx) * 0.66;
				stp = Math.min(d1, stp);
			} else {
				d1 = stx + (sty - stx) * 0.66;
				stp = Math.max(d1, stp);
			}
		}

		return;
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
}

class Mcsrch1Fork extends RecursiveAction {
	private int start;
	private int end;
	private double[] wa, x;

	public Mcsrch1Fork(int start, int end, double[] wa, double[] x) {
		this.start = start;
		this.end = end;
		this.x = x;
		this.wa = wa;
	}

	@Override
	protected void compute() {
		int middle = (end - start) / 2;
		if (middle > 5000) {
			Mcsrch1Fork of1 = new Mcsrch1Fork(start, start + middle, wa, x);
			Mcsrch1Fork of2 = new Mcsrch1Fork(start + middle, end, wa, x);

			of1.invoke();
			of2.invoke();
		} else {
			for (int i = start; i < end; i++) {
				wa[i] = x[i];
			}
		}
	}

}

class Mcsrch2Fork extends RecursiveAction {
	private int start;
	private int end;
	private double[] wa, x, s;
	private double stp_t;
	private int s_idx;

	public Mcsrch2Fork(int start, int end, double[] wa, double[] x, double[] s, double stp_t, int s_idx) {
		this.start = start;
		this.end = end;
		this.x = x;
		this.wa = wa;
		this.s = s;
		this.stp_t = stp_t;
		this.s_idx = s_idx;
	}

	@Override
	protected void compute() {
		int middle = (end - start) / 2;
		if (middle > 5000) {
			Mcsrch2Fork of1 = new Mcsrch2Fork(start, start + middle, wa, x, s, stp_t, s_idx);
			Mcsrch2Fork of2 = new Mcsrch2Fork(start + middle, end, wa, x, s, stp_t, s_idx);

			of1.invoke();
			of2.invoke();
		} else {
			for (int i = start; i < end; i++) {
				x[i] = (wa[i] + stp_t * s[s_idx + i]);
			}
		}
	}

}
