package ca.concordia.server;

import java.net.Socket;

public class jobQueue {
    private int jobCount;
    public Socket[] queue = new Socket[1000];

    public int getCount(){
        return jobCount;
    }

    public void setcount(int count){
        jobCount = count;
    }

    public Socket pull(){
        WebServer.queueLock.lock(); // Lock the queue
        try {
            while (jobCount == 0) {
                //System.out.println("No jobs available. Waiting...");
                WebServer.jobAvailable.await(); // Wait until a job is available
            }

            Socket job = queue[0]; // Pop the first job
            for (int i = 1; i < queue.length; i++) {
                queue[i - 1] = queue[i]; // Shift jobs left
                queue[i] = null; // Clear the last slot
            }
            jobCount--; // Decrement the job count
            //System.out.println("Job pulled from queue. Total jobs: " + jobCount);
            return job;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore interrupt status
            return null;
        } finally {
            WebServer.queueLock.unlock(); // Unlock the queue
        }
    }

    public void push(Socket socket) {
        WebServer.queueLock.lock(); // Lock the queue
        try {
            for (int i = 0; i < queue.length; i++) {
                if (queue[i] == null) {
                    queue[i] = socket; // Push the socket to the array
                    jobCount++; // Increment the job count
                    //System.out.println("Job added to queue at index: " + i + ". Total jobs: " + jobCount);
                    WebServer.jobAvailable.signal(); // Notify one waiting thread that a job is available
                    break;
                }
            }
        } finally {
            WebServer.queueLock.unlock(); // Unlock the queue
        }
    }

}
