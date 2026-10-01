package com.swmad.spotcast.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.swmad.spotcast.domain.Email
import com.swmad.spotcast.domain.Password

@Composable
fun SignInScreen(
    isLoading: Boolean,
    error: String?,
    onSignIn: (Email, Password) -> Unit,
    onGoToSignUp: () -> Unit
) {
    // rememberSaveable keeps the input over rotation and while the screen is on the back stack
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    AuthLayout(title = "Sign in", subtitle = "Your spots and their forecast") {
        EmailField(value = email, onValueChange = { email = it }, enabled = !isLoading)
        PasswordField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            enabled = !isLoading
        )
        AuthError(validationError ?: error)
        SubmitButton(text = "Sign in", isLoading = isLoading) {
            val typedEmail = Email(email.trim())
            validationError = when {
                !typedEmail.isValid() -> "Enter a valid e-mail address"
                password.isEmpty() -> "Enter your password"
                else -> null
            }
            if (validationError == null) {
                onSignIn(typedEmail, Password(password))
            }
        }
        TextButton(onClick = onGoToSignUp, enabled = !isLoading) {
            Text("No account yet? Sign up")
        }
    }
}

@Composable
fun SignUpScreen(
    isLoading: Boolean,
    error: String?,
    onSignUp: (Email, Password) -> Unit,
    onGoToSignIn: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var repeatedPassword by rememberSaveable { mutableStateOf("") }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    AuthLayout(title = "Create account", subtitle = "Sign up with e-mail and password") {
        EmailField(value = email, onValueChange = { email = it }, enabled = !isLoading)
        PasswordField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            enabled = !isLoading
        )
        PasswordField(
            value = repeatedPassword,
            onValueChange = { repeatedPassword = it },
            label = "Repeat password",
            enabled = !isLoading
        )
        AuthError(validationError ?: error)
        SubmitButton(text = "Sign up", isLoading = isLoading) {
            val typedEmail = Email(email.trim())
            val typedPassword = Password(password)
            validationError = when {
                !typedEmail.isValid() -> "Enter a valid e-mail address"
                !typedPassword.isValid() ->
                    "The password needs at least ${Password.MIN_LENGTH} characters"
                password != repeatedPassword -> "The two passwords are not the same"
                else -> null
            }
            if (validationError == null) {
                onSignUp(typedEmail, typedPassword)
            }
        }
        TextButton(onClick = onGoToSignIn, enabled = !isLoading) {
            Text("Already have an account? Sign in")
        }
    }
}

// Shared frame for the two screens. The content is passed as a composable parameter (lesson 2).
@Composable
private fun AuthLayout(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SpotCast",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}

@Composable
private fun EmailField(value: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("E-mail") },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AuthError(message: String?) {
    if (message != null) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun SubmitButton(text: String, isLoading: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(text)
        }
    }
}
