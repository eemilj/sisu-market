package fi.sisu.market.wallet;

import fi.sisu.market.common.ApiException;
import jakarta.ws.rs.core.Response.Status;

public class InsufficientBalanceException extends ApiException {

    public InsufficientBalanceException() {
        super(Status.CONFLICT, "Insufficient Sisu balance");
    }
}
