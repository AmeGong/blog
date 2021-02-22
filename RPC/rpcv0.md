# RPC基本程序
RPC的全称为remote procedure call，即远程程序调用。与普通的程序调用不同在于，RPC的程序调用可以不受调用者和被调用者在同一个主机或项目下的限制，实现跨主机和跨程序的程序调用。
本文使用RPC实现一个简单的加法，所以我们先定义一个接口Calculator。
Calculator.java

```
public interface Calculator {
    int add(int a, int b);
}
```

函数的调用分为调用者和被调用者，在RPC中我们分别称呼这两个对象为消费者(consumers)和提供者(provider)，分别代表服务的消费者和服务的提供者。consumers提供需要调用的函数名称，参数等信息，providers根据这些信息找到被调用的函数，然后返回函数的放回值。
所以RPC程序分为至少分为三块，consumers和providers。为了方便信息传输，我们将consumers对providers的提供的信息封装为一个类，名为Request，结构如下。
Request.java

```
public class Request implements Serializable {
    private static final long serialVersionUID = 7503710091945320739L; 
    private String method;        // the name of the invoked method
    private int a;               // the first parameter
    private int b;              // the first parameter

    // getters and setters

    @Override
    public String toString() {  // override the interface of Serializable 
        return "CalculateRpcRequest{" +
                "method='" + method + '\'' +
                ", a=" + a +
                ", b=" + b +
                '}';
    }
}
```

然后我们开始编写Consumer部分，在这里我们使用多线程技术，假设有多个consumers调用加法。Consumer实现如下。
Consumer.java

```
public class Consumer implements Runnable{
    private int a, b;
    private Calculator remoteCalculater = new RemoteCalculater();
    public Consumer(int a, int b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public void run() {
        int result = remoteCalculater.add(a, b);
        System.out.println(result);
    }
}
```

在Consumer中有一个Calculator，这是一个用来实现加法的类，我们通过调用该类的add方法实现加法。在这个类中，我们通过远程调用provider提供的服务实现加法。
RemoteCalculator.java

```
public class RemoteCalculater implements Calculator {
    private int port = 9090;

    @Override
    public int add(int a, int b) {
        Object response = null;
        try {
            // 优化点：可以用反射获得方法的全限定名;
            List<String> addressList = lookupProviders("Calculator.add"); //查找提供服务的主机地址及其端口号
            String host = chooseProvider(addressList); // 选择主机
            try(Socket socket = new Socket(host, port)) {  // 开启socket连接
                ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
                Request request = generateRequest(a, b);  // 序列化封装消费者的提供的参数，方法信息等
                
               outputStream.writeObject(request); // 通过socket将调用的方法，参数发送给服务器

                ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());  // 等待服务器的回复
                response = inputStream.readObject();  // 反序列化得到结果
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (response instanceof Integer) {
            return (Integer) response;
        } else {
            throw new InternalError();
        }
    }

    private Request generateRequest(int a, int b) {
        Request request = new Request();
        request.setA(a);
        request.setB(b);
        request.setMethod("add");
        return request;
    }

    private String chooseProvider(List<String> addressList) {
        if (addressList == null || addressList.size() == 0){
            throw new IllegalArgumentException();
        }
        return addressList.get(0);
    }

    private List<String> lookupProviders(String serviceName) {
        List<String> strings = new ArrayList<>();
        strings.add("127.0.0.1");
        return strings;
    }
}
```

我们可以看到，add方法主要做了如下几件事

    查找提供服务的providers
    选择合适的provider
    打开socket连接
    序列化封装consumer提供的参数
    阻塞等待provider的消息
    反序列化provider返回的结果

至此，consumer部分结束，需要编写provider
由于我们使用多线程，所以我们需要一个线程池用来接收服务请求。另外需要一个专门的线程来监听又服务调用。Provider相关代码如下：
Provider.java

```
public class Provider {
    private final ExecutorService pool;  // 线程池用来处理不同的consumers
    private final Listener listener;    // 一个独立的线程，用来专门监听是否有consumer

    public Provider(int port, int poolSize) throws IOException {
        pool = Executors.newFixedThreadPool(10);
        listener = new Listener(9090);
    }

    public void start() throws IOException {
        try {
            while(true) {
                // 这里不能使用try with resource语句，因为关闭socket需要等待consumer方，
                // 多线程时，execute用另一个个线程执行，所以用try with resource会立马关闭socket
                pool.execute(new Handler(listener.start()));
            }
        } finally {
            pool.shutdown();
        }
    }
}
```

Listener.java

```
public class Listener{
    private final ServerSocket serverSocket;

    public Listener(int port) throws IOException {
        serverSocket = new ServerSocket(port);
    }

    public Socket start() throws IOException {
        return serverSocket.accept();
    }
}
```

在Listener接收到consumer之后，返回请求者的套接字信息，Handler接收socket信息进行处理，也就是说Handler是真正处理请求的类，业务逻辑集中在Handler中。
Handler.java

```
public class Handler implements Runnable {
    private Socket socket;
    private Calculator calculator = new CalculatorImpl();

    public Handler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            System.out.println("Recieve connection from sockect: "+socket.getInetAddress()+":"+socket.getPort());
            ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
            Object recv = inputStream.readObject();

            int result = 0;
            if (recv instanceof Request) {
                Request request = (Request) recv;
                if ("add".equals(request.getMethod())) {
                    result = calculator.add(request.getA(), request.getB());
                }
            }
            ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
            outputStream.writeObject(Integer.valueOf(result));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
```

可以看到Handler做了四件事：
* 反序列化从socket收到的信息
* 判断方法是否为add
* 执行加法
* 返回结果

Consumer拿到Provider返回的结果进行反序列化就可以得到最终的结果了。
这是一个简单的RPC程序，接下来会使用动态代理实现RPC程序。



