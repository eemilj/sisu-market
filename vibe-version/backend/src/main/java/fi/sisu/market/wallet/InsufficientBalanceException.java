package fi.sisu.market.wallet;

import fi.sisu.market.common.ApiException;
import fi.sisu.market.common.ErrorCode;
import jakarta.ws.rs.core.Response.Status;

public class InsufficientBalanceException extends ApiException {

    public InsufficientBalanceException(long balance, long required) {
        super(Status.CONFLICT, ErrorCode.INSUFFICIENT_BALANCE, "Insufficient Sisu balance");
        with("balance", balance);
        with("required", required);
    }
}
