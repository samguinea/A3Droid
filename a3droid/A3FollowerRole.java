package a3.a3droid;

/**This class represents the logic that will be executed on a follower.
 * It is needed only to distinguish between followers and supervisor.
 */
public abstract class A3FollowerRole extends A3Role{

	public A3FollowerRole(){
		super();
	}
	
	@Override
	public abstract void logic();

	@Override
	public abstract void receiveApplicationMessage(A3Message message);
	
	@Override
	public void handleMessage(A3Message message) {

		super.handleMessage(message);
		switch(message.reason){
		//I already filtered them in super.handleMessage(message).
		case Constants.STACK_REPLY:
		case Constants.PEERS_REPLY:
		case Constants.HIERARCHY_REPLY:
			break;
		default: receiveApplicationMessage(message); break;
		}
	}
}
