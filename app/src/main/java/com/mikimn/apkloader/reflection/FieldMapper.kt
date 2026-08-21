package com.mikimn.apkloader.reflection

import androidx.core.util.Predicate
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.Collections


/**
 * Utility methods for copying field values by name from a source object to a target object.
 * This is useful for emulating an object's state in another object, given they share
 * field names.
 */
object FieldMapper {
    fun <T : Any> copy(to: T, from: T, predicate: Predicate<Pair<Field, Any?>>? = null) {
        try {
            val fromFields = getAllFields(from.javaClass)
            val toFields = getAllFields(to.javaClass)

            fromFields.let {
                for (field in fromFields) {
                    val matchingField = toFields.firstOrNull {
                        it.name.split(".").last() == field.name.split(".")
                            .last() && it.type == field.type
                    }
                    matchingField?.let {
                        try {
                            field.isAccessible = true
                            matchingField.isAccessible = true
                            val data = field.get(from)
                            if (predicate?.test(matchingField to data) != false) {
                                matchingField.set(to, data)
                            }
                        } catch (e: IllegalAccessException) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    /**
     * Get all fields of a class, either declared in the class itself or in any of its
     * superclasses.
     */
    private fun getAllFields(paramClass: Class<*>): List<Field> {
        var theClass: Class<*>? = paramClass
        val fields = ArrayList<Field>()
        try {
            while (theClass != null) {
                theClass.declaredFields.filter { it.modifiers and Modifier.FINAL == 0 }.toTypedArray().let { Collections.addAll(fields, *it) }
                theClass.fields.filter { it.modifiers and Modifier.FINAL == 0 }.toTypedArray().let { Collections.addAll(fields, *it) }
                theClass = theClass.superclass
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return fields.toSet().toList()
    }
}