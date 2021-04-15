package javaMiniSockets.clientSide;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.ReadPendingException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.util.concurrent.MoreExecutors;

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

	private ScheduledExecutorService fixedReaderPool;
	private ExecutorService readerPoolPool;
	private ExecutorService readerPool;
	private ExecutorService fixedReader;
	private AsynchronousClient asyncClient;
	private long delay_N = 100;
	private int FixedReader_N = 1;
	private int initialDelay_N = 0;
	private int bufferSize_N = 8192;
	@SuppressWarnings("unused")
	private ByteBuffer inputBuffer;
	private ServerInfo serverInfo;
	private ClientMessageHandler messageHandler;
	private ReentrantLock inputLock;

	/**
	 * 
	 * @param asynchronousClient
	 * @param messageHandler
	 */
	protected ClientConnectionHandler(AsynchronousClient asynchronousClient, ClientMessageHandler messageHandler2) {
		readerPoolPool = Executors.newFixedThreadPool(1);
		readerPool = MoreExecutors.getExitingExecutorService((ThreadPoolExecutor) readerPoolPool, 100,
				TimeUnit.MILLISECONDS);
		inputLock = new ReentrantLock();
		asyncClient = asynchronousClient;
		messageHandler = messageHandler2;
		fixedReaderPool = Executors.newScheduledThreadPool(FixedReader_N);
		System.out.println("Client handler updated");
	}

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

		fixedReaderPool.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {

				readCheck();

			}

			private void readCheck() {
				try {
					if (!inputLock.isLocked()) {
						readerPool.execute(() -> readloop());
					}
				} catch (Exception e) {
				}

			}
		}, initialDelay_N, delay_N, TimeUnit.MILLISECONDS);
		fixedReader = MoreExecutors.getExitingExecutorService((ThreadPoolExecutor) fixedReaderPool, 100,
				TimeUnit.MILLISECONDS);

		asyncClient.connectedFlag = true;

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
		if (!fixedReader.isShutdown()) {
			fixedReader.shutdown();
		}
	}

	/**
	 * Continously checks if there are messages coming from the server.
	 */

	private void readloop() {

		inputLock.lock();
		int bytesRead = -1;

		try {
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

			if (bytesRead >= 4) {
				byte[] internal = serverInfo.inputBuffer.array();
				byte[] expectedBytes = { internal[0], internal[1], internal[2], internal[3] };
				ByteBuffer expectedBuffer = ByteBuffer.wrap(expectedBytes);
				int expected = expectedBuffer.getInt();
				if (expected <= bytesRead - 4) {

					if (serverInfo.inputBuffer.position() > 2) {

						serverInfo.inputBuffer.flip();
						int numberOfBytes = serverInfo.inputBuffer.getInt();
						byte[] lineBytes = new byte[numberOfBytes];
						serverInfo.inputBuffer.get(lineBytes, 0, numberOfBytes);


						try {

							ByteArrayInputStream InputBAOS = new ByteArrayInputStream(lineBytes);
							ObjectInputStream serverInput = new ObjectInputStream(InputBAOS);
							Serializable message = (Serializable) serverInput.readObject();

							InputBAOS.close();
							InputBAOS.reset();
							serverInput.close();
							InputBAOS = null;
							serverInput = null;
							// Send to queue

							if (message != null) {
								MessageInfoPair pair = new MessageInfoPair(message, serverInfo);
								asyncClient.sendMessageToReadingQueue(pair);
							}

						} catch (Exception e11) {
							e11.printStackTrace();
						} finally {
							serverInfo.inputBuffer.compact();
						}

					}
				}
			}
			serverInfo.inputBuffer.clear();

		} finally {
			inputLock.unlock();

		}
	}
}