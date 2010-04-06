package edu.uc.skullduggery;

public class SkullMessage {
	
	private byte[] _hash, _data;
	
	public SkullMessage(byte[] data, byte[] hash)
	{
		_hash = hash;
		_data = data;
	}
	
	public byte[] getData()
	{
		return _data;
	}
	
	public byte[] getHashedData()
	{
		byte[] b = new byte[_hash.length + _data.length];
		for(int i=0; i < _hash.length; i++)
			b[i] = _hash[i];
		for(int i=0; i<_data.length; i++)
			b[_hash.length + i] = _data[i];
		return b;
	}
}
