package edu.drexel.group5.protocol;

import edu.drexel.group5.MessageType;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class does the work of processing the client packets and provisioning
 * a new StreamSession if needed or routes the packet to an existing session.
 * @author Marcus McCurdy <marcus@drexel.edu>
 */
public class PacketHandler extends Thread {

	private static final Logger logger = Logger.getLogger(PacketHandler.class.getName());
	private final LinkedBlockingQueue<DatagramPacket> packetQueue;
	private final Map<String, LinkedBlockingQueue<DatagramPacket>> sessions;
	private final Executor executor = Executors.newCachedThreadPool();
	private final DatagramSocket socket;
	private byte sessionId;
	private final String pathToFile;

	public PacketHandler(LinkedBlockingQueue<DatagramPacket> packetQueue, DatagramSocket socket, String pathToFile) {
		super("Packet Handler");
		this.socket = socket;
		this.packetQueue = packetQueue;
		sessions = new ConcurrentHashMap<String, LinkedBlockingQueue<DatagramPacket>>();
		this.pathToFile = pathToFile;
	}

	@Override
	public void run() {
		while (!isInterrupted()) {
			try {
				DatagramPacket packet = packetQueue.take();
				byte[] data = packet.getData();
				MessageType message = MessageType.getMessageTypeFromId(data[0]);
				String ip = packet.getAddress().getHostAddress();
				logger.log(Level.INFO, "Received {0} message", message);
				switch (message) {
					//Setup a new StreamSession to handle the request and subsequent messages
					case SESSION_REQUEST:
						if (sessions.containsKey(ip)) {
							logger.log(Level.WARNING, "Received a SessionRequest for an existing session! Packet Info: {0}", packet);
							continue;
						}
						setupNewStreamSession(ip, packet);
						break;
					case THROTTLE:
					case CHALLENGE_RESPONSE:
					case DISCONNECT:
						//fall through is intentional
						if (!sessions.containsKey(ip)) {
							logger.log(Level.WARNING, "Received MessageType: {0}, but no session exists for IP: {1}", new Object[]{message, ip});
							continue;
						}
						sessions.get(ip).put(packet);
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

	private void setupNewStreamSession(String ip, DatagramPacket packet) {
		logger.log(Level.INFO, "Setting up new stream session for IP {0}", ip);
		final LinkedBlockingQueue<DatagramPacket> sessionsQueue = new LinkedBlockingQueue<DatagramPacket>();
		sessionsQueue.add(packet);
		sessions.put(ip, sessionsQueue);
		final StreamSession session = new StreamSession(sessionsQueue, socket, sessionId, pathToFile);
		executor.execute(session);
	}
}
