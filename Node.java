import java.util.*;
import java.io.*;

/* Class to represent a node. Each node must run on its own thread.*/

public class Node extends Thread {

    private int id;
    private int currentLeader;
    private boolean participant = false;
    private boolean running = false;
    private boolean leader = false;
    private int next;
    private int prev;
    private boolean locked = false;
    // Flags if the node's original next neighbour has failed 
    private boolean nextIsDead = false;
    private Network network;
    public boolean hasBeenStarted = false;
    
    // IDs of the neighbouring nodes
    private List<Integer> neighbours;

    // Queues for the incoming and outgoing messages
    private List<String> incomingMsg;
    private List<Message> outgoingMsg;
    
    
    
    
    public Node(int id, Network n){
        this.id = id;
        this.network = n;
        neighbours = new ArrayList<Integer>();
        incomingMsg = new ArrayList<String>();
        outgoingMsg = new ArrayList<Message>();
    }
    
    // Basic methods for the Node class
    
    public int getNodeId() {
        return id;
    }
    
    public boolean getNextIsDead() {
        return nextIsDead;
    }
    
    public void setNextIsDead(boolean nextIsDead) {
        this.nextIsDead = nextIsDead;
    }
            
    public boolean isNodeLeader() {
        return leader;
    }
        
    public List<Integer> getNeighbours() {
        return neighbours;
    }

    public void addNeighbour(int n) {
        if (!neighbours.contains(n)) {
            neighbours.add(n);
        }
    }
    
    public void removeNeighbour(int n) {
        for (int i = 0; i < neighbours.size(); i++) {
            if (neighbours.get(i) == n) {
                neighbours.remove(i);
            }
        }
    }
    
    public Network getNetwork() {
        return network;
    }
    
    public void lock() {
        locked = true;
    }
    
    public void unlock() {
        locked = false;
    }
    
    public boolean isLocked() {
        return locked;
    }
    
    public int getNext() {
        return next;
    }
    public void setNext(int next) {
        this.next = next;
    }
    
    public int getPrev() {
        return prev;
    }
    public void setPrev(int prev) {
        this.prev = prev;
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public List<Message> getOutgoingMessages() {
        return outgoingMsg;
    }
    
    public void removeOutgoingMessage(Message m) {
        outgoingMsg.remove(m);
    }
    
    public void stopExecution() {
        running = false;
        participant = false;
    }
    
    
            
    public void receiveMsg(String m) {
        // Lock node to prevent concurrent access to incoming messages
        lock();
        incomingMsg.add(m);
        unlock();
    }
        
    
    // Given a message, correctly labels it and puts it in the outgoing messages pile
    public void forwardMessage(String m) {
        // Lock node to prevent concurrent access to outgoing messages
        lock();
        // If the original next node has failed
        if (nextIsDead) {
            // Send a forward message
            String msg = "FORWARDTO " + next + " " + m;
            outgoingMsg.add(new Message(id, next, msg, true));
            System.out.println(String.format("Node(%d) sends message (%s)", id, msg));
        }
        // Otherwise just pass the message on to the next node
        else {
            outgoingMsg.add(new Message(id, next, m, false));
            System.out.println(String.format("Node(%d) sends message (%s) to Node(%d)", id, m, next));
        }
        unlock();
    }
    
    
    // Given a FORWARDTO message, parses it correctly and adds it to the outgoing pile
    public void sendForwardedMessage(String msg) {
        String temp[] = msg.split(" ");
        int recipient = Integer.parseInt(temp[1]);
        outgoingMsg.add(new Message(id, recipient, msg, true));
    }

    
    // Triggers the node to start a leader election
    public void triggerElection() {
        System.out.println(String.format("Node(%d) starting ELECTION",  id));
        participant = true;
        running = true;
        String msg = "ELECT " + id;
        forwardMessage(msg);
    }
    
    
    // Processes the incoming message
    private void processMsg(String msg) {
        String temp[] = msg.split(" ");
        String msgType = temp[0];
        
        System.out.println(String.format("Node(%d) recieved message(%s)", id, msg));
        
        switch (msgType) {
            case "ELECT":
                int msgID = Integer.parseInt(temp[1]);
                if (msgID > id) {
                    participant = true;
                    forwardMessage(msg);
                }
                else if (msgID < id) {
                    if (!participant) {
                        participant = true;
                        forwardMessage("ELECT " + id);
                    }
                    else {
                        System.out.println(String.format("Node(%d) discards message (%s)", id, msg));
                    }
                }
                else {
                    leader = true;
                    network.logElection(id);
                    System.out.println(String.format("Node(%d) marks itself as LEADER", id));
                    forwardMessage("LEADER " + id);
                    participant = false;
                    running = false;
                }
                break;
                
            case "LEADER":
                msgID = Integer.parseInt(temp[1]);
                // Set the leader
                System.out.println(String.format("Node(%d) set Node(%d) as leader", id, msgID));
                currentLeader = msgID;
                // Forward the message except if it would be to the leader
                if (next != msgID) {
                    forwardMessage(msg);
                }
                // Stop the thread
                participant = false;
                running = false;
                break;
                
            case "FORWARDTO":
                // Just put the message in the outgoing messages
                sendForwardedMessage(msg);
                break;    
            }
    }
    

    
    public void run() {
        System.out.println(String.format("Node(%d) started running", id));
        running = true;
        hasBeenStarted = true;
        while (running || participant) {
            // Need to sync the incoming and outgoing messages between the Node and Network threads
            synchronized (this) {
                // Process the incoming messages
                Iterator iter = incomingMsg.iterator();
                while (iter.hasNext()) {
                    locked = true;
                    String msg = (String) iter.next();
                    processMsg(msg);
                    iter.remove();
                    locked = false;
                }  
            }
        }
        running = false;
        participant = false;
        System.out.println(String.format("Node(%d) stopped running", id));
    }
    
}