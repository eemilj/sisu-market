package market.sisu.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import market.sisu.common.ApiException;
import market.sisu.prediction.Outcome;
import market.sisu.prediction.Prediction;
import market.sisu.prediction.PredictionDetail;
import market.sisu.prediction.PredictionQueryService;
import market.sisu.prediction.PredictionRepository;
import market.sisu.prediction.PredictionStatus;
import market.sisu.prediction.PredictionSummary;
import market.sisu.user.User;
import market.sisu.user.UserRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Browsing predictions (User Story 2; FR-016 through FR-018, FR-025) without a database. */
class PredictionQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    private PredictionRepository predictions;
    private UserRepository users;
    private PredictionQueryService service;

    @BeforeEach
    void setUp() {
        predictions = mock(PredictionRepository.class);
        users = mock(UserRepository.class);
        service = new PredictionQueryService(predictions, users, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void listMapsPoolsToTotalAndOddsAndPassesTheFilterAndClockThrough() {
        Prediction prediction = prediction(PredictionStatus.OPEN, NOW.plusSeconds(3600), 300, 100);
        when(predictions.findForListing(PredictionStatus.OPEN, NOW)).thenReturn(List.of(prediction));

        List<PredictionSummary> list = service.list(PredictionStatus.OPEN);

        assertEquals(1, list.size());
        PredictionSummary summary = list.getFirst();
        assertEquals(prediction.id.toHexString(), summary.id());
        assertEquals(prediction.question, summary.question());
        assertEquals(PredictionStatus.OPEN, summary.status());
        assertEquals(prediction.closingAt, summary.closingAt());
        assertEquals(400, summary.totalSisuWagered());
        assertEquals(75.0, summary.oddsYesPercent());
        assertEquals(25.0, summary.oddsNoPercent());
    }

    @Test
    void openPredictionAtOrPastClosingTimeIsReportedClosed() {
        Prediction atClosing = prediction(PredictionStatus.OPEN, NOW, 0, 0);
        Prediction overdue = prediction(PredictionStatus.OPEN, NOW.minusSeconds(1), 0, 0);
        Prediction cancelledEarly = prediction(PredictionStatus.CANCELLED, NOW.minusSeconds(1), 0, 0);
        when(predictions.findForListing(null, NOW)).thenReturn(List.of(atClosing, overdue, cancelledEarly));

        List<PredictionStatus> statuses =
                service.list(null).stream().map(PredictionSummary::status).toList();

        assertEquals(List.of(PredictionStatus.CLOSED, PredictionStatus.CLOSED, PredictionStatus.CANCELLED), statuses);
    }

    @Test
    void detailIncludesDescriptionCriteriaCreatorAndOutcome() {
        User creator = new User();
        creator.id = new ObjectId();
        creator.username = "matti";
        Prediction prediction = prediction(PredictionStatus.RESOLVED, NOW.minusSeconds(60), 1, 2);
        prediction.creatorId = creator.id;
        prediction.winningOutcome = Outcome.NO;
        when(predictions.findById(prediction.id)).thenReturn(prediction);
        when(users.findById(creator.id)).thenReturn(creator);

        PredictionDetail detail = service.detail(prediction.id.toHexString());

        assertEquals(prediction.description, detail.description());
        assertEquals(prediction.resolutionCriteria, detail.resolutionCriteria());
        assertEquals("matti", detail.creatorUsername());
        assertEquals(PredictionStatus.RESOLVED, detail.status());
        assertEquals(Outcome.NO, detail.winningOutcome());
        assertEquals(3, detail.totalSisuWagered());
        assertEquals(33.3, detail.oddsYesPercent());
        assertEquals(66.7, detail.oddsNoPercent());
    }

    @Test
    void detailOfADeletedCreatorsPredictionHasNoCreatorName() {
        User deleted = new User();
        deleted.id = new ObjectId();
        deleted.username = null;
        deleted.deletedAt = NOW;
        Prediction prediction = prediction(PredictionStatus.CANCELLED, NOW.plusSeconds(60), 0, 0);
        prediction.creatorId = deleted.id;
        when(predictions.findById(prediction.id)).thenReturn(prediction);
        when(users.findById(deleted.id)).thenReturn(deleted);

        assertNull(service.detail(prediction.id.toHexString()).creatorUsername());
    }

    @Test
    void unknownPredictionIsNotFound() {
        ObjectId id = new ObjectId();

        ApiException error = assertThrows(ApiException.class, () -> service.detail(id.toHexString()));

        assertEquals(404, error.status());
        assertEquals("PREDICTION_NOT_FOUND", error.code());
        verify(predictions).findById(id);
    }

    @Test
    void malformedIdIsNotFoundWithoutTouchingTheDatabase() {
        ApiException error = assertThrows(ApiException.class, () -> service.detail("not-an-id"));

        assertEquals(404, error.status());
        assertEquals("PREDICTION_NOT_FOUND", error.code());
        verifyNoInteractions(users);
        verify(predictions, org.mockito.Mockito.never()).findById(any(ObjectId.class));
    }

    private static Prediction prediction(PredictionStatus status, Instant closingAt, long yes, long no) {
        Prediction prediction = new Prediction();
        prediction.id = new ObjectId();
        prediction.question = "Sataako huomenna?";
        prediction.description = "Helsinki-Vantaan mittaus";
        prediction.resolutionCriteria = "Ilmatieteen laitoksen data";
        prediction.status = status;
        prediction.closingAt = closingAt;
        prediction.yesPoolSisu = yes;
        prediction.noPoolSisu = no;
        return prediction;
    }
}
