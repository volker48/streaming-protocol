package edu.drexel.group5.protocol;

import edu.drexel.group5.MessageType;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;

/**
 * This class does the work of processing the client packets and provisioning
 * a new StreamSession if needed or routes the packet to an existing session.
 * @author Marcus McCurdy <marcus@drexel.edu>
 */
public class PacketHandler extends Thread {

	private static final Logger logger = Logger.getLogger(PacketHandler.class.getName());
	private final LinkedBlockingQueue<DatagramPacket> serverPacketQueue;
	private final Map<Byte, LinkedBlockingQueue<DatagramPacket>> sessions;
	private final Set<SocketAddress> connectedClients = new HashSet<SocketAddress>();
	private final Executor executor = Executors.newCachedThreadPool();
	private final DatagramSocket socket;
	private byte sessionId = 0;
	private final String pathToFile;
	private final AudioFormat format;

	public PacketHandler(LinkedBlockingQueue<DatagramPacket> packetQueue, 
			DatagramSocket socket, String pathToFile, AudioFormat format) {
		super("Packet Handler");
		this.socket = socket;
		this.serverPacketQueue = packetQueue;
		sessions = new HashMap<Byte, LinkedBlockingQueue<DatagramPacket>>();
		this.pathToFile = pathToFile;
		this.format = format;
	}

	@Override
	public void run() {
		while (!isInterrupted()) {
			try {
				logger.log(Level.INFO, "Waiting for packet...");
				DatagramPacket packet = serverPacketQueue.take();
				logger.log(Level.INFO, "Packet taken from queue");
				byte[] data = packet.getData();
				MessageType message = MessageType.getMessageTypeFromId(data[0]);
				logger.log(Level.INFO, "Received message type: {0}", message);
				switch (message) {
					//Setup a new StreamSession to handle the request and subsequent messages
					case SESSION_REQUEST:
						if (connectedClients.contains(packet.getSocketAddress())) {
							logger.log(Level.INFO, "Received a SessionRequest for an existing session! Packet Info: {0}", packet);
							continue;
						}
						setupNewStreamSession(packet);
						break;
					case THROTTLE:
					case CHALLENGE_RESPONSE:
					case DISCONNECT:
					case PAUSE:
						//fall through is intentional
						byte clientsSessionId = data[1];
						if (!connectedClients.contains(packet.getSocketAddress())) {
							logger.log(Level.WARNING, "Received MessageType: {0}, but no session exists for Session ID: {1}", new Object[]{message, sessionId});
							continue;
						}
						sessions.get(clientsSessionId).put(packet);
						break;
					default:
						logger.log(Level.WARNING, "Received an unexpected message: {0} dropping the packet", message);
				}
			} catch (InterruptedException ex) {
				interrupt();
				logger.log(Level.INFO, "PacketHandler interrupt received shutting down...");
			}
		}
	}

	private void setupNewStreamSession(DatagramPacket sessionRequest) {
		logger.log(Level.INFO, "Provisionning new Stream session with ip: {0}, port: {1}, and ID: {2}", new Object[]{sessionRequest.getAddress(), sessionRequest.getPort(), sessionId});
		final LinkedBlockingQueue<DatagramPacket> newSessionsQueue =
				new LinkedBlockingQueue<DatagramPacket>();
		sessions.put(sessionId, newSessionsQueue);
		final StreamSession session =
				new StreamSession(newSessionsQueue, sessionRequest,
				socket, sessionId, pathToFile, format);
		connectedClients.add(sessionRequest.getSocketAddress());
		sessionId++;
		executor.execute(session);
	}
}
