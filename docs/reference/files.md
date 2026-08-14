---
title: 文件与目录工具
---

## 概览

原生文件工具只能在**当前工作区**内操作（相对路径），`global_*` 系列可访问整个 `/storage/emulated/0` 共享存储（`Android/data`、`Android/obb`、`/data` 除外）。

## 目录与搜索

### list_directory

列出目录内容。用于确认目录结构，是最常用的探索工具。

### search_files

按**文件名/路径片段**搜索工作区。

::: tip 使用要点
- `query` 只放文件名或片段，`path` 为 `.` 或相对子目录；
- 只匹配文件名，**不搜索文件内容**；
- 返回 `SEARCH_EMPTY` 且目标可能在工作区外时，再用 `global_search_files` 全盘搜索一次。
:::

### global_search_files

在 `/storage/emulated/0` 范围内按文件名搜索，返回绝对路径（供 global_* 工具使用）。

### get_file_info

查看文件/目录元数据：大小、类型、修改时间、真实路径。

## 读取

### read_file

读取文本文件（≤ 1 MB）。适合中小文件整体阅读。

### read_file_lines

按行区间读取（支持最大 16 MB 文件）。**大文件先读片段定位，再精确编辑**。

## 写入与编辑

| 工具 | 场景 | 关键点 |
| --- | --- | --- |
| write_file | 创建新文件 / 全量替换 | `content` 与 `content_lines` 二选一，`content_lines` 必须是真实 JSON 数组 |
| edit_file | 精确修改 | 唯一文本匹配或 1-based 行区间；`expected_replacements` 不符会拒绝写入 |
| append_file | 末尾追加 | 适合日志、更新记录 |
| global_write_file / global_edit_file / global_append_file | 工作区外 | 需用户审批 |

::: warning 编辑前先读
修改前先 `read_file` / `read_file_lines` 确认现状；匹配数不符时重新读取，改用更长的唯一上下文，不要盲目重试。
:::

## 目录与移动

| 工具 | 说明 |
| --- | --- |
| create_folder | 创建目录 |
| rename_move | 重命名 / 移动（工作区内） |
| delete_file_or_folder | 删除文件或**空目录** |
| global_create_folder / global_rename_move / global_delete_file_or_folder | 工作区外版本，需审批 |

## 通用注意事项

- 不要向原生工具传 Termux 私有路径（`/data/data/com.termux`），会被拒绝；
- `Download` / `Downloads` 均映射到 `/storage/emulated/0/Download`；
- 覆盖修改前会自动生成 `.bak` 备份（同目录），重要操作可先显式备份；
- 批量删除/移动前先列出目标，核对路径再确认。