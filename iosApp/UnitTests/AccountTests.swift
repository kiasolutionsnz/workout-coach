import XCTest
import WorkoutCore
@testable import WorkoutCoach
final class AccountTests:XCTestCase {
    func testOfflineBetaDoesNotReadOrModifyStoredIdentity(){
        let store = OfflineProbeStore();let model = IOSAccountModel(offlineBeta:true,store:store)
        model.login("synthetic@example.invalid","synthetic-password");model.refresh();model.logout()
        XCTAssertEqual(model.namespace,"guest");XCTAssertEqual(IOSAccountScope.namespace,"guest")
        XCTAssertFalse(model.accountsEnabled);XCTAssertFalse(model.busy)
        XCTAssertEqual(store.calls,0)
    }
    func testNativeKeychainRoundTripAndCancelledSessionCannotRestoreAccount()throws{
        let store = KeychainSessionStore();try store.clear();defer{try? store.clear()}
        let session = AuthSession(userId:"11111111-1111-4111-8111-111111111111",accessToken:"synthetic-access-secret",refreshToken:"synthetic-refresh-secret",expiresEpochSeconds:500)
        try store.write(session:session);XCTAssertEqual(KeychainSessionStore().read()?.userId,session.userId)
        let machine = AuthMachine(store:store);XCTAssertNotNil(try machine.beginRestore());let attempt = machine.generation
        XCTAssertEqual(machine.storageNamespace,"guest");_ = try machine.logout()
        XCTAssertFalse(try machine.accept(attempt:attempt,session:session,nowEpochSeconds:100));XCTAssertNil(store.read())
    }
}

private final class OfflineProbeStore:NSObject,SecureSessionStore {
    var calls = 0
    func read()->AuthSession?{calls += 1;return nil}
    func write(session:AuthSession)throws{calls += 1}
    func clear()throws{calls += 1}
}
