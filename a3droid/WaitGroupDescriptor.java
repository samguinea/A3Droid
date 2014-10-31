package a3.a3droid;

import java.util.Random;

/**
 * This class is the descriptor of the group named "wait",
 * where the nodes looking for a group, but not in conditions to join them, are parked.
 * Nodes are parked here if:
 * - they can only be supervisors of the looked for group, but a supervisor already exists;
 * - or the can only be followers of the looked for group, but they are elected supervisors.
 * @author Francesco
 *
 */
public class WaitGroupDescriptor extends GroupDescriptor {

	public WaitGroupDescriptor(){
		super("wait", Constants.PACKAGE_NAME + ".WaitSupervisorRole", Constants.PACKAGE_NAME + ".WaitFollowerRole");
	}
	
	@Override
	public int getSupervisorFitnessFunction() {
		// TODO Auto-generated method stub
		Random randomNumberGenerator = new Random();
		int result = randomNumberGenerator.nextInt(10);
		return result;

	}

}
