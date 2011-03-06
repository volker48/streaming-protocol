/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.drexel.group5;

import java.io.ByteArrayInputStream;
import java.io.PrintStream;
import java.security.MessageDigest;
import org.junit.Test;

/**
 *
 * @author marcus
 */
public class PacketFactoryTest {


	@Test
	public void testCreateSessionRequest() throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		md.update("5".getBytes());
		byte[] hash = md.digest();
		System.out.println("hash = " + hash);
		System.out.println("hash.length = " + hash.length);
		String secret = "secret";
		md.update("5".getBytes());
		md.update(secret.getBytes());
		byte[] hash2 = md.digest();
		System.out.println("hash2: " + hash2);
		System.out.println("hash2.length = " + hash2.length);
		PrintStream ps = new PrintStream(System.out);
		System.out.println("printstream has output");
		ps.write(hash);
		System.out.println("Hash2:");
		ps.write(hash2);
	}

//	@Test
//	public void testCreateSessionMessage() throws Exception {
//	}
//
//	@Test
//	public void testCreateChallengeResponse() throws Exception {
//	}
//
//	@Test
//	public void testCreateDisconnectMessage() throws Exception {
//	}

}