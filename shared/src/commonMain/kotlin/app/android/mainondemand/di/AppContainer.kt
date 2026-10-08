package app.android.mainondemand.di

import app.android.mainondemand.data.FakeBookingRepository
import app.android.mainondemand.data.FakeProfessionalRepository
import app.android.mainondemand.data.PersistentDraftRepository
import app.android.mainondemand.data.fake.FakeBackend
import app.android.mainondemand.data.fake.FaultController
import app.android.mainondemand.data.persistence.KeyValueStore
import app.android.mainondemand.domain.repository.BookingRepository
import app.android.mainondemand.domain.repository.DraftRepository
import app.android.mainondemand.domain.repository.ProfessionalRepository
import kotlin.time.Clock

/**
 * Manual dependency graph, created once per process by each platform entry point.
 * The platform supplies only the [KeyValueStore].
 */
class AppContainer(
    store: KeyValueStore,
    val clock: Clock = Clock.System,
) {
    val faults = FaultController()
    private val backend = FakeBackend(clock, store, faults)

    val professionalRepository: ProfessionalRepository = FakeProfessionalRepository(backend)
    val bookingRepository: BookingRepository = FakeBookingRepository(backend)
    val draftRepository: DraftRepository = PersistentDraftRepository(store)
}
