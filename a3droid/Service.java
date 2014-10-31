package a3.a3droid;

import java.util.ArrayList;
import java.util.Random;

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusException;
import org.alljoyn.bus.BusListener;
import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.Mutable;
import org.alljoyn.bus.SessionListener;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.SessionPortListener;
import org.alljoyn.bus.SignalEmitter;
import org.alljoyn.bus.Status;
import org.alljoyn.bus.annotation.BusMethod;
import org.alljoyn.bus.annotation.BusSignal;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

/**
 * This class represents a group and can be seen as an antenna without which the channel couldn't communicate.
 * 
 * If the Service fails or move out of the transport range,
 * a channel becomes aware of it only when it tries to send a message.
 * Then it tries to reconnect to the group:
 * groupName is found on the bus, but the Service is not visible anymore.
 * The tried reconnection is sufficient for the name to be published again, but results in an error:
 * the channel sets itself "in transition conditions" and creates a Service
 * telling it to ignore the discovery of other groups with the same its name.
 * At its reconnection time, the channel finds the Service and it is "in transition conditions" no more.
 * 
 * The Service name is always discovered,
 * because discovery starts after the publication of the name on the bus:
 * that's why the first discovered "duplicated" name is always ignored.
 * The names of duplicated groups are discovered if such groups actually exist,
 * but even if names are published on the bus without the Services being visible:
 * that's why also the second "duplicated" name is ignored when "in transition conditions".
 * The discovery of further "duplicated" names implies the existence of duplicated groups.
 * 
 * If the Service changes, but a channel doesn't send any message, the channel isn't aware of it.
 * When the channel sends its message, the Service receives it, but it can't reply back.
 * This situation is avoided on the Service by checking if the channel is still in the list of the group members:
 * if the channel isn't in the list (called "view"), then its message isn't processed,
 * the method used to send the message returns false and the channels reconnects to the group.
 * 
 * The Service doesn't get soon aware if some channels exit from its transport range
 * and the view doesn't change rapidly.
 * The fact just described is exploited to solve this problem:
 * every time a message is received from a non-in-view channel, a view update is triggered.
 * The channels which answer within a timeout will be part of the new view.
 * @author Francesco
 *
 */
public class Service extends HandlerThread implements BusObject, A3ServiceInterface, UserInterface, TimerInterface{

	private static final int SEND_TO_SUPERVISOR = 0;
	private static final int SEND_BROADCAST = 1;
	private static final int SEND_MULTICAST = 2;
	private static final int SEND_UNICAST = 3;

	/**The connection to the AllJoyn bus.*/
	private BusAttachment mBus;

	/**A3ServiceInterface has signals, so I need this field to transmit them. */
	protected SignalEmitter emitter;

	/**The interface through which to interact with the channels.*/
	protected A3ServiceInterface txInterface;

	/**The name published on the bus, which is the group name.*/
	private String groupName;

	/**The node this Service belongs to.*/
	private A3Node node;
	/**The list of the group members and all the methods to manage it.*/
	private View view;

	/**The address of the supervisor channel.*/
	private String supervisorId;

	/**The transmitter used to send unicast messages to the channels of the group.*/
	private A3UnicastTransmitter groupTransmitter;

	/**For each kind of message, the list of the channel which are interested in receiving it.*/
	private Subscriptions subscriptions;

	/**The object that collects the integer fitness function values for supervisor election.*/
	private FitnessFunctionManager fitnessFunctionManager;

	private Handler mHandler;
	private boolean isNotMerging;

	/**
	 * @param groupName The name published on the bus, which is the group name.
	 * @param a3Channel The channel this Service belongs to.
	 * @param inTransitionConditions If duplicated groups must be ignored or not (see class comments).
	 */
	public Service(String groupName, A3Node a3Channel, boolean inTransitionConditions) {
		// TODO Auto-generated constructor stub

		super("Service_" + groupName);
		this.groupName = groupName;
		node = a3Channel;
		view = new View(this);
		supervisorId = "";
		groupTransmitter = new A3UnicastTransmitter(groupName);
		subscriptions = new Subscriptions(this);
		fitnessFunctionManager = new FitnessFunctionManager(this);
		isNotMerging = true;
		sendToOtherGroup(new A3Message(Constants.NEW_GROUP, getGroupName()), "wait");
		start();
	}

	/**It is used to publish the group name on the bus.*/
	public void connect(){

		mBus = new BusAttachment(getClass().getPackage().getName(), BusAttachment.RemoteMessage.Receive);
		mBus.registerBusListener(new BusListener());

		Status status = mBus.registerBusObject(this, "/SimpleService");
		if (status != Status.OK) {
			return;
		}

		status = mBus.connect();
		if (status != Status.OK) {
			return;
		}

		Mutable.ShortValue contactPort = new Mutable.ShortValue(Constants.CONTACT_PORT);
		SessionOpts sessionOpts = new SessionOpts();
		sessionOpts.traffic = SessionOpts.TRAFFIC_MESSAGES;
		sessionOpts.isMultipoint = true;
		sessionOpts.proximity = SessionOpts.PROXIMITY_ANY;

		sessionOpts.transports = SessionOpts.TRANSPORT_ANY + SessionOpts.TRANSPORT_WFD;

		status = mBus.bindSessionPort(contactPort, sessionOpts, new SessionPortListener() {

			@Override
			public boolean acceptSessionJoiner(short sessionPort, String joiner, SessionOpts sessionOpts) {
				if (sessionPort == Constants.CONTACT_PORT) {
					return true;
				} else {
					return false;
				}
			}

			public void sessionJoined(short sessionPort, int id, String joiner) {
				
				emitter = new SignalEmitter(Service.this, id, SignalEmitter.GlobalBroadcast.Off);
				mBus.setSessionListener(id, new SessionListener() {

					@Override
					public void sessionLost(int sessionId, int reason) {

						Message msg = view.obtainMessage();
						msg.arg2 = Constants.SESSION_LOST;
						view.sendMessage(msg);
					}

					@Override
					public void sessionMemberAdded(int sessionId, String uniqueName){

						Message msg = view.obtainMessage();
						msg.obj = uniqueName;
						msg.arg2 = Constants.MEMBER_ADDED;
						view.sendMessage(msg);
					}

					@Override
					public void sessionMemberRemoved(int sessionId, String uniqueName){

						Message msg = view.obtainMessage();
						msg.obj = uniqueName;
						msg.arg2 = Constants.MEMBER_REMOVED;
						view.sendMessage(msg);
					}
				});
				txInterface = emitter.getInterface(A3ServiceInterface.class);
			}
		});

		if (status != Status.OK) {
			return;
		}

		int flag = BusAttachment.ALLJOYN_REQUESTNAME_FLAG_REPLACE_EXISTING | BusAttachment.ALLJOYN_REQUESTNAME_FLAG_DO_NOT_QUEUE;

		status = mBus.requestName(groupName, flag);
		if (status == Status.OK) {
			status = mBus.advertiseName(groupName, sessionOpts.transports);
			if (status != Status.OK) {
				status = mBus.releaseName(groupName);
				return;
			}
			else
				showOnScreen("Group " + getGroupName() + " created.");
		}
	}

	/**It is used to unpublish the name from the bus.*/
	public void disconnect(){

		try{
			mBus.disconnect();
			showOnScreen("Group " + getGroupName() + " destroyed.");
		}
		catch (Exception e){}
	}

	public Message obtainMessage() {
		// TODO Auto-generated method stub
		return mHandler.obtainMessage();
	}

	public void sendMessage(Message msg) {
		// TODO Auto-generated method stub
		mHandler.sendMessage(msg);
	}

	@Override
	protected void onLooperPrepared() {
		super.onLooperPrepared();

		mHandler = new Handler(getLooper()) {
			/**This method handles the reception of messages from/to the supervisor.
			 * This is done in another thread, in order not to block the bus.
			 */
			@Override
			public void handleMessage(Message msg) {

				A3Message object = (A3Message) msg.obj;

				switch(msg.arg2){

				//A node called the sendToSupervisor(A3Message) method.
				case SEND_TO_SUPERVISOR:

					switch(object.reason){

					case Constants.SUBSCRIPTION:
					case Constants.UNSUBSCRIPTION:
						subscriptions.onMessage(object);
						break;

						/* If I receive them here, they are sent by parent groups to the supervisor,
						 * but I need to transmit them broadcast.
						 */
					case Constants.MERGE:
						isNotMerging = false;
						sendToOtherGroup(new A3Message(Constants.WAIT_MERGE,
								object.object + Constants.A3_SEPARATOR + getGroupName()), "wait");
					case Constants.ADD_TO_HIERARCHY:
					case Constants.REMOVE_FROM_HIERARCHY:		
					case Constants.WAIT_SUPERVISOR_FITNESS_FUNCTION_REQUEST:
					case Constants.WAIT_NEW_SUPERVISOR:
					case Constants.WAIT_MERGE:
						handleBroadcastMessage(object);
						break;

					case Constants.SUPERVISOR_FITNESS_FUNCTION_REQUEST:
						
						/* If a supervisor election is ongoing, then this message is accepted.
						 * Else
						 * 
						 */
						if(!fitnessFunctionManager.onMessage(object)){
							if(supervisorId.equals(""))
								setSupervisorId(object.senderAddress);
							else
								handleUnicastMessage(new A3Message(Constants.NEW_SUPERVISOR, supervisorId), object.senderAddress);
						}
						break;

					case Constants.SUPERVISOR_FITNESS_FUNCTION_REPLY:
						
						fitnessFunctionManager.onMessage(object);
						break;
						
					case Constants.SPLIT:
						//Random split operation.

						A3Message newGroupMessage = new A3Message(Constants.NEW_SPLITTED_GROUP, "");
						handleBroadcastMessage(newGroupMessage);

						int nodesToTransfer = Integer.valueOf(object.object);
						ArrayList<String> selectedNodes = new ArrayList<String>();
						int numberOfNodes = view.getNumberOfNodes();
						String[] splittedView = view.getView().substring(1, view.getView().length()-1).split(", ");
						Random randomNumberGenerator = new Random();
						String tempAddress;

						/* I can't move the supervisor in another group,
						 * so the supervisor never sends its integer fitness function value.
						 * I can't move more nodes than I have.
						 */
						if(nodesToTransfer < numberOfNodes){

							for(int i = 0; i < nodesToTransfer; i++){

								do{
									tempAddress = splittedView[randomNumberGenerator.nextInt(numberOfNodes)];
								}while(tempAddress.equals(supervisorId) || selectedNodes.contains(tempAddress));

								selectedNodes.add(tempAddress);
							}

							for(String address : selectedNodes)
								handleUnicastMessage(new A3Message(Constants.SPLIT, ""), address);
						}
						break;

					case Constants.WAIT_SUPERVISOR_FITNESS_FUNCTION:

						/* "senderAddress Constants.WAIT_SUPERVISOR_FITNESS_FUNCTION otherGroupName integerValue".
						 * 
						 * If I receive this message, I'm the Service of group "wait".
						 * A waiting node is communicating its supervisor fitness function value
						 * because it is participating to a supervisor election in group "otherGroupName".
						 * I must send this message to the group "otherGroupName".
						 */
						String[] splittedObject = ((String)object.object).split(Constants.A3_SEPARATOR);
						sendToOtherGroup(object, splittedObject[0]);
						break;

					case Constants.NEW_SUPERVISOR:

						/* This message is sent by a node which was in "wait" group.
						 * The supervisor election procedure identified a "wait" channel as supervisor,
						 * because the sender was disconnected.
						 * Now that the sender is connected,
						 * its address is different from the one of the one of the "wait" channel,
						 * so it is stored as the address of the new supervisor.
						 */
						supervisorId = object.senderAddress;
						break;

					case Constants.SUPERVISOR_ELECTION:
						supervisorElection();
						break;
						
					default:
						try {
							if (txInterface != null) {
								txInterface.SupervisorReceive(object);
							}
						} catch (Exception e) {}
						break;
					}
					break;

					//A node called the sendBroadcast(A3Message) method.
				case SEND_BROADCAST:

					int reason = object.reason;
					
					if(reason == Constants.BOOLEAN_SPLIT_FITNESS_FUNCTION || reason == Constants.INTEGER_SPLIT_FITNESS_FUNCTION){
						A3Message newGroupMessage = new A3Message(Constants.NEW_SPLITTED_GROUP, "");
						handleBroadcastMessage(newGroupMessage);
					}
					
					try{

						handleBroadcastMessage(object);

					} catch (Exception e) {}

					break;

					//A node called the sendMulticast(A3Message) method.
				case SEND_MULTICAST:

					ArrayList<String> destinations = subscriptions.getSubscriptions(object.reason);

					for (int i = 0; i < destinations.size(); i ++)
						handleUnicastMessage(object, destinations.get(i));

					break;

					//A node called the sendUnicast(A3Message) method.
				case SEND_UNICAST:

					/*In sendUnicast(A3Message, String) I changed the sender address with the destination address,
					 * in order to pass it correctly to this thread without adding further logic.*/
					handleUnicastMessage(object, object.senderAddress);
					break;
				}
			}
		};
	}

	@Override
	@BusMethod(signature = "(sis)", replySignature = "b")
	public boolean sendToSupervisor(A3Message message) {
		// TODO Auto-generated method stub

		// I filter here the messages exchanged between each group and the "wait" group.
		String address = message.senderAddress;
		boolean isInView = view.isInView(address);

		if(getGroupName().equals("wait")){
			switch(message.reason){
			case Constants.WAIT_SUPERVISOR_FITNESS_FUNCTION:
				if(isInView){

					Message msg = obtainMessage();
					msg.obj = message;
					msg.arg2 = SEND_TO_SUPERVISOR;
					sendMessage(msg);
					}
				break;
				
			case Constants.WAIT_SUPERVISOR_FITNESS_FUNCTION_REQUEST:
			case Constants.WAIT_NEW_SUPERVISOR:
			case Constants.WAIT_MERGE:
				isInView = true;
				Message msg = obtainMessage();
				msg.obj = message;
				msg.arg2 = SEND_TO_SUPERVISOR;
				sendMessage(msg);
				break;
				
			default:
				if(isInView){
					msg = obtainMessage();
					msg.obj = message;
					msg.arg2 = SEND_TO_SUPERVISOR;
					sendMessage(msg);
				}
				break;
			}
		}
		
		else{
			switch(message.reason){
			case Constants.WAIT_SUPERVISOR_FITNESS_FUNCTION:
				String[] splittedObject = ((String)message.object).split(Constants.A3_SEPARATOR);
				A3Message reply = new A3Message(Constants.SUPERVISOR_FITNESS_FUNCTION_REPLY, splittedObject[1]);
				reply.senderAddress = message.senderAddress;
				
				isInView = true;
				Message msg = obtainMessage();
				msg.obj = reply;
				msg.arg2 = SEND_TO_SUPERVISOR;
				sendMessage(msg);
				break;
				
			case Constants.WAIT_SUPERVISOR_FITNESS_FUNCTION_REQUEST:
			case Constants.WAIT_NEW_SUPERVISOR:
				isInView = true;
				msg = obtainMessage();
				msg.obj = message;
				msg.arg2 = SEND_TO_SUPERVISOR;
				sendMessage(msg);
				break;
				
			default:
				if(isInView){
					msg = obtainMessage();
					msg.obj = message;
					msg.arg2 = SEND_TO_SUPERVISOR;
					sendMessage(msg);
				}
				break;
			}
		}
		
		return isInView;
	}

	@Override
	@BusMethod(signature = "(sis)", replySignature = "b")
	public boolean sendBroadcast(A3Message message) {
		// TODO Auto-generated method stub

		boolean isSupervisor;

		synchronized(supervisorId){
			isSupervisor = message.senderAddress.equals(supervisorId);
		}

		if(isSupervisor){

			Message msg = obtainMessage();
			msg.obj = message;
			msg.arg2 = SEND_BROADCAST;
			sendMessage(msg);
		}
		return isSupervisor;
	}

	@Override
	@BusMethod(signature = "(sis)", replySignature = "b")
	public boolean sendMulticast(A3Message message) {
		// TODO Auto-generated method stub

		boolean isSupervisor;

		synchronized(supervisorId){
			isSupervisor = message.senderAddress.equals(supervisorId);
		}

		if(isSupervisor){

			Message msg = obtainMessage();
			msg.obj = message;
			msg.arg2 = SEND_MULTICAST;
			sendMessage(msg);
		}
		return isSupervisor;
	}

	@Override
	@BusMethod(signature = "(sis)s", replySignature = "b")
	public boolean sendUnicast(A3Message message, String receiverAddress) {
		// TODO Auto-generated method stub

		boolean isSupervisor = false;

		synchronized(supervisorId){
			isSupervisor = message.senderAddress.equals(supervisorId);
		}

		if(isSupervisor){
			/*Only in this way I can correctly pass the receiver address to the other thread
			without adding further logic.*/
			message.senderAddress = receiverAddress;
			Message msg = obtainMessage();
			msg.obj = message;
			msg.arg2 = SEND_UNICAST;
			sendMessage(msg);
		}
		return isSupervisor;
	}

	@Override
	@BusSignal(signature = "(sis)")
	public void ReceiveBroadcast(A3Message message) throws BusException {
		// TODO Auto-generated method stub

	}

	@Override
	@BusSignal(signature = "(sis)")
	public void SupervisorReceive(A3Message message) throws BusException {
		// TODO Auto-generated method stub

	}

	/**
	 * Effectively sends a message to all the group members.
	 * @param messageToBroadcast The message to be sent.
	 */
	public void handleBroadcastMessage(A3Message messageToBroadcast) {
		// TODO Auto-generated method stub

		/* The receiver knows that it received the message from the supervisor,
		 * then the sender address is not useful and I don't send it.
		 */
		try {
			if (txInterface != null) {
				txInterface.ReceiveBroadcast(messageToBroadcast);
			}
		} catch (Exception e) {}
	}

	/**
	 * Effectively sends a unicast message to its destination.
	 * @param message The message to send.
	 * @param receiverAddress The address of the destination of the message.
	 */
	public void handleUnicastMessage(A3Message message, String receiverAddress) {

		try{
			if(view.isInView(receiverAddress)){
				boolean ok = groupTransmitter.sendUnicast(message, groupName + "._" + receiverAddress.hashCode(), false);

				if(!ok){
					subscriptions.cancelSubscriptions(receiverAddress);
					view.removeGroupMember(receiverAddress);
				}
			}
		} catch (Exception e) {}
	}

	private void sendToOtherGroup(A3Message message, String groupName) {
		try{
			groupTransmitter.sendUnicast(message, Constants.PREFIX + groupName, true);
		} catch (Exception e) {}

	}

	/**It starts a new supervisor election, asking for integer fitness function values.*/
	public void supervisorElection(){

		if(isNotMerging){
			fitnessFunctionManager.startCollectingFitnessFunctions(Constants.SUPERVISOR_FITNESS_FUNCTION_REQUEST);
			A3Message message = new A3Message(Constants.SUPERVISOR_FITNESS_FUNCTION_REQUEST, "");
			handleBroadcastMessage(message);
			sendToOtherGroup(new A3Message(Constants.WAIT_SUPERVISOR_FITNESS_FUNCTION_REQUEST, getGroupName()), "wait");
		}
	}

	/**
	 * It is called when the supervisor has changed.
	 * This method sets the supervisor id to the address of the new supervisor channel, if it is known.
	 * In this case, this method notifies the new supervisor with the message "Constants.NEW_SUPERVISOR".
	 * 
	 * @param supervisorId The address of the new supervisor channel, if it is known,
	 * or "" if a supervisor election must start because of the leaving of the old supervisor.
	 */
	public synchronized void setSupervisorId(String supervisorId) {
		this.supervisorId = supervisorId;

		if(!supervisorId.equals("?")){
			A3Message message = new A3Message(Constants.NEW_SUPERVISOR, supervisorId);
			handleBroadcastMessage(message);
			sendToOtherGroup(new A3Message(Constants.WAIT_NEW_SUPERVISOR,
					getGroupName() + Constants.A3_SEPARATOR + supervisorId), "wait");
		}
	}

	public synchronized String getSupervisorId() {
		return supervisorId;
	}

	@Override
	public void timerFired(int reason) {
		// TODO Auto-generated method stub

		// I can only have a supervisor election, so I don't check the value of reason.
		try {
			setSupervisorId(fitnessFunctionManager.getBest(1)[0]);
		} catch (Exception e) {
			// No result is arrived: no one can be the supervisor, so I disconnect.
			disconnect();
		}
	}

	@Override
	public void showOnScreen(String string) {
		// TODO Auto-generated method stub
		node.showOnScreen("(Service " + groupName + "): " + string);
	}

	public String getGroupName() {
		// TODO Auto-generated method stub
		return groupName.replaceFirst(Constants.PREFIX, "");
	}

	public View getView() {
		// TODO Auto-generated method stub
		return view;
	}
}
