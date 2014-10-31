package a3.a3droid;

/**
 * This class represents the role played by a follower of "wait" group.
 * There's no need to do anything.
 * @author Francesco
 *
 */
public class WaitFollowerRole extends A3FollowerRole {

	public WaitFollowerRole() {
		// TODO Auto-generated constructor stub
		super();
	}

	@Override
	public void logic() {
		// TODO Auto-generated method stub
		showOnScreen("(WaitFollowerRole)");
		active = false;
	}

	@Override
	public void receiveApplicationMessage(A3Message message) {
		// TODO Auto-generated method stub
		showOnScreen(message.toString());
	}

	@Override
	public void onActivation() {}
}
