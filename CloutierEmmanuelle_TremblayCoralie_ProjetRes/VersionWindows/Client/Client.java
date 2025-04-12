import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private static String serverIp;
    private static int serverPort;
    private static String clientToken = null;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        //demander à l'utilisateur adresseIP et le port du serveur a se connecter 
        System.out.print("Veuillez entrer l'adresse IP du serveur : ");
        serverIp = scanner.nextLine();
        System.out.print("Veuillez entrer le port du serveur : ");
        serverPort = scanner.nextInt();
        scanner.nextLine(); 

        try {
            //connexion au serveur principal
            connectToServer(scanner);
        } catch (IOException e) {
            System.out.println("Erreur de connexion au serveur : " + e.getMessage());
        }
    }

    //fonction qui gère la connexion et les interaction avec le serveur
    private static void connectToServer(Scanner scanner) throws IOException {
        try (Socket socket = new Socket(serverIp, serverPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true); //pour envoyer des messages
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) { //pour recevoir des messages

            System.out.println("Connecté au serveur.");

            //boucle principale pour les commandes
            while (true) {
                System.out.print("> ");
                String command = scanner.nextLine().trim();

                //si la commande contient un |, on l'envoie telle quelle
                if (command.contains("|")) {
                    out.println(command);

                    //lecture de la réponsse du serveur
                    String response;
                    while ((response = in.readLine()) != null) {
                        System.out.println(response);
                        //on s'arrête si on reçoit une fin de bloc
                        if (response.equals("WRITE|BEGIN") || response.equals("READ|BEGIN") ||
                                response.equals("WRITE|END") || response.equals("READ|END") ||
                                response.startsWith("LS|") || response.startsWith("REGISTERED|") ||
                                response.equals("WRITE|SUCCESS") || response.startsWith("READ|UNAUTHORIZED")) {
                            break;
                        }

                        //si redirection reçue, gérer la reconnexion au nouveau serveur
                        if (response.startsWith("READ-REDIRECT|")) {
                            String[] parts = response.split("\\|");
                            String newIp = parts[1];
                            int newPort = Integer.parseInt(parts[2]);
                            System.out.println("Redirigé vers " + newIp + ":" + newPort);

                            //mise a jour des informations de connexion
                            serverIp = newIp;
                            serverPort = newPort;

                            //reconnexion au nouveau serveur
                            connectToServer(scanner);
                            return; //sortir de la méthode pour éviter la boucle infinie
                        }
                    }
                } else if (command.equalsIgnoreCase("register")) {
                    register(out, in);
                } else if (command.equalsIgnoreCase("ls")) {
                    listFiles(out, in);
                } else if (command.startsWith("write ")) {
                    String filePath = command.substring(6).trim();
                    sendFile(out, in, filePath);
                } else if (command.startsWith("read ")) {
                    String fileName = command.substring(5).trim();
                    readFile(out, in, fileName);
                } else if (command.equalsIgnoreCase("quit")) {
                    System.out.println("Déconnexion...");
                    break;
                } else {
                    System.out.println("Commande inconnue. Commandes disponibles : register, ls, write <chemin>, read <fichier>, quit");
                }
            }

        }
    }

    //enregistrer le client
    private static void register(PrintWriter out, BufferedReader in) throws IOException {
        out.println("REGISTER");
        String response = in.readLine();
        if (response.startsWith("REGISTERED|")) {
            clientToken = response.split("\\|")[1];
            System.out.println("Enregistré avec le token : " + clientToken);
        } else {
            System.out.println("Erreur d'enregistrement : " + response);
        }
    }

    //lister les fichiers
    private static void listFiles(PrintWriter out, BufferedReader in) throws IOException {
        if (clientToken == null) {
            System.out.println("Veuillez vous enregistrer d'abord.");
            return;
        }

        out.println("LS|" + clientToken);
        String response = in.readLine();
        if (response.startsWith("LS|")) {
            String[] parts = response.split("\\|");
            if (parts[1].equals("UNAUTHORIZED")) {
                System.out.println("Accès non autorisé.");
                return;
            }

            System.out.println("Fichiers disponibles :");
            for (int i = 2; i < parts.length; i++) {
                System.out.println("- " + parts[i]);
            }
        } else {
            System.out.println("Erreur lors de la récupération de la liste des fichiers.");
        }
    }

    //envoyer un fichier au serveur
    private static void sendFile(PrintWriter out, BufferedReader in, String filePath) throws IOException {
        if (clientToken == null) {
            System.out.println("Veuillez vous enregistrer d'abord.");
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("Le fichier n'existe pas.");
            return;
        }

        out.println("WRITE|" + clientToken);
        String response = in.readLine();
        if (!response.equals("WRITE|BEGIN")) {
            System.out.println("Erreur d'autorisation.");
            return;
        }

        out.println(file.getName());
        try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                while (line.length() > 500) {
                    out.println(line.substring(0, 500));
                    line = line.substring(500);
                }
                out.println(line);
                System.out.println("Envoi de ligne: " + line);
            }
        }
        out.println("WRITE|END");

        //verifie la réponse du serveur
        response = in.readLine();
        if (response.equals("WRITE|SUCCESS")) {
            System.out.println("Fichier envoyé avec succès.");
        } else {
            System.out.println("Erreur lors de l'envoi du fichier.");
        }
    }

    //lire un fichier 
    private static void readFile(PrintWriter out, BufferedReader in, String fileName) throws IOException {
        if (clientToken == null) {
            System.out.println("Veuillez vous enregistrer d'abord.");
            return;
        }

        out.println("READ|" + clientToken + "|" + fileName);
        String response = in.readLine();

        if (response.startsWith("READ|UNAUTHORIZED")) {
            System.out.println("Accès non autorisé.");
        } else if (response.startsWith("READ-REDIRECT|")) {
            String[] parts = response.split("\\|");
            String newIp = parts[1];
            int newPort = Integer.parseInt(parts[2]);
            System.out.println("Redirigé vers " + newIp + ":" + newPort);

            //mise a jour de la connexion au nouveau serveur
            serverIp = newIp;
            serverPort = newPort;
            connectToServer(new Scanner(System.in)); 

        } else {
            //affiche le contenu du fichier
            System.out.println("Contenu du fichier :");
            while (true) {
                response = in.readLine();
                if (response.equals("READ|END")) break;
                System.out.println(response);
            }
        }
    }
}
