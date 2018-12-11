package uk.gov.hmcts.reform.sscs.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import uk.gov.hmcts.reform.logging.exception.AlertLevel;
import uk.gov.hmcts.reform.logging.exception.UnknownErrorCodeException;

@ResponseStatus(value = HttpStatus.UNPROCESSABLE_ENTITY,
        reason = "Invalid robotics validation")
public class RoboticsValidationException extends UnknownErrorCodeException {

    public RoboticsValidationException(Throwable cause) {
        super(AlertLevel.P3, cause);
    }

}