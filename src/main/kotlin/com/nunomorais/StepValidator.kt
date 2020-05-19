package com.nunomorais

import com.fasterxml.jackson.annotation.JsonProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.jvmErasure

annotation class MinLengthFieldValidation(
    val type: KClass<out Validator>,
    val min: Int
)

annotation class FieldValidation

data class ResultValidator(
    val isValid: Boolean = true,
    val error: String = ""
)

interface Validator {
    fun validate(target: Any, type: KType, vararg args: Any): ResultValidator
}

class MatchingType : Validator {
    override fun validate(target: Any, type: KType, vararg input: Any) =
        if (!target::class.isSubclassOf(type.jvmErasure)) {
            ResultValidator(false, "WRONG_TYPE")
        } else ResultValidator()
}

class StepValidatorProcessor {
    private val matchingType = MatchingType()

    fun validate(path: String, target: Any, type: KClass<*>): Map<String, Any> =
        if (target is Map<*, *>) {
            type.declaredMemberProperties.asSequence()
                .fold(mapOf()) { acc, it ->
                    val name = it.name.toSnakeCase()
                    acc + validate("${path}/${name}", target[name], it)
                }
        } else {
            throw Exception()
        }

    private fun validate(path: String, target: Any?, type: KProperty<*>): Map<String, Any> =
        if (target == null) {
            if (!type.returnType.isMarkedNullable) {
                mapOf(path to listOf("REQUIRED"))
            } else {
                emptyMap()
            }
        } else {
            if (target is Map<*, *>) {
                validate(path, target, type.returnType.jvmErasure).let {
                    if (it.isEmpty()) {
                        applyFieldValidations(type, target, path)
                        // If empty, applyClassValidations...
                    } else it
                }
            } else {
                matchingType.validate(target, type.returnType).let {
                    if (!it.isValid) {
                        mapOf(path to listOf(it.error))
                    } else {
                        applyFieldValidations(type, target, path)
                    }
                }
            }
        }

    private fun applyFieldValidations(
        type: KProperty<*>,
        target: Any,
        path: String
    ) = type.annotations { annotationName -> annotationName.endsWith(FieldValidation::class.simpleName!!) }
        .fold(mapOf<String, Any>()) { acc, annotation ->
            annotation::class.memberProperties.find { prop -> prop.name == "type" }?.let { prop ->
                val validatorType = annotation.javaClass.getMethod(prop.name).invoke(annotation) as Class<out Validator>
                val customValidator = validatorType.newInstance()
                val args = annotation::class.memberProperties
                    .filter { p -> p.name != prop.name && p.name != "h" }
                    .map { argument ->
                        annotation.javaClass.getMethod(argument.name).invoke(annotation)
                    }.toTypedArray()
                val result = customValidator.validate(target, type.returnType, *args)
                acc + if (!result.isValid) {
                    mapOf(path to listOf(result.error))
                } else emptyMap()
            } ?: emptyMap()
        }
}

data class FooBar(
    val list: List<Int>
)

data class Bar(
    val name: String,
    val foo: FooBar
)

data class Foo(
    @field:MinLengthFieldValidation(type = MinLengthValidator::class, min = 5)
    val age: Int?,
    val bar: Bar,
    val bool: Boolean
)


class MinLengthValidator : Validator {
    override fun validate(target: Any, type: KType, vararg args: Any) =
        if (target is Number && args.first() is Number) {
            if (target.toDouble() < (args.first()!! as Number).toDouble()) {
                ResultValidator(false, "MIN_LENGTH")
            } else ResultValidator()
        } else {
            ResultValidator(false, "UNHANDLED_EXCEPTION")
        }
}

fun main() {
    val m = mapOf(
        "age" to 6,
        "bar" to mapOf(
            "name" to "hello1",
            "foo" to mapOf(
                "list" to listOf(1, 2, 3)
            )
        ),
        "bool" to false
    )
    println(StepValidatorProcessor().validate("", m, Foo::class))
}
