## 使用pytorch1.8，cuda11，cudnn8时，多卡训练出现NCCL出现bug
错误信息如下
```
RuntimeError: NCCL error in: /opt/conda/conda-bld/pytorch_1614378098133/work/torch/lib/c10d/ProcessGroupNCCL.cpp:825, unhandled system error, NCCL version 2.7.8
```
将NCCL设置为DEBUG模式，得到一下信息
```
# 在.bashrc文件中设置环境变量，export NCCL_DEBUG=info设置为debug模式 或则在运行python命令行之前加上NCCL_DEBUG=info
4e9a7654171e:8639:8639 [0] NCCL INFO Bootstrap : Using [0]eth0:172.17.0.2<0>
4e9a7654171e:8639:8639 [0] NCCL INFO NET/Plugin : No plugin found (libnccl-net.so), using internal implementation

4e9a7654171e:8639:8639 [0] misc/ibvwrap.cc:63 NCCL WARN Failed to open libibverbs.so[.1]
4e9a7654171e:8639:8639 [0] NCCL INFO NET/Socket : Using [0]eth0:172.17.0.2<0>
4e9a7654171e:8639:8639 [0] NCCL INFO Using network Socket
NCCL version 2.7.8+cuda11.1
4e9a7654171e:8640:8640 [1] NCCL INFO Bootstrap : Using [0]eth0:172.17.0.2<0>
4e9a7654171e:8640:8640 [1] NCCL INFO NET/Plugin : No plugin found (libnccl-net.so), using internal implementation

4e9a7654171e:8640:8640 [1] misc/ibvwrap.cc:63 NCCL WARN Failed to open libibverbs.so[.1]
4e9a7654171e:8640:8640 [1] NCCL INFO NET/Socket : Using [0]eth0:172.17.0.2<0>
4e9a7654171e:8640:8640 [1] NCCL INFO Using network Socket
4e9a7654171e:8642:8642 [3] NCCL INFO Bootstrap : Using [0]eth0:172.17.0.2<0>
4e9a7654171e:8642:8642 [3] NCCL INFO NET/Plugin : No plugin found (libnccl-net.so), using internal implementation

4e9a7654171e:8642:8642 [3] misc/ibvwrap.cc:63 NCCL WARN Failed to open libibverbs.so[.1]
4e9a7654171e:8642:8642 [3] NCCL INFO NET/Socket : Using [0]eth0:172.17.0.2<0>
4e9a7654171e:8642:8642 [3] NCCL INFO Using network Socket
4e9a7654171e:8641:8641 [2] NCCL INFO Bootstrap : Using [0]eth0:172.17.0.2<0>
4e9a7654171e:8641:8641 [2] NCCL INFO NET/Plugin : No plugin found (libnccl-net.so), using internal implementation

4e9a7654171e:8641:8641 [2] misc/ibvwrap.cc:63 NCCL WARN Failed to open libibverbs.so[.1]
4e9a7654171e:8641:8641 [2] NCCL INFO NET/Socket : Using [0]eth0:172.17.0.2<0>
4e9a7654171e:8641:8641 [2] NCCL INFO Using network Socket
4e9a7654171e:8639:8745 [0] NCCL INFO Channel 00/02 :    0   1   2   3
4e9a7654171e:8639:8745 [0] NCCL INFO Channel 01/02 :    0   1   2   3
4e9a7654171e:8639:8745 [0] NCCL INFO threadThresholds 8/8/64 | 32/8/64 | 8/8/64
4e9a7654171e:8640:8763 [1] NCCL INFO threadThresholds 8/8/64 | 32/8/64 | 8/8/64
4e9a7654171e:8639:8745 [0] NCCL INFO Trees [0] 1/-1/-1->0->-1|-1->0->1/-1/-1 [1] 1/-1/-1->0->-1|-1->0->1/-1/-1
4e9a7654171e:8640:8763 [1] NCCL INFO Trees [0] 2/-1/-1->1->0|0->1->2/-1/-1 [1] 2/-1/-1->1->0|0->1->2/-1/-1
4e9a7654171e:8639:8745 [0] NCCL INFO Setting affinity for GPU 0 to ffff,0000ffff
4e9a7654171e:8640:8763 [1] NCCL INFO Setting affinity for GPU 1 to ffff,0000ffff
4e9a7654171e:8641:8784 [2] NCCL INFO threadThresholds 8/8/64 | 32/8/64 | 8/8/64
4e9a7654171e:8641:8784 [2] NCCL INFO Trees [0] 3/-1/-1->2->1|1->2->3/-1/-1 [1] 3/-1/-1->2->1|1->2->3/-1/-1
4e9a7654171e:8641:8784 [2] NCCL INFO Setting affinity for GPU 2 to ffff0000,ffff0000
4e9a7654171e:8642:8783 [3] NCCL INFO threadThresholds 8/8/64 | 32/8/64 | 8/8/64
4e9a7654171e:8642:8783 [3] NCCL INFO Trees [0] -1/-1/-1->3->2|2->3->-1/-1/-1 [1] -1/-1/-1->3->2|2->3->-1/-1/-1
4e9a7654171e:8642:8783 [3] NCCL INFO Setting affinity for GPU 3 to ffff0000,ffff0000
4e9a7654171e:8640:8763 [1] NCCL INFO Could not enable P2P between dev 1(=3d000) and dev 0(=1a000)
4e9a7654171e:8642:8783 [3] NCCL INFO Could not enable P2P between dev 3(=b2000) and dev 2(=89000)
4e9a7654171e:8641:8784 [2] NCCL INFO Could not enable P2P between dev 2(=89000) and dev 3(=b2000)
4e9a7654171e:8641:8784 [2] NCCL INFO Channel 00 : 2[89000] -> 3[b2000] via direct shared memory
4e9a7654171e:8642:8783 [3] NCCL INFO Channel 00 : 3[b2000] -> 0[1a000] via direct shared memory
4e9a7654171e:8639:8745 [0] NCCL INFO Could not enable P2P between dev 0(=1a000) and dev 1(=3d000)
4e9a7654171e:8640:8763 [1] NCCL INFO Channel 00 : 1[3d000] -> 2[89000] via direct shared memory
4e9a7654171e:8639:8745 [0] NCCL INFO Channel 00 : 0[1a000] -> 1[3d000] via direct shared memory
4e9a7654171e:8641:8784 [2] NCCL INFO Could not enable P2P between dev 2(=89000) and dev 3(=b2000)
4e9a7654171e:8642:8783 [3] NCCL INFO Could not enable P2P between dev 3(=b2000) and dev 2(=89000)
4e9a7654171e:8642:8783 [3] NCCL INFO Channel 00 : 3[b2000] -> 2[89000] via direct shared memory
4e9a7654171e:8639:8745 [0] NCCL INFO Could not enable P2P between dev 0(=1a000) and dev 1(=3d000)

4e9a7654171e:8639:8745 [0] include/shm.h:28 NCCL WARN Call to posix_fallocate failed : No space left on device
4e9a7654171e:8639:8745 [0] NCCL INFO include/shm.h:41 -> 2

4e9a7654171e:8639:8745 [0] include/shm.h:48 NCCL WARN Error while creating shared memory segment nccl-shm-recv-170206743cd9fd1d-0-1-0 (size 9637888)

4e9a7654171e:8639:8745 [0] NCCL INFO transport/shm.cc:101 -> 2
4e9a7654171e:8639:8745 [0] NCCL INFO transport.cc:30 -> 2
4e9a7654171e:8639:8745 [0] NCCL INFO transport.cc:49 -> 2
4e9a7654171e:8639:8745 [0] NCCL INFO init.cc:767 -> 2
4e9a7654171e:8639:8745 [0] NCCL INFO init.cc:840 -> 2
4e9a7654171e:8639:8745 [0] NCCL INFO group.cc:73 -> 2 [Async thread]

4e9a7654171e:8640:8763 [1] include/shm.h:28 NCCL WARN Call to posix_fallocate failed : No space left on device
4e9a7654171e:8640:8763 [1] NCCL INFO include/shm.h:41 -> 2

4e9a7654171e:8640:8763 [1] include/shm.h:48 NCCL WARN Error while creating shared memory segment nccl-shm-recv-1cead96201472d35-0-2-1 (size 9637888)

4e9a7654171e:8640:8763 [1] NCCL INFO transport/shm.cc:101 -> 2
4e9a7654171e:8640:8763 [1] NCCL INFO transport.cc:30 -> 2
4e9a7654171e:8640:8763 [1] NCCL INFO transport.cc:49 -> 2
4e9a7654171e:8640:8763 [1] NCCL INFO init.cc:767 -> 2
4e9a7654171e:8640:8763 [1] NCCL INFO init.cc:840 -> 2
4e9a7654171e:8640:8763 [1] NCCL INFO group.cc:73 -> 2 [Async thread]
```
关键的地方在于**4e9a7654171e:8640:8763 [1] include/shm.h:48 NCCL WARN Error while creating shared memory segment nccl-shm-recv-1cead96201472d35-0-2-1 (size 9637888)**可以看到，在创建共享内存时出错。
一种方法是关闭共享内存，通过设置环境变量export NCCL_SHM_DISABLE=1或则在运行python之前加上NCCL_SHM_DISABLE=1
另一种方法是增大共享内存，这需要在启动容器是添加对应的参数。