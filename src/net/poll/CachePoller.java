package net.poll;

import atrium.Core;
import atrium.Peer;
import atrium.Utilities;
import packets.data.Data;
import packets.data.DataTypes;

public class CachePoller implements Runnable {

	public void run() {
		while (true) {
			try {
				for (int i = 0; i < Core.peers.size(); i++) {
					Peer peer = Core.peers.get(i);
					Thread.sleep(3000);
					if (peer.getConnection().isConnected()) {
						if (peer.getAES() == null) {
							continue;
						}
						Utilities.log(this, "Polling peer " + peer.getMutex() + " for cache", false);
						peer.getConnection().sendTCP(new Data(DataTypes.CACHE_REQS, null));
					}
				}
				Thread.sleep(90000);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
}
