package tech.kiasolutions.workoutcoach.account
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.*
@Composable fun AccountView(model:AccountModel){
    val state by model.state.collectAsState();var email by remember{mutableStateOf("")};var password by remember{mutableStateOf("")}
    Text("Account",style=MaterialTheme.typography.headlineMedium)
    if(state.namespace!="guest"){
        Text("Signed in. Your workouts on this phone belong to this account.")
        OutlinedButton(onClick={model.logout()}){Text("Sign out")}
    }else{
        Text("Sign in to use your account. You can keep using guest workouts.")
        OutlinedTextField(email,{email=it},label={Text("Email")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),modifier=Modifier.fillMaxWidth())
        OutlinedTextField(password,{password=it},label={Text("Password")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        Button(onClick={val value=password;password="";model.login(email,value)},enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Sign in")}
        if(state.busy){CircularProgressIndicator();TextButton(onClick={password="";model.logout()}){Text("Cancel sign-in")}}
    }
    state.message?.let{Text(it,color=MaterialTheme.colorScheme.error)}
}
