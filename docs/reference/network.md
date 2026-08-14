---
title: 下载与联网工具
---

## download_file

应用原生 HTTP/HTTPS 下载，支持重定向、请求头、进度与完整性校验。

| 参数 | 说明 |
| --- | --- |
| url | HTTP/HTTPS 地址 |
| path | 目标路径（工作区相对路径，或全局路径） |
| destination | workspace / global |
| headers | 如 `Authorization: Bearer xxx` |
| sha256 | 可选完整性校验，不匹配则失败 |
| timeout_seconds | 下载超时 |

::: tip
优先使用 `download_file` 而不是 curl/wget——自带进度、重定向与校验。
:::

## web_search

联网搜索，返回候选标题/URL/摘要。**摘要只是线索，不是最终证据**。

## read_web_page

打开网页读取正文。

::: warning 只信读到的内容
- 优先官方文档、权威来源；
- 页面被屏蔽（blocked_by_user）不要尝试绕过；
- 登录墙/动态渲染/内容过短时换来源或如实说明局限。
:::

## mark_web_sources

记录回答实际使用的网页来源，并在答案中附带链接。仅在确实使用了网页内容时调用。

## 网站黑名单

设置中的黑名单阻止 AI 打开指定域名：

- 精确匹配：`x.com` 只拦 `x.com`，`www.x.com` 需单独添加；
- 通配符：`*.x.com` 拦全部子域名；
- 全拦：同时填 `x.com` 与 `*.x.com`。