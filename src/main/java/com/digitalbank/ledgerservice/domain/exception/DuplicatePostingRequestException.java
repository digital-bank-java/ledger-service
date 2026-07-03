package com.digitalbank.ledgerservice.domain.exception;

public class DuplicatePostingRequestException extends RuntimeException {

    private final String postingRequestId;

    public DuplicatePostingRequestException(String postingRequestId) {
        super("Posting request already exists: " + postingRequestId);
        this.postingRequestId = postingRequestId;
    }

    public String postingRequestId() {
        return postingRequestId;
    }
}
