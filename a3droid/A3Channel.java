package a3.a3droid;

import java.util.ArrayList;
import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusListener;
import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.Mutable;
import org.alljoyn.bus.ProxyBusObject;
import org.alljoyn.bus.SessionListener;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.Status;
import org.alljoyn.bus.annotation.BusSignalHandler;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

/**
 * This class is the channel that lets the nodes communicate with each other.
 * It has the methods to receive broadcast messages and to send messages to the supervisor of the group.
 * 
 * When a node needs to join a group, it creates a channel.
 * The channel start the discovery of the group and waits 2 seconds:
 * if the group was found, then the channel connects to it,
 * otherwise the channel creates the group and connects to it.
 * If the group name is found, but the Service is not visible,
 * the channels becomes creates the group and connects to it.
 * 
 * Once connected, the channel must know if it is the supervisor or a follower:
 * if the channel is the supervisor, it receives communication within a 2 seconds timeout,
 * and it soon sets itself as the supervisor.
 * Otherwise the channel sets itself as a follower after the timeout.
 * @author Francesco
 *
 */
public class A3Channel extends Thread implements BusObject, TimerInterface, UserInterface{

	/**The name of the group to join.*/
	private String groupName;
	
	/**The connection to the AllJoyn bus.*/
	private BusAttachment mBus;
	
	/**The Service proxy to communicate with.*/
	private ProxyBusObject mProxyObj;
	
	/**The interface used to communicate to the proxy.*/
	private A3ServiceInterface serviceInterface;
	
	/**The id of the AllJoyn session this channel is joined.*/
	private int mSessionId;

	/**It indicates if this channel is connected or not.*/
	private boolean mIsConnected;
	
	/**The node this channel belongs to.*/
	private A3Node node;
	
	/**The thread which manages session lost and timeout firing.*/
	private CallbackThread callbackThread;
	
	/**The Service this channel eventually creates.*/
	private Service service;
	
	/**The address of this channel.*/
	private String myId;
	
	/**A timer.*/
	private Timer timer;

	/** */
	private boolean discovered;
	
	/**The thread which handles the received messages.*/
	private MessageHandler messageHandler;
	
	/**Indicates if the group name was found, but the Service wasn't visible.*/
	private boolean inTransitionConditions;
	
	/**Indicates if this channel is currently the group supervisor or not.*/
	private boolean isSupervisor;
	
	/**The receiver used to receive unicast messages from the Service.*/
	private A3UnicastReceiver unicastReceiver;
	
	/**The logic that is executed when this channel is a follower.*/
	private A3FollowerRole followerRole;

	/**The logic that is executed when this channel is the supervisor.*/
	private A3SupervisorRole supervisorRole;
	
	/**The role that is currently active on this channel.*/
	private A3Role activeRole;
	
	/**The list of message kinds this channel is interested in.*/
	private Subscriptions subscriptions;
	
	/**The list of the messages waiting to be sent to the supervisor.*/
	private MessageQueue queue;

	/**It indicates if the channel must reconnect or not.*/
	private boolean reconnect;

	/**The list of the parent groups.*/
	private Hierarchy hierarchy;
	
	/**The thread which passes the incoming messages to the active role.*/
	private InputQueueHandler inputQueueHandler;
	
	/**The list of the incoming message.*/
	private MessageQueue inputQueue;

	/**true if this channel can only act as a follower, false otherwise.*/
	private boolean followerOnly;

	/**true if this channel can only act as a supervisor, false otherwise.*/
	private boolean supervisorOnly;
	
	/**The user interface to interact to.*/
	protected UserInterface ui;

	/**true if this channel is used by the application, false otherwise.*/
	private boolean connectedForApplication;

	/**true if this channel is used by the system, false otherwise.*/
	private boolean connectedForSystem;

	private boolean firstConnection;
	
	/**The descriptor of the group this channel is connected to.*/
	private GroupDescriptor groupDescriptor;
	
	/**
	 * @param a3node The node this channel belongs to.
	 * @param userInterface The user interface to interact to.
	 * @param groupDescriptor The descriptor of the group to connect this channel to.
	 */
	public A3Channel (A3Node a3node, UserInterface userInterface, GroupDescriptor groupDescriptor){

		super();
		start();
		mIsConnected = false;
		node = a3node;
		serviceInterface = null;
		service = null;
		myId = null;
		discovered = false;
		inTransitionConditions = false;
		isSupervisor = false;
		subscriptions = new Subscriptions(this);
		hierarchy = new Hierarchy(this);
		queue = new MessageQueue();
		inputQueue = new MessageQueue();
		ui = userInterface;
		connectedForApplication = false;
		connectedForSystem = false;
		firstConnection = true;
		this.groupDescriptor = groupDescriptor;
		
		/* Thread that reads the first message in the queue and try to send it to the Service.
		 * If the transmission fails, the channel reconnects and the message is still available in the queue,
		 * otherwise such message is removed from the queue.*/
		new Thread(){
			public void run(){
				A3Message message;
				while(true){
					try{
						message = queue.get();
						if(send(message))
							queue.dequeue();
						else if(reconnect)
							reconnect();
					} catch (Exception e) {}
				}
			}
		}.start();
		
		callbackThread = new CallbackThread();
		messageHandler = new MessageHandler();
		inputQueueHandler = new InputQueueHandler();
		inputQueueHandler.start();
	}

	/**
	 * Called by an A3Node when connecting the channel.
	 * Used to connect to the AllJoyn bus and to start group discovery.
	 * It also sets the roles of this channel.
	 * @param groupName The name of the group to which to connect this channel.
	 * @param a3FollowerRole The logic that is executed when this channel is a follower.
	 * @param a3SupervisorRole The logic that is executed when this channel is the supervisor.
	 * @param supervisorOnly 
	 * @param followerOnly 
	 */
	public void connect(String groupName, A3FollowerRole a3FollowerRole,
			A3SupervisorRole a3SupervisorRole, boolean followerOnly, boolean supervisorOnly) {
		// TODO Auto-generated method stub
		
			this.groupName = Constants.PREFIX + groupName;
			followerRole = a3FollowerRole;
			supervisorRole = a3SupervisorRole;
			this.followerOnly = followerOnly;
			this.supervisorOnly = supervisorOnly;
			setName(groupName);
			connect(groupName);
		
	}

	/**Used to connect to the AllJoyn bus and to start group discovery.*/
	public void connect(String group_name){

		groupName = Constants.PREFIX + group_name;
		
		showOnScreen("Starting ...");
		mBus = new BusAttachment(getClass().getPackage().getName(), BusAttachment.RemoteMessage.Receive);

		mBus.registerBusListener(new BusListener() {

			@Override
			public void foundAdvertisedName(String name, short transport, String namePrefix) {

				/* "name" can be a prefix of another group name: in this case, I must do nothing.
				 * If the group is duplicated, starting the merging procedure takes time
				 * and blocks the callbacks, so I manage this case in another thread.
				 */
				if(name.equals(groupName)){
					discovered = true;
				}
			}

			public void lostAdvertisedName(String name, short transport, String namePrefix){}
		});

		Status status = mBus.connect();
		if (Status.OK != status)
			return;

		status = mBus.registerSignalHandlers(this);
		if (status != Status.OK)
			return;

		// The discovery and the timer start.
		status = mBus.findAdvertisedName(groupName);
		if (Status.OK != status)
			return;

		try{
			timer = new Timer(this,0);
			timer.start();
		} catch (Exception e){}
	}

	/**It is called when the timeout fires and the group name was discovered.
	 * It lets this channel join the AllJoyn session and connect to the group.
	 */
	public void joinSession(){
		
		try{
			short contactPort = Constants.CONTACT_PORT;
			SessionOpts sessionOpts = new SessionOpts();
			sessionOpts.transports = SessionOpts.TRANSPORT_ANY + SessionOpts.TRANSPORT_WFD;
			Mutable.IntegerValue sessionId = new Mutable.IntegerValue();

			Status status = mBus.joinSession(groupName, contactPort, sessionId, sessionOpts, new SessionListener() {

				@Override
				public void sessionLost(int sessionId, int reason) {

					Message msg = callbackThread.obtainMessage();
					msg.arg2 = Constants.SESSION_LOST;
					callbackThread.sendMessage(msg);
				}
			});

			if (status == Status.OK)
				onSessionJoined(sessionId);

			else{
				mIsConnected = false;

				//The group name was found, but the Service is not visible.
				if(status == Status.ALLJOYN_JOINSESSION_REPLY_UNREACHABLE){
					inTransitionConditions = true;
					createGroup();
					reconnect();

				}
				else{
					//If this channel is already joined.
					if(status == Status.ALLJOYN_JOINSESSION_REPLY_ALREADY_JOINED)
						onSessionJoined(sessionId);
				}
			}
		}catch (Exception ex) {}
	}

	/**It disconnect this channel from the group and the AllJoyn bus.*/
	public void disconnect(){
	
		/*The name of my UnicastReceiver is strictly based on my address in the group,
		 * so I must disconnect it when I disconnect.
		 */
		unicastReceiver.disconnect();
		
		/* I stop the logic of this channel, whatever it is.
		 * A supervisor only channel which discovers to be a follower at first connection
		 * doesn't have any active logic: that's why I ignore an error here.
		 */
		try{
			activeRole.setActive(false);
		}catch (Exception ex) {}
		
		if(isSupervisor){
			isSupervisor = false;
		}
	
		try{
			if (mIsConnected) {
				mBus.leaveSession(mSessionId);
				mIsConnected = false;
				firstConnection = true;
			}
		}
		catch (Exception ex) {
			showOnScreen("EXCEPTION IN A3Channel.disconnect(): " + ex.getMessage());
		}
	
		try{
			mBus.disconnect();
			reset();
			showOnScreen("Disconnected.");
		}
		catch (Exception ex) {}
		reset();
	}

	/**It resets the initial configuration of the channel, in order to reuse it next.*/
	private void reset() {
		// TODO Auto-generated method stub
	
		mIsConnected = false;
		serviceInterface = null;
		service = null;
		myId = null;
		discovered = false;
		isSupervisor = false;
	}

	/**
	 * It is used in order to reconnect this channel at the same group
	 * in case of errors, or when a duplicated group is found.
	 */
	private void reconnect() {
		// TODO Auto-generated method stub
		disconnect();
		connect(getGroupName());
	}

	/**
	 * It is called when the channel has just joined the AllJoyn session.
	 * It retreives the communication interface, connects the unicast receiver
	 * and starts the timer to wait for communication about the role this channel has in the group.
	 * 
	 * @param sessionId The id of the AllJoyn session this channel is joined.
	 */
	private void onSessionJoined(Mutable.IntegerValue sessionId) {
		
		// TODO Auto-generated method stub
		mProxyObj =  mBus.getProxyBusObject(groupName, "/SimpleService", sessionId.value,
				new Class<?>[] { A3ServiceInterface.class });

		serviceInterface =  mProxyObj.getInterface(A3ServiceInterface.class);

		mSessionId = sessionId.value;
		mIsConnected = true;
		myId = mBus.getUniqueName();

		String id = String.valueOf(myId.hashCode());

		/*The name of my UnicastReceiver is strictly based on my address in the group,
		 * so I can create and connect it only now that I know my address.
		 */
		unicastReceiver = new A3UnicastReceiver(groupName + "._" + id, this);
		unicastReceiver.connect();
		
		//I transmit my subscriptions only if I am subscribed to receive something.
		String message = subscriptions.toString();
		if(!message.equals("")){
			A3Message subscriptionsMessage = new A3Message(Constants.SUBSCRIPTION, message);
			sendToSupervisor(subscriptionsMessage);
		}
		sendToSupervisor(new A3Message(Constants.GET_HIERARCHY, ""));
		
		try {
			sendToSupervisor(new A3Message(Constants.SUPERVISOR_FITNESS_FUNCTION_REQUEST, ""));
		} catch (Exception e) {}
		unblock();
		showOnScreen("Connected.");
	}

	/**
	 * Called when this channels becomes follower.
	 * It deactivates the supervisor role (if it is active) and it activates follower role.
	 */
	private void becomeFollower() {
		// TODO Auto-generated method stub
		
		try{
			/*At my first connection time I'm neither the supervisor nor a follower:
			 * if I discover to be a follower, I don't have to deactivate the supervisor role.
			 * In other cases, this method is called only if I was the supervisor,
			 * so this "if" is always executed.
			 */
			if(isSupervisor){
				mBus.unregisterSignalHandlers(supervisorRole);
				supervisorRole.setActive(false);
			}
			
			if(supervisorOnly){
				disconnect();
				
				node.setWaiting(this);
			}
			else{
				isSupervisor = false;
				followerRole.setActive(true);
				activeRole = followerRole;
				new Thread(followerRole).start();
				
				synchronized(inputQueue){
					inputQueue.notify();
				}
				
				node.setConnected(this);
			}
		}catch(Exception e){}
	}

	/**
	 * Called when this channels becomes supervisor.
	 * It deactivates the follower role (if it is active) and it activates supervisor role.
	 */
	private void becomeSupervisor() {
		// TODO Auto-generated method stub
		
		//This method is always called when I am the supervisor, but I wasn't it before.
		isSupervisor = true;
		try{
			if(!supervisorOnly)
				followerRole.setActive(false);
		
			if(followerOnly){
				disconnect();
				
				node.setWaiting(this);
			}
			else{
				supervisorRole.setActive(true);
				activeRole = supervisorRole;
				new Thread(supervisorRole).start();
				mBus.registerSignalHandlers(supervisorRole);
				
					synchronized(inputQueue){
						inputQueue.notify();
					}
					
				node.setConnected(this);
			}
		}catch(Exception e){}
	}

	/**
	 * Called by the Service when it sends a broadcast message.
	 * It passes the received messages to another thread, in order not to block the bus.
	 * @param message The received message.
	 */
	@BusSignalHandler(iface = Constants.PACKAGE_NAME + ".A3ServiceInterface", signal = "ReceiveBroadcast")
	public void ReceiveBroadcast(A3Message message) {
	
		Message msg = messageHandler.obtainMessage();
		msg.obj = message;
		messageHandler.sendMessage(msg);
	}

	/**It is called by the thread that handles the received messages.
	 * @param message The received message.
	 */
	private void onMessage(A3Message message) throws Exception{
	
		/* If the session was lost and now I receive a message,
		 * then I'm connected and I can retrive the information about the new session.
		 */
		if(!mIsConnected){
			mIsConnected = true;
		}
	
		myId = mBus.getUniqueName();
	
		switch(message.reason){
		case Constants.NEW_SUPERVISOR:
			//The new supervisor was elected.
	
			if(message.object.equals("?")){
				message = new A3Message(Constants.SUPERVISOR_FITNESS_FUNCTION_REPLY, String.valueOf(getSupervisorFitnessFunction()));
				sendToSupervisor(message);
			}
			
			else{
				if(message.object.equals(myId)){
					if(!isSupervisor){
						becomeSupervisor();
					}
				}
				
				else{
					if(isSupervisor || firstConnection){
						firstConnection = false;
						becomeFollower();
					}
				}
			}
			break;
			
		case Constants.SUBSCRIPTION:
		case Constants.UNSUBSCRIPTION:
			subscriptions.onMessage(message);
			break;
			
		case Constants.HIERARCHY:
		case Constants.ADD_TO_HIERARCHY:
		case Constants.REMOVE_FROM_HIERARCHY:
			hierarchy.onMessage(message);
			break;
			
		case Constants.SUPERVISOR_FITNESS_FUNCTION_REQUEST:
			if(!followerOnly){
				//I send the value of my fitness function to the Service, which collects it.
				message = new A3Message(Constants.SUPERVISOR_FITNESS_FUNCTION_REPLY, String.valueOf(getSupervisorFitnessFunction()));
				sendToSupervisor(message);
			}
			break;
			
		case Constants.BOOLEAN_SPLIT_FITNESS_FUNCTION:
			//If my fitness function equals true, I transfer to the new group.
			hierarchy.incrementSubgroupsCounter();
			if(!isSupervisor && getBooleanSplitFitnessFunction())
				node.actualMerge(getGroupName() + "_" + hierarchy.getSubgroupsCounter(), groupName);
			break;
			
		case Constants.INTEGER_SPLIT_FITNESS_FUNCTION:
			//I send my integer split fitness function value to the Service.
			hierarchy.incrementSubgroupsCounter();
			if(!isSupervisor){
				message = new A3Message(Constants.INTEGER_SPLIT_FITNESS_FUNCTION, String.valueOf(getIntegerSplitFitnessFunction()));
				sendToSupervisor(message);
			}
			break;
			
		case Constants.NEW_SPLITTED_GROUP:
			/*The supervisor triggered a random split command:
			 * a new group is created and I get notified of it.
			 */
			hierarchy.incrementSubgroupsCounter();
			break;
			
		case Constants.MERGE:
			//"senderAddress Constants.MERGE otherGroupName".
			node.actualMerge(message.object, getGroupName());
			break;
			
		case Constants.SPLIT:
			
			/* I will connect to a group splitted by this group, which has the same roles of this group,
			 * so I don't need to check for right roles here.
			 */
			if(!isSupervisor)
				node.actualMerge(getGroupName() + "_" + hierarchy.getSubgroupsCounter(), getGroupName());
			
			break;
		
		case Constants.WAIT_SUPERVISOR_FITNESS_FUNCTION_REQUEST:
			
			try{
				sendToSupervisor(new A3Message(Constants.WAIT_SUPERVISOR_FITNESS_FUNCTION,
						message.object + Constants.A3_SEPARATOR +
						String.valueOf(node.getSupervisorFitnessFunction(message.object))));
			}
			catch(Exception e){
				/* I can have this exception only if the channel to group "message.object" doesn't exist.
				 * In this case, I don't have to send back reply message, so I do nothing.
				 */
			}
			
			break;
			
		case Constants.WAIT_NEW_SUPERVISOR:
			// "senderAddress Constants.WAIT_NEW_SUPERVISOR groupName supervisorId".
			String[] splittedObject = ((String)message.object).split(Constants.A3_SEPARATOR);
			
			A3Channel channel;
			
			try{
				channel = node.getChannel(splittedObject[0]);
				
				if(splittedObject[1].equals(myId)){
					
					if(channel.followerOnly){
						channel.disconnect();
						node.setWaiting(channel);
					}
					else{
						channel.connect(splittedObject[0]);
						channel.becomeSupervisor();
						channel.sendToSupervisor(new A3Message(Constants.NEW_SUPERVISOR, ""));
					}
				}
				
				else{
					if((channel.supervisorOnly)){
						channel.disconnect();
						node.setWaiting(channel);
					}
					else{
						channel.connect(splittedObject[0]);
						channel.becomeFollower();
					}
				}
			}catch (Exception e){}
			
			break;
			
		case Constants.WAIT_MERGE:
			// "senderAddress Constants.WAIT_MERGE groupToJoin groupToDestroy".
			splittedObject = ((String)message.object).split(Constants.A3_SEPARATOR);
			node.actualMerge(splittedObject[0], splittedObject[1]);
			break;
			
		default:
			//I pass the message to the active role.
			inputQueue.enqueue(message);
			break;
		}
	}

	/**
	 * It puts a message in the queue of the messages directed to the supervisor.
	 * @param msg The message to be sent.
	 */
	public void sendToSupervisor(A3Message msg){
		queue.enqueue(msg);
	}

	/**
	 * Sends a message to the Service and, form there, to the supervisor.
	 * If the transmission is unsuccesful, this channel reconnects, and a view update starts.
	 * If the channel isn't connected, the sender thread is blocked.
	 * @param message The message to send.
	 */
	public boolean send(A3Message msg){
		boolean inServiceView = false;
		boolean sent = false;
		
		synchronized(this){
			while(!mIsConnected){
				try {
					wait();
					
				} catch (Exception e) {
					return false;
				}
			}
		}
		try {
	
			msg.senderAddress = myId;
			
			if (mIsConnected && serviceInterface != null){
				inServiceView = serviceInterface.sendToSupervisor(msg);
	
				if(!inServiceView){
					sent = false;
					reconnect = true;
				}
	
				else{
					sent = true;
					reconnect = false;
					inTransitionConditions = false;
					inServiceView = true;
				}
			}
			else{
				sent = false;
				reconnect = false;
			}
	
		} catch (Exception ex) {
			if(ex.getMessage().equals("org.alljoyn.Bus.Exiting"))
				stop();
			else{
				sent = false;
				reconnect = true;
			}
		}
		return sent;
	}

	/**
	 * Sends a message to the Service and, form there, to all members of the group.
	 * Such operation is possible only if this channel is the supervisor.
	 * If the transmission is unsuccesful, this channel reconnects, and a view update starts.
	 * @param message The message to send to every member of the group.
	 */
	public void sendBroadcast(A3Message message){
		
		boolean ok = false;
		
		if(isSupervisor){
			message.senderAddress = myId;
			try{
				ok = serviceInterface.sendBroadcast(message);
			}
			catch(Exception e){}
			if(!ok)
				reconnect();
		}
		else
			showOnScreen("Sending failed: I'm not the supervisor.");
	}

	/**
	 * Sends a message to the Service and, form there, to the specified member of the group.
	 * Such operation is possible only if this channel is the supervisor.
	 * If the transmission is unsuccesful, this channel reconnects, and a view update starts.
	 * @param message The message to send.
	 * @param receiverAddress The address of the channel that must receive the message.
	 */
	public void sendUnicast(A3Message message, String receiverAddress){
	
		boolean ok = false;
		
		if(isSupervisor){
			message.senderAddress = myId;
			try{
				ok = serviceInterface.sendUnicast(message, receiverAddress);
			}
			catch(Exception e){}
			if(!ok)
				reconnect();
		}
		else
			showOnScreen("Sending failed: I'm not the supervisor.");
	}

	/**
	 * Sends a message to the Service and, form there,
	 * to the members of the group which are interested in receiving it, basing on the reason of the message.
	 * Such operation is possible only if this channel is the supervisor.
	 * If the transmission is unsuccesful, this channel reconnects, and a view update starts.
	 * @param message The message to send.
	 */
	public void sendMulticast(A3Message message){
	
		boolean ok = false;
		
		if(isSupervisor){
			message.senderAddress = myId;
			try{
				ok = serviceInterface.sendMulticast(message);
			}
			catch(Exception e){}
			if(!ok)
				reconnect();
		}
		else
			showOnScreen("Sending failed: I'm not the supervisor.");
	}

	/**
	 * Sends a message to the Service and, form there, to the members of the group specified in "destinations".
	 * This results in calling "sendUnicast(message, destination)" on the Service for every destination.
	 * Such operation is possible only if this channel is the supervisor.
	 * If the transmission is unsuccesful, this channel reconnects, and a view update starts.
	 * @param message The message to send.
	 * @param destinations The members of the group that must receive the message.
	 */
	public void sendMulticast(A3Message message, ArrayList<String> destinations){
	
		boolean ok = true;
		
		if(isSupervisor){
			for (int i = 0; i < destinations.size() && ok; i ++){
				try{
					ok = serviceInterface.sendUnicast(message, destinations.get(i));
				}
				catch(Exception e){}
			}
			if(!ok)
				reconnect();
		}
		else
			showOnScreen("Sending failed: I'm not the supervisor.");
	}

	/**
	 * It adds a new subscription to the list "mySubscriptions" used on the channel
	 * and notifies it to the Service.
	 * @param reason The subscription to add.
	 */
	public void subscribe(int reason){
		try{
			subscriptions.subscribe(reason);
			A3Message subscriptionsMessage = new A3Message(Constants.SUBSCRIPTION, String.valueOf(reason));
			sendToSupervisor(subscriptionsMessage);
		}catch(Exception e){}
	}

	/**
	 * It removes a subscription from the list "mySubscriptions" used on the channel
	 * and notifies it to the Service.
	 * @param reason The subscription to remove.
	 */
	public void unsubscribe(int reason) {
		// TODO Auto-generated method stub
		try{	
			subscriptions.unsubscribe(reason);
			A3Message unsubscriptionsMessage = new A3Message(Constants.UNSUBSCRIPTION, String.valueOf(reason));
			sendToSupervisor(unsubscriptionsMessage);
		}catch(Exception e){}
	}
	
	@Override
	public void showOnScreen(String message) {
		// TODO Auto-generated method stub

		if(message.startsWith("("))
			ui.showOnScreen(message);
		else
			ui.showOnScreen("(Client " + myId + " " + groupName + "):\n" + message);
	}

	/**It creates the Service when it doesn't exist.*/
	private void createGroup() {
		// TODO Auto-generated method stub
		
		try{

			if(!mIsConnected){
				service = new Service(groupName, node, inTransitionConditions);
				service.connect();
			}
		
		}catch(Exception e){}
	}

	@Override
	public void timerFired(int reason) {
			// TODO Auto-generated method stub
			Message msg = callbackThread.obtainMessage();
			msg.arg1 = reason;
			msg.arg2 = Constants.TIMER_FIRED;
			callbackThread.sendMessage(msg);
	}

	/**It unblocks the thread which sends the messages to the supervisor.*/
	public synchronized void unblock() {
		// TODO Auto-generated method stub
		try{
			mIsConnected = true;
			notify();
		}catch (Exception ex) {}
	}

	/**It initializes this thread.
	 * This is done here to solve Android problems.
	 */
	@Override
	public void run(){}

	/**This thread manages the incoming messages.*/
	private class MessageHandler extends HandlerThread{
	
		private Handler mHandler;
		
		public MessageHandler() {
			super("MessageHandler_" + groupName);
			start();
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
				
				@Override
				public void handleMessage(Message msg) {
					
					A3Message message = (A3Message) msg.obj;
					try {
						onMessage(message);
					} catch (Exception e) {}
				}
			};
		}
	}

	/**This thread manages session losing and timeout firing.*/
	class CallbackThread extends HandlerThread{

		private Handler mHandler;
		
		public CallbackThread() {
			super("CallbackThread_" + groupName);
			start();
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
		
				@Override
				public void handleMessage(Message msg) {
		
					switch(msg.arg2){
		
					case Constants.SESSION_LOST:
		
						showOnScreen("Session lost: I reconnect.");
						reconnect();
						
						break;
		
					case Constants.TIMER_FIRED:{
						if(msg.arg1 == 0){
							
							mBus.cancelFindAdvertisedName(groupName);
							
							/*The group name wasn't found, so I must create the Service.
							 * If I create the group, I will probably be the supervisor:
							 * if I can only be a follower, I don't create the group.
							 */
							if(!discovered){
								
								if(followerOnly){
									
									node.setWaiting(A3Channel.this);
									return;
								}
								else
									createGroup();
							}
							
							try{
								joinSession();
							}catch(Exception e){}
						}
					}
					break;	
					default: break;
					}
				}
			};
	}
	}
	
	private class InputQueueHandler extends Thread{
		
		public InputQueueHandler() {
			super();
		}
	
		public void run() {
	
			A3Message message;
			
			while(true){
				try{
					message = inputQueue.get();
					while(activeRole == null)
						synchronized(inputQueue){
							inputQueue.wait();
						}
					activeRole.onMessage(message);
					inputQueue.dequeue();
				} catch (Exception e) {
					return;
				}
			}
		}
	}

	/**
	 * @return The value of an integer fitness function used for split,
	 * as defined in the group descriptor class.
	 * @throws Exception The integer fitness function is not implemented in the group descriptor class.
	 */
	protected int getIntegerSplitFitnessFunction() throws Exception{
		// TODO Auto-generated method stub
		
		return groupDescriptor.getIntegerSplitFitnessFunction();
	}

	/**
	 * @return The value of the boolean fitness function used for split,
	 * as defined in the group descriptor class.
	 * @throws Exception The integer fitness function is not implemented in the group descriptor class.
	 */
	protected boolean getBooleanSplitFitnessFunction() throws Exception{
		// TODO Auto-generated method stub
		return groupDescriptor.getBooleanSplitFitnessFunction();
	}

	/**
	 * @return The value of an integer fitness function used for supervisor election,
	 * as defined in the group descriptor class.
	 * @throws Exception The integer fitness function is not implemented in the group descriptor class.
	 */
	protected int getSupervisorFitnessFunction() throws Exception {
		// TODO Auto-generated method stub
		if(!followerOnly)
			return groupDescriptor.getSupervisorFitnessFunction();
		throw new Exception("Cannot become supervisor.");
	}

	/**
	 * It starts a supervisor election in the group this channel belongs to.
	 * @param groupName The name of the group in which to start the supervisor election.
	 */
	public void startSupervisorElection() {
		// TODO Auto-generated method stub
		A3Message message = new A3Message(Constants.SUPERVISOR_ELECTION, "");
		sendToSupervisor(message);
	}

	/**It sends a message to the Service, in order for it to start a random split operation.
	 * 
	 * @param nodesToTransfer The number of nodes to translate to the new group.
	 */
	public void split(int nodesToTransfer) {
		// TODO Auto-generated method stub
		
		A3Message message = new A3Message(Constants.SPLIT, String.valueOf(nodesToTransfer));
		sendToSupervisor(message);
	}

	/**It broadcasts a message, in order to collect the integer split fitness functions of the nodes.
	 * It is called only if this node is the supervisor.
	 * 
	 * @param nodesToTransfer The number of nodes to translate to the new group.
	 */
	public void splitWithIntegerFitnessFunction(int nodesToTransfer) throws Exception{
		// TODO Auto-generated method stub
		
		try{
			// I'm sure I am the supervisor.
			supervisorRole.startSplit(nodesToTransfer);
			A3Message message = new A3Message(Constants.INTEGER_SPLIT_FITNESS_FUNCTION, "");
			sendBroadcast(message);
		} catch (Exception e) {
			throw new Exception(e.getLocalizedMessage());
		}
	}

	/**It broadcasts a message, in order to start a boolean split operation.
	 * It is called only if this node is the supervisor.
	 */
	public void splitWithBooleanFitnessFunction() throws Exception {
		// TODO Auto-generated method stub
		
		try{
			A3Message message = new A3Message(Constants.BOOLEAN_SPLIT_FITNESS_FUNCTION, "");
			sendBroadcast(message);
		} catch (Exception e) {
			throw new Exception(e.getLocalizedMessage());
		}
	}

	/*--- Getters and setters of interest ---*/
	public boolean isConnectedForApplication() {
		return connectedForApplication;
	}

	public void setConnectedForApplication(boolean connectedForApplication) {
		this.connectedForApplication = connectedForApplication;
	}

	public boolean isConnectedForSystem() {
		return connectedForSystem;
	}

	public void setConnectedForSystem(boolean connectedForSystem) {
		this.connectedForSystem = connectedForSystem;
	}
	
	public String getGroupName() {
		// TODO Auto-generated method stub
		return groupName.replaceFirst(Constants.PREFIX, "");
	}

	public Hierarchy getHierarchy(){
		return hierarchy;
	}
	
	public boolean isSupervisor() {
		// TODO Auto-generated method stub
		return isSupervisor;
	}

	public String getChannelId() {
		// TODO Auto-generated method stub
		return myId;
	}

	public Service getService() {
		// TODO Auto-generated method stub
		return service;
	}

}

