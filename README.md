# Training Admin AI

基于SpringBoot的AI管理平台，采用DDD架构设计，支持多AI提供商接入。

## 技术栈

- **JDK**: 21
- **Spring Boot**: 3.2.0
- **数据库**: MySQL
- **缓存**: Redis
- **鉴权**: Spring Security + JWT
- **监控**: Actuator + Micrometer + Prometheus
- **API**: RESTful风格
- **HTTP客户端**: OpenFeign
- **其他**: Hutool、Lombok、MapStruct

## 项目架构

采用DDD分层架构：

```
com.training.ai
├── domain              # 领域层
│   ├── user           # 用户聚合
│   └── ai             # AI聚合
├── application        # 应用层
│   ├── service        # 应用服务
│   └── dto            # 数据传输对象
├── infrastructure     # 基础设施层
│   ├── ai             # AI客户端实现
│   ├── config         # 配置类
│   └── security       # 安全相关
└── interfaces         # 接口层
    └── controller     # 控制器
```

## 快速开始

### 1. 数据库配置

创建数据库：
```sql
CREATE DATABASE training_admin CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

修改 `application.yml` 中的数据库连接信息：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/training_admin
    username: your_username
    password: your_password
```

### 2. Redis配置

确保Redis服务已启动，修改 `application.yml` 中的Redis配置：
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: your_password
```

### 3. AI配置

配置AI API密钥：
```yaml
ai:
  openai:
    api-key: your-openai-api-key
  qwen:
    api-key: your-qwen-api-key
  bigmodel:
    api-key: your-bigmodel-api-key
```

### 4. 启动项目

```bash
mvn clean install
mvn spring-boot:run
```

## API接口

### 认证接口

- `POST /api/auth/register` - 用户注册
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/refresh` - 刷新Token

### AI接口

- `POST /api/ai/chat` - AI对话
- `GET /api/ai/records` - 获取AI记录

### 监控接口

- `GET /api/actuator/health` - 健康检查
- `GET /api/actuator/metrics` - 指标数据
- `GET /api/actuator/prometheus` - Prometheus数据

## 主要特性

### 1. JWT鉴权

- 使用JWT进行用户认证
- Token和RefreshToken机制
- 自动Token刷新

### 2. 多AI提供商支持

- 策略模式实现多AI提供商接入
- 支持OpenAI、通义千问等
- 易于扩展新的AI提供商

### 3. Redis缓存

- 使用Redis作为缓存
- 配置了序列化策略
- 支持缓存注解

### 4. 监控

- 集成Spring Boot Actuator
- 支持Prometheus指标导出
- 便于性能监控和问题排查

### 5. 全局异常处理

- 统一异常处理机制
- 友好的错误信息返回
- 参数校验异常处理

## 开发规范

### 代码风格

- 使用Lombok简化代码
- 使用MapStruct进行对象映射
- 使用Builder模式创建对象
- 遵循阿里巴巴Java开发规范

### 设计模式应用

- **工厂模式**: AiClientFactory
- **策略模式**: 多AI提供商接入
- **建造者模式**: 对象构建
- **模板方法模式**: Repository基类
- **适配器模式**: 第三方API适配

### 数据库设计

- 使用JPA作为ORM框架
- 实体类包含审计字段
- 使用乐观锁防止并发问题
- 支持软删除

## 配置说明

### JWT配置
```yaml
jwt:
  secret: your-secret-key-must-be-at-least-256-bits-long
  expiration: 86400000        # 24小时
  refresh-expiration: 604800000  # 7天
```

## 部署

### Docker部署

```bash
docker build -t training-admin-ai .
docker run -p 8080:8080 training-admin-ai
```

### JVM参数

```bash
java -Xms512m -Xmx1024m -jar training-admin-ai-1.0.0.jar
```

## 常见问题

1. **数据库连接失败**: 检查数据库服务是否启动，配置是否正确
2. **Redis连接失败**: 检查Redis服务是否启动，密码是否正确
3. **AI API调用失败**: 检查API密钥是否正确，网络是否正常

## 贡献

欢迎提交Issue和Pull Request。

## 许可证

MIT License
