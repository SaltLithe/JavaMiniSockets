package javaMiniSockets.clientSide;

/**
 * 
 * @author Carmen G�mez Moreno
 *
 */
class NotConnectedYetException extends Exception {

	private static final long serialVersionUID = 1L;

	public NotConnectedYetException(String errorMessage) {
		super(errorMessage);
	}

}
