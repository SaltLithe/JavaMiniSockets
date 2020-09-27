package javaMiniSockets.clientSide;

/**
 * 
 * @author Carmen Gómez Moreno
 *
 */
public class ClientCouldNotConnectException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ClientCouldNotConnectException(String errorMessage) {
		super(errorMessage);
	}

}
