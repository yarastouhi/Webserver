package ca.concordia.server;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URLDecoder;
import java.io.FileWriter;


class jobThread extends Thread{

    private static String success = "false";
    private jobQueue jobs;

    //Constructor to see jobs object
    public jobThread(jobQueue jobs){
        this.jobs = jobs;
    }

    @Override
    public void run(){
        try{
            handleRequest();
        }
        catch(IOException e){
            System.err.println("Bad handle");
            e.printStackTrace();
        };

    }

    private static void handleGetRequest(OutputStream out) throws IOException {
        // Respond with a basic HTML page
        System.out.println("Handling GET request");
        String response = "HTTP/1.1 200 OK\r\n\r\n" +
                "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "<title>Concordia Transfers</title>\n" +
                "</head>\n" +
                "<body>\n" +
                "\n" +
                "<h1>Welcome to Concordia Transfers</h1>\n" +
                "<p>Select the account and amount to transfer</p>\n" +
                "\n" +
                "<form action=\"/submit\" method=\"post\">\n" +
                "        <label for=\"account\">Account:</label>\n" +
                "        <input type=\"text\" id=\"account\" name=\"account\"><br><br>\n" +
                "\n" +
                "        <label for=\"value\">Value:</label>\n" +
                "        <input type=\"text\" id=\"value\" name=\"value\"><br><br>\n" +
                "\n" +
                "        <label for=\"toAccount\">To Account:</label>\n" +
                "        <input type=\"text\" id=\"toAccount\" name=\"toAccount\"><br><br>\n" +
                "\n" +
                "        <label for=\"toValue\">To Value:</label>\n" +
                "        <input type=\"text\" id=\"toValue\" name=\"toValue\"><br><br>\n" +
                "\n" +
                "        <input type=\"submit\" value=\"Submit\">\n" +
                "    </form>\n" +
                "</body>\n" +
                "</html>\n";
        out.write(response.getBytes());
        out.flush();
    }

    private static void handlePostRequest(BufferedReader in, OutputStream out) throws IOException {
        System.out.println("Handling post request");
        StringBuilder requestBody = new StringBuilder();
        int contentLength = 0;
        String line;

        // Read headers to get content length
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            if (line.startsWith("Content-Length")) {
                contentLength = Integer.parseInt(line.substring(line.indexOf(' ') + 1));
            }
        }

        // Read the request body based on content length
        for (int i = 0; i < contentLength; i++) {
            requestBody.append((char) in.read());
        }

        System.out.println(requestBody.toString());
        // Parse the request body as URL-encoded parameters
        String[] params = requestBody.toString().split("&");
        String account = null, value = null, toAccount = null, toValue = null;

        for (String param : params) {
            String[] parts = param.split("=");
            if (parts.length == 2) {
                String key = URLDecoder.decode(parts[0], "UTF-8");
                String val = URLDecoder.decode(parts[1], "UTF-8");

                switch (key) {
                    case "account":
                        account = val;
                        break;
                    case "value":
                        value = val;
                        break;
                    case "toAccount":
                        toAccount = val;
                        break;
                    case "toValue":
                        toValue = val;
                        break;
                }
            }
        }

        try{success = transferFunds(account, toAccount, value);}
        catch(InterruptedException ie){
            System.out.println("Transfer exception");
            ie.printStackTrace();
        }
        
        String responseContent;
        if(success == "false"){
        // Create the response
            responseContent = "<html><body><h1>Thank you for using Concordia Transfers</h1>" +
                "<h2>The following transaction could not be completed due to insufficient funds:</h2>"+
                "<p>Account: " + WebServer.accounts[0].getBalance() + "</p>" +
                "<p>Account: " + success + "</p>" +
                "<p>Account: " + account + "</p>" +
                "<p>Value: " + value + "</p>" +
                "<p>To Account: " + toAccount + "</p>" +
                "<p>To Value: " + toValue + "</p>" +
                "</body></html>";

        
        }
        else if (success == "nonexistent"){
            responseContent = "<html><body><h1>Thank you for using Concordia Transfers</h1>" +
                "<h2>The following transaction could not be completed because one of these accounts do not exist:</h2>"+
                "<p>Account: " + WebServer.accounts[0].getBalance() + "</p>" +
                "<p>Account: " + success + "</p>" +
                "<p>Account: " + account + "</p>" +
                "<p>Value: " + value + "</p>" +
                "<p>To Account: " + toAccount + "</p>" +
                "<p>To Value: " + toValue + "</p>" +
                "</body></html>";

        
        }
        else{
            responseContent = "<html><body><h1>Thank you for using Concordia Transfers</h1>" +
                "<h2>Received Form Inputs:</h2>"+
                "<p>Account: " + WebServer.accounts[0].getBalance() + "</p>" +
                "<p>Account: " + success + "</p>" +
                "<p>Account: " + account + "</p>" +
                "<p>Value: " + value + "</p>" +
                "<p>To Account: " + toAccount + "</p>" +
                "<p>To Value: " + toValue + "</p>" +
                "</body></html>";
        }
        // Respond with the received form inputs
        String response = "HTTP/1.1 200 OK\r\n" +
        "Content-Length: " + responseContent.length() + "\r\n" +
        "Content-Type: text/html\r\n\r\n" +
        responseContent;
        out.write(response.getBytes());
        out.flush();
    }

    public void handleRequest() throws IOException {
        
        Socket clientSocket = jobs.pull();
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        OutputStream out = clientSocket.getOutputStream();

        String request = in.readLine();
        if (request != null) {
            if (request.startsWith("GET")) {
                // Handle GET request
                handleGetRequest(out);
                
            } else if (request.startsWith("POST")) {
                // Handle POST request
                handlePostRequest(in, out);
            }
        }
        in.close();
        out.close();
        clientSocket.close();
    }

    public static String transferFunds(String account1, String account2, String transfer) throws InterruptedException{
        int account = Integer.parseInt(account1);
        int toAccount = Integer.parseInt(account2);
        int value = Integer.parseInt(transfer);
        int lock1 = -1;
        int lock2 = -1;

        //This semaphore prevents deadlock by letting each thread secure two locks, without entering a cycle
        WebServer.accountsAccess.acquire();        
        
        for(int i = 0; i < WebServer.accounts.length; i++){
            if(WebServer.accounts[i] == null){break;}
            if(WebServer.accounts[i].getId() == account){
                lock1 = i;
                //If first account doesn't have enough money, transaction fails
                if(WebServer.accounts[lock1].getBalance() < value){WebServer.accountsAccess.release(); return "false";}
                WebServer.accountLocks[lock1].acquire();

            }
            if(WebServer.accounts[i].getId() == toAccount){
                lock2 = i;
                WebServer.accountLocks[lock2].acquire();

            }
        }
        WebServer.accountsAccess.release();

        //If one of the locks isn't set by the end of the loop, the account doesn't exist. Release locks if they were acquired
        if (lock1 == -1 || lock2 == -1) {
            if (lock1 != -1) WebServer.accountLocks[lock1].release();
            if (lock2 != -1) WebServer.accountLocks[lock2].release();
            return "nonexistent";
        }

        //Begin logging transaction
        try{FileWriter out = new FileWriter("transactionlog.txt", true);
            out.write("Initial:\nAccount " + account1 + ": $" + WebServer.accounts[lock1].getBalance());
            out.write("\nAccount " + account2 + ": $" + WebServer.accounts[lock2].getBalance() + "\n");
            out.close();
        }
        catch(IOException e){
            e.printStackTrace();
        }

        WebServer.accounts[lock1].withdraw(value);
        WebServer.accounts[lock2].deposit(value);

        //Finish logging transaction
        try{FileWriter out = new FileWriter("transactionlog.txt", true);
            out.write("Amount transferred: " + transfer);
            out.write("\nPost-transaction:\nAccount " + account1 + ": $" + WebServer.accounts[lock1].getBalance());
            out.write("\nAccount " + account2 + ": $" + WebServer.accounts[lock2].getBalance() + "\n\n");
            out.close();
        }
        catch(IOException e){
            e.printStackTrace();
        }
        
        WebServer.accountLocks[lock2].release();
        WebServer.accountLocks[lock1].release();
        return "true";
    }



}