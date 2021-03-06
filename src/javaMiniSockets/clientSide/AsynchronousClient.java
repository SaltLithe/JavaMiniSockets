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
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


import com.google.common.util.concurrent.MoreExecutors;

//import com.dosse.upnp.UPnP;

import javaMiniSockets.messages.CommonInternalMessage;
import javaMiniSockets.messages.HandShakeInternalMessage;
import javaMiniSockets.messages.MessageInfoPair;

/**
 * 
 * @author Carmen G�mez Moreno
 * 
 *         A simple client that connects to a server using a given address and
 *         port. Contains methods that send messages in the form of serializable
 *         objects to the server it has connected to. Can receive messages from
 *         the server. This client will process messages in a single thread
 *         separate from the thread that has instantiated it.
 *
 */

public class AsynchronousClient {

	public int checksum = 0;
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
	private ThreadPoolExecutor queueReaderPool;
	private ExecutorService queueReader;
	private ClientMessageHandler messageHandler;
	private String ownAddress;
	private ExecutorService executor;
	private ExecutorService executorPool;
	private ScheduledExecutorService beaterPool;
	private ExecutorService beater;
	private int executorthreads_N = 1;
	private Semaphore clientLock;
	private MessageInfoPair lastReadMessage;
	private ClientConnectionHandler connectionHandler;
	private long lastTimestamp;
	private long initialDelay = 0;
	public int heartcount = 0;
	public int serverPort;
	private int clientport;
	private long connectionDelay = 15000;
	boolean connectedFlag = false;
	private Semaphore notifySem;

	/**
	 * How often a heartbeat message is sent in milliseconds.
	 */
	public long heartBeatDelay = 3000;
	/*
	 * Size of the buffer used to send messages to the server
	 */
	public int outputbufferSize = 8192;
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
	public AsynchronousClient(String serverAddress, String ownAddress, int serverPort, ClientMessageHandler handler,
			String separator) {
		

		notifySem = new Semaphore(1);
	
		if (ownAddress != null) {

			this.ownAddress = ownAddress;
		}
		clientBAOS = new ByteArrayOutputStream();
		try {
			clientOutput = new ObjectOutputStream(clientBAOS);
		} catch (IOException e) {
			e.printStackTrace();
		}

		this.serverPort = serverPort;
		messageHandler = handler;
		this.port = serverPort;
		this.address = serverAddress;
		outputbuffer = ByteBuffer.allocate(outputbufferSize);

		messageQueue = new ArrayBlockingQueue<MessageInfoPair>(100);
		@SuppressWarnings("unused")
		MessageInfoPair lastReadMessage;
		executorPool = Executors.newFixedThreadPool(1);
		executor = MoreExecutors.getExitingExecutorService((ThreadPoolExecutor) executorPool, 100,
				TimeUnit.MILLISECONDS);
		beaterPool = Executors.newScheduledThreadPool(executorthreads_N);
		beater = MoreExecutors.getExitingExecutorService((ThreadPoolExecutor) beaterPool, 100, TimeUnit.MILLISECONDS);

		clientLock = new Semaphore(1);
		lastTimestamp = System.currentTimeMillis();

	}

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

		stopHeartBeat();
		serverSocket.close();
		messageHandler.onDisconnect();
		connectedFlag = false;
	}

	/**
	 * Method used to send messages to the server. WARNING : Multiple messages sent
	 * in succession may not be received by the server.
	 * 
	 * @param message : Any Object that implements Serializable.
	 * @throws IOException
	 */
	public void send(Serializable message) throws IOException {

		byte[] serializedMessage;
		CommonInternalMessage outMessage = new CommonInternalMessage(message, calculateTimestamp());

		clientBAOS = new ByteArrayOutputStream();
		clientOutput = new ObjectOutputStream(clientBAOS);
		clientOutput.writeObject(outMessage);

		clientOutput.flush();
		serializedMessage = clientBAOS.toByteArray();
		clientBAOS.close();
		clientOutput.flush();
		clientOutput.close();

		executor.execute(() -> {
			try {
				sendRoutine(serializedMessage);
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

	}

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
			}
		}

		return addresses;
	}

	private void openOnRandom() {

		try {

			client = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(ownAddress, 0));
			clientport = ((InetSocketAddress) client.getLocalAddress()).getPort();

		} catch (IOException e) {

			e.printStackTrace();

		}
	}

	/**
	 * Opens an AsynchronousServerSocketChannel and binds it to the client port
	 * before connecting to the server so that the server may establish a connection
	 * to the client right after the client connects to it.
	 */
	private void startConnection() {

		openOnRandom();

		ServerInfo serverinfo = new ServerInfo();
		connectionHandler = new ClientConnectionHandler(this, messageHandler);
		serverinfo.client = client;
		client.accept(serverinfo, connectionHandler);

	}

	private void connectionTimeout() {

		try {
			Thread.sleep(connectionDelay);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		if (!connectedFlag) {

			try {

				disconnect();
			} catch (NotConnectedYetException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

		}

	}

	/**
	 * Conects the client to the server.
	 * 
	 * @throws IOException
	 */

	private void connect() throws IOException {

		try {
			// new Thread(() -> connectionTimeout()).start();
			serverSocket = SocketChannel.open(new InetSocketAddress(address, port));

			sendAddressInfo();
			startHeartBeat();

			messageHandler.onConnect();
		} catch (IOException e) {
			messageHandler.onConnectFail();
			serverSocket = null;
		}

	}

	/**
	 * Starts the reading thread that will wait onto the message queue for any
	 * message the server may have sent.
	 * 
	 * @throws IOException
	 */
	private void run() throws IOException {

		queueReaderPool = (ThreadPoolExecutor) Executors.newScheduledThreadPool(1);
		queueReader = MoreExecutors.getExitingExecutorService(queueReaderPool, 100, TimeUnit.MILLISECONDS);
		while (true) {
			Future<MessageInfoPair> resultado = queueReader.submit(() -> readfromqueue());
			try {
				CommonInternalMessage incomingMessage = (CommonInternalMessage) resultado.get().getMessage();
				// If the message is null it is considered a teartbeat from the client
				if (incomingMessage.getMessage() != null) {
					notifySem.acquire();
					messageHandler.onMessageSent(incomingMessage.getMessage(), serverInfo);

				}

			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			} finally {
				notifySem.release();
			}
		}
	}

	private void sendAddressInfo() {

		try {
			byte[] serializedMessage;
			HandShakeInternalMessage outMessage = new HandShakeInternalMessage(ownAddress, clientport);

			clientBAOS = new ByteArrayOutputStream();
			clientOutput = new ObjectOutputStream(clientBAOS);
			clientOutput.writeObject(outMessage);
			clientOutput.flush();
			clientOutput.close();
			serializedMessage = clientBAOS.toByteArray();
			clientBAOS.flush();
			clientBAOS.close();
			sendRoutine(serializedMessage);

		} catch (Exception e) {
			e.printStackTrace();
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
			clientLock.acquire();

			heartBAOS = new ByteArrayOutputStream();
			heartOutput = new ObjectOutputStream(heartBAOS);

			heartOutput.writeObject(heartbeat);

			serializedMessage = heartBAOS.toString();

			// serializedMessage += separator;

			byte[] sizeof = serializedMessage.getBytes();
			outputbuffer = ByteBuffer.allocate(sizeof.length + 4);
			outputbuffer.clear();
			outputbuffer.putInt(sizeof.length);
			outputbuffer.put(sizeof);
			outputbuffer.flip();
			while (outputbuffer.hasRemaining()) {
				serverSocket.write(outputbuffer);
			}
			heartBAOS.close();
			heartOutput.close();

		} catch (Exception e) {
			try {
				disconnect();
			} catch (NotConnectedYetException e1) {
				e1.printStackTrace();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			// System.out.println("Error writing heartbeat message");
			e.printStackTrace();
		} finally {
			outputbuffer.clear();
			clientLock.release();
		}

	}

	private void startHeartBeat() {

		beaterPool.scheduleAtFixedRate(() -> sendHeartbeat(), initialDelay, heartBeatDelay, TimeUnit.MILLISECONDS);

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
	private void sendRoutine(byte[] serializedMessage) throws IOException {

		try {
			clientLock.acquire();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		try {
			// serializedMessage += separator;

			
			outputbuffer = ByteBuffer.allocate(serializedMessage.length + 4);
			outputbuffer.clear();

			outputbuffer.putInt(serializedMessage.length);
			outputbuffer.put(serializedMessage);
			outputbuffer.flip();
			int written = 0;
			while (outputbuffer.hasRemaining()) {
				written += serverSocket.write(outputbuffer);
			}
			if (written != (serializedMessage.length + 4)) {
				System.out.println("Written : " + written + " out of " + (serializedMessage.length + 4));
			}
			
			outputbuffer.clear();


			Thread.sleep(100);

		} catch (Exception e) {
		} finally {

			clientLock.release();
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
				// If the message is null it is considered a heartbeat from the client
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

	}

}
