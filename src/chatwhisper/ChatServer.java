package chatwhisper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;

/**
 * A multithreaded chat room server. When a client connects, the
 * server requests a screen name by sending the client the
 * text "SUBMITNAME" and keeps requesting a name until
 * a unique one is received. After a client submits a unique
 * name, the server acknowledges with "NAMEACCEPTED". Then
 * all messages from that client will be broadcast to all other
 * clients that have submitted a unique screen name. The
 * broadcast messages are prefixed with "MESSAGE ".
 *
 * Because this is just a teaching example to illustrate a simple
 * chat server, there are a few features that have been left out.
 * Two are very useful and belong in production code:
 *
 * 1. The protocol should be enhanced so that the client can
 * send clean disconnect messages to the server.
 *
 * 2. The server should do some logging.
 */
public class ChatServer {

    /**
     * The port that the server listens on.
     */
    private static final int PORT = 9001;

    /**
     * The set of all names of clients in the chat room. Maintained
     * so that we can check that new clients are not registering names
     * already in use.
     */
    private static HashSet<String> names = new HashSet<String>();

    /**
     * The set of all the print writers for all the clients. This
     * set is kept so we can easily broadcast messages.
     */
    private static HashSet<PrintWriter> writers = new HashSet<PrintWriter>();
    // Added synchronization object
    private static Object writersLock = new Object();

    /**
     * The application main method, which just listens on a port and
     * spawns handler threads.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("The chat server is running.");
        ServerSocket listener = new ServerSocket(PORT);
        try {
            while (true) {
                // Spawn a new handler thread for each incoming client connection
                new Handler(listener.accept()).start();
            }
        } finally {
            listener.close();
        }
    }

    /**
     * A handler thread class. Handlers are spawned from the listening
     * loop and are responsible for dealing with a single client
     * and broadcasting its messages.
     */
    private static class Handler extends Thread {
        private String name;
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        /**
         * Constructs a handler thread, storing away the socket.
         * All the interesting work is done in the run method.
         */
        public Handler(Socket socket) {
            this.socket = socket;
        }

        // Inside the Handler class, modify the broadcastEntranceExitMessage method
        private static void broadcastEntranceExitMessage(String message) {
            // Broadcast entrance or exit messages to all connected clients
            synchronized (writersLock) {
                for (PrintWriter writer : writers) {
                    writer.println("MESSAGE " + message);
                    writer.flush(); // Ensure the message is sent immediately
                }
            }
        }

        // Use a HashMap to store the mapping between client names and their respective PrintWriter instances
        private static HashMap<String, PrintWriter> nameWriterMap = new HashMap<>();

        /**
         * Services this thread's client by repeatedly requesting a
         * screen name until a unique one has been submitted, then
         * acknowledges the name and registers the output stream for
         * the client in a global set. After that, it repeatedly gets inputs and
         * broadcasts them.
         */
        public void run() {
            try {

                // Create character streams for the socket.
                in = new BufferedReader(new InputStreamReader(
                        socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Request a name from this client. Keep requesting until
                // a name is submitted that is not already used. Note that
                // checking for the existence of a name and adding the name
                // must be done while locking the set of names.
                while (true) {
                    out.println("SUBMITNAME");
                    name = in.readLine();
                    if (name == null) {
                        return;
                    }
                    synchronized (names) {
                        if (!names.contains(name)) {
                            names.add(name);
                            break;
                        }
                    }
                }

                // Now that a successful name has been chosen, add the
                // socket's print writer to the set of all writers and
                // store the mapping between the client's name and its PrintWriter.
                synchronized (writersLock) {
                    out.println("NAMEACCEPTED");
                    writers.add(out);
                    nameWriterMap.put(name, out);
                }

                // Broadcast entrance message
                broadcastEntranceExitMessage("---" + name + " Enters---");

                // Accept messages from this client and broadcast them.
                // Ignore other clients that cannot be broadcasted to.
                while (true) {
                    String input = in.readLine();
                    if (input == null) {
                        return;
                    }

                    if (input.startsWith("<WHISPER>")) {
                        // Extract target user and whisper message
                        String[] whisperParts = input.split(" ", 3);
                        if (whisperParts.length == 3) {
                            String targetUser = whisperParts[1];
                            String whisperMessage = whisperParts[2];

                            // Find the PrintWriter of the target user from the map and send the whisper message
                            synchronized (writersLock) {
                                PrintWriter targetWriter = nameWriterMap.get(targetUser);
                                if (targetWriter != null) {
                                    // Send the whisper message only to the target user
                                    if (!targetWriter.equals(out)) {
                                        targetWriter.println("MESSAGE <Whisper from " + name + "> " + whisperMessage);
                                        targetWriter.flush(); // Ensure the message is sent immediately

                                        // Notify the sender as well
                                        out.println("MESSAGE <Whisper to " + targetUser + "> " + whisperMessage);
                                        out.flush(); // Ensure the message is sent immediately
                                    }
                                }
                            }
                        }
                    } else {
                        // Broadcast regular messages
                        synchronized (writersLock) {
                            for (PrintWriter writer : writers) {
                                writer.println("MESSAGE " + name + ": " + input.substring(8));
                                writer.flush(); // Ensure the message is sent immediately
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println(e);
            } finally { // Part that runs unconditionally
                // This client is going down! Remove its name and its print
                // writer from the sets and close its socket.
                if (name != null) {
                    names.remove(name);
                }
                if (out != null) {
                    writers.remove(out);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                }

                // Remove the client's name from the map
                nameWriterMap.remove(name);

                // Broadcast exit message
                broadcastEntranceExitMessage("---" + name + " Exits---");
            }
        }
    }
}
