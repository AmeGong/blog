package Question.WaitAndSleep;

import java.util.stream.Stream;

public class Test {
    private final static Object lock = new Object();

    public static void main(String[] args) {
        Stream.of("Thread1", "Thread2").forEach(n -> new Thread(n) {
            public void run() {
                Test.testWait();
            }
        }.start());
    }

    private static void testWait() {
        synchronized (lock) {
            try{
                System.out.println(Thread.currentThread().getName() + " is running");
                lock.wait(10000);
                System.out.println(Thread.currentThread().getName() + " waiting finished");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}