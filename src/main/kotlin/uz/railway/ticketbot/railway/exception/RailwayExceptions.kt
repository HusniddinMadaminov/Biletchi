package uz.railway.ticketbot.railway.exception

sealed class RailwayException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** 429 / 500 / 502 / 503 / 504 / timeout / connection reset - safe to retry with backoff. */
class RailwayTransientException(message: String, cause: Throwable? = null) : RailwayException(message, cause)

/** 401 / 403 - logged separately, never retried automatically. */
class RailwayAuthException(message: String, cause: Throwable? = null) : RailwayException(message, cause)

/** Any other non-retryable client/server error. */
class RailwayClientException(message: String, cause: Throwable? = null) : RailwayException(message, cause)
