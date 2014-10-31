package a3.a3droid;

import org.alljoyn.bus.annotation.BusInterface;
import org.alljoyn.bus.annotation.BusMethod;

@BusInterface (name = Constants.PACKAGE_NAME + ".A3UnicastInterface")
public interface A3UnicastInterface {

	/**
	 * Called by the transmitter of the Service to send the unicast message.
	 * @param message The message to be sent by the transmitter of the service.
	 */
	@BusMethod(signature = "(sis)")
	public void receiveUnicast(A3Message message);
}
