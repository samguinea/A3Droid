package a3.a3droid;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

/**This class represents the role that the Node can play in a group.
 * A list of the roles a node can play resides on A3Node, it is fixed at node creation time and it can't change.
 * The A3Node constructor automatically sets the field "node" of the role to itself,
 * and the "id" field of the role to the canonical name of the role class.
 * 
 * There are two roles that can be played in a group by a node: the supervisor or a follower.
 * So a node, to connect a group, must have both roles in its list.
 * If it has both of them, the node creates a channel and sets the roles of that channel to a clone of theirs:
 * cloning the roles is necessary in order to avoid that two channels with the same role block together
 * when deactivating only one of them.
 * When needed, the channel creates a new thread using the role and starts it.
 * 
 * The role id is transmitted in messages about operations between groups.
 * Being it the canonical name of the class, a role is uniquely identified.
 * When a node receives communication to connect to a group with certain two roles,
 * it looks for them in its list, and if it finds them it connects to the group.
 * 
 * This class must be extended, so this solution solves the problem to instantiate the correct superclass.
 * The constructors of the superclasses must call this constructor by calling "super();"
 * and must contain only that instruction.
 * @author Francesco
 *
 */
public abstract class A3Role implements Runnable{

	/**It indicates if this role is currently active or not.*/
	protected boolean active;

	/**The canonical name of this role class.*/
	protected String id;

	/**The node whose methods this role can call.*/
	protected A3Node node;

	/**The channel this role belongs to.*/
	protected A3Channel channel;

	/**he user interface to interact with.*/
	protected UserInterface ui;

	private RoleMessageHandler handler;
	/**
	 * Set this role as not active and the id of this role to its class canonical name.
	 */
	public A3Role(){
		super();
		active = false;
		id = getClass().getCanonicalName();
	}

	/**
	 * It is composed of an initialization part and a loop that is executed while this role is active.
	 * The initialization part must be defined in the abstract method "onActivation()".
	 * The logic in the loop must be defined in the abstract method "logic()".
	 */
	@Override
	public void run(){
		
		onActivation();
		
		while(active){
			logic();
		}
	}

	/**
	 * The initialization part executed before the beginning of the loop.
	 * This method must be seen as a constructor, since the real constructor must contain only "super()" instruction.
	 */
	public abstract void onActivation();

	/**
	 * The logic that is executed within the loop.
	 * If some waiting is needed, use the static method "Thread.sleep(int)".
	 * To exit the loop, execute "active = false".
	 */
	public abstract void logic();

	public void setActive(boolean active) {
		this.active = active;

		if(active){
			handler = new RoleMessageHandler();
		}
	}

	/**
	 * The logic that must be executed when receiving an application message.
	 * System messages, which doesn't depend on the application,
	 * are filtered and managed in A3Channel or in A3SupervisorRole.
	 * @param message The received message.
	 */
	public abstract void receiveApplicationMessage(A3Message message);

	/**It receives the incoming messages and passes them to another thread.
	 * 
	 * @param message The incoming message.
	 */
	public void onMessage(A3Message message){
		Message msg = handler.obtainMessage();
		msg.obj = message;
		handler.sendMessage(msg);
	}

	public String getId(){
		return id;
	}

	public String getGroupName(){
		return channel.getGroupName();
	}

	public void setNode(A3Node node){
		this.node = node;
	}

	public void setChannel(A3Channel a3channel) {
		// TODO Auto-generated method stub
		channel = a3channel;
		ui = channel.ui;
	}

	public void showOnScreen(String message){
		ui.showOnScreen(message);
	}

	public void handleMessage(A3Message message){
		String[] object;

		switch(message.reason){

		case Constants.STACK_REPLY:
			//"senderAddress Constants.STACK_REPLY otherGroupName true/false".
			try{
				object = message.object.split(Constants.A3_SEPARATOR);
				node.stackReply(object[0], getGroupName(), Boolean.valueOf(object[1]));
			} catch (Exception e) {}
			break;

		case Constants.PEERS_REPLY:
			//"senderAddress Constants.PEERS_REPLY otherGroupName true/false".
			object = message.object.split(Constants.A3_SEPARATOR);

			/* peers(group1, group2) = peers(group2, group1),
			 * so the way I visualize the involved groups doesn't matter.
			 */
			node.peersReply(getGroupName(), object[0], Boolean.valueOf(object[1]));
			break;

			/*If I receive this message, I connected another group to ask for a hierarchy operation.
			 * Probably I joined such group as follower, but not necessarily,
			 * or I became supervisor during an ongoing hierarchy operation.
			 */
		case Constants.HIERARCHY_REPLY:
			//"senderAddress Constants.HIERARCHY_REPLY parentGroupName otherGroupName true/false".
			object = message.object.split(Constants.A3_SEPARATOR);
			node.hierarchyReply(object[0], object[1], getGroupName(), Boolean.valueOf(object[2]));
			break;

		default:
			break;
		}
	}

	private class RoleMessageHandler extends HandlerThread{

		private Handler mHandler;

		public RoleMessageHandler() {
			super("RoleMessageHandler_" + getGroupName());
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
				/**
				 * There are system messages whose management doesn't depend on the application:
				 * they are filtered and managed here.
				 * @param msg The incoming message.
				 */
				@Override
				public void handleMessage(Message msg) {
					A3Role.this.handleMessage((A3Message) msg.obj);
				}
			};
		}
	}
}
