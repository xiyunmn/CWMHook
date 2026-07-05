package com.xiyunmn.cwmhook.feature.autosignin

import android.content.Context
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import java.lang.reflect.Proxy

internal class AutoSignInHostBridge(
    private val classLoader: ClassLoader,
) {
    data class HostUser(
        val readerId: String,
        val token: String,
    )

    data class HostResult(
        val message: String,
        val reward: Reward,
    )

    data class Reward(
        val hlb: String?,
        val exp: String?,
        val recommend: String?,
    ) {
        fun toToastSuffix(): String {
            val parts = buildList {
                if (!hlb.isNullOrBlank() && hlb != "0") {
                    add("代币 +$hlb")
                }
                if (!exp.isNullOrBlank() && exp != "0") {
                    add("经验 +$exp")
                }
                if (!recommend.isNullOrBlank() && recommend != "0") {
                    add("推荐票 +$recommend")
                }
            }
            return if (parts.isEmpty()) "" else "：" + parts.joinToString("，")
        }
    }

    interface Callback {
        fun onSuccess(result: HostResult)
        fun onFailure(message: String)
        fun onNetworkUnavailable()
    }

    fun currentUser(): HostUser? {
        return runCatching {
            val userClass = Class.forName(CiweiMaoClasses.LOGINED_USER, false, classLoader)
            val user = userClass.getMethod("getLoginedUser").invoke(null) ?: return null
            val token = user.javaClass.getMethod("getLoginToken").invoke(user) as? String
            val readerInfo = user.javaClass.getMethod("getReaderInfo").invoke(user) ?: return null
            val readerId = readerInfo.javaClass.getMethod("getReader_id").invoke(readerInfo) as? String
            if (token.isNullOrBlank() || readerId.isNullOrBlank()) {
                null
            } else {
                HostUser(readerId = readerId, token = token)
            }
        }.getOrElse { throwable ->
            ModuleFileLogger.e(TAG, "Failed to read host login state", throwable)
            null
        }
    }

    fun signIn(context: Context, callback: Callback) {
        runCatching {
            val baseTaskClass = Class.forName(CiweiMaoClasses.BASE_TASK_NEW, false, classLoader)
            val signTaskClass = Class.forName(CiweiMaoClasses.SIGN_TASK, false, classLoader)
            val successCallbackClass = Class.forName(
                CiweiMaoClasses.BASE_TASK_NEW + "\$AsyncTaskSuccessCallback",
                false,
                classLoader,
            )
            val failCallbackClass = Class.forName(
                CiweiMaoClasses.BASE_TASK_NEW + "\$AsyncTaskFailCallback",
                false,
                classLoader,
            )
            val nullCallbackClass = Class.forName(
                CiweiMaoClasses.BASE_TASK_NEW + "\$AsyncTaskResultNullCallback",
                false,
                classLoader,
            )

            val task = signTaskClass.getConstructor(Context::class.java).newInstance(context)
            baseTaskClass.getMethod("setShowProgressDialog", Boolean::class.javaPrimitiveType)
                .invoke(task, false)
            baseTaskClass.getMethod("setAsyncTaskSuccessCallback", successCallbackClass)
                .invoke(task, successProxy(successCallbackClass, callback))
            baseTaskClass.getMethod("setAsyncTaskFailCallback", failCallbackClass)
                .invoke(task, failProxy(failCallbackClass, callback))
            baseTaskClass.getMethod("setAsyncTaskResultNullCallback", nullCallbackClass)
                .invoke(task, nullProxy(nullCallbackClass, callback))

            baseTaskClass.getMethod("execute", Array<Any>::class.java)
                .invoke(task, arrayOf<Any>(1))
        }.onFailure { throwable ->
            ModuleFileLogger.e(TAG, "Failed to invoke host SignTask", throwable)
            callback.onFailure(throwable.message ?: "调用宿主签到接口失败")
        }
    }

    private fun successProxy(callbackClass: Class<*>, callback: Callback): Any {
        return Proxy.newProxyInstance(classLoader, arrayOf(callbackClass)) { _, method, args ->
            if (method.name == "successCallback") {
                val result = args?.firstOrNull()
                updateHostUser(result)
                callback.onSuccess(
                    HostResult(
                        message = result?.message().orEmpty(),
                        reward = result?.reward() ?: Reward(null, null, null),
                    ),
                )
            }
            null
        }
    }

    private fun failProxy(callbackClass: Class<*>, callback: Callback): Any {
        return Proxy.newProxyInstance(classLoader, arrayOf(callbackClass)) { _, method, args ->
            if (method.name == "failCallback") {
                callback.onFailure(args?.firstOrNull()?.message().orEmpty())
            }
            null
        }
    }

    private fun nullProxy(callbackClass: Class<*>, callback: Callback): Any {
        return Proxy.newProxyInstance(classLoader, arrayOf(callbackClass)) { _, method, _ ->
            if (method.name == "resultNullCallback") {
                callback.onNetworkUnavailable()
            }
            null
        }
    }

    private fun updateHostUser(result: Any?) {
        runCatching {
            val value = result?.value() ?: return
            val userClass = Class.forName(CiweiMaoClasses.LOGINED_USER, false, classLoader)
            val user = userClass.getMethod("getLoginedUser").invoke(null) ?: return
            val readerInfo = value.publicField("reader_info")
            val propInfo = value.publicField("prop_info")
            var changed = false
            if (readerInfo != null) {
                user.javaClass.getMethod("setReaderInfo", readerInfo.javaClass).invoke(user, readerInfo)
                changed = true
            }
            if (propInfo != null) {
                user.javaClass.getMethod("setPropInfo", propInfo.javaClass).invoke(user, propInfo)
                changed = true
            }
            if (changed) {
                user.javaClass.getMethod("saveToFile").invoke(user)
            }
        }.onFailure { throwable ->
            ModuleFileLogger.w(TAG, "Failed to update host user after sign-in", throwable)
        }
    }

    private fun Any.message(): String {
        return runCatching { javaClass.getMethod("getMessage").invoke(this) as? String }
            .getOrNull()
            .orEmpty()
    }

    private fun Any.value(): Any? {
        return runCatching { javaClass.getMethod("getValue").invoke(this) }.getOrNull()
    }

    private fun Any.reward(): Reward? {
        val value = value() ?: return null
        val bonus = value.publicField("bonus") ?: return null
        return Reward(
            hlb = bonus.stringMethod("getHlb"),
            exp = bonus.stringMethod("getExp"),
            recommend = bonus.stringMethod("getRecommend"),
        )
    }

    private fun Any.publicField(name: String): Any? {
        return runCatching { javaClass.getField(name).get(this) }.getOrNull()
    }

    private fun Any.stringMethod(name: String): String? {
        return runCatching { javaClass.getMethod(name).invoke(this) as? String }.getOrNull()
    }

    private companion object {
        const val TAG = "CWMHook.AutoSignInBridge"
    }
}
