package fi.sisu.market.prediction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code closesAt} is taken as a string so a malformed date gets a readable 400 from
 * {@link PredictionService} instead of a generic JSON deserialisation error.
 */
public record CreatePredictionRequest(
        @NotBlank(message = "Question is required")
        @Size(min = 10, max = 200, message = "Question must be 10-200 characters")
        String question,

        @NotBlank(message = "Description is required")
        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @NotBlank(message = "Resolution criteria are required")
        @Size(min = 10, max = 1000, message = "Resolution criteria must be 10-1000 characters")
        String resolutionCriteria,

        @NotNull(message = "Category is required")
        Category category,

        @NotBlank(message = "Closing date is required")
        String closesAt) {
}
