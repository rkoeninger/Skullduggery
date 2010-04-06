package edu.uc.skullduggery;

import java.security.NoSuchAlgorithmException;

import android.os.Bundle;

public class SkullUserInfoFactory {
	Bundle state;
	
	public SkullUserInfoFactory(Bundle savedInstanceState)
	{
		state = savedInstanceState;
	}
	
	public SkullUserInfo getInstance(String number) throws NoSuchAlgorithmException
	{
		if(state.containsKey(number))
			return (SkullUserInfo) state.get(number);
		return new SkullUserInfo(number);	
	}
}
