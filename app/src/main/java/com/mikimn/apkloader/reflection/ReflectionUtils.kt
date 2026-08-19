package com.mikimn.apkloader.reflection

import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Field
import java.lang.reflect.Method


fun <T> Class<T>.tryGetMethod(name: String, vararg parameterTypes: Class<*>): Method? {
    return try {
        HiddenApiBypass.getDeclaredMethod(this, name, *parameterTypes).apply {
            isAccessible = true
        }
    } catch (ex: NoSuchMethodException) {
        try {
            getMethod(name, *parameterTypes).apply {
                isAccessible = true
            }
        } catch (ex: NoSuchMethodException) {
            superclass?.let {
                superclass.tryGetMethod(name, *parameterTypes)
            }
        }
    }
}


fun <T> Class<T>.findMethodByName(name: String): Method? {
//    val found = declaredMethods.plus(methods).find { it.name.endsWith(name) }?.apply {
//        isAccessible = true
//    }
    val executables = HiddenApiBypass.getDeclaredMethods(this).map {
        if (it is Method) it else null
    }.filterNotNull()
    val found = executables.plus(methods).find { it.name.split(".").last().endsWith(name) }?.apply {
        isAccessible = true
    }

    return found ?: superclass?.findMethodByName(name)
}

fun <T> Class<T>.findMethodsByName(name: String): List<Method> {
    val executables = HiddenApiBypass.getDeclaredMethods(this).map {
        if (it is Method) it else null
    }.filterNotNull()
    val found = executables.plus(methods).filter { it.name.split(".").last().contains(name) }.map {
        it.isAccessible = true
        it
    }

    return found.plus(superclass?.findMethodsByName(name) ?: emptyList())
}


fun <T> Class<T>.tryGetField(name: String): Field? {
    return try {
        getDeclaredField(name).apply {
            isAccessible = true
        }
    } catch (ex: NoSuchFieldException) {
        try {
            getField(name).apply {
                isAccessible = true
            }
        } catch (ex: NoSuchFieldException) {
            superclass?.let {
                superclass.tryGetField(name)
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> Any.tryGetValue(fieldName: String): T? {
    val field = javaClass.tryGetField(fieldName)
    field?.isAccessible = true
    return field?.get(this) as T?
}