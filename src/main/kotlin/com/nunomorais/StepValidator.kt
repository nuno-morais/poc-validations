package com.nunomorais

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonValue
import examples.PlayAudio
import sun.reflect.generics.reflectiveObjects.NotImplementedException
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
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

class ValidateEnum : Validator {
    override fun validate(target: Any, type: KType, vararg args: Any): ResultValidator{
        val constants = type.jvmErasure.java.enumConstants
        val properties = constants[0]::class.declaredMemberProperties

        properties.forEach { property ->
            var name = property.name
            if (target is Map<*,*> && target[name] == null){
                return ResultValidator(false, "MISSING_PROPERTY")
            }
        }

        val enumValues = constants.map { constant ->
            properties.fold(mapOf<String, Any?>()){ acc, elem ->
                acc + mapOf(elem.name to elem.getter.call(constant))
            }
        }

        val isValid = enumValues.find { it == target } != null
        if(!isValid){
            return ResultValidator(false, "ENUM_DOES_NOT_EXIST")
        }

        return ResultValidator(true)
    }
}

class StepValidatorProcessor {
    private val matchingType = MatchingType()
    private val validateEnum = ValidateEnum()

    fun validate(path: String, target: Any, type: KClass<*>): Map<String, Any> =
        when {
            target is Map<*, *> -> type.declaredMemberProperties.asSequence()
                .fold(mapOf()) { acc, it ->
                    val name = it.name.toSnakeCase()
                    acc + validate("${path}/${name}", target[name], it)
                }
            else -> throw Exception()
        }

    private fun validate(path: String, target: Any?, type: KProperty<*>): Map<String, Any> =
        when (target) {
            null -> validateNull(type, path)
            else -> validate(path, target, type.returnType).let {
                if (it.isEmpty()) {
                    applyFieldValidations(type, target, path)
                    // If empty, applyClassValidations...
                } else it
            }
        }

    private fun getClass(target: Map<*, *>, from: String) = try {
        val classPackageName = target[from]
        Class.forName(classPackageName as String).kotlin
    } catch (e: Exception) {
        null
    }

    private fun validate(path: String, target: Any, type: KType): Map<String, Any> =
        when {
            target is Map<*, *> && type.jvmErasure.hasJsonTypeInfo() ->
                getClass(target, type.jvmErasure.getJsonTypeInfo()!!)?.let {
                    validate(path, target, it)
                } ?: mapOf(path to listOf("NOT_FOUND_TYPE"))
            type.jvmErasure.isSubclassOf(Enum::class) -> validateEnum.validate(target, type).let {
                if  (!it.isValid) {
                    mapOf(path to listOf(it.error))
                } else mapOf()
            }
            target is Map<*, *> -> validate(path, target, type.jvmErasure)
            target is List<*> -> target.foldIndexed(mapOf()) { index, acc, element ->
                acc + validate("${path}[${index}]", element!!, type.arguments[0].type!!)
            }
            else -> matchingType.validate(target, type).let {
                if (!it.isValid) {
                    mapOf(path to listOf(it.error))
                } else mapOf()
            }
        }

    private fun validateEnums(path: String, type: KType, target:Any): Map<String, Any>{
        val constants = type.jvmErasure.java.enumConstants
        val properties = constants[0]::class.declaredMemberProperties

        val errors = properties.fold(mapOf<String,Any>()) { acc, property ->
            var name = property.name
            if(target is Map<*,*>){
                acc + validate("$path/$name", target[name], property)
            }
            else {
                acc + mapOf("$path/$name" to listOf("wrong_type"))
            }
        }

        if(errors.isNotEmpty()){
            return errors
        }

        val enumValues = constants.map { constant ->
            properties.fold(mapOf<String, Any?>()){ acc, elem ->
                acc + mapOf(elem.name to elem.getter.call(constant))
            }
        }

        val isValid = enumValues.find { it == target } != null
        if(!isValid){
            return mapOf("$path" to listOf("ENUM_NOT_FOUND"))
        }

       return emptyMap()
    }

    private fun validateNull(
        type: KProperty<*>,
        path: String
    ) = if (!type.returnType.isMarkedNullable) {
        mapOf(path to listOf("REQUIRED"))
    } else {
        emptyMap()
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
    val play_audio = mapOf(
        "message" to mapOf(
            "language" to mapOf("code" to "en-US", "cenas" to "rr")
        )
    )

    var validatorService = StepValidatorProcessor()
    println(validatorService.validate("", play_audio, PlayAudio::class))

  /*  val m = mapOf(
        "age" to 6,
        "bar" to mapOf(
            "name" to "hello1",
            "foo" to mapOf(
                "list" to listOf(1, 2, 3)
            )
        ),
        "bool" to false,
        "message" to mapOf(
            "@td-type" to "com.nunomorais.UrlMessage",
            "url" to "http://dummyurl.com/music.mp3"
        )
    )
    println(StepValidatorProcessor().validate("", m, Foo::class))*/
}
