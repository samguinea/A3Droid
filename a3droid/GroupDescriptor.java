package a3.a3droid;

/**
 * This class contains the names of the group and of its roles.
 * It must be extended in order to define the fitness functions to use in supervisor election operation.
 * The default implementations of split operation fitness function raise an exception to warn that they are not defined.
 * @author Francesco
 *
 */
public abstract class GroupDescriptor {
	
	/**The name of the group this descriptor describe.*/
	private String name;
	
	/**The role the supervisor must play in group "name".*/
	private String supervisorRoleId;
	
	/**The role a follower must play in group "name".*/
	private String followerRoleId;
	
	/**
	 * 
	 * @param name The name of the group this descriptor describe.
	 * @param supervisorRoleId The role the supervisor must play in group "name".
	 * @param followerRoleId The role a follower must play in group "name".
	 */
	public GroupDescriptor(String name, String supervisorRoleId, String followerRoleId){
		this.name = name;
		this.supervisorRoleId = supervisorRoleId;
		this.followerRoleId = followerRoleId;
	}
	
	/**To override in order to determine the value of an integer fitness function used for split.
	 * 
	 * @return Nothing (default implementation). It should return the value of an integer fitness function.
	 * @throws Exception The integer fitness function is not implemented (default implementation).
	 */
	public int getIntegerSplitFitnessFunction() throws Exception{
		// TODO Auto-generated method stub
		
		throw new Exception("Split failed: integer fitness function not implemented.");
	}

	/**To override in order to determine the value of a boolean fitness function used for split.
	 * 
	 * @return Nothing (default implementation). It should return the value of a boolean fitness function.
	 * @throws Exception The integer fitness function is not implemented (default implementation).
	 */
	public boolean getBooleanSplitFitnessFunction() throws Exception{
		// TODO Auto-generated method stub
		throw new Exception("Split failed: boolean fitness function not implemented.");
	}

	/**To override in order to determine the value of an integer fitness function used for supervisor election.
	 * 
	 * @return It should return the value of an integer fitness function.
	 */
	public abstract int getSupervisorFitnessFunction();
	
	/**
	 * Create the string representation of the type GroupInfo.
	 * The obtained string is like "name supervisorRoleId followerRoleId".
	 */
	@Override
	public String toString(){
		return name + Constants.A3_SEPARATOR + supervisorRoleId + Constants.A3_SEPARATOR + followerRoleId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getSupervisorRoleId() {
		return supervisorRoleId;
	}

	public void setSupervisorRoleId(String supervisorRoleId) {
		this.supervisorRoleId = supervisorRoleId;
	}

	public String getFollowerRoleId() {
		return followerRoleId;
	}

	public void setFollowerRoleId(String followerRoleId) {
		this.followerRoleId = followerRoleId;
	}
	
}
