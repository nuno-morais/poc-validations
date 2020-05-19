package com.nunomorais

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SNAKE_CASE
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.jvmErasure
/*
data class FooBar(
    @JsonProperty("prop_bar")
    val propBar: List<Int>
)

data class Bar(
    val propB1: String,
    val propB2: FooBar
)

data class Foo(
    @field:Validate(MinValidator::class)
    val propA: Int?,
    @field:Validate(CustomBarValidator::class)
    val propB: Bar,
    val propC: Boolean
)

annotation class Validate(
    val type: KClass<out Validator>
)

class MinValidator : Validator {
    override fun validate(target: Any, type: KType) =
        if (target is Number) {
            if (target.toFloat() > 10) {
                ResultValidator()
            } else ResultValidator(false, "MIN")
        } else {
            ResultValidator(false, "MIN")
        }
}

class CustomBarValidator : Validator {
    override fun validate(target: Any, type: KType): ResultValidator =
        jacksonObjectMapper().apply {
            registerModule(KotlinModule())
            propertyNamingStrategy = SNAKE_CASE
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
        }.run {
            val str = this.writeValueAsString(target)
            val bar = this.readValue(str, Bar::class.java)
            if (bar.propB1 == "hello")
                ResultValidator()
            else
                ResultValidator(false, "CUSTOM_ERROR")
        }
}

inline fun <reified T> Map<String, Any>.fetch(propertyPath: String): T? =
    propertyPath.split(".").let { properties ->
        val lastProperty = properties.last()
        val lastMap = properties
            .dropLast(1)
            .fold(this) { subMap, prop ->
                if (subMap.isEmpty())
                    subMap
                else
                    @Suppress("UNCHECKED_CAST")
                    subMap[prop] as? Map<String, Any> ?: emptyMap()
            }
        lastMap[lastProperty].takeIf { it is T } as T?
    }

fun String.toSnakeCase() = "[A-Z]".toRegex().findAll(this).let {
    var snackString = this
    it.forEach { matchResult ->
        snackString = snackString.replace(matchResult.value, "_${matchResult.value.toLowerCase()}")
    }
    snackString
}

data class ResultValidator(
    val isValid: Boolean = true,
    val error: String = ""
)

interface Validator {
    fun validate(target: Any, type: KType): ResultValidator
}

class MatchingType : Validator {
    override fun validate(target: Any, type: KType) =
        if (!target::class.isSubclassOf(type.jvmErasure)) {
            ResultValidator(false, "WRONG_TYPE")
        } else ResultValidator()

}

val validators = listOf<Validator>(
    MatchingType()
)

fun validator(path: String, target: Any?, type: KProperty<*>): List<Map<String, List<String>>> {
    if (target == null) {
        return if (!type.returnType.isMarkedNullable) {
            listOf(mapOf(path to listOf("REQUIRED")))
        } else {
            emptyList()
        }
    }

    if (target is Map<*, *>) {
        val errors = validator(path, target, type.returnType.jvmErasure)
        if (errors.isEmpty()) {
            val annotations = type.javaField?.getAnnotationsByType(Validate::class.java) ?: emptyArray()
            annotations.forEach {
                // TODO: Get Custom Validator...
                val customValidator = it.type.createInstance()
                val result = customValidator.validate(target, type.returnType)
                if (!result.isValid) {
                    return listOf(mapOf(path to listOf(result.error)))
                }
            }
        }
        return errors
    } else {
        for (validatorX in validators) {
            val result = validatorX.validate(target, type.returnType)
            if (!result.isValid) {
                return listOf(mapOf(path to listOf(result.error)))
            }
        }
    }

    return emptyList()
}

fun validator(path: String, target: Any?, type: KClass<*>): List<Map<String, List<String>>> {
    val errors = mutableListOf<Map<String, List<String>>>()

    if (target is Map<*, *>) {
        type.declaredMemberProperties.forEach {
            val name = it.name.toSnakeCase()
            validator("${path}/${name}", target[name], it).forEach { error ->
                errors.add(error)
            }
        }

        val mapMembers = target.keys
    } else {

    }

    return errors
}

fun main() {
    val m = mapOf(
        "prop_a" to 1,
        "prop_b" to mapOf(
            "prop_b1" to "hello1",
            "prop_b2" to mapOf(
                "prop_bar" to listOf(1, 2, 3)
            )
        ),
        "prop_c" to false
    )
    println(validator("", m, Foo::class))

    val propA: Int? = m.fetch("prop_a")
    val propB1: String? = m.fetch("prop_b.prop_b1")
    val propB21_1: Int? = m.fetch<List<Int>>("prop_b.prop_b2.prop_b21")?.get(0)
    val invalidType: String? = m.fetch("prop_b.prop_b1.prop_b21")
    val invalidProperty: String? = m.fetch("invalid.path")

    println(propA) // 1
    println(propB1) // "hello"
    println(propB21_1) // 1
    println(invalidType) // null
    println(invalidProperty) // null
}*/