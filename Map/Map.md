# 三种线程安全的HashMap
* Collections.synchronizedMap
* Hashtable
* ConcurrentHashMap

其中前两者并发度不高。一下说明原因。

## Collections.synchronizedMap实现
synchronizedMap中维护着一把锁，每次对Map进行操作时都会进行加锁。
```
private static class SynchronizedMap<K,V>
    implements Map<K,V>, Serializable {
    private static final long serialVersionUID = 1978198479659022715L;

    private final Map<K,V> m;     // Backing Map
    final Object      mutex;        // Object on which to synchronize

    public int size() {
        synchronized (mutex) {return m.size();}
    }
    public boolean isEmpty() {
        synchronized (mutex) {return m.isEmpty();}
    }
    public boolean containsKey(Object key) {
        synchronized (mutex) {return m.containsKey(key);}
    }
    public boolean containsValue(Object value) {
        synchronized (mutex) {return m.containsValue(value);}
    }
    public V get(Object key) {
        synchronized (mutex) {return m.get(key);}
    }

    public V put(K key, V value) {
        synchronized (mutex) {return m.put(key, value);}
    }
    public V remove(Object key) {
        synchronized (mutex) {return m.remove(key);}
    }
    public void putAll(Map<? extends K, ? extends V> map) {
        synchronized (mutex) {m.putAll(map);}
    }
    public void clear() {
        synchronized (mutex) {m.clear();}
    }
}
```
每一个方法都要对整个Map加锁，这样就导致性能很低。

## Hashtable
Hashtable与上一个map相似，每个方法前都有synchronized关键词修饰，从而保证并发安全。
```
public synchronized V remove(Object key)
```
与HashMap不同点在于，Hashtable是不能允许键或者值为null，但是HashMap的键和值都可以为null。
Hashtable使用键对象自带的hashCode方法计算hash值，所以键值不能为null，同时再put时检查value值是否为null，是则抛出异常。从设计思想上来看，Hashtable使用安全失败机制(fail-safe)，这种机制会使你此次读到的数据不一定是最新的数据。如果你使用null值，就会使得其无法判断对应的key是不存在还是为空，因为你无法再调用一次contain(key）来对key是否存在进行判断，ConcurrentHashMap同理。

```
public synchronized V put(K key, V value) {
    // Make sure the value is not null
    if (value == null) {
        throw new NullPointerException();
    }

    // Makes sure the key is not already in the hashtable.
    Entry<?,?> tab[] = table;
    int hash = key.hashCode();
    ...
}
```
但是在HashMap中，hash值是使用自定义的静态方法计算的，所以键可以为null。
```
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```
同时在put函数中没有检查value值是否为null的代码。

其他不同在于：
* 实现方式不同：Hashtable继承Dictionary，而HashMap继承AbstractMap类
* 初始容量不同：Hashtable为11，HashMap为16。
* 扩容机制不同：当容量大于总容量*负载因子时，HashMap扩容机制是当前容量翻倍，Hashtable是当前容量翻倍+1
* 迭代器不同：HashMap使用的iterator迭代器是fail-fast的，但是Hashtable的Enumerator不是fail-fast的。

## fail-fast
在用迭代器遍历集合对象时，如果遍历过程中对对象中的内容进行了修改，则会抛出Concurrent Modification Exception。
其原理时在结合中维护一个modCount变量来记录修改操作的次数，如果modCount!=expectedmodCount则抛出异常。如果有人篡改了modCount值，且刚好等于expectmodCount值时该异常就不会出现。因此不能依赖与该异常来进行并发编程。

# ConcurrentHashMap
在jdk1.7和1.8中，ConcurrentHashMap的实现不同。先说说1.7中的实现。
## 1.7中的ConcurrentHashMap
在jdk1.7中，ConcurrentHashMap由


## 1.8中的ConcurentHashMap
1.8中ConcurrentHashMap抛弃了Segment分段锁，而是采用了`CAS+synchronized`来保证并发。
put方法的步骤如下：
* 计算key的hashCode。
* 判断是否需要进行初始化。
* 为当前key定位Node，如果为空表示当前位置可以写入数据，利用CAS尝试写入，失败则自旋保证成功。(这样的实现了保证了只有在发送hash碰撞的时候才使用`synchronized`重量级锁，没有发生碰撞的时候都是CAS轻量级锁，保证了效率。)
* 如果当前位置满足`hashCode==MOVED`，则需要扩容。
* 如果都不满足，则利用synchronized锁写入数据。
* 如果数量大于`TREEIFY_THRESHOLD`则要转换为红黑树。

代码如下：
```
final V putVal(K key, V value, boolean onlyIfAbsent) {
    if (key == null || value == null) throw new NullPointerException();
    int hash = spread(key.hashCode());
    int binCount = 0;
    for (Node<K,V>[] tab = table;;) {
        Node<K,V> f; int n, i, fh;
        // 是否需要进行初始化
        if (tab == null || (n = tab.length) == 0)
            tab = initTable();
        // 判断当前key对应的是否为空Node，如果是则使用CAS+自选保证成功写入
        else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
            if (casTabAt(tab, i, null,
                            new Node<K,V>(hash, key, value, null)))
                break;                   // no lock when adding to empty bin
        }
        // 如果当前位置达到MOVED则扩容
        else if ((fh = f.hash) == MOVED)
            tab = helpTransfer(tab, f);
        else {
        // 使用synchronized锁，锁住当前节点f，也就是说其他node还可以访问操作。
            V oldVal = null;
            synchronized (f) {
                ...
            }
            // 如果数量大于TREEIFY_THRESHOLD，则转换为红黑树
            if (binCount != 0) {
                if (binCount >= TREEIFY_THRESHOLD)
                    treeifyBin(tab, i);
                if (oldVal != null)
                    return oldVal;
                break;
            }
        }
    }
    addCount(1L, binCount);
    return null;
}
```

## CAS(compare and swap)
CAS是一种乐观锁，每次进行修改时总是认为没有并发操作，在线程读取数据时不进行加锁，在准备写回数据的时候，比较原值是否修改，如果没有被修改则写回，若已被修改则重新执行读取流程。
### ABA问题
CAS的一个典型问题就是ABA问题。如果说一个线程将A值改成了B，又有一个线程将B改回了A，那么对于判断线程来说，他就不知道这个值有没有被人改过，但是从结果上都是一样的。对于一些对于记录敏感的业务需要结局ABA问题。

版本号可以用来解决这个问题，我们每次查询变量的值时都查询变量的版本号，在修改时，如果版本号相同则修改，修改成功后将该版本号+1。版本号也可以用时间戳来代替。



