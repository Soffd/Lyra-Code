import { defineConfig } from 'vitepress'

export default defineConfig({
  lang: 'zh-CN',
  base: '/Lyra-Code/',
  title: 'Lyra Code 使用文档',
  head: [
    ['link', { rel: 'icon', type: 'image/png', href: '/img/logo-256.png' }],
    ['meta', { name: 'theme-color', content: '#5b7cfa' }]
  ],
  description: '面向 Android 的本地 AI Agent 应用 · 详细使用指南',
  lastUpdated: true,
  cleanUrls: false,

  themeConfig: {
    logo: '/img/logo-256.png',
    siteTitle: 'Lyra Code 使用文档',
    nav: [
      { text: '指南', link: '/guide/01-简介与安装' }
    ],
    socialLinks: [
      { icon: 'github', link: 'https://github.com/lyracode-app/Lyra-Code' }
    ],
    sidebar: [
      {
        text: '使用指南',
        items: [
          { text: '简介与安装', link: '/guide/01-简介与安装' },
          { text: '界面与模型', link: '/guide/02-界面与模型' },
          { text: 'Agent 工具', link: '/guide/03-Agent工具' },
          { text: '文件操作与命令执行', link: '/guide/04-文件与命令' },
          { text: '远程集成', link: '/guide/05-远程集成' },
          { text: '微型服务器与 Skills', link: '/guide/06-服务器与Skills' },
          { text: '记忆、定时任务与备份', link: '/guide/07-记忆任务与备份' },
          { text: '统计、诊断与多模态', link: '/guide/08-统计与多模态' },
          { text: '安全与排障', link: '/guide/09-安全与排障' },
          { text: '高级工作流与附录', link: '/guide/10-高级工作流与附录' }
        ]
      },
      {
        text: '工具参考',
        items: [
          { text: '文件与目录', link: '/reference/files' },
          { text: '命令执行', link: '/reference/commands' },
          { text: '下载与联网', link: '/reference/network' },
          { text: '远程集成', link: '/reference/remote' },
          { text: '系统与运维', link: '/reference/system' }
        ]
      },
      {
        text: '其他',
        items: [
          { text: '更新日志', link: '/changelog' }
        ]
      }
    ],
    outline: {
      level: [2, 3],
      label: '本页目录'
    },
    footer: {
      message: 'AGPLv3-or-later',
      copyright: 'Lyra Code 使用文档'
    },
    search: {
      provider: 'local',
      options: {
        miniSearch: {
          tokenize: (text: string) => {
            const words = text.split(/[\s，。；：！？、,.!?;:()（）"'“”‘’\/\\\-_]+/u).filter(Boolean)
            const chars = [...text].filter((c) => /[\u4e00-\u9fff]/.test(c))
            return [...words, ...chars]
          }
        },
        locales: {
          zh: {
            translations: {
              button: { buttonText: '搜索文档', buttonAriaLabel: '搜索文档' },
              modal: {
                displayDetails: '显示详情',
                resetButtonTitle: '清除',
                backButtonTitle: '返回',
                noResultsText: '未找到相关结果',
                footer: {
                  selectText: '选择',
                  navigateText: '切换',
                  closeText: '关闭'
                }
              }
            }
          }
        }
      }
    },
    docFooter: {
      prev: '上一页',
      next: '下一页'
    },
    darkModeSwitchLabel: '主题',
    sidebarMenuLabel: '菜单',
    returnToTopLabel: '回到顶部'
  }
})