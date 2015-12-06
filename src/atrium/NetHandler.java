package atrium;

import java.awt.Desktop;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Server;
import io.serialize.StreamedBlock;
import io.serialize.StreamedBlockedFile;
import listeners.BlockListener;
import listeners.DualListener;
import net.discover.DiscoveryClient;
import net.discover.DiscoveryServer;
import packets.data.Data;
import packets.requests.Request;
import packets.requests.RequestTypes;

/**
 * Handles server, client, and discovery operations
 * @author Kevin Cai
 */
public class NetHandler {

	public static String externalIp;
	public static boolean extVisible;
	public static List<InetAddress> foundHosts;

	private Server server;

	public NetHandler() {
		externalIp = getExtIp();
		checkExtVisibility();
		Core.peers = new ArrayList<Peer> ();
		registerServerListeners();
		Client initialClient = getClient();
		registerClientListeners(initialClient);
		peerDiscovery(initialClient);
	}

	private String getExtIp() {
		try {
			URL apiUrl = new URL("http://checkip.amazonaws.com");
			HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();

			if (conn.getResponseCode() != 200) {
				throw new IOException(conn.getResponseMessage());
			}

			BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = rd.readLine()) != null) {
				sb.append(line);
			}
			rd.close();
			conn.disconnect();
			String finalStr = sb.toString();
			Utilities.log(this, "External IP is: " + finalStr);
			return finalStr;
		} catch(Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}

	private void checkExtVisibility() {
		if(externalIp != null) {
			try {
				URL apiUrl = new URL("http://tuq.in/tools/port.txt?ip=" + externalIp + "&port=" + Core.config.tcpPort);
				HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();

				if (conn.getResponseCode() != 200) {
					throw new IOException(conn.getResponseMessage());
				}

				BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				StringBuilder sb = new StringBuilder();
				String line;
				while ((line = rd.readLine()) != null) {
					sb.append(line);
				}
				rd.close();
				conn.disconnect();
				String finalStr = sb.toString();
				if(finalStr != null) {
					Boolean works = Boolean.parseBoolean(finalStr);
					extVisible = Core.config.cacheEnabled = works;
					if(!Core.headless) {
						if(!extVisible && !Core.config.notifiedPortForwarding) {
							(new Thread(new Runnable() {
								public void run() {
									Utilities.log(this, "Not externally visible, caching disabled");
									Core.config.notifiedPortForwarding = true;
									Core.config.writeConfig();
									JLabel label = new JLabel();
									Font font = label.getFont();

									StringBuffer style = new StringBuffer("font-family:" + font.getFamily() + ";");
									style.append("font-weight:" + (font.isBold() ? "bold" : "normal") + ";");
									style.append("font-size:" + font.getSize() + "pt;");
									style.append("color: D1D0CE;");
									JEditorPane ep = new JEditorPane(
											"text/html", 
											"<html><body style=\"" + style + "\">Please consider port forwarding " + Core.config.tcpPort
											+ " TCP on your network. &nbsp; <br>"
											+ "Not port forwarding leeches on the network :( <br><br>"
											+ "<a style=\"color: #FFFFFF\" href=\"http://www.wikihow.com/Set-Up-Port-Forwarding-on-a-Router\">"
											+ "How to Port Forward</a></html>"
											);
									ep.addHyperlinkListener(new HyperlinkListener() {
										@Override
										public void hyperlinkUpdate(HyperlinkEvent he) {
											if(he.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
												try {
													Desktop.getDesktop().browse(new URI("http://www.wikihow.com/Set-Up-Port-Forwarding-on-a-Router"));
												} catch(Exception ex) {
													ex.printStackTrace();
												}
											}
										}
									});
									ep.setEditable(false);
									JOptionPane.showMessageDialog(null, ep);
								}
							})).start();
						} else {
							Utilities.log(this, "Externally visible, caching enabled");
						}
					}
				}
			} catch(Exception ex) {
				ex.printStackTrace();
			}
		}
		extVisible = false;
	}

	public void registerServerListeners() {
		try {
			server = new Server(512000 * 6, 512000 * 6);
			registerClasses(server.getKryo());

			Utilities.log(this, "Registering block listener");
			server.addListener(new BlockListener());
			
			Utilities.switchGui(this, "Registering server listeners");
			server.addListener(new DualListener(1));

			Utilities.switchGui(this, "Starting server component");
			server.bind(Core.config.tcpPort);
			server.start();

		} catch (Exception ex) {}
	}

	public Client getClient() {
		Client client = new Client(512000 * 4, 512000 * 4);
		registerClientListeners(client);
		return client;
	}

	public void registerClientListeners(Client client) {
		try {
			registerClasses(client.getKryo());
			Utilities.log(this, "Registered client listeners");

			//Use listeners, not arbitrary code
			client.addListener(new DualListener(0));

			Utilities.log(this, "Starting client component");
			client.start();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private void registerClasses(Kryo kryo) {
		//Shared fields import
		kryo.register(String[].class);
		kryo.register(ArrayList.class);
		kryo.register(byte[].class);

		//Specifics import
		kryo.register(Data.class);
		kryo.register(Request.class);
		kryo.register(Peer.class);
		kryo.register(Client.class);
		kryo.register(Inet4Address.class);
		kryo.register(StreamedBlockedFile.class);
		kryo.register(StreamedBlock.class);
	}

	private void peerDiscovery(Client client) {
		try {
			Utilities.switchGui(this, "Locating peers...");

			foundHosts = new ArrayList<InetAddress> ();
			(new Thread(new DiscoveryServer())).start();
			Thread discoverClient = new Thread(new DiscoveryClient());
			discoverClient.start();
			discoverClient.join();

			//TODO: remove this debug section
			foundHosts.clear();
			foundHosts.add(InetAddress.getByName("136.167.192.28"));
			//foundHosts.add(InetAddress.getByName("192.227.251.74"));
			//foundHosts.add(InetAddress.getByName("136.167.252.240"));

			//Filter out local IP
			InetAddress localhost = InetAddress.getLocalHost();
			foundHosts.remove(localhost);
			InetAddress[] allLocalIps = InetAddress.getAllByName(localhost.getCanonicalHostName());
			if(allLocalIps != null && allLocalIps.length > 1) {
				Utilities.log(this, "Multiple local IPs detected");
				for(int i=0; i < allLocalIps.length; i++) {
					foundHosts.remove(allLocalIps[i]);
				}
			}

			//Filter out loop-back interfaces
			Enumeration<?> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				NetworkInterface networkInterface = (NetworkInterface) interfaces.nextElement();
				Enumeration<InetAddress> addresses =  networkInterface.getInetAddresses();
				while(addresses.hasMoreElements()) {
					InetAddress inetAddress = addresses.nextElement();
					foundHosts.remove(inetAddress);
				}
			}

			if(foundHosts.size() == 0) {
				Utilities.log(this, "No hosts found on LAN");
			} else {
				Utilities.log(this, "Found hosts: " + foundHosts);
			}

			//DEBUG
			//TODO: make this not just an IP, but also port
			//TODO: port randomization
			for(InetAddress ia : foundHosts) {
				try {
					Utilities.log(this, "Attempting connect to " + ia.getHostAddress());
					Client newConnection = getClient();
					newConnection.connect(8000, ia, Core.config.tcpPort);
				} catch (Exception ex) {
					Utilities.log(this, "Connection to " + ia.getHostAddress() + " failed");
				}
			}

			Utilities.log(this, "Terminated peer discovery");
			(new Thread(new Runnable() {
				public void run() {
					while(true) {
						if(!Core.headless) {
							if(Core.peers.isEmpty()) {
								try {
									Thread.sleep(1000);
								} catch (Exception ex) {
									ex.printStackTrace();
								}
							} else {
								Core.mainWindow.ready();
								break;
							}
						}
					}
				}
			})).start();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public static void doSearch(String keyword) {
		for(Peer peer : Core.peers) {
			peer.getConnection().sendTCP(new Request(RequestTypes.SEARCH, Core.aes.encrypt(keyword)));
		}
	}

	public static void requestBlock(String origin, String block) {
		for(Peer peer : Core.peers) {
			peer.getConnection().sendTCP(new Request(RequestTypes.BLOCK, new String[] {Core.aes.encrypt(origin), Core.aes.encrypt(block)}));
		}
	}
}
