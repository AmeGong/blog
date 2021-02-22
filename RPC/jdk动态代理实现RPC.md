# 动态代理实现RPC
在上一篇文章中，我们使用序列化和网络通信的方法实现了RPC程序，但是在使用时需要再创建一个新的类实现RPC功能，这一点不是我们想要的，我们想要让调用者调用RPC方法时感觉和调用普通的函数方法没有差别，为此我们使用动态代理的手段。动态代理的核心函数为Object Proxy.newProxyInstance(ClassLoader loader, Class<?>[] interfaces, InvocationHandler h)。第一个参数为类加载器，第二个是代理的接口对象，第三个是真正实现接口功能的类，该类实现了InvocationHandler接口，实现该接口需要实现invoke方法，当接口方法被调用时被自动代理到invoke方法。也就是说invoke方法是接口的真正实现位置。

接下来我们开始使用动态代理实现一个RPC程序。
首先我们的项目分为四个部分，consumer，core，provider和service。core为RPC的核心程序，service用来放置需要远程调用的接口。
在本文中我们实现如下接口的RPC
HelloService.java
```
public interface HelloService {
    String sayHello(String word);
}
```


由于consumer为newProxyInstance返回的对象，调用时与普通函数调用一样，于是我们先编写consumer代码
RpcConsumer.java

```
public class RpcConsmuer {
    public static void main(String[] args) {
        HelloService helloService = RemoteServiceImpl.newRemoteProxyService(HelloService.class);
        String result = helloService.sayHello("Ame");
        System.out.println(result);
    }
}
```

其中RemoteServiceImpl封装Proxy.newProxyInstance函数。
RemoteServiceImpl.java

```
public class RemoteServiceImpl<T> {
    public static <T> T newRemoteProxyService(final Class<T> service) {
        return (T) Proxy.newProxyInstance(service.getClassLoader(),new Class[] {service}, new ProxyHandler(service));
    }
}
```

通过Proxy对象得到了代理对象，consumer部分的程序就完成了。真正实现函数功能的部分在于ProxyHandler类中。
ProxyHandler.java

```
public class ProxyHandler implements InvocationHandler {
    private long timeout = 1000;
    private Class<?> service;
    private ExecutorService executor;
    private InetSocketAddress remoteAddress = new InetSocketAddress("127.0.0.1", 8989);

    public ProxyHandler(Class<?> service) {
        this.service = service;
        executor = Executors.newFixedThreadPool(1);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        RpcContext rpcContext = new RpcContext();
        rpcContext.setServiceName(service.getName());
        rpcContext.setMethodName(method.getName());
        rpcContext.setArguments(args);
        rpcContext.setParameterTypes(method.getParameterTypes());

        rpcContext.setRemoteAddress(remoteAddress);
        return request(rpcContext);
    }

    \\ ....
}
```

ProxyHandler中最主要的方法是覆写的invoke方法。与上次的RPC程序相同，我们也需要将函数调用的信息序列化，这里我们用RpcContext类来封装这些信息。通过反射得到了service Name，methodName，args和ParameterTypes后，将这些信息封装到RpcContext类中。然后将RpcContext类传给request函数。该函数实现了socket通信，该部分与上一次的RPC程序差别不大。
RpcContext.java

```
@Data
public class RpcContext implements Serializable {
    private String serviceName;  
    private String methodName;
    private Class<?>[] parameterTypes;
    private Object[] arguments;
    private InetSocketAddress localAddress;
    private InetSocketAddress remoteAddress;
    private long timeout = 10000;

    @Override
    public String toString() {
        return "RpcContext{" +
            "serviceName='" + serviceName + '\'' +
            ", methodName='" + methodName + '\'' +
            ", parameterTypes=" + Arrays.toString(parameterTypes) +
            ", arguments=" + Arrays.toString(arguments) +
            ", localAddress=" + localAddress +
            ", remoteAddress=" + remoteAddress +
            ", timeout=" + timeout +
            '}';
    }
}
```

当consumer将封装好的RpcContext发送给provider端后，我们需要在provider中查找该接口是否有具体的实现类。于是我们需要一个类去对服务接口进行管理。

```
public class RegisterServicesCenter {
    private static ConcurrentHashMap<String, Class> registerServices = new ConcurrentHashMap<>();

    public static void setRegisterServices(ConcurrentHashMap<String, Class> registerServices) {
        RegisterServicesCenter.registerServices = registerServices;
    }

    public static ConcurrentHashMap<String, Class> getRegisterServices() {
        return registerServices;
    }

    public static Class<?> getService(String serviceName) {
        return registerServices.get(serviceName);
    }

    public static void register(String serviceName, Class clazz) {
        registerServices.put(serviceName, clazz);
    }

    public static void register(Class service, Class clazz) {
        if (service.isInterface()) {
            registerServices.put(service.getName(), clazz);
        } else {
            try {
                throw new RpcException("The service must be interface!");
            } catch (RpcException e) {
                e.printStackTrace();
            }
        }
    }
}
```

本质上其就是一个Map。
同时为了方便，我们要求所有的ServiceProvider继承RpcServer接口，该接口用来管理RpcServiceCenter类。

```
public interface RpcServer {

    void start();

    void stop();

    void register(String className, Class clazz) throws Exception;

    boolean isAlive();
}
```

我们编写实现类
RpcServerImpl.java

```
public class RpcServerImpl implements RpcServer {
    private int nThreads = 10;
    private boolean isAlive = false;
    private int port = 8989;
    private ExecutorService pool;

    private void init(){
        pool = Executors.newFixedThreadPool(nThreads);
    }

    public RpcServerImpl(int port, int nThreads) {
        this.nThreads = nThreads;
        this.port = port;
        init();
    }

    public RpcServerImpl(int port) {
        this.port = port;
        init();
    }

    @Override
    public void start() {
        isAlive = true;
        try(ServerSocket listener = new ServerSocket(port)) {
            System.out.println("The server has started...");
            while(true) {
                Socket socket = listener.accept();
                System.out.println("Recieve request from socket: "+socket.getInetAddress()+":"+socket.getPort());
                pool.submit(new RpcRequestHandler(socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        isAlive = false;
        pool.shutdown();
    }

    @Override
    public void register(String className, Class clazz) throws Exception {
        if (RegisterServicesCenter.getRegisterServices() != null) {
            RegisterServicesCenter.register(className, clazz);
        }
        else {
            throw new RpcException("The RPC server has not initialized!");
        }
    }

    @Override
    public boolean isAlive() {
        return isAlive;
    }
}

```
每当有socket连接时，RpcServer就将真正处理连接的程序交给RpcRequestHandler来处理，该类实现了runnable接口，用来实现多线程，其run的逻辑如下：

    RpcContext反序列化
    在RpcServiceCenter中查找实现类，调用实现类的对应方法
    序列化结果并写入socket中。

RpcRequestHandler.java

```
public class RpcRequestHandler implements Runnable {
    private Socket socket;

    public RpcRequestHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream())) {
            RpcContext rpcContext = (RpcContext)inputStream.readObject();

            Class clazz = RegisterServicesCenter.getService(rpcContext.getServiceName());
            if(clazz == null) {
                throw new RpcException("Can not find Class: "+rpcContext.getServiceName());
            }

            Method method = clazz.getMethod(rpcContext.getMethodName(), rpcContext.getParameterTypes());
            if (method == null) {
                throw new RpcException("Can not find method: "+rpcContext.getMethodName());
            }

            Object result = method.invoke(clazz.getConstructor().newInstance(), rpcContext.getArguments());
            outputStream.writeObject(result);
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

provider部分。
ProviderApp.java

```
public class ProviderApp {
    public static void main(String[] args) throws Exception {
        RpcServer rpcServer = new RpcServerImpl(8989, 10);
        rpcServer.register(HelloService.class.getName(), HelloServiceImpl.class);
        rpcServer.start();
    }
}
```

HelloServiceImpl.java

```
public class HelloServiceImpl implements HelloService {
    @Override
    public String sayHello(String word) {
        return "Hello, "+word;
    }
}
```

现在我们使用动态代理实现了多线程的RPC服务，下一步我们使用Spring作为服务中心，ZooKeeper作为注册中心进一步完成RPC。