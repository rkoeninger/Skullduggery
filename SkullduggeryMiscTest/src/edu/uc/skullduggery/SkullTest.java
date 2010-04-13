package edu.uc.skullduggery;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class SkullTest {

	private final static String AES = "AES";
	
	/**
	 * @param args
	 * @throws NoSuchPaddingException 
	 * @throws NoSuchAlgorithmException 
	 * @throws InvalidKeyException 
	 * @throws BadPaddingException 
	 * @throws IllegalBlockSizeException 
	 */
	public static void main(String[] args) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
		// TODO Auto-generated method stub
		
		KeyGenerator keygen;
		SecretKey AESKey;
		byte[] encKey;
		byte[] dataFull, dataTrunc, dataGap;
		SecretKeySpec keyFull,keyTrunc, keyGap;
		Cipher cipherFull,cipherTrunc,cipherGap;
		
		keygen = KeyGenerator.getInstance(AES);
		keygen.init(128);
		AESKey = keygen.generateKey();
		encKey = AESKey.getEncoded();
		
		keyFull = new SecretKeySpec(encKey, AES);
		keyTrunc = new SecretKeySpec(encKey, AES);
		keyGap = new SecretKeySpec(encKey, AES);
		
		cipherFull = Cipher.getInstance(AES);
		cipherTrunc = Cipher.getInstance(AES);
		cipherGap = Cipher.getInstance(AES);
		
		cipherFull.init(Cipher.ENCRYPT_MODE, keyFull);
		cipherTrunc.init(Cipher.ENCRYPT_MODE, keyTrunc);
		cipherGap.init(Cipher.ENCRYPT_MODE, keyGap);

		dataFull = new byte[768];
		dataTrunc = new byte[512];
		dataGap = new byte[768];
		
		for(int i=0; i<256; i++)
			dataFull[i] = dataTrunc[i] = dataGap[i] = (byte) i;
		
		for(int i=0; i<256; i++)
		{
			dataFull[i + 256] = (byte) i;
			dataGap[i+256] = 0;
		}
		
		for(int i=0; i<256; i++)
			dataFull[i + 512] = dataTrunc[i+256] = dataGap[i+512] =  (byte) (256 - i);
		
		System.out.println("Full encrypted data:" + Util.byteArrayToString(cipherFull.doFinal(dataFull)));
		System.out.println("Truncated encrypted data:" + Util.byteArrayToString(cipherTrunc.doFinal(dataTrunc)));
		System.out.println("Gapped encrypted data:" + Util.byteArrayToString(cipherGap.doFinal(dataGap)));
	}

}
