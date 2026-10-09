import SwiftUI
struct AccountView:View {
    @ObservedObject var model:IOSAccountModel
    @State private var email = ""
    @State private var password = ""
    var body:some View {
        Section("Account") {
            if model.namespace != "guest"{Text("Signed in. Your workouts on this phone belong to this account.");Button("Sign out"){model.logout()}}
            else {
                Text("Sign in to use your account. You can keep using guest workouts.")
                TextField("Email",text:$email).textContentType(.username).keyboardType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled()
                SecureField("Password",text:$password).textContentType(.password)
                Button("Sign in"){let value = password;password = "";model.login(email,value)}.disabled(model.busy)
                if model.busy {ProgressView();Button("Cancel sign-in"){password = "";model.logout()}}
            }
            if let message = model.message {Text(message).foregroundStyle(.red)}
        }
    }
}
