package a3.a3droid;

import org.alljoyn.bus.BusException;
import org.alljoyn.bus.annotation.BusInterface;
import org.alljoyn.bus.annotation.BusMethod;
import org.alljoyn.bus.annotation.BusSignal;

/**The interface implemented by the Service,
 * which is used by AllJoyn to know the remote callable method.
 * @author Francesco
 *
 */
@BusInterface (name = Constants.PACKAGE_NAME + ".A3ServiceInterface")
public interface A3ServiceInterface {

	/**Used by the Service to receive messages directed to the supervisor.
	 * Such messages are sent by channels.
	 * @param msg The message to be sent.
	 * @throws BusException AllJoyn errors.
	 */
	@BusMethod(signature = "(sis)", replySignature = "b")
	public boolean sendToSupervisor(A3Message msg) throws BusException;
	
	/**Used by the Service to receive messages directed to all the channels of the group.
	 * Such messages are sent by supervisor only.
	 * @param message The message to be sent.
	 * @return true if the transmission was successful, otherwise false.
	 * @throws BusException AllJoyn errors.
	 */
	@BusMethod(signature = "(sis)", replySignature = "b")
	public boolean sendBroadcast(A3Message message) throws BusException;
	
	/**Used by the Service to receive messages directed to the channels of the group
	 * which are subscribed to receive it.
	 * Such messages are sent by supervisor only.
	 * @param message The message to be sent.
	 * @return true if the transmission was successful, otherwise false.
	 * @throws BusException AllJoyn errors.
	 */
	@BusMethod(signature = "(sis)", replySignature = "b")
	public boolean sendMulticast(A3Message message) throws BusException;
	
	/**Used by the Service to receive messages directed to the specified channel of the group.
	 * Such messages are sent by supervisor only.
	 * @param message The message to be sent.
	 * @return true if the transmission was successful, otherwise false.
	 * @throws BusException AllJoyn errors.
	 */
	@BusMethod(signature = "(sis)s", replySignature = "b")
	public boolean sendUnicast(A3Message message, String address) throws BusException;
	
	/**Used by the Service to send messages to all the channels of the group.
	 * @param message The message to be sent.
	 * @throws BusException AllJoyn errors.
	 */
	@BusSignal(signature = "(sis)")
	public void ReceiveBroadcast(A3Message message) throws BusException;
	
	/**Used by the Service to send messages to the supervisor.
	 * Such messages are sent by channels.
	 * @param msg The message to be sent.
	 * @throws BusException AllJoyn errors.
	 */
	@BusSignal(signature = "(sis)")
	public void SupervisorReceive(A3Message message) throws BusException;
}
