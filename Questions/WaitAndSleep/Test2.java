package Questions.WaitAndSleep;

public class Test2 {
    private final static Object lock = new Object();

    private static void testWait() {
        synchronized(lock){
            try{
                System.out.println("Thread"+Thread.currentThread().getName()+" is waiting...");
                lock.wait();
                System.out.println("Thread"+Thread.currentThread().getName()+" has been awaken");
            } catch (InterruptedException e){
                e.printStackTrace();
            }
        }
    }

    private static void notifyWait() {
        synchronized(lock){
            try{
                Thread.sleep(1000);
                lock.notify();
                System.out.println("notify the waiting object...");
            } catch (InterruptedException e){
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        new Thread() {
            public void run() {Test2.testWait();}
        }.start();
        new Thread() {
            public void run() {Test2.notifyWait();}
        }.start();
    }
    
}
