import java.io.*;
import java.net.*;
import java.util.*;

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

    private static void startServer(String host, int port) {
        try (ServerSocket serverSocket = new ServerSocket(port, 5, InetAddress.getByName(host))) {
            System.out.println("Serveur démarré sur " + host + ":" + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            System.out.println("Erreur lors du démarrage : " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            Scanner input = new Scanner(clientSocket.getInputStream())
        ) {
            System.out.println("Nouveau client connecté : " + clientSocket.getInetAddress());

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

    private static void handleMessage(String message, Socket clientSocket, Scanner input, PrintWriter out) {
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

    private static void handleRegister(Socket clientSocket, PrintWriter out) {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        clients.put(token, clientSocket.getInetAddress().toString());
        out.println("REGISTERED|" + token + "|");
        System.out.println("Client enregistré avec le jeton : " + token);
    }

    private static void handleLS(String token, PrintWriter out) {
        if (!clients.containsKey(token)) {
            out.println("LS|UNAUTHORIZED");
            return;
        }
        out.println("LS|" + fileLocations.keySet().size() + "|" + String.join("|", fileLocations.keySet()) + "|");
    }

    private static void saveFileList() {
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream("Files_list.txt"), "UTF-8"))) {
            for (Map.Entry<String, String> entry : fileLocations.entrySet()) {
                writer.println(entry.getKey() + " " + entry.getValue());
            }
        } catch (IOException e) {
            System.out.println("Erreur lors de la mise à jour de Files_list.txt");
        }
    }

    private static void saveFile(String fileName, Map<Integer, String> parts) {
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

    private static void receiveFile(Scanner input, PrintWriter out) {
        Map<Integer, String> fileParts = new HashMap<>();
        String fileName = null;

        while (input.hasNextLine()) {
            String line = input.nextLine();
            if (line.equals("WRITE|END")) break;

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
                if (last == 1) break;
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

    private static void handleWrite(String token, Scanner input, PrintWriter out) {
        if (!clients.containsKey(token)) {
            out.println("WRITE|UNAUTHORIZED");
            return;
        }
        out.println("WRITE|BEGIN");
        receiveFile(input, out);
    }

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

    private static void sendFile(String fileName, PrintWriter out) {
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

    private static boolean canConnectToPeer(String peerIp, String peerPort) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(peerIp, Integer.parseInt(peerPort)), 2000);
            System.out.println("Connexion réussie au pair : " + peerIp + ":" + peerPort);
            return true;
        } catch (IOException e) {
            System.out.println("Échec de la connexion au pair : " + peerIp + ":" + peerPort + " - " + e.getMessage());
            return false;
        }
    }

    private static void verifRedirectFile(String fileName, PrintWriter out) {
        System.out.println("Vérification des pairs pour le fichier " + fileName);
        for (String peer : activePeers) {
            String[] parts = peer.split(":");
            if (parts.length == 2 && canConnectToPeer(parts[0], parts[1])) {
                out.println("READ-REDIRECT|" + parts[0] + "|" + parts[1] + "|");
                return;
            }
        }
        out.println("READ|ERROR_NO_PEER");
    }
}
