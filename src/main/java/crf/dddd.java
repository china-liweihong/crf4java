package crf;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.RecursiveAction;


import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
 
public class dddd {
	private static final int THREAD_NUMBER = 3;
	private static CyclicBarrier sCyclicBarrier = new CyclicBarrier(
			THREAD_NUMBER, new Runnable() {
 
				@Override
				public void run() {
					System.out.println("大家都到达了宿舍楼下，一起出发吧。。。");
				}
			});
 
	public static void main(String[] args) {
		ExecutorService executorService = Executors
				.newFixedThreadPool(THREAD_NUMBER);
		for (int i = 0; i < THREAD_NUMBER; i++) {
			executorService.execute(new WalkFromDomitoryToCanteenRunnable(
					sCyclicBarrier, "同学" + i));
		}
		try {
			Thread.sleep(10000);//主线程睡眠
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("CyclicBarrier重用");
		for (int i = THREAD_NUMBER; i < THREAD_NUMBER * 2; i++) {
			executorService.execute(new WalkFromDomitoryToCanteenRunnable(
					sCyclicBarrier, "同学" + i));
		}
	}
 
	/**
	 * 从宿舍到食堂线程
	 * 
	 * @author LiuYi
	 *
	 */
	public static class WalkFromDomitoryToCanteenRunnable implements Runnable {
		private CyclicBarrier mCyclicBarrier;
		private String mName;
 
		public WalkFromDomitoryToCanteenRunnable(CyclicBarrier cyclicBarrier,
				String name) {
			this.mCyclicBarrier = cyclicBarrier;
			this.mName = name;
		}
 
		@Override
		public void run() {
			System.out.println(mName + "开始从宿舍出发。。。");
			try {
				Thread.sleep(1000);
				mCyclicBarrier.await();// 等待别同学
				// 前往食堂
				System.out.println(mName + "开始从宿舍楼下出发。。。");
				Thread.sleep(1000);
				System.out.println(mName + "达到食堂。。。");
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (BrokenBarrierException e) {
				e.printStackTrace();
			}
		}
	}
}