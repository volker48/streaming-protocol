package edu.drexel.group5.protocol;

import com.google.common.base.Preconditions;
import edu.drexel.group5.MessageType;
import edu.drexel.group5.PacketFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.AudioSystem;

/**
 * This class opens a connection to the server to receive streaming media
 */
public class Client extends Thread {

	private static final Logger logger = Logger.getLogger(Client.class.getName());
	private static final int BUFFER_LENGTH = 4096;
	private static final int SOCKET_TIMEOUT = 5000;
	private static final byte CLIENT_VERSION = 1;
	private ObjectInputStream objectIn;
	private final String password;
	private final DatagramSocket socket;
	private final PacketFactory packetFactory;
	private final MessageDigest digest;
	private byte sessionId;
	private String streamType;
	private edu.drexel.group5.State state;
	private int challengeValue;
	// Playback data members
	private SourceDataLine audioLine;
	private float sampleRate;
	private int sampleSizeInBits;
	private int channels;
	private boolean audioSigned;
	private boolean bigEndian;

	public Client(InetAddress serverAddress, int serverPort, String password) {
		super("Streaming Protocol Client");
		Preconditions.checkArgument(serverPort >= 0 && serverPort <= 65535, serverPort + " is not a valid port");
		logger.log(Level.INFO, "Stream Client starting");
		this.password = password;
		this.packetFactory = new PacketFactory(serverPort, serverAddress);

		try {
			this.digest = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException ex) {
			throw new RuntimeException("Could not obtain the hash algoritm", ex);
		}

		try {
			this.socket = new DatagramSocket();
			socket.setSoTimeout(SOCKET_TIMEOUT);
		} catch (IOException ex) {
			throw new RuntimeException("Could not create a ClientSocket", ex);
		}


		this.state = edu.drexel.group5.State.DISCONNECTED;
	}

    public void parseAudioFormat(String streamType) {
        // parse stream type (not too robust, oh well)
        // example: PCM_SIGNED 8000.0 Hz, 16 bit, stereo, 4 bytes/frame, little-endian
        if(streamType.contains("8000.0 Hz")) {
            sampleRate = 8000;
        } else if(streamType.contains("16000.0 Hz")) {
            sampleRate = 16000;
        } else if(streamType.contains("44100.0 Hz")) {
            sampleRate = 44100;
        } else {
            throw new RuntimeException("Unknown sample rate");
        }

        if(streamType.contains("16 bit")) {
            sampleSizeInBits = 16;
        } else if(streamType.contains("8 bit")) {
            sampleSizeInBits = 8;
        } else {
            throw new RuntimeException("Unknown sample size in bits");
        }

        if(streamType.contains("stereo")) {
            channels = 2;
        } else if(streamType.contains("mono")) {
            channels = 1;
        } else {
            throw new RuntimeException("Unknown channel count");
        }

        if(streamType.contains("SIGNED")) {
            audioSigned = true;
        } else if(streamType.contains("UNSIGNED")) {
            audioSigned = false;
        } else {
            throw new RuntimeException("Unknown signed");
        }

        if(streamType.contains("little-endian")) {
            bigEndian = false;
        } else if(streamType.contains("big-endian")) {
            bigEndian = true;
        }
    }

	public void acceptSession(byte[] buffer) {
		logger.log(Level.INFO, "Received SESSION Message");
		if (state != edu.drexel.group5.State.CONNECTING) {
			logger.log(Level.WARNING, "Received SESSION - not in CONNECTING state");
		} else {
			try {
				DataInputStream bytestream = new DataInputStream(new ByteArrayInputStream(buffer, 1, BUFFER_LENGTH));
				sessionId = bytestream.readByte();
				int serverVersion = bytestream.readByte();
				int typelen = bytestream.readByte();
				byte[] typestringbyte = new byte[typelen];
				bytestream.readFully(typestringbyte, 0, typelen);
				streamType = new String(typestringbyte);

		        try {
			        AudioFormat format = new AudioFormat(sampleRate, sampleSizeInBits, channels, audioSigned, bigEndian);
			        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
			        audioLine = (SourceDataLine) AudioSystem.getLine(info);
			        audioLine.open(format);
			        audioLine.start();

			        // Now ready to receive audio buffer via the write method
			        logger.log(Level.INFO, "Now ready to playback audio when received.");

		        } catch (LineUnavailableException ex) {
			        throw new RuntimeException("Could not create an open audio line due to no line being available.");
		        } catch (Exception ex) {
			        throw new RuntimeException("Could not create an open audio line due to an unknown error.");
		        }

				if (serverVersion != CLIENT_VERSION) {
					logger.log(Level.WARNING, "Version mismatch: Server = " + serverVersion + " Client = " + CLIENT_VERSION);
					throw new RuntimeException("Server version does not match");
				}
				logger.log(Level.INFO, "Versions are ok...");
				challengeValue = bytestream.readInt();
				logger.log(Level.INFO, "Challenge Value from server: {0}", challengeValue);
				socket.send(packetFactory.createChallengeResponse(sessionId, challengeValue, password));
				logger.log(Level.INFO, "Challenge response sent!");
				state = edu.drexel.group5.State.AUTHENTICATING;
			} catch (IOException ex) {
				logger.log(Level.WARNING, "Problem?", ex);
			}
		}
	}

	public void acceptChallenge(byte[] buffer) {
		logger.log(Level.INFO, "Received CHALLENGE Message");
		if (state != edu.drexel.group5.State.CONNECTED) {
			logger.log(Level.WARNING, "Received CHALLENGE - not in CONNECTED state");
		} else {
			try {
				DataInputStream bytestream = new DataInputStream(new ByteArrayInputStream(buffer, 1, BUFFER_LENGTH - 1));
				byte sessionId = bytestream.readByte();
				int challengeValue = bytestream.readInt();

				socket.send(packetFactory.createChallengeResponse(sessionId, challengeValue, password));
				state = edu.drexel.group5.State.AUTHENTICATING;
			} catch (IOException ex) {
				logger.log(Level.WARNING, "Problem?");
			}
		}
	}

	public void acceptChallengeResult(byte[] buffer) {
		logger.log(Level.INFO, "Received CHALLENGE RESULT Message");
		if (state != edu.drexel.group5.State.AUTHENTICATING) {
			logger.log(Level.WARNING, "Received CHALLENGE_RESULT - not in AUTHENTICATING state");
		} else {
			try {
				DataInputStream bytestream = new DataInputStream(new ByteArrayInputStream(buffer, 1, BUFFER_LENGTH - 1));
				byte sessionId = bytestream.readByte();
				byte challengeResult = bytestream.readByte();

				if (challengeResult == 1) {
					// not sure we need the authenticated state since its never really used
					state = edu.drexel.group5.State.AUTHENTICATED;
					state = edu.drexel.group5.State.STREAMING;
				} else {
					// go back to connected and wait for challenge from server?
					// is this right? should we send a message to the server????
					// its unclear from our proposal
					// TODO: figure out if this is right
					state = edu.drexel.group5.State.CONNECTED;
				}
			} catch (IOException ex) {
				logger.log(Level.WARNING, "Problem?");
			}
		}
	}

	public void acceptAuthenticationError(byte[] buffer) {
		logger.log(Level.INFO, "Received AUTH ERROR Message");
		if (state != edu.drexel.group5.State.AUTHENTICATING) {
			logger.log(Level.WARNING, "Received AUTHENTICATION_ERROR - not in AUTHENTICATING state");
		} else {
			try {
				DataInputStream bytestream = new DataInputStream(new ByteArrayInputStream(buffer, 1, BUFFER_LENGTH - 1));
				byte sessionIdFromServer = bytestream.readByte(); //FIXME: We should be checking these every message
				int errorCode = bytestream.readInt();
				logger.log(Level.WARNING, "Authentication Error: {0}", errorCode);
			} catch (IOException ex) {
				logger.log(Level.WARNING, "Problem?");
			}
		}
	}

	public void acceptStream(byte[] buffer) {
		logger.log(Level.INFO, "Received STREAM Message");
		if (state != edu.drexel.group5.State.STREAMING) {
			logger.log(Level.WARNING, "Received STREAM - not in STREAMING state");
		} else {
			try {
				logger.log(Level.INFO, "In acceptingStream");
				DataInputStream bytestream = new DataInputStream(new ByteArrayInputStream(buffer, 1, BUFFER_LENGTH - 1));
				byte sessionIdFromServer = bytestream.readByte();

				if (sessionIdFromServer != sessionId) {
					logger.log(Level.SEVERE, "Session ID mismatch! Client id: {0}, Server id: {1}", new Object[]{sessionId, sessionIdFromServer});
				}

				// Decompose incoming stream message
				byte seqNum = bytestream.readByte();
				int datalen = bytestream.readInt();  
				byte data[] = new byte[datalen];
				bytestream.readFully(data, 0, datalen);
				int crcLength = bytestream.readInt();
				byte[] crcFromServer = new byte[crcLength];
				bytestream.readFully(crcFromServer, 0, crcLength);
				// compute CRC and check it
				byte[] computed_crc = digest.digest(data);
				if (!Arrays.equals(computed_crc, crcFromServer)) {
					logger.log(Level.SEVERE, "Data from server did not pass CRC!"); //FIXME: Not really sure what we should do here
				}

				// TODO: compare sequence numbers

				// Place received buffer into pre-configured, open audio playback buffer
				// TODO: It might be better to pass the audio format information in the StreamMessage
				logger.log(Level.INFO, "Writing to audio line");
				audioLine.write(data, 0, datalen);

			} catch (IOException ex) {
				logger.log(Level.WARNING, "Problem?", ex);

				// Drain and close the audio stream
				audioLine.drain();
				audioLine.close();
			}
		}
	}

	public void acceptStreamError(byte[] buffer) {
		logger.log(Level.INFO, "Received STREAM ERROR Message");
		if (state != edu.drexel.group5.State.STREAMING) {
			logger.log(Level.WARNING, "Received STREAM_ERROR - not in STREAMING state");
		} else {
			try {
				DataInputStream bytestream = new DataInputStream(new ByteArrayInputStream(buffer, 1, BUFFER_LENGTH - 1));
				byte sessionId = bytestream.readByte();
				int errorCode = bytestream.readInt();

				logger.log(Level.WARNING, "Stream Error: {0}", errorCode);

				// Drain and close the audio stream
				audioLine.drain();
				audioLine.close();

			} catch (IOException ex) {
				logger.log(Level.WARNING, "Problem?");
			}
		}
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

	private void sendSession() {
		logger.log(Level.INFO, "Sending SessionRequest!");
		try {
			this.socket.send(packetFactory.createSessionRequest(CLIENT_VERSION));
			this.state = edu.drexel.group5.State.CONNECTING;
		} catch (IOException ex) {
			throw new RuntimeException("Could not send session request", ex);
		}
	}

	public void timeoutConnecting() {
		sendSession();
	}

	public void timeoutConnected() {
		// nothing to do here
	}

	public void timeoutAuthenticating() {
		try {
			socket.send(packetFactory.createChallengeResponse(sessionId, challengeValue, password));
			logger.log(Level.INFO, "Sending ChallengeResponse");
		} catch (IOException ex) {
			Logger.getLogger(Client.class.getName()).log(Level.SEVERE, "Couldn't send challenge response!", ex);
		}
	}

	public void timeoutAuthenticated() {
		// unused state
	}

	public void timeoutStreaming() {
		// nothing to do here really
	}

	@Override
	public void run() {
		sendSession();
		while (!isInterrupted()) {
			try {
				final byte[] buffer = new byte[BUFFER_LENGTH];
				final DatagramPacket packet = new DatagramPacket(buffer, BUFFER_LENGTH);
				socket.receive(packet);

				// handle the received message
				MessageType message = MessageType.getMessageTypeFromId(packet.getData()[0]);
				logger.log(Level.INFO, "Rcved packet of MessageType: {0}", message);
				switch (message) {
					case SESSION:
						acceptSession(buffer);
						break;
					case CHALLENGE:
						acceptChallenge(buffer);
						break;
					case CHALLENGE_RESULT:
						acceptChallengeResult(buffer);
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
					default:
						logger.log(Level.WARNING, "Received an unexpected message: {0} dropping the packet", message);
				}
			} catch (SocketTimeoutException ex) {
				logger.log(Level.INFO, "Socket timedout. State is: {0}", state);
				// if the socket times out, we may need to resend the last message to the server
				switch (state) {
					case DISCONNECTED:
						timeoutDisconnected();
						break;
					case CONNECTING:
						timeoutConnecting();
						break;
					case CONNECTED:
						timeoutConnected();
						break;
					case AUTHENTICATING:
						timeoutAuthenticating();
						break;
					case AUTHENTICATED:
						timeoutAuthenticated();
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
		}
	}

	public static void main(String[] args) {
		Preconditions.checkArgument(args.length == 3, "Argument missing.\nUsage: server-ip server-port password");
		InetAddress serverAddress = null;
		try {
			serverAddress = InetAddress.getByName(args[0]);
		} catch (UnknownHostException ex) {
			logger.log(Level.SEVERE, "Unknown server", ex);
			System.exit(1);
		}
		Client client = new Client(serverAddress, Integer.parseInt(args[1]), args[2]);
		client.start();

		try {
			client.join();
		} catch (InterruptedException ex) {
			logger.log(Level.SEVERE, "The main thread was interrupted!", ex);
		}
	}
}
