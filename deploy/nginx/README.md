# TLS 证书

Nginx 容器从 `/etc/nginx/certs/` 读取 `fullchain.pem` 与 `privkey.pem`，compose 将本目录下的 `certs/` 挂载进去。证书文件已被 gitignore，禁止提交。

## 有域名（推荐）

```bash
# 服务器上先停占用 80 端口的服务
certbot certonly --standalone -d your-domain.example
mkdir -p deploy/nginx/certs
cp /etc/letsencrypt/live/your-domain.example/fullchain.pem deploy/nginx/certs/
cp /etc/letsencrypt/live/your-domain.example/privkey.pem deploy/nginx/certs/
```

证书续期后重新复制并执行 `docker compose restart nginx`。

## 无域名（自签，仅联调）

```bash
mkdir -p deploy/nginx/certs
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout deploy/nginx/certs/privkey.pem \
  -out deploy/nginx/certs/fullchain.pem \
  -subj "/CN=<公网IP>" \
  -addext "subjectAltName=IP:<公网IP>"
```

浏览器会提示证书不受信任；正式对外使用请换成受信任证书。
