package Questions.SynchronizedKeyWord;

public class TestSyn {
    public static synchronized void staticFunction() {
        System.out.println("static function");
        System.out.println("static function sleep...");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.out.println("static function wake...");
    }

    public synchronized void function() {
        System.out.println("member function");
        System.out.println("member function sleep...");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.out.println("member function wake...");
    }

    public void flattenFunction() {
        System.out.println("Flatten function...");
    }

    public static void main(String[] args) {
        // new Thread(() -> {
        //     synchronized (TestSyn.class) {
        //         System.out.println("class TestSyn is locked...");
        //         try {
        //             Thread.sleep(1000);
        //         } catch (InterruptedException e) {
        //             // TODO Auto-generated catch block
        //             e.printStackTrace();
        //         }
        //         System.out.println("class TestSyn is unlocked...");
        //     }
        // }).start(); 

        // new Thread(() -> {
        //     TestSyn.staticFunction();
        // }).start();
        // new Thread(() -> {
        //     // TestSyn.staticFunction();
        //     new TestSyn().function();
        // }).start();

        TestSyn a = new TestSyn();
        TestSyn b = new TestSyn();

        new Thread(()->{
            synchronized(a) {
                a.flattenFunction();
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            // a.function();
        }).start();
        new Thread(()->{
            a.function();
        }).start();
        // new Thread(() -> {
        //     synchronized (a) {
        //         System.out.println("object a is locked...");
        //         try {
        //             Thread.sleep(1000);
        //         } catch (InterruptedException e) {
        //             // TODO Auto-generated catch block
        //             e.printStackTrace();
        //         }
        //         System.out.println("object a is unlocked...");
        //     }
        // }).start(); 
        // new Thread(() -> {
        //     synchronized (b) {
        //         System.out.println("object b is locked...");
        //         try {
        //             Thread.sleep(1000);
        //         } catch (InterruptedException e) {
        //             // TODO Auto-generated catch block
        //             e.printStackTrace();
        //         }
        //         System.out.println("object b is unlocked...");
        //     }
        // }).start(); 
    }
}
