package edu.uc.skullduggery;

public class Util {
	public static void appendByteArray(byte[] a, byte[] b, int a_offset)
	{
		for(int i = 0; i<b.length; i++)
			a[a_offset + i] = b[i];
	}

}
