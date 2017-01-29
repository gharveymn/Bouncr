package com.geneharvey.bouncr;

/**
 * Created by Gene on 1/24/2017.
 * Matches text strings and checks them off a list using Camera2 based OCR regex matching.
 */

class Guest
{
	private boolean checkedOff;
	private String ident;

	Guest(String ident)
	{
		this.ident = ident;
		checkedOff = false;
	}

	boolean getCheckedOff()
	{
		return checkedOff;
	}

	void setCheckedOff(boolean bool)
	{
		checkedOff = bool;
	}

}
