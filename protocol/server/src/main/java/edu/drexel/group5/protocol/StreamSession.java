package edu.drexel.group5.protocol;

import com.google.common.base.Preconditions;
import edu.drexel.group5.common.MessageType;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import edu.drexel.group5.common.PacketFactory;
import edu.drexel.group5.common.State;
import edu.drexel.group5.common.StringUtils;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.UnsupportedAudioFileException;

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
	private final DatagramPacket sessionRequest;
	private final AudioFormat format;
	private boolean isPaused = false;
	private int bytesPerMessage; // Calculated from the audio attributes and the sleep value	
	// This variable will change to reach the desired rate of bytes/sec
	private int sleep = 32; // in milliseconds

	/**
	 *
	 * @param sessionRequest a DatagramPacket that represents a SESSION_REQUEST message.
	 * The data in this packet will be used for future communication with the
	 * client.
	 */
	public StreamSession(LinkedBlockingQueue<DatagramPacket> packetQueue,
			DatagramPacket sessionRequest, DatagramSocket socket,
			byte sessionId, String pathToFile, AudioFormat format) {
		Preconditions.checkNotNull(packetQueue);
		Preconditions.checkNotNull(socket);
		Preconditions.checkArgument(packetQueue.size() == 0);
		this.pathToFile = pathToFile;
		this.packetQueue = packetQueue;
		this.socket = socket;
		this.sessionId = sessionId;
		this.sessionRequest = sessionRequest;
		this.format = format;
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
			try {
				//create stream Error message
				socket.send(factory.createDisconnectMessage(sessionId));
			} catch (IOException ex) {
				Logger.getLogger(StreamSession.class.getName()).log(Level.SEVERE, "", ex);
			}
			throw new RuntimeException("Client has incompatible version!");
		}
		final int challengeValue = rand.nextInt();
		logger.log(Level.INFO, "Challenge Value is: {0}", challengeValue);
		try {
			DatagramPacket session = factory.createSessionMessage(sessionId, SERVER_VERSION, challengeValue, format);
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

	/**
	 * Processes the clients authentication and handles sending the rechallenge
	 * message.
	 * @param serverCalculatedHash
	 * @return true if the client authenticates, false otherwise.
	 */
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
		logger.log(Level.INFO, "Session {0} shutting down...", sessionId);
		Thread.currentThread().interrupt();
	}

	private void handlePacketWhileStreaming(DatagramPacket packet) {
		logger.log(Level.INFO, "Received packet while streaming!"); //TODO: Implement
		byte[] data = packet.getData();
		MessageType messageType = MessageType.getMessageTypeFromId(data[0]);
		switch (messageType) {
			case PAUSE:
				handlePauseMessage(packet);
				break;
			case THROTTLE:
				handleThrottleMessage(packet);
				break;
		}

	}

	private void handlePauseMessage(DatagramPacket packet) {
		byte[] data = packet.getData();
		byte inputPause = data[2];
		logger.log(Level.INFO, "Processing Pause Message, sessionId = {0}, paused? = {1}", new Object[]{data[1], data[2]});
		isPaused = (inputPause == 1);
		logger.log(Level.INFO, "New paused status = ", inputPause);
	}

	private void handleThrottleMessage(DatagramPacket packet) {
		byte[] data = packet.getData();
		final DataInputStream input = new DataInputStream(new BufferedInputStream(new ByteArrayInputStream(data)));
		try {
			input.skipBytes(2);
			int inputRateDiff = input.readInt();
			logger.log(Level.INFO, "Processing Throttle Message, sessionId = {0}, rate change (bytes/sec) = {1}", new Object[]{data[1], inputRateDiff});
			int currentRate = (sleep / bytesPerMessage);
			int newRate = currentRate + inputRateDiff;
			sleep = (newRate / bytesPerMessage);
			logger.log(Level.INFO, "New rate = {0}", newRate);
			logger.log(Level.INFO, "New calculated sleep time (ms) = {0}", sleep);
		} catch (IOException ex) {
			logger.log(Level.WARNING, "Error processing throttle message.", ex);
		}
	}

	private void startStreaming() {
		streamer = new StreamingThread();
		streamer.start();
	}

	private class StreamingThread extends Thread {

		//private final BufferedInputStream input;
		private final AudioInputStream input;
		private int sequenceNumber = 0;
		private final MessageDigest digest;
		private final byte[] buffer;

		public StreamingThread() {
			logger.log(Level.INFO, "Streamer thread initialization");
			try {
				// Get the audio stream to transmit
				File soundFile = new File(pathToFile);
				input = AudioSystem.getAudioInputStream(soundFile);
				AudioFormat format = input.getFormat();

				// Logging to find out what audio file and format we are sending
				logger.log(Level.INFO, "Audio file properties: {0}", format.toString());
				logger.log(Level.INFO, "AUDIO - Frame Rate = {0}", format.getFrameRate());
				logger.log(Level.INFO, "AUDIO - Frame Size = {0}", format.getFrameSize());
				logger.log(Level.INFO, "AUDIO - Channels = {0}", format.getChannels());
				logger.log(Level.INFO, "AUDIO - Encoding = {0}", format.getEncoding());
				logger.log(Level.INFO, "AUDIO - Sample Rate = {0}", format.getSampleRate());
				logger.log(Level.INFO, "AUDIO - Sample Size = {0}", format.getSampleSizeInBits());
				logger.log(Level.INFO, "AUDIO - Big Endian = {0}", format.isBigEndian());
				logger.log(Level.INFO, "AUDIO - Frames (file) = {0}", input.getFrameLength());

			} catch (FileNotFoundException ex) {
				throw new RuntimeException("Could not open the file for streaming!", ex);
			} catch (UnsupportedAudioFileException ex) {
				throw new RuntimeException("Audio file type not supported.", ex);
			} catch (IOException ex) {
				throw new RuntimeException("Error when reading file.", ex);
			}
			try {
				digest = MessageDigest.getInstance("MD5");
			} catch (NoSuchAlgorithmException ex) {
				throw new RuntimeException("Could not obtain the hash algoritm", ex);
			}
			//This is the minimum number of bytes we can send per second to
			//properly play the stream in real time. 1000 is the number of ms in a second.
			bytesPerMessage = (format.getChannels() * format.getFrameSize()
					* (int) format.getFrameRate()) / (1000 / sleep);
			if (bytesPerMessage % format.getFrameSize() != 0) {
				bytesPerMessage++;
			}
			buffer = new byte[bytesPerMessage];
		}

		@Override
		public void run() {
			while (!isInterrupted()) {
				if (!isPaused) {
					int bytesRead;
					try {
						bytesRead = input.read(buffer, 0, bytesPerMessage);
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
						Thread.sleep(sleep);
					} catch (InterruptedException ex) {
						logger.log(Level.INFO, "Streaming cancelled!");
						interrupt();
					}
				} // While in a pause state, echo pause message to client to keep socket alive
				else {
					try {
						socket.send(factory.createPauseMessage(sessionId, isPaused));
						Thread.sleep(1000);
					} catch (InterruptedException ex) {
						interrupt();
					} catch (SocketException ex) {
						throw new RuntimeException("Problem echoing pause message!", ex);
					} catch (IOException ex) {
						throw new RuntimeException("Could not echo pause message!", ex);
					}
				}
			}
		}
	}
}
