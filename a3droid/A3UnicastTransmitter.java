package a3.a3droid;

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusListener;
import org.alljoyn.bus.Mutable;
import org.alljoyn.bus.ProxyBusObject;
import org.alljoyn.bus.SessionListener;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.Status;

/**This class is designed to stay on a Service,
 * in order to send unicast messages to every channel of the group.
 * It is the only transmitter on the Service,
 * so it connects and disconnects to the channels' UnicastReceivers as needed.
 * It is a kind of simplified channel,
 * but it is missing group discovery and reconnection in case of missing transmission.
 * In order to connect to a specific client in a multipoint session,
 * AllJoyn requires that every client is uniquely identified by a well-known-name:
 * such name is obtained by adding an hash of the channel address to the name of the group.
 * Service discovery is not needed here because,
 * if the channel is in the view, the corresponding group surely exists.
 * @author Francesco
 *
 */
public class A3UnicastTransmitter extends Thread{

	/**The name of the message receiver.*/
	private String name;

	/**The connection to the AllJoyn bus.*/
	private BusAttachment mBus;

	/**The object representing the UnicastReceiver of the message destination.*/
	private ProxyBusObject proxyObject;

	/**The interface of "proxyBusObject". Used to send the messages.*/
	private A3UnicastInterface unicastInterface;

	/**The identifier of the AllJoyn session that is created between this transmitter and the receiver.*/
	private int mSessionId;

	/**It indicates if this transmitter is actually connected or not.*/
	private boolean connected;

	private A3ServiceInterface toOtherGroupInterface;

	private boolean toOtherGroup;

	/**
	 * @param groupName The name of the group which nodes the transmitter must send the messages.
	 * @param a3supervisor The Service this transmitter belongs to.
	 */
	public A3UnicastTransmitter(String groupName){
		name = "";
		unicastInterface = null;
		toOtherGroupInterface = null;
		start();
	}

	/**
	 * It sends the unicast messages.
	 * @param message The message to be sent.
	 * @param address The address of the message destination.
	 * @return true, if the transmission was successful, false otherwise.
	 */
	public boolean sendUnicast(A3Message message, String address, boolean toOtherGroup){
		
		this.toOtherGroup = toOtherGroup;
		boolean transmissionOk = false;

		/* The receiver knows that it received the message from the supervisor,
		 * then the sender address is not useful and I don't send it, so gaining band.
		 */
		try {
			//If the transmitter is already connected to the right receiver, I avoid to reconnect it.
			if(!name.equals(address)){
				disconnect();
				name = address;
				connect();
			}

			if(toOtherGroup){
				if (connected && toOtherGroupInterface != null){
					toOtherGroupInterface.sendToSupervisor(message);
					transmissionOk = true;
				}
			}
			
			else{
				if (connected && unicastInterface != null){
					unicastInterface.receiveUnicast(message);
					transmissionOk = true;
				}
			}
		
		} catch (Exception ex) {}
		return transmissionOk;
	}

	/**It is used in order to create the AllJoyn session with the receiver.*/
	public void connect(){

		mBus = new BusAttachment(getClass().getPackage().getName(), BusAttachment.RemoteMessage.Receive);

		mBus.registerBusListener(new BusListener() {

			@Override
			public void foundAdvertisedName(String name, short transport, String namePrefix) {}
		});

		Status status = mBus.connect();
		if (Status.OK != status)
			return;

		status = mBus.registerSignalHandlers(this);
		if (status != Status.OK)
			return;

		short contactPort = Constants.CONTACT_PORT;
		SessionOpts sessionOpts = new SessionOpts();
		sessionOpts.transports = SessionOpts.TRANSPORT_ANY + SessionOpts.TRANSPORT_WFD;
		Mutable.IntegerValue sessionId = new Mutable.IntegerValue();

		status = mBus.joinSession(name, contactPort, sessionId, sessionOpts, new SessionListener() {

			@Override
			public void sessionLost(int sessionId, int reason) {}
		});

		if (status == Status.OK) {

			if(toOtherGroup){
				proxyObject =  mBus.getProxyBusObject(name, "/SimpleService", sessionId.value,
						new Class<?>[] { A3ServiceInterface.class });

				toOtherGroupInterface =  proxyObject.getInterface(A3ServiceInterface.class);
			}
			
			else{
				proxyObject =  mBus.getProxyBusObject(name, "/SimpleService", sessionId.value,
						new Class<?>[] { A3UnicastInterface.class });
	
				unicastInterface =  proxyObject.getInterface(A3UnicastInterface.class);
			}
			
			mSessionId = sessionId.value;
			connected = true;
		}

		else
			connected = false;
	}

	/**It is use to leave the AllJoyn session with the receiver.*/
	public void disconnect(){

		try{
			if(toOtherGroup)
				toOtherGroupInterface = null;
			else
				unicastInterface = null;
			
			if (connected) {
				mBus.leaveSession(mSessionId);
				connected = false;

				mBus.disconnect();
			}
		}
		catch (Exception ex) {}
	}

	public String getChannelName() {
		return name;
	}
}
