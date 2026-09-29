package market.sisu.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import market.sisu.common.ApiException;
import market.sisu.prediction.CreatePredictionRequest;
import market.sisu.prediction.Prediction;
import market.sisu.prediction.PredictionDetail;
import market.sisu.prediction.PredictionRepository;
import market.sisu.prediction.PredictionService;
import market.sisu.prediction.PredictionStatus;
import market.sisu.user.User;
import market.sisu.user.UserRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Creating a prediction (User Story 4; FR-012 through FR-015) without a database. */
class PredictionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    private PredictionRepository predictions;
    private UserRepository users;
    private PredictionService service;
    private User creator;

    @BeforeEach
    void setUp() {
        predictions = mock(PredictionRepository.class);
        users = mock(UserRepository.class);
        service = new PredictionService(predictions, users, Clock.fixed(NOW, ZoneOffset.UTC));
        creator = new User();
        creator.id = new ObjectId();
        creator.username = "matti";
        when(users.findById(creator.id)).thenReturn(creator);
    }

    @Test
    void createsAnOpenPredictionWithEmptyPoolsAttributedToTheCreator() {
        Instant closingAt = NOW.plusSeconds(86_400).plusNanos(123_456_789);

        PredictionDetail detail = service.create(
                creator.id, new CreatePredictionRequest("  Sataako huomenna?  ", " Helsinki ", " Mittaus ", closingAt));

        ArgumentCaptor<Prediction> saved = ArgumentCaptor.forClass(Prediction.class);
        verify(predictions).persist(saved.capture());
        Prediction prediction = saved.getValue();
        assertEquals(creator.id, prediction.creatorId);
        assertEquals("Sataako huomenna?", prediction.question);
        assertEquals("Helsinki", prediction.description);
        assertEquals("Mittaus", prediction.resolutionCriteria);
        assertEquals(Instant.parse("2026-09-29T12:00:00.123Z"), prediction.closingAt);
        assertEquals(PredictionStatus.OPEN, prediction.status);
        assertEquals(0, prediction.yesPoolSisu);
        assertEquals(0, prediction.noPoolSisu);
        assertEquals(NOW, prediction.createdAt);

        assertEquals(prediction.id.toHexString(), detail.id());
        assertEquals("matti", detail.creatorUsername());
        assertEquals(PredictionStatus.OPEN, detail.status());
        assertEquals(0, detail.totalSisuWagered());
        assertEquals(50.0, detail.oddsYesPercent());
        assertNull(detail.winningOutcome());
    }

    @Test
    void missingDescriptionIsStoredAsEmpty() {
        PredictionDetail detail = service.create(
                creator.id, new CreatePredictionRequest("Kysymys?", null, "Peruste", NOW.plusSeconds(60)));

        assertEquals("", detail.description());
    }

    @Test
    void rejectsEmptyOrBlankQuestion() {
        assertRejected("QUESTION_REQUIRED", new CreatePredictionRequest(null, "", "Peruste", NOW.plusSeconds(60)));
        assertRejected("QUESTION_REQUIRED", new CreatePredictionRequest(" \t", "", "Peruste", NOW.plusSeconds(60)));
    }

    @Test
    void rejectsEmptyOrBlankResolutionCriteria() {
        assertRejected(
                "RESOLUTION_CRITERIA_REQUIRED", new CreatePredictionRequest("Kysymys?", "", null, NOW.plusSeconds(60)));
        assertRejected(
                "RESOLUTION_CRITERIA_REQUIRED", new CreatePredictionRequest("Kysymys?", "", "  ", NOW.plusSeconds(60)));
    }

    @Test
    void rejectsAClosingDateThatIsNotInTheFuture() {
        assertRejected(
                "CLOSING_DATE_NOT_IN_FUTURE",
                new CreatePredictionRequest("Kysymys?", "", "Peruste", NOW.minusSeconds(1)));
        assertRejected("CLOSING_DATE_NOT_IN_FUTURE", new CreatePredictionRequest("Kysymys?", "", "Peruste", NOW));
        assertRejected("CLOSING_DATE_NOT_IN_FUTURE", new CreatePredictionRequest("Kysymys?", "", "Peruste", null));
    }

    @Test
    void rejectsAMissingBody() {
        assertRejected("VALIDATION_FAILED", null);
    }

    @Test
    void rejectsACreatorWhoseAccountNoLongerExists() {
        creator.deletedAt = NOW;
        CreatePredictionRequest request = new CreatePredictionRequest("Kysymys?", "", "Peruste", NOW.plusSeconds(60));

        ApiException error = assertThrows(ApiException.class, () -> service.create(creator.id, request));
        assertEquals(401, error.status());

        ApiException unknown = assertThrows(ApiException.class, () -> service.create(new ObjectId(), request));
        assertEquals(401, unknown.status());
        verify(predictions, never()).persist(any(Prediction.class));
    }

    private void assertRejected(String code, CreatePredictionRequest request) {
        ApiException error = assertThrows(ApiException.class, () -> service.create(creator.id, request));
        assertEquals(422, error.status());
        assertEquals(code, error.code());
        verify(predictions, never()).persist(any(Prediction.class));
    }
}
