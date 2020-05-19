package com.nunomorais

import com.fasterxml.jackson.annotation.JsonTypeInfo
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation
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

fun KClass<*>.hasJsonTypeInfo() = this.getJsonTypeInfo() != null

fun KClass<*>.getJsonTypeInfo() = this.findAnnotation<JsonTypeInfo>()?.let {
    if (it.use == JsonTypeInfo.Id.CLASS) {
        it.property
    } else null
}
