# JavaMiniSockets
Hello, this library was designed as a part for a college project, designed and coded on my own as the final assignment required to graduate.
This project may be updated and improved upon in the future but I don't recommed it's use unless it's for learning purposes. 

# What is JavaMiniSockets?
Java Mini Sockets is an experimental library to support local connectivity using Asynchronous Socket Channels. This library includes a class called AsynchronousServer,
which is a hybrid between a server and a client, and a class called AsynchronousClient that can connect to AsynchronousServer instances. Both classes can send messages
between each other in the form of objects as long as they implement the Serializable interface and can be controlled by the user using any class that implements the
included ServerMessageHandler or ClientMessageHandler interfaces. These interfaces provide essential methods that will run everytime an important event happens, such as
connections opening or closing, messages received or the client's or servers being ready for connections.

These classes are implemented using a series of threads but they ensure that the messages will always be procesed in the same single thread.

# What can JavaMiniSockets do?
1. Create simple Server/Client networks in your local network.
2. Manage messages between a Server and it's clients, the server can broadcast a message if wanted.
3. Manage network events such as connections or disconnections.
4. Create your own messages using custom classes implementing the Serializable interface.

# How do you use JavaMiniSockets?

To use this library you will need to create at least one AsynchronousServer and its handler and one AsynchronousClient and its handler.





Then, start the server first and then connect the client or else the Client's connection method will fail. Once the server is ready you may connect the client.




Now create your own message class, then send it in any direction.


You are done!


