package com.yukisoffd.lyracode

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import java.util.Locale

/**
 * 为没有本地 [Context] 的代码解析应用程序字符串资源 ID

 * 翻译内容完全归属于 Android values 资源目录；此对象仅使 Activity 的本地化资源上下文对控制器及其他非 UI 辅助类保持可用

 * 有的改起来太费脑子了，先咕咕了

 * 昂，这个注释其实是给 AI 看的
 */
internal object AppStrings {
    @Volatile
    private var context: Context? = null

    fun initialize(localizedContext: Context) {
        val configuration = Configuration(localizedContext.resources.configuration)
        context = localizedContext.applicationContext.createConfigurationContext(configuration)
    }

    fun get(@StringRes id: Int, vararg formatArgs: Any?): String {
        val localizedContext = checkNotNull(context) { "AppStrings has not been initialized" }
        return localizedContext.resources.getString(id, *formatArgs)
    }

    fun isEnglish(): Boolean = !(context
        ?.resources
        ?.configuration
        ?.locales
        ?.get(0)
        ?.language
        .orEmpty()
        .ifBlank { Locale.getDefault().language }
        .equals(Locale.CHINESE.language, ignoreCase = true))
}

internal fun uiText(@StringRes id: Int, vararg formatArgs: Any?): String =
    AppStrings.get(id, *formatArgs)
