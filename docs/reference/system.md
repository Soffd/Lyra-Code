---
title: 系统与运维工具
---

## 时间与设备

| 工具 | 用途 |
| --- | --- |
| get_current_time | 当前时间与时区，相对日期/时段判断用 |
| get_current_location | 最后已知位置，位置感知回答 |
| get_device_hardware_info | 系统/CPU/内存/存储/屏幕/电池诊断（不构成设备真伪证明） |
| list_installed_apps | 已安装应用列表（包名/版本/签名） |

## 微型服务器

```text
manage_mini_server(action=start|stop|status|update) + get_mini_server_status + read_mini_server_logs
```

- 静态站点根目录 = 当前工作区；`127.0.0.1` 仅本机，`0.0.0.0` 暴露局域网；
- 支持 Basic 认证、HTTPS、强制 HTTPS、SPA 回退；
- 排障看日志：404=路径问题，auth 失败=认证不匹配，JS 报错=脚本问题。

## 定时任务

```text
manage_scheduled_tasks(action=list|create|update|delete|enable|disable)
```

| schedule_type | 参数 |
| --- | --- |
| once | run_at |
| daily | hour + minute |
| weekly | day_of_week(1=周一,7=周日) + hour + minute |
| monthly | day_of_month + hour + minute |

任务可指定独立模型与资料，不出现在普通聊天记录中；触发时是新会话，提示词中写清输出位置与格式。

## 备份

```text
export_backup(destination=local|webdav) / import_backup(source=local|download|global|webdav)
```

- 安全导出（默认）不含密钥；完整迁移需显式 include_secrets；
- Agent 导入一律采用补充模式：非破坏、自动去重。

## 规划与记忆

| 工具 | 用途 |
| --- | --- |
| set_todo_list / update_todo_item | 多步任务进度管理（3–7 项） |
| read_memories / save_memory / update_memory / delete_memory | 跨会话持久偏好管理 |

::: tip 记忆使用原则
只保存明确、持久的偏好；不保存密钥、临时任务状态与一次性上下文。
:::