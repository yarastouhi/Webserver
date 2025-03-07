package ca.concordia.server;

import java.net.ServerSocket;
import java.io.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

//create the WebServer class to receive connections on port 5000. Each connection is handled by a master thread that puts the descriptor in a bounded buffer. A pool of worker threads take jobs from this buffer if there are any to handle the connection.
public class WebServer {
    String fileName = "accounts.txt";
    private jobThread[] threadPool = new jobThread[1000];
    public static Account[] accounts = new Account[10];

    //Synchronization structures
    public static Semaphore[] accountLocks = new Semaphore[10];
    public static Semaphore accountsAccess = new Semaphore(1);
    public static final Lock queueLock = new ReentrantLock();
    public static final Condition jobAvailable = queueLock.newCondition();
    
    private void accountsInit(){
        try(BufferedReader br = new BufferedReader(new InputStreamReader(WebServer.class.getClassLoader().getResourceAsStream("accounts.txt")))) {           
            for(int i = 0; i < accounts.length; i++){
                    String line = br.readLine();
                    if (line == null) {
                        break;
                    }
                String[] data = line.split(",");
                int id = Integer.parseInt(data[0].trim());
                int balance = Integer.parseInt(data[1].trim());
                accounts[i] = new Account(balance, id);
            }
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

    private void accountLocksInit(){
        for(int i = 0; i < 10; i++){
            accountLocks[i] = new Semaphore(1);
        }
    }

    private void threadPoolInit(jobQueue jobs){
        for(int i = 0; i < 1000; i++){
            threadPool[i] = new jobThread(jobs);
            threadPool[i].start();
        }
    }

    private void threadPoolRefresh(jobQueue jobs){
        //This will allow our pool to handle more than 1000 clients
        for(int i = 0; i < 1000; i++){
            if(threadPool[i].isAlive() != true){
                threadPool[i] = new jobThread(jobs);
                threadPool[i].start();
            }
        }
    }

    public void start() throws java.io.IOException{
        
        jobQueue jobs = new jobQueue();
        
        accountsInit();
        accountLocksInit();
        threadPoolInit(jobs);
        
        //Create a server socket
        ServerSocket serverSocket = new ServerSocket(5000);
        while(true){
            System.out.println("Waiting for a client to connect...");
            //Accept a connection from a client
            threadPoolRefresh(jobs);
            jobs.push(serverSocket.accept());
            System.out.println("New client...");
        }
    }
    
    public static void main(String[] args) {
        //Start the server, if an exception occurs, print the stack trace
        WebServer server = new WebServer();

        try {
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

