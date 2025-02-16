package net.i2p.router.peermanager;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import net.i2p.router.RouterContext;
import net.i2p.stat.RateStat;
import net.i2p.util.Log;

/**
 * Tunnel related history information
 *
 */
public class TunnelHistory {
    private final RouterContext _context;
    private final Log _log;
    private final AtomicLong _lifetimeAgreedTo = new AtomicLong();
    private final AtomicLong _lifetimeRejected = new AtomicLong();
    private volatile long _lastAgreedTo;
    private volatile long _lastRejectedCritical;
    private volatile long _lastRejectedBandwidth;
    private volatile long _lastRejectedTransient;
    private volatile long _lastRejectedProbabalistic;
    private final AtomicLong _lifetimeFailed = new AtomicLong();
    private volatile long _lastFailed;
    private final RateStat _rejectRate;
    private final RateStat _failRate;
    private final String _statGroup;
    static final long[] RATES = new long[] { 10*60*1000l, 60*60*1000l, 24*60*60*1000l };

    /** probabalistic tunnel rejection due to a flood of requests - infrequent */
    public static final int TUNNEL_REJECT_PROBABALISTIC_REJECT = 10;
    /** tunnel rejection due to temporary cpu/job/tunnel overload - rare */
    public static final int TUNNEL_REJECT_TRANSIENT_OVERLOAD = 20;
    /** tunnel rejection due to excess bandwidth usage - used for most rejections even if not really for bandwidth */
    public static final int TUNNEL_REJECT_BANDWIDTH = 30;
    /** tunnel rejection due to system failure - not currently used */
    public static final int TUNNEL_REJECT_CRIT = 50;

    public TunnelHistory(RouterContext context, String statGroup) {
        _context = context;
        _log = context.logManager().getLog(TunnelHistory.class);
        _statGroup = statGroup;
        _rejectRate = new RateStat("tunnelHistory.rejectRate", "How often does this peer reject a tunnel request?", statGroup, RATES);
        _failRate = new RateStat("tunnelHistory.failRate", "How often do tunnels this peer accepts fail?", statGroup, RATES);
    }

    /** total tunnels the peer has agreed to participate in */
    public long getLifetimeAgreedTo() {
        return _lifetimeAgreedTo.get();
    }
    /** total tunnels the peer has refused to participate in */
    public long getLifetimeRejected() {
        return _lifetimeRejected.get();
    }
    /** total tunnels the peer has agreed to participate in that were later marked as failed prematurely */
    public long getLifetimeFailed() {
        return _lifetimeFailed.get();
    }
    /** when the peer last agreed to participate in a tunnel */
    public long getLastAgreedTo() {
        return _lastAgreedTo;
    }
    /** when the peer last refused to participate in a tunnel with level of critical */
    public long getLastRejectedCritical() {
        return _lastRejectedCritical;
    }
    /** when the peer last refused to participate in a tunnel complaining of bandwidth overload */
    public long getLastRejectedBandwidth() {
        return _lastRejectedBandwidth;
    }
    /** when the peer last refused to participate in a tunnel complaining of transient overload */
    public long getLastRejectedTransient() {
        return _lastRejectedTransient;
    }
    /** when the peer last refused to participate in a tunnel probabalistically */
    public long getLastRejectedProbabalistic() {
        return _lastRejectedProbabalistic;
    }
    /** when the last tunnel the peer participated in failed */
    public long getLastFailed() {
        return _lastFailed;
    }

    public void incrementProcessed(int processedSuccessfully, int failedProcessing) {
        // old strict speed calculator
    }

    public void incrementAgreedTo() {
        _lifetimeAgreedTo.incrementAndGet();
        _lastAgreedTo = _context.clock().now();
    }

    /**
     * @param severity how much the peer doesnt want to participate in the
     *                 tunnel (large == more severe)
     */
    public void incrementRejected(int severity) {
        _lifetimeRejected.incrementAndGet();
        long now = _context.clock().now();
        if (severity >= TUNNEL_REJECT_CRIT) {
            _lastRejectedCritical = now;
        } else if (severity >= TUNNEL_REJECT_BANDWIDTH) {
            _lastRejectedBandwidth = now;
        } else if (severity >= TUNNEL_REJECT_TRANSIENT_OVERLOAD) {
            _lastRejectedTransient = now;
        } else if (severity >= TUNNEL_REJECT_PROBABALISTIC_REJECT) {
            _lastRejectedProbabalistic = now;
        }
        // a rejection is always a rejection, don't factor based on severity,
        // which could impact our ability to avoid a congested peer
        _rejectRate.addData(1);
    }

    /**
     * Define this rate as the probability it really failed
     * @param pct = probability * 100
     */
    public void incrementFailed(int pct) {
        _lifetimeFailed.incrementAndGet();
        _failRate.addData(pct);
        _lastFailed = _context.clock().now();
    }

    /*****  all unused
        public void setLifetimeAgreedTo(long num) { _lifetimeAgreedTo = num; }
        public void setLifetimeRejected(long num) { _lifetimeRejected = num; }
        public void setLifetimeFailed(long num) { _lifetimeFailed = num; }
        public void setLastAgreedTo(long when) { _lastAgreedTo = when; }
        public void setLastRejectedCritical(long when) { _lastRejectedCritical = when; }
        public void setLastRejectedBandwidth(long when) { _lastRejectedBandwidth = when; }
        public void setLastRejectedTransient(long when) { _lastRejectedTransient = when; }
        public void setLastRejectedProbabalistic(long when) { _lastRejectedProbabalistic = when; }
        public void setLastFailed(long when) { _lastFailed = when; }
    ******/

    public RateStat getRejectionRate() {
        return _rejectRate;
    }
    public RateStat getFailedRate() {
        return _failRate;
    }

    public void coalesceStats() {
        if (_log.shouldLog(Log.DEBUG))
            _log.debug("Coallescing stats");
        _rejectRate.coalesceStats();
        _failRate.coalesceStats();
    }

    private final static String NL = System.getProperty("line.separator");

    public void store(OutputStream out) throws IOException {
        store(out, true);
    }

    /**
     * write out the data from the profile to the stream
     * @param addComments add comment lines to the output
     * @since 0.9.41
     */
    public void store(OutputStream out, boolean addComments) throws IOException {
        StringBuilder buf = new StringBuilder(512);
        if (addComments) {
            buf.append(NL);
            buf.append("#################").append(NL);
            buf.append("# Tunnel history").append(NL);
            buf.append("###").append(NL);
        }
        addDate(buf, addComments, "lastAgreedTo", _lastAgreedTo, "When did the peer last agree to participate in a tunnel?");
        addDate(buf, addComments, "lastFailed", _lastFailed, "When was the last time a tunnel that the peer agreed to participate failed?");
        addDate(buf, addComments, "lastRejectedCritical", _lastRejectedCritical, "When was the last time the peer refused to participate in a tunnel (Critical response code)?");
        addDate(buf, addComments, "lastRejectedBandwidth", _lastRejectedBandwidth, "When was the last time the peer refused to participate in a tunnel (Bandwidth response code)?");
        addDate(buf, addComments, "lastRejectedTransient", _lastRejectedTransient, "When was the last time the peer refused to participate in a tunnel (Transient load response code)?");
        addDate(buf, addComments, "lastRejectedProbabalistic", _lastRejectedProbabalistic, "When was the last time the peer refused to participate in a tunnel (Probabalistic response code)?");
        add(buf, addComments, "lifetimeAgreedTo", _lifetimeAgreedTo.get(), "How many tunnels has the peer ever agreed to participate in?");
        add(buf, addComments, "lifetimeFailed", _lifetimeFailed.get(), "How many tunnels has the peer ever agreed to participate in that failed prematurely?");
        add(buf, addComments, "lifetimeRejected", _lifetimeRejected.get(), "How many tunnels has the peer ever refused to participate in?");
        out.write(buf.toString().getBytes("UTF-8"));
        _rejectRate.store(out, "tunnelHistory.rejectRate", addComments);
        _failRate.store(out, "tunnelHistory.failRate", addComments);
    }

    private static void addDate(StringBuilder buf, boolean addComments, String name, long val, String description) {
        if (addComments) {
            String when = val > 0 ? (new Date(val)).toString() : "Never";
            add(buf, true, name, val, description + ' ' + when);
        } else {
            add(buf, false, name, val, description);
        }
    }

    private static void add(StringBuilder buf, boolean addComments, String name, long val, String description) {
        if (addComments)
            buf.append("# ").append(name).append(NL).append("# ").append(description).append(NL);
        buf.append("tunnels.").append(name).append('=').append(val).append(NL);
        if (addComments)
            buf.append(NL);
    }

    public void load(Properties props) {
        _lastAgreedTo = getLong(props, "tunnels.lastAgreedTo");
        _lastFailed = getLong(props, "tunnels.lastFailed");
        _lastRejectedCritical = getLong(props, "tunnels.lastRejectedCritical");
        _lastRejectedBandwidth = getLong(props, "tunnels.lastRejectedBandwidth");
        _lastRejectedTransient = getLong(props, "tunnels.lastRejectedTransient");
        _lastRejectedProbabalistic = getLong(props, "tunnels.lastRejectedProbabalistic");
        _lifetimeAgreedTo.set(getLong(props, "tunnels.lifetimeAgreedTo"));
        _lifetimeFailed.set(getLong(props, "tunnels.lifetimeFailed"));
        _lifetimeRejected.set(getLong(props, "tunnels.lifetimeRejected"));
        try {
            _rejectRate.load(props, "tunnelHistory.rejectRate", true);
            _failRate.load(props, "tunnelHistory.failRate", true);
        } catch (IllegalArgumentException iae) {
            _log.warn("TunnelHistory rates are corrupt", iae);
        }
    }

    private final static long getLong(Properties props, String key) {
        return ProfilePersistenceHelper.getLong(props, key);
    }
}
