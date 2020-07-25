# Wireless-Network-Simulation


## Description
This project simulates of a wireless network and implements the ringbased Change and Roberts algorithm for leader election among the nodes of the network.
It simulates a distributed system using multiple threads. Each node runs on its own thread. Additionally, a different thread will simulates the actions of the network delivering messages from one node to another. Communication is assumed to be synchronous. Each synchronous round lasts for 20ms. The program simulates this behavior of the network receiving and delivering messages exactly once every round. A node is allowed to send messages only to its neighbors as specified in the input file describing the network graph. It can send at most one message to each neighbor in one round.


## Input file specification
There are three types of input files: graph.txt, elect.txt, fail.txt.The input file graph.txt, contains the network graph. Each line describes a node; the first the item is the id of the node, and the following ones are its neighbors. The ordering of the rows gives an ordering of the nodes on the ring (the first follows the last).
The input file elect.txt contains a list of leader elections initiated by different sets of nodes. These lines start with ELECT followed by the round number, followed by nodes that startelection at that round. The input file fail.txt contains the lines describing nodes failing. The file begins with a single ELECT statement like the ones in elect.txt. Following this line, there are FAIL lines.


## Implementation
* The nodes have incoming and outgoing message lists.
* The network collects and distributes these every round if they are valid (max 1 message to each neighbour in a round)
* The election process follows the Chang and Roberts algorithm, except I decided not to use the FORWARD message type, so the node just sends the same message it received in this case.
* I used sync and locks to ensure that only 1 thread has access to a Node’s variable.
* If a node dies, all of it’s neighbours will remove that node from their neighbour list, and the 2 neighbour the failed node was pointing to (previous, next) are going to point to each other instead.
* Those 2 nodes will remember that their pointers have been changed.
* The nodes are still going to follow the Chang and Roberts algorithm, but if they want to send a message to their next neighbour which isn’t their original next neighbour then they will prefix their message with a “FORWARDTO next“ text.
* When the network finds a message which has to be forwarded, it finds the shortest route to the destination and delivers the message to the next node on the route.
* When a node receives a FORWARDTO message, it just adds it to its outgoing messages, so the message is sent back to the network to find the next node.
* When the next node in the path is the destination, the network removes the forwarding prefix.
* Now the destination node receives the original message (through a number of intermediate nodes), and it continues the same way, as if it was directly sent from the source node.
* Every time a node fails, the graph is checked. If it becomes disconnected the program exits.
* When the network cannot find a route from the source node to the destination node, the election cannot succeed, so the program quits.

