package a3.a3droid;

import java.util.ArrayList;

/**This class collects integer fitness function values from the nodes of a group, for any reason.
 * Values are collected in a time interval: values that arrives after time firing are discarded.
 * At the end of the timeout, values are reordered from the best (higher) to the worst (lower)
 * and the best value(s) can be retrieved.
 * 
 * @author Francesco
 *
 */
public class FitnessFunctionManager implements TimerInterface {

	/**The list of couples <node address, fitness function value>.*/
	private ArrayList<FitnessFunction> fitnessFunctions;
	
	/**The object to which communicate timer firing.*/
	private TimerInterface timerInterface;
	
	/**true if fitness function collection is ongoing, false otherwise.*/
	private boolean collecting;
	
	/**
	 * @param timerInterface The object to which communicate timer firing.
	 */
	public FitnessFunctionManager(TimerInterface timerInterface){
		fitnessFunctions = new ArrayList<FitnessFunction>();
		this.timerInterface = timerInterface;
	}
	
	/**
	 * It receives the fitness function values and add them to the list.
	 * @param message The incoming message.
	 */
	public synchronized boolean onMessage(A3Message message){
		if(collecting)
			fitnessFunctions.add(new FitnessFunction(message.senderAddress, Integer.valueOf(message.object)));
		return collecting;
	}

	/**It clears the fitness function values list and start the timer.
	 * 
	 * @param reason The reason of fitness function values collecting.
	 */
	public synchronized void startCollectingFitnessFunctions(int reason){
		fitnessFunctions = new ArrayList<FitnessFunction>();
		collecting = true;
		new Timer(this, reason).start();
	}
	
	/**It reorders received fitness function values from the best (higher) to the worst (lower).
	 */
	private synchronized void reorder(){
		
		if(!fitnessFunctions.isEmpty()){
			ArrayList<FitnessFunction> reorderedFitnessFunctions = new ArrayList<FitnessFunction>();
			FitnessFunction temp;
			int j;
			
			reorderedFitnessFunctions.add(fitnessFunctions.get(0));
			
			for(int i = 1; i < fitnessFunctions.size(); i++){
				temp = fitnessFunctions.get(i);
				for(j = 0; j < reorderedFitnessFunctions.size() && 
						temp.value <= reorderedFitnessFunctions.get(j).value; j++){}
				
				reorderedFitnessFunctions.add(j, temp);
			}
			fitnessFunctions = reorderedFitnessFunctions;
		}
	}
	
	/**Used to get the best results of a fitness function values collecting.
	 * 
	 * @param numberOfNodes The number of best results requested.
	 * @return
	 * The addresses of the selected nodes, from the best (in first position) to the worst (in last position).
	 * @throws Exception parameter is 0, or no fitness function values are arrived.
	 */
	public synchronized String[] getBest(int numberOfNodes) throws Exception{
		
		if(numberOfNodes == 0 || fitnessFunctions.isEmpty())
			throw new Exception("No result requested or no fitness function values arrived.");
		
		String[] result = new String[numberOfNodes];
		
		for(int i = 0; i < fitnessFunctions.size() && i < numberOfNodes; i++)
			result[i] = fitnessFunctions.get(i).node;
		
		return result;
	}
	
	@Override
	public void timerFired(int reason) {
		// TODO Auto-generated method stub
		collecting = false;
		try {
			reorder();
		} catch (Exception e) {}
		timerInterface.timerFired(reason);
	}

	/**A pair <node address, integer fitness function value>.*/
	private class FitnessFunction{
		private String node;
		private int value;
		
		private FitnessFunction(String address, int fitnessFunctionValue){
			node = address;
			value = fitnessFunctionValue;
		}
		
		public String toString(){
			return node + " " + String.valueOf(value) + ";";
		}
	}
}