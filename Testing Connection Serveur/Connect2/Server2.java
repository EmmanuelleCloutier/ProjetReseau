import java.io.IOException;
import java.net.ServerSocket;

public class Server2 {
    public static void main(String[] args) {
        // Port sur lequel Serveur 2 écoute
        int port = 12346;

        try {
            // Créer un ServerSocket pour écouter sur le port
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Serveur 2 est en attente de connexions sur le port " + port + "...");
            while (true) {
                // Accepter une connexion entrante (serveur 1 ou d'autres clients)
                serverSocket.accept();
                System.out.println("Connexion reçue !");
            }
        } catch (IOException e) {
            System.out.println("Erreur : Impossible de démarrer Serveur 2 sur le port " + port);
            e.printStackTrace();
        }
    }
}
