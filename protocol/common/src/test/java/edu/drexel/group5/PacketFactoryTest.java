/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.drexel.group5;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

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

	@Test
	public void	testCreateSessionMessage() throws Exception {
		DatagramSocket testSocket = new DatagramSocket(1337, InetAddress.getLocalHost());
		PacketFactory factory = new PacketFactory(1337, InetAddress.getLocalHost());
		byte sessionId = 5;
		byte version = 1;
		Random rand = new Random(4);
		int challengeValue = rand.nextInt();
		DatagramPacket session = factory.createSessionMessage(sessionId, version, "DEFAULT", challengeValue);
		final DataInputStream input = new DataInputStream(new ByteArrayInputStream(session.getData()));
		assertEquals(MessageType.SESSION.getMessageId(), input.readByte());
		assertEquals(sessionId, input.readByte());
		assertEquals(version, input.readByte());
		assertEquals("DEFAULT".getBytes().length, input.readByte());
		byte[] stringArray = new byte["DEFAULT".length()];
		input.readFully(stringArray, 0, "DEFAULT".length());
		assertEquals("DEFAULT", new String(stringArray, Charset.forName("US-ASCII")));
		assertEquals(challengeValue, input.readInt());
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