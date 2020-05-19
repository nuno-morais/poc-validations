package com.nunomorais

import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.jvmErasure

annotation class FieldValidation(
    val type: KClass<out Validator>
)

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
                    } else mapOf()
                }
            }
        }

    private fun applyFieldValidations(
        type: KProperty<*>,
        target: Map<*, *>,
        path: String
    ) = type.annotations { annotationName -> annotationName.endsWith("FieldValidation") }
        .fold(mapOf<String, Any>()) { acc, it ->
            it::class.memberProperties.find { prop -> prop.name == "type" }?.let { prop ->
                val validatorType = prop.getter.call(it) as KClass<out Validator>
                val customValidator = validatorType.createInstance()
                val result = customValidator.validate(target, type.returnType)
                acc + if (!result.isValid) {
                    mapOf(path to listOf(result.error))
                } else emptyMap()
            } ?: emptyMap()
        }
}