import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID; 

public class Server {

    private static final Map<String, String> clients = new HashMap<>();
    private static final Map<String, String> fileLocations = new HashMap<>();
    private static final List<String> activePeers = new ArrayList<>();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Veuillez entrer l'adresse IP du serveur : ");
        String ipAddress = scanner.nextLine();

        System.out.print("Veuillez entrer le port du serveur : ");
        int port = scanner.nextInt();

        loadPeers();
        loadFiles();
        checkActivePeers();
        startServer(ipAddress, port);
    }

    // Charger les pairs
    private static void loadPeers() {
        try (Scanner scanner = new Scanner(new File("Peers_list.txt"))) {
            while (scanner.hasNextLine()) {
                String peer = scanner.nextLine().trim();
                if (!peer.isEmpty()) activePeers.add(peer);
            }
        } catch (FileNotFoundException e) {
            System.out.println("Fichier Peers_list.txt introuvable.");
        }
    }

    // Charger les fichiers
    private static void loadFiles() {
        try (Scanner scanner = new Scanner(new File("Files_list.txt"))) {
            while (scanner.hasNextLine()) {
                String[] parts = scanner.nextLine().trim().split(" ");
                String fileName = parts[0];
                String location = (parts.length > 1) ? parts[1] : "LOCAL";
                fileLocations.put(fileName, location);
            }
        } catch (FileNotFoundException e) {
            System.out.println("Fichier Files_list.txt introuvable.");
        }
    }

    // Vérifier les pairs actifs
    private static void checkActivePeers() {
        Iterator<String> iterator = activePeers.iterator();
        while (iterator.hasNext()) {
            String peer = iterator.next();
            String[] parts = peer.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            try (Socket socket = new Socket(ip, port)) {
                System.out.println("Peer actif : " + peer);
            } catch (IOException e) {
                System.out.println("Peer inactif : " + peer);
                iterator.remove();
            }
        }
    }

    // Lancement du serveur
    private static void startServer(String host, int port) {
        try (ServerSocket serverSocket = new ServerSocket(port, 5, InetAddress.getByName(host))) {
            System.out.println("Serveur démarré sur " + host + ":" + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.out.println("Erreur lors du démarrage : " + e.getMessage());
        }
    }

    // Gestion des clients
    private static class ClientHandler extends Thread {
        private final Socket clientSocket;
        private PrintWriter out;
        private Scanner input;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                input = new Scanner(clientSocket.getInputStream());

                System.out.println("Nouveau client connecté : " + clientSocket.getInetAddress());

                while (input.hasNextLine()) {
                    String message = input.nextLine();
                    System.out.println("Message reçu : " + message);
                    handleMessage(message);
                }

                input.close();
                clientSocket.close();
                System.out.println("Client déconnecté.");

            } catch (IOException e) {
                System.out.println("Erreur avec un client : " + e.getMessage());
            }
        }

        private void handleMessage(String message) {
            String[] parts = message.split("\\|");
            switch (parts[0]) {
                case "REGISTER":
                    handleRegister();
                    break;
                case "LS":
                    handleLS(parts[1]);
                    break;
                case "WRITE":
                    handleWrite(parts[1]);
                    break;
                case "READ":
                    handleRead(parts[1], parts[2]);
                    break;
                default:
                    out.println("Message inconnu : " + message);
            }
        }

        private void handleRegister() {
            String token = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            clients.put(token, clientSocket.getInetAddress().toString());
            out.println("REGISTERED|" + token + "|");
            System.out.println("Client enregistré avec le jeton : " + token);
        }

        private void handleLS(String token) {
            if (!clients.containsKey(token)) {
                out.println("LS|UNAUTHORIZED");
                return;
            }
            out.println("LS|" + fileLocations.keySet().size() + "|" + String.join("|", fileLocations.keySet()) + "|");
        }

        private void handleWrite(String token) {
            if (!clients.containsKey(token)) {
                out.println("WRITE|UNAUTHORIZED");
                return;
            }
            out.println("WRITE|BEGIN");
        }

        private void handleRead(String token, String fileName) {
            if (!clients.containsKey(token)) {
                out.println("READ|UNAUTHORIZED");
                return;
            }

            if (!fileLocations.containsKey(fileName)) {
                out.println("READ|NOT_FOUND");
                return;
            }

            String location = fileLocations.get(fileName);
            if (location.equals("LOCAL")) {
                sendFile(fileName);
            } else {
                String[] parts = location.split(":");
                String peerIp = parts[0];
                String peerPort = parts[1];
                out.println("READ-REDIRECT|" + peerIp + "|" + peerPort + "|" + token + "|");
            }
        }

        private void sendFile(String fileName) {
            File file = new File("txt/" + fileName);
            if (!file.exists()) {
                out.println("READ|ERROR");
                return;
            }

            try (Scanner fileScanner = new Scanner(file)) {
                int offset = 0;
                while (fileScanner.hasNextLine()) {
                    String content = fileScanner.nextLine();
                    while (content.length() > 500) {
                        out.println("FILE|" + fileName + "|" + offset + "|0|" + content.substring(0, 500));
                        content = content.substring(500);
                        offset++;
                    }
                    out.println("FILE|" + fileName + "|" + offset + "|1|" + content);
                }
                out.println("READ|END");
            } catch (FileNotFoundException e) {
                out.println("READ|ERROR");
            }
        }
    }
}
