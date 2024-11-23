package chatwhisper;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * A simple Swing-based client for the chat server. Graphically, it is a frame
 * with a text field for entering messages and a text area to see the whole
 * dialog.
 *
 * The client follows the Chat Protocol as follows: When the server
 * sends "SUBMITNAME," the client replies with the desired screen name. The
 * server will keep sending "SUBMITNAME" requests as long as the client submits
 * screen names that are already in use. When the server sends a line beginning
 * with "NAMEACCEPTED," the client is now allowed to start sending arbitrary strings
 * to the server, which will be broadcast to all connected chatters. When the server
 * sends a line beginning with "MESSAGE," all characters following this string should
 * be displayed in its message area.
 */
public class Client3 {

    BufferedReader in;
    PrintWriter out;
    JFrame frame = new JFrame("Chatter");
    JTextField textField = new JTextField(40);
    JTextArea messageArea = new JTextArea(8, 40);

    private String serverAddress;
    private int serverPort;

    /**
     * Reads server information from the configuration file or sets default values.
     */
    private void readServerInfo() {
        // Read server information from a configuration file, if available
        File configFile = new File("serverinfo.dat");

        try {
            if (configFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(configFile));
                String line = reader.readLine();
                if (line != null) {
                    // Parse server information from the file
                    String[] serverInfo = line.split(" ");
                    if (serverInfo.length == 2) {
                        serverAddress = serverInfo[0];
                        serverPort = Integer.parseInt(serverInfo[1]);
                    }
                }
                reader.close();
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace(); // Handle or log the exception
        }

        // Set default values if not read from the file
        if (serverAddress == null || serverPort <= 0) {
            serverAddress = "localhost";
            serverPort = 1234;
        }
    }

    /**
     * Constructs the client by laying out the GUI and registering a listener
     * with the text field so that pressing Return in the listener sends the
     * text field contents to the server. Note that the text field is initially
     * NOT editable and only becomes editable AFTER the client receives the
     * NAMEACCEPTED message from the server.
     */
    public Client3() {

        // Layout GUI components
        textField.setEditable(false);
        messageArea.setEditable(false);
        frame.getContentPane().add(textField, "North");
        frame.getContentPane().add(new JScrollPane(messageArea), "Center");
        frame.pack();

        JButton whisperButton; // Declare the whisperButton as a class variable

        // Add a new JButton for whisper functionality
        whisperButton = new JButton("Whisper");
        frame.getContentPane().add(whisperButton, "East");

        // Add Whisper ActionListener
        whisperButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // Get the target user for whispering
                String targetUser = JOptionPane.showInputDialog(frame, "Enter the target user for whisper:");

                // Prompt for whisper message
                String whisperMessage = JOptionPane.showInputDialog(frame, "Enter the whisper message:");

                // Send the whisper command to the server
                out.println("<WHISPER> " + targetUser + " " + whisperMessage);
                out.flush();  // Ensure the message is sent immediately

                // Debug check message
                System.out.println("Whisper button clicked. Sending whisper message to " + targetUser + ": " + whisperMessage);
            }
        });

        // Add Listeners
        textField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // Check if the message is a whisper command
                String message = textField.getText();
                if (message.startsWith("<WHISPER>")) {
                    out.println(message);
                } else {
                    // Check if the message is a direct message to a user
                    if (message.matches("^<([^/]+)/>(.+)$")) {
                        // Extract target user and whisper message
                        String[] parts = message.split("/>");
                        String targetUser = parts[0].substring(1);
                        String whisperMessage = parts[1].trim();

                        // Send the whisper command to the server
                        out.println("<WHISPER> " + targetUser + " " + whisperMessage);
                    } else {
                        // Send a regular message
                        out.println("MESSAGE " + message);
                    }
                }
                textField.setText("");
            }
        });
    }

    /**
     * Prompt for and return the address of the server.
     */
    private String getServerAddress() {
        readServerInfo();
        return serverAddress;
    }

    /**
     * Prompt for and return the desired screen name.
     */
    private String getName() {
        return JOptionPane.showInputDialog(frame, "Choose a screen name:", "Screen name selection",
                JOptionPane.PLAIN_MESSAGE);
    }

    /**
     * Connects to the server then enters the processing loop.
     */
    private void run() throws IOException {

        // Make connection and initialize streams
        String serverAddress = getServerAddress();
        Socket socket = new Socket(serverAddress, 9001);
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Process all messages from the server, according to the protocol.
            while (true) {
                String line = in.readLine();
                if (line == null) {
                    return;
                }

                SwingUtilities.invokeLater(() -> {
                    synchronized (messageArea) {
                        if (line.startsWith("SUBMITNAME")) {
                            // Prompt the user to choose a screen name
                            out.println(getName());
                        } else if (line.startsWith("NAMEACCEPTED")) {
                            // Enable text field for entering messages after the name is accepted
                            textField.setEditable(true);
                        } else if (line.startsWith("MESSAGE")) {
                            // Display regular messages in the message area
                            String message = line.substring(8);
                            messageArea.append(message + "\n");

                            // Check for entrance and exit messages
                            if (message.startsWith("---") && message.endsWith("---")) {
                                // This is an entrance or exit message, display it in a dialog
                                JOptionPane.showMessageDialog(frame, message, "User Status", JOptionPane.INFORMATION_MESSAGE);
                            }
                        }
                    }
                });
            }
        } finally {
            // Close the socket in the finally block to ensure it's always closed
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace(); // Handle or log the exception
                }
            }
        }
    }

    /**
     * Runs the client as an application with a closeable frame.
     */
    public static void main(String[] args) throws Exception {
        Client3 client = new Client3();
        client.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        client.frame.setVisible(true);
        client.run();
    }
}