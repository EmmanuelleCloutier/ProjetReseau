import java.io.*;
import java.net.*;
import java.util.*;

public class Server {

    private static final Map<String, String> clients = new HashMap<>(); //dictionnaire des clients connectés
    private static final Map<String, String> fileLocations = new HashMap<>(); //dictionnaire des fichiers
    private static final List<String> activePeers = new ArrayList<>(); //listes des peers

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in); //lire les entrées de l'utilisateur

        System.out.print("Bienvenu!");
        System.out.print("Veuillez entrer l'adresse IP du serveur : ");
        String ipAddress = scanner.nextLine(); //lecture adresseIP
        System.out.print("Veuillez entrer le port du serveur : ");
        int port = scanner.nextInt(); //lecture du port

        //chargement de la liste des peers et des fichiers
        loadPeers();
        loadFiles();

        startServer(ipAddress, port);
    }

    //lecture du Files_list
    private static void loadPeers() {
        try (Scanner scanner = new Scanner(new File("Peers_list.txt"))) {
            //parcour du fichier ligne par ligne 
            while (scanner.hasNextLine()) {
                String peer = scanner.nextLine().trim(); //récupere l'adresse du peer
                if (!peer.isEmpty()) {
                    activePeers.add(peer); //l'ajoute a la liste 
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("Fichier Peers_list.txt introuvable.");
        }
    }

    //lecture du Peers_list
    private static void loadFiles() {
        try (Scanner scanner = new Scanner(new File("Files_list.txt"))) {
            while (scanner.hasNextLine()) {
                //separe chaque ligne en deux partie : nom du fichier et emplacement 
                String[] parts = scanner.nextLine().trim().split(" ");
                String fileName = parts[0]; //nom du fichier
                String location = (parts.length > 1) ? parts[1] : "LOCAL"; //emplacement ou local
                fileLocations.put(fileName, location); //ajoute dans le dictionnaire
            }
        } catch (FileNotFoundException e) {
            System.out.println("Fichier Files_list.txt introuvable.");
        }
    }

    //demarrage du serveur à l'adresse et au port spécifiés
    private static void startServer(String host, int port) {
        try (ServerSocket serverSocket = new ServerSocket(port, 5, InetAddress.getByName(host))) {
            System.out.println("Serveur démarré sur " + host + ":" + port);
            while (true) {
                //attente dun client
                Socket clientSocket = serverSocket.accept();

                //traitement du client dans un thread séparé
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            System.out.println("Erreur lors du démarrage : " + e.getMessage());
        }
    }

    //gère un client connecté
    private static void handleClient(Socket clientSocket) {
        try (
            //préparation des flix pour lire et écrire avec le client
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            Scanner input = new Scanner(clientSocket.getInputStream())
        ) {
            System.out.println("Nouveau client connecté : " + clientSocket.getInetAddress());

            //boucle qui lit les lignes envoyées par le client 
            while (input.hasNextLine()) {
                String message = input.nextLine();
                System.out.println("Message reçu : " + message);
                handleMessage(message, clientSocket, input, out);
            }

            System.out.println("Client déconnecté.");
        } catch (IOException e) {
            System.out.println("Erreur avec un client : " + e.getMessage());
        }
    }

    //interprète et traite les messages envoyés par le client
    private static void handleMessage(String message, Socket clientSocket, Scanner input, PrintWriter out) {
        //découpe le message en plusieur parties séparées par des |
        String[] parts = message.split("\\|");

        switch (parts[0]) {
            case "REGISTER":
                handleRegister(clientSocket, out);
                break;
            case "LS":
                handleLS(parts[1], out);
                break;
            case "WRITE":
                handleWrite(parts[1], input, out);
                break;
            case "READ":
                handleRead(parts[1], parts[2], out);
                break;
            default:
                out.println("Message inconnu : " + message);
        }
    }

    //enrigistre un nouveau client et lui attribue un token
    private static void handleRegister(Socket clientSocket, PrintWriter out) {
        //génère un token aléatoire de 20 caractères
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        
        //associe le token a l'adresseIp du client dans le dictionnaire du client
        clients.put(token, clientSocket.getInetAddress().toString());
        out.println("REGISTERED|" + token + "|");
        System.out.println("Client enregistré avec le jeton : " + token);
    }

    //envoie la liste des fichiers au client si le token est ok
    private static void handleLS(String token, PrintWriter out) {
        if (!clients.containsKey(token)) {
            out.println("LS|UNAUTHORIZED");
            return;
        }
        out.println("LS|" + fileLocations.keySet().size() + "|" + String.join("|", fileLocations.keySet()) + "|");
    }

    //sauvegarde la list des fichiers dans Files_list
    private static void saveFileList() {
        //pour pouvoir écrire dans Files_list
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream("Files_list.txt"), "UTF-8"))) {
            //parcout chaque entrée de la map
            for (Map.Entry<String, String> entry : fileLocations.entrySet()) {
                //écrit nom du fichier
                writer.println(entry.getKey() + " " + entry.getValue());
            }
        } catch (IOException e) {
            System.out.println("Erreur lors de la mise à jour de Files_list.txt");
        }
    }

    //sauvegarde le fichier reçu
    private static void saveFile(String fileName, Map<Integer, String> parts) {
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream("txt/" + fileName), "UTF-8"))) {
            int offset = 0; //initialise l'offset a 0

            //écrit chaque partie du fichier dans l'ordre cr4oissant des offesets
            while (parts.containsKey(offset)) {
                writer.println(parts.get(offset)); //écrit le conttenu
                offset++; //passe a la ligne suivante
            }
        } catch (IOException e) {
            System.out.println("Erreur lors de la sauvegarde du fichier : " + e.getMessage());
        }
    }

    //réception d'un fichier ligne par ligne
    private static void receiveFile(Scanner input, PrintWriter out) {
        //création dictionnaire pour stocjer les difféerentes partie du fichier
        Map<Integer, String> fileParts = new HashMap<>();
        String fileName = null; //initialise la varaible pour le nom du fichier

        //tant qu'il y a des lignes a lire du client 
        while (input.hasNextLine()) {
            String line = input.nextLine(); //lit une ligne envoyée par le client 
            if (line.equals("WRITE|END")) break;

            
            if (line.startsWith("FILE|")) {
                //découpe la ligne pour obetnir les difféerentes partie du fichier
                String[] parts = line.split("\\|", 5);
                if (parts.length < 5) {
                    //si le format de la ligne est po correct
                    out.println("WRITE|ERROR_FORMAT");
                    return;
                }

                fileName = parts[1]; //nom du fichier
                int offset = Integer.parseInt(parts[2]); //position de la partie du fichier
                int last = Integer.parseInt(parts[3]); //si c'est la derniere partie du fichier
                String content = parts[4]; //contenu de la partie du fichier

                //stockes la partie du fichier avec l'offset comme clé
                fileParts.put(offset, content); 
                if (last == 1) break;
            }
        }

        //quand le fichier est good
        if (fileName != null) { 
            saveFile(fileName, fileParts); //sauvegarde les parties du fichier dans un fichier local
            fileLocations.put(fileName, "LOCAL"); //enregistre l'emplacement di fichier dans la liste des fichiers
            saveFileList(); //sauvegarde la liste des fichiers
            out.println("WRITE|SUCCESS");
            System.out.println("Fichier '" + fileName + "' reçu et sauvegardé.");
        }
    }

    //traitement de la demande write (upload)
    private static void handleWrite(String token, Scanner input, PrintWriter out) {
        //vérifie si le token est good
        if (!clients.containsKey(token)) {
            out.println("WRITE|UNAUTHORIZED");
            return;
        }
        out.println("WRITE|BEGIN");
        //pour la réception du fichier envoyé par le client
        receiveFile(input, out);
    }

    //traitement de la demande de read (download)
    private static void handleRead(String token, String fileName, PrintWriter out) {
        if (!clients.containsKey(token)) {
            out.println("READ|UNAUTHORIZED");
            return;
        }

        if (!fileLocations.containsKey(fileName)) {
            out.println("READ|NOT_FOUND");
            verifRedirectFile(fileName, out);
            return;
        }

        String location = fileLocations.get(fileName);
        if (location.equals("LOCAL")) {
            sendFile(fileName, out);
        } else {
            String[] parts = location.split(":");
            out.println("READ-REDIRECT|" + parts[0] + "|" + parts[1] + "|" + token + "|");
        }
    }

    //envoie le contenu d'un fichier au client (par frag 500)
    private static void sendFile(String fileName, PrintWriter out) {
        //crée un objet pour le fichier spécifié
        File file = new File("txt/" + fileName);
        //vérifie si le fichier existe
        if (!file.exists()) {
            out.println("READ|ERROR");
            return;
        }

        //lit le fichier ligne par ligne
        try (Scanner fileScanner = new Scanner(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            int offset = 0;
            //tant qu'il y a des lignes
            while (fileScanner.hasNextLine()) {
                String content = fileScanner.nextLine();
                //si le contenu dépasse limite
                while (content.length() > 500) {
                    out.println("FILE|" + fileName + "|" + offset + "|0|" + content.substring(0, 500));
                    content = content.substring(500); //réduit le contenu à envoyer
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

    //teste la connexiona un peer distant 
    private static boolean canConnectToPeer(String peerIp, String peerPort) {
        try (Socket socket = new Socket()) {
            //tente de se connecter au peer dans un délai
            socket.connect(new InetSocketAddress(peerIp, Integer.parseInt(peerPort)), 2000);
            System.out.println("Connexion réussie au pair : " + peerIp + ":" + peerPort);
            return true;
        } catch (IOException e) {
            System.out.println("Échec de la connexion au pair : " + peerIp + ":" + peerPort + " - " + e.getMessage());
            return false;
        }
    }

    //verifie si un autre peer possede le fichier demandé
    private static void verifRedirectFile(String fileName, PrintWriter out) {
        System.out.println("Vérification des pairs pour le fichier " + fileName);

        //parcourt la liste des peers actifs
        for (String peer : activePeers) {
            //divise l'adrese du peer en IP et port
            String[] parts = peer.split(":");
            if (parts.length == 2 && canConnectToPeer(parts[0], parts[1])) {
                out.println("READ-REDIRECT|" + parts[0] + "|" + parts[1] + "|");
                return;
            }
        }
        out.println("READ|ERROR_NO_PEER");
    }
}
