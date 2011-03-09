package edu.drexel.group5.protocol;

import edu.drexel.group5.MessageType;
import edu.drexel.group5.PacketFactory;
import edu.drexel.group5.protocol.ServerFinder.ServerInfo;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import javax.sound.sampled.AudioFormat;

/**
 * This class opens a connection to the server to receive streaming media
 */
public class Client extends Thread {

	private static final Logger logger = Logger.getLogger(Client.class.getName());
	private static final int BUFFER_LENGTH = 4096;
	private static final int SOCKET_TIMEOUT = 5000;
	private static final byte CLIENT_VERSION = 1;
	private final String password;
	private final DatagramSocket socket;
	private final PacketFactory packetFactory;
	private final MessageDigest md5;
	private byte sessionId;
	private edu.drexel.group5.State state;
	private int challengeValue;
	private StreamPlayer player;
	private final LinkedBlockingQueue<ByteBuffer> dataQueue;
	private Thread playerThread;
	private boolean isPaused = false;
	private InputStreamReader reader;

	public Client(ServerInfo info, String password) {
		super("Streaming Protocol Client");
		
		logger.log(Level.INFO, "Stream Client starting");
		this.password = password;
		this.packetFactory = new PacketFactory(info.port, info.ip);
		try {
			this.md5 = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException ex) {
			throw new RuntimeException("Could not obtain the hash algorithm", ex);
		}

		try {
			this.socket = new DatagramSocket();
			socket.setSoTimeout(SOCKET_TIMEOUT);
		} catch (IOException ex) {
			throw new RuntimeException("Could not create a ClientSocket", ex);
		}
		this.dataQueue = new LinkedBlockingQueue<ByteBuffer>();
		this.state = edu.drexel.group5.State.DISCONNECTED;

		// Send initial session request message to server
		sendSessionRequest();
	}

	public void acceptSession(byte[] buffer) throws IOException {
		logger.log(Level.INFO, "Received SESSION Message");
		if (state != edu.drexel.group5.State.CONNECTING) {
			logger.log(Level.WARNING, "Received SESSION - not in CONNECTING state");
			return;
		}
		DataInputStream bytestream = new DataInputStream(new ByteArrayInputStream(buffer, 1, BUFFER_LENGTH));
		sessionId = bytestream.readByte();
		byte serverVersion = bytestream.readByte();
		if (serverVersion != CLIENT_VERSION) {
			logger.log(Level.WARNING, "Version mismatch: Server = {0} Client = {1}", new Object[]{serverVersion, CLIENT_VERSION});
			throw new RuntimeException("Server version does not match");
		}
		logger.log(Level.INFO, "Versions are ok...");

		//get audio data
		challengeValue = bytestream.readInt();

		//create player
		AudioFormat format = getAudioFormatFromStream(bytestream);
		this.player = new StreamPlayer(dataQueue, format, md5);
		this.playerThread = new Thread(player, "StreamPlayer Thread");

		logger.log(Level.INFO, "Challenge Value from server: {0}", challengeValue);
		socket.send(packetFactory.createChallengeResponse(sessionId, challengeValue, password));
		logger.log(Level.INFO, "Challenge response sent!");
		state = edu.drexel.group5.State.AUTHENTICATING;
	}

	private AudioFormat getAudioFormatFromStream(DataInputStream bytestream) throws IOException {
		float sampleRate = bytestream.readFloat();
		int sampleSizeInBits = bytestream.readInt();
		int channels = bytestream.readInt();
		boolean audioSigned = bytestream.readBoolean();
		boolean bigEndian = bytestream.readBoolean();
		return new AudioFormat(sampleRate, sampleSizeInBits, channels, audioSigned, bigEndian);
	}

	public void acceptReChallenge(byte[] buffer) throws IOException {
		logger.log(Level.INFO, "Received CHALLENGE RESULT Message");
		if (state != edu.drexel.group5.State.AUTHENTICATING) {
			logger.log(Level.WARNING, "Received CHALLENGE_RESULT - not in AUTHENTICATING state");
			return;
		}
		ByteBuffer data = ByteBuffer.wrap(buffer);
		byte sessionIdFromServer = data.get(1);
		if (sessionIdFromServer != sessionId) {
			logger.log(Level.WARNING, "Received a message for another session! This Session ID: {0} Session ID in message: {1}",
					new Object[]{sessionId, sessionIdFromServer});
			return;
		}
		challengeValue = data.getInt(2);
		socket.send(packetFactory.createChallengeResponse(sessionId, challengeValue, password));

	}

	public void acceptAuthenticationError(byte[] buffer) throws IOException {
		logger.log(Level.INFO, "Received AUTH ERROR Message");
		if (state != edu.drexel.group5.State.AUTHENTICATING) {
			logger.log(Level.WARNING, "Received AUTHENTICATION_ERROR - not in AUTHENTICATING state");
			return;
		}
		DataInputStream bytestream = new DataInputStream(new ByteArrayInputStream(buffer, 1, BUFFER_LENGTH - 1));
		byte sessionIdFromServer = bytestream.readByte();
		if (sessionIdFromServer != sessionId) {
			logger.log(Level.SEVERE, "Session ID mismatch! Client id: {0}, Server id: {1}", new Object[]{sessionId, sessionIdFromServer});
			return;  // stop processing this message
		}

		int errorCode = bytestream.readInt();
		logger.log(Level.WARNING, "Authentication Error: {0}", errorCode);
	}

	public void acceptStream(byte[] buffer) {
		logger.log(Level.FINEST, "Received STREAM Message");
		if (state == edu.drexel.group5.State.AUTHENTICATING) {
			state = edu.drexel.group5.State.STREAMING;
			playerThread.start();
		}
		if (state != edu.drexel.group5.State.STREAMING) {
			logger.log(Level.WARNING, "Received STREAM - not in STREAMING state");
			return;
		}

		try {
			dataQueue.put(ByteBuffer.wrap(buffer));
		} catch (InterruptedException ex) {
			Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	public void acceptStreamError(byte[] buffer) throws IOException {
		logger.log(Level.INFO, "Received STREAM ERROR Message");
		if (state != edu.drexel.group5.State.STREAMING) {
			logger.log(Level.WARNING, "Received STREAM_ERROR - not in STREAMING state");
			return;
		}
		DataInputStream bytestream = new DataInputStream(new ByteArrayInputStream(buffer, 1, BUFFER_LENGTH - 1));
		byte sessionIdFromServer = bytestream.readByte();
		if (sessionIdFromServer != sessionId) {
			logger.log(Level.SEVERE, "Session ID mismatch! Client id: {0}, Server id: {1}", new Object[]{sessionId, sessionIdFromServer});
			return;  // stop processing this message
		}

		int errorCode = bytestream.readInt();

		logger.log(Level.WARNING, "Stream Error: {0}", errorCode);

		//Shutdown
		playerThread.interrupt();
		interrupt();
	}

	public void timeoutDisconnected() {
		logger.log(Level.INFO, "In timeoutDisconnected");
		try {
			this.socket.send(packetFactory.createSessionRequest(CLIENT_VERSION));
			this.state = edu.drexel.group5.State.CONNECTING;
		} catch (IOException ex) {
			throw new RuntimeException("Could not send session request", ex);
		}
	}

	private void sendSessionRequest() {
		logger.log(Level.INFO, "Sending SessionRequest!");
		try {
			this.socket.send(packetFactory.createSessionRequest(CLIENT_VERSION));
			this.state = edu.drexel.group5.State.CONNECTING;
		} catch (IOException ex) {
			throw new RuntimeException("Could not send session request", ex);
		}
	}

	public void timeoutConnecting() {
		sendSessionRequest();
	}

	public void timeoutAuthenticating() {
		try {
			socket.send(packetFactory.createChallengeResponse(sessionId, challengeValue, password));
			logger.log(Level.INFO, "Sending ChallengeResponse");
		} catch (IOException ex) {
			Logger.getLogger(Client.class.getName()).log(Level.SEVERE, "Couldn't send challenge response!", ex);
		}
	}

	public void timeoutStreaming() {
		// nothing to do here really
	}

	@Override
	public void run() {
		sendSessionRequest();
		while (!isInterrupted()) {
			try {
				final byte[] buffer = new byte[BUFFER_LENGTH];
				final DatagramPacket packet = new DatagramPacket(buffer, BUFFER_LENGTH);
				socket.receive(packet);

				// handle the received message
				MessageType message = MessageType.getMessageTypeFromId(buffer[0]);
				logger.log(Level.FINEST, "Rcved packet of MessageType: {0}", message);
				switch (message) {
					case SESSION:
						acceptSession(buffer);
						break;
					case RECHALLENGE:
						acceptReChallenge(buffer);
						break;
					case AUTHENTICATION_ERROR:
						acceptAuthenticationError(buffer);
						break;
					case STREAM:
						acceptStream(buffer);
						break;
					case STREAM_ERROR:
						acceptStreamError(buffer);
						break;
					case PAUSE: // used simply to keep socket alive
						break;
					default:
						logger.log(Level.WARNING, "Received an unexpected message: {0} dropping the packet", message);
				}
			} catch (SocketTimeoutException ex) {
				// if the socket times out, we may need to resend the last message to the server
				switch (state) {
					case DISCONNECTED:
						timeoutDisconnected();
						break;
					case CONNECTING:
						timeoutConnecting();
						break;
					case AUTHENTICATING:
						timeoutAuthenticating();
						break;
					case STREAMING:
						timeoutStreaming();
						break;
					default:
						throw new IllegalStateException("Unaccounted for state, " + state);
				}
			} catch (IOException ex) {
				logger.log(Level.WARNING, "Error handling packet!", ex);
			}
			checkConsole();
		}
	}

	private void checkConsole() {
		// Check system input for a pause command
		char input;
		reader = new InputStreamReader(System.in);
		try {
			if (reader.ready()) {
				input = (char) reader.read();
				if (input == 'p' || input == 'P') {
					isPaused = !isPaused;
					socket.send(packetFactory.createPauseMessage(sessionId, isPaused));
					logger.log(Level.INFO, "Sent Pause Message");
				}
				if (input == '+') {
					socket.send(packetFactory.createThrottleMessage(sessionId, 10240)); // Increase by 10kB/sec
					logger.log(Level.INFO, "Send Throttle Message, increased rate by 10kB/sec");
				}
				if (input == '-') {
					socket.send(packetFactory.createThrottleMessage(sessionId, -10240)); // Decrease by 10kB/sec
					logger.log(Level.INFO, "Send Throttle Message, decreased rate by 10kB/sec");
				}

			}
		} catch (IOException e) {
			logger.log(Level.WARNING, "Error reading from input!", e);
		}
	}

	public static void main(String[] args) throws IOException {
		Handler logFileHandler = new FileHandler("client.log", 8192, 5, true);
		Logger.getLogger("").addHandler(logFileHandler);
		ServerInfo info = null;
		Client client = null;
		if (args.length == 1) {
			logger.log(Level.INFO, "Trying to auto discover a local server...");
			final ServerFinder finder = new ServerFinder();
			info = finder.findServer();
			if (info == null) {
				logger.log(Level.SEVERE,
						"No server could be found on the local network! Try starting the client with the arguments server-ip server-port password");
				System.exit(1);
			}
			client = new Client(info, args[0]);
		} else if (args.length == 3) {
			InetAddress serverAddress = null;
			try {
				serverAddress = InetAddress.getByName(args[0]);
			} catch (UnknownHostException ex) {
				logger.log(Level.SEVERE, "Unknown server", ex);
				System.exit(1);
			}
			info = new ServerInfo(Integer.parseInt(args[1]), serverAddress);
			client = new Client(info, args[2]);
		}

		
		client.start();

		try {
			client.join();
		} catch (InterruptedException ex) {
			logger.log(Level.SEVERE, "The main thread was interrupted!", ex);
		}
	}
}
