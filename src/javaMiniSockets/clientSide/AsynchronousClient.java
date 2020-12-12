package javaMiniSockets.clientSide;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;


import javaMiniSockets.messages.CommonInternalMessage;
import javaMiniSockets.messages.ConnectionInternalMessage;
import javaMiniSockets.messages.MessageInfoPair;

/**
 * 
 * @author Carmen Gómez Moreno
 * 
 *         A simple client that connects to a server using a given address and
 *         port. Contains methods that send messages in the form of serializable
 *         objects to the server it has connected to. Can receive messages from
 *         the server. This client will process messages in a single thread
 *         separate from the thread that has instantiated it.
 *
 */

public class AsynchronousClient {

	private String address;
	private int port;
	private SocketChannel serverSocket;
	private AsynchronousServerSocketChannel client;
	@SuppressWarnings("unused")
	private OutputStream output;
	private ArrayBlockingQueue<MessageInfoPair> messageQueue;
	private ByteArrayOutputStream clientBAOS;
	private ObjectOutputStream clientOutput;
	private ObjectOutputStream heartOutput;
	private ByteArrayOutputStream heartBAOS;
	private ByteBuffer outputbuffer;
	private ThreadPoolExecutor queueReader;
	private ClientMessageHandler messageHandler;
	private String ownAddress;
	private ExecutorService executor;
	private ScheduledExecutorService beater;
	private int executorthreads_N = 1;
	private ReentrantLock clientLock;
	private MessageInfoPair lastReadMessage;
	private ClientConnectionHandler connectionHandler;
	private long lastTimestamp;
	private long initialDelay = 0;
	public int heartcount = 0;
	public int serverPort;
	public int clientPort;

	/**
	 * How often a heartbeat message is sent in milliseconds.
	 */
	public long heartBeatDelay = 3000;
	/*
	 * Size of the buffer used to send messages to the server
	 */
	public int outputbufferSize = 4096;
	/**
	 * Information about the server the client is connected to.
	 */
	public ServerInfo serverInfo;

	/**
	 * 
	 * @param serverAddress : Ipv4 of the server. // * @param port : Port the server
	 *                      is bound to. Any dynamic port up to 65,536.
	 * @param handler       : Instance of any object that extends abstract class
	 *                      ClientMessageHandler.
	 * @param clientPort    : Port this client will be bound to for receiving server
	 *                      messages. Any dynamic port up to 65,536.
	 */
	public AsynchronousClient(String serverAddress, String ownAddress, int serverPort, int clientPort,
			ClientMessageHandler handler) {

		if (ownAddress != null) {

			this.ownAddress = ownAddress;
		}
		this.serverPort = serverPort;
		this.clientPort = clientPort;
		messageHandler = handler;
		this.port = serverPort;
		this.address = serverAddress;
		outputbuffer = ByteBuffer.allocate(outputbufferSize);

		messageQueue = new ArrayBlockingQueue<MessageInfoPair>(100);
		@SuppressWarnings("unused")
		MessageInfoPair lastReadMessage;
		executor = Executors.newSingleThreadExecutor();
		beater = Executors.newScheduledThreadPool(executorthreads_N);

		clientLock = new ReentrantLock();
		lastTimestamp = System.currentTimeMillis();

	}
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Connects this client to the server using the address given in the
	 * constructor.
	 * 
	 * @throws IOException
	 */
	public void Connect() throws IOException {

		startConnection();
		connect();
		Thread clientThread = new Thread(() -> {
			try {
				run();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		clientThread.start();
		messageHandler.onConnect();

	}

	/**
	 * Disconnects the client from the server it has connected to.
	 * 
	 * @throws NotConnectedYetException
	 * @throws IOException
	 */
	public void disconnect() throws NotConnectedYetException, IOException {
		if (serverSocket.isConnected()) {
			stopHeartBeat();
			serverSocket.close();
			messageHandler.onDisconnect();

		} else {
			throw new NotConnectedYetException("There is no connection to disconnect from");
		}

	}

	/**
	 * Method used to send messages to the server. WARNING : Multiple messages sent
	 * in succession may not be received by the server.
	 * 
	 * @param message : Any Object that implements Serializable.
	 * @throws IOException
	 */
	public void send(Serializable message) throws IOException {

		String serializedMessage;
		CommonInternalMessage outMessage = new CommonInternalMessage(message, calculateTimestamp());

		clientBAOS = new ByteArrayOutputStream();
		clientOutput = new ObjectOutputStream(clientBAOS);
		clientOutput.writeObject(outMessage);
		clientOutput.flush();
		clientOutput.close();
		serializedMessage = clientBAOS.toString();
		clientBAOS.flush();
		clientBAOS.close();
		// System.out.println("Sending bytes" + serializedMessage.getBytes().length);

		executor.execute(() -> {
			try {
				sendRoutine(serializedMessage);
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Calculates difference between the last time the client has sent a message ,
	 * this is whenever the client sends a normal message or a heartbeat message.
	 * 
	 * @return
	 */
	private synchronized long calculateTimestamp() {

		long thisTimestamp = System.currentTimeMillis();
		long result = thisTimestamp - lastTimestamp;
		lastTimestamp = thisTimestamp;
		return result;

	}

	public String getOwnAddress() {
		return ownAddress;
	}

	public void setOwnAddress(String ownAddress) {
		this.ownAddress = ownAddress;
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
	 * Opens an AsynchronousServerSocketChannel and binds it to the client port
	 * before connecting to the server so that the server may establish a connection
	 * to the client right after the client connects to it.
	 */
	private void startConnection() {

		try {

			// System.out.println(ownAddress);
			client = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(ownAddress, clientPort));

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		ServerInfo serverinfo = new ServerInfo();
		connectionHandler = new ClientConnectionHandler(this, messageHandler);
		serverinfo.client = client;
		client.accept(serverinfo, connectionHandler);
	}

	/**
	 * Conects the client to the server.
	 * 
	 * @throws IOException
	 */
	private void connect() throws IOException {

		try {
			serverSocket = SocketChannel.open(new InetSocketAddress(address, port));

			startHeartBeat();
			sendAddressInfo();
			messageHandler.onConnect();
		} catch (IOException e) {
			serverSocket = null;
			System.err.println("Unable to connect to the server");
			e.printStackTrace();
		}
	}

	/**
	 * Starts the reading thread that will wait onto the message queue for any
	 * message the server may have sent.
	 * 
	 * @throws IOException
	 */
	private void run() throws IOException {

		queueReader = (ThreadPoolExecutor) Executors.newScheduledThreadPool(1);
		while (true) {
			Future<MessageInfoPair> resultado = queueReader.submit(() -> readfromqueue());
			try {
				CommonInternalMessage incomingMessage = (CommonInternalMessage) resultado.get().getMessage();
				// If the message is null it is considered a teartbeat from the client
				if (incomingMessage.getMessage() != null) {
					messageHandler.onMessageSent(incomingMessage.getMessage(), serverInfo);

				}

			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
		}
	}

	private void sendAddressInfo() {

		try {
			String serializedMessage;
			ConnectionInternalMessage outMessage = new ConnectionInternalMessage(ownAddress, clientPort);
			clientBAOS = new ByteArrayOutputStream();
			clientOutput = new ObjectOutputStream(clientBAOS);
			clientOutput.writeObject(outMessage);
			clientOutput.flush();
			clientOutput.close();
			serializedMessage = clientBAOS.toString();
			clientBAOS.flush();
			clientBAOS.close();
			// System.out.println("Sending bytes" + serializedMessage.getBytes().length);

			executor.execute(() -> {
				try {
					sendRoutine(serializedMessage);
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
		} catch (Exception e) {
		}
	}

	/**
	 * Continously sends a heartbeat message to the server to indicate that it is
	 * still connected.
	 */

	private void sendHeartbeat() {
		CommonInternalMessage heartbeat = new CommonInternalMessage(null, calculateTimestamp());

		String serializedMessage = null;

		try {
			clientLock.lock();
			heartBAOS = new ByteArrayOutputStream();
			heartOutput = new ObjectOutputStream(heartBAOS);

			heartOutput.writeObject(heartbeat);
			serializedMessage = heartBAOS.toString();

			serializedMessage += System.lineSeparator();

			outputbuffer.put(serializedMessage.getBytes());
			outputbuffer.flip();
			serverSocket.write(outputbuffer);

			heartBAOS.close();
			heartOutput.close();

		} catch (Exception e) {

			// System.out.println("Error writing heartbeat message");
			e.printStackTrace();
		} finally {
			outputbuffer.clear();
			clientLock.unlock();
		}

	}

	private void startHeartBeat() {

		// System.out.println("sending heartbeat");
		beater.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				sendHeartbeat();
			}

		}, initialDelay, heartBeatDelay, TimeUnit.MILLISECONDS);

	}

	/**
	 * Stops this client's heartbeat.
	 */
	private void stopHeartBeat() {
		if (beater.isShutdown()) {
			throw new UnsupportedOperationException("Heartbeat has not been started");
		}

		beater.shutdown();

	}

	/**
	 * Sends message to the server.
	 * 
	 * @param serializedMessage
	 * @throws IOException
	 */
	private void sendRoutine(String serializedMessage) throws IOException {

		clientLock.lock();
		try {
			serializedMessage += System.lineSeparator();

			outputbuffer.put(serializedMessage.getBytes());
			outputbuffer.flip();
			serverSocket.write(outputbuffer);

			outputbuffer.clear();

		} catch (Exception e) {
		} finally {

			clientLock.unlock();
		}

	}

	/**
	 * Continously tries to read from the message queue , will proccess any message
	 * it can retreive from the queue and call onMessageSent on the handler.
	 */
	@SuppressWarnings("unused")
	private void readloop() {
		queueReader = (ThreadPoolExecutor) Executors.newScheduledThreadPool(1);
		while (true) {
			Future<MessageInfoPair> resultado = queueReader.submit(() -> readfromqueue());
			try {
				CommonInternalMessage incomingMessage = (CommonInternalMessage) resultado.get().getMessage();
				// If the message is null it is considered a teartbeat from the client
				if (incomingMessage.getMessage() != null) {
					messageHandler.onMessageSent(incomingMessage.getMessage(), serverInfo);
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
		}

	}

	/**
	 * Method called by the reader thread that will block in the case that there are
	 * no messages in the message queue.
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
	 * Called by the connection handler to send messages to the readingqueue.
	 * 
	 * @param message
	 */
	protected void sendMessageToReadingQueue(MessageInfoPair message) {
		messageQueue.add(message);
		// TODO Implementar la cola y todo lo demas

	}

}
