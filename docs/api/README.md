# API 快照

`openapi.json` 由本地 `dev` profile 启动后导出：

```bash
curl -s http://localhost:8080/v3/api-docs -o docs/api/openapi.json
```

生产 profile 关闭了 springdoc，接口文档只在开发/联调环境可用。接口变更后重新导出并提交。
