package edu.wisc.cs.sdn.vnet.rt;

import java.util.Map;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{   
    /** Routing table for the router */
    private RouteTable routeTable;
    
    /** ARP cache for the router */
    private ArpCache arpCache;
    
    /**
     * Creates a router for a specific host.
     * @param host hostname for the router
     */
    public Router(String host, DumpFile logfile)
    {
        super(host,logfile);
        this.routeTable = new RouteTable();
        this.arpCache = new ArpCache();
    }
    
    /**
     * @return routing table for the router
     */
    public RouteTable getRouteTable()
    { return this.routeTable; }
    
    /**
     * Load a new routing table from a file.
     * @param routeTableFile the name of the file containing the routing table
     */
    public void loadRouteTable(String routeTableFile)
    {
        if (!routeTable.load(routeTableFile, this))
        {
            System.err.println("Error setting up routing table from file "
                    + routeTableFile);
            System.exit(1);
        }
        
        System.out.println("Loaded static route table");
        System.out.println("-------------------------------------------------");
        System.out.print(this.routeTable.toString());
        System.out.println("-------------------------------------------------");
    }
    
    /**
     * Load a new ARP cache from a file.
     * @param arpCacheFile the name of the file containing the ARP cache
     */
    public void loadArpCache(String arpCacheFile)
    {
        if (!arpCache.load(arpCacheFile))
        {
            System.err.println("Error setting up ARP cache from file "
                    + arpCacheFile);
            System.exit(1);
        }
        
        System.out.println("Loaded static ARP cache");
        System.out.println("----------------------------------");
        System.out.print(this.arpCache.toString());
        System.out.println("----------------------------------");
    }

    /**
     * Handle an Ethernet packet received on a specific interface.
     * @param etherPacket the Ethernet packet that was received
     * @param inIface the interface on which the packet was received
     */
    public void handlePacket(Ethernet etherPacket, Iface inIface)
    {
        System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));
        
        // verify type 
        if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
        {
            System.out.println("[Router] Dropped packet of type: " + etherPacket.getEtherType());
            return;
        }
        
        // verify checksum 
        IPv4 pkt = (IPv4) etherPacket.getPayload();
        short recvChecksum = pkt.getChecksum();
        
        // redo checksum
        pkt.resetChecksum();
        pkt.serialize();
        short computedChecksum = pkt.getChecksum();
        
        if (computedChecksum != recvChecksum)
        {
            System.out.println("[Router] Checksum error: received " + recvChecksum + ", computed " + computedChecksum);
            return;
        }
        
        // verify TTL
        if (pkt.getTtl() <= 1)
        {
            System.out.println("[Router] Received pkt with TTL <= 1. Dropping.");
            return;
        }
        
        // update TTL & checksum
        pkt.setTtl((byte)(pkt.getTtl() - 1));
        pkt.resetChecksum();
        pkt.serialize(); // probably not necessary
        
        // is the packet inbound on one of the router interfaces?
        for (Map.Entry<String, Iface> ifaceEntry : this.interfaces.entrySet())
        {
            if (ifaceEntry.getValue().getIpAddress() == pkt.getDestinationAddress())
            {
                System.out.println("[Router] Received pkt inbound on router interfaces, dropped.");
                return;
            }
        }
        
        // forward packet to other routers
        RouteEntry routeEntry = routeTable.lookup(pkt.getDestinationAddress());
        if (routeEntry == null)
        {
            System.out.println("[Router] No RouteEntry found, dropping packet.");
            return;
        }
        
        // update ethernet packet MAC addresses
        System.out.println("[Router] ARP Lookup input: " + IPv4.fromIPv4Address(routeEntry.getGatewayAddress()));
        ArpEntry arpEntry = arpCache.lookup(routeEntry.getGatewayAddress());
        if (arpEntry == null)
        {
            // intended for us?
            arpEntry = arpCache.lookup(pkt.getDestinationAddress());
            if (arpEntry == null)
            {
                // I guess not
                System.out.println("[Router] 2nd ARP Lookup failed, dropping.");
                return;
            }
            System.out.println("[Router] #2 ARP Lookup: " + IPv4.fromIPv4Address(pkt.getDestinationAddress()));
        }
        System.out.println("[Router] ARP Lookup: " + arpEntry);
        etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());
        etherPacket.setSourceMACAddress(routeEntry.getInterface().getMacAddress().toBytes());
        
        // send packet
        this.sendPacket(etherPacket, routeEntry.getInterface());
    }
}
