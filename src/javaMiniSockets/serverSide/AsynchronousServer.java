package javaMiniSockets.serverSide;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.MoreExecutors;

//import com.dosse.upnp.UPnP;

import javaMiniSockets.messages.CommonInternalMessage;
import javaMiniSockets.messages.ConnectionInternalMessage;
import javaMiniSockets.messages.HandShakeInternalMessage;
import javaMiniSockets.messages.MessageInfoPair;

/**
 * 
 * A simple server that can accept multiple connections from multiple clients.
 * Receives messages from clients and opens a connection to them to send
 * messages back in the form of serializable objects. This server will process
 * messages in a single thread separate from the thread that has instantiated
 * it.
 *
 * @author Carmen G�mez Moreno
 *
 */

public class AsynchronousServer {

	private int port;
//	private int clientport;
	private ServerMessageHandler messageHandler;
	private ThreadPoolExecutor queueReader;
	private int messageQueue_N;
	private int maxClients;
	private ServerConnectionHandler serverHandler;
	private AsynchronousServerSocketChannel server = null;
	private ArrayBlockingQueue<MessageInfoPair> messageQueue;
	private MessageInfoPair lastReadMessage = null;
	private ThreadPoolExecutor sendPoolPool;
	private ExecutorService sendPool;
	private ByteArrayOutputStream serializeBAOS;
	private ObjectOutputStream serializeOutput;
	private Thread serverThread;
	private String ownAddress;
	private Semaphore notifySem;
	private Semaphore broadCastSem;

	/**
	 * 
	 * @param serverName     : Any string.
	 * @param messageHandler : Instance of any object that extends abstract class
	 *                       ServerMessageHandler.
	 * @param maxClients     : The maximum number of clients that can connect to
	 *                       this server.
	 */

	public AsynchronousServer(String serverName, ServerMessageHandler messageHandler, int maxClients, int port,
			String ownAddress, int messageQueueSize, String separator) {
		System.out.println("Update server");

		broadCastSem = new Semaphore(1);
		notifySem = new Semaphore(1);
		if (ownAddress != null) {
			this.ownAddress = ownAddress;
		}
		sendPoolPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
		sendPool = MoreExecutors.getExitingExecutorService(sendPoolPool, 100, TimeUnit.MILLISECONDS);

		this.messageQueue_N = messageQueueSize;
		this.port = port;
		// this.clientport = clientport;
		this.maxClients = maxClients;
		this.messageHandler = messageHandler;
		messageQueue = new ArrayBlockingQueue<MessageInfoPair>(messageQueue_N);

	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Starts the server in it's own thread
	 */
	public boolean Start() {

		boolean success = startConnections();

		if(success) {
		serverThread = new Thread(() -> run());
		// serverThread.setName(serverName);
		serverThread.start();

		messageHandler.onReady();
		}
		return success;
	}

	/**
	 * Stops the server from reading from the clients and closes it's connection.
	 */
	public void Stop() {
	try {
		if (serverThread.isAlive() && !serverThread.isInterrupted()) {
			serverThread.interrupt();
			serverHandler.closeAllConnections();
			messageHandler.onDisconnect();
		}
	}
	catch(Exception e) {
		e.printStackTrace();
	}
	}

	/**
	 * 
	 * Returns a list of all of the clients that are connected to the server
	 * 
	 * @return : ArrayList containing ClientInfo instances.
	 */
	public ArrayList<ClientInfo> getClients() {

		ConcurrentHashMap<Integer, ClientInfo> clientmap = serverHandler.getAllClients();
		Collection<ClientInfo> values = clientmap.values();
		ArrayList<ClientInfo> allclients = new ArrayList<ClientInfo>(values);

		return allclients;

	}

	public void close() {
		serverHandler.close();
	}

	/**
	 * Sends the given messages to the given clients.
	 * 
	 * @param clientIDS : An array containing the id of every client the message
	 *                  will be sent to.
	 * @param messages  : An array of any object that implements Serializable
	 *                  interface.
	 * @throws IOException : Thrown if the objects can't be serialized.
	 */
	public void sendMessage(int[] clientIDS, Serializable[] messages) throws IOException {
		try {
			// sendLock.lock();
			ConcurrentHashMap<Integer, ClientInfo> clientes = serverHandler.getSelectedClients(clientIDS);
			ArrayList<byte[]> serializedMessages = new ArrayList<byte[]>(messages.length);

			for (int i = 0; i < messages.length; i++) {

				serializeBAOS = new ByteArrayOutputStream();
				serializeOutput = new ObjectOutputStream(serializeBAOS);
				byte[] serializedMessage;
				CommonInternalMessage outMessage = new CommonInternalMessage(messages[i], 0);

				serializeOutput.writeObject(outMessage);
				serializeOutput.flush();
				serializeOutput.close();
				serializedMessage = serializeBAOS.toByteArray();
				serializeBAOS.close();
				serializedMessages.add(serializedMessage);
			}

			for (ClientInfo client : clientes.values()) {
				sendPool.execute(() -> {
					try {
						sendRoutine(client, serializedMessages);
					} catch (IOException e) {
						e.printStackTrace();
					}
				});
			}
		} finally {

			// sendLock.unlock();
		}

	}

	/**
	 * Sends the given messages to all of the clients connected to the server.
	 * 
	 * @param messages : An array of any object that implements Serializable
	 *                 interface.
	 * @throws IOException : Thrown if the objects can't be serialized.
	 */
	public void broadcastAllMessage(Serializable[] messages) throws IOException {
		try {
			if (serverHandler != null) {
				ConcurrentHashMap<Integer, ClientInfo> clientes = serverHandler.getAllClients();
				ArrayList<byte[]> serializedMessages = new ArrayList<byte[]>(messages.length);

				for (int i = 0; i < messages.length; i++) {

					byte[] serializedMessage;
					CommonInternalMessage outMessage = new CommonInternalMessage(messages[i], 0);

					serializeBAOS = new ByteArrayOutputStream();
					serializeOutput = new ObjectOutputStream(serializeBAOS);
					serializeOutput.writeObject(outMessage);
					serializeOutput.flush();
					serializeOutput.close();
					serializedMessage = serializeBAOS.toByteArray();
					serializeBAOS.close();
					serializedMessages.add(serializedMessage);
				}

				for (ClientInfo client : clientes.values()) {
					sendPool.execute(() -> {
						try {
							sendRoutine(client, serializedMessages);
						} catch (IOException e) {
							e.printStackTrace();
						}
					});
				}

			}
		} catch (Exception e1) {
			e1.printStackTrace();
		} finally {
		}
	}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Sends already serialized messages to the client
	 * 
	 * @param client
	 * @param serializedMessages
	 * @throws IOException
	 */
	private void sendRoutine(ClientInfo client, ArrayList<byte[]> serializedMessages) throws IOException {
		try {
			broadCastSem.acquire();
			client.clientOutputLock.lock();

			for (byte[] message : serializedMessages) {

				client.inputBuffer.putInt(message.length);
				client.inputBuffer.put(message);
				client.inputBuffer.flip();
				int written = 0;
				while (client.inputBuffer.hasRemaining()) {
					written += client.clientOut.write(client.inputBuffer);
				}
				if (written != (message.length + 4)) {
					System.out.println("Written : " + written + "out of " + (message.length + 4));
				}
				client.inputBuffer.clear();
				Thread.sleep(50);
			}

		} catch (Exception e) {
		} finally {
			client.clientOutputLock.unlock();
			broadCastSem.release();

		}

	}

	public String getOwnAddress() {
		return ownAddress;
	}

	public void setOwnAddress(String ownAddress) {
		this.ownAddress = ownAddress;
	}

	public void setAutomaticIP() {
		// ownAddress = UPnP.getLocalIP();
		ownAddress = "192.168.1.73";
	}

	public ArrayList<String> getAvailableIP() {

		@SuppressWarnings("rawtypes")
		Enumeration e = null;
		try {
			e = NetworkInterface.getNetworkInterfaces();
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		ArrayList<String> addresses = new ArrayList<String>();
		InetAddress i = null;
		while (e.hasMoreElements()) {
			NetworkInterface n = (NetworkInterface) e.nextElement();
			Enumeration<InetAddress> ee = n.getInetAddresses();
			while (ee.hasMoreElements()) {
				i = (InetAddress) ee.nextElement();
				addresses.add(i.getHostAddress());
				// System.out.println(i.getHostAddress());
			}
		}

		return addresses;
	}

	/**
	 * Method invoked by the constructor to start accepting connections , reading
	 * messages from the clients and sending them to the message qeue.
	 * 
	 */
	private boolean startConnections() {
		try {

			// System.out.println(ownAddress);
			server = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(ownAddress, port));

			ClientInfo clientInfo = new ClientInfo();
			clientInfo.server = server;
			serverHandler = new ServerConnectionHandler(this, maxClients, messageHandler);
			server.accept(clientInfo, serverHandler);
			return true;

		} catch (IOException e) {
			this.messageHandler.onServerConnectionProblem();
			e.printStackTrace();
			return false;

		}

	}

	/**
	 * Method used to read from the queue one message at a time to ensure that the
	 * message is read in a single thread for the user to manage.
	 */

	
	private void run() {

		queueReader = (ThreadPoolExecutor) Executors.newScheduledThreadPool(1);
		while (true) {
			Future<MessageInfoPair> resultado = queueReader.submit(() -> readfromqueue());
			try {

				try {
					CommonInternalMessage incomingMessage = (CommonInternalMessage) resultado.get().getMessage();
					// If the message is null it is considered a teartbeat from the client
					if (incomingMessage != null) {
						if (incomingMessage.getMessage() != null) {
							notifySem.acquire();
							messageHandler.onMessageSent(incomingMessage.getMessage(), resultado.get().getClient());

						}
						serverHandler.updateHeartBeat(resultado.get().getClient().clientID,
								incomingMessage.getTimestamp());
					}
				} catch (ClassCastException e) {
					try {
						ConnectionInternalMessage incomingMessage = (ConnectionInternalMessage) resultado.get()
								.getMessage();
						// System.out.println(incomingMessage.getAddress() + " "
						// +incomingMessage.getOpenPort());
						resultado.get().getClient().connectToClient(incomingMessage.getAddress(),
								incomingMessage.getOpenPort());
					} catch (ClassCastException e2) {

						HandShakeInternalMessage incomingHandshake = (HandShakeInternalMessage) resultado.get()
								.getMessage();
						serverHandler.openBackwardsConnection(resultado.get().getClient().clientID,
								incomingHandshake.address, incomingHandshake.port);

					}
				}
			} catch (InterruptedException e) {
				queueReader.shutdown();

				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			} finally {
				notifySem.release();
			}
		}

	}

	/**
	 * Reads from message queue and waits if no message is available until one is.
	 * 
	 * @return
	 */
	private MessageInfoPair readfromqueue() {
		try {

			lastReadMessage = messageQueue.take();
		} catch (Exception e) {

		}
		return lastReadMessage;

	}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Method invoked by this server's ConnectionHandler whenever a message has to
	 * be sent to the message queue.
	 * 
	 * @param message
	 */
	protected void sendMessageToReadingQueue(MessageInfoPair message) {
		messageQueue.offer(message);
	}

	protected void clientDisconnected(ClientInfo client) {

		messageHandler.onClientConnect(client);
	}

}