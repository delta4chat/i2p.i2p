package net.i2p.router;

import net.i2p.router.util.DecayingBloomFilter;
import net.i2p.router.util.DecayingHashSet;
import net.i2p.util.Log;

/**
 * Singleton to manage the logic (and historical data) to determine whether a message
 * is valid or not (meaning it isn't expired and hasn't already been received).  We'll
 * need a revamp once we start dealing with long message expirations (since it might
 * involve keeping a significant number of entries in memory), but that probably won't
 * be necessary until I2P 3.0.
 *
 */
public class MessageValidator {
    private final Log _log;
    private final RouterContext _context;
    private DecayingBloomFilter _filter;


    public MessageValidator(RouterContext context) {
        _log = context.logManager().getLog(MessageValidator.class);
        _context = context;
        long[] rates = new long[] { 60*60*1000, 24*60*60*1000 };
        context.statManager().createRateStat("router.duplicateMessageId", "Note that a duplicate messageId was received", "Router",
                                             rates);
        context.statManager().createRateStat("router.invalidMessageTime", "Note that a message outside the valid range was received", "Router",
                                             rates);
    }


    /**
     * Determine if this message should be accepted as valid (not expired, not a duplicate)
     *
     * @return reason why the message is invalid (or null if the message is valid)
     */
    public String validateMessage(long messageId, long expiration) {
        String msg = validateMessage(expiration);
        if (msg != null)
            return msg;

        boolean isDuplicate = noteReception(messageId, expiration);
        if (isDuplicate) {
            if (_log.shouldLog(Log.INFO))
                _log.info("Rejecting message " + messageId + " duplicate", new Exception("Duplicate origin"));
            _context.statManager().addRateData("router.duplicateMessageId", 1);
            return "duplicate";
        } else {
            //if (_log.shouldLog(Log.DEBUG))
            //    _log.debug("Accepting message " + messageId + " because it is NOT a duplicate", new Exception("Original origin"));
            return null;
        }
    }

    /**
     * Only check the expiration for the message
     */
    public String validateMessage(long expiration) {
        long now = _context.clock().now();
        if (now - (Router.CLOCK_FUDGE_FACTOR * 3 / 2) >= expiration) {
            if (_log.shouldLog(Log.INFO))
                _log.info("Rejecting message expired " + (now-expiration) + "ms ago");
            _context.statManager().addRateData("router.invalidMessageTime", (now-expiration));
            return "expired " + (now-expiration) + "ms ago";
        } else if (now + 4*Router.CLOCK_FUDGE_FACTOR < expiration) {
            if (_log.shouldLog(Log.INFO))
                _log.info("Rejecting message expiring too far in the future (" + (expiration-now) + "ms)");
            _context.statManager().addRateData("router.invalidMessageTime", (now-expiration));
            return "expire too far in the future (" + (expiration-now) + "ms)";
        }
        return null;
    }

    private static final long TIME_MASK = 0xFFFFFC00;

    /**
     * Note that we've received the message (which has the expiration given).
     * This functionality will need to be reworked for I2P 3.0 when we take into
     * consideration messages with significant user specified delays (since we dont
     * want to keep an infinite number of messages in RAM, etc)
     *
     * @return true if we HAVE already seen this message, false if not
     */
    private boolean noteReception(long messageId, long messageExpiration) {
        long val = messageId;
        // tweak the high order bits with the message expiration /seconds/
        ////val ^= (messageExpiration & TIME_MASK) << 16;
        val ^= (messageExpiration & TIME_MASK);
        boolean dup = _filter.add(val);
        if (dup && _log.shouldLog(Log.WARN)) {
            _log.warn("Duplicate with " + _filter.getCurrentDuplicateCount()
                      + " other dups, " + _filter.getInsertedCount()
                      + " other entries, and a false positive rate of "
                      + _filter.getFalsePositiveRate());
        }
        return dup;
    }

    public synchronized void startup() {
        _filter = new DecayingHashSet(_context, (int)Router.CLOCK_FUDGE_FACTOR * 2, 8, "RouterMV");
    }

    synchronized void shutdown() {
        _filter.stopDecaying();
    }
}
