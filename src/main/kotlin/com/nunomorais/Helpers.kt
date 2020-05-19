package com.nunomorais

import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaField

fun String.toSnakeCase() = "[A-Z]".toRegex().findAll(this).let {
    var snackString = this
    it.forEach { matchResult ->
        snackString = snackString.replace(matchResult.value, "_${matchResult.value.toLowerCase()}")
    }
    snackString
}

fun KProperty<*>.annotations(lambda: (annotationName: String) -> Boolean = { true }) =
    this.javaField?.let {
        it.annotations.filter { annotation -> lambda(annotation.annotationClass.simpleName ?: "") }
    } ?: emptyList()