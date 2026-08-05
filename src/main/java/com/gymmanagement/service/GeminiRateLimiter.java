package com.gymmanagement.service;

import com.gymmanagement.exception.AiRateLimitExceededException;

import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * GeminiRateLimiter — a deliberate, hand-rolled safeguard sitting in
 * front of every Gemini call, added specifically because of a real past
 * incident: buggy code once sent far more requests than intended and
 * burned through an API quota. This exists to make that structurally
 * hard to repeat, not just to trust that the calling code is correct.
 *
 * How it works: a simple fixed-window counter. Every call to
 * checkAllowed() increments a counter; once a new 60-second window
 * starts, the counter resets. If more than MAX_REQUESTS_PER_MINUTE
 * calls land in one window, every call after that throws immediately
 * — before any HTTP request to Gemini is ever made, so no quota is
 * consumed once the limit is hit.
 *
 * Deliberately set well below Gemini's own free-tier limit (roughly
 * 10 RPM for gemini-2.5-flash) rather than matching it exactly — the
 * whole point is a safety margin, not squeezing out the maximum
 * possible throughput.
 *
 * In-memory, not distributed — completely correct for this project's
 * actual deployment: Render's free tier runs exactly one instance, no
 * horizontal scaling, so there's only ever one JVM process to track.
 * A multi-instance deployment would need a shared store (Redis) instead
 * — noted here as a "what I'd change at scale" item, not something
 * this project currently needs.
 */
@Component
public class GeminiRateLimiter {

    private static final int MAX_REQUESTS_PER_MINUTE = 5;

    private int     requestCount = 0;
    private Instant windowStart  = Instant.now();

    /**
     * Call this before every Gemini API call. Throws immediately if the
     * limit for the current one-minute window has already been reached
     * — the caller never gets far enough to actually hit the network.
     */
    public synchronized void checkAllowed() {
        Instant now = Instant.now();

        if (now.isAfter(windowStart.plusSeconds(60))) {
            windowStart  = now;
            requestCount = 0;
        }

        requestCount++;

        if (requestCount > MAX_REQUESTS_PER_MINUTE) {
            throw new AiRateLimitExceededException(
                "AI request limit reached (" + MAX_REQUESTS_PER_MINUTE + " per minute). " +
                "This exists specifically to stop a bug or repeated calls from silently " +
                "burning through the Gemini free-tier quota. Wait a minute and try again."
            );
        }
    }
}