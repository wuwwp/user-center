# 文档地图

新会话按需读取，不要整目录加载。

## 当前

| 文件 | 用途 |
|---|---|
| `progress.md` | 当前状态、测试、下一步、阻塞项；会话开始先读 |
| `stage-6-handoff.md` | 当前阶段交接：CI、OpenAPI、Docker、压测、部署 |
| `plan.md` | 设计文档；按章节读 §4、§7、§8、§11 |
| `standards.md` | 编码规范；写代码前读 |
| `decisions.md` | 本地取舍记录（gitignore，不提交） |

## 历史

已完成的阶段交接放在 `archive/`，仅在追溯设计或排查历史问题时读：

- `archive/stage-2-handoff.md`：鉴权与防护
- `archive/stage-3-handoff.md`：管理员功能
- `archive/stage-4-handoff.md`：注销、恢复与审计
- `archive/stage-5-handoff.md`：Web 前端
