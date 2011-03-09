package edu.drexel.group5;

import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StringUtils {

	private StringUtils() {
		//private constructor should not instantiate this utility class
	}

	private static final byte[] HEX_CHAR_TABLE = {
		(byte) '0', (byte) '1', (byte) '2', (byte) '3',
		(byte) '4', (byte) '5', (byte) '6', (byte) '7',
		(byte) '8', (byte) '9', (byte) 'a', (byte) 'b',
		(byte) 'c', (byte) 'd', (byte) 'e', (byte) 'f'
	};

	public static String getHexString(byte[] raw) {
		byte[] hex = new byte[2 * raw.length];
		int index = 0;

		for (byte b : raw) {
			int v = b & 0xFF;
			hex[index++] = HEX_CHAR_TABLE[v >>> 4];
			hex[index++] = HEX_CHAR_TABLE[v & 0xF];
		}
		String hexString = null;
		try {
			hexString = new String(hex, "US-ASCII");
		} catch (UnsupportedEncodingException ex) {
			Logger.getLogger(StringUtils.class.getName()).log(Level.SEVERE, "Could not convert bytes to hex string!", ex);
		}
		return hexString == null ? "Coult not convert to hex!" : hexString;
	}
}
