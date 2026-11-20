# 用户中心 Web

Vue 3 + Vite + TypeScript + Element Plus + Pinia + Vue Router。

## 开发

```bash
npm install
npm run dev
```

开发服务器默认在 `http://localhost:5173`，`/api` 代理到 `http://localhost:8080`。

## 脚本

```bash
npm run lint        # oxlint + eslint
npm run lint:fix    # 自动修复
npm run type-check  # vue-tsc
npm run test:unit   # Vitest
npm run build       # 类型检查 + 生产构建
npm run format      # Prettier
```

## 页面

- `/login`：登录、注册、忘记密码、恢复账号
- `/profile`：资料、修改密码、注销账号
- `/admin`：用户分页、状态过滤、封禁/解封、重置密码（仅管理员）

## 约定

- 令牌存 `sessionStorage`，只通过 `Authorization: Bearer` 发送。
- 错误按 `ProblemDetail.code` 分支，不依赖后端中文文案。
- 不使用 `v-html`；生产构建关闭 source map。
- 管理端路由守卫只做体验层，权限以后端 403 为准。
