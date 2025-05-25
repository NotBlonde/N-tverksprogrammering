// Importerar nödvändiga Java-bibliotek
import javax.swing.*;           // För GUI
import java.awt.*;              // Layout och komponenter
import java.awt.event.*;        // För knappar och fönsterhändelser
import java.net.*;              // För nätverkskommunikation
import java.io.*;               // För strömmar och undantag
import java.util.*;             // För listor och hashset

// Huvudklassen är ett grafiskt fönster (JFrame)
public class ChatClientG extends JFrame {

    // GUI-komponenter
    private JTextArea chatArea;                      // Visar chattens meddelanden
    private JTextField inputField;                   // Fält där man skriver sitt meddelande
    private DefaultListModel<String> userListModel;  // Modell för användarlistan
    private MulticastSocket socket;                  // UDP-socket för multicast
    private InetAddress group;                       // Multicast-gruppens IP-adress
    private final int PORT = 4446;                   // Port som alla klienter använder
    private final String MULTICAST_IP = "230.0.0.0"; // Multicast IP-adress (lokalt)
    private String userName;                         // Användarnamn för denna klient
    private boolean running = true;                  // Används för att stänga tråden
    private Set<String> currentUsers = new HashSet<>(); // Håller koll på aktiva användare

    // Konstruktor – körs när en ny chattklient startar
    public ChatClientG() throws IOException {
        // Be användaren ange ett namn
        userName = JOptionPane.showInputDialog(this, "Ange ditt namn:");
        if (userName == null || userName.trim().isEmpty()) System.exit(0);

        // ===== GUI-byggnation =====
        setTitle("Gruppchatt – " + userName);
        chatArea = new JTextArea();            // Skapad chattfönstret
        chatArea.setEditable(false);
        inputField = new JTextField();         // Inmatningsfält för nya meddelanden
        userListModel = new DefaultListModel<>();
        JList<String> userList = new JList<>(userListModel); // Lista med användarnamn
        JButton disconnectButton = new JButton("Koppla ner");

        // Panel med användarlistan och koppla ner-knappen
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(new JLabel("Anslutna användare:"), BorderLayout.NORTH);
        rightPanel.add(new JScrollPane(userList), BorderLayout.CENTER);
        rightPanel.add(disconnectButton, BorderLayout.SOUTH);

        // Lägg till komponenter i huvudfönstret
        add(new JScrollPane(chatArea), BorderLayout.CENTER);
        add(inputField, BorderLayout.SOUTH);
        add(rightPanel, BorderLayout.EAST);

        setSize(600, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);

        // ===== Nätverksinställningar =====
        group = InetAddress.getByName(MULTICAST_IP); // IP-adress för gruppen
        socket = new MulticastSocket(PORT);          // Lyssna på angiven port
        socket.setReuseAddress(true);                // Tillåt flera klienter på samma port
        socket.joinGroup(group);                     // Gå med i multicastgruppen

        // Skicka JOIN-meddelande så att andra klienter vet att vi gått med
        sendMessage("JOIN:" + userName);

        // Starta en tråd som lyssnar på inkommande meddelanden
        new Thread(this::receiveMessages).start();

        // När användaren trycker Enter i inputfältet
        inputField.addActionListener(e -> {
            String msg = inputField.getText().trim();
            if (!msg.isEmpty()) {
                sendMessage("MSG:" + userName + ":" + msg); // Skicka meddelandet
                inputField.setText(""); // Töm textfältet
            }
        });

        // Koppla ner-knappen – skicka LEAVE och stäng
        disconnectButton.addActionListener(e -> {
            sendMessage("LEAVE:" + userName);
            shutdown();
        });

        // Fönsterhantering (t.ex. klick på X)
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                sendMessage("LEAVE:" + userName);
                shutdown();
            }
        });
    }

    // Stänger klienten och nätverksanslutningen
    private void shutdown() {
        running = false;
        try {
            socket.leaveGroup(group); // Lämna multicastgruppen
            socket.close();           // Stäng socketen
        } catch (IOException e) {
            e.printStackTrace();
        }
        dispose(); // Stäng fönstret
    }

    // Skickar ett meddelande som byte-array via multicast
    private void sendMessage(String message) {
        try {
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, group, PORT);
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Tråd som lyssnar på inkommande meddelanden
    private void receiveMessages() {
        byte[] buffer = new byte[512];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength());
                SwingUtilities.invokeLater(() -> handleMessage(msg));
            } catch (IOException e) {
                if (running) e.printStackTrace();
                break;
            }
        }
    }

    // Tolkar meddelanden och uppdaterar gränssnittet
    private void handleMessage(String msg) {
        if (msg.startsWith("JOIN:")) {
            String newUser = msg.substring(5);
            if (!currentUsers.contains(newUser)) {
                currentUsers.add(newUser);
                userListModel.addElement(newUser);
                chatArea.append("* " + newUser + " har gått med i chatten *\n");
            }
        } else if (msg.startsWith("LEAVE:")) {
            String user = msg.substring(6);
            if (currentUsers.contains(user)) {
                currentUsers.remove(user);
                userListModel.removeElement(user);
                chatArea.append("* " + user + " har lämnat chatten *\n");
            }
        } else if (msg.startsWith("MSG:")) {
            String[] parts = msg.split(":", 3);
            if (parts.length == 3) {
                chatArea.append(parts[1] + ": " + parts[2] + "\n");
            }
        }
    }

    // Huvudmetod – frågar hur många fönster man vill öppna och startar dem
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String input = JOptionPane.showInputDialog(null, "Hur många chattfönster vill du öppna?", "Starta Chatt", JOptionPane.QUESTION_MESSAGE);
            if (input == null || input.trim().isEmpty()) return;

            try {
                int antal = Integer.parseInt(input.trim());
                for (int i = 0; i < antal; i++) {
                    new ChatClientG(); // Skapar nya instanser
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Ogiltigt tal!");
            }
        });
    }
}
