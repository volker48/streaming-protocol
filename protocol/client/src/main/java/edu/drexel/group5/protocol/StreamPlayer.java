package edu.drexel.group5.protocol;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

/**
 *
 * @author Marcus McCurdy <marcus@drexel.edu>
 */
public class StreamPlayer implements Runnable {

	private static final Logger logger = Logger.getLogger(StreamPlayer.class.getName());
	private final LinkedBlockingQueue<ByteBuffer> dataQueue;
	private final AudioFormat format;
	private int curSeqNum = -1;
	private SourceDataLine audioLine;
	private final MessageDigest md5;

	public StreamPlayer(LinkedBlockingQueue<ByteBuffer> dataQueue, AudioFormat format, MessageDigest md5) {
		this.dataQueue = dataQueue;
		this.format = format;
		this.md5 = md5;
	}

	@Override
	public void run() {
		openAudioLine();
		while (!Thread.currentThread().isInterrupted()) {
			ByteBuffer buffer = null;
			try {
				buffer = dataQueue.take();
			} catch (InterruptedException ex) {
				logger.log(Level.INFO, "Stream player shutting down...");
				Thread.currentThread().interrupt();
			}

			// Decompose incoming stream message
			buffer.position(1);//skip the MessageType byte
			byte sessionIdFromServer = buffer.get();
			int seqNum = buffer.getInt();
			int datalen = buffer.getInt();
			byte[] audio = new byte[datalen];
			buffer.get(audio, 0, datalen);
			int crcLength = buffer.getInt();
			byte[] crc = new byte[crcLength];
			buffer.get(crc, 0, crcLength);
			logger.log(Level.INFO, "Stream Message Details [SESSID:{0},SEQNUM:{1},DATALEN:{2}]", new Object[]{sessionIdFromServer, seqNum, datalen});

			curSeqNum++;

			// Handle CRC
			// compute CRC and check it
			byte[] computed_crc = md5.digest(audio);
			if (!Arrays.equals(computed_crc, crc)) {
				logger.log(Level.SEVERE, "Data from server did not pass CRC!");
				continue;
			}

			// Compare sequence numbers
			if (curSeqNum == seqNum) {
				// We have received the next sequence number expected
				logger.log(Level.INFO, "Valid sequencing: SeqNum expected={0}, SeqNum received={1}", new Object[]{curSeqNum, seqNum});
			} else {
				// We did not receive the sequence number expected, probably due to lag
				logger.log(Level.INFO, "Invalid sequencing: SeqNum expected={0}, SeqNum received={1}. Missed {2} packets.", new Object[]{curSeqNum, seqNum, (seqNum - curSeqNum)});

				// Rather than stop playing the audio, probably better to throw out the missed frame and move on.
				// A good way to fix the lag is to slow the message rate.
				// Reset the sequence number we are expecting.
				curSeqNum = seqNum;
			}


			// Place received buffer into pre-configured, open audio playback buffer
			// TODO: It might be better to pass the audio format information in the StreamMessage
			logger.log(Level.INFO, "Writing to audio line");
			int bytesAvailable = audioLine.available();
			audioLine.write(audio, 0, datalen);
		}
	}

	private void openAudioLine() {
		logger.log(Level.INFO, "Opening SourceDataLine...");
		logger.log(Level.INFO, "Audio Format: {0}", format);
		Mixer javaMixer = null;
		for (Mixer.Info info : AudioSystem.getMixerInfo()) {
			if (info.getName().toLowerCase().contains("java")) {
				javaMixer = AudioSystem.getMixer(info);
				break;
			}
		}
		if (javaMixer == null) {
			throw new RuntimeException("No suitable mixer found!");
		}
		try {
			DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
			audioLine = (SourceDataLine) javaMixer.getLine(info);
			audioLine.open(format);
			audioLine.start();

			// Now ready to receive audio buffer via the write method
			logger.log(Level.INFO, "Now ready to playback audio when received.");

		} catch (LineUnavailableException ex) {
			throw new RuntimeException("Could not create an open audio line due to no line being available.");
		}
	}
}
