package com.nunomorais.examples

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonValue
import com.nunomorais.MinLengthFieldValidation
import com.nunomorais.MinLengthValidator

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
    val messages: List<Message>
)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.CLASS,
    property = "@td-type"
)
sealed class Message

class TextMessage(
    val text: String,
    val language: Language
) : Message()

class UrlMessage(
    val url: String
) : Message()

data class PlayAudio(
    val message: Message
)

enum class Language(
    @field:JsonValue
    val code: String
) {
    EN_US("en-US"),
    EN_UK("en-UK"),
    PT_PT("pt-PT")
}

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

