import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.HasMap; 
import java.util.Scanner;
import java.util.UUID; 

public class Server {

    private static List<String> peersActif = new ArrayList<>();
    private static HashMap<String, String> clients = new HashMap<>();

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

    public static void GetFileslist(){
        try{
            File file = new File("Files_List.txt");
            Scanner reader = new Scanner(file);

            while(reader.hasNextLine()){
                String data = reader.nextLine();
                System.out.println(data); 
            }
            reader.close();
        } catch(FileNotFoundException e){
            System.out.println("An error occured.");
            e.printStackTrace();
        }
    }

    public static void Start(String host, int port){
        try (ServerSocket serverSocket = new ServerSocket(port, 5, InetAddress.getByName(host))) {
                System.out.println("Serveur started on " + host + ":" + port);

            //Thread pour handle les autres connection server et client 
            while (true){
                try{
                    Socket socket = serverSocket.accept();
                    SetPeersActif(); 
                    GetFileslist(); 
                    new HandleClient(socket).start();

                }catch (IOException e) {
                System.out.println("Error de connexion");
            }}

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
                        handleWrite(parts[1]);
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
    
        private void handleRegister(String clientIp) {
            String token = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            clients.put(token, clientIp);
            out.println("REGISTERED|" + token);
            System.out.println("Client enregistré avec le token : " + token);
        }
    
        private void handleLS(String token) {
            if (!clients.containsKey(token)) {
                out.println("LS | UNAUTHORIZED");
                return;
            }
    
            File file = new File("Files_List.txt");
            if (!file.exists()) {
                out.println("LS | ERROR | File list not found");
                return;
            }
    
            try {
                Scanner reader = new Scanner(file);
                StringBuilder fileList = new StringBuilder("LS | ok");
    
                while (reader.hasNextLine()) {
                    fileList.append(" | ").append(reader.nextLine());
                }
                reader.close();
    
                out.println(fileList.toString());
            } catch (FileNotFoundException e) {
                out.println("LS | ERROR | Cannot read file list");
            }
        }
    
        private void handleWrite(String token) {
            if (!clients.containsKey(token)) {
                out.println("WRITE | UNAUTHORIZED");
                return;
            }
        
            out.println("WRITE | BEGIN");
        
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
                        if (line.equals("WRITE | END")) {
                            break;
                        }
                        fileWriter.println(line);
                    }
                }
        
                try (PrintWriter listWriter = new PrintWriter(new java.io.FileWriter("Files_List.txt", true))) {
                    listWriter.println(fileName);
                }
        
                out.println("WRITE | SUCCESS");
        
            } catch (IOException e) {
                out.println("WRITE | ERROR");
                e.printStackTrace();
            }
        }
        

        private void handleUpdateFilesList(String message) {
            String[] parts = message.split(" \\| ");
            if (parts.length != 3) {
                return;
            }
        
            String fileName = parts[1];
            String serverInfo = parts[2];
        
            try {
                PrintWriter listWriter = new PrintWriter(new java.io.FileWriter("Files_List.txt", true));
                listWriter.println(fileName + " | " + serverInfo);
                listWriter.close();
                System.out.println("Fichier mis à jour : " + fileName + " (ajouté par " + serverInfo + ")");
            } catch (IOException e) {
                System.out.println("Erreur lors de la mise à jour de Files_List.txt");
            }
        }
        
    }
    
}

