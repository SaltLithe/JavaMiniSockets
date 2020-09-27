package javaMiniSockets.serverSide;

/**
 * 
 * @author Carmen G�mez Moreno
 *
 */
public class ServerCouldNotConnectException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ServerCouldNotConnectException(String errorMessage) {
		super(errorMessage);
	}

}
