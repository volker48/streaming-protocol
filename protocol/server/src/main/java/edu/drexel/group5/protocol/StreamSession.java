package edu.drexel.group5.protocol;

import com.google.common.base.Preconditions;
import edu.drexel.group5.MessageType;
import edu.drexel.group5.PacketFactory;
import edu.drexel.group5.State;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class represents a session of the protocol. Each client should belong
 * to only one session, but the server will have 0 to many sessions.
 * @author Marcus McCurdy <marcus@drexel.edu>
 */
public class StreamSession implements Runnable {

	private static final Logger logger = Logger.getLogger(StreamSession.class.getName());
	private final LinkedBlockingQueue<DatagramPacket> packetQueue;
	private final DatagramSocket socket;
	private final SocketAddress clientAddress;
	private final byte sessionId;
	private State state = State.DISCONNECTED;
	private static final byte VERSION = 1;
	private static final int MAX_AUTH_RETRY = 5;
	private static final int MAX_RETRY_ERROR = 1;

	/**
	 *
	 * @param packet a DatagramPacket that represents a SESSION_REQUEST message.
	 * The data in this packet will be used for future communication with the
	 * client.
	 */
	public StreamSession(LinkedBlockingQueue<DatagramPacket> packetQueue, DatagramSocket socket, byte sessionId) {
		Preconditions.checkNotNull(packetQueue);
		Preconditions.checkNotNull(socket);
		Preconditions.checkArgument(packetQueue.size() == 1);
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
		clientAddress = sessionRequest.getSocketAddress();
	}

	@Override
	public void run() {
		try {
			final PacketFactory factory = new PacketFactory(clientAddress);
			final Random rand = new Random(System.currentTimeMillis());
			DatagramPacket session = factory.createSessionMessage(sessionId, VERSION, "WAV", rand.nextInt());
			socket.send(session);
			state = state.AUTHENTICATING;
			int counter = 0;
			while (counter < MAX_AUTH_RETRY) {
				DatagramPacket challengeResponse = packetQueue.take();
				ByteArrayInputStream bis = new ByteArrayInputStream(challengeResponse.getData());
				MessageType type = MessageType.getMessageTypeFromId((byte) bis.read());
				if (type != MessageType.CHALLENGE_RESPONSE) {
					logger.log(Level.WARNING, "Received MessageType: {0} while in the {1} state!", new Object[]{type, state});
					counter++;
					continue;
				}
				//check hash in response.
			}
			if (counter == MAX_AUTH_RETRY) {
				state = State.DISCONNECTED;
				socket.send(factory.createAuthenticationError(sessionId, MAX_RETRY_ERROR));
				return;
			}
			while (!Thread.currentThread().isInterrupted() && state == State.STREAMING) {
			}
		} catch (IOException ex) {
			Logger.getLogger(StreamSession.class.getName()).log(Level.SEVERE, null, ex);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}
}
