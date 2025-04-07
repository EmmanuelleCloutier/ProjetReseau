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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID; 

public class Server {

    //maps pour stocker les clients enregistrées et les fichiers 
    private static final Map<String, String> clients = new HashMap<>();
    private static final Map<String, String> fileLocations = new HashMap<>();
    private static final List<String> activePeers = new ArrayList<>();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in); //pour les entrées de l'utilisateur

        //recevoir adresseIP et port du serveur
        System.out.print("Bienvenu!");
        System.out.print("Veuillez entrer l'adresse IP du serveur : ");
        String ipAddress = scanner.nextLine();

        System.out.print("Veuillez entrer le port du serveur : ");
        int port = scanner.nextInt();

        //charge le contenu dans le Peers_list et Files_list
        loadPeers();
        loadFiles();
        startServer(ipAddress, port);
    }

    // Charger les peers 
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
                fileLocations.put(fileName, location);//stockage du fichier avec sa localisation 
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
                Socket clientSocket = serverSocket.accept(); //attente dune connexion client
                new ClientHandler(clientSocket).start(); //lancement dun thread pour gerer le client
            }
        } catch (IOException e) {
            System.out.println("Erreur lors du démarrage : " + e.getMessage());
        }
    }

    // Gestion des clients
    private static class ClientHandler extends Thread {
        private final Socket clientSocket; //socket client
        private PrintWriter out; //pour envoyer des messages
        private Scanner input; //pour recevoir des messages 

        //constructeur
        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        //quand le thread commence
        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(),true);
                input = new Scanner(clientSocket.getInputStream());

                System.out.println("Nouveau client connecté : " + clientSocket.getInetAddress());

                while (input.hasNextLine()) {
                    String message = input.nextLine();
                    System.out.println("Message reçu : " + message);
                    handleMessage(message); //gestion du message reçu
                }

                input.close();
                clientSocket.close();
                System.out.println("Client déconnecté.");

            } catch (IOException e) {
                System.out.println("Erreur avec un client : " + e.getMessage());
            }
        }

        //gestion des messages du client
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

        //enrigistement du client et génération dun token unique
        private void handleRegister() {
            String token = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            clients.put(token, clientSocket.getInetAddress().toString());
            out.println("REGISTERED|" + token + "|");
            System.out.println("Client enregistré avec le jeton : " + token);
        }

        //réponse pour le LS
        private void handleLS(String token) {
            if (!clients.containsKey(token)) {
                out.println("LS|UNAUTHORIZED");
                return;
            }
        
            out.println("LS|" + fileLocations.keySet().size() + "|" + String.join("|", fileLocations.keySet()) + "|");
        }
        
        //-------------------------- WRITE ----------------------------------------------
        //sauvegarde des fichiers dans le fichier FILES_list.txt
        private void saveFileList() {
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream("Files_list.txt"), "UTF-8"))) {
                for (Map.Entry<String, String> entry : fileLocations.entrySet()) {
                    writer.println(entry.getKey() + " " + entry.getValue());
                }
            } catch (IOException e) {
                System.out.println("Erreur lors de la mise à jour de Files_list.txt");
            }
        }
        
        //enrigistres les données dun fichier par des fragments 
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
        
        //recépection des données du WRITE
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
                        break; 
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
        

        //commande WRITE envoye par un client 
        private void handleWrite(String token) {
            if (!clients.containsKey(token)) {
                out.println("WRITE|UNAUTHORIZED");
                return;
            }
            out.println("WRITE|BEGIN");
            receiveFile(); //reception du fichier
        }
        
        //-------------------------------------- READ -------------------------------------------------------
        private void handleRead(String token, String fileName) {
            if (!clients.containsKey(token)) {
                out.println("READ|UNAUTHORIZED");
                return;
            }
        
            //vérifier si le fichier est dans la liste des fichiers
            if (!fileLocations.containsKey(fileName)) {
                out.println("READ|NOT_FOUND");
        
                //si le fichier n'est pas trouvé localement, vérifier les pairs
                verifRedirectFile(fileName);
                return;
            }
        
            //si le fichier est local
            String location = fileLocations.get(fileName);
            if (location.equals("LOCAL")) {
                sendFile(fileName); // Envoi direct du fichier
            } else {
                //rediriger vers le pair
                String[] parts = location.split(":");
                String peerIp = parts[0];
                String peerPort = parts[1];
                out.println("READ-REDIRECT|" + peerIp + "|" + peerPort + "|" + token + "|");
            }
        }
        
        //envoie un fichier ligne par ligne 
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
        
        //test la connection avec le pair selon la demande 
        private boolean canConnectToPeer(String peerIp, String peerPort) {
            int port = Integer.parseInt(peerPort); // Convertir le port en entier
    
            try (Socket socket = new Socket()) {
                //tente la connection avec un délai (lautre serveur doit etre active d'avance)
                socket.connect(new InetSocketAddress(peerIp, port), 2000); // Timeout de 2 secondes
                
                //connexion réussit
                System.out.println("Connexion réussie au pair : " + peerIp + ":" + port);
                return true;
            } catch (IOException e) {
                //connexion échoue
                System.out.println("Échec de la connexion au pair : " + peerIp + ":" + port + " - " + e.getMessage());
                return false;
            }
        }
        
        //verifie la présence dun fichier chez les autres peers et rediriges si on peut
        private void verifRedirectFile(String fileName) {
                    //vérification des peers dans le fichier Peers_list.txt
            System.out.println("Vérification des pairs pour le fichier " + fileName);

            //recherche du fichier dans les peers
            for (String peer : activePeers) {
                //découper l'adresse IP et le port du peer
                String[] parts = peer.split(":");
                if (parts.length == 2) {
                    String peerIp = parts[0];
                    String peerPort = parts[1];

                    //vérifier si le serveur peut se connecter au peer avant de tenter de lui demander le fichier
                    if (canConnectToPeer(peerIp, peerPort)) {
                        //si le peer est accessible, rediriger le client vers ce peer pour obtenir le fichier
                        out.println("READ-REDIRECT|" + peerIp + "|" + peerPort + "|");
                        return;  // Une fois la redirection effectuée, sortir de la méthode
                    } else {
                        System.out.println("Le pair " + peerIp + ":" + peerPort + " n'est pas accessible.");
                    }
                }
            }

            // Si aucun peer n'est accessible, renvoyer un message d'erreur
            out.println("READ|NOT_FOUND_PEER");
        }
    }
}


