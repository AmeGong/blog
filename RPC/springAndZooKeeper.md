# 使用Spring和ZooKeeper管理服务

在上一篇文章中，我们使用jdk中的动态代理实现了RPC程序，只要consumer和provider实现同样的接口，consumer就可以通过调用接口来调用provider提供的方法。在这一篇文章中我们使用Spring来管理provider提供的服务，并且使用ZooKeeper将服务抽象为一个文件路径，便于consumer进行调用。

因为我们还是使用jdk中的动态代理实现RPC，我们先定义需要调用的接口，这些接口放在同样的一个包内。

Hello.java

AddService.java



## consumer部分：

因为我们使用Spring管理provider提供的服务，所以我们从spring拿对应的服务。

```
AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(SpringSocketClientMain.class);
HelloController helloController = (HelloController) applicationContext.getBean("helloController");
```

然后我们调用Controller中对应service中的接口，service实现了API包中的接口。

同时我们提供一个自定义的注解，@RpcReference。该注解使用在需要代理的对象上，spring会自动将该对象设置为代理对象，该对象最好为动态代理中需要代理的接口。如何实现该接口将在framework部分讲解。



## provider部分：

我们同样将rpc server注册到spring中，从spring中取到rpc server服务后启动服务器，服务器等待着consumer请求。

```
AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(SpringSocketServerMain.class);
SpringSocketRpcServer rpcServer = applicationContext.getBean(SpringSocketRpcServer.class);
rpcServer.start();
```

RpcServer服务器中需要实现服务的注册和启动。服务器启动后监听指定端口，然后使用一个新线程来处理请求。

其中注册服务可以交给ServiceProvider来完成，该模块可以使用Spring进行注入。服务的启动与上一章的多线程rpc相似。

ServiceProvider是一个接口，需要实现添加服务，获取服务，发布服务的功能。其中添加服务和获取服务都可以通过map来实现，发布服务需要将服务的路径和服务器与连接池的指定端口ZooKeeper中，以便之后客户端通过服务能够查找到对应的服务。我们使用ServiceRegistry来实现发布到ZooKeeper中。其中ServiceRegister通过单例模式进行实例化，主要通过ExtensionLoader这个类来进行加载。在以后的文章我们在讲解ExtensionLoader这个类。ServiceProvider接口如下。

```
public interface ServiceProvider {
    /**
     * @param service              service object
     * @param serviceClass         the interface class implemented by the service instance object
     * @param rpcServiceProperties service related attributes
     */
    void addService(Object service, Class<?> serviceClass, RpcServiceProperties rpcServiceProperties);

    /**
     * @param rpcServiceProperties service related attributes
     * @return service object
     */
    Object getService(RpcServiceProperties rpcServiceProperties);

    /**
     * @param service              service object
     * @param rpcServiceProperties service related attributes
     */
    void publishService(Object service, RpcServiceProperties rpcServiceProperties);

    /**
     * @param service service object
     */
    void publishService(Object service);
}
```



ServiceProvider有一个主要的实现类。

```
@Component // 注入到spring中
public class ServiceProviderImpl implements ServiceProvider {
    private final Map<String, Object> serviceMap;
    private final Set<String> registeredService;
    private final ServiceRegistry serviceRegistry;

    public ServiceProviderImpl() {
        serviceMap = new ConcurrentHashMap<>();
        registeredService = ConcurrentHashMap.newKeySet();
        serviceRegistry = ExtensionLoader.getExtensionLoader(ServiceRegistry.class).getExtension("zk");
    }

    @Override
    public void addService(Object service, Class<?> serviceClass, RpcServiceProperties rpcServiceProperties) {
        String rpcServiceName = rpcServiceProperties.toRpcServiceName();
        if (registeredService.contains(rpcServiceName)) {
            return;
        }
        registeredService.add(rpcServiceName);
        serviceMap.put(rpcServiceName, service);
        log.info("Add service: {} and interfaces:{}", rpcServiceName, service.getClass().getInterfaces());
    }

    @Override
    public Object getService(RpcServiceProperties rpcServiceProperties) {
        Object service = serviceMap.get(rpcServiceProperties.toRpcServiceName());
        if (null == service) {
            throw new RpcException(RpcErrorMessageEnum.SERVICE_CAN_NOT_BE_FOUND);
        }
        return service;
    }

    @Override
    public void publishService(Object service) {
        this.publishService(service, RpcServiceProperties.builder().group("").version("").build());
    }

    @Override
    public void publishService(Object service, RpcServiceProperties rpcServiceProperties) {
        try {
            String host = InetAddress.getLocalHost().getHostAddress();
            Class<?> serviceRelatedInterface = service.getClass().getInterfaces()[0];
            String serviceName = serviceRelatedInterface.getCanonicalName();
            rpcServiceProperties.setServiceName(serviceName);
            this.addService(service, serviceRelatedInterface, rpcServiceProperties);
            serviceRegistry.registerService(rpcServiceProperties.toRpcServiceName(), new InetSocketAddress(host, PORT));
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }
}
```

可以看到，ServiceProviderImpl类主要有四个方法函数，有一个函数为重载（overload），于是四个方法对应三个功能。

* 添加服务。将服务类的实例添加到map中
* 获取服务。根据服务的名称得到服务类实例
* 发布服务。将服务publish到ZooKeeper中，将该socket与该服务进行绑定。将在后续进行详细分析。



为了方便发布服务，我们提供一个自定义注解，@RpcService。该注解用于接口的实现类上，spring会将该对象提供的服务发布到ZooKeeper，并且使用map记录这个服务实例。如何实现该接口将在framework部分讲解。



## framework部分：

在这一部分，我们提供一些主要的类供consumer和provider使用。

### 实现@RpcReference和@RpcService

被@RpcService注解的类需要完成三个功能。

* 将提供的服务发布到ZooKeeper中，供服务器发现
* 将服务的实现类注册到Spring容器中
* 将该类添加到provider中的map中



被@RpcReference注解的接口需要完成以下功能：

* spring为该接口自动为该接口生成动态代理

可以看到，两个注解都需要被spring扫描到，当spring遇到两个注解时会进行不同的处理。

### 将自定义的注解添加到Spring扫描中

在Spring中，有一个`ImportBeanDefinitionRegistrar`接口，所有被该类注册的Bean都会被`ConfigurationClassPostProcessor`处理。我们定义一个`CustomScannerRegister`继承`ImportBeanDefinitionRegistrar`。为了发现自定义注解修饰的接口和类，我们在`CustomScannerRegister`中定义一个`CustomScanner`，继承`ClassPathBeanDefinitionScanner`。该扫描器用于发现自定义注解修饰的接口和类。

注意：`CustomScannerRegister`不能使用`@Component`进行导入，因为这个类是用来发现其他被注解的bean的，起着类似于配置类的作用，所以我们需要使用`@Import`来导入`CustomScannerRegister`。为了能够复用`ImportBeanDefinitionRegistrar`，我们定义一个注解`@RpcScan`来导入`ImportBeanDefinitionRegistrar`类。将`@RpcScan`注解应用到main方法的类上。

```
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(CustomScannerRegister.class)
public @interface RpcScan {
    String[] basePackage();
}
```

### ConfigurationClassPostProcessor

这个类使用来处理bean的，这个类继承了`BeanPostProcessor`接口，其中的`postProcessBeforeInitialization`和`postProcessAfterInitialization`分别定义了在bean初始化之前和bean初始化之后需要做的东西，我们将在这两个方法中生成动态代理。

根据名字可以知道，一个方法在initialization之前调用， 一个方法啊在initialization之后调用。这里的initialzation与构造函数不同，这是在构造函数之后调用的，是spring中用来初始化bean的一个步骤。我们就是在这两个方法中完成基于接口的动态代理。

在postProcessBeforeInitialization方法中，我们对注解为@RpcService的服务实现类进行publish。

```
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean.getClass().isAnnotationPresent(RpcService.class)) {
            log.info("[{}] is annotated with  [{}]", bean.getClass().getName(), RpcService.class.getCanonicalName());
            // get RpcService annotation
            RpcService rpcService = bean.getClass().getAnnotation(RpcService.class);
            // build RpcServiceProperties
            RpcServiceProperties rpcServiceProperties = RpcServiceProperties.builder().group(rpcService.group()).version(rpcService.version()).build();
            serverProvider.publishService(bean, rpcServiceProperties);
        }
        return bean;
    }
```

在postProcessAfterInitializtion方法中，我们对注解为@RpcReference的接口进行动态代理。

```
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = bean.getClass();
        Field[] declaredFields = targetClass.getDeclaredFields();
        for (Field declaredField : declaredFields) {
            RpcReference reference = declaredField.getAnnotation(RpcReference.class);
            if (reference != null) {
                RpcServiceProperties rpcServiceProperties = RpcServiceProperties.builder().group(reference.group()).version(reference.version()).build();
                RpcClientProxy rpcClientProxy = new RpcClientProxy(rpcClient, rpcServiceProperties);
                
                // 生成代理对象
                Object clientProxy = rpcClientProxy.getProxy(declaredField.getType());
                declaredField.setAccessible(true);
                try {
                	// bean引用指向生成的代理对象
                    declaredField.set(bean, clientProxy);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return bean;
    }
```

到此，使用spring管理服务的核心已经结束。接下来将分析一些重要的类。



## RpcRequestTransport接口

```
public interface RpcRequestTransport {
    Object sendRequest(RpcRequest rpcRequest);
}
```

该接口属于consumer那一部分，consumer将接口信息，方法信息和方法的参数等封装为RpcRequest，然后使用sendRequest方法将RpcRequest发送出去。有一个主要的实现类为`SocketRpcClient`。

```
public class SocketRpcClient implements RpcRequestTransport {
    private final ServiceDiscovery serviceDiscovery;

    public SocketRpcClient() {
        // initialize the serviceDiscovery
        serviceDiscovery = ExtensionLoader.getExtensionLoader(ServiceDiscovery.class).getExtension("zk");
    }
    @Override
    public Object sendRequest(RpcRequest rpcRequest) {
        String rpcServiceName = RpcServiceProperties.builder().serviceName(rpcRequest.getInterfaceName())
                .group(rpcRequest.getGroup()).version(rpcRequest.getVersion()).build().toRpcServiceName();
        InetSocketAddress inetSocketAddress = serviceDiscovery.lookupService(rpcServiceName);
        try(Socket socket = new Socket()) {
            socket.connect(inetSocketAddress);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            objectOutputStream.writeObject(rpcRequest);

            ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
            return objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException  e) {
            throw new RpcException("调用服务失败: ", e);
        }
    }
}
```

该方法类有一个ServiceDiscovery类，该类与ZooKeeper相关，主要功能在于发现提供目标服务的socket，然后与该socket建立连接，将封装好的RpcRequest发送到服务器然后等待响应。

## SocketRpcServer类

该类有两个作用。

* 发布服务
* 使用多线程技术响应consumer请求

这两点可以从SocketRpcServer类的两个成员变量看出来。

```
@Component
public class SocketRpcServer {
    private final ExecutorService threadPool;
    private final ServiceProvider serviceProvider;

    public SocketRpcServer() {
        threadPool = ThreadPoolFactoryUtils.createCustomThreadPoolIfAbsent("socket-server-rpc-pool");
        // 也可以使用自动注入
        serviceProvider = SingletonFactory.getInstance(ServiceProviderImpl.class);
    }

    public void registerService(Object service) {
        serviceProvider.publishService(service);
    }

    public void registerService(Object service, RpcServiceProperties rpcServiceProperties) {
        serviceProvider.publishService(service, rpcServiceProperties);
    }

    public void start() {
        try(ServerSocket server = new ServerSocket()) {
            String host = InetAddress.getLocalHost().getHostAddress();
            server.bind(new InetSocketAddress(host, PORT));

            Socket socket;
            while((socket = server.accept()) != null) {
                log.info("client connected [{}]", socket.getInetAddress());
                // 启动一个新线程去响应consumer请求，SocketRpcRequestHanlderRunner实现了runnable接口
                threadPool.execute(new SocketRpcRequestHandlerRunnable(socket));
            }
            threadPool.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

serviceProvider类主要复杂发布服务，而threadPool类主要用于处理consumer的请求。



## SocketRpcRequestHandlerRunnable类

该类主要用于响应consumer的请求，因为使用的多线程技术，所以该类实现了Runnable接口。

```
public class SocketRpcRequestHandlerRunnable implements Runnable {
    private final Socket socket;
    private final RpcRequestHandler rpcRequestHandler;

    public SocketRpcRequestHandlerRunnable(Socket socket) {
        this.socket = socket;
        this.rpcRequestHandler = SingletonFactory.getInstance(RpcRequestHandler.class);
    }

    @Override
    public void run() {
        log.info("server handle message from client by thread: [{}]", Thread.currentThread().getName());
        try(ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream())) {
             RpcRequest rpcRequest = (RpcRequest) objectInputStream.readObject();
             // 处理rpcRequest
            Object result = rpcRequestHandler.handle(rpcRequest);
            objectOutputStream.writeObject(RpcResponse.success(result, rpcRequest.getRequestId()));
            objectOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
```

可以看到，构造函数需要传递一个socket给该类，每一个consumer请求都对应一个该类去进行处理。也可以看到，该类中有一个RpcRequestHandler对象，该对象是真正干活的，该类根据提供的rpcRequest实例寻找对应的服务类，然后使用找到的服务类处理rpcRequest参数并返回结果。

```
public class RpcRequestHandler implements RequestHandler {
    private final ServiceProvider serviceProvider;

    public RpcRequestHandler() {
        // 也可以使用自动注入
        this.serviceProvider = SingletonFactory.getInstance(ServiceProviderImpl.class);
    }
    
	// RequestHandler是一个接口，实现handle方法
    @Override
    public Object handle(RpcRequest rpcRequest) {
    	// 在serviceProvider中寻找rpcRquest中请求的服务
        Object service = serviceProvider.getService(rpcRequest.toRpcProperties());
        // 使用找到的服务处理rpcRequest请求并返回结果
        return invokeTargetMethod(rpcRequest, service);
    }

    private Object invokeTargetMethod(RpcRequest rpcRequest, Object service) {
        Object result = null;
        try {
            Method method = service.getClass().getMethod(rpcRequest.getMethodName(), rpcRequest.getParamTypes());
            result = method.invoke(service, rpcRequest.getParameters());
            log.info("service:[{}] successful invoke method:[{}]", rpcRequest.getInterfaceName(), rpcRequest.getMethodName());
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return result;
    }
}
```





下图说明了整个RPC程序的关系。

![procedure-of-rpc](.\figures\procedure-of-rpc.png)





下图说明了spring部分。

![procedure-of-rpc](.\figures\spring-part.png)



下图为zookeeper部分

![procedure-of-rpc](.\figures\zookeeper-rpc.png)





其中一些箭头含义如下：

![procedure-of-rpc](.\figures\uml.png)











































