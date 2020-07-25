public class Message {
	private int sender;
	private int recipient;
	private String msg;
	private boolean forward;
	
	public Message(int sender, int recipient, String msg, boolean forward) {
		this.sender = sender;
		this.recipient = recipient;
		this.msg = msg;
		this.forward = forward;
	}
	
	public int getSender() {
		return sender;
	}
	
	public int getRecipient() {
		return recipient;
	}
	
	public String getMessage() {
		return msg;
	}
	
	public boolean isForward() {
		return forward;
	}
}
