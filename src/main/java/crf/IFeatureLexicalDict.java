package crf;

import java.util.List;
import java.util.Map;

import com.hankcs.hanlp.collection.trie.bintrie.BinTrie;

import utils.BTree;

public interface IFeatureLexicalDict {

	public void shrink(int freq);

	public long getOrAddId(String strFeature);

//	public int regenerateFeatureId(BTree<Long, Integer> old2new, int ysize);
	
	public int regenerateFeatureId(BinTrie<Integer> old2new, int ysize);
	
	public Map<String,Object[]> generateLexicalIdList();

	void clear();

	public int getSize();
}
