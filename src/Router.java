
import cpsc441.a4.shared.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Timer;

/**
 * Router Class
 *
 * This class implements the functionality of a router when running the distance
 * vector routing algorithm.
 *
 * The operation of the router is as follows: 1. send/receive HELLO message 2.
 * while (!QUIT) receive ROUTE messages update mincost/nexthop/etc 3. Cleanup
 * and return
 *
 * A separate process broadcasts routing update messages to directly connected
 * neighbors at regular intervals.
 *
 *
 * @author Majid Ghaderi
 * @version	2.1
 *
 */
public class Router {

    private final int routerId;
    private final String serverName;
    private final int serverPort;
    private final int updateInterval;

    private Socket relayServerSocket;

    private ObjectInputStream relayois;
    private ObjectOutputStream relayoos;

    private int[] linkcost;
    private int[] mincost;
    private int[] nexthop;

    private Timer timer;
    private RouterTimeoutHandler rth;

    /**
     * Constructor to initialize the router instance
     *
     * @param routerId	Unique ID of the router starting at 0
     * @param serverName	Name of the host running the network server
     * @param serverPort	TCP port number of the network server
     * @param updateInterval	Time interval for sending routing updates to
     * neighboring routers (in milli-seconds)
     */
    public Router(int routerId, String serverName, int serverPort, int updateInterval) {
        this.routerId = routerId;
        this.serverName = serverName;
        this.serverPort = serverPort;
        this.updateInterval = updateInterval;
        try {
            relayServerSocket = new Socket(serverName, serverPort);
            relayois = new ObjectInputStream(relayServerSocket.getInputStream());
            relayoos = new ObjectOutputStream(relayServerSocket.getOutputStream());
        } catch (UnknownHostException uhe) {
            System.err.println("Constructor uhe exception");
        } catch (IOException ioe) {
            System.err.println("Constructor ioe exception");
        }
    }

    /**
     * starts the router
     *
     * @return The forwarding table of the router
     */
    public RtnTable start() {
        try {
            //1. send/receive HELLO message
            DvrPacket pcktTx = new DvrPacket(routerId, DvrPacket.SERVER, DvrPacket.HELLO); // hello packet
            relayoos.writeObject(pcktTx);
            relayoos.flush();
            System.out.println(">>>> " + pcktTx.toString());

            DvrPacket pcktRv;
            rth = new RouterTimeoutHandler(this);
            timer = new Timer();
            //* 2. while (!QUIT)
            while ((pcktRv = (DvrPacket) relayois.readObject()).type != DvrPacket.QUIT) { // blocking op
                switch (pcktRv.type) {
                    case DvrPacket.HELLO:
                        System.out.println("<<<< " + pcktRv.toString());
                        System.out.println("Initialization complete - setting costs");
                        initCosts(pcktRv); // initial costs set
                        timer.scheduleAtFixedRate(rth, 0, updateInterval);
                        break;
                    case (DvrPacket.ROUTE):
                        if (pcktRv.sourceid == DvrPacket.SERVER){
                            System.out.println("<<<< " + pcktRv.toString());
                            System.out.println("Topology Update - setting link costs");
                            initCosts(pcktRv); // initial costs set
                        }else{
                            System.out.println("<<<< " + pcktRv.toString());
                            System.out.println("Mincost Update - running processDvr");
                            processDvr(pcktRv);
                        }
                        break;
                }
            }
            //* 3. Cleanup and return
            shutdown();
            return new RtnTable(mincost,nexthop);
        } catch (UnknownHostException uhe) {
            System.err.println("Start method uhe exception");
            shutdown();
            return null;
        } catch (IOException ioe) {
            System.err.println("Start method ioe exception");
            shutdown();
            return new RtnTable(mincost,nexthop);
        } catch (ClassNotFoundException cnfe) {
            System.err.println("Start method cnfe exception");
            shutdown();
            return null;
        }
    }

    public void processTimeout() {
        //broadcast mincosts to all neighbours
        try {
            for (int i = 0; i < mincost.length; i++) {
                if (i < this.routerId && linkcost[i] != DvrPacket.INFINITY) {
                    //send to linked router with lower id than me
                    DvrPacket pcktTx = new DvrPacket(this.routerId, i, DvrPacket.ROUTE, Arrays.copyOf(mincost, mincost.length));
                    relayoos.writeObject(pcktTx);
                    relayoos.flush();
                    System.out.println(">>>> "+pcktTx.toString());
                } else if (i > this.routerId && linkcost[i] != DvrPacket.INFINITY) {
                    // send to router with higher id than me
                    DvrPacket pcktTx = new DvrPacket(this.routerId, i, DvrPacket.ROUTE, Arrays.copyOf(mincost, mincost.length));
                    relayoos.writeObject(pcktTx);
                    relayoos.flush();
                    System.out.println(">>>> "+pcktTx.toString());
                }
            }
        } catch (IOException e) {
            System.err.println("Connection to server Lost. Terminating");
            shutdown();

        }
    }

    private void initCosts(DvrPacket pcktRv) {
        System.out.println("setting costs");
        linkcost = pcktRv.getMinCost();
        mincost = pcktRv.getMinCost(); // initialize mincost values to linkcost
        nexthop = new int[linkcost.length];
        for(int i=0;i<nexthop.length;i++){ // init the next hop array to my id
           nexthop[i] = i;
        }
    }

    private void processDvr(DvrPacket pcktRv) {
        
        int[] nmincost = pcktRv.getMinCost(); // broadcast min costs
        int ourLink = linkcost[pcktRv.sourceid]; // edge or shorter value

        for(int i=0; i<mincost.length; i++){
            if((nmincost[i] + ourLink) < mincost[i]){
                mincost[i] = nmincost[i] + ourLink;
                nexthop[i] = pcktRv.sourceid;
            }
        }
    }

    private void shutdown(){
        this.timer.cancel();
    }

    /**
     * A simple test driver
     *
     */
    public static void main(String[] args) {
        // default parameters
        int routerId = 0;
        String serverName = "localhost";
        int serverPort = 2227;
        int updateInterval = 1000; //milli-seconds

        // the router can be run with:
        // i. a single argument: router Id
        // ii. all required arquiments
        if (args.length == 1) {
            routerId = Integer.parseInt(args[0]);
        } else if (args.length == 4) {
            routerId = Integer.parseInt(args[0]);
            serverName = args[1];
            serverPort = Integer.parseInt(args[2]);
            updateInterval = Integer.parseInt(args[3]);
        } else {
            System.out.println("incorrect usage, try again.");
            System.exit(0);
        }

        // print the parameters
        System.out.printf("starting Router #%d with parameters:\n", routerId);
        System.out.printf("Relay server host name: %s\n", serverName);
        System.out.printf("Relay server port number: %d\n", serverPort);
        System.out.printf("Routing update intwerval: %d (milli-seconds)\n", updateInterval);

        // start the server
        // the start() method blocks until the router receives a QUIT message
        Router router = new Router(routerId, serverName, serverPort, updateInterval);
        RtnTable rtn = router.start();
        System.out.println("Router terminated normally");

        // print the computed routing table
        System.out.println();
        System.out.println("Routing Table at Router #" + routerId);
        System.out.print(rtn.toString());
    }

}
