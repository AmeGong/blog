# synchronized关键字
synchronized关键字可以用在方法上和代码块上，其中方法分为静态方法和成员方法讨论。

## synchronized用于静态方法上
此时静态方法被上锁，当多个线程调用该静态方法时会发生互斥现象。

## synchronized用在非静态方法上
此时非静态方法被上锁，不同的实例对象调用该方法时不会发生互斥现象，但是同一个实例对象调用该方法时会发生互斥现象。

synchronized的静态方法和非静态方法不会发生互斥现象。
```
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

    public static void main(String[] args) {
        new Thread(() -> {
            TestSyn.staticFunction();
        }).start();
        new Thread(() -> {
            new TestSyn().function();
        }).start();
    }
}
输出：
------------------------------
static function
static function sleep...
member function
member function sleep...
static function wake...
member function wake...
```
可以看到，静态方法和实例方法是同时sleep然后同时wake的，这说明synchronized的静态方法和实例方法是非互斥的。

## synchronized(){}用在代码块上
当synchronized(实例对象){}时，该实例对象被锁定，请求同一个实例对象锁的线程会阻塞。
当synchronized(class){}时，该class文件被上锁，该方法与对静态方法上锁相同。
```
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

    public static void main(String[] args) {
        new Thread(() -> {
            synchronized (TestSyn.class) {
                System.out.println("class TestSyn is locked...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                System.out.println("class TestSyn is unlocked...");
            }
        }).start(); 

        new Thread(() -> {
            TestSyn.staticFunction();
        }).start();
        new Thread(() -> {
            // TestSyn.staticFunction();
            new TestSyn().function();
        }).start();
    }
}
输出：
-------------------------
class TestSyn is locked...
member function
member function sleep...
class TestSyn is unlocked...
static function
static function sleep...
member function wake...
static function wake...
```
可以看到class和member function部分代码是同时输出的，当class部分的锁释放后静态方法才得到锁。所以synchronized(class)与静态方法的作用效果相同。
但是synchronized(){}的方式加锁范围是整个实例对象时，当其他线程调用加锁的实例对象的其他任何synchronized实例方法是也会互斥。
当加锁范围时class时，当其他线程调用加锁类的其他任何synchronized静态方法时也会互斥，非静态方法不会发生互斥现象。
从这个角度来说，synchronized(){}的方式加锁范围比修饰方法名的范围大。

## 典型应用 —— 双重校验锁实现单例模式
```
public class Singleton {
    private volatile static Singleton singleton;
    private Singleton() {};

    public static Singleton getInstance() {
        // 1. 先判断singleton是否已经实例化过
        if (singleton == null) {
            // 2. 如果没有实例化过就对Singleton.class对象加锁，这样其他线程调用getInstance方法非阻塞
            synchronized(Singleton.class) {
                // 3. 线程恢复，如果其他线程实例化了singleton，则什么都不做退出，如果其他线程没有实例化singleton，则当前线程去实例化singleton
                if (singleton == null) {
                    singleton = new Singleton();
                }
            }
        }
        // 5. 返回实例化后的singleton
        return singleton;
    }
    
}
```
这里必须使用`volatile`修饰singleton对象，`volatile`可以防止指令重排序。实例化分为三步：
* 为singleton分配内容空间
* 在分配的内存空间中初始化singleton
* 将singleton指向分配的内存空间
由于JVM指令重拍的特性，在多线程执行的情况下可能比变成1->3->2。这样还没有初始化对象就有了对象的指针，其他线程在判断singleton是否实例化时就会认为singleton已经实例化完成而返回损坏的对象。
