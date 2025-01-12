package net.i2p.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.i2p.CoreVersion;

/**
 * Simple command line access to various utilities.
 * Not a public API. Subject to change.
 * Apps and plugins should use specific classes.
 *
 * @since 0.9.25
 */
public class CommandLine {

    protected static final List<String> CLASSES = Arrays.asList(new String[] {
                "help",
                "freenet.support.CPUInformation.CPUID",
                "net.i2p.CoreVersion",
                "net.i2p.client.naming.LookupDest",
                "net.i2p.crypto.Blinding",
                "net.i2p.crypto.CertUtil",
                "net.i2p.crypto.CryptoCheck",
                "net.i2p.crypto.KeyGenerator",
                "net.i2p.crypto.KeyStoreUtil",
                "net.i2p.crypto.SelfSignedGenerator",
                "net.i2p.crypto.SHA256Generator",
                "net.i2p.crypto.SU3File",
                "net.i2p.crypto.TrustedUpdate",
                "net.i2p.data.Base32",
                "net.i2p.data.Base64",
                "net.i2p.data.PrivateKeyFile",
                "net.i2p.time.BuildTime",
                "net.i2p.util.Addresses",
                "net.i2p.util.ConvertToHash",
                "net.i2p.util.DNSOverHTTPS",
                "net.i2p.util.EepGet",
                "net.i2p.util.EepHead",
                "net.i2p.util.FileUtil",
                "net.i2p.util.FortunaRandomSource",
                "net.i2p.util.NativeBigInteger",
                "net.i2p.util.PartialEepGet",
                "net.i2p.util.RFC822Date",
                "net.i2p.util.ShellCommand",
                "net.i2p.util.SSLEepGet",
                "net.i2p.util.SystemVersion",
                "net.i2p.util.TranslateReader",
                "net.i2p.util.ZipFileComment"
            });

    protected CommandLine() {}

    public static void main(String args[]) {
        if (args.length > 0) {
            exec(args, CLASSES);
        }
        usage();
        System.exit(1);
    }

    /** will only return if command not found */
    protected static void exec(String args[], List<String> classes) {
        boolean help = false;
        String cmd = args[0].toLowerCase(Locale.US);
        if (cmd.equals("help")) {
            if (args.length != 2)
                return;
            cmd = args[1].toLowerCase(Locale.US);
            args[1] = "-?";
            help = true;
        }
        for (String cls : classes) {
            String ccmd = cls.substring(cls.lastIndexOf('.') + 1).toLowerCase(Locale.US);
            if (cmd.equals(ccmd)) {
                try {
                    Class<?> c = Class.forName(cls, true, ClassLoader.getSystemClassLoader());
                    if (help) {
                        // if it has a usage() method, call that instead
                        try {
                            Method usage = c.getDeclaredMethod("usage", (Class[]) null);
                            usage.setAccessible(true);
                            usage.invoke(null);
                            System.exit(0);
                        } catch (Exception e) {}
                        // else fall through to try main("-?")
                    }
                    Method main = c.getMethod("main", String[].class);
                    String[] cargs = new String[args.length - 1];
                    System.arraycopy(args, 1, cargs, 0, args.length - 1);
                    main.invoke(null, (Object) cargs);
                    System.exit(0);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        }
    }

    private static void usage() {
        System.err.println("I2P Core version " + CoreVersion.VERSION + '\n' +
                           "USAGE: java -jar /path/to/i2p.jar command [args]");
        printCommands(CLASSES);
    }

    protected static void printCommands(List<String> classes) {
        System.err.println("Available commands:");
        List<String> cmds = new ArrayList<String>(classes.size());
        int max = 0;
        for (String cls : classes) {
            String ccmd = cls.substring(cls.lastIndexOf('.') + 1).toLowerCase(Locale.US);
            cmds.add(ccmd);
            if (ccmd.length() > max)
                max = ccmd.length();
        }
        Collections.sort(cmds);
        StringBuilder buf = new StringBuilder(80);
        for (String cmd : cmds) {
            int len = buf.length();
            if (len == 0)
                buf.append("    ");
            buf.append(cmd);
            if (len > 80 - max) {
                System.err.println(buf);
                buf.setLength(0);
            } else {
                int spc = 1 + max - cmd.length();
                for (int i = 0; i < spc; i++) {
                    buf.append(' ');
                }
            }
        }
        if (buf.length() > 0)
            System.out.println(buf);
        System.err.println();
        System.err.println("Enter \"help command\" for detailed help.");
    }
}
