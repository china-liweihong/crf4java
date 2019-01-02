package crf;

import java.util.ArrayList;

public class Utils {
	public static final double eps = 1e-7;

	public static final int MINUS_LOG_EPSILON = 13;
	public static final int DEFAULT_CRF_MAX_WORD_NUM = 100;

	public static final int MODEL_TYPE_NORM = 100;

	public static final int ERROR_INVALIDATED_FEATURE = -8;
	public static final int ERROR_HEAP_SIZE_TOO_BIG = -7;
	public static final int ERROR_INSERT_HEAP_FAILED = -6;
	public static final int ERROR_EMPTY_FEATURE = -5;
	public static final int ERROR_INVALIDATED_PARAMETER = -4;
	public static final int ERROR_WRONG_STATUS = -3;
	public static final int ERROR_TOO_LONG_WORD = -2;
	public static final int ERROR_UNKNOWN = -1;
	public static final int ERROR_SUCCESS = 0;

	public static Heap heap_init(int max_size) {
		Heap H;

		H = new Heap();
		H.capacity = max_size;
		H.size = 0;
		H.elem_size = 0;

		H.elem_ptr_list = new ArrayList<QueueElement>(max_size + 1);
		H.elem_list = new ArrayList<QueueElement>(max_size + 1);

		for (int z = 0; z < max_size; z++) {
			H.elem_list.add(new QueueElement());
			H.elem_ptr_list.add(null);
		}
		H.elem_list.get(0).setFx( Double.MIN_VALUE);
		H.elem_ptr_list.add(H.elem_list.get(0));

		return H;
	}

	public static QueueElement allc_from_heap(Heap H) {
		if (H.elem_size >= H.capacity) {
			return null;
		} else {
			return H.elem_list.get(++H.elem_size);
		}
	}

	public static int heap_insert(QueueElement qe, Heap H) {
		if (H.size >= H.capacity) {
			return Utils.ERROR_HEAP_SIZE_TOO_BIG;
		}
		int i = ++H.size;
		while (i != 1 && H.elem_ptr_list.get(i / 2).getFx() > qe.getFx()) {
			H.elem_ptr_list.set(i, H.elem_ptr_list.get(i / 2));
			i /= 2;
		}
		H.elem_ptr_list.set(i, qe);
		return 0;
	}

	public static QueueElement heap_delete_min(Heap H) {
		QueueElement min_elem = H.elem_ptr_list.get(1); // 堆是从第1号元素开始的
		QueueElement last_elem = H.elem_ptr_list.get(H.size--);
		int i = 1, ci = 2;
		while (ci <= H.size) {
			if (ci < H.size && H.elem_ptr_list.get(ci).getFx() > H.elem_ptr_list.get(ci + 1).getFx()) {
				ci++;
			}
			if (last_elem.getFx() <= H.elem_ptr_list.get(ci).getFx()) {
				break;
			}
			H.elem_ptr_list.set(i, H.elem_ptr_list.get(ci));
			i = ci;
			ci *= 2;
		}
		H.elem_ptr_list.set(i, last_elem);
		return min_elem;
	}

	public static boolean is_heap_empty(Heap H) {
		return H.size == 0;
	}

	public static void heap_reset(Heap H) {
		if (H != null) {
			H.size = 0;
			H.elem_size = 0;
		}
	}

	public static double logsumexp(double x, double y, boolean flg) {
		if (flg) {
			return y; // init mode
		}
		double vmin;
		double vmax;
		if (x > y) {
			vmin = y;
			vmax = x;
		} else {
			vmin = x;
			vmax = y;
		}

		if (vmax > vmin + MINUS_LOG_EPSILON) {
			return vmax;
		}
		return vmax + Math.log(Math.exp(vmin - vmax) + 1.0);
	}
}
