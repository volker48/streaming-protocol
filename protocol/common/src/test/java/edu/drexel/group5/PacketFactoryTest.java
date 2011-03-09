/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.drexel.group5;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
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
		System.out.println("hash = " + StringUtils.getHexString(hash));
		System.out.println("hash.length = " + hash.length);
		String secret = "secret";
		md.update("5".getBytes());
		md.update(secret.getBytes());
		byte[] hash2 = md.digest();
		System.out.println("hash2: " + StringUtils.getHexString(hash2));
		System.out.println("hash2.length = " + hash2.length);
	}

	@Test
	public void byteBufferTest() throws IOException {
		final byte[] data = new byte[5];
		final ByteBuffer buffer = ByteBuffer.wrap(data);
		final byte sessionId = 5;
		final int challengeValue = 1234568;
		buffer.put(sessionId);
		buffer.putInt(challengeValue);

		final DataInputStream input = new DataInputStream(new ByteArrayInputStream(data));
		byte fromInputSessionId = input.readByte();
		int fromInputChallenge = input.readInt();
		assertEquals(sessionId, fromInputSessionId);
		assertEquals(challengeValue, fromInputChallenge);
	}


	@Test
	public void	testCreateSessionMessage() throws Exception {
        // not sure how to do this test without specifying the path-to-file
        // which is necessary to determine audio format!
		/*DatagramSocket testSocket = new DatagramSocket(1337, InetAddress.getLocalHost());
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
		assertEquals(challengeValue, input.readInt());*/
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
