import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private static String serverIp;
    private static int serverPort;
    private static String clientToken = null;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Veuillez entrer l'adresse IP du serveur: ");
        serverIp = scanner.nextLine();

        System.out.print("Veuillez entrer le port du serveur: ");
        serverPort = scanner.nextInt();
        scanner.nextLine(); 

        try (Socket socket = new Socket(serverIp, serverPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            System.out.println("Connecté au serveur");
            
            // Enregistrement du client
            out.println("REGISTER");
            String response = in.readLine();
            if (response.startsWith("REGISTERED|")) {
                clientToken = response.split("\\|")[1];
                System.out.println("Enregistré avec le token: " + clientToken);
            } else {
                System.out.println("Erreur d'enregistrement: " + response);
                return;
            }

            while (true) {
                System.out.println("\nOptions :");
                System.out.println("1 - Lister les fichiers");
                System.out.println("2 - Envoyer un fichier");
                System.out.println("3 - Quitter");
                System.out.print("Votre choix: ");
                int choice = scanner.nextInt();
                scanner.nextLine();

                switch (choice) {
                    case 1:
                        listFiles(out, in);
                        break;
                    case 2:
                        sendFile(out, in, scanner);
                        break;
                    case 3:
                        System.out.println("Déconnexion...");
                        return;
                    default:
                        System.out.println("Choix invalide");
                }
            }
        } catch (IOException e) {
            System.out.println("Erreur de connexion au serveur : " + e.getMessage());
        }
    }

    private static void listFiles(PrintWriter out, BufferedReader in) throws IOException {
        out.println("LS | " + clientToken);
        String response = in.readLine();
        if (response.startsWith("LS | ok")) {
            System.out.println("Fichiers disponibles :");
            String[] files = response.split(" \| ");
            for (int i = 2; i < files.length; i++) {
                System.out.println("- " + files[i]);
            }
        } else {
            System.out.println("Erreur : " + response);
        }
    }

    private static void sendFile(PrintWriter out, BufferedReader in, Scanner scanner) throws IOException {
        System.out.print("Entrez le chemin du fichier à envoyer : ");
        String filePath = scanner.nextLine();
        File file = new File(filePath);

        if (!file.exists()) {
            System.out.println("Le fichier n'existe pas.");
            return;
        }

        out.println("WRITE | " + clientToken);
        String response = in.readLine();
        if (!response.equals("WRITE | BEGIN")) {
            System.out.println("Erreur d'autorisation.");
            return;
        }

        out.println(file.getName());
        try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                out.println(line);
            }
        }
        out.println("WRITE | END");

        response = in.readLine();
        if (response.equals("WRITE | SUCCESS")) {
            System.out.println("Fichier envoyé avec succès.");
        } else {
            System.out.println("Erreur lors de l'envoi du fichier.");
        }
    }
}
