package edu.uc.skullduggery;

public class Util {
	public static void appendByteArray(byte[] a, byte[] b, int a_offset)
	{
		for(int i = 0; i<b.length; i++)
			a[a_offset + i] = b[i];
	}
	
	public static String byteArrayToString(byte[] b)
	{
		java.util.Formatter f = new java.util.Formatter();
		for(int i=0; i<b.length; i++)
			f.format("%02x",b[i]);
		return f.toString();
	}
}
