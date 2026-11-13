package com.fileshare.network;

/**
 * Enumeration representing all protocol message types between FileShare client and server.
 */
public enum MessageType {
    // Authentication & Registration
    REGISTER_REQUEST,
    REGISTER_RESPONSE,
    LOGIN_REQUEST,
    LOGIN_RESPONSE,
    LOGOUT_REQUEST,

    // Online Users
    USER_LIST_REQUEST,
    USER_LIST_UPDATE,

    // File Transfer Negotiation
    TRANSFER_REQUEST,
    TRANSFER_ACCEPT,
    TRANSFER_REJECT,

    // File Transfer Data Flow
    TRANSFER_CHUNK,
    TRANSFER_COMPLETE,
    TRANSFER_CANCEL,
    TRANSFER_FAILED,

    // Transfer History
    HISTORY_REQUEST,
    HISTORY_RESPONSE
}
