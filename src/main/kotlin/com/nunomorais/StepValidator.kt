package com.nunomorais

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonValue
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

class StepValidatorProcessor {
    private val matchingType = MatchingType()

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
    val bool: Boolean,
    val message: Message
)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.CLASS,
    property = "@td-type"
)
sealed class Message

enum class Language(
    @field:JsonValue
    val code: String
) {
    EN_US("en-US"),
    EN_UK("en-UK"),
    PT_PT("pt-PT")
}

class TextMessage(
    val text: String,
    val language: Language
) : Message()

class UrlMessage(
    val url: String
) : Message()

enum class TalkdeskResources(
    @field:JsonValue
    val code: String
) {
    Asset("asset")
}

class AssetReference(
    val id: String,
    val type: TalkdeskResources
)

class AudioMessage(
    val reference: AssetReference
) : Message()

/*
data class Language(
    val code: String
)

data class Message(
    val text: String,
    val languages: List<Language>
)

data class PlayAudio(
    val message: Message
)
*/
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
    /*val play_audio = mapOf(
        "message" to mapOf(
            "text" to "Message",
            "languages" to listOf(
                mapOf("code" to "ui"),
                mapOf("code" to 2),
                mapOf("code" to 3),
                mapOf("code" to "ui")
            )
        ),
        "prop" to "Hello"
    )

    var validatorService = StepValidatorProcessor()
    println(validatorService.validate("", play_audio, PlayAudio::class))
*/
    val m = mapOf(
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
    println(StepValidatorProcessor().validate("", m, Foo::class))
}
