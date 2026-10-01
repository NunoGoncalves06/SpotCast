package com.swmad.spotcast.domain

@JvmInline
value class Email(val value: String) {
    fun isValid(): Boolean = EMAIL_REGEX.matches(value)

    companion object {
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

@JvmInline
value class Password(val value: String) {
    // Firebase rejects passwords shorter than 6 characters
    fun isValid(): Boolean = value.length >= MIN_LENGTH

    companion object {
        const val MIN_LENGTH = 6
    }
}

data class User(val id: String, val email: Email)

enum class Status { OK, ERROR }

data class AuthResult(val user: User?, val status: Status, val message: String? = null) {
    fun isOk(): Boolean = status == Status.OK && user != null
}
