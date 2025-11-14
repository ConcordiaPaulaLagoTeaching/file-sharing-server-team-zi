package ca.concordia.server;
import ca.concordia.filesystem.FileSystemManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class FileServer {

    private FileSystemManager fsManager;
    private int port;
    public FileServer(int port, String fileSystemName, int totalSize){
        // Initialize the FileSystemManager
        FileSystemManager fsManager = new FileSystemManager(fileSystemName,
                10*128 );
        this.fsManager = fsManager;
        this.port = port;
    }

    public void start(){
        try (ServerSocket serverSocket = new ServerSocket(12345)) {
            System.out.println("Server started. Listening on port 12345...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Handling client: " + clientSocket);
                try (
                        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
                ) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("Received from client: " + line);
                        String[] parts = line.split(" ");
                        String command = parts[0].toUpperCase();

                        switch (command) {
                            case "CREATE":
                                if (parts.length < 2) {
                                    writer.println("ERROR: Filename required for CREATE command.");
                                    writer.flush();
                                    break;
                                }
                                try {
                                    fsManager.createFile(parts[1]);
                                    writer.println("SUCCESS: File '" + parts[1] + "' created.");
                                } catch (Exception e) {
                                    writer.println("ERROR: " + e.getMessage());
                                }
                                writer.flush();
                                break;
                            case "LIST":
                                try{
                                    String[] files = fsManager.listFiles();
                                    StringBuilder response = new StringBuilder();
                                    for (String file : files) {
                                        response.append(file).append(" ");
                                    }
                                    String fileList = response.toString().trim();
                                    writer.println(fileList.isEmpty() ? "No files found." : "FILES: " + fileList);
                                } catch (Exception e){
                                    writer.println("ERROR: " + e.getMessage());
                                }
                                writer.flush();
                                break;
                            case "WRITE":
                                if (parts.length < 2) {
                                    writer.println("ERROR: Filename missing.");
                                    writer.flush();
                                    break;
                                }
                                try {
                                    StringBuilder message = new StringBuilder();
                                    for (int i = 2; i < parts.length; i++) {
                                        message.append(parts[i]);
                                        if (i < parts.length - 1)
                                            message.append(" "); //adding space after each words except the last one
                                    }
                                    byte[] bytes = message.toString().getBytes();
                                    fsManager.writeFile(parts[1], bytes);
                                    writer.println("SUCCESS: File '" + parts[1] + "' written.");
                                } catch (Exception e) {
                                    writer.println("ERROR: " + e.getMessage());
                                }
                                writer.flush();
                                break;
                            case "READ":
                                if (parts.length < 2) {
                                    writer.println("ERROR: Filename missing.");
                                    writer.flush();
                                    break;
                                }
                                try {
                                    byte[] data = fsManager.readFile(parts[1]);
                                    writer.println("CONTENTS: " + new String(data));
                                } catch (Exception e) {
                                    writer.println("ERROR: " + e.getMessage());
                                }
                                writer.flush();
                                break;
                            case "DELETE":
                                if (parts.length < 2) {
                                    writer.println("ERROR: Filename missing.");
                                    writer.flush();
                                    break;
                                }
                                try {
                                    fsManager.deleteFile(parts[1]);
                                    writer.println("SUCCESS: File '" + parts[1] + "' deleted.");
                                } catch (Exception e) {
                                    writer.println("ERROR: " + e.getMessage());
                                }
                                writer.flush();
                                break;
                            case "QUIT":
                                writer.println("SUCCESS: Disconnecting.");
                                writer.flush();
                                return;
                            default:
                                writer.println("ERROR: Unknown command.");
                                writer.flush();
                                break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        clientSocket.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        }
    }

}
