import java.io.IOException;
import java.net.Socket;

public class Server1 {
    public static void main(String[] args) {
        // Le port sur lequel Serveur 2 écoute (assurez-vous qu'il correspond à celui de Serveur 2)
        String server2Address = "localhost"; // L'adresse de Serveur 2
        int server2Port = 12346; // Le port de Serveur 2

        try {
            // Tente de se connecter à Serveur 2 sur son port
            System.out.println("Serveur 1 tente de se connecter à Serveur 2 sur le port " + server2Port + "...");
            Socket socket = new Socket(server2Address, server2Port);
            System.out.println("Serveur 1 a réussi à se connecter à Serveur 2 !");
            socket.close();
        } catch (IOException e) {
            // Si la connexion échoue, cela signifie que Serveur 2 n'est pas actif
            System.out.println("Erreur : Serveur 2 n'est pas actif sur le port " + server2Port);
            e.printStackTrace();
        }
    }
}
