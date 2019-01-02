package crf;

import java.util.List;

public class Heap {
	public int capacity;
	public int elem_size; // size of elem_list
	public int size; // size of elem_ptr_list
	public List<QueueElement> elem_ptr_list;
	public List<QueueElement> elem_list;
}
