package edu.drexel.group5.protocol;

import com.google.common.base.Preconditions;
import edu.drexel.group5.MessageType;
import java.io.ByteArrayInputStream;
import java.io.File;
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
import java.io.DataInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.sound.sampled.*;

/**
 * This class represents a session of the protocol. Each client should belong
 * to only one session, but the server will have 0 to many sessions.
 * @author Marcus McCurdy <marcus@drexel.edu>
 */
public class StreamSession implements Runnable {

	private static final Logger logger = Logger.getLogger(StreamSession.class.getName());
	private static final byte SERVER_VERSION = 1;
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
	private final DatagramPacket sessionRequest;

	/**
	 *
	 * @param sessionRequest a DatagramPacket that represents a SESSION_REQUEST message.
	 * The data in this packet will be used for future communication with the
	 * client.
	 */
	public StreamSession(LinkedBlockingQueue<DatagramPacket> packetQueue, DatagramPacket sessionRequest, DatagramSocket socket, byte sessionId, String pathToFile) {
		Preconditions.checkNotNull(packetQueue);
		Preconditions.checkNotNull(socket);
		Preconditions.checkArgument(packetQueue.size() == 0);
		this.pathToFile = pathToFile;
		this.packetQueue = packetQueue;
		this.socket = socket;
		this.sessionId = sessionId;
		this.sessionRequest = sessionRequest;
		state = State.CONNECTING;
		factory = new PacketFactory(sessionRequest.getSocketAddress());
	}

	@Override
	public void run() {
		logger.log(Level.INFO, "Streaming Session: {0} starting...", sessionId);
		handleSessionRequest(sessionRequest);
		while (!Thread.currentThread().isInterrupted() && state != State.DISCONNECTED) {
			final DatagramPacket packet;
			try {
				logger.log(Level.INFO, "Waiting for packets from client...");
				packet = packetQueue.take();
			} catch (InterruptedException ex) {
				shutdownSession();
				continue;
			}
			final byte[] data = packet.getData();
			MessageType messageType = MessageType.getMessageTypeFromId(data[0]);
			logger.log(Level.INFO, "Received a packet state is: {0}, MessageType is: {1}", new Object[]{state, messageType});
			handlePacketWhileStreaming(packet);

		}
	}

	private void handleSessionRequest(DatagramPacket packet) {
		logger.log(Level.INFO, "Performing session handshake...");
		final int client = packet.getData()[1];
		logger.log(Level.INFO, "Client version is: {0}", client);
		if (client > SERVER_VERSION) {
			throw new RuntimeException("Client has incompatible version!");
		}
		final int challengeValue = rand.nextInt();
		logger.log(Level.INFO, "Challenge Value is: {0}", challengeValue);
		try {
			DatagramPacket session = factory.createSessionMessage(sessionId, SERVER_VERSION, challengeValue, pathToFile);
			socket.send(session);
			logger.log(Level.INFO, "Session Message sent to client");
		} catch (IOException ex) {
			shutdownSession();
		}
		state = state.AUTHENTICATING;
		byte[] serverCalculatedHash = calculateHash(challengeValue, PASSWORD);
		logger.log(Level.INFO, "Server Hash: {0}", StringUtils.getHexString(serverCalculatedHash));
		boolean authSuccessful = authenticate(serverCalculatedHash);
		if (!authSuccessful) {
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

	private byte[] calculateHash(int challengeValue, String secret) {
		final MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException ex) {
			throw new RuntimeException("SHA-1 is not available on this system!", ex);
		}
		md.update(factory.intToByteArray(challengeValue));
		md.update(secret.getBytes());
		return md.digest();
	}

	private boolean authenticate(byte[] serverCalculatedHash) {
		int counter = 0;
		logger.log(Level.INFO, "Counter: {0}, State: {1}", new Object[]{counter, state});
		while (counter < MAX_AUTH_RETRY) {
			DatagramPacket challengeResponse;
			try {
				logger.log(Level.INFO, "Waiting for client's challenge response...");
				challengeResponse = packetQueue.take();
			} catch (InterruptedException ex) {
				shutdownSession();
				return false;
			}
			logger.log(Level.INFO, "Received a message from the client checking the message...");
			final byte[] data = challengeResponse.getData();
			final DataInputStream input = new DataInputStream(new BufferedInputStream(new ByteArrayInputStream(data)));
			try {
				MessageType type = MessageType.getMessageTypeFromId(input.readByte());
				if (type != MessageType.CHALLENGE_RESPONSE) {
					logger.log(Level.WARNING, "Received MessageType: {0} while in the {1} state, ignoring", new Object[]{type, state});
					continue;
				}
				logger.log(Level.INFO, "Message is a Challenge Response");
				byte clientSessionId = input.readByte();
				if (clientSessionId != sessionId) {
					logger.log(Level.WARNING, "Received a packet for session id: {0}, but this session has id: {1}",
							new Object[]{data[1], sessionId});
					continue;
				}
				int lengthOfHash = input.readInt();
				final byte[] responseHash = new byte[lengthOfHash];
				input.read(responseHash, 0, lengthOfHash);
				logger.log(Level.INFO, "Client hash is: {0}", StringUtils.getHexString(responseHash));
				if (!Arrays.equals(responseHash, serverCalculatedHash)) {
					logger.log(Level.WARNING, "Client did not authenticate!");
					int newChallenge = rand.nextInt();
					serverCalculatedHash = calculateHash(newChallenge, PASSWORD);
					socket.send(factory.createRechallengeMessage(sessionId, newChallenge));
					counter++;
				} else {
					logger.log(Level.INFO, "Hashes match, transitioning to streaming state");
					state = State.STREAMING;
					startStreaming();
					return true;
				}
			} catch (IOException ex) {
				shutdownSession();
			}
		}
		return false;
	}

	private void shutdownSession() {
		Thread.currentThread().interrupt();
		logger.log(Level.INFO, "Session {0} shutting down...", sessionId);
	}

	private void handlePacketWhileStreaming(DatagramPacket packet) {
		logger.log(Level.INFO, "Received packet while streaming!"); //TODO: Implement
	}

	private void startStreaming() {
		streamer = new StreamingThread();
		streamer.start();
	}

	private class StreamingThread extends Thread {

        private AudioInputStream input = null;
		private byte sequenceNumber = 0;
		private final MessageDigest digest;
		private final byte[] buffer = new byte[2048];

		public StreamingThread() {
			logger.log(Level.INFO, "Streamer thread initialization");
			try {
                input = AudioSystem.getAudioInputStream(new File(pathToFile));
                AudioFormat format = input.getFormat();
                logger.log(Level.INFO, "Audio format: " + format.toString());
			} catch (FileNotFoundException ex) {
				throw new RuntimeException("Could not open the file for streaming!", ex);
			} catch (UnsupportedAudioFileException ex) {
                throw new RuntimeException("Unsupported Audio File Exception", ex);
            } catch (IOException ex) {
                throw new RuntimeException("IOException", ex);
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
                    bytesRead = input.read(buffer, 0, buffer.length);
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
                    // TODO: playback is very sensitive to this value, there's probably
                    // a canonical way to determine it
					Thread.sleep(25); 
				} catch (InterruptedException ex) {
					logger.log(Level.INFO, "Streaming cancelled!");
					interrupt();
				}
			}
		}
	}
}
