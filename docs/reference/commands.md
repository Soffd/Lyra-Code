---
title: 命令执行工具
---

## 权限阶梯

| 工具 | 身份 | 适用场景 | 前置条件 |
| --- | --- | --- | --- |
| run_command | Termux 应用用户 | 构建、Git、脚本、包管理、内容搜索 | Termux 已授权 |
| execute_shell_command | Shizuku Shell（ADB 级别） | pm / cmd / dumpsys / 受保护路径 | Shizuku 已授权 |
| execute_root_command | root（su） | 系统文件、/data、系统包管理 | 已 root 且配置 su |

::: warning 权限纪律
不要为了通过命令而升级权限。能用 Termux 解决的不动用 Shizuku/Root。
:::

## run_command

在 Termux 中执行非交互命令，返回 exit_code / stdout / stderr。

::: tip 使用要点
- 默认工作目录是当前工作区，跨目录用 `workDir` 或绝对路径；
- 多行/缩进敏感命令用 `command_lines` 数组；
- 用 `&&` 串联有依赖的步骤，独立命令分开；
- 交互式程序不要运行；长驻服务用 `background=true`（返回 launcher_pid 与输出文件），**启动成功 ≠ 健康**，需另开命令检查进程/日志；
- 超时范围 5–600 秒；输出过长时重定向到文件再读：`cmd > out.txt 2>&1`；
- 高危操作（`rm -rf /`、写 `/dev/block`、`mkfs`）会被拦截，但人工审查仍需把关。
:::

## execute_shell_command

通过 Shizuku 以 ADB 权限执行系统命令，常用于 `pm`、`cmd`、`dumpsys` 等。变更前先只读检查现场。

## execute_root_command

以 root 执行。操作 /data、系统文件、系统包前，先确认精确目标并准备恢复方案（备份/快照）。

## 命令安全纪律

- 破坏性操作前：核对**确切目标路径**，把范围缩到最小；
- 优先可恢复操作（重命名而非删除、先备份）；
- 删除前 `ls -la` 确认目标；
- 失败先读错误分类再换参数重试，不盲目重跑。