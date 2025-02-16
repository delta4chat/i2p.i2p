package net.i2p.router.message;
/*
 * free (adj.): unencumbered; not under the control of others
 * Written by jrandom in 2003 and released into the public domain
 * with no warranty of any kind, either expressed or implied.
 * It probably won't make your computer catch on fire, or eat
 * your children, but it might.  Use at your own risk.
 *
 */

import java.util.Date;

import net.i2p.data.Hash;
import net.i2p.data.router.RouterInfo;
import net.i2p.data.i2np.I2NPMessage;
import net.i2p.router.Job;
import net.i2p.router.JobImpl;
import net.i2p.router.MessageSelector;
import net.i2p.router.OutNetMessage;
import net.i2p.router.ReplyJob;
import net.i2p.router.RouterContext;
import net.i2p.util.Log;

/**
 *  Send a message directly to another router, i.e. not through a tunnel.
 *  This is safe to run inline via runJob().
 *  If the RouterInfo for the Hash is not found locally, it will
 *  queue a lookup and register itself to be run again when the lookup
 *  succeeds or times out.
 */
public class SendMessageDirectJob extends JobImpl {
    private final Log _log;
    private final I2NPMessage _message;
    private final Hash _targetHash;
    private RouterInfo _router;
    private final long _expiration;
    private final int _priority;
    private final Job _onSend;
    private final ReplyJob _onSuccess;
    private final Job _onFail;
    private final MessageSelector _selector;
    private boolean _alreadySearched;
    private boolean _sent;
    private long _searchOn;
    private final long _msgIDBloomXor;

    /**
     * @param toPeer may be ourselves
     */
    public SendMessageDirectJob(RouterContext ctx, I2NPMessage message, Hash toPeer, int timeoutMs, int priority) {
        this(ctx, message, toPeer, null, null, null, null, timeoutMs, priority, 0);
    }

    /**
     * @param toPeer may be ourselves
     * @param msgIDBloomXor value to xor the messageID with before passing to the InNetMessagePool, may be 0
     */
    public SendMessageDirectJob(RouterContext ctx, I2NPMessage message, Hash toPeer, int timeoutMs, int priority, long msgIDBloomXor) {
        this(ctx, message, toPeer, null, null, null, null, timeoutMs, priority, msgIDBloomXor);
    }

    /**
     * @param toPeer may be ourselves
     * @param onSuccess may be null
     * @param onFail may be null
     * @param selector be null
     */
    public SendMessageDirectJob(RouterContext ctx, I2NPMessage message, Hash toPeer, ReplyJob onSuccess,
                                Job onFail, MessageSelector selector, int timeoutMs, int priority) {
        this(ctx, message, toPeer, null, onSuccess, onFail, selector, timeoutMs, priority, 0);
    }

    /**
     * @param toPeer may be ourselves
     * @param onSuccess may be null
     * @param onFail may be null
     * @param selector be null
     * @param msgIDBloomXor value to xor the messageID with before passing to the InNetMessagePool, may be 0
     */
    public SendMessageDirectJob(RouterContext ctx, I2NPMessage message, Hash toPeer, ReplyJob onSuccess,
                                Job onFail, MessageSelector selector, int timeoutMs, int priority, long msgIDBloomXor) {
        this(ctx, message, toPeer, null, onSuccess, onFail, selector, timeoutMs, priority, msgIDBloomXor);
    }

    /**
     * @param toPeer may be ourselves
     * @param onSend may be null
     * @param onSuccess may be null
     * @param onFail may be null
     * @param selector be null
     * @param msgIDBloomXor value to xor the messageID with before passing to the InNetMessagePool, may be 0
     */
    public SendMessageDirectJob(RouterContext ctx, I2NPMessage message, Hash toPeer, Job onSend, ReplyJob onSuccess,
                                Job onFail, MessageSelector selector, int timeoutMs, int priority, long msgIDBloomXor) {
        super(ctx);
        _log = getContext().logManager().getLog(SendMessageDirectJob.class);
        _msgIDBloomXor = msgIDBloomXor;
        _message = message;
        _targetHash = toPeer;
        if (timeoutMs < 5*1000) {
            if (_log.shouldLog(Log.WARN))
                _log.warn("Very little time given [" + timeoutMs + "], resetting to 10s", new Exception("stingy caller!"));
            _expiration = ctx.clock().now() + 10*1000;
        } else {
            _expiration = timeoutMs + ctx.clock().now();
        }
        _priority = priority;
        _onSend = onSend;
        _onSuccess = onSuccess;
        _onFail = onFail;
        _selector = selector;
        if (message == null)
            throw new IllegalArgumentException("Attempt to send a null message");
        if (_targetHash == null)
            throw new IllegalArgumentException("Attempt to send a message to a null peer");
    }

    public String getName() {
        return "Send Message Direct";
    }

    public void runJob() {
        long now = getContext().clock().now();

        if (_expiration < now) {
            if (_log.shouldLog(Log.WARN))
                _log.warn("Timed out sending message " + _message + " directly (expiration = "
                          + new Date(_expiration) + ") to " + _targetHash.toBase64());
            if (_onFail != null)
                getContext().jobQueue().addJob(_onFail);
            return;
        }

        if (_router != null) {
            if (_log.shouldLog(Log.DEBUG))
                _log.debug("Router specified, sending");
            send();
        } else {
            _router = getContext().netDb().lookupRouterInfoLocally(_targetHash);
            if (_router != null) {
                if (_log.shouldLog(Log.DEBUG))
                    _log.debug("Router not specified but lookup found it");
                send();
            } else {
                if (!_alreadySearched) {
                    if (_log.shouldLog(Log.DEBUG))
                        _log.debug("Router not specified, so we're looking for it...");
                    getContext().netDb().lookupRouterInfo(_targetHash, this, this,
                                                          _expiration - getContext().clock().now());
                    _searchOn = getContext().clock().now();
                    _alreadySearched = true;
                } else {
                    if (_log.shouldLog(Log.WARN))
                        _log.warn("Unable to find the router to send to: " + _targetHash
                                  + " after searching for " + (getContext().clock().now()-_searchOn)
                                  + "ms, message: " + _message);
                    if (_onFail != null)
                        getContext().jobQueue().addJob(_onFail);
                }
            }
        }
    }

    private void send() {
        if (_sent) {
            if (_log.shouldLog(Log.WARN))
                _log.warn("Not resending!", new Exception("blah"));
            return;
        }
        _sent = true;
        Hash to = _router.getIdentity().getHash();
        Hash us = getContext().routerHash();
        if (us.equals(to)) {
            if (_selector != null) {
                OutNetMessage outM = new OutNetMessage(getContext(), _message, _expiration, _priority, _router);
                outM.setOnFailedReplyJob(_onFail);
                outM.setOnFailedSendJob(_onFail);
                outM.setOnReplyJob(_onSuccess);
                outM.setOnSendJob(_onSend);
                outM.setReplySelector(_selector);
                getContext().messageRegistry().registerPending(outM);
            }

            if (_onSend != null)
                getContext().jobQueue().addJob(_onSend);

            getContext().inNetMessagePool().add(_message, _router.getIdentity(), null, _msgIDBloomXor);

            if (_log.shouldLog(Log.DEBUG))
                _log.debug("Adding " + _message.getClass().getName()
                           + " to inbound message pool as it was destined for ourselves");
            //_log.debug("debug", _createdBy);
        } else {
            OutNetMessage msg = new OutNetMessage(getContext(), _message, _expiration, _priority, _router);
            msg.setOnFailedReplyJob(_onFail);
            msg.setOnFailedSendJob(_onFail);
            msg.setOnReplyJob(_onSuccess);
            msg.setOnSendJob(_onSend);
            msg.setReplySelector(_selector);
            getContext().outNetMessagePool().add(msg);
            if (_log.shouldLog(Log.DEBUG))
                _log.debug("Adding " + _message.getClass().getName()
                           + " to outbound message pool targeting "
                           + _router.getIdentity().getHash().toBase64());
            //_log.debug("Message pooled: " + _message);
        }
    }
}
