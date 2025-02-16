package net.i2p.client;

import net.i2p.data.DateAndFlags;

/**
 *  Advanced options attached to a single outgoing I2CP message.
 *
 *  Note that the packing of options into the 16-bit flags field is
 *  is subject to change. Therefore, for now, this is only recommended
 *  within RouterContext.
 *
 *  Static methods are for OutboundClientMessageOneShotJob to decode the
 *  flags field on the router side.
 *
 *  GzipOption flags are as of 0.9.36, are client-side only, and are
 *  not included in the flags field or sent to the router.
 *
 *  @since 0.9.2
 */
public class SendMessageOptions extends DateAndFlags {

    private GzipOption _gzip = GzipOption.DEFAULT;

    /** all subject to change */

    /**
     *  1 means don't send, 0 means default
     */
    private static final int LS_MASK = 0x0100;

    /**
     *  Tags to send field:
     *  see below for possible values
     */
    private static final int TAGS_SEND_MASK = 0x000f;
    /**
     *  Possible values. Configured values will be rounded down.
     *  Note that ElGamalAESEngine enforces a max of 200 on receive.
     */
    private static final int[] TAGS_SEND = {
        0, 2, 4, 6, 8, 12, 16, 24,
        32, 40, 51, 64, 80, 100, 125, 160
    };

    /**
     *  Tags threshold field:
     *  see below for possible values
     */
    private static final int TAGS_REQD_MASK = 0x00f0;
    /** Possible values. Configured values will be rounded down. */
    private static final int[] TAGS_REQD = {
        0, 2, 3, 6, 9, 14, 20, 27,
        35, 45, 57, 72, 92, 117, 147, 192
    };

    /**
     *  Reliability bits 9-10
     *  @since 0.9.14
     */
    public enum Reliability { DEFAULT, BEST_EFFORT, GUARANTEED, UNDEFINED }

    private static final int BEST_EFFORT_MASK = 0x0200;
    private static final int GUARANTEED_MASK = 0x0400;
    private static final int RELIABILITY_MASK = BEST_EFFORT_MASK | GUARANTEED_MASK;

    /** default true */
    public void setSendLeaseSet(boolean yes) {
        if (yes)
            _flags &= ~LS_MASK;
        else
            _flags |= LS_MASK;
    }

    /** default true */
    public boolean getSendLeaseSet() {
        return getSendLeaseSet(_flags);
    }

    /** default true */
    public static boolean getSendLeaseSet(int flags) {
        return (flags & LS_MASK) == 0;
    }

    /**
     *  If we are low on tags, send this many.
     *  Power of 2 recommended - rounds down.
     *  default 0, meaning unset, use the SKM config (default 40)
     *  @param tags 0 or 2 to 128
     */
    public void setTagsToSend(int tags) {
        if (tags < 0)
            throw new IllegalArgumentException();
        _flags &= ~TAGS_SEND_MASK;
        _flags |= valToCode(tags, TAGS_SEND);
    }

    /**
     *  If we are low on tags, send this many.
     *  @return default 0, meaning unset, use the SKM config (default 40)
     */
    public int getTagsToSend() {
        return getTagsToSend(_flags);
    }

    /**
     *  If we are low on tags, send this many.
     *  @return default 0, meaning unset, use the SKM config (default 40)
     */
    public static int getTagsToSend(int flags) {
        int exp = (flags & TAGS_SEND_MASK);
        return codeToVal(exp, TAGS_SEND);
    }

    /**
     *  Low tag threshold. If less than this many, send more.
     *  Power of 2 recommended - rounds down.
     *  default 0, meaning unset, use the SKM config (default 30)
     *  @param tags 0 to 90
     */
    public void setTagThreshold(int tags) {
        if (tags < 0)
            throw new IllegalArgumentException();
        _flags &= ~TAGS_REQD_MASK;
        _flags |= valToCode(tags, TAGS_REQD) << 4;
    }

    /**
     *  Low tag threshold. If less than this many, send more.
     *  @return default 0, meaning unset, use the SKM config (default 30)
     */
    public int getTagThreshold() {
        return getTagThreshold(_flags);
    }

    /**
     *  Low tag threshold. If less than this many, send more.
     *  @return default 0, meaning unset, use the SKM config (default 30)
     */
    public static int getTagThreshold(int flags) {
        int exp = (flags & TAGS_REQD_MASK) >> 4;
        return codeToVal(exp, TAGS_REQD);
    }

    /** rounds down */
    private static int valToCode(int val, int[] codes) {
        // special case, round up so we don't turn it into default
        if (val > 0 && val <= codes[1])
            return 1;
        for (int i = 1; i < codes.length; i++) {
            if (val < codes[i])
                return i - 1;
        }
        return codes.length - 1;
    }

    private static int codeToVal(int code, int[] codes) {
        return codes[code];
    }

    /**
     *  default Reliability.DEFAULT
     *  @since 0.9.14
     */
    public void setReliability(Reliability r) {
        _flags &= ~RELIABILITY_MASK;
        switch (r) {
        case BEST_EFFORT:
            _flags |= BEST_EFFORT_MASK;
            break;

        case GUARANTEED:
            _flags |= GUARANTEED_MASK;
            break;

        case UNDEFINED:
            _flags |= RELIABILITY_MASK;
            break;

        case DEFAULT:
        default:
            break;
        }
    }

    /**
     *  default Reliability.DEFAULT
     *  @since 0.9.14
     */
    public Reliability getReliability() {
        return getReliability(_flags);
    }

    /**
     *  default Reliability.DEFAULT
     *  @since 0.9.14
     */
    public static Reliability getReliability(int flags) {
        switch (flags & RELIABILITY_MASK) {
        case BEST_EFFORT_MASK:
            return Reliability.BEST_EFFORT;

        case GUARANTEED_MASK:
            return Reliability.GUARANTEED;

        default:
        case RELIABILITY_MASK:
            return Reliability.UNDEFINED;

        case 0:
            return Reliability.DEFAULT;
        }
    }

    /**
     *  Overrides i2cp.gzip session option and size threshold
     *  for this message only.
     *
     *  @since 0.9.36
     */
    public enum GzipOption { DEFAULT, GZIP_OFF, GZIP_ON }

    /**
     *  Overrides i2cp.gzip session option and size threshold
     *  for this message only.
     *
     *  @return non-null, DEFAULT unless setGzip() was called
     *  @since 0.9.36
     */
    public GzipOption getGzip() {
        return _gzip;
    }

    /**
     *  Overrides i2cp.gzip session option and size threshold
     *  for this message only.
     *
     *  @since 0.9.36
     */
    public void setGzip(boolean yes) {
        _gzip = yes? GzipOption.GZIP_ON : GzipOption.GZIP_OFF;
    }
}
