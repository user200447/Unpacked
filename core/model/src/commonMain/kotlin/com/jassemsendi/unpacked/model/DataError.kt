package com.jassemsendi.unpacked.model

/**
 * Why the library could not do what was asked. Only local errors exist today: nothing leaves the
 * device, so there is no network to fail. A `Network` family joins this one with the agent's providers.
 */
public sealed interface DataError : Error {
    public enum class Local : DataError {
        /** The device has no room left; whatever was written before this stays. */
        DISK_FULL,

        /** Nothing in the library has the id or Provenance asked for. */
        NOT_FOUND,

        /** The library failed in a way it cannot name; the cause is in the log, not in the user's hands. */
        UNKNOWN,
    }
}
