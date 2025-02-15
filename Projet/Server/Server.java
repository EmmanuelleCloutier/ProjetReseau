import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Server {

    private static List<String> peersActif = new ArrayList<>();

    //fonction ping 
    public static boolean VerfiPeer(String ipAddress, String port) {
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
            return true; 
        }
        catch (IOException e) {
            return false;  
        }
    }

    //pour verifier si les autres serveur sont actif
    public static void SetPeersActif(){
        try {
            File Peers = new File("Peers_list.txt");
            Scanner reader = new Scanner(Peers);
            List<String> peersActifTemp = new ArrayList<>();


            while(reader.hasNextLine()) {
                String data = reader.nextLine();
                System.out.println(data);

                if(VerfiPeer(data.substring(0, data.indexOf(':') - 1), data.substring(data.indexOf(':') + 1, data.length()))){
                    peersActifTemp.add(data); 
                    System.out.println(data + " Added to list of peersActifTemp");
                }
                else {
                    System.out.println(data + " Not added to list of peersActifTemp");
                }  
            }

            peersActif = peersActifTemp;
            reader.close();

        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    public static void Start(String host, int port){
        try (ServerSocket serverSocket = new ServerSocket(port, 5, InetAddress.getByName(host))) {
                System.out.println("Serveur started on " + host + ":" + port);

        } catch (IOException e) {
            System.out.println("Error on start : " + e.getMessage());
        }
    }

    
    public static void main(String[] args) {
        
        //Demander utilisateur marquer IP et port 
        Scanner scanner = new Scanner(System.in);

        System.out.print("Veuillez entrer l'adresse IP du serveur de la machine: ");
        String ipAddress = scanner.nextLine();

        System.out.print("Veuillez entrer le port du serveur: ");
        int port = scanner.nextInt();

        System.out.println(ipAddress + " :" + port);
        Start(ipAddress, port);

        /* 
        //Mettre information du server dans fichier Peers
        try{
            FileWriter Peers = new FileWriter("Peers_list.txt", true);
            Peers.write(ipAddress + " :" + port + "\n");
            Peers.close();
            System.out.println("Added au Peers.list");
            
        } 
        catch (IOException e)
        {
            System.out.println("Pas access au fichier.txt");
            e.printStackTrace();
        }
        */

        //SetPeersActif();
    }

    
}

