package com.xiyunmn.cwmhook.feature.chapterbackup

import java.lang.reflect.Field

internal fun Any.stringMethod(name: String): String? {
    return runCatching { javaClass.getMethod(name).invoke(this) as? String }.getOrNull()
}

internal fun Any.intMethod(name: String): Int {
    return runCatching { javaClass.getMethod(name).invoke(this) as? Int }.getOrNull() ?: -1
}

internal fun Any.booleanMethod(name: String): Boolean? {
    return runCatching { javaClass.getMethod(name).invoke(this) as? Boolean }.getOrNull()
}

internal fun Any.callNoArgMethod(name: String): Any? {
    return runCatching {
        findMethod(javaClass, name, emptyArray())?.also { it.isAccessible = true }?.invoke(this)
    }.getOrNull()
}

internal fun Any.callMethod(name: String, vararg args: Any?): Any? {
    val argTypes = args.map<Any?, Class<*>?> { it?.javaClass }.toTypedArray()
    return runCatching {
        findMethod(javaClass, name, argTypes)?.also { it.isAccessible = true }?.invoke(this, *args)
    }.getOrNull()
}

internal fun Any.declaredField(name: String): Any? {
    return runCatching {
        findField(javaClass, name)?.also { it.isAccessible = true }?.get(this)
    }.getOrNull()
}

internal fun Any.toChapterBackupBook(): ChapterBackupBook? {
    val bookId = stringMethod("getBook_id")?.takeIf { it.isNotBlank() } ?: return null
    return ChapterBackupBook(
        bookId = bookId,
        title = stringMethod("getBook_name") ?: bookId,
        author = stringMethod("getAuthor_name"),
    )
}

private fun findField(type: Class<*>, name: String): Field? {
    var current: Class<*>? = type
    while (current != null) {
        runCatching { current.getDeclaredField(name) }.getOrNull()?.let { return it }
        current = current.superclass
    }
    return null
}

private fun findMethod(type: Class<*>, name: String, argTypes: Array<Class<*>?>): java.lang.reflect.Method? {
    var current: Class<*>? = type
    while (current != null) {
        current.declaredMethods.firstOrNull { method ->
            method.name == name && method.parameterTypes.matches(argTypes)
        }?.let { return it }
        current = current.superclass
    }
    return null
}

private fun Array<Class<*>>.matches(args: Array<Class<*>?>): Boolean {
    if (size != args.size) {
        return false
    }
    return indices.all { index ->
        val argType = args[index] ?: return@all !this[index].isPrimitive
        this[index].wrapPrimitive().isAssignableFrom(argType.wrapPrimitive())
    }
}

private fun Class<*>.wrapPrimitive(): Class<*> {
    return when (this) {
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Character.TYPE -> java.lang.Character::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        java.lang.Void.TYPE -> java.lang.Void::class.java
        else -> this
    }
}
