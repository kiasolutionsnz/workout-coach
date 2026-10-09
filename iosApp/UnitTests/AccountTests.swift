import XCTest
import WorkoutCore
@testable import WorkoutCoach
final class AccountTests:XCTestCase {
    func testNativeKeychainRoundTripAndCancelledSessionCannotRestoreAccount()throws{
        let store = KeychainSessionStore();try store.clear();defer{try? store.clear()}
        let session = AuthSession(userId:"11111111-1111-4111-8111-111111111111",accessToken:"synthetic-access-secret",refreshToken:"synthetic-refresh-secret",expiresEpochSeconds:500)
        try store.write(session:session);XCTAssertEqual(KeychainSessionStore().read()?.userId,session.userId)
        let machine = AuthMachine(store:store);XCTAssertNotNil(try machine.beginRestore());let attempt = machine.generation
        XCTAssertEqual(machine.namespace,"guest");_ = try machine.logout()
        XCTAssertFalse(try machine.accept(attempt:attempt,session:session,nowEpochSeconds:100));XCTAssertNil(store.read())
    }
}
