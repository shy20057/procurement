# 本地基础服务连接信息

> 仅用于本机开发环境。部署到测试或生产环境前，必须更换所有默认密码，且不要把真实环境密码提交到代码仓库。

## PostgreSQL

| 配置项 | 值 |
| --- | --- |
| Host | `127.0.0.1` |
| Port | `5433` |
| Database | `procurement` |
| Username | `procurement` |
| Password | `change-me-postgres` |
| 主要业务 Schema | `supplier_schema` |

JDBC URL：

```text
jdbc:postgresql://127.0.0.1:5433/procurement
```

适用客户端：DBeaver、DataGrip、Navicat。

## Redis

| 配置项 | 值 |
| --- | --- |
| Host | `127.0.0.1` |
| Port | `6381` |
| Username | `default`（客户端不要求时可以留空） |
| Password | `change-me-redis` |
| Database | `0` |
| SSL | 关闭 |

连接 URI：

```text
redis://default:change-me-redis@127.0.0.1:6381/0
```

适用客户端：RedisInsight、Another Redis Desktop Manager。

## RabbitMQ

| 配置项 | 值 |
| --- | --- |
| Host | `127.0.0.1` |
| AMQP Port | `5672` |
| Management Port | `15672` |
| Username | `procurement` |
| Password | `change-me-rabbitmq` |
| Virtual Host | `procurement` |
| SSL | 关闭 |

管理后台：<http://localhost:15672>

## 常用命令

查看容器状态：

```powershell
docker compose --env-file .env -f deploy\docker-compose.yml ps
```

启动日常所需的三个基础服务：

```powershell
docker compose --env-file .env -f deploy\docker-compose.yml up -d postgres redis rabbitmq
```

停止基础服务：

```powershell
.\scripts\stop-infrastructure.ps1
```

配置的实际来源为项目根目录下的 `.env`。如果以后修改 `.env` 中的端口、用户名或密码，也要同步更新本文档。
