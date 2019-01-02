package utils;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class BinaryReader {
	
	
	private static File file;
	private static InputStream in;
	private static DataInputStream dStream;
	
	public BinaryReader(File file){
		this.file = file;
		try {
			in = new FileInputStream(file);
			dStream = new DataInputStream(in);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * 读取一个byte
	 * @author RKGG
	 * @param 返回int
	 * */
	public static int read(){
		int b = 0;
		try {
			b = dStream.read();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return b;
	}
	
	/**
	 * 一次性读取 length长度
	 * 返回长度为length的byte[]
	 * @author RKGG 
	 * @param length 长度
	 * @return 返回byte[]
	 * */
	public static byte[] read(int length){
		byte[] bs = new byte[length];
		byte[] rs = new byte[length];
		try {
			dStream.read(bs,0,length);
			System.arraycopy(bs, 0, rs, 0, length);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return rs;
	}
	
	/**
	 * 读取2byte 返回1个int
	 * @author RKGG
	 * @return int
	 * */
	public static int readInt16(){
		byte[] bs = new byte[2];
		int temp = 0;
		try {
			dStream.read(bs,0,2);
			temp = HexUtil.byte2int(bs);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return temp;
	}
	
	/**
	 * 读取4byte 返回1个int
	 * @author RKGG
	 * @return 返回int
	 * */
	public static int readInt32(){
		byte[] bs = new byte[4];
		int temp = 0;
		try {
			dStream.read(bs,0,4);
			temp = HexUtil.byteArrayToInt(bs, 0);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return temp;
	}
 
	/**
	 * 将一个byte[] 按索引位置读取2byte 返回Int
	 * @author RKGG
	 * @param byte[]
	 * @param 起始位置
	 * @return int
	 * */
	public static int ToInt16(byte[] b,int start){
		byte[] bs = new byte[2];
		int temp = 0;
		System.arraycopy(b, start, bs, 0, 2);
		temp = HexUtil.byte2int(bs);
		return temp;
	}
	
	/**
	 * 将一个byte[] 按索引位置读取4byte 返回Int
	 * @author RKGG
	 * @param byte[]
	 * @param 起始位置
	 * @return int
	 * */
	public static int ToInt32(byte[] b,int start){
		byte[] bs = new byte[4];
		int temp = 0;
		System.arraycopy(b, start, bs, 0, 4);
		temp = HexUtil.byteArrayToInt(bs, 0);
		return temp;
	}
	
	/**
	 * 读取8byte 返回1个double
	 * @author RKGG
	 * @return double
	 * */
	public static double readDouble(){
		byte[] bs = new byte[8];
		double b = 0;
		try {
			dStream.read(bs,0,8);
			b = HexUtil.byteArrayToDouble(bs, 0);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return b;
	}
	
	/**
	 * 将一个byte[] 按索引位置读取8byte 返回Double
	 * @author RKGG
	 * @param byte[]
	 * @param 起始位置
	 * @return double
	 * */
	public static double ToDouble(byte[] b,int start){
		byte[] bs = new byte[8];
		System.arraycopy(b, start, bs, 0, 8);
		double d = HexUtil.byteArrayToDouble(bs, 0);
		return d;
	}
	
	/**
	 * 读取一个长度为 length*8 的byte[]
	 * 返回一个长度为 length的double[]
	 * @author RKGG
	 * @param length 长度
	 * @param 返回double[]
	 * */
	public static double[] readDoubles(int length){
		byte[] bs = new byte[length*8];
		double[] d = new double[length];
		try {
			dStream.read(bs,0,bs.length);
			d = HexUtil.getData(bs, 0);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return d;
	}
	
	/**
	 * 读取4byte 返回1个float
	 * @author RKGG
	 * @return 返回float
	 * */
	public static float readFloat(){
		byte[] bs = new byte[4];
		float f = 0;
		try {
			dStream.read(bs,0,4);
			f = HexUtil.byteArrayToFloat(bs, 0, 0);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return f;
	}
	
	/**
	 * 将一个byte[] 按索引位置读取4byte返回 1个float
	 * @author RKGG
	 * @param byte[]
	 * @param 起始位置
	 * @return 返回float
	 * */
	public static float ToFloat(byte[] b,int start){
		byte[] bs = new byte[4];
		System.arraycopy(b, start, bs, 0, 4);
		float f = HexUtil.byteArrayToFloat(bs, 0, 0);
		return f;
	}
	
	/**
	 * 读取一个长度为 length*4 的byte[]
	 * 返回一个长度为 length的float[] 
	 * @author RKGG
	 * @param length
	 * @return float[]
	 * */
    public static float[] readFloats(int length){
    	byte[] bs = new byte[length*4];
    	float[] fs = new float[length];
    	try {
			dStream.read(bs,0,bs.length);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        String s = new String(bs);
        String[] ssr = s.split(",");
        for (int i = 0; i < fs.length; i++) {
            fs[i] = Float.parseFloat(ssr[i]);
        }
        return fs;
    }
    
	/**
	 * 读取1byte转1char
	 * @author RKGG
	 * @return char
	 * */
	public static char readChar(){
		byte[] bs = new byte[1];
		char ch = 0;
		try {
			dStream.read(bs,0,1);
			ch = HexUtil.byteArrayToChar(bs);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return ch;	
	}
	
	/**
	 * 读取长度为length的byte[]
	 * 返回长度为length的char[]
	 * @author RKGG
	 * @param length 
	 * @return char[]
	 * */
	public static char[] readChars(int length){
		byte[] bs = new byte[length];
		char[] ch = new char[length];
		try {
			dStream.read(bs,0,bs.length);
			ch = HexUtil.getChars(bs);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return ch;
	}
	
	
	/**
	 * 读取一定长度 l的byte[]
	 * 返回长度为 l的String
	 * @author RKGG
	 * @param length
	 * @return String
	 * */
	public static String readString(int length){
		byte[] bs = new byte[length];
		String string = "";
		try {
			dStream.read(bs,0,bs.length);
			string = new String(bs);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return string;
	}
	
	
	/**
	 * 读取日期格式的double
	 * 返回日期格式
	 * @author RKGG
	 * @param format
	 * @return String
	 * */
	public static String readDate(String format){
		byte[] bs = new byte[8];
		String time = "";
		double time_d = 0;
		try {
			dStream.read(bs,0,8);
			time_d = HexUtil.byteArrayToDouble(bs, 0);
			time = HexUtil.doubleToDate(time_d, format);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return time;
	}
	
	/**
	 * 读取4byte转1boolean
	 * @author RKGG
	 * @param byte[]
	 * @param start
	 * @return boolean
	 * */
	public static boolean ToBoolean(byte[] b,int start){
		byte[] bs = new byte[4];
		System.arraycopy(b, start, bs, 0, 4);
		boolean tmp = HexUtil.byteArrayToBoolean(bs, 0);
		return tmp;
	}
	
	/**
	 * 关流
	 * @author RKGG
	 * */
	public static void close(){
		try {
			dStream.close();
			in.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}