package net.i2p.data.i2cp;
/*
 * free (adj.): unencumbered; not under the control of others
 * Written by jrandom in 2003 and released into the public domain
 * with no warranty of any kind, either expressed or implied.
 * It probably won't make your computer catch on fire, or eat
 * your children, but it might.  Use at your own risk.
 *
 */

import net.i2p.data.StructureTest;
import net.i2p.data.DataStructure;
import net.i2p.data.DataFormatException;
import net.i2p.data.Hash;

/**
 * Test harness for loading / storing Hash objects
 *
 * @author jrandom
 */
public class MessageIdTest extends StructureTest {
    public DataStructure createDataStructure() throws DataFormatException {
        MessageIdStructure id = new MessageIdStructure();
        id.setMessageId(101);
        return id;
    }
    public DataStructure createStructureToRead() {
        return new MessageIdStructure();
    }

    /**
     * so we can test it as a structure
     * @since 0.9.48 no longer extends DataStructureImpl
     */
    private static class MessageIdStructure extends MessageId implements DataStructure {
        public Hash calculateHash() {
            return null;
        }
        public void fromByteArray(byte[] in) {}
        public byte[] toByteArray() {
            return null;
        }
        public void fromBase64(String in) {}
        public String toBase64() {
            return null;
        }
    }
}
