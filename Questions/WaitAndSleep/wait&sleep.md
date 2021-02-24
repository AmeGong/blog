# 所属的类不同
wait是Object的方法，sleep是Thread的方法
# 处理锁的方式不同
sleep不会释放锁，wait会释放锁。
```
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


输出:
------------------------
Thread1 is running
Thread2 is running
Thread1 waiting finished
Thread2 waiting finished
```
在synchronized锁的情况下Thread1在wait的时候，Thread2也可以执行，说明wait方法会释放锁。但是线程Thread.sleep()就不会释放锁。

# wait方法可以与notify和notifyAll配合使用。
一个线程可以唤醒处于wait的线程，当wait线程拿到锁时会自动往下执行。
```
public class Test2 {
    private final static Object lock = new Object();

    private static void testWait() {
        synchronized(lock){
            try{
                System.out.println(Thread.currentThread().getName()+" is waiting...");
                lock.wait();
                System.out.println(Thread.currentThread().getName()+" has been awaken");
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


输出：
-----------------------------
Thread-0 is waiting...
notify the waiting object...
Thread-0 has been awaken
```

# sleep不依赖同步，但是wait方式依赖同步。
在Test.testWait方法中，如果没有synchronized(lock)语句，则会出错。如果没有synchroized锁，在当前线程调用wait方法后，该线程就处于wait状态，如果在其他线程中有notify方法，则wait线程又会被立刻唤醒执行，这样做不到wait和notify互相配合。