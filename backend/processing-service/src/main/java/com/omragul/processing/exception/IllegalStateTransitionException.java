package com.omragul.processing.exception;

import com.omragul.processing.enums.ProcessingStatus;

public class IllegalStateTransitionException extends ProcessingException {
    public IllegalStateTransitionException(ProcessingStatus current, ProcessingStatus target) {
        super(String.format("Illegal state transition from %s to %s", current, target));
    }

    public IllegalStateTransitionException(String message) {
        super(message);
    }
}
