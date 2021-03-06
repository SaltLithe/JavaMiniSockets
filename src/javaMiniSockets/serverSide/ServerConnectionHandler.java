package javaMiniSockets.serverSide;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.ReadPendingException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.util.concurrent.MoreExecutors;
import com.sun.org.apache.xml.internal.security.exceptions.Base64DecodingException;
import com.sun.org.apache.xml.internal.security.utils.Base64;

import javaMiniSockets.clientSide.ClientCouldNotConnectException;
import javaMiniSockets.messages.MessageInfoPair;

/***
 * Checks continously for server messages after a fixed delay amd sends them to
 * the message queue. Accepts connection from the server and manages
 * disconnections. Keeps track of all of the clients connected.
 * 
 * 
 * 
 * @author Carmen G�mez Moreno
 *
 */

class ServerConnectionHandler implements CompletionHandler<AsynchronousSocketChannel, ClientInfo> {

	public int checkSum = 0;
	private ConcurrentHashMap<Integer, ClientInfo> clients;
	private AtomicInteger ids;
	private ScheduledExecutorService fixedReaderPool;
	private ExecutorService readerPoolPool;
	private AtomicInteger connected;
	private AsynchronousServer asyncServer;
	private final int MaxConnections;
	// private int clientport;
	private float heartbeatminimumClientSide = 10000;
	private float heartbeatminimumServerSide = 10000;
	private long delay_N = 33;
	private int FixedReader_N = 1;
	private int initialDelay_N = 0;
	private int bufferSize_N = 10240;
	private int ReaderPool_N = 1;
	private ServerMessageHandler serverMessageHandler;
	private boolean closed;
	ExecutorService readerPool;

	/**
	 * 
	 * @param server
	 * @param max
	 * @param ServerMessageHandler
	 */
	protected ServerConnectionHandler(AsynchronousServer server, int max, ServerMessageHandler mHandler) {

		com.sun.org.apache.xml.internal.security.Init.init();
		closed = false;
		serverMessageHandler = mHandler;
		MaxConnections = max;
		this.asyncServer = server;

		connected = new AtomicInteger();
		clients = new ConcurrentHashMap<Integer, ClientInfo>();
		ids = new AtomicInteger();

		fixedReaderPool = Executors.newScheduledThreadPool(FixedReader_N);
		readerPoolPool = Executors.newFixedThreadPool(ReaderPool_N);

		readerPool = MoreExecutors.getExitingExecutorService((ThreadPoolExecutor) readerPoolPool, 100,
				TimeUnit.MILLISECONDS);

	}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Called when any client opens a connection to send messages to the server.
	 * 
	 * @param client
	 * @param clientInfo
	 */

	@Override
	public void completed(AsynchronousSocketChannel client, ClientInfo attach) {

		try {
			addClient(client, attach);
		} catch (MaximumConnectionsReachedException e) {
			// e.printStackTrace();
		}

	}

	/**
	 * Called when any client fails to establish a connection with the server.
	 * 
	 * @param exception
	 * @param clientInfo
	 */
	@Override
	public void failed(Throwable e, ClientInfo attach) {
		throw new ClientCouldNotConnectException("A client could not connect to this server");
	}

	/**
	 * Invoked each time a client is added to the server
	 * 
	 * @param client
	 * @param clientInfo
	 * @throws MaximumConnectionsReachedException
	 * 
	 * 
	 */
	private synchronized void addClient(AsynchronousSocketChannel client, ClientInfo clientInfo)
			throws MaximumConnectionsReachedException {

		if (connected.get() < MaxConnections && !closed) {
			try {
				SocketAddress clientAddr = client.getRemoteAddress();
				clientInfo.server.accept(clientInfo, this);
				int clientID = ids.getAndIncrement();

				ClientInfo newClientInfo = new ClientInfo(clientInfo.server, client, ByteBuffer.allocate(bufferSize_N),
						clientAddr, clientID);
				serverMessageHandler.onServerConnect(clientInfo);

				newClientInfo.lastHeartBeatServerSide = System.currentTimeMillis();
				clients.put(clientID, newClientInfo);
				connected.incrementAndGet();
				serverMessageHandler.onClientConnect(clientInfo);

			} catch (IOException e) {
				e.printStackTrace();

			}
			if (connected.get() == 1) {

				fixedReaderPool.scheduleAtFixedRate(new Runnable() {
					@Override
					public void run() {
						readloop();
					}
				}, initialDelay_N, delay_N, TimeUnit.MILLISECONDS);
			}
		} else {
			throw new MaximumConnectionsReachedException("Maximum number of connections reached");

		}
	}

	/**
	 * Method used to disconnect a client.
	 * 
	 * @param clientInfo
	 */
	protected void disconnectClient(ClientInfo clientInfo, int ClientID) {
		try {
			clientInfo.clientInputLock.lock();
			clientInfo.clientOutputLock.lock();
			clients.remove(clientInfo.clientID);
			connected.decrementAndGet();
			clientInfo.clientOut.close();
			clientInfo.clientInputLock.unlock();
			clientInfo.clientOutputLock.unlock();
		} catch (Exception e) {

		} finally {

			serverMessageHandler.onClientDisconnect(ClientID);
		}
	}

	private void readcheck(ClientInfo clientInfo) {

		clientInfo.clientInputLock.lock();
		int bytesRead = -1;

		try {

			// We access the client's buffer to see if there are bytes to read
			try {

				bytesRead = clientInfo.clientIn.read(clientInfo.inputBuffer).get();

			} catch (ReadPendingException e) {
				// This should not happen with the semaphore in place
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				// If this is thrown , the client has most likely disconnected
				// so we will eliminate it and consider the read operation done

				clientInfo.clientOutputLock.lock();
				clients.remove(clientInfo.clientID);
				connected.decrementAndGet();
				clientInfo.clientOutputLock.unlock();

			}

			// If there are bytes to read they are read and parsed to a string , this string
			// is deserialized and sent to the
			// message queue and the client's buffer is cleared so it can be read from again
			if (bytesRead >= 4) {

				byte[] internal = clientInfo.inputBuffer.array();
				byte[] expectedBytes = { internal[0], internal[1], internal[2], internal[3] };
				ByteBuffer expectedBuffer = ByteBuffer.wrap(expectedBytes);
				int expected = expectedBuffer.getInt();
				if (expected <= bytesRead - 4) {

					if (clientInfo.inputBuffer.position() > 2) {

						clientInfo.inputBuffer.flip();

						int numberOfBytes = clientInfo.inputBuffer.getInt();
						byte[] lineBytes = new byte[numberOfBytes];
						clientInfo.inputBuffer.get(lineBytes, 0, numberOfBytes);

						try {
							ByteArrayInputStream InputBAOS = new ByteArrayInputStream(lineBytes);
							ObjectInputStream clientInput = new ObjectInputStream(InputBAOS);
							Serializable message = (Serializable) clientInput.readObject();

							InputBAOS.close();
							InputBAOS.reset();
							clientInput.close();
							InputBAOS = null;
							clientInput = null;
							if (message != null) {

								MessageInfoPair pair = new MessageInfoPair(message, clientInfo);
								// System.out.println("Message is : " + message);
								asyncServer.sendMessageToReadingQueue(pair);
								clientInfo.lastHeartBeatServerSide = 0;
							}
						} catch (ClassNotFoundException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						} finally {
							clientInfo.lastHeartBeatServerSide = System.currentTimeMillis();
							clientInfo.inputBuffer.compact();

						}
					}

				} else {
					
					if (System.currentTimeMillis() - clientInfo.lastHeartBeatServerSide >= heartbeatminimumServerSide) {
						disconnectClient(clientInfo, clientInfo.clientID);
					}
					updateHeartBeat(clientInfo.clientID,
							System.currentTimeMillis() - clientInfo.lastHeartBeatServerSide);
				}
			}
		} finally {

			clientInfo.clientInputLock.unlock();
		}

	}

	/**
	 * Method called continoustly after a delay by the reader thread to check
	 * messages from each client in it's own thread.
	 */
	private void readloop() {

		if (connected.get() > 0) {
			for (int key : clients.keySet()) {
				ClientInfo client = clients.get(key);
				try {
					if (!client.clientInputLock.isLocked()) {
						readerPool.execute(() -> readcheck(client));
					}
				} catch (Exception e) {
				}
			}

		} else {
			fixedReaderPool.shutdown();
			readerPool.shutdown();
		}
	}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Returns the collection of clients connected at time of invoking
	 * 
	 * @return
	 */
	protected ConcurrentHashMap<Integer, ClientInfo> getAllClients() {
		return clients;
	}

	/**
	 * Returns the collection of selected clients.
	 * 
	 * @param clientIDS
	 * @return
	 */
	protected ConcurrentHashMap<Integer, ClientInfo> getSelectedClients(int[] clientIDS) {
		ConcurrentHashMap<Integer, ClientInfo> selected = new ConcurrentHashMap<Integer, ClientInfo>();
		for (int id : clientIDS) {
			selected.put(id, clients.get(id));
		}

		return selected;

	}

	/**
	 * Disconnects a client given it's id
	 * 
	 * @param clientID
	 */
	protected void disconnectClient(int clientID) {
		disconnectClient(clients.get(clientID), clientID);
	}

	/**
	 * Checks client's hearbeat and disconnects it if the different between the new
	 * heartbeat and the last heartbeat is greater than the minimum difference
	 * allowed.
	 * 
	 * @param clientID
	 * @param last
	 */
	protected void updateHeartBeat(int clientID, long last) {
		// System.out.println("Update heartbeat");
		try {

			if (last - clients.get(clientID).lastHeartBeat > heartbeatminimumClientSide) {
				// System.out.println("Last heartbeat " + (last -
				// clients.get(clientID).lastHeartBeat));
				disconnectClient(clients.get(clientID), clientID);
			} else {
				clients.get(clientID).lastHeartBeat = last;
			}
		} catch (NullPointerException e) {

			disconnectClient(clients.get(clientID), clientID);
		}
	}

	/**
	 *  
	 */
	protected void closeAllConnections() {
		for (int key : clients.keySet()) {

			clients.get(key).clientInputLock.lock();
			clients.get(key).clientOutputLock.lock();

			try {
				clients.get(key).clientOut.close();
				clients.get(key).clientIn.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			clients.get(key).clientInputLock.unlock();
			clients.get(key).clientOutputLock.unlock();

			disconnectClient(key);

		}

		connected.set(0);
	}

	protected void close() {
		closed = true;
	}

	public void openBackwardsConnection(int clientID, String clientIP, int clientPort) {
		this.clients.get(clientID).connectToClient(clientIP, clientPort);

	}

}