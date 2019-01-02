package crf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

public class fff {

	public static void main(String[] args) throws InterruptedException {
		// TODO Auto-generated method stub
		SortedMap<Double, List<String>> sdict = new TreeMap();
		for (int i = 0; i < 4; i++) {
			sdict.put(Double.valueOf(i), new ArrayList<>());
			sdict.get(Double.valueOf(i)).add("" + i);
		}

		Double[] keySet = sdict.keySet().toArray(new Double[0]);
		Arrays.sort(keySet, Collections.reverseOrder());

		for (double key :keySet) {
			System.out.println(key );
		}
	}

	public static void changeValue(int[] a) {
		a[0] = new AtomicInteger(a[0]).incrementAndGet();
	}

	private static String sec2time(long timeSpan) {
		long min = timeSpan / 60;
		long hour = min / 60;

		long sec = timeSpan % 60;

		return hour + "小时" + (min - (hour * 60)) + "分" + sec + "秒";
	}

}
