package javaMiniSockets.serverSide;

/**
 * 
 * @author Carmen G�mez Moreno
 *
 */
class MaximumConnectionsReachedException extends Exception {

	private static final long serialVersionUID = 1L;

	public MaximumConnectionsReachedException(String errorMessage) {
		super(errorMessage);
	}

}
