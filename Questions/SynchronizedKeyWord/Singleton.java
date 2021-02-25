package Questions.SynchronizedKeyWord;

public class Singleton {
    private volatile static Singleton singleton;
    private Singleton() {};

    public synchronized static Singleton getInstance() {
        if (singleton == null) {
            synchronized(Singleton.class) {
                if (singleton == null) {
                    singleton = new Singleton();
                }
            }
        }
        return singleton;
    }

    public static Singleton getInstance2() {
        synchronized(Singleton.class) {
            if (singleton == null) {
                synchronized(Singleton.class) {
                    if (singleton == null) {
                        singleton = new Singleton();
                    }
                }
            }
            return singleton;
        }
    }
    
}
