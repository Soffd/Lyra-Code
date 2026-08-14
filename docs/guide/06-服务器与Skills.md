---
title: 微型服务器与 Skills
---

## 9. 内置微型服务器（Mini Server）


微型服务器是一个内置的 HTTP/HTTPS 静态文件服务器，根目录为**当前工作区**，用于预览和调试本地网站、文档站、生成的前端项目。

### 9.1 使用流程

1. 在对话中让 AI 启动服务器：`manage_mini_server(action=start)`，可指定端口与主机；
2. 获取访问地址：`get_mini_server_status` 返回本机 URL 与局域网 URL；
3. 预览调试：浏览器打开站点；
4. 出问题看日志：`read_mini_server_logs` 查看连接、资源加载、404、认证失败和 JS 报错；
5. 用完关闭：`manage_mini_server(action=stop)`。

### 9.2 配置项

| 配置 | 说明 | 安全提醒 |
| --- | --- | --- |
| host | `127.0.0.1` 仅本机；`0.0.0.0` 暴露到局域网/端口映射 | 暴露后局域网设备可访问 |
| port | 监听端口 | — |
| username/password | Basic 认证 | 空密码 = 无认证 |
| protocol | HTTP 或 HTTPS | HTTP 明文，凭据与内容可被截获 |
| tls 证书/私钥 | PEM 链或 keystore | 自签证书不被浏览器信任 |
| force_https | HTTP 重定向到 HTTPS | 需先配置证书 |
| spa_fallback | 单页应用回退到 index.html | 适合 Vue/React 构建产物 |
| mdns | 局域网发现名称 | — |

### 9.3 调试技巧

- **404 / 资源加载失败**：先看日志里请求的路径，确认文件确实存在、大小写一致；
- **认证失败**：检查 username/password 是否与 Basic Auth 匹配；
- **JS 报错**：日志会记录页面 JavaScript 错误，先修复语法/路径问题；
- **改动不生效**：静态服务器可能缓存，先 hard refresh（Ctrl+F5 / 清缓存）；
- **SPA 路由 404**：启用 spa_fallback；
- **VitePress/Vite 预览**：先用 `build` 生成静态产物到工作区，再启动服务器指向产物目录。

::: danger 安全提示
把服务器绑定到 `0.0.0.0`、内网穿透或公网前，务必检查服务目录是否包含敏感文件、是否设置了密码、是否启用 HTTPS。
:::

---


## 10. Skills 能力包


Skills 是给 Agent 的可选「技能包」，一个 Skill 包含 `SKILL.md` 说明文件和配套资源，让 Agent 在相关任务中按说明工作。

### 10.1 三种导入方式

1. **zip 包**：从文件导入（如 `小说SKILL.zip`）；
2. **单个 SKILL.md**：直接导入 Markdown 文件；
3. **Git 仓库链接**：从 GitHub / Gitee / GitLab 仓库导入。

也可以在应用内**手动编辑 SKILL.md** 创建 Skill。

### 10.2 SKILL.md 的标准结构

```markdown
---
name: my-skill
description: 一句话说明这个技能做什么、何时被触发
---

# 使用说明

（Agent 读取此文件后如何行动：步骤、规则、输出格式、注意事项）

## 参考文件

- prompts/template.md
- scripts/process.py

```

关键点：

- `name` / `description` 决定 Agent 是否会加载它——描述要写清适用场景；
- Agent 先读 SKILL.md 判断相关性，**按需**读取内部文件，避免上下文膨胀；
- 若当前任务与某 Skill 强相关，Agent 会调用 `list_skill_files` / `read_skill_file` 读取其内容。

### 10.3 编写高质量 Skill 的建议

- 描述用「当用户要做 X 时」句式，降低误触发；
- 把稳定的流程（步骤、格式、检查清单）写进 SKILL.md，减少重复对话；
- 大脚本/模板放到子文件，SKILL.md 只写索引与调用方式；
- 标注环境假设（桌面/云工具在 Android 上可能不可用，Agent 会适配）；
- 不要把密钥写进 Skill 文件。

### 10.4 启用与管理

- Skill 可单独启用/禁用，禁用后不再注入；
- 通过配置管理可以列出、更新、删除 Skill；
- 打包 Skill 时确认目录结构：根目录放 SKILL.md，子文件按相对路径引用。

---

