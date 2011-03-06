package edu.drexel.group5.protocol;

import com.google.common.base.Preconditions;
import edu.drexel.group5.MessageType;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import edu.drexel.group5.PacketFactory;
import edu.drexel.group5.State;
import edu.drexel.group5.StringUtils;
import java.io.BufferedInputStream;
import java.security.MessageDigest;

/**
 * This class represents a session of the protocol. Each client should belong
 * to only one session, but the server will have 0 to many sessions.
 * @author Marcus McCurdy <marcus@drexel.edu>
 */
public class StreamSession implements Runnable {

	private static final Logger logger = Logger.getLogger(StreamSession.class.getName());
	private static final byte SERVER_VERSION = 1;
	private static final String STREAM_FORMAT = "DEFAULT";
	private final LinkedBlockingQueue<DatagramPacket> packetQueue;
	private final DatagramSocket socket;
	private final byte sessionId;
	private State state = State.DISCONNECTED;
	private static final int MAX_AUTH_RETRY = 5;
	private static final int MAX_RETRY_ERROR = 1;
	private final PacketFactory factory;
	final Random rand = new Random(System.currentTimeMillis());
	private static final String PASSWORD = "CS544GROUP5";
	private final String pathToFile;
	private StreamingThread streamer;

	/**
	 *
	 * @param packet a DatagramPacket that represents a SESSION_REQUEST message.
	 * The data in this packet will be used for future communication with the
	 * client.
	 */
	public StreamSession(LinkedBlockingQueue<DatagramPacket> packetQueue, DatagramSocket socket, byte sessionId, String pathToFile) {
		Preconditions.checkNotNull(packetQueue);
		Preconditions.checkNotNull(socket);
		Preconditions.checkArgument(packetQueue.size() == 1);
		this.pathToFile = pathToFile;
		this.packetQueue = packetQueue;
		this.socket = socket;
		this.sessionId = sessionId;
		DatagramPacket sessionRequest = null;
		try {
			sessionRequest = packetQueue.take();
			state = State.CONNECTING;
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			Logger.getLogger(StreamSession.class.getName()).log(Level.SEVERE, null, ex);
		}
		factory = new PacketFactory(sessionRequest.getSocketAddress());
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted() && state != State.DISCONNECTED) {
			final DatagramPacket packet;
			try {
				packet = packetQueue.take();
			} catch (InterruptedException ex) {
				shutdownSession();
				continue;
			}
			final byte[] data = packet.getData();
			MessageType messageType = MessageType.getMessageTypeFromId(data[0]);
			logger.log(Level.INFO, "Received a packet state is: {0}, MessageType is: {1}", new Object[]{state, messageType});
			if (state == State.CONNECTING && messageType == messageType.SESSION_REQUEST) {
				handleSessionRequest(packet);
			} else if (state == State.STREAMING) {
				handlePacketWhileStreaming(packet);
			}

		}
	}

	private void handleSessionRequest(DatagramPacket packet) {
		logger.log(Level.INFO, "Performing session handshake...");
		final int version = packet.getData()[1];
		if (version > SERVER_VERSION) {
			throw new RuntimeException("Client has incompatible version!");
		}
		final int challengeValue = rand.nextInt();
		logger.log(Level.INFO, "Challenge Value is: {0}", challengeValue);
		try {
			DatagramPacket session = factory.createSessionMessage(sessionId, SERVER_VERSION, STREAM_FORMAT, challengeValue);
			socket.send(session);
			logger.log(Level.INFO, "Session Message sent to client");
		} catch (IOException ex) {
			shutdownSession();
		}
		state = state.AUTHENTICATING;
		int counter = 0;
		final MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException ex) {
			throw new RuntimeException("SHA-1 is not available on this system!", ex);
		}
		md.update(factory.intToByteArray(challengeValue));
		md.update(PASSWORD.getBytes());
		byte[] serverCalculatedHash = md.digest();
		final String serverHash = StringUtils.getHexString(serverCalculatedHash);
		logger.log(Level.INFO, "Server Hash: {0}", serverCalculatedHash);
		while (counter < MAX_AUTH_RETRY && state != State.AUTHENTICATED) {
			DatagramPacket challengeResponse;
			try {
				challengeResponse = packetQueue.take();
			} catch (InterruptedException ex) {
				shutdownSession();
				return;
			}
			final byte[] data = challengeResponse.getData();
			MessageType type = MessageType.getMessageTypeFromId(data[0]);
			if (type != MessageType.CHALLENGE_RESPONSE) {
				logger.log(Level.WARNING, "Received MessageType: {0} while in the {1} state, ignoring", new Object[]{type, state});
				continue;
			}
			if (data[1] != sessionId) {
				logger.log(Level.WARNING, "Received a packet for session id: {0}, but this session has id: {1}",
						new Object[]{data[1], sessionId});
				continue;
			}
			int lengthOfHash = data[2];
			ByteArrayInputStream bis = new ByteArrayInputStream(data, 2, lengthOfHash);
			final byte[] responseHash = new byte[lengthOfHash];
			bis.read(responseHash, 0, lengthOfHash);
			String clientHash = StringUtils.getHexString(responseHash);
			logger.log(Level.INFO, "Client hash is: {0}", clientHash);
			try {
				if (responseHash != serverCalculatedHash) {
					logger.log(Level.WARNING, "Client did not authenticate!");
					socket.send(factory.createChallengeResult(sessionId, (byte) 0));
					counter++;
				} else {
					logger.log(Level.INFO, "Hashes match, transitioning to streaming state");
					state = State.STREAMING;
					socket.send(factory.createChallengeResult(sessionId, (byte) 1));
					logger.log(Level.INFO, "Challenge result sent to client");
					startStreaming();
				}
			} catch (IOException ex) {
				shutdownSession();
			}
		}
		if (counter == MAX_AUTH_RETRY) {
			state = State.DISCONNECTED;
			try {
				socket.send(factory.createAuthenticationError(sessionId, MAX_RETRY_ERROR));
			} catch (IOException ex) {
				shutdownSession();
			}
			logger.log(Level.SEVERE, "Could not authenticate client, max retries reached!");
			shutdownSession();
			return;
		}
	}

	private void shutdownSession() {
		Thread.currentThread().interrupt();
		logger.log(Level.INFO, "Session {0} shutting down...", sessionId);
	}

	private void handlePacketWhileStreaming(DatagramPacket packet) {
	}

	private void startStreaming() {
		streamer = new StreamingThread();
		streamer.start();
	}

	private class StreamingThread extends Thread {
		private final BufferedInputStream input;
		private byte sequenceNumber = 0;
		private final MessageDigest digest;
		private final byte[] buffer = new byte[2048];

		public StreamingThread() {
			logger.log(Level.INFO, "Streamer thread initialization");
			try {
				input = new BufferedInputStream(new FileInputStream(pathToFile));
			} catch (FileNotFoundException ex) {
				throw new RuntimeException("Could not open the file for streaming!", ex);
			}
			try {
				digest = MessageDigest.getInstance("MD5");
			} catch (NoSuchAlgorithmException ex) {
				throw new RuntimeException("Could not obtain the hash algoritm", ex);
			}
		}

		@Override
		public void run() {
			while (!isInterrupted()) {
				int bytesRead;
				try {
					bytesRead = input.read(buffer);
				} catch (IOException ex) {
					throw new RuntimeException("Error during streaming!", ex);
				}
				if (bytesRead == -1) {
					logger.log(Level.INFO, "End of stream reached, stream complete");
					try {
					input.close();
					} catch (IOException ex) {
						logger.log(Level.SEVERE, "Could not close the streams!", ex);
					}
					return;
				}
				byte[] crc = digest.digest(buffer);
				try {
					DatagramPacket streamMessage = factory.createStreamMessage(sessionId, sequenceNumber, buffer, crc);
					socket.send(streamMessage);
					sequenceNumber++;
				} catch (SocketException ex) {
					throw new RuntimeException("Problem creating stream message!", ex);
				} catch (IOException ex) {
					throw new RuntimeException("Could not send stream message!", ex);
				}
				try {
					Thread.sleep(100);
				} catch (InterruptedException ex) {
					logger.log(Level.INFO, "Streaming cancelled!");
					interrupt();
				}
			}
		}
	}
}
