package javaMiniSockets.clientSide;

/**
 * 
 * @author Carmen G�mez Moreno
 *
 */
public class ClientCouldNotConnectException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ClientCouldNotConnectException(String errorMessage) {
		super(errorMessage);
	}

}
