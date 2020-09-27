package javaMiniSockets.serverSide;

/**
 * 
 * @author Carmen Gómez Moreno
 *
 */
public class ServerCouldNotConnectException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ServerCouldNotConnectException(String errorMessage) {
		super(errorMessage);
	}

}
