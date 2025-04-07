import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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

        System.out.print("Bienvenu!");
        System.out.print("Veuillez entrer l'adresse IP du serveur : ");
        String ipAddress = scanner.nextLine();

        System.out.print("Veuillez entrer le port du serveur : ");
        int port = scanner.nextInt();

        loadPeers();
        loadFiles();
        startServer(ipAddress, port);
    }

        // Charger les pairs depuis le fichier Peers_list.txt
        private static void loadPeers() {
            try (Scanner scanner = new Scanner(new File("Peers_list.txt"))) {
                while (scanner.hasNextLine()) {
                    String peer = scanner.nextLine().trim();
                    if (!peer.isEmpty()) {
                        activePeers.add(peer);
                    }
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
                out = new PrintWriter(clientSocket.getOutputStream(),true);
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
        
        
        private void saveFileList() {
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream("Files_list.txt"), "UTF-8"))) {
                for (Map.Entry<String, String> entry : fileLocations.entrySet()) {
                    writer.println(entry.getKey() + " " + entry.getValue());
                }
            } catch (IOException e) {
                System.out.println("Erreur lors de la mise à jour de Files_list.txt");
            }
        }
        

        private void saveFile(String fileName, Map<Integer, String> parts) {
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream("txt/" + fileName), "UTF-8"))) {
                int offset = 0;
                while (parts.containsKey(offset)) {
                    writer.println(parts.get(offset));
                    offset++;
                }
            } catch (IOException e) {
                System.out.println("Erreur lors de la sauvegarde du fichier : " + e.getMessage());
            }
        }
        
        private void receiveFile() {
            Map<Integer, String> fileParts = new HashMap<>();
            String fileName = null;
        
            while (input.hasNextLine()) {
                String line = input.nextLine();
                if (line.equals("WRITE|END")) {
                    break;
                }
        
                if (line.startsWith("FILE|")) {
                    String[] parts = line.split("\\|", 5);
                    if (parts.length < 5) {
                        out.println("WRITE|ERROR_FORMAT");
                        return;
                    }
        
                    fileName = parts[1];
                    int offset = Integer.parseInt(parts[2]);
                    int last = Integer.parseInt(parts[3]);
                    String content = parts[4];
        
                    fileParts.put(offset, content);
        
                    if (last == 1) {
                        break; // Fin de fichier
                    }
                }
            }
        
            if (fileName != null) {
                saveFile(fileName, fileParts);
                fileLocations.put(fileName, "LOCAL");
                saveFileList();
                out.println("WRITE|SUCCESS");
                System.out.println("Fichier '" + fileName + "' reçu et sauvegardé.");
            }
        }
        

        private void handleWrite(String token) {
            if (!clients.containsKey(token)) {
                out.println("WRITE|UNAUTHORIZED");
                return;
            }
            out.println("WRITE|BEGIN");
            receiveFile(); // Appel pour recevoir les données de fichier
        }
        

        private void handleRead(String token, String fileName) {
            if (!clients.containsKey(token)) {
                out.println("READ|UNAUTHORIZED");
                return;
            }
        
            // Vérifier si le fichier est dans la liste des fichiers
            if (!fileLocations.containsKey(fileName)) {
                out.println("READ|NOT_FOUND");
        
                // Si le fichier n'est pas trouvé localement, vérifier les pairs
                verifRedirectFile(fileName);
                return;
            }
        
            // Si le fichier est local
            String location = fileLocations.get(fileName);
            if (location.equals("LOCAL")) {
                sendFile(fileName); // Envoi direct du fichier
            } else {
                // Si le fichier est distant, rediriger vers le pair
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
        
            try (Scanner fileScanner = new Scanner(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                int offset = 0;
                while (fileScanner.hasNextLine()) {
                    String content = fileScanner.nextLine();
                    while (content.length() > 500) {
                        out.println("FILE|" + fileName + "|" + offset + "|0|" + content.substring(0, 500));
                        content = content.substring(500);
                        offset++;
                    }
                    out.println("FILE|" + fileName + "|" + offset + "|1|" + content);
                    offset++;
                }
                out.println("READ|END");
            } catch (IOException e) {
                out.println("READ|ERROR");
            }
        }
        

        private boolean canConnectToPeer(String peerIp, String peerPort) {
            int port = Integer.parseInt(peerPort); // Convertir le port en entier
    
            try (Socket socket = new Socket()) {
                // Tentative de connexion avec un délai d'attente
                socket.connect(new InetSocketAddress(peerIp, port), 2000); // Timeout de 2 secondes
                
                // Si la connexion réussit
                System.out.println("Connexion réussie au pair : " + peerIp + ":" + port);
                return true;
            } catch (IOException e) {
                // Si la connexion échoue
                System.out.println("Échec de la connexion au pair : " + peerIp + ":" + port + " - " + e.getMessage());
                return false;
            }
        }
        
        private void verifRedirectFile(String fileName) {
                    // Vérification des pairs dans le fichier Peers_list.txt
            System.out.println("Vérification des pairs pour le fichier " + fileName);

            // Recherche du fichier dans les pairs
            for (String peer : activePeers) {
                // Découper l'adresse IP et le port du pair
                String[] parts = peer.split(":");
                if (parts.length == 2) {
                    String peerIp = parts[0];
                    String peerPort = parts[1];

                    // Vérifier si le serveur peut se connecter au pair avant de tenter de lui demander le fichier
                    if (canConnectToPeer(peerIp, peerPort)) {
                        // Si le pair est accessible, rediriger le client vers ce pair pour obtenir le fichier
                        out.println("READ-REDIRECT|" + peerIp + "|" + peerPort + "|");
                        return;  // Une fois la redirection effectuée, sortir de la méthode
                    } else {
                        System.out.println("Le pair " + peerIp + ":" + peerPort + " n'est pas accessible.");
                    }
                }
            }

            // Si aucun pair n'est accessible, renvoyer un message d'erreur
            out.println("READ|NOT_FOUND_PEER");
        }
    }
}


