package edu.drexel.group5.common;

/**
 * Utility method to convert a byte array into a String of hex characters. Adapted
 * from http://rgagnon.com/javadetails/java-0596.html.
 * @author Marcus McCurdy <marcus@drexel.edu>
 */
public class StringUtils {

	private static final String HEXES = "0123456789ABCDEF";

	private StringUtils() {
		//private constructor should not instantiate this utility class
	}

	public static String getHexString(byte[] raw) {
		if (raw == null) {
			return null;
		}
		final StringBuilder hex = new StringBuilder(2 * raw.length);
		for (final byte b : raw) {
			hex.append(HEXES.charAt((b & 0xF0) >> 4)).append(HEXES.charAt((b & 0x0F)));
		}
		return hex.toString();
	}
}
