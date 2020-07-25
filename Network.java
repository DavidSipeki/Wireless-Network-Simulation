import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.*;

/* 
Class to simulate the network. System design directions:

- Synchronous communication: each round lasts for 20ms
- At each round the network receives the messages that the nodes want to send and delivers them
- The network should make sure that:
	- A node can only send messages to its neighbours
	- A node can only send one message per neighbour per round
- When a node fails, the network must inform all the node's neighbours about the failure
*/


public class Network {

	private static List<Node> nodes = new ArrayList<>();
	private static int round = 0;
	private static int period = 20;
	// Key corresponds to round number, list contains the nodeIDs that start an election in given round
	private static Map<Integer, List<Integer>> elections = new HashMap<>();
	// Key corresponds to round number, value contains the nodeIDs that fail in given round
	private static Map<Integer, Integer> fails = new HashMap<>();
	// Message buffer
	// Messages from nodes are moved to this list and then distributed at the end of the round
	private static List<Message> msgBuffer = new ArrayList<>();
	// Scheduler
	private static ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
	
	// List of elected leaders during execution of part A
	private static List<Integer> electedA = new ArrayList<>();
	// List of elected leaders during execution of part B
	private static List<Integer> electedB = new ArrayList<>();
	
	// Used to keep track if we are on part A or part B
	private static boolean stillOnPartA = true;
	

	
	
	// Reads and processes the graph input file
	private void processGraphFile(String fileName, Network net) throws Exception{
		// Read the file
		File f = new File(fileName);
		Scanner myReader = new Scanner(f);
		
		// Create nodes with appropriate IDs and neighbours
	    while (myReader.hasNextLine()) {
	        String data[] = myReader.nextLine().split(" ");
	        int nodeID = (Integer.parseInt(data[0]));
	        Node n = new Node(nodeID, net);
	        
	        for (int j = 1; j < data.length; j++) {
	        	n.addNeighbour(Integer.parseInt(data[j]));
	        }
	        nodes.add(n);
	    }
	    
	    // Update prev and next values and compensate for missing links
	    for (int i = 0; i < nodes.size(); i++) {
	    	Node n = nodes.get(i);
	    	n.setNext(nodes.get((i+1) % nodes.size()).getNodeId());
	    	n.addNeighbour(nodes.get((i+1) % nodes.size()).getNodeId());
	    	if (i == 0) {
	    		n.setPrev(nodes.get(nodes.size() - 1).getNodeId());
	    		n.addNeighbour(nodes.get(nodes.size() - 1).getNodeId());
	    	}
	    	else {
	    		n.setPrev(nodes.get(i - 1).getNodeId());
	    		n.addNeighbour(nodes.get(i - 1).getNodeId());
	    	}
	    }
	    
	    myReader.close();
	}
	
	
	// Reads and processes the events input file
	public static void processEventsFile(String fileName) throws Exception {
		// Read the file
		File f = new File(fileName);
		Scanner myReader = new Scanner(f);
	    while (myReader.hasNextLine()) {
	        String data[] = myReader.nextLine().split(" ");
	        
	        // ELECT commands
	        if (data[0].equals("ELECT")) {
		        int roundNum = Integer.parseInt(data[1]);
		        List<Integer> ns = new ArrayList<>();
		        for (int i = 2; i < data.length; i++) {
		        	ns.add(Integer.parseInt(data[i]));
		        }
		        elections.put(roundNum, ns);
	        }
	        
	        // FAIL commands
	        else if (data[0].equals("FAIL")) {
	        	int roundNum = Integer.parseInt(data[1]);
	        	int nodeId = Integer.parseInt(data[2]);
	        	fails.put(roundNum, nodeId);
	        }
	    }
	    
	    myReader.close();
	}
	
	
	
	// Logs the results of the simulation
	private static void logger() {
		PrintWriter writer;
		try {
			writer = new PrintWriter("log.txt", "UTF-8");
			writer.println("Part A");
			for (int i = 0; i < electedA.size(); i++) {
				writer.println("Leader Node " + electedA.get(i));
			}
			writer.println("\nPart B");
			for (int i = 0; i < electedB.size(); i++) {
				writer.println("Leader Node " + electedB.get(i));
			}
			writer.println("simulation completed");
			writer.close();
		} 
		catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	
	// Allows the nodes to signal election result
	public void logElection(int leader) {
		if (stillOnPartA) {
			electedA.add(leader);
		}
		else {
			electedB.add(leader);
		}
	}
	

	
	// Returns the node with the requested id
	public static Node getNodeById(int id) {
		for (int i = 0; i < nodes.size(); i++) {
        	if (nodes.get(i).getNodeId() == id) {
        		return nodes.get(i);
        	}
        }
		return null;
	}
	
	
	// Moves the outgoing messages from the nodes to the network buffer
	public synchronized static void collectMessages() {
		// Iterate over the nodes
		for (int i = 0; i < nodes.size(); i++) {
			Node n = nodes.get(i);
			List<Integer> alreadySentTo = new ArrayList<>();
			// Iterate over its outgoing messages
			for (int j = 0; j < n.getOutgoingMessages().size(); j++) {
				// Limit it to one message to each neighbour in one round
				if (!alreadySentTo.contains(n.getOutgoingMessages().get(j).getRecipient())) {
					// Wait for lock
					while(n.isLocked()) {
					    try {
							Node.class.wait();
						}
						catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
					n.lock();
					// Check if the recipient is a neighbour of the sender except if it is a forwarding request
					if (n.getNeighbours().contains(n.getOutgoingMessages().get(j).getRecipient()) ||
							n.getOutgoingMessages().get(j).isForward()) {
						// Move message from node to network buffer and log that node has sent a message to recipient this round
						msgBuffer.add(n.getOutgoingMessages().get(j));
						alreadySentTo.add(n.getOutgoingMessages().get(j).getRecipient());
						n.removeOutgoingMessage(n.getOutgoingMessages().get(j));
					}
					n.unlock();
				}
			}
		}
	}
	
	// Recreates a node
	public static Node copyNode(Node n) {
		Node newNode = new Node(n.getNodeId(), n.getNetwork());
        for (int j = 0; j < n.getNeighbours().size(); j++) {
        	newNode.addNeighbour(n.getNeighbours().get(j));
        }
        newNode.setNext(n.getNext());
        newNode.setPrev(n.getPrev());
        newNode.setNextIsDead(n.getNextIsDead());
        return newNode;
	}
	
	
	// Sends out the messages
	public synchronized static void deliverMessages() {
		
		// Iterate over the messages
		Iterator<Message> it = msgBuffer.iterator();
		while (it.hasNext()) {
		    Message m = (Message)it.next();
		    // Case where the recipient is a neighbour of the sender
		    if (!m.isForward()) {
		    	
			    Node n = getNodeById(m.getRecipient());
			    // Fire up node that is receiving the message, if it's not running already
			    if (!n.isAlive()) {
			    	// Can't .run() original Node, so I recreate the node and .start()
			    	Node newNode = copyNode(n);
			        nodes.remove(n);
			        nodes.add(newNode);
			        newNode.start();
			        n = newNode;
			    }
			    // Wait for lock
			    while (n.isLocked()) {
			    	try {
						Node.class.wait();
						System.out.println("waiting");
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
			    }
			    n.lock();
			    // Deliver message to node
			    n.receiveMsg(m.getMessage());
			    n.unlock();
			    // Remove message from the list
			    it.remove();
		    }
		    
		    // If the recipient is not a neighbour of the sender
		    else {
		    	// Find the next node which leads to the shortest path to recipient
		    	int nextID = getNextNodeOnPath(m.getSender(), m.getRecipient());
		    	
		    	// If path exists
		    	if (nextID != (-1)) {
		    		System.out.println("Network forwards the mesage to " + nextID);
			    	// Send the message to the next node on the route
			    	Node n = getNodeById(nextID);
			    	if (!n.isAlive()) {
				    	// Can't .run() original Node, so I recreate the node and .start()
				    	Node newNode = copyNode(n);
				        nodes.remove(n);
				        nodes.add(newNode);
				        newNode.start();
				        n = newNode;
				    }
			    	// Wait for lock
				    while(n.isLocked()) {
				    	try {
							Node.class.wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
				    }
				    n.lock();
				    String fwdMsg;
				    // If the target node is the next node on the path
				    if (nextID == m.getRecipient()) {
				    	// Remove the FORWARDTO x from the front of the message
				    	String[] temp = m.getMessage().split(" ");
				    	fwdMsg = temp[2] + " " + temp[3];
				    }
				    // If message has to be further forwarded
				    else {
				    	// Keep The FORWARDTO x prefix
				    	fwdMsg = m.getMessage();
				    }
				    // Deliver message to node
				    n.receiveMsg(fwdMsg);
				    n.unlock();
				    System.out.println("Sending message " + fwdMsg + " to " + nextID);
				    // Remove message from the list
				    it.remove();
		    	}
		    	
		    	// If there is no path to recipient
		    	else {
		    		executorService.shutdown();
		    		System.out.println("\n\nUnreachable node detected");
		    		logger();
		    	}
		    }
		}
	}
	
	
	// Breadth first search from the recipient to find shortest path between 2 nodes
	private static int getNextNodeOnPath(int from, int to) {
		List<Integer> visited = new ArrayList<>();
		visited.add(to);
		Queue<Integer> q = new LinkedList<>();
		q.add(to);
		while (!q.isEmpty()) {
			Node current = getNodeById(q.remove());
			for (int i = 0; i < current.getNeighbours().size(); i++) {
				if (current.getNeighbours().get(i) == from) {
					return current.getNodeId();
				}
				if (!visited.contains(current.getNeighbours().get(i))) {
					q.add(current.getNeighbours().get(i));
					visited.add(current.getNeighbours().get(i));
				}
			}
		}
		
		return -1;
	}
		
	
	
	public static synchronized void processNodeFailure(int id) {

		System.out.println(String.format("Node(%d) has failed", id));
		Node failedNode = getNodeById(id);
		
		// Update the neighbours of the failed node
		for (int i = 0; i < failedNode.getNeighbours().size(); i++) {
			Node neighbour = getNodeById(failedNode.getNeighbours().get(i));
			neighbour.removeNeighbour(id);
			System.out.println(String.format("Node(%d) has been notified of failure", neighbour.getNodeId()));
		}
		
		// Update the node's previous and next neighbour
		Node prev = getNodeById(failedNode.getPrev());
		prev.setNextIsDead(true);
		prev.setNext(failedNode.getNext());
		Node next = getNodeById(failedNode.getNext());
		next.setPrev(failedNode.getPrev());
		
		// Stop the node's execution
		failedNode.stopExecution();
		
		// Remove node from nodes list
		nodes.remove(failedNode);
		
		// If the graph stayed connected we need to trigger a new election
		if (graphIsConnected()) {
			nodes.get(0).triggerElection();
		}
		// If the graph is disconnected stop execution
		else {
    		executorService.shutdown();
    		System.out.println("\n\nGraph has become disconnected");
    		logger();
		}
	}
	
	
	// Checks if the nodes in node list forms a connected graph
	private static boolean graphIsConnected() {
		if (nodes.size() == 0) {
			return false;
		}
		
		List<Integer> visited = new ArrayList<>();
		Stack<Node> s = new Stack<>();
		s.push(nodes.get(0));
		while (!s.isEmpty() && visited.size() < nodes.size()) {
			Node current = s.pop();
			List<Integer> neighbours = current.getNeighbours();
			for (int i = 0; i < neighbours.size(); i++) {
				if (!visited.contains(neighbours.get(i))) {
					s.push(getNodeById(neighbours.get(i)));
					visited.add(neighbours.get(i));
				}
			}
		}
		
		return (visited.size() == nodes.size());
	}

	
	public static void triggerEvents() {
		// Triggering ELECTIIONS
		Iterator<Map.Entry<Integer, List<Integer>>> it = elections.entrySet().iterator();
		// Iterate over the events list
	    while (it.hasNext()) {
	    	Map.Entry<Integer, List<Integer>> pair = it.next();
	        int roundNum = (Integer) pair.getKey();
	        List<Integer> nodesToTrigger = (List<Integer>) pair.getValue();
	        // If the current round matches the round the event should be triggered in
	        if (round == roundNum) {
	        	// Trigger all the necessary nodes
	        	for (int i = 0; i < nodesToTrigger.size(); i++) {
	        		Node n = getNodeById(nodesToTrigger.get(i));
	        		n.triggerElection();
	        	}
	        	// Remove event from the list
	        	it.remove();
	        }
	    }
	    
	    // Trigger FAILS
		Iterator<Map.Entry<Integer, Integer>> it2 = fails.entrySet().iterator();
		// Iterate over the events list
	    while (it2.hasNext()) {
	    	Map.Entry<Integer, Integer> pair = it2.next();
	        int roundNum = (Integer) pair.getKey();
	        int nodeID = (Integer) pair.getValue();
	        // If the current round matches the round the event should be triggered in
	        if (round == roundNum) {
	        	// From this point on we are on part B
	        	stillOnPartA = false;
	        	// Process failure
	        	processNodeFailure(nodeID);
	        	// Remove event from the list
	        	it2.remove();
	        }
	    }
	}
	

	// Returns the number of nodes currently running
	private static int numOfActiveNodes() {
		int activeNodes = 0;
		for (int i = 0 ; i < nodes.size(); i++) {
			if (nodes.get(i).isRunning()) {
				activeNodes++;
			}
		}
		
		return activeNodes;
	}
	
	
	// Periodically collects and delivers messages, and triggers events
    private static void run() {
    	// If there are no active nodes and no further events
    	if (elections.size() == 0 && fails.size() == 0 && numOfActiveNodes() == 0) {
    		// End the execution
    		executorService.shutdown();
    		System.out.println("\n\nProgram has finished executing");
    		logger();
    	}
    	else {
            System.out.println("\nRound " + round);
            System.out.println("-------");
            collectMessages();
            deliverMessages();
            triggerEvents();
            round++;
    	}
    }
	
	
	public static void main(String args[]) throws IOException, InterruptedException {
		
		Network n = new Network();
		// Process first input file
		try {
			n.processGraphFile(args[0], n);
		}
		catch (Exception e) {
			System.out.println("Invalid graph input file");
		}
		
		
		// Process the second input file
		try {
			processEventsFile(args[1]);
		}
		catch (Exception e) {
			System.out.println("Invalid events input file");
		}
		
		
		// Start the periodic network processing
		executorService.scheduleAtFixedRate(Network::run, period, period, TimeUnit.MILLISECONDS);
	}
	
	
	
	
	
	
	
}