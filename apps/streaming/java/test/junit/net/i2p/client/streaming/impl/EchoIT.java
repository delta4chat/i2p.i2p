package net.i2p.client.streaming.impl;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import org.junit.Test;


import net.i2p.I2PAppContext;
import net.i2p.client.I2PSession;
import net.i2p.client.streaming.I2PSocket;
import net.i2p.client.streaming.I2PServerSocket;
import net.i2p.client.streaming.I2PSocketManager;
import net.i2p.client.streaming.IncomingConnectionFilter;
import net.i2p.data.DataHelper;
import net.i2p.util.Log;

/**
 *
 */
public class EchoIT extends StreamingITBase {
    private Log _log;
    private I2PSession _client;
    private I2PSession _server;

    @Test
    public void test() throws Exception {
        I2PAppContext context = I2PAppContext.getGlobalContext();
        _log = context.logManager().getLog(ConnectIT.class);
        _log.debug("creating server session");
        _server = createSession();
        _log.debug("running server");
        runServer(context, _server);
        _log.debug("creating client session");
        _client = createSession();
        _log.debug("running client");
        runClient(context, _client);
    }



    @Override
    protected Properties getProperties() {
        return new Properties();
    }

    @Override
    protected Runnable getClient(I2PAppContext ctx, I2PSession session) {
        return new ClientRunner(ctx,session);
    }

    @Override
    protected Runnable getServer(I2PAppContext ctx, I2PSession session) {
        return new ServerRunner(ctx,session);
    }



    private class ServerRunner extends RunnerBase {
        public ServerRunner(I2PAppContext ctx, I2PSession session) {
            super(ctx,session);
        }

        public void run() {
            try {
                Properties opts = new Properties();
                I2PSocketManager mgr = new I2PSocketManagerFull(
                    _context, _session, opts, "client", IncomingConnectionFilter.ALLOW);
                _log.debug("manager created");
                I2PServerSocket ssocket = mgr.getServerSocket();
                _log.debug("server socket created");
                while (true) {
                    I2PSocket socket = ssocket.accept();
                    _log.debug("socket accepted: " + socket);
                    InputStream in = socket.getInputStream();
                    OutputStream out = socket.getOutputStream();
                    _log.debug("server streams built");
                    byte buf[] = new byte[5];
                    while (buf != null) {
                        for (int i = 0; i < buf.length; i++) {
                            int c = in.read();
                            if (c == -1) {
                                buf = null;
                                break;
                            } else {
                                buf[i] = (byte)(c & 0xFF);
                            }
                        }
                        if (buf != null) {
                            _log.debug("* server read: " + new String(buf));
                            out.write(buf);
                            out.flush();
                        }
                    }
                    if (_log.shouldLog(Log.DEBUG))
                        _log.debug("Closing the received server socket");
                    socket.close();
                }
            } catch (Exception e) {
                _log.error("error running", e);
            }
        }

    }

    private class ClientRunner extends RunnerBase {
        public ClientRunner(I2PAppContext ctx, I2PSession session) {
            super(ctx,session);
        }

        public void run() {
            try {
                Properties opts = new Properties();
                I2PSocketManager mgr = new I2PSocketManagerFull(
                    _context, _session, opts, "client", IncomingConnectionFilter.ALLOW);
                _log.debug("manager created");
                I2PSocket socket = mgr.connect(_server.getMyDestination());
                _log.debug("socket created");
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                for (int i = 0; i < 3; i++) {
                    out.write(DataHelper.getASCII("blah!"));
                    _log.debug("client wrote a line");
                    out.flush();
                    _log.debug("client flushed");
                    byte buf[] = new byte[5];

                    for (int j = 0; j < buf.length; j++) {
                        int c = in.read();
                        if (c == -1) {
                            buf = null;
                            break;
                        } else {
                            //_log.debug("client read: " + ((char)c));
                            buf[j] = (byte)(c & 0xFF);
                        }
                    }
                    if (buf != null) {
                        _log.debug("* client read: " + new String(buf));
                    }
                }
                if (_log.shouldLog(Log.DEBUG))
                    _log.debug("Closing the client socket");
                socket.close();
                _log.debug("socket closed");

                Thread.sleep(5*1000);
                System.exit(0);
            } catch (Exception e) {
                _log.error("error running", e);
            }
        }

    }
}
