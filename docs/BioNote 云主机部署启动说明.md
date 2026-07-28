# BioNote 云主机部署启动说明

## 1. 项目简介

BioNote 是一个生物实验记录协作平台。

当前部署环境：

- 操作系统：Windows Server
- 后端：Spring Boot 3.x
- Java：JDK 21
- 数据库：MySQL 8.0
- 前端：Vue + Vite
- Web服务器：Nginx

系统访问链路：

```
用户浏览器
      |
      ↓
Nginx :80
      |
      ↓
Spring Boot :8080
      |
      ↓
MySQL :3306
```

---

# 2. 项目目录结构

```
C:\Users\member1\BioNote

├── project
│   └── SE26Project-14
│       ├── backend
│       │   └── target
│       │       └── bionote-backend-0.1.0.jar
│       │
│       ├── frontend
│       │   └── dist
│       │
│       └── docs
│
├── environment
│   ├── jdk-21
│   ├── maven
│   ├── node
│   ├── nginx
│   └── mysql
│
├── start-backend.bat
├── start-backend-hidden.vbs
├── start-nginx.bat
└── start-mysql.bat
```

---

# 3. 启动顺序

启动顺序必须保持：

```
1. MySQL数据库
        ↓
2. Spring Boot后端
        ↓
3. Nginx前端代理
```

---

# 4. 启动MySQL数据库

## 方法一：CMD启动

打开 CMD：

执行：

```cmd
cd /d C:\Users\member1\BioNote\environment\mysql\bin
```

启动：

```cmd
mysqld.exe
```

保持该窗口运行。


## 检查MySQL状态

新开 CMD：

```cmd
netstat -ano | findstr :3306
```

正常结果：

```
TCP    0.0.0.0:3306     LISTENING
```


测试数据库连接：

```cmd
mysql -uroot -p
```

输入密码后进入 MySQL。


---

# 5. 启动Spring Boot后端

## 方法一：隐藏窗口启动（推荐）

执行：

```cmd
start C:\Users\member1\BioNote\start-backend-hidden.vbs
```


## 方法二：直接启动

执行：

```cmd
cd /d C:\Users\member1\BioNote
```

然后：

```cmd
start-backend.bat
```


启动参数：

```
--spring.profiles.active=dev
```


后端配置：

```
端口：
8080

数据库：
MySQL bionote
```


---

## 检查Spring Boot状态

执行：

```cmd
netstat -ano | findstr 8080
```

正常：

```
TCP    0.0.0.0:8080     LISTENING
```


健康检查：

```cmd
curl http://127.0.0.1:8080/actuator/health
```


正常返回：

```json
{
  "status":"UP"
}
```

---

# 6. 启动Nginx

进入目录：

```cmd
cd /d C:\Users\member1\BioNote\environment\nginx
```


启动：

```cmd
start nginx.exe
```


或者：

```cmd
start-nginx.bat
```


---

## 检查Nginx状态

执行：

```cmd
tasklist | findstr nginx
```


正常：

```
nginx.exe
```


检查80端口：

```cmd
netstat -ano | findstr :80
```


正常：

```
TCP    0.0.0.0:80     LISTENING
```

---

# 7. 系统访问地址

浏览器访问：

```
http://10.119.16.133
```


访问链路：

```
浏览器
 ↓
Nginx 80
 ↓
Spring Boot 8080
 ↓
MySQL 3306
```

---

# 8. 常见检查命令


## 查看Java进程

```cmd
tasklist | findstr java
```


## 查看MySQL进程

```cmd
tasklist | findstr mysqld
```


## 查看Nginx进程

```cmd
tasklist | findstr nginx
```


## 查看端口占用

Spring Boot:

```cmd
netstat -ano | findstr 8080
```


MySQL:

```cmd
netstat -ano | findstr :3306
```


Nginx:

```cmd
netstat -ano | findstr :80
```

---

# 9. 服务停止方式


## 停止Spring Boot

查看PID：

```cmd
tasklist | findstr java
```

停止：

```cmd
taskkill /PID <PID> /F
```


例如：

```cmd
taskkill /PID 11144 /F
```


---

## 停止MySQL

查看：

```cmd
tasklist | findstr mysqld
```

停止：

```cmd
taskkill /IM mysqld.exe /F
```


---

## 停止Nginx

执行：

```cmd
cd /d C:\Users\member1\BioNote\environment\nginx
```

然后：

```cmd
nginx -s stop
```

---

# 10. 部署验收流程

启动完成后依次测试：

## 1. 首页访问

```
http://10.119.16.133
```


## 2. 用户登录

确认：

- 登录成功
- JWT认证正常


## 3. 创建实验记录

确认：

- 实验记录创建成功
- 页面刷新后数据仍存在


## 4. 文件上传

确认：

- 附件上传成功
- 文件可以访问


## 5. 后端重启恢复

停止：

```cmd
taskkill /IM java.exe /F
```

重新启动：

```cmd
start C:\Users\member1\BioNote\start-backend-hidden.vbs
```

检查：

```cmd
curl http://127.0.0.1:8080/actuator/health
```

返回：

```json
{"status":"UP"}
```

---

# 11. 注意事项

当前部署采用手动启动方式。

云主机重启后，需要重新启动：

1. MySQL
2. Spring Boot
3. Nginx


推荐启动顺序：

```
MySQL
 ↓
Spring Boot
 ↓
Nginx
```


当前版本已经满足：

- 前后端分离部署
- 数据库持久化
- 公网访问
- API代理
- 用户认证
- 实验记录业务运行要求
