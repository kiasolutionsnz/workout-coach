import Foundation
import Security
import Combine
import WorkoutCore

enum AccountError:Error {case unavailable}
enum IOSAccountScope {static var namespace = "guest"}
final class KeychainSessionStore:NSObject,SecureSessionStore {
    private let base:[String:Any] = [kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:"tech.kiasolutions.workoutcoach.session",kSecAttrAccount as String:"active"]
    func read() throws -> AuthSession? {
        var query = base;query[kSecReturnData as String] = true;query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result:CFTypeRef?;let status = SecItemCopyMatching(query as CFDictionary,&result)
        if status == errSecItemNotFound{return nil}
        guard status == errSecSuccess,let data = result as? Data,let value = try JSONSerialization.jsonObject(with:data) as? [String:Any],let id = value["id"] as? String,let access = value["access"] as? String,let refresh = value["refresh"] as? String,let expiry = value["expiry"] as? NSNumber,UUID(uuidString:id) != nil,expiry.int64Value>0,!access.isEmpty,!refresh.isEmpty else{throw AccountError.unavailable}
        return AuthSession(userId:id,accessToken:access,refreshToken:refresh,expiresEpochSeconds:expiry.int64Value)
    }
    func write(session:AuthSession) throws {
        let data = try JSONSerialization.data(withJSONObject:["id":session.userId,"access":session.accessToken,"refresh":session.refreshToken,"expiry":session.expiresEpochSeconds])
        let update:[String:Any] = [kSecValueData as String:data]
        let status = SecItemUpdate(base as CFDictionary,update as CFDictionary)
        if status == errSecItemNotFound {
            var query = base;query[kSecValueData as String] = data;query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            guard SecItemAdd(query as CFDictionary,nil) == errSecSuccess else{throw AccountError.unavailable}
        }else if status != errSecSuccess{throw AccountError.unavailable}
    }
    func clear() throws {let status = SecItemDelete(base as CFDictionary);guard status == errSecSuccess || status == errSecItemNotFound else{throw AccountError.unavailable}}
}
enum IOSAccountAPI {
    static let base = (Bundle.main.object(forInfoDictionaryKey:"WORKOUT_API_BASE_URL") as? String) ?? "https://api.example.invalid"
    private static func request(_ path:String,body:[String:Any]? = nil,access:String? = nil)throws->[String:Any]{
        var request = URLRequest(url:URL(string:base+path)!);request.timeoutInterval = 15
        request.setValue("WorkoutCoach/0.1",forHTTPHeaderField:"User-Agent");request.setValue("application/json",forHTTPHeaderField:"Content-Type")
        if let access {request.setValue("Bearer \(access)",forHTTPHeaderField:"Authorization")}
        if let body{request.httpMethod = "POST";request.httpBody = try JSONSerialization.data(withJSONObject:body)}
        let configuration = URLSessionConfiguration.ephemeral;configuration.httpShouldSetCookies = false;configuration.urlCache = nil
        let session = URLSession(configuration:configuration);defer{session.invalidateAndCancel()}
        let semaphore = DispatchSemaphore(value:0);var answer:Data?;var code = 0
        let task = session.dataTask(with:request){data,response,_ in answer = data;code = (response as? HTTPURLResponse)?.statusCode ?? 0;semaphore.signal()};task.resume()
        guard semaphore.wait(timeout:.now()+20) == .success,(200...299).contains(code),let data = answer,data.count<=65536 else{throw AccountError.unavailable}
        if data.isEmpty{return [:]}
        guard let value = try JSONSerialization.jsonObject(with:data) as? [String:Any] else{throw AccountError.unavailable};return value
    }
    private static func verified(_ token:[String:Any])throws->AuthSession{
        guard let access = token["access_token"] as? String,let refresh = token["refresh_token"] as? String,let seconds = token["expires_in"] as? NSNumber,let original = token["user"] as? [String:Any] else{throw AccountError.unavailable}
        let user = try request("/auth/v1/user",access:access)
        guard let id = user["id"] as? String,user["role"] as? String == "authenticated",original["id"] as? String == id,UUID(uuidString:id) != nil,(1...3600).contains(seconds.intValue) else{throw AccountError.unavailable}
        let now = Int64(Date().timeIntervalSince1970);let expiry = (token["expires_at"] as? NSNumber)?.int64Value ?? now+seconds.int64Value
        guard expiry>now && expiry<=now+3600 else{throw AccountError.unavailable}
        return AuthSession(userId:id,accessToken:access,refreshToken:refresh,expiresEpochSeconds:expiry)
    }
    static func login(_ email:String,_ password:String)throws->AuthSession{try verified(request("/auth/v1/token?grant_type=password",body:["email":email,"password":password]))}
    static func refresh(_ session:AuthSession)throws->AuthSession{try verified(request("/auth/v1/token?grant_type=refresh_token",body:["refresh_token":session.refreshToken]))}
    static func logout(_ session:AuthSession)throws{_ = try request("/auth/v1/logout",body:[:],access:session.accessToken)}
}
final class IOSAccountModel:ObservableObject {
    @Published private(set) var namespace = "guest"
    @Published private(set) var busy = false
    @Published private(set) var message:String?
    private let machine = AuthMachine(store:KeychainSessionStore())
    private let network = DispatchQueue(label:"workout.account.network")
    private var timer:Timer?
    init(){
        do {if let saved = try machine.beginRestore(){exchange(machine.generation){try IOSAccountAPI.refresh(saved)}}}catch{publish("Couldn’t restore the account. Guest workouts are available.")}
        timer = Timer.scheduledTimer(withTimeInterval:30,repeats:true){[weak self]_ in
            guard let self,self.machine.status == .signedIn else{return}
            let now = Int64(Date().timeIntervalSince1970)
            if self.machine.session(nowEpochSeconds:now).map({$0.expiresEpochSeconds-now<60}) ?? true{self.refresh()}
        }
    }
    private func publish(_ message:String? = nil){namespace = machine.namespace;IOSAccountScope.namespace = namespace;busy = machine.status == .checking || machine.status == .signingIn;self.message = message}
    private func exchange(_ attempt:Int64,_ action:@escaping ()throws->AuthSession){publish();network.async{[weak self] in
        do {let session = try action();DispatchQueue.main.async{guard let self else{return};do{let accepted = try self.machine.accept(attempt:attempt,session:session,nowEpochSeconds:Int64(Date().timeIntervalSince1970));self.publish();if !accepted{self.network.async{try? IOSAccountAPI.logout(session)}}}catch{self.publish("Secure account storage is unavailable. Guest workouts are available.")}}}
        catch {DispatchQueue.main.async{guard let self,attempt == self.machine.generation else{return};try? self.machine.fail(attempt:attempt);self.publish("Couldn’t sign in. Check your details or try again. Guest workouts are available.")}}
    }}
    func login(_ email:String,_ password:String){guard !email.isEmpty && !password.isEmpty else{publish("Enter your email and password.");return};do{let attempt = try machine.beginLogin();exchange(attempt){try IOSAccountAPI.login(email.trimmingCharacters(in:.whitespacesAndNewlines),password)}}catch{publish("Secure account storage is unavailable. Guest workouts are available.")}}
    func refresh(){do{if let saved = try machine.beginRefresh(){exchange(machine.generation){try IOSAccountAPI.refresh(saved)}}}catch{publish("Couldn’t refresh the account. Guest workouts are available.")}}
    func logout(){do{let old = try machine.logout();publish();if let old{network.async{[weak self] in do{try IOSAccountAPI.logout(old)}catch{DispatchQueue.main.async{self?.publish("Signed out on this phone. Server sign-out could not be confirmed.")}}}}}catch{publish("Couldn’t clear secure storage. Try again.")}}
    deinit{timer?.invalidate()}
}
