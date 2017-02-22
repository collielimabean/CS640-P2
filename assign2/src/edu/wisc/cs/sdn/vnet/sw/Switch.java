package edu.wisc.cs.sdn.vnet.sw;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.MACAddress;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device implements Runnable
{
    private static final long ADDRESS_TIMEOUT = 15000; // 15 seconds
    
    private Thread learnedAddressThread;
    
    private Map<MACAddress, IfaceTime> learnedAddrMap;
    
    private class IfaceTime
    {
        public Iface iface;
        public long timeAdded;
        
        public IfaceTime(Iface iface, long timeAdded)
        {
            this.iface = iface;
            this.timeAdded = timeAdded;
        }
    }
    
    /**
     * Creates a router for a specific host.
     * @param host hostname for the router
     */
    public Switch(String host, DumpFile logfile)
    {
        super(host, logfile);
        learnedAddrMap = new ConcurrentHashMap<MACAddress, IfaceTime>();
        learnedAddressThread = new Thread(this);
        learnedAddressThread.start();
    }

    /**
     * Handle an Ethernet packet received on a specific interface.
     * @param etherPacket the Ethernet packet that was received
     * @param inIface the interface on which the packet was received
     */
    public void handlePacket(Ethernet etherPacket, Iface inIface)
    {
        System.out.println("*** -> Received packet: " + etherPacket.toString().replace("\n", "\n\t"));

        /* TODO: Handle packets */
        // First, add to source MAC & interface to table
        if (learnedAddrMap.containsKey(etherPacket.getSourceMAC()))
            learnedAddrMap.get(etherPacket.getSourceMAC()).timeAdded = System.currentTimeMillis();
        else
            learnedAddrMap.put(etherPacket.getSourceMAC(), new IfaceTime(inIface, System.currentTimeMillis()));
        
        // do we know where the dest MAC is?
        if (learnedAddrMap.containsKey(etherPacket.getDestinationMAC()))
        {
            // yes, we do
            this.sendPacket(etherPacket, learnedAddrMap.get(etherPacket.getDestinationMAC()).iface);
            return;
        }
        
        // no, we don't - so broadcast
        for (Iface iface : this.interfaces.values())
        {
            if (!inIface.getName().equals(iface.getName()))
                this.sendPacket(etherPacket, iface);
        }
        
    }

    @Override
    public void run()
    {
        while (true)
        {
            for (Map.Entry<MACAddress, IfaceTime> pair : learnedAddrMap.entrySet())
            {
                long currentTime = System.currentTimeMillis();
                if (currentTime - pair.getValue().timeAdded >= ADDRESS_TIMEOUT)
                    learnedAddrMap.remove(pair.getKey());
            }
            
            try
            {
                Thread.sleep(500);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }
}
