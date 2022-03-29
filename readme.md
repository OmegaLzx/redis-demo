# Redis文档

## 了解基本概念

> Redis是现在最受欢迎的NoSQL数据库之一，Redis是一个使用ANSI C编写的开源、包含多种数据结构、支持网络、基于内存、可选持久性的键值对存储数据库，其具备如下特性：
>
> - 基于内存运行，性能高效
> - 支持分布式，理论上可以无限扩展
> - key-value存储系统
> - 开源的使用ANSI C语言编写、遵守BSD协议、支持网络、可基于内存亦可持久化的日志型、Key-Value数据库，并提供多种语言的API
>
> 相比于其他数据库类型，Redis具备的特点是：
>
> - C/S通讯模型
> - 单进程单线程模型
> - 丰富的数据类型
> - 操作具有原子性
> - 持久化
> - 高并发读写
> - 支持lua脚本

## 环境搭建

### 裸机部署

~~~shell
# Redis是基于C语言编写，因此首先需要安装Redis所需的gcc依赖
$ yum install -y gcc tcl
# 上传压缩包redis-6.2.6.tar.gz，并解压缩
$ tar -zxvf redis-6.2.6.tar.gz -C /opt/module/
# 编译安装
$ make && make install
# 进入redis安装目录
$ cd /usr/local/bin
# 存在以下文件
total 31348
drwxr-xr-x.  2 root root      172 Mar 28 07:09 .
drwxr-xr-x. 12 root root      131 Feb 19 11:25 ..
-rw-r--r--.  1 root root       92 Mar 28 07:09 dump.rdb
-rwxr-xr-x.  1 root root  4829544 Mar 28 07:08 redis-benchmark
lrwxrwxrwx.  1 root root       12 Mar 28 07:08 redis-check-aof -> redis-server
lrwxrwxrwx.  1 root root       12 Mar 28 07:08 redis-check-rdb -> redis-server
-rwxr-xr-x.  1 root root  5003824 Mar 28 07:08 redis-cli
lrwxrwxrwx.  1 root root       12 Mar 28 07:08 redis-sentinel -> redis-server
-rwxr-xr-x.  1 root root  9518952 Mar 28 07:08 redis-server
# 修改配置文件
# 打开任意外部访问
bind 0.0.0.0
# 打开以守护进程方式运行
daemonize yes
# 密码设置，测试或可靠环境可以不设置
requirepass 123456
# 工作目录。默认为当前目录，即运行redis-server命令在哪个目录，日志和持久化文件就保存在哪个目录下
dir .
# 日志文件，默认为空，不记录日志，可以指定日志文件名
logfile "redis.log"
# 启动redis服务
$ redis-server redis.conf
~~~

### 配置开机自启

~~~shell
$ vim /etc/systemd/system/redis/service
~~~

内容如下

~~~shell
[Unit]
Description=reids-server
After=network.target

[service]
Type=forking
ExecStart=/usr/local/bin/redis-server /opt/module/redis-6.2.6/redis.conf
PrivateTmp=true

[Install]
WantedBy=multi-user.target
~~~

重载系统服务

~~~shell
$ systemctl daemon-reload
~~~

现在可以使用如下命令操作redis

~~~shell
$ systemctl start redis
# 其他同systemctl命令
~~~

### Docker部署

1. 主从复制

~~~shell
# node1
version: '3.6'

services:
  master:
    image: redis
    container_name: redis-master
    restart: always
    command: redis-server --port 6000 --appendonly yes
    ports:
      - 6000:6000
    volumes:
      - ./data/master:/data

# node2
version: '3.6'

services:
  master:
    image: redis
    container_name: redis-slave
    restart: always
    command: redis-server --slaveof 192.168.113.130 6000 --port 6000 --appendonly yes
    ports:
      - 6000:6000
    volumes:
      - ./data/master:/data
      
# node3
version: '3.6'

services:
  master:
    image: redis
    container_name: redis-slave
    restart: always
    command: redis-server --slaveof 192.168.113.130 6000 --port 6000 --appendonly yes
    ports:
      - 6000:6000
    volumes:
      - ./data/master:/data
~~~

2. 哨兵模式

~~~shell
# 以主从复制模式开启redis
# 编写哨兵配置文件，创建文件夹
$ mkdir -p config/redis-sentinel
$ vim sentinel.conf

port 26379
daemonize no
pidfile "/var/run/redis-sentinel.pid"
logfile "sentinel-log"
dir "/tmp"
sentinel monitor mymaster 192.168.113.130 6001 2
~~~

编写docker-compose.yml

~~~yaml
# 编写docker-compose.yml
services:
  master-sentinel:
    image: redis
    container_name: master-sentinel
    restart: always
    command: redis-server --port 6001 --appendonly yes
    ports:
      - 6001:6001
    volumes:
      - ./data/master:/data
  redis-sentinel:
    image: redis
    container_name: redis-sentinel
    ports:
      - 26379:26379
    command: redis-sentinel /usr/local/etc/redis/sentinel.conf
    volumes:
      - ./config/redis-sentinel:/usr/local/etc/redis
~~~

## 架构文档

![redis主从复制](.\assert\redis主从复制.png)

![redis哨兵模式](.\assert\redis哨兵模式.png)

## Hello  World实战

## 学习原理

## 场景实战

## repeat

## 发布文档