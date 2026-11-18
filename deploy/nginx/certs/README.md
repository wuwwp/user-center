# TLS 证书

`default.conf` 使用以下两个文件：

- `fullchain.pem`
- `privkey.pem`

生产环境使用云证书或 Let's Encrypt 签发，放入本目录（已在 `.gitignore` 中忽略）。

本地/测试可生成自签名证书：

```bash
openssl req -x509 -nodes -newkey rsa:2048 -days 365 \
  -keyout privkey.pem -out fullchain.pem \
  -subj "/CN=localhost"
```

生成后启动完整编排：

```bash
docker compose up -d
```

只启动 MySQL/Redis 做本地开发时，用开发覆盖文件（Nginx 走 HTTP）：

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d mysql redis
```
