package javaMiniSockets.clientSide;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.ReadPendingException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javaMiniSockets.messages.MessageInfoPair;
import javaMiniSockets.serverSide.ServerCouldNotConnectException;

/**
 * Checks continously for server messages after a fixed delay amd sends them to
 * the message queue. Accepts connection from the server and manages it's
 * disconnection.
 * 
 * @author Carmen Gómez Moreno
 *
 */
class ClientConnectionHandler implements CompletionHandler<AsynchronousSocketChannel, ServerInfo> {

	private String separator;
	private ScheduledExecutorService fixedReader;
	private AsynchronousClient asyncClient;
	private long delay_N = 33;
	private int FixedReader_N = 1;
	private int initialDelay_N = 0;
	private int bufferSize_N = 6144;
	@SuppressWarnings("unused")
	private ByteBuffer inputBuffer;
	private ServerInfo serverInfo;
	private ClientMessageHandler messageHandler;

	/**
	 * 
	 * @param asynchronousClient
	 * @param messageHandler
	 */
	protected ClientConnectionHandler(AsynchronousClient asynchronousClient, ClientMessageHandler messageHandler2) {
		asyncClient = asynchronousClient;
		messageHandler = messageHandler2;
		separator = System.lineSeparator();
		fixedReader = Executors.newScheduledThreadPool(FixedReader_N);

	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Called when the server opens a connection to send messages to the client.
	 * 
	 * @param result
	 * @param ServerInfo
	 */
	@Override
	public void completed(AsynchronousSocketChannel result, ServerInfo serverInfo) {
		this.serverInfo = serverInfo;
		this.serverInfo.inputBuffer = ByteBuffer.allocate(bufferSize_N);
		this.serverInfo.serverIn = result;

		asyncClient.serverInfo = this.serverInfo;

		fixedReader.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				readloop();
			}
		}, initialDelay_N, delay_N, TimeUnit.MILLISECONDS);

		messageHandler.onServerConnect(serverInfo);
	}

	/**
	 * Called when the server fails to establish a connection with the client.
	 * 
	 * @param exc
	 * @param server
	 */
	@Override
	public void failed(Throwable exc, ServerInfo server) {

		throw new ServerCouldNotConnectException("Server could not open a connection back");

	}


	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	private void stopReading() {
		if(!fixedReader.isShutdown()) {
		fixedReader.shutdown();
		}
	}
	/**
	 * Continously checks if there are messages coming from the server.
	 */
	
	private void readloop() {

		int bytesRead = -1;
		try {

			bytesRead = serverInfo.serverIn.read(serverInfo.inputBuffer).get();

		} catch (ReadPendingException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {

			
			messageHandler.onServerDisconnect(serverInfo);
			stopReading(); 

		}

		if (bytesRead != -1) {
			if (serverInfo.inputBuffer.position() > 2) {
				serverInfo.inputBuffer.flip();

				// Read
				byte[] lineBytes = new byte[bytesRead];
				serverInfo.inputBuffer.get(lineBytes, 0, bytesRead);

				// Lines are separated by system line separator and processed individually
				String fullLine = new String(lineBytes);
				String[] lines = fullLine.split(separator);
				serverInfo.inputBuffer.clear();

				// Deserialize
				for (String line : lines) {
					byte b[] = line.getBytes();
					serverInfo.serverInputBAOS = new ByteArrayInputStream(b);
					try {
						serverInfo.serverInput = new ObjectInputStream(serverInfo.serverInputBAOS);
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					Serializable message = null;
					try {
		
						message = (Serializable) serverInfo.serverInput.readObject();
						serverInfo.serverInput.close();
						serverInfo.serverInputBAOS.close();
					   
					} catch (Exception e) {
						e.printStackTrace();
					}
					// Send to queue
					MessageInfoPair pair = new MessageInfoPair(message, serverInfo);

					asyncClient.sendMessageToReadingQueue(pair);
				}
			}
		}

	}

}
