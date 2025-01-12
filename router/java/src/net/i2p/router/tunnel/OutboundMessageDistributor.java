package net.i2p.router.tunnel;

import java.util.HashSet;
import java.util.Set;

import net.i2p.data.Hash;
import net.i2p.data.router.RouterInfo;
import net.i2p.data.TunnelId;
import net.i2p.data.i2np.DatabaseLookupMessage;
import net.i2p.data.i2np.I2NPMessage;
import net.i2p.data.i2np.I2NPMessageException;
import net.i2p.data.i2np.TunnelGatewayMessage;
import net.i2p.data.i2np.UnknownI2NPMessage;
import net.i2p.router.JobImpl;
import net.i2p.router.OutNetMessage;
import net.i2p.router.RouterContext;
import net.i2p.util.Log;

/**
 * When a message arrives at the outbound tunnel endpoint, this distributor
 * honors the instructions.
 */
class OutboundMessageDistributor {
    private final RouterContext _context;
    private final int _priority;
    private final Log _log;
    // following only for somebody else's OBEP, not for zero-hop
    private final Set<Hash> _toRouters;
    private int _newRouterCount;
    private long _newRouterTime;

    private static final long MAX_DISTRIBUTE_TIME = 15*1000;
    // This is probably too high, to be reduced later
    private static final int MAX_ROUTERS_PER_PERIOD = 48;
    private static final long NEW_ROUTER_PERIOD = 30*1000;

    /**
     *  @param priority OutNetMessage.PRIORITY_PARTICIPATING for somebody else's OBEP, or
     *                  OutNetMessage.PRIORITY_MY_DATA for our own zero-hop OBGW/EP
     */
    public OutboundMessageDistributor(RouterContext ctx, int priority) {
        _context = ctx;
        _priority = priority;
        _log = ctx.logManager().getLog(OutboundMessageDistributor.class);
        if (priority <= OutNetMessage.PRIORITY_PARTICIPATING) {
            _toRouters = new HashSet<Hash>(4);
            _toRouters.add(ctx.routerHash());
        } else {
            _toRouters = null;
        }
        // all createRateStat() in TunnelDispatcher
    }

    /**
     *  Warning - as of 0.9.63, msg will be an UnknownI2NPMessage,
     *  and must be converted before handling locally.
     */
    public void distribute(I2NPMessage msg, Hash target) {
        distribute(msg, target, null);
    }

    /**
     *  Warning - as of 0.9.63, msg will be an UnknownI2NPMessage,
     *  and must be converted before handling locally.
     */
    public void distribute(I2NPMessage msg, Hash target, TunnelId tunnel) {
        if (shouldDrop(target)) {
            _context.statManager().addRateData("tunnel.dropAtOBEP", 1);
            if (_log.shouldWarn())
                _log.warn("Drop msg at OBEP (new conn throttle) to " + target.toBase64() + " type " + msg.getType());
            return;
        }
        RouterInfo info = _context.netDb().lookupRouterInfoLocally(target);
        if (info == null) {
            if (_toRouters != null) {
                // only if not zero-hop
                // credit our lookup message as part. traffic
                if (_context.tunnelDispatcher().shouldDropParticipatingMessage(TunnelDispatcher.Location.OBEP, DatabaseLookupMessage.MESSAGE_TYPE, 1024)) {
                    if (_log.shouldWarn())
                        _log.warn("Drop msg at OBEP (lookup bandwidth) to " + target.toBase64() + " type " + msg.getType());
                    return;
                }
            }
            if (_log.shouldLog(Log.INFO))
                _log.info("outbound distributor to " + target
                          + "." + (tunnel != null ? tunnel.getTunnelId() + "" : "")
                          + ": no info locally, searching...");
            // TODO - should we set the search timeout based on the message timeout,
            // or is that a bad idea due to clock skews?
            _context.netDb().lookupRouterInfo(target, new DistributeJob(_context, msg, target, tunnel), null, MAX_DISTRIBUTE_TIME);
            return;
        } else {
            distribute(msg, info, tunnel);
        }
    }

    /**
     *  Throttle msgs to unconnected routers after we hit
     *  the limit of new routers in a given time period.
     *  @since 0.9.12
     */
    private boolean shouldDrop(Hash target) {
        if (_toRouters == null)
            return false;
        synchronized(this) {
            if (!_toRouters.add(target) || _context.commSystem().isEstablished(target) || ++_newRouterCount <= MAX_ROUTERS_PER_PERIOD)
                return false;
            long now = _context.clock().now();
            if (_newRouterTime < now - NEW_ROUTER_PERIOD) {
                // latest guy is outside previous period
                _newRouterCount = 1;
                _newRouterTime = now;
                return false;
            }
            // rarely get here at current limits
            _toRouters.remove(target);
        }
        return true;
    }

    /**
     *  Warning - as of 0.9.63, msg will be an UnknownI2NPMessage,
     *  and must be converted before handling locally.
     */
    private void distribute(I2NPMessage msg, RouterInfo target, TunnelId tunnel) {
        boolean toUs = _context.routerHash().equals(target.getIdentity().calculateHash());
        if (toUs) {
            // If UnknownI2NPMessage, convert it.
            // See FragmentHandler.receiveComplete()
            if (msg instanceof UnknownI2NPMessage) {
                try {
                    UnknownI2NPMessage umsg = (UnknownI2NPMessage) msg;
                    msg = umsg.convert();
                } catch (I2NPMessageException ime) {
                    if (_log.shouldLog(Log.WARN))
                        _log.warn("Unable to convert to std. msg. class at zero-hop IBGW", ime);
                    return;
                }
            }
        }

        if (tunnel != null) {
            TunnelGatewayMessage t = new TunnelGatewayMessage(_context);
            t.setMessage(msg);
            t.setTunnelId(tunnel);
            t.setMessageExpiration(msg.getMessageExpiration());
            msg = t;
        }

        if (toUs) {
            if (_log.shouldLog(Log.DEBUG))
                _log.debug("queueing inbound message to ourselves: " + msg);
            _context.inNetMessagePool().add(msg, null, null, 0);
            return;
        } else {
            OutNetMessage out = new OutNetMessage(_context, msg, _context.clock().now() + MAX_DISTRIBUTE_TIME, _priority, target);

            if (_log.shouldLog(Log.DEBUG))
                _log.debug("queueing outbound message to " + target.getIdentity().calculateHash());
            _context.outNetMessagePool().add(out);
        }
    }

    private class DistributeJob extends JobImpl {
        private final I2NPMessage _message;
        private final Hash _target;
        private final TunnelId _tunnel;

        public DistributeJob(RouterContext ctx, I2NPMessage msg, Hash target, TunnelId id) {
            super(ctx);
            _message = msg;
            _target = target;
            _tunnel = id;
        }

        public String getName() {
            return "OBEP distribute after lookup";
        }

        public void runJob() {
            RouterInfo info = getContext().netDb().lookupRouterInfoLocally(_target);
            int stat;
            if (info != null) {
                if (_log.shouldLog(Log.DEBUG))
                    _log.debug("outbound distributor to " + _target
                               + "." + (_tunnel != null ? _tunnel.getTunnelId() + "" : "")
                               + ": found on search");
                distribute(_message, info, _tunnel);
                stat = 1;
            } else {
                if (_log.shouldLog(Log.WARN))
                    _log.warn("outbound distributor msg type " + _message.getType() + " to " + _target
                              + "." + (_tunnel != null ? _tunnel.getTunnelId() + "" : "")
                              + ": NOT found on search");
                stat = 0;
            }
            _context.statManager().addRateData("tunnel.distributeLookupSuccess", stat);
        }
    }
}
