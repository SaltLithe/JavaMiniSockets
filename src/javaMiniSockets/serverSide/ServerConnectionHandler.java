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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javaMiniSockets.clientSide.ClientCouldNotConnectException;
import javaMiniSockets.messages.MessageInfoPair;

/***
 * Checks continously for server messages after a fixed delay amd sends them to
 * the message queue. Accepts connection from the server and manages
 * disconnections. Keeps track of all of the clients connected.
 * 
 * 
 * 
 * @author Carmen Gómez Moreno
 *
 */

class ServerConnectionHandler implements CompletionHandler<AsynchronousSocketChannel, ClientInfo> {

	private ConcurrentHashMap<Integer, ClientInfo> clients;
	private AtomicInteger ids;
	private ScheduledExecutorService fixedReader;
	private ExecutorService readerPool;
	private AtomicInteger connected;
	private AsynchronousServer asyncServer;
	private final int MaxConnections;
	// private int clientport;
	private float heartbeatminimumClientSide = 10000;
	private float heartbeatminimumServerSide = 10000;
	private long delay_N = 33;
	private int FixedReader_N = 1;
	private int initialDelay_N = 0;
	private int bufferSize_N = 2048;
	private int surplus = 0;
	private int ReaderPool_N;
	private String separator = "DONOTWRITETHIS";
	private ServerMessageHandler serverMessageHandler;

	/**
	 * 
	 * @param server
	 * @param max
	 * @param ServerMessageHandler
	 */
	protected ServerConnectionHandler(AsynchronousServer server, int max, ServerMessageHandler mHandler) {

		serverMessageHandler = mHandler;
		MaxConnections = max;
		// this.clientport = clientport;
		this.asyncServer = server;
		ReaderPool_N = MaxConnections + surplus;

		connected = new AtomicInteger();
		clients = new ConcurrentHashMap<Integer, ClientInfo>();
		ids = new AtomicInteger();

		fixedReader = Executors.newScheduledThreadPool(FixedReader_N);
		readerPool = Executors.newFixedThreadPool(ReaderPool_N);

		fixedReader.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				readloop();
			}
		}, initialDelay_N, delay_N, TimeUnit.MILLISECONDS);

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
			e.printStackTrace();
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

		if (connected.get() < MaxConnections) {
			try {
				SocketAddress clientAddr = client.getRemoteAddress();
				clientInfo.server.accept(clientInfo, this);
				int clientID = ids.getAndIncrement();
				// Crear un mensaje especifico de conexion hacia atras que se detecte en bucle
				// principal en un else al final porque si no no deja
				// crear varios clientes por equipo y es una porqueria que el usuario tenga que
				// saberse dos puertos , ugh

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
		} else {
			throw new MaximumConnectionsReachedException("Maximum number of connections reached");

		}
	}

	/**
	 * Method used to disconnect a client.
	 * 
	 * @param clientInfo
	 */
	protected void disconnectClient(ClientInfo clientInfo) {
		try {
			clientInfo.clientInputLock.lock();
			clientInfo.clientOutputLock.lock();
			clients.remove(clientInfo.clientID);
			connected.decrementAndGet();
			clientInfo.clientOut.close();
		} catch (Exception e) {

		} finally {
			clientInfo.clientInputLock.unlock();
			clientInfo.clientOutputLock.unlock();
			serverMessageHandler.onClientDisconnect(clientInfo);
		}
	}

	private void readcheck(ClientInfo clientInfo) {

		clientInfo.clientInputLock.lock();
		

		try {

			int bytesRead = -1;
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
			if (bytesRead != -1) {
				if (clientInfo.inputBuffer.position() > 2) {
					clientInfo.inputBuffer.flip();

					// Read
					byte[] lineBytes = new byte[bytesRead];
					clientInfo.inputBuffer.get(lineBytes, 0, bytesRead);

					// Lines are separated by system line separator and processed individually
					String fullLine = new String(lineBytes);
					String[] lines = fullLine.split(separator);
					clientInfo.inputBuffer.clear();

					// Deserialize
					for (int i = 0; i < lines.length; i++) {

						String line = lines[i];
						byte b[] = line.getBytes();
						clientInfo.clientInputBAOS = new ByteArrayInputStream(b);
						// System.out.println("Line is " + line);

						try {

							clientInfo.clientInput = new ObjectInputStream(clientInfo.clientInputBAOS);

						} catch (IOException e1) {
							e1.printStackTrace();
						}
						Serializable message = null;

						try {

							// System.out.println(clientInfo.clientInput.readObject().toString());
							message = (Serializable) clientInfo.clientInput.readObject();
							clientInfo.clientInput.close();
							clientInfo.clientInputBAOS.close();
							clientInfo.clientInputBAOS.reset();

						} catch (ClassNotFoundException | IOException e) {
							e.printStackTrace();
						}
						// Send to queue
						if(message != null) {
						MessageInfoPair pair = new MessageInfoPair(message, clientInfo);

						asyncServer.sendMessageToReadingQueue(pair);
						clientInfo.lastHeartBeatServerSide = 0;
						}
					}
				}
				clientInfo.lastHeartBeatServerSide = System.currentTimeMillis();

			} else {
				// System.out.println("no message could be found");
				// System.out.println(System.currentTimeMillis() -
				// clientInfo.lastHeartBeatServerSide);
				if (System.currentTimeMillis() - clientInfo.lastHeartBeatServerSide >= heartbeatminimumServerSide) {
					disconnectClient(clientInfo);
				}
				updateHeartBeat(clientInfo.clientID, System.currentTimeMillis() - clientInfo.lastHeartBeatServerSide);

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
		disconnectClient(clients.get(clientID));
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
				disconnectClient(clients.get(clientID));
			} else {
				clients.get(clientID).lastHeartBeat = last;
			}
		} catch (NullPointerException e) {

			disconnectClient(clients.get(clientID));
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
			} catch (IOException e) {
				e.printStackTrace();
			}

			clients.get(key).clientInputLock.unlock();
			clients.get(key).clientOutputLock.unlock();

			disconnectClient(key);

		}

		connected.set(0);
	}

	public void openBackwardsConnection(int clientID, String clientIP, int clientPort) {
		System.out.println("Client ip is : " + clientIP);
		this.clients.get(clientID).connectToClient(clientIP, clientPort);

	}

}