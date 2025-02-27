import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID; 

public class Server {

    private static List<String> peersActif = new ArrayList<>();
    private static HashMap<String, String> clients = new HashMap<>();

    //pour avoir des connexion de serveur quand demande 
    public static boolean ServerStart(String ipAddress, String port) {
        int IPort = -1;

        // String -> int
        try{
            IPort = Integer.parseInt(port);
        }
        catch (NumberFormatException ex){
            System.out.println("Cannot convert : String -> int ");
            ex.printStackTrace();
            return false;
        }

        if(IPort == -1){
            return false;
        }

        try (Socket socket = new Socket()){
            socket.connect(new java.net.InetSocketAddress(ipAddress, IPort)); 
            try {
                socket.getOutputStream().write("Bienvenue sur le serveur !\n".getBytes());
                socket.getOutputStream().flush();
            } catch (IOException e) {
                System.out.println("Impossible d'envoyer le message au serveur " + ipAddress + ":" + port);
            }
            return true; 
        }
        catch (IOException e) {
            return false;  
        }
    }

    public static void SetPeersActif() {
        // Crée une HashMap pour stocker les pairs avec adresse IP et port
        Map<String, Integer> peersActif = new HashMap<>();

        try {
            File Peers = new File("Peers_list.txt");
            Scanner reader = new Scanner(Peers);

            // Parcours chaque ligne du fichier
            while (reader.hasNextLine()) {
                String data = reader.nextLine();
                System.out.println("Lecture de la ligne: " + data);

                // Extraire l'adresse IP et le port de la chaîne
                String ip = data.substring(0, data.indexOf(':'));
                int port = Integer.parseInt(data.substring(data.indexOf(':') + 1));

                // Ajoute l'adresse IP et le port dans la HashMap
                peersActif.put(ip, port);
                System.out.println("Ajouté au dictionnaire: " + ip + " -> " + port);
            }
            reader.close();

        } catch (FileNotFoundException e) {
            System.out.println("Un problème est survenu.");
            e.printStackTrace();
        }

        // Affiche le dictionnaire des pairs
        System.out.println("Dictionnaire des pairs : " + peersActif);
    }

    

    //connexion des clients !thread principal 
    public static void ClientStart(String host, int port){
        try (ServerSocket serverSocket = new ServerSocket(port, 5, InetAddress.getByName(host))) {
                System.out.println("Serveur started on " + host + ":" + port);

            //thread principal pour les clients  
            while (true){
                try{
                    Socket socket = serverSocket.accept();
                    new HandleClient(socket).start();
                }catch (IOException e) {
                System.out.println("Error de connexion");
            }}

        } catch (IOException e) {
            System.out.println("Error on start : " + e.getMessage());
        }
    }

    //faire un start pour les serveurs 
    
    public static void main(String[] args) {
        
        //Demander utilisateur marquer IP et port 
        Scanner scanner = new Scanner(System.in);

        System.out.print("Veuillez entrer l'adresse IP du serveur de la machine: ");
        String ipAddress = scanner.nextLine();

        System.out.print("Veuillez entrer le port du serveur: ");
        int port = scanner.nextInt();

        System.out.println(ipAddress + " :" + port);
        //SetPeersActif(); 
        ClientStart(ipAddress, port);
    }

    private static class HandleClient extends Thread {
        private Socket clientSocket;
        private PrintWriter out;
        private Scanner input;
    
        public HandleClient(Socket socket){
            this.clientSocket = socket;
            try {
                this.out = new PrintWriter(clientSocket.getOutputStream(), true);
                this.input = new Scanner(clientSocket.getInputStream());
            } catch (IOException e) {
                System.out.println("Erreur lors de l'initialisation : " + e.getMessage());
            }
        }
    
        public void run(){
            try {
                System.out.println("Nouveau client connecté : " + clientSocket.getInetAddress());
    
                while (input.hasNextLine()) {
                    String message = input.nextLine();
                    System.out.println("Message reçu du client : " + message);
    
                    String[] parts = message.split(" \\| ");
                    if (parts.length == 1 && parts[0].equalsIgnoreCase("REGISTER")) {
                        handleRegister(clientSocket.getInetAddress().toString());
                    } else if (parts.length == 2 && parts[0].equalsIgnoreCase("LS")) {
                        handleLS(parts[1]);
                    } else if (parts.length == 2 && parts[0].equalsIgnoreCase("WRITE")) {
                        handleWrite(parts[1]);  // Utilise la version complète de handleWrite
                    } else if (parts.length == 2 && parts[0].equalsIgnoreCase("READ")) {
                        handleRead(parts[1]);
                    } else {
                        out.println("Message inconnu: " + message);
                    }
                }
    
                input.close();
                clientSocket.close();
                System.out.println("Client déconnecté.");
            } catch (IOException e) {
                System.out.println("Erreur avec un client : " + e.getMessage());
            }
        }

        private void handleRegister(String clientAddress) {
            String token = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            clients.put(token, clientAddress);
            out.println("REGISTERED|" + token + "|");
            System.out.println("Client enregistré : " + clientAddress + " avec le jeton " + token);
        }

        // Vérifier si le jeton est valide et envoyer la liste des fichiers
        private void handleLS(String token) {
            if (!clients.containsKey(token)) {
                out.println("LS|UNAUTHORIZED");
                return;
            }

            File filesList = new File("Files_list.txt");
            List<String> fileNames = new ArrayList<>();
            try (Scanner scanner = new Scanner(filesList)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    fileNames.add(line);
                }
            } catch (FileNotFoundException e) {
                out.println("LS|ERROR");
                return;
            }

            out.println("LS|" + fileNames.size() + "|" + String.join("|", fileNames) + "|");
        }

        private void handleWrite(String token) {
            if (!clients.containsKey(token)) {
                out.println("WRITE|UNAUTHORIZED");
                return;
            }

            out.println("WRITE|BEGIN");

            try {
                String fileName = input.nextLine();
                File dir = new File("txt");
                if (!dir.exists()) {
                    dir.mkdir();
                }

                File file = new File("txt/" + fileName);
                try (PrintWriter fileWriter = new PrintWriter(file)) {
                    while (input.hasNextLine()) {
                        String line = input.nextLine();
                        if (line.equals("WRITE|END")) {
                            break;
                        }
                        fileWriter.println(line);
                    }
                }

                try (PrintWriter listWriter = new PrintWriter(new java.io.FileWriter("Files_List.txt", true))) {
                    listWriter.println(fileName);
                }

                out.println("WRITE|SUCCESS");

            } catch (IOException e) {
                out.println("WRITE|ERROR");
                e.printStackTrace(); 
            }
        }

        private void handleRead(String token) {
            if (!clients.containsKey(token)) {
                out.println("READ|UNAUTHORIZED");
                return;
            }
            out.println("READ|NOT_IMPLEMENTED");
        }

        
    }
    
}

