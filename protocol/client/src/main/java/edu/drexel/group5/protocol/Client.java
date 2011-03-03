package edu.drexel.group5.protocol;

import com.google.common.base.Preconditions;
import edu.drexel.group5.MessageType;
import edu.drexel.group5.PacketFactory;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class opens a connection to the server to receive streaming media
 */
public class Client extends Thread {
	private static final Logger logger = Logger.getLogger(Client.class.getName());
	private static final int BUFFER_LENGTH = 128; //TODO: This can be lowered I think the largest client-to-server message is pretty small.
    private static final byte CLIENT_VERSION = 1;

	private final InetAddress serverAddress;
	private final int serverPort;
    private final String password;
	private final DatagramSocket socket;
    private final PacketFactory packetFactory;
    private byte sessionId;
    private String streamType;
    private edu.drexel.group5.State state;


	public Client(InetAddress serverAddress, int serverPort, String password) {
		super("Streaming Protocol Client");
		Preconditions.checkArgument(serverPort >= 0 && serverPort <= 65535, serverPort + " is not a valid port");
		logger.log(Level.INFO, "Stream Client starting");
		this.serverAddress = serverAddress;
		this.serverPort = serverPort;
        this.password = password;
        this.packetFactory = new PacketFactory(serverPort, serverAddress);
		try {
			this.socket = new DatagramSocket();
			socket.setSoTimeout(1000);
		} catch (IOException ex) {
			throw new RuntimeException("Could not create a ClientSocket", ex);
		}

        try {
            // transmit first connection packet
            this.socket.send(packetFactory.createSessionRequest(CLIENT_VERSION));
            this.state = edu.drexel.group5.State.CONNECTING;
        } catch(IOException ex) {
			throw new RuntimeException("Could not send session request", ex);
        }
	}

    public void acceptSession(byte[] buffer) {
        if(state != edu.drexel.group5.State.CONNECTING) {
            logger.log(Level.WARNING, "Received SESSION - not in CONNECTING state");
        }

        try {
            DataInputStream bytestream = new DataInputStream(new ByteArrayInputStream(buffer, 1, BUFFER_LENGTH - 1));
            sessionId = bytestream.readByte();
            int serverVersion = bytestream.readByte();
            int typelen = bytestream.readByte();
            byte [] typestringbyte = new byte[typelen];
            bytestream.read(typestringbyte, 0, typelen);
            streamType = new String(typestringbyte);

            if(serverVersion != CLIENT_VERSION) {
			    throw new RuntimeException("Server version does not match");
            }

            // no message to send to the server
            // although i think we probably want to ack this
            state = edu.drexel.group5.State.CONNECTED;
        } catch(IOException ex) {
            logger.log(Level.WARNING, "Problem?");
        }
    }

    public void acceptChallenge(byte[] buffer) {
        if(state != edu.drexel.group5.State.CONNECTED) {
            logger.log(Level.WARNING, "Received CHALLENGE - not in CONNECTED state");
        }

        try {
            DataInputStream bytestream = new DataInputStream(new ByteArrayInputStream(buffer, 1, BUFFER_LENGTH - 1));
            sessionId = bytestream.readByte();
            int challengeValue = bytestream.readInt();

            socket.send(packetFactory.createChallengeResponse(sessionId, challengeValue, password));
            state = edu.drexel.group5.State.AUTHENTICATING;
        } catch(IOException ex) {
            logger.log(Level.WARNING, "Problem?");
        }
    }

	@Override
	public void run() {
		while (!isInterrupted()) {
			try {
				final byte[] buffer = new byte[BUFFER_LENGTH];
				final DatagramPacket packet = new DatagramPacket(buffer, BUFFER_LENGTH);
				socket.receive(packet);
				logger.log(Level.INFO, "Rcved packet: {0}", packet);

				MessageType message = MessageType.getMessageTypeFromId(buffer[0]);
                // might be good to refactor this
                switch(message) {
					case SESSION:
						break;
					case CHALLENGE:
                        acceptChallenge(buffer);
						break;
					case CHALLENGE_RESULT:
						break;
					case AUTHENTICATION_ERROR:
						break;
					case STREAM:
						break;
					case STREAM_ERROR:
						break;
					default:
						logger.log(Level.WARNING, "Received an unexpected message: {0} dropping the packet", message);
                }

				//packetQueue.add(packet);
			} catch (IOException ex) {
				logger.log(Level.WARNING, "Error handling packet!", ex);
			}
		}
	}

	public static void main(String[] args) {
		Preconditions.checkArgument(args.length == 3, "Argument missing.");
		InetAddress serverAddress = null;
		try {
			serverAddress = InetAddress.getByName(args[0]);
		} catch(UnknownHostException ex) {
			logger.log(Level.SEVERE, "Unknown server", ex);
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
